variable "project_id" {
  type        = string
  description = "GCP project for all K2 resources (dev and staging share one project in K2; a separate production project is a K3+ decision)."
}

variable "region" {
  type        = string
  description = "Default GCP region (state bucket location; individual environments set their own region variable too)."
  default     = "europe-west1"
}

variable "state_bucket_name" {
  type        = string
  description = "Globally unique GCS bucket name for Terraform remote state."
  default     = "vis-terraform-state"
}

variable "github_repository" {
  type        = string
  description = "GitHub \"owner/repo\" allowed to authenticate via Workload Identity Federation."
  default     = "rundibasci/ValueInvestingSupport"
}
