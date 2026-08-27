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
  type        = string
  description = "Full Artifact Registry image reference, tagged by immutable commit SHA/digest. Never a mutable tag like \"latest\"."
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
  description = "Each entry maps a container env var name to a Secret Manager secret_id (latest version). Built by the environment root from the secret-manager module's outputs."
  default     = []
}

variable "max_instances" {
  type        = number
  description = "Starts at 1 in every environment; raise only after the Cloud Run Jobs migration (plan.md Group 5) is verified for this environment."
  default     = 1
}

variable "cpu" {
  type    = string
  default = "1"
}

variable "memory" {
  type    = string
  default = "1Gi"
}

variable "custom_domain" {
  type        = string
  description = "Custom HTTPS domain for this environment. Leave empty for dev (default run.app URL only); set for staging."
  default     = ""
}
