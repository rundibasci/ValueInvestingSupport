#!/usr/bin/env bash

set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/k1-common.sh"

k1_require_command gcloud
k1_require_command docker
k1_require_command git
k1_require_command curl
k1_load_config

account="$(gcloud auth list --filter=status:ACTIVE --format='value(account)' | head -n 1)"
[[ -n "$account" ]] || k1_die "no active gcloud account"
gcloud projects describe "$K1_GCP_PROJECT_ID" >/dev/null

current_sha="$(git -C "$K1_REPO_ROOT" rev-parse HEAD)"
[[ "$K1_IMAGE_TAG" == "$current_sha" ]] || \
  k1_die "K1_IMAGE_TAG must equal the full current Git SHA ($current_sha)"

[[ -z "$(git -C "$K1_REPO_ROOT" status --porcelain --untracked-files=no)" ]] || \
  k1_die "tracked working-tree changes exist; publish only a reviewed commit"

k1_info "preflight passed"
k1_info "account=$account"
k1_info "project=$K1_GCP_PROJECT_ID region=$K1_GCP_REGION"
k1_info "image=$(k1_image_uri)"
k1_info "invoker-mode=$K1_INVOKER_MODE max-instances=$K1_CLOUD_RUN_MAX_INSTANCES"
k1_info "no GCP resources were changed"
