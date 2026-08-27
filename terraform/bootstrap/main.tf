# K2 one-time bootstrap: resources that exist once for the whole project,
# not per environment. Run this BEFORE terraform/environments/{dev,staging}
# and BEFORE any environment's own state is initialized.
#
# This configuration deliberately does NOT use a remote backend — its own
# state is small, rarely changes, and bootstrapping a backend from a
# configuration that creates that backend's bucket is circular. Keep this
# state file safe (e.g. commit its location/backup approach to the runbook);
# do not delete it casually.
#
# specs/2026-08-26-k2-production-shaped-gcp-platform/plan.md — Group 1.

terraform {
  required_version = ">= 1.5.0"
  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 5.0"
    }
  }
}

provider "google" {
  project = var.project_id
  region  = var.region
}

locals {
  required_apis = [
    "run.googleapis.com",
    "sqladmin.googleapis.com",
    "sql-component.googleapis.com",
    "redis.googleapis.com",
    "secretmanager.googleapis.com",
    "artifactregistry.googleapis.com",
    "compute.googleapis.com",
    "servicenetworking.googleapis.com",
    "cloudscheduler.googleapis.com",
    "monitoring.googleapis.com",
    "logging.googleapis.com",
    "iamcredentials.googleapis.com",
    "sts.googleapis.com",
    "cloudbuild.googleapis.com",
  ]
}

resource "google_project_service" "required" {
  for_each = toset(local.required_apis)
  project  = var.project_id
  service  = each.value

  disable_dependent_services = false
  disable_on_destroy         = false
}

# One shared Artifact Registry repository for both environments: staging
# always promotes the exact digest already built and verified for dev, so
# a single repo (not one per environment) is the natural fit — see
# requirements.md, CI/CD Pipeline scope.
resource "google_artifact_registry_repository" "vis_k2" {
  project       = var.project_id
  location      = var.region
  repository_id = "vis-k2"
  format        = "DOCKER"
  description   = "VIS K2 immutable application images, shared by dev and staging"

  depends_on = [google_project_service.required]
}

# Terraform remote state, one bucket shared by both environments via
# distinct object-path prefixes (dev/terraform.tfstate, staging/terraform.tfstate).
resource "google_storage_bucket" "terraform_state" {
  project                     = var.project_id
  name                        = var.state_bucket_name
  location                    = var.region
  uniform_bucket_level_access = true
  force_destroy               = false

  versioning {
    enabled = true
  }

  lifecycle_rule {
    condition {
      num_newer_versions = 20
    }
    action {
      type = "Delete"
    }
  }
}

# Workload Identity Federation: lets GitHub Actions impersonate a GCP
# service account without a stored JSON key. Scoped to this one repository.
resource "google_iam_workload_identity_pool" "github" {
  project                   = var.project_id
  workload_identity_pool_id = "vis-k2-github-pool"
  display_name              = "VIS K2 GitHub Actions pool"
}

resource "google_iam_workload_identity_pool_provider" "github" {
  project                            = var.project_id
  workload_identity_pool_id          = google_iam_workload_identity_pool.github.workload_identity_pool_id
  workload_identity_pool_provider_id = "github-actions"
  display_name                       = "GitHub Actions OIDC"

  attribute_mapping = {
    "google.subject"       = "assertion.sub"
    "attribute.repository" = "assertion.repository"
    "attribute.ref"        = "assertion.ref"
  }

  # Scoped to this repository only — never organization-wide.
  attribute_condition = "assertion.repository == \"${var.github_repository}\""

  oidc {
    issuer_uri = "https://token.actions.githubusercontent.com"
  }
}
