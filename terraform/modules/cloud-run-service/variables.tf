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

variable "google_oauth2_redirect_uri" {
  type        = string
  description = "Full backend callback URL Google redirects to after consent, e.g. https://<service-url>/login/oauth2/code/google. Leave empty to omit the env var entirely — Google sign-in also requires GOOGLE_CLIENT_ID/SECRET (see secret_env_bindings) to activate, so an empty value here just avoids shipping a placeholder."
  default     = ""
}

variable "google_oauth2_frontend_callback" {
  type        = string
  description = "Frontend URL that receives the short-lived OAuth handoff code, e.g. https://<service-url>/auth/oauth2/callback. Leave empty to omit the env var entirely, matching google_oauth2_redirect_uri."
  default     = ""
}

# AI Investment Thesis (Group TA, Phase TA4/TA5). No GOOGLE_APPLICATION_CREDENTIALS var is
# ever set here: Cloud Run's attached runtime service account (var.runtime_service_account_email,
# granted roles/aiplatform.user in the iam module) is resolved automatically as Application
# Default Credentials — no key file, matching mission.md's secrets principle. Stays disabled
# (false) by default in every environment; flipped only via an explicit -var at apply time,
# the same explicit-operational-decision boundary specs/tech-stack.md documents.
variable "thesis_agent_enabled" {
  type        = bool
  description = "THESIS_AGENT_ENABLED. False in every committed default — enabling Vertex AI Gemini calls is a separate, explicit operational decision, never implied by a routine deploy."
  default     = false
}

variable "google_cloud_project" {
  type        = string
  description = "GOOGLE_CLOUD_PROJECT for the Vertex AI client. Empty omits the env var (thesis_agent_enabled must also be true for the feature to activate — see ThesisClientConfig's startup validation)."
  default     = ""
}

variable "vertex_ai_location" {
  type        = string
  description = "VERTEX_AI_LOCATION — the data-residency region recorded in Group TA's governance review (Phase TA1)."
  default     = ""
}

variable "gemini_model_id" {
  type        = string
  description = "GEMINI_MODEL_ID — the specific pinned model string cleared by the TA3 capability gate (vis-model-training/config/vertex-gemini-v1.json). Never a floating/auto-updating alias."
  default     = ""
}
