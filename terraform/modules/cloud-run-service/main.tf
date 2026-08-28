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
      # Without this, Spring Boot never exposes the /actuator/health/{liveness,readiness}
      # sub-paths at all (plain 404), which fails the Cloud Run startup probe
      # unconditionally — this is not optional. Matches K1's proven fix
      # (scripts/gcp/k1-deploy.sh MANAGEMENT_ENDPOINT_HEALTH_PROBES_ENABLED=true).
      env {
        name  = "MANAGEMENT_ENDPOINT_HEALTH_PROBES_ENABLED"
        value = "true"
      }
      # No SMTP secrets/config exist for K2 yet; disable the mail health
      # indicator so it doesn't mark the app DOWN, matching K1's fix.
      env {
        name  = "MANAGEMENT_HEALTH_MAIL_ENABLED"
        value = "false"
      }
      # No run.googleapis.com/cloudsql-instances annotation/volume needed:
      # the app connects via the Cloud SQL Java Connector
      # (postgres-socket-factory in backend/pom.xml) through the Admin API
      # using the runtime SA's roles/cloudsql.client grant, not a Unix-socket
      # sidecar — see the same note in modules/cloud-run-job/main.tf, where
      # the equivalent annotation is outright rejected by the Jobs v2 API.
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

      # Google sign-in (Group J): plain (non-secret) callback URLs, paired
      # with the GOOGLE_CLIENT_ID/GOOGLE_CLIENT_SECRET secrets above.
      # GoogleOAuthConfig only registers the Google client when both secrets
      # are non-empty, so leaving these two vars unset (default "") keeps
      # Google sign-in disabled without any extra flag.
      dynamic "env" {
        for_each = var.google_oauth2_redirect_uri != "" ? [var.google_oauth2_redirect_uri] : []
        content {
          name  = "GOOGLE_REDIRECT_URI"
          value = env.value
        }
      }
      dynamic "env" {
        for_each = var.google_oauth2_frontend_callback != "" ? [var.google_oauth2_frontend_callback] : []
        content {
          name  = "GOOGLE_FRONTEND_CALLBACK"
          value = env.value
        }
      }

      # AI Investment Thesis (Group TA). Plain env vars, not secrets: project id, region,
      # and a public model identifier carry no confidential value. Auth is via the runtime
      # service account's attached identity (roles/aiplatform.user, granted in the iam
      # module), never a key file. thesis_agent_enabled defaults false in every environment.
      env {
        name  = "THESIS_AGENT_ENABLED"
        value = tostring(var.thesis_agent_enabled)
      }
      dynamic "env" {
        for_each = var.google_cloud_project != "" ? [var.google_cloud_project] : []
        content {
          name  = "GOOGLE_CLOUD_PROJECT"
          value = env.value
        }
      }
      dynamic "env" {
        for_each = var.vertex_ai_location != "" ? [var.vertex_ai_location] : []
        content {
          name  = "VERTEX_AI_LOCATION"
          value = env.value
        }
      }
      dynamic "env" {
        for_each = var.gemini_model_id != "" ? [var.gemini_model_id] : []
        content {
          name  = "GEMINI_MODEL_ID"
          value = env.value
        }
      }

      # Startup probe deliberately targets liveness, not readiness: K1
      # found the readiness group includes a custom ingestionJobs health
      # indicator that reports DOWN on a fresh database (no ingestion job
      # has run yet), which would permanently fail the startup probe on
      # every new environment. Liveness only confirms the JVM/web server
      # is up. Job health remains visible separately via /actuator/health,
      # not hidden — see specs/2026-08-25-k1-stakeholder-cloud-deployment/validation.md.
      startup_probe {
        http_get {
          path = "/actuator/health/liveness"
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
