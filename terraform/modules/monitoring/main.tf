# K2 monitoring module — uptime check + alert policies per environment,
# extending K1's notification-channel pattern.

resource "google_monitoring_notification_channel" "email" {
  count        = var.notification_email != "" ? 1 : 0
  project      = var.project_id
  display_name = "VIS K2 ${title(var.environment)} alerts"
  type         = "email"
  labels = {
    email_address = var.notification_email
  }
}

resource "google_monitoring_uptime_check_config" "api_health" {
  project      = var.project_id
  display_name = "vis-k2-${var.environment}-api-liveness"
  timeout      = "10s"
  period       = "60s"

  http_check {
    path         = "/actuator/health/liveness"
    port         = 443
    use_ssl      = true
    validate_ssl = true
  }

  monitored_resource {
    type = "uptime_url"
    labels = {
      project_id = var.project_id
      host       = var.service_hostname
    }
  }
}

resource "google_monitoring_alert_policy" "uptime_failure" {
  project               = var.project_id
  display_name          = "vis-k2-${var.environment}-uptime-failure"
  combiner              = "OR"
  notification_channels = [for c in google_monitoring_notification_channel.email : c.id]

  conditions {
    display_name = "Liveness uptime check failing"
    condition_threshold {
      filter          = "resource.type=\"uptime_url\" AND metric.type=\"monitoring.googleapis.com/uptime_check/check_passed\" AND metric.label.check_id=\"${google_monitoring_uptime_check_config.api_health.uptime_check_id}\""
      comparison      = "COMPARISON_LT"
      threshold_value = 1
      duration        = "180s"
      aggregations {
        alignment_period   = "60s"
        per_series_aligner = "ALIGN_FRACTION_TRUE"
      }
    }
  }

  alert_strategy {
    auto_close = "1800s"
  }
}

resource "google_monitoring_alert_policy" "error_rate" {
  project               = var.project_id
  display_name          = "vis-k2-${var.environment}-api-error-rate"
  combiner              = "OR"
  notification_channels = [for c in google_monitoring_notification_channel.email : c.id]

  conditions {
    display_name = "Cloud Run 5xx rate elevated"
    condition_threshold {
      filter          = "resource.type=\"cloud_run_revision\" AND resource.labels.service_name=\"${var.service_name}\" AND metric.type=\"run.googleapis.com/request_count\" AND metric.label.response_code_class=\"5xx\""
      comparison      = "COMPARISON_GT"
      threshold_value = var.error_rate_threshold
      duration        = "300s"
      aggregations {
        alignment_period   = "300s"
        per_series_aligner = "ALIGN_RATE"
      }
    }
  }

  alert_strategy {
    auto_close = "1800s"
  }
}

resource "google_monitoring_alert_policy" "job_failure" {
  project               = var.project_id
  display_name          = "vis-k2-${var.environment}-cloud-run-job-failure"
  combiner              = "OR"
  notification_channels = [for c in google_monitoring_notification_channel.email : c.id]

  conditions {
    display_name = "Cloud Run Jobs execution failed"
    condition_threshold {
      filter          = "resource.type=\"cloud_run_job\" AND metric.type=\"run.googleapis.com/job/completed_execution_count\" AND metric.label.result=\"failed\" AND resource.labels.location=\"${var.region}\""
      comparison      = "COMPARISON_GT"
      threshold_value = 0
      duration        = "60s"
      aggregations {
        alignment_period   = "300s"
        per_series_aligner = "ALIGN_SUM"
      }
    }
  }

  alert_strategy {
    auto_close = "1800s"
  }
}
