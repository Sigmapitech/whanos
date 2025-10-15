folder('Whanos base images') {
  displayName('Whanos base images')
  description('Base images of whanos.')

  def languages = [
    'befunge',
    'c',
    'python',
    'java',
    'javascript'
  ]

  languages.each { lang ->
    freeStyleJob("Whanos base images/whanos-${lang}") {
      steps {
        shell("docker build -t whanos-foundation - < /images/Dockerfile.foundation")
        shell("docker build -t whanos-${lang} - < /images/${lang}/Dockerfile.base")
      }

      triggers {
        upstream('Build all base images')
      }
    }
  }

  freeStyleJob("Whanos base images/Build all base images") {
    steps {
      shell('echo "Building all base images"')
    }

    publishers {
      downstreamParameterized {
        trigger(languages.collect { "Whanos base images/whanos-${it}" }) {
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
  displayName('Projects')
  description('Available projets in whanos')
}
