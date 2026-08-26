#!/usr/bin/env bash

set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/k1-common.sh"

k1_require_command gcloud
k1_load_config

target_revision="${1:-}"
[[ -n "$target_revision" ]] || k1_die "usage: $0 <known-good-revision>"
[[ "${K1_CONFIRM_TRAFFIC_CHANGE:-}" == "YES" ]] || \
  k1_die "set K1_CONFIRM_TRAFFIC_CHANGE=YES after confirming the target revision"

known="$(gcloud run revisions list --service "$K1_CLOUD_RUN_SERVICE" --region "$K1_GCP_REGION" \
  --project "$K1_GCP_PROJECT_ID" --filter="metadata.name=$target_revision" --format='value(metadata.name)')"
[[ "$known" == "$target_revision" ]] || k1_die "revision not found for this service: $target_revision"

gcloud run services update-traffic "$K1_CLOUD_RUN_SERVICE" --region "$K1_GCP_REGION" \
  --project "$K1_GCP_PROJECT_ID" --to-revisions "$target_revision=100"
k1_info "traffic now targets revision=$target_revision; execute health/login/core smoke checks"
