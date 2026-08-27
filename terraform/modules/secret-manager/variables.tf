variable "project_id" {
  type = string
}

variable "environment" {
  type = string
}

variable "runtime_service_account_email" {
  type        = string
  description = "Email of the Cloud Run runtime service account granted accessor on every secret here."
}

variable "secret_keys" {
  type        = list(string)
  description = "Short secret identifiers, e.g. [\"fmp-api-key\", \"database-username\", \"database-password\", \"jwt-private-key\", \"jwt-public-key\"]. SMTP keys are optional and included only if SMTP delivery is configured for this environment."
  default = [
    "fmp-api-key",
    "database-username",
    "database-password",
    "jwt-private-key",
    "jwt-public-key",
  ]
}
