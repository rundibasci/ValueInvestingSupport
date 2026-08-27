variable "project_id" {
  type        = string
  description = "GCP project hosting this environment's identities."
}

variable "environment" {
  type        = string
  description = "Environment name (dev, staging). Used in resource naming only."
}

variable "workload_identity_pool_name" {
  type        = string
  description = "Full resource name of the shared WIF pool from terraform/bootstrap/ (projects/.../workloadIdentityPools/vis-k2-github-pool)."
}

variable "github_deploy_ref" {
  type        = string
  description = "The git ref (e.g. refs/heads/main) whose GitHub Actions runs may impersonate this environment's deployer identity."
}
