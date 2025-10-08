#!/usr/bin/env bash
set -e

JENKINS_HOME="./jenkins_home"
CONTAINER_NAME="jenkins"

echo "🧹 Cleaning Jenkins home and fixing permissions..."
rm -rf "$JENKINS_HOME"/*
mkdir -p "$JENKINS_HOME"
# Ensure the volume is writable by Jenkins user (UID 1000)
sudo chown -R 1000:1000 "$JENKINS_HOME"

echo "🚀 Building Jenkins image..."
docker compose build

echo "🧱 Starting Jenkins..."
docker compose up -d

echo "⏳ Waiting a few seconds for Jenkins to initialize..."
sleep 10

echo "🔑 Jenkins initial admin password:"
docker exec "$CONTAINER_NAME" cat /var/jenkins_home/secrets/initialAdminPassword || true

echo
echo "✅ Jenkins is running at: http://localhost:8080"
