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
  type = string
}

variable "private_service_connection" {
  type        = string
  description = "Sequencing-only reference to the network module's private service connection."
}

variable "memory_size_gb" {
  type    = number
  default = 1
}
