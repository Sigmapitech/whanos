def String projectName = binding.variables['PROJECT_NAME']
def String gitUrl = binding.variables['GIT_URL']
def String gitBranch = binding.variables['GIT_BRANCH']
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
        shell("docker build -t ${projectName}:${language} --build-arg BASE_IMAGE=${baseImage} .")
        shell("""
            #!/bin/bash
            set -e
            // if [ -f whanos.yml ]; then
            //     echo "Deploying ${projectName} to Kubernetes..."
            //     kubectl apply -f whanos.yml
            // else
            //     echo "No whanos.yml found, skipping deployment."
            // fi
        """)
    }
}
