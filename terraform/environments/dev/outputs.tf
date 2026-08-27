output "cloud_run_url" {
  value = module.cloud_run_service.url
}

output "deployer_service_account_email" {
  value = module.iam.deployer_email
}

output "runtime_service_account_email" {
  value = module.iam.runtime_email
}

output "cloud_sql_connection_name" {
  value = module.cloud_sql.connection_name
}
