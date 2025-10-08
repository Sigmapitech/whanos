#!/usr/bin/env bash
set -euo pipefail

IMAGES_DIR="./images"

for dir in "$IMAGES_DIR"/*/; do
    [[ -f "${dir}Dockerfile.base" ]] || continue

    lang=$(basename "$dir")
    echo "Building base image for $lang..."
    docker build -t "whanos-${lang}" - < "${dir}Dockerfile.base"
    echo "Built whanos-${lang}"
    echo
done
