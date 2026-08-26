# K2 Cloud Run API service module — one Cloud Run v2 service per
# environment. max_instances starts at 1 in every environment (see the K2
# spec, Decision 6) and is only raised once Group 5's Cloud Run Jobs
# migration is verified for that environment.

resource "google_cloud_run_v2_service" "this" {
  project  = var.project_id
  name     = "vis-k2-${var.environment}-api"
  location = var.region
  ingress  = "INGRESS_TRAFFIC_ALL"

  template {
    service_account                  = var.runtime_service_account_email
    max_instance_request_concurrency = 80

    scaling {
      min_instance_count = 0
      max_instance_count = var.max_instances
    }

    vpc_access {
      network_interfaces {
        network    = var.network_name
        subnetwork = var.subnet_name
      }
      egress = "PRIVATE_RANGES_ONLY"
    }

    containers {
      image = var.image

      resources {
        limits = {
          cpu    = var.cpu
          memory = var.memory
        }
      }

      ports {
        container_port = 8080
      }

      env {
        name  = "SPRING_PROFILES_ACTIVE"
        value = "prod"
      }
      env {
        name  = "APP_DEPLOYMENT_MODE"
        value = "k2"
      }
      env {
        name  = "APP_JOBS_SCHEDULING_ENABLED"
        value = "false" # SchedulerConfig never loads; every background task runs via Cloud Run Jobs instead
      }
      env {
        name  = "APP_DECLARED_MAX_INSTANCES"
        value = tostring(var.max_instances)
      }
      env {
        name  = "MARKET_DATA_SOURCE"
        value = "fmp"
      }
      env {
        name  = "DATABASE_URL"
        value = "jdbc:postgresql:///${var.database_name}?cloudSqlInstance=${var.cloudsql_connection_name}&socketFactory=com.google.cloud.sql.postgres.SocketFactory"
      }
      env {
        name  = "REDIS_HOST"
        value = var.redis_host
      }
      env {
        name  = "REDIS_PORT"
        value = tostring(var.redis_port)
      }

      dynamic "env" {
        for_each = var.secret_env_bindings
        content {
          name = env.value.env_var_name
          value_source {
            secret_key_ref {
              secret  = env.value.secret_id
              version = "latest"
            }
          }
        }
      }

      startup_probe {
        http_get {
          path = "/actuator/health/readiness"
        }
        initial_delay_seconds = 5
        period_seconds        = 5
        failure_threshold     = 12
      }

      liveness_probe {
        http_get {
          path = "/actuator/health/liveness"
        }
        period_seconds = 15
      }
    }

    annotations = {
      "run.googleapis.com/cloudsql-instances" = var.cloudsql_connection_name
    }
  }

  traffic {
    type    = "TRAFFIC_TARGET_ALLOCATION_TYPE_LATEST"
    percent = 100
  }
}

# Staging-only custom HTTPS domain (K2 spec Decision 7: dev keeps its
# default run.app URL).
resource "google_cloud_run_domain_mapping" "staging" {
  count    = var.custom_domain != "" ? 1 : 0
  project  = var.project_id
  location = var.region
  name     = var.custom_domain

  metadata {
    namespace = var.project_id
  }

  spec {
    route_name = google_cloud_run_v2_service.this.name
  }
}
