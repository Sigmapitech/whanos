FROM jenkins/jenkins:lts

USER root

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

COPY plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.txt

ENV CASC_JENKINS_CONFIG=/usr/share/jenkins/ref/jenkins.yaml
COPY --chown=jenkins:jenkins \
  ./job_dsl.groovy           \
  /usr/share/jenkins/ref/init.groovy.d/job_dsl.groovy
