variable "project_id" {
  type = string
}

variable "region" {
  type = string
}

variable "environment" {
  type = string
}

variable "runtime_service_account_email" {
  type        = string
  description = "Must already hold roles/run.invoker on every targeted Job (granted by the cloud-run-job module)."
}

variable "job_triggers" {
  description = "Map of job key -> { job_name = <Cloud Run Job resource name>, cron = <schedule> }. Cadence mirrors backend/src/main/resources/application.yml app.jobs.cron.*."
  type = map(object({
    job_name = string
    cron     = string
  }))
}

variable "job_timeout" {
  type    = string
  default = "1800s"
}
