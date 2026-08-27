variable "project_id" {
  type        = string
  description = "GCP project hosting this environment's network."
}

variable "region" {
  type        = string
  description = "GCP region for the subnet."
}

variable "environment" {
  type        = string
  description = "Environment name (dev, staging). Used in resource naming only."
  validation {
    condition     = contains(["dev", "staging"], var.environment)
    error_message = "environment must be \"dev\" or \"staging\"."
  }
}

variable "subnet_cidr" {
  type        = string
  description = "Primary IPv4 CIDR range for this environment's subnet. Must not overlap other environments' or K1's retired 10.20.0.0/24."
}
