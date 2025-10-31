FROM jenkins/jenkins:lts

USER root
ARG DOCKER_GID=998
RUN groupadd -for -g $DOCKER_GID docker && usermod -aG docker jenkins

# Install Docker CLI + kubectl
RUN apt-get update && \
    apt-get install -y docker.io curl && \
    curl -LO "https://storage.googleapis.com/kubernetes-release/release/$(curl -s https://storage.googleapis.com/kubernetes-release/release/stable.txt)/bin/linux/amd64/kubectl" && \
    chmod +x kubectl && mv kubectl /usr/local/bin/ && \
    apt-get clean

# Jenkins setup
USER root
RUN mkdir -p /var/jenkins_home && chown -R jenkins:jenkins /var/jenkins_home
USER jenkins

COPY infra/jenkins/plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.txt

ENV CASC_JENKINS_CONFIG=/usr/share/jenkins/ref/jenkins.yaml
COPY script /usr/share/jenkins/ref/script

COPY --chown=jenkins:jenkins \
  ./infra/jenkins/job_dsl.groovy           \
  /usr/share/jenkins/ref/init.groovy.d/job_dsl.groovy
