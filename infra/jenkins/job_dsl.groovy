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
        ''')

        systemGroovyCommand('''
            import javaposse.jobdsl.plugin.*
            import jenkins.model.*

            def projectName = build.buildVariableResolver.resolve("PROJECT_NAME")
            def gitUrl = build.buildVariableResolver.resolve("GIT_URL")
            def language = new File("detected_language.txt").text.trim().toLowerCase()
            def baseImage = "whanos-${language}"

            def dslScript = """
                folder('Projects') {
                    displayName('Projects')
                    description('Available projects in Whanos')
                }

                freeStyleJob("Projects/${projectName}") {
                    displayName("${projectName}")
                    description("Linked project managed by Whanos")

                    scm {
                        git {
                            remote {
                                url("${gitUrl}")
                                credentials('${build.buildVariableResolver.resolve("GIT_CREDENTIALS_ID")}')
                            }
                            branch('*/main')
                        }
                    }

                    triggers {
                        scm('* * * * *')  // every minute
                    }

                    steps {
                        shell("echo Building ${projectName} using ${baseImage}")
                        shell("docker build -t ${projectName}:${language} --build-arg BASE_IMAGE=${baseImage} .")
                        shell('\'\'\'
                            #!/bin/bash
                            set -e
                            if [ -f whanos.yml ]; then
                                echo "Deploying \${projectName} to Kubernetes..."
                                kubectl apply -f whanos.yml
                            else
                                echo "No whanos.yml found, skipping deployment."
                            fi
                        \'\'\')
                    }
                }
            """

            def dsl = new ExecuteDslScripts()
            dsl.scriptText = dslScript
            dsl.ignoreExisting = false
            dsl.removeAction = javaposse.jobdsl.plugin.RemovedJobAction.IGNORE
            dsl.run()
        ''')
    }
}
