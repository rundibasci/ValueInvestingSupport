#!/usr/bin/env bash

set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/k1-common.sh"

k1_require_command gcloud
k1_require_command docker
k1_require_command git
k1_load_config

image_uri="$(k1_image_uri)"
current_sha="$(git -C "$K1_REPO_ROOT" rev-parse HEAD)"
[[ "$K1_IMAGE_TAG" == "$current_sha" ]] || k1_die "image tag must equal current full Git SHA"

gcloud auth configure-docker "$K1_GCP_REGION-docker.pkg.dev" --quiet
docker build --platform linux/amd64 --build-arg "APP_VERSION=$K1_IMAGE_TAG" --build-arg "VCS_REF=$current_sha" \
  --file "$K1_REPO_ROOT/deploy/gcp/Dockerfile" --tag "$image_uri" "$K1_REPO_ROOT"
docker push "$image_uri"
k1_info "published immutable image: $image_uri"
