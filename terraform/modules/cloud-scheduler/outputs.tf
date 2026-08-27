output "scheduler_job_names" {
  value = { for k, s in google_cloud_scheduler_job.this : k => s.name }
}
