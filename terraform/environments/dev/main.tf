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
  source                       = "../../modules/iam"
  project_id                   = var.project_id
  environment                  = var.environment
  workload_identity_pool_name  = var.workload_identity_pool_name
  github_deploy_ref            = var.github_deploy_ref
}

module "secret_manager" {
  source                         = "../../modules/secret-manager"
  project_id                     = var.project_id
  environment                    = var.environment
  runtime_service_account_email  = module.iam.runtime_email
}

module "cloud_sql" {
  source                      = "../../modules/cloud-sql"
  project_id                  = var.project_id
  region                      = var.region
  environment                 = var.environment
  network_id                  = module.network.network_id
  private_service_connection  = module.network.private_service_connection
}

module "redis" {
  source                      = "../../modules/redis"
  project_id                  = var.project_id
  region                      = var.region
  environment                 = var.environment
  network_id                  = module.network.network_id
  private_service_connection  = module.network.private_service_connection
}

# Short secret key -> the container env var name the Spring app reads.
locals {
  secret_env_var_names = {
    "fmp-api-key"         = "FMP_API_KEY"
    "database-username"   = "DATABASE_USERNAME"
    "database-password"   = "DATABASE_PASSWORD"
    "jwt-private-key"     = "JWT_PRIVATE_KEY"
    "jwt-public-key"      = "JWT_PUBLIC_KEY"
  }

  secret_env_bindings = [
    for key, env_var in local.secret_env_var_names : {
      env_var_name = env_var
      secret_id    = module.secret_manager.secret_ids[key]
    }
  ]

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
}

module "cloud_run_job" {
  source                          = "../../modules/cloud-run-job"
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
  job_keys                        = keys(local.job_cron)
}

module "cloud_scheduler" {
  source                          = "../../modules/cloud-scheduler"
  project_id                      = var.project_id
  region                          = var.region
  environment                     = var.environment
  runtime_service_account_email   = module.iam.runtime_email
  job_triggers = {
    for key, cron in local.job_cron : key => {
      job_name = module.cloud_run_job.job_names[key]
      cron     = cron
    }
  }
}

module "monitoring" {
  source              = "../../modules/monitoring"
  project_id          = var.project_id
  region              = var.region
  environment         = var.environment
  service_name        = module.cloud_run_service.service_name
  service_hostname    = replace(module.cloud_run_service.url, "https://", "")
  notification_email  = var.notification_email
}
