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
