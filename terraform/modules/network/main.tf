# K2 network module — private connectivity for one environment, mirroring
# K1's proven topology (custom VPC, Direct VPC egress, private service
# access for Cloud SQL/Redis). See specs/2026-08-26-k2-production-shaped-gcp-platform/.

resource "google_compute_network" "this" {
  project                 = var.project_id
  name                    = "vis-k2-${var.environment}-network"
  auto_create_subnetworks = false
  routing_mode            = "REGIONAL"
}

resource "google_compute_subnetwork" "this" {
  project       = var.project_id
  name          = "vis-k2-${var.environment}-subnet"
  region        = var.region
  network       = google_compute_network.this.id
  ip_cidr_range = var.subnet_cidr

  private_ip_google_access = true
}

# Reserved range for the private services (Cloud SQL, Memorystore) peering.
resource "google_compute_global_address" "private_service_range" {
  project       = var.project_id
  name          = "vis-k2-${var.environment}-managed-services"
  purpose       = "VPC_PEERING"
  address_type  = "INTERNAL"
  prefix_length = 16
  network       = google_compute_network.this.id
}

resource "google_service_networking_connection" "private_service_connection" {
  network                 = google_compute_network.this.id
  service                 = "servicenetworking.googleapis.com"
  reserved_peering_ranges = [google_compute_global_address.private_service_range.name]
}
