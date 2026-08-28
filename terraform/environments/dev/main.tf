# K2 "dev" environment root. See specs/2026-08-26-k2-production-shaped-gcp-platform/.
# Apply order is handled by Terraform's dependency graph via module
# references below; no manual ordering is required beyond running
# terraform/bootstrap/ first.

module "network" {
  source      = "../../modules/network"
  project_id  = var.project_id
  region      = var.region
  environment = var.environment
  subnet_cidr = var.subnet_cidr
}

module "iam" {
  source                      = "../../modules/iam"
  project_id                  = var.project_id
  environment                 = var.environment
  workload_identity_pool_name = var.workload_identity_pool_name
  github_deploy_ref           = var.github_deploy_ref
}

module "secret_manager" {
  source                        = "../../modules/secret-manager"
  project_id                    = var.project_id
  environment                   = var.environment
  runtime_service_account_email = module.iam.runtime_email
}

module "cloud_sql" {
  source                     = "../../modules/cloud-sql"
  project_id                 = var.project_id
  region                     = var.region
  environment                = var.environment
  network_id                 = module.network.network_id
  private_service_connection = module.network.private_service_connection
}

module "redis" {
  source                     = "../../modules/redis"
  project_id                 = var.project_id
  region                     = var.region
  environment                = var.environment
  network_id                 = module.network.network_id
  private_service_connection = module.network.private_service_connection
}

# Short secret key -> the container env var name the Spring app reads.
locals {
  secret_env_var_names = {
    "fmp-api-key"          = "FMP_API_KEY"
    "database-username"    = "DATABASE_USERNAME"
    "database-password"    = "DATABASE_PASSWORD"
    "jwt-private-key"      = "JWT_PRIVATE_KEY"
    "jwt-public-key"       = "JWT_PUBLIC_KEY"
    "google-client-id"     = "GOOGLE_CLIENT_ID"
    "google-client-secret" = "GOOGLE_CLIENT_SECRET"
  }

  secret_env_bindings = [
    for key, env_var in local.secret_env_var_names : {
      env_var_name = env_var
      secret_id    = module.secret_manager.secret_ids[key]
    }
  ]

  # Cloud Run v2's default run.app URL includes a random per-service hash
  # component (confirmed live for vis-k2-dev-api: ...-ughjapmueq-ew.a.run.app),
  # not the deterministic projectNumber.region format assumed on first
  # write of this file — so it can't be computed before the service
  # exists, and the service can't consume its own computed .url as an
  # input (self-reference). Hardcoded from the real `terraform output
  # cloud_run_url` / `gcloud run services describe` value after the first
  # apply. If the service is ever destroyed and recreated, re-check this
  # value — Cloud Run does not guarantee the same hash on a fresh create —
  # and update both this local and the Google Cloud Console OAuth client's
  # authorized redirect URI to match.
  google_oauth2_base_url          = "https://vis-k2-${var.environment}-api-ughjapmueq-ew.a.run.app"
  google_oauth2_redirect_uri      = "${local.google_oauth2_base_url}/login/oauth2/code/google"
  google_oauth2_frontend_callback = "${local.google_oauth2_base_url}/auth/oauth2/callback"

  # Cloud Scheduler uses standard 5-field Unix cron; the Spring app's
  # 6-field cron (application.yml app.jobs.cron.*) has a leading seconds
  # field that must be dropped. Keep this mapping in sync with that file.
  job_cron = {
    "bulk-profile-sync"      = "0 2 * * *"
    "bulk-fundamentals-sync" = "0 3 * * *"
    "bulk-ratios-sync"       = "30 3 * * *"
    "bulk-dcf-sync"          = "0 4 * * *"
    "quote-refresh"          = "*/15 * * * *"
    "dividend-update"        = "0 6 * * *"
    "insider-trading"        = "0 * * * *"
    "alert-detection"        = "20 0 * * *"
  }
}

module "cloud_run_service" {
  source                          = "../../modules/cloud-run-service"
  project_id                      = var.project_id
  region                          = var.region
  environment                     = var.environment
  image                           = var.image
  runtime_service_account_email   = module.iam.runtime_email
  network_name                    = module.network.network_name
  subnet_name                     = module.network.subnet_name
  cloudsql_connection_name        = module.cloud_sql.connection_name
  database_name                   = module.cloud_sql.database_name
  redis_host                      = module.redis.host
  redis_port                      = module.redis.port
  secret_env_bindings             = local.secret_env_bindings
  max_instances                   = var.max_instances
  custom_domain                   = "" # dev keeps its default run.app URL
  google_oauth2_redirect_uri      = local.google_oauth2_redirect_uri
  google_oauth2_frontend_callback = local.google_oauth2_frontend_callback
  thesis_agent_enabled            = var.thesis_agent_enabled
  google_cloud_project            = var.project_id
  vertex_ai_location              = var.vertex_ai_location
  gemini_model_id                 = var.gemini_model_id
}

module "cloud_run_job" {
  source                        = "../../modules/cloud-run-job"
  project_id                    = var.project_id
  region                        = var.region
  environment                   = var.environment
  image                         = var.image
  runtime_service_account_email = module.iam.runtime_email
  network_name                  = module.network.network_name
  subnet_name                   = module.network.subnet_name
  cloudsql_connection_name      = module.cloud_sql.connection_name
  database_name                 = module.cloud_sql.database_name
  redis_host                    = module.redis.host
  redis_port                    = module.redis.port
  secret_env_bindings           = local.secret_env_bindings
  job_keys                      = keys(local.job_cron)
}

module "cloud_scheduler" {
  source                        = "../../modules/cloud-scheduler"
  project_id                    = var.project_id
  region                        = var.region
  environment                   = var.environment
  runtime_service_account_email = module.iam.runtime_email
  job_triggers = {
    for key, cron in local.job_cron : key => {
      job_name = module.cloud_run_job.job_names[key]
      cron     = cron
    }
  }
}

module "monitoring" {
  source             = "../../modules/monitoring"
  project_id         = var.project_id
  region             = var.region
  environment        = var.environment
  service_name       = module.cloud_run_service.service_name
  service_hostname   = replace(module.cloud_run_service.url, "https://", "")
  notification_email = var.notification_email
}
