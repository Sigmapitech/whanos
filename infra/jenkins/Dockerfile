FROM jenkins/jenkins:lts-jdk17

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


# Copy plugin list and install them
COPY plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.txt

# (Optional) Configuration as Code
COPY jenkins.yaml /usr/share/jenkins/ref/jenkins.yaml
ENV CASC_JENKINS_CONFIG=/usr/share/jenkins/ref/jenkins.yaml
