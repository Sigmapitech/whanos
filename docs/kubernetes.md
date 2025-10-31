# Whanos CI/CD: Jenkins + Docker + Kubernetes (AKS)

This document explains how to wire Whanos to Jenkins so any push to a Whanos‑compatible repository builds a container image and optionally deploys it to a Kubernetes cluster. It also lists the required dependencies and provides an end‑to‑end walkthrough.

## Overview

Pipeline, per assignment:

1. Fetch the Git repository
2. Detect language (C, Java, JavaScript, Python, Befunge)
3. Containerize using Whanos base/standalone images
4. Push to a Docker registry
5. If `whanos.yml` contains `deployment`, render manifests and deploy to the cluster

## Prerequisites

- Docker Engine on the Jenkins host (the container will mount `/var/run/docker.sock`)
- Python 3 on the Jenkins agent (for the renderer)
- kubectl on the Jenkins agent (already installed in our Jenkins image)
- A Kubernetes cluster with at least 2 nodes (our `infra/azure/az-setup.sh` creates AKS + NGINX Ingress)
- A container registry (Azure Container Registry recommended)

Optional (Azure):

- Azure CLI for local setup; not required inside Jenkins because we use `docker login` with credentials

## Bring up Jenkins

Use the helper script to build and start Jenkins (Configuration as Code included):

```bash
python script/setup fresh
```

You’ll be prompted for the Admin password (user: `admin`). The container mounts the host Docker socket to run Docker commands.

## Jenkins architecture (as required by the assignment)

- Users
  - Sign‑up disabled; `admin` user created with full rights (set in `infra/jenkins/jenkins.yaml`)
- Folders
  - `Whanos base images` (root): holds language base image jobs and a “Build all” job
  - `Projects` (root): contains one job per linked project
- Jobs
  - For each supported language: a freestyle job named like the base image (`whanos-<lang>`) that builds the base image from `images/<lang>/Dockerfile.base` (also builds `whanos-foundation`)
  - `Build all base images`: triggers all language base image jobs
  - `link-project` (root): parameterized job that creates a project job under `Projects/` from a Git URL
  - Project jobs (created by `link-project`): poll SCM every minute; on changes, build the container; if `whanos.yml` contains `deployment`, deploy to Kubernetes

Plugins (already baked in `plugins.txt`):

- configuration‑as‑code, job‑dsl, credentials, git, docker‑workflow, kubernetes‑cli, and QoL plugins

## Registry authentication

You need Jenkins to push images to your registry. Two common options for ACR:

1. ACR admin account (simple)

- Enable once: `az acr update -n <ACR_NAME> --admin-enabled true`
- In Jenkins, add “Username with password” credentials for the registry:
  - ID: `REGISTRY_CREDS`
  - Username: the ACR admin username
  - Password: the ACR admin password

1. Service Principal with acrpush (recommended)

- Create a Service Principal with `AcrPush` on your registry and note its `appId` and password
- In Jenkins, add “Username with password” credentials:
  - ID: `REGISTRY_CREDS`
  - Username: the `appId`
  - Password: the SP password

Either way, set these as global environment variables in Jenkins (Manage Jenkins → System):

- `REGISTRY_URL` = `whanos.azurecr.io` (or your registry)
- `K8S_NAMESPACE` = `default` (or your target namespace)

## Kubernetes access for Jenkins

kubectl is installed in the Jenkins container. Provide kubeconfig to Jenkins in one of two ways:

- Option A (mount host kubeconfig): add a volume to `infra/jenkins/docker-compose.yml` under the `jenkins` service:
  
  ```yaml
  - $HOME/.kube:/var/jenkins_home/.kube:ro
  
  ```

  Then set `KUBECONFIG=/var/jenkins_home/.kube/config` in Jenkins global env vars.

- Option B (copy kubeconfig): copy your kubeconfig into `infra/jenkins/jenkins_home/.kube/config` before starting Jenkins; the path inside the container is `/var/jenkins_home/.kube/config`.

Tip: our Azure script `infra/azure/az-setup.sh` already created AKS and configured your local kubeconfig. Use Option A to reuse it.

## Linking a project (link-project)

Run the `link-project` job with parameters:

