# K2 secret-manager module — creates secret CONTAINERS only. Values are
# created and rotated outside Terraform state (operator-controlled,
# non-logging mechanism, same as K1's pattern); this module never accepts
# or writes a secret value.

resource "google_secret_manager_secret" "this" {
  for_each  = toset(var.secret_keys)
  project   = var.project_id
  secret_id = "vis-k2-${var.environment}-${each.value}"

  replication {
    auto {}
  }
}

resource "google_secret_manager_secret_iam_member" "runtime_accessor" {
  for_each  = google_secret_manager_secret.this
  project   = var.project_id
  secret_id = each.value.secret_id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${var.runtime_service_account_email}"
}
