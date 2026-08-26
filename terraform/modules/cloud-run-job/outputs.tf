output "job_names" {
  description = "Map of job key -> full Cloud Run Job resource name, for wiring Cloud Scheduler triggers."
  value       = { for k, j in google_cloud_run_v2_job.this : k => j.name }
}
