folder('Whanos base images') {
    def List<String> languages = [
        'befunge',
        'c',
        'python',
        'java',
        'javascript'
    ]

    displayName(name)
    description('Base images of whanos.')

    languages.each { lang ->
        freeStyleJob("${name}/whanos-${lang}") {
            steps {
                shell('docker build -t whanos-foundation - < /images/Dockerfile.foundation')
                shell("docker build -t whanos-${lang} - < /images/${lang}/Dockerfile.base")
            }

            triggers {
                upstream('Build all base images')
            }
        }
    }

    freeStyleJob("${name}/Build all base images") {
        steps {
            shell('echo "Building all base images"')
        }

        publishers {
            downstreamParameterized {
                trigger(languages.collect { lang -> "${name}/whanos-${lang}" }) {
                    condition('ALWAYS')
                    parameters {
                        currentBuild()
                    }
                }
            }
        }
    }
}

folder('Projects') {
    displayName(name)
    description('Available projects in whanos')
}

freeStyleJob('link-project') {
    displayName('Link Project')
    description('Links a repository to the Whanos infrastructure by creating a corresponding Jenkins project job.')

    parameters {
        stringParam('PROJECT_NAME', '', 'Name of the project (e.g. my-app)')
        stringParam('GIT_URL', '', 'Git repository URL to link')
        stringParam('GIT_BRANCH', 'main', 'Branch to monitor (default: main)')
        stringParam('ROOT_FOLDER', '', 'Root folder in the repository (if applicable)')
        stringParam('NAMESPACE', 'default', 'Kubernetes namespace for deployment (if applicable)')
        credentialsParam('GIT_CREDENTIALS_ID') {
            description('Credentials ID for accessing the Git repository (if private)')
            defaultValue('')
        }
    }

    steps {
        shell('''
            #!/bin/bash
            set -e

            rm -rf repo
            git clone "$GIT_URL" repo
            cd repo
            git checkout "$GIT_BRANCH"

            echo "Detecting language..."
            WHANOS_DIR=$PWD
            if [ -n "$ROOT_FOLDER" ]; then
                cd "$ROOT_FOLDER"

            fi

            LANG_DETECTED=$("$WHANOS_DIR/script/detect_language")
            EXIT_CODE=$?

            if [ $EXIT_CODE -ne 0 ]; then
                echo "Error: repository is not Whanos-compatible"
                exit 1
            fi

            echo "Detected language: $LANG_DETECTED"
            echo "$LANG_DETECTED" > $WHANOS_DIR/detected_language.txt
            export LANG_DETECTED=$(cat $WHANOS_DIR/detected_language.txt)

            # Copy DSL script to workspace
            cp /workspace/infra/jenkins/project_job.groovy $WORKSPACE/project_job.groovy
        ''')
        dsl {
            external("project_job.groovy")
            additionalClasspath("project_job.groovy/lives")
            ignoreExisting(false)
        }
    }
}
