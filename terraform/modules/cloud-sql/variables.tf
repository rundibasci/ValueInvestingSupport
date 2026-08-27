variable "project_id" {
  type = string
}

variable "region" {
  type = string
}

variable "environment" {
  type = string
}

variable "network_id" {
  type        = string
  description = "VPC network self-link/id for private IP connectivity (from the network module)."
}

variable "private_service_connection" {
  type        = string
  description = "The network module's private_service_connection output, used only to sequence apply order (must exist before Cloud SQL)."
}

variable "tier" {
  type        = string
  description = "Cloud SQL machine tier."
  default     = "db-f1-micro"
}

variable "disk_size_gb" {
  type    = number
  default = 10
}

variable "deletion_protection" {
  type        = bool
  description = "Set true once an environment holds data worth protecting from accidental `terraform destroy`. Defaults to false so K2's verify-then-teardown cycle (plan.md Group 9) is not blocked."
  default     = false
}

variable "backup_retention_count" {
  type    = number
  default = 7
}

variable "pitr_retention_days" {
  type        = number
  description = "Point-in-time recovery window, in days. Must comfortably exceed the gap between the restore drill's captured state and its forced change."
  default     = 7
}
