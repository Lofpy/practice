output "server_ip" { value = google_compute_address.poppy.address }
output "vm_name" { value = google_compute_instance.poppy.name }
output "vm_zone" { value = var.zone }
output "deploy_service_account" { value = google_service_account.deploy.email }
output "workload_identity_provider" { value = google_iam_workload_identity_pool_provider.github.name }

