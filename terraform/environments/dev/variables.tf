variable "project_id" {
  type        = string
  description = "GCP project (dev and staging share vis-version0 in K2; a separate project is a K3+ decision)."
  default     = "vis-version0"
}

variable "region" {
  type    = string
  default = "europe-west1"
}

variable "environment" {
  type    = string
  default = "dev"
}

variable "subnet_cidr" {
  type        = string
  description = "Must not overlap staging's or K1's retired 10.20.0.0/24."
  default     = "10.30.0.0/24"
}

variable "image" {
  type        = string
  description = "Full Artifact Registry image reference, tagged by immutable commit SHA. Set by CI/CD on each deploy."
}

variable "workload_identity_pool_name" {
  type        = string
  description = "From terraform/bootstrap/ output workload_identity_pool_name."
}

variable "github_deploy_ref" {
  type        = string
  description = "Git ref whose GitHub Actions runs may deploy dev."
  default     = "refs/heads/main"
}

variable "notification_email" {
  type    = string
  default = ""
}

variable "max_instances" {
  type        = number
  description = "Starts at 1; raise only after the Cloud Run Jobs migration is verified for dev (plan.md Group 5/9)."
  default     = 1
}

# AI Investment Thesis (Group TA). False by default in dev, same as every other
# environment — enabling it is an explicit, separate operational decision passed via
# -var at apply time, never a committed default (specs/tech-stack.md).
variable "thesis_agent_enabled" {
  type    = bool
  default = false
}

variable "vertex_ai_location" {
  type        = string
  description = "Data-residency region for Vertex AI Gemini calls, per Group TA Phase TA1's governance decision."
  default     = "europe-west1"
}

variable "gemini_model_id" {
  type        = string
  description = "Pinned Gemini model id cleared by the TA3 capability gate. Safe to default (not a secret) — only takes effect when thesis_agent_enabled=true."
  default     = "gemini-2.5-flash"
}
