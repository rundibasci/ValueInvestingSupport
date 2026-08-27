output "deployer_email" {
  value = google_service_account.deployer.email
}

output "deployer_name" {
  value = google_service_account.deployer.name
}

output "runtime_email" {
  value = google_service_account.runtime.email
}

output "runtime_name" {
  value = google_service_account.runtime.name
}
