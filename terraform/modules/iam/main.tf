# K2 IAM module — one deployer identity (CI/CD) and one runtime identity
# (Cloud Run) per environment, each least-privilege. No binding in this
# module ever grants roles/owner or roles/editor.

resource "google_service_account" "deployer" {
  project      = var.project_id
  account_id   = "vis-k2-${var.environment}-deployer"
  display_name = "VIS K2 ${title(var.environment)} CI/CD deployer"
}

resource "google_service_account" "runtime" {
  project      = var.project_id
  account_id   = "vis-k2-${var.environment}-runtime"
  display_name = "VIS K2 ${title(var.environment)} Cloud Run runtime"
}

# --- Runtime identity: only what the application needs at request time ---

resource "google_project_iam_member" "runtime_cloudsql_client" {
  project = var.project_id
  role    = "roles/cloudsql.client"
  member  = "serviceAccount:${google_service_account.runtime.email}"
}

resource "google_project_iam_member" "runtime_logging_writer" {
  project = var.project_id
  role    = "roles/logging.logWriter"
  member  = "serviceAccount:${google_service_account.runtime.email}"
}

resource "google_project_iam_member" "runtime_metric_writer" {
  project = var.project_id
  role    = "roles/monitoring.metricWriter"
  member  = "serviceAccount:${google_service_account.runtime.email}"
}

# Per-secret accessor bindings are granted by the secret-manager module,
# not here, so this module never needs to know the secret list.

# --- Deployer identity: only what Terraform/CI needs to manage this environment's resources ---

resource "google_project_iam_member" "deployer_run_admin" {
  project = var.project_id
  role    = "roles/run.admin"
  member  = "serviceAccount:${google_service_account.deployer.email}"
}

resource "google_project_iam_member" "deployer_sa_user" {
  project = var.project_id
  role    = "roles/iam.serviceAccountUser"
  member  = "serviceAccount:${google_service_account.deployer.email}"
}

resource "google_project_iam_member" "deployer_artifact_writer" {
  project = var.project_id
  role    = "roles/artifactregistry.writer"
  member  = "serviceAccount:${google_service_account.deployer.email}"
}

resource "google_project_iam_member" "deployer_cloudsql_admin" {
  project = var.project_id
  role    = "roles/cloudsql.admin"
  member  = "serviceAccount:${google_service_account.deployer.email}"
}

resource "google_project_iam_member" "deployer_redis_admin" {
  project = var.project_id
  role    = "roles/redis.admin"
  member  = "serviceAccount:${google_service_account.deployer.email}"
}

resource "google_project_iam_member" "deployer_secret_admin" {
  project = var.project_id
  role    = "roles/secretmanager.admin"
  member  = "serviceAccount:${google_service_account.deployer.email}"
}

resource "google_project_iam_member" "deployer_compute_network_admin" {
  project = var.project_id
  role    = "roles/compute.networkAdmin"
  member  = "serviceAccount:${google_service_account.deployer.email}"
}

resource "google_project_iam_member" "deployer_monitoring_admin" {
  project = var.project_id
  role    = "roles/monitoring.editor"
  member  = "serviceAccount:${google_service_account.deployer.email}"
}

resource "google_project_iam_member" "deployer_scheduler_admin" {
  project = var.project_id
  role    = "roles/cloudscheduler.admin"
  member  = "serviceAccount:${google_service_account.deployer.email}"
}

resource "google_project_iam_member" "deployer_service_usage_consumer" {
  project = var.project_id
  role    = "roles/serviceusage.serviceUsageConsumer"
  member  = "serviceAccount:${google_service_account.deployer.email}"
}

# --- Workload Identity Federation binding ---
#
# The WIF pool/provider itself is a one-time, project-level resource created
# in terraform/bootstrap/ (shared across environments), not here — creating
# it per-environment would collide on the pool's project-scoped ID. This
# module only binds this environment's deployer identity to it, scoped so
# only workflow runs on this environment's deploy ref can impersonate it.
resource "google_service_account_iam_member" "github_workload_identity_binding" {
  service_account_id = google_service_account.deployer.name
  role               = "roles/iam.workloadIdentityUser"
  member             = "principalSet://iam.googleapis.com/${var.workload_identity_pool_name}/attribute.ref/${var.github_deploy_ref}"
}
