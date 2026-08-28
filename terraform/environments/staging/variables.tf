variable "project_id" {
  type    = string
  default = "vis-version0"
}

variable "region" {
  type    = string
  default = "europe-west1"
}

variable "environment" {
  type    = string
  default = "staging"
}

variable "subnet_cidr" {
  type        = string
  description = "Must not overlap dev's 10.30.0.0/24 or K1's retired 10.20.0.0/24."
  default     = "10.31.0.0/24"
}

variable "image" {
  type        = string
  description = "Full Artifact Registry image reference, tagged by immutable commit SHA. Set by CI/CD; staging always deploys the exact digest already verified on dev, never a fresh build."
}

variable "workload_identity_pool_name" {
  type = string
}

variable "github_deploy_ref" {
  type    = string
  default = "refs/heads/main"
}

variable "notification_email" {
  type    = string
  default = ""
}

variable "max_instances" {
  type        = number
  description = "Starts at 1; may raise to a conservative value (e.g. 3) only after the Cloud Run Jobs migration is verified for staging (plan.md Group 5/9)."
  default     = 1
}

variable "custom_domain" {
  type        = string
  description = "Staging-only custom HTTPS domain (K2 spec Decision 7). Leave empty until DNS/certificate provisioning is ready."
  default     = ""
}

# AI Investment Thesis (Group TA). False by default, same as every other environment —
# enabling it is an explicit, separate operational decision passed via -var at apply time,
# never a committed default (specs/tech-stack.md).
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
