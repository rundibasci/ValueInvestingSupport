output "network_id" {
  value = google_compute_network.this.id
}

output "network_name" {
  value = google_compute_network.this.name
}

output "subnet_id" {
  value = google_compute_subnetwork.this.id
}

output "subnet_name" {
  value = google_compute_subnetwork.this.name
}

# Depend on this output (not just network_id) wherever a resource requires
# the private services peering to exist first (Cloud SQL, Redis).
output "private_service_connection" {
  value = google_service_networking_connection.private_service_connection.network
}
