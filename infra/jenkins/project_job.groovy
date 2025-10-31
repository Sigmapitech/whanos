def String projectName = binding.variables['PROJECT_NAME']
def String gitUrl = binding.variables['GIT_URL']
def String gitBranch = binding.variables['GIT_BRANCH']
def String root_folder = binding.variables['ROOT_FOLDER']
def String credentialsId = binding.variables['GIT_CREDENTIALS_ID']
def File languageFile = new File("${WORKSPACE}/repo/detected_language.txt")
def String language = languageFile.text.trim().toLowerCase()
def String baseImage = "whanos-${language}"

folder('Projects') {
    displayName('Projects')
    description('Available projects in Whanos')
}

freeStyleJob("Projects/${projectName}") {
    displayName("${projectName}")
    description('Linked project managed by Whanos')

    scm {
        git {
            remote {
                url("${gitUrl}")
                if (credentialsId) {
                    credentials(credentialsId)
                }
            }
            branch("*/${gitBranch}")
        }
    }

    triggers {
        scm('* * * * *')  // every minute
    }

    steps {
        shell("echo Building ${projectName} using ${baseImage}")
        shell("""
            set -e
            WORK_DIR="/var/jenkins_home/workspace/Projects/${projectName}/${root_folder}"
            cd "$WORK_DIR"

            if [ -f Dockerfile ]; then
                echo "Dockerfile found, building image..."
                docker build -t ${projectName} --build-arg BASE_IMAGE=${baseImage} .
            else
                docker build -t ${projectName} -f /images/${language}/Dockerfile.standalone .
            fi

            # Tag and push if registry env vars are provided
            IMAGE_TAG=${GIT_COMMIT:-dev}
            IMAGE_REF="${REGISTRY_URL}/${projectName}:${IMAGE_TAG}"

            if [ -n "${REGISTRY_URL}" ] && [ -n "${REGISTRY_USERNAME}" ] && [ -n "${REGISTRY_PASSWORD}" ]; then
                echo "Logging into registry ${REGISTRY_URL}"
                echo "${REGISTRY_PASSWORD}" | docker login "${REGISTRY_URL}" -u "${REGISTRY_USERNAME}" --password-stdin
                echo "Tagging and pushing ${IMAGE_REF}"
                docker tag ${projectName} "${IMAGE_REF}"
                docker push "${IMAGE_REF}"
            else
                echo "WARN: REGISTRY_URL/REGISTRY_USERNAME/REGISTRY_PASSWORD not set. Skipping push." >&2
            fi

            # Deploy to Kubernetes if whanos.yml present
            if [ -f whanos.yml ]; then
                echo "Rendering Kubernetes manifests"
                python /workspace/script/kube_render.py \
                  --image "${IMAGE_REF:-${projectName}:dev}" \
                  --name "${projectName}" \
                  --namespace "${K8S_NAMESPACE:-default}" \
                  --input whanos.yml \
                  --out .kube-out

                echo "Applying manifests to cluster"
                kubectl --namespace "${K8S_NAMESPACE:-default}" apply -f .kube-out/
            else
                echo "No whanos.yml present. Skipping deployment."
            fi
        """)
    }
}
