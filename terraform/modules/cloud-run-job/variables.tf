variable "project_id" {
  type = string
}

variable "region" {
  type = string
}

variable "environment" {
  type = string
}

variable "image" {
  type = string
}

variable "runtime_service_account_email" {
  type = string
}

variable "network_name" {
  type = string
}

variable "subnet_name" {
  type = string
}

variable "cloudsql_connection_name" {
  type = string
}

variable "database_name" {
  type    = string
  default = "vis"
}

variable "redis_host" {
  type = string
}

variable "redis_port" {
  type = number
}

variable "secret_env_bindings" {
  type = list(object({
    env_var_name = string
    secret_id    = string
  }))
  default = []
}

variable "job_keys" {
  type        = set(string)
  description = "CloudRunJob#jobKey() values; one Cloud Run Job resource per entry. Must match exactly the keys registered in backend/src/main/java/it/mazzoni/vis/jobs/*.java."
  default = [
    "bulk-profile-sync",
    "bulk-fundamentals-sync",
    "bulk-ratios-sync",
    "bulk-dcf-sync",
    "quote-refresh",
    "dividend-update",
    "insider-trading",
    "alert-detection",
  ]
}

variable "job_timeout" {
  type    = string
  default = "1800s"
}

variable "cpu" {
  type    = string
  default = "1"
}

variable "memory" {
  type    = string
  default = "1Gi"
}
