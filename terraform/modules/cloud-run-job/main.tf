# K2 Cloud Run Jobs module — one Job resource per CloudRunJob key
# (backend/src/main/java/it/mazzoni/vis/jobs/*.java implementing
# it.mazzoni.vis.jobs.CloudRunJob), reusing the same application image with
# a job-specific `--job=<key>` argument. Job logic, JobRunLogger
# bookkeeping, and idempotency are identical to the former in-process
# `@Scheduled` path — see it.mazzoni.vis.jobs.CloudRunJobEntryPoint.

resource "google_cloud_run_v2_job" "this" {
  for_each = var.job_keys
  project  = var.project_id
  name     = "vis-k2-${var.environment}-job-${each.value}"
  location = var.region

  template {
    template {
      service_account = var.runtime_service_account_email
      max_retries     = 2
      timeout         = var.job_timeout

      vpc_access {
        network_interfaces {
          network    = var.network_name
          subnetwork = var.subnet_name
        }
        egress = "PRIVATE_RANGES_ONLY"
      }

      containers {
        image = var.image
        args  = ["--job=${each.value}"]

        resources {
          limits = {
            cpu    = var.cpu
            memory = var.memory
          }
        }

        env {
          name  = "SPRING_PROFILES_ACTIVE"
          value = "prod"
        }
        env {
          name  = "APP_JOBS_SCHEDULING_ENABLED"
          value = "false" # no need for the scheduler thread pool in a one-shot process
        }
        env {
          name  = "MARKET_DATA_SOURCE"
          value = "fmp"
        }
        # No run.googleapis.com/cloudsql-instances annotation/volume needed:
        # the app connects via the Cloud SQL Java Connector
        # (postgres-socket-factory in backend/pom.xml), which reaches Cloud
        # SQL through the Admin API using the runtime SA's
        # roles/cloudsql.client grant — not a Unix-socket sidecar. That
        # system annotation is also rejected outright by the Cloud Run Jobs
        # v2 API ("system annotations are not supported").
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
      }
    }
  }
}

# Cloud Scheduler invokes each Job as the runtime service account (OIDC);
# grant exactly that, per Job, nothing broader.
resource "google_cloud_run_v2_job_iam_member" "scheduler_invoker" {
  for_each = google_cloud_run_v2_job.this
  project  = var.project_id
  location = var.region
  name     = each.value.name
  role     = "roles/run.invoker"
  member   = "serviceAccount:${var.runtime_service_account_email}"
}
