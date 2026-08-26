# K2 Cloud SQL module — private IP only, automated backups + PITR enabled.
# Sizing mirrors K1's proven db-f1-micro/10GB baseline; both dev and staging
# stay on conservative dev-tier sizing per the K2 spec's cost discipline.

resource "google_sql_database_instance" "this" {
  project             = var.project_id
  name                = "vis-k2-${var.environment}-postgres"
  region              = var.region
  database_version    = "POSTGRES_16"
  deletion_protection = var.deletion_protection

  settings {
    tier              = var.tier
    edition           = "ENTERPRISE" # required so db-f1-micro/db-g1-small tiers are accepted; see K1's ENTERPRISE_PLUS incident
    availability_type = "ZONAL"
    disk_size         = var.disk_size_gb
    disk_type         = "PD_SSD"

    ip_configuration {
      ipv4_enabled    = false
      private_network = var.network_id
    }

    backup_configuration {
      enabled                        = true
      point_in_time_recovery_enabled = true
      transaction_log_retention_days = var.pitr_retention_days
      backup_retention_settings {
        retained_backups = var.backup_retention_count
        retention_unit   = "COUNT"
      }
    }
  }

  depends_on = [var.private_service_connection]
}

resource "google_sql_database" "app" {
  project  = var.project_id
  name     = "vis"
  instance = google_sql_database_instance.this.name
}
