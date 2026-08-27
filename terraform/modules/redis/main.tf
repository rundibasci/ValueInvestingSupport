# K2 Memorystore Redis module — Basic tier, direct peering, matching K1's
# proven 1GB sizing for both dev and staging.

resource "google_redis_instance" "this" {
  project        = var.project_id
  name           = "vis-k2-${var.environment}-redis"
  region         = var.region
  tier           = "BASIC"
  memory_size_gb = var.memory_size_gb
  redis_version  = "REDIS_7_0"

  authorized_network      = var.network_id
  connect_mode            = "DIRECT_PEERING"
  transit_encryption_mode = "DISABLED" # matches K1; revisit under K3 hardening

  depends_on = [var.private_service_connection]
}
