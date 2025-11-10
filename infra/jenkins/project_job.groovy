def String projectName = binding.variables['PROJECT_NAME']
def String gitUrl = binding.variables['GIT_URL']
def String gitBranch = binding.variables['GIT_BRANCH']
def String root_folder = binding.variables['ROOT_FOLDER']
def String k8sNamespace = binding.variables['NAMESPACE']
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

    environmentVariables {
        env('REGISTRY_URL', System.getenv('REGISTRY_URL') ?: '')
        env('REGISTRY_USERNAME', System.getenv('REGISTRY_USERNAME') ?: '')
        env('REGISTRY_PASSWORD', System.getenv('REGISTRY_PASSWORD') ?: '')
    }

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
            cd /var/jenkins_home/workspace/Projects/${projectName}/${root_folder}

            # Get latest Git commit hash
            GIT_COMMIT=\$(git rev-parse --short HEAD)
            IMAGE_TAG=\${GIT_COMMIT}
            IMAGE_REF="\${REGISTRY_URL:-local}/${projectName}"

            # Build Docker image
            if [ -f Dockerfile ]; then
                echo "Dockerfile found, building image..."
                docker build -t ${projectName} --build-arg BASE_IMAGE=${baseImage} .
            else
                docker build -t ${projectName} -f /images/${language}/Dockerfile.standalone .
            fi

            # Tag with commit hash and latest
            docker tag ${projectName} "\${IMAGE_REF}:\${IMAGE_TAG}"
            docker tag ${projectName} "\${IMAGE_REF}:latest"

            # Push if registry credentials are set
            if [ -n "\${REGISTRY_URL}" ] && [ -n "\${REGISTRY_USERNAME}" ] && [ -n "\${REGISTRY_PASSWORD}" ]; then
                echo "Logging into registry \${REGISTRY_URL}"
                echo "\${REGISTRY_PASSWORD}" | docker login "\${REGISTRY_URL}" -u "\${REGISTRY_USERNAME}" --password-stdin
                echo "Pushing images..."
                docker push "\${IMAGE_REF}:\${IMAGE_TAG}"
                docker push "\${IMAGE_REF}:latest"
            else
                echo "WARN: REGISTRY_URL/REGISTRY_USERNAME/REGISTRY_PASSWORD not set. Skipping push." >&2
            fi

            # Deploy to Kubernetes if whanos.yml present
            if [ -f whanos.yml ]; then
                echo "Rendering Kubernetes manifests"
                /venv/bin/python3 /usr/share/jenkins/ref/script/kube_render \
                    --image "\${IMAGE_REF}:\${IMAGE_TAG}" \
                    --name "${projectName}" \
                    --namespace "${k8sNamespace}" \
                    --input whanos.yml \
                    --out .kube-out

                echo "Applying manifests to cluster"
                kubectl --namespace "${k8sNamespace}" apply -f .kube-out/
            else
                echo "No whanos.yml present. Skipping deployment."
            fi
        """)
    }
}