- `PROJECT_NAME`: display name and Docker image name prefix
- `GIT_URL`: repository URL
- `GIT_BRANCH`: default `main`
- `ROOT_FOLDER`: if the app is not at repo root
- `GIT_CREDENTIALS_ID`: Jenkins credentials ID for private repos (optional)

The job will:

- Clone the repo, detect the language with `script/detect_language`
- Create `Projects/<PROJECT_NAME>` job bound to your Git repo
- Configure SCM polling every minute

## What a project job does

On each change on the default branch, it:

1. Builds the container image

- If the repo has a `Dockerfile` at its root, it builds with `BASE_IMAGE=whanos-<lang>` so you can extend the base image
- Otherwise it builds the language’s standalone image using `images/<lang>/Dockerfile.standalone`

1. Tags and pushes the image

Extend the job’s shell steps to push using registry credentials:

```bash
IMAGE_TAG=${GIT_COMMIT:-dev}
IMAGE_REF="${REGISTRY_URL}/${PROJECT_NAME}:${IMAGE_TAG}"

echo "Pushing ${IMAGE_REF}"
docker tag ${PROJECT_NAME} ${IMAGE_REF}

# Login once per run using Jenkins credentials
echo "$REGISTRY_PASSWORD" | docker login ${REGISTRY_URL} -u "$REGISTRY_USERNAME" --password-stdin
docker push ${IMAGE_REF}
```

1. Deploys to Kubernetes (if `whanos.yml` contains `deployment`)

```bash
if [ -f whanos.yml ]; then
  python script/kube_render                 \
    --image "${IMAGE_REF}"                  \
    --name "${PROJECT_NAME}"                \
    --namespace "${K8S_NAMESPACE:-default}" \
    --input whanos.yml                      \
    --out .kube-out

  kubectl --namespace "${K8S_NAMESPACE:-default}" apply -f .kube-out/
fi
```

If your `whanos.yml` is YAML and PyYAML isn’t available, either install PyYAML on the agent or provide a JSON equivalent (the renderer supports both).

## whanos.yml contract

Place a `whanos.yml` at the repository root. If it includes a `deployment` section, Whanos will deploy:

- `replicas`: integer (default 1)
- `resources`: Kubernetes‑style resources map (requests/limits)
- `ports`: list of container ports; if provided, a `Service type=LoadBalancer` is generated to expose them

Example:

```yaml
deployment:
  replicas: 2
  resources:
    requests:
      cpu: "100m"
      memory: "128Mi"
    limits:
      cpu: "500m"
      memory: "512Mi"
  ports: [3000]
```

## End‑to‑end walkthrough

1. Provision AKS + ACR (optional helper)

Run `infra/azure/az-setup.sh` to create RG, ACR, AKS and install NGINX Ingress. Providers are auto‑registered.

2. Start Jenkins

Run `python script/setup fresh` and open the Jenkins UI at <https://localhost:8080>, then login as `admin`.

3. Build base images once

Run `Whanos base images / Build all base images`.

4. Link a project

Run `link-project` with your Git URL (and credentials if private).

5. Configure registry credentials

Add `REGISTRY_CREDS` (username/password) and set `REGISTRY_URL` globally.

6. Provide kubeconfig to Jenkins

Mount `~/.kube` and set `KUBECONFIG` or copy kubeconfig into the Jenkins home.

7. Commit to the repo

Jenkins builds, tags, pushes the image, then deploys if `whanos.yml` has `deployment`.

8. Access the app

On AKS, wait for the Service external IP, then `curl http://<ip>:<port>/`.

## Troubleshooting

- Service external IP pending
  - `kubectl get svc -w` and `kubectl describe svc <name>`; check Azure events/quotas
- kubectl context not set in Jenkins
  - Ensure kubeconfig is mounted and `KUBECONFIG` points to it
- Docker push fails
  - Verify registry credentials; for ACR without admin user, use a Service Principal with `AcrPush`
- Non‑root build permission errors
  - When copying sources in Dockerfiles, prefer `COPY --chown=<user>:<group>` so build tools can write outputs

## Notes

- Keep runtime images minimal; use multi‑stage builds to strip build‑only tooling
- Test locally before pushing; then deploy via the renderer
- The cluster is attached to ACR in our setup, so Pods can pull images without explicit imagePullSecrets
