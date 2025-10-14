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
        shell("docker build -t whanos-${lang} - < /images/${lang}/Dockerfile.base")
        shell("docker tag whanos-${lang} localhost:5000/whanos-${lang}")
        shell("docker push localhost:5000/whanos-${lang}")
        shell("docker pull localhost:5000/whanos-${lang}")
        shell("docker rmi whanos-${lang}")
      }

      triggers {
        upstream('Build all base images')
      }
    }
  }
}

folder('Projects') {
  displayName('Projects')
  description('Available projets in whanos')
}
