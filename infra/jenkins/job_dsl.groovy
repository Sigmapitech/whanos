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
