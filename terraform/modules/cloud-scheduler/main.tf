# K2 Cloud Scheduler module — one HTTP+OIDC trigger per Cloud Run Job,
# preserving each job's existing cron cadence from
# backend/src/main/resources/application.yml (app.jobs.cron.*).

resource "google_cloud_scheduler_job" "this" {
  for_each  = var.job_triggers
  project   = var.project_id
  region    = var.region
  name      = "vis-k2-${var.environment}-scheduler-${each.key}"
  schedule  = each.value.cron
  time_zone = "UTC"

  attempt_deadline = var.job_timeout

  retry_config {
    retry_count = 2
  }

  http_target {
    uri         = "https://${var.region}-run.googleapis.com/apis/run.googleapis.com/v1/namespaces/${var.project_id}/jobs/${each.value.job_name}:run"
    http_method = "POST"

    oauth_token {
      service_account_email = var.runtime_service_account_email
    }
  }
}
