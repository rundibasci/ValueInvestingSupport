variable "project_id" {
  type = string
}

variable "region" {
  type = string
}

variable "environment" {
  type = string
}

variable "service_name" {
  type        = string
  description = "Cloud Run service name (from the cloud-run-service module) for the error-rate alert filter."
}

variable "service_hostname" {
  type        = string
  description = "Hostname only (no scheme), e.g. vis-k2-staging-api-xxxxx-ew.a.run.app or the custom domain, for the uptime check."
}

variable "notification_email" {
  type        = string
  description = "Alert destination email. Leave empty to skip creating a notification channel (alerts still fire but have nowhere to go — not recommended)."
  default     = ""
}

variable "error_rate_threshold" {
  type        = number
  description = "5xx requests per second (post-alignment rate) that triggers the error-rate alert."
  default     = 0.5
}
