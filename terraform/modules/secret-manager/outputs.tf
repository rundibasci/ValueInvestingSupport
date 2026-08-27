output "secret_ids" {
  description = "Map of short secret key -> full Secret Manager secret_id, for wiring into the Cloud Run service module."
  value       = { for k, s in google_secret_manager_secret.this : k => s.secret_id }
}
