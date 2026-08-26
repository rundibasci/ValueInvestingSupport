output "state_bucket_name" {
  value = google_storage_bucket.terraform_state.name
}

output "workload_identity_pool_name" {
  description = "Pass this into each environment's iam module as workload_identity_pool_name."
  value       = google_iam_workload_identity_pool.github.name
}

output "workload_identity_provider_name" {
  description = "Full provider resource name for GitHub Actions' google-github-actions/auth workload_identity_provider input."
  value       = google_iam_workload_identity_pool_provider.github.name
}

output "artifact_registry_repository" {
  description = "Full repository id, for building the image reference: {region}-docker.pkg.dev/{project}/vis-k2/vis-backend"
  value       = google_artifact_registry_repository.vis_k2.repository_id
}
