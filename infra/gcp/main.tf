locals {
  registry = "${var.region}-docker.pkg.dev/${var.project_id}/poppy"
}

resource "google_project_service" "apis" {
  for_each = toset([
    "artifactregistry.googleapis.com", "compute.googleapis.com", "iam.googleapis.com",
    "iamcredentials.googleapis.com", "iap.googleapis.com", "sts.googleapis.com",
    "storage.googleapis.com", "oslogin.googleapis.com"
  ])
  service            = each.value
  disable_on_destroy = false
}

resource "google_compute_network" "poppy" {
  name                    = "poppy"
  auto_create_subnetworks = false
  depends_on              = [google_project_service.apis]
}

resource "google_compute_subnetwork" "poppy" {
  name          = "poppy"
  ip_cidr_range = "10.42.0.0/24"
  region        = var.region
  network       = google_compute_network.poppy.id
}

resource "google_artifact_registry_repository" "poppy" {
  location      = var.region
  repository_id = "poppy"
  format        = "DOCKER"
  depends_on    = [google_project_service.apis]
}

resource "google_service_account" "vm" {
  account_id   = "poppy-vm"
  display_name = "Poppy production VM"
  depends_on   = [google_project_service.apis]
}

resource "google_service_account" "deploy" {
  account_id   = "poppy-github-deploy"
  display_name = "Approved Poppy release deployer (root on one VM)"
  depends_on   = [google_project_service.apis]
}

resource "google_compute_address" "poppy" {
  name       = "poppy-ip"
  region     = var.region
  depends_on = [google_project_service.apis]
}

resource "google_compute_disk" "data" {
  name = "poppy-data"
  zone = var.zone
  type = "pd-balanced"
  size = var.disk_size_gb
  lifecycle {
    prevent_destroy = true
  }
  depends_on = [google_project_service.apis]
}

resource "google_compute_resource_policy" "snapshots" {
  name   = "poppy-data-daily"
  region = var.region
  snapshot_schedule_policy {
    schedule {
      daily_schedule {
        days_in_cycle = 1
        start_time    = "18:00"
      }
    }
    retention_policy {
      max_retention_days    = 7
      on_source_disk_delete = "KEEP_AUTO_SNAPSHOTS"
    }
  }
  depends_on = [google_project_service.apis]
}

resource "google_compute_disk_resource_policy_attachment" "snapshots" {
  name = google_compute_resource_policy.snapshots.name
  disk = google_compute_disk.data.name
  zone = var.zone
}

resource "google_storage_bucket" "backups" {
  name                        = "${var.project_id}-poppy-backups"
  location                    = var.region
  uniform_bucket_level_access = true
  public_access_prevention    = "enforced"
  force_destroy               = false
  retention_policy {
    retention_period = 604800
  }
  lifecycle_rule {
    condition {
      age = 30
    }
    action {
      type = "Delete"
    }
  }
  lifecycle {
    prevent_destroy = true
  }
  depends_on = [google_project_service.apis]
}

resource "google_compute_instance" "poppy" {
  name                = "poppy-production"
  machine_type        = var.machine_type
  deletion_protection = true
  boot_disk {
    initialize_params {
      image = "debian-cloud/debian-12"
      size  = 50
      type  = "pd-balanced"
    }
  }
  attached_disk {
    source      = google_compute_disk.data.id
    device_name = "poppy-data"
  }
  network_interface {
    subnetwork = google_compute_subnetwork.poppy.id
    access_config {
      nat_ip = google_compute_address.poppy.address
    }
  }
  service_account {
    email  = google_service_account.vm.email
    scopes = ["cloud-platform"]
  }
  shielded_instance_config {
    enable_secure_boot          = true
    enable_vtpm                 = true
    enable_integrity_monitoring = true
  }
  metadata = {
    enable-oslogin         = "TRUE"
    block-project-ssh-keys = "TRUE"
  }
  metadata_startup_script = templatefile("${path.module}/startup.sh.tftpl", {
    registry_host = "${var.region}-docker.pkg.dev"
    config        = jsonencode({ registry = local.registry, backup_bucket = google_storage_bucket.backups.name })
  })
  depends_on = [google_project_service.apis]
}

resource "google_compute_firewall" "minecraft" {
  count                   = length(var.player_cidrs) > 0 ? 1 : 0
  name                    = "poppy-minecraft"
  network                 = google_compute_network.poppy.name
  direction               = "INGRESS"
  source_ranges           = var.player_cidrs
  target_service_accounts = [google_service_account.vm.email]
  allow {
    protocol = "tcp"
    ports    = ["25565"]
  }
}

resource "google_compute_firewall" "iap_ssh" {
  name                    = "poppy-iap-ssh"
  network                 = google_compute_network.poppy.name
  direction               = "INGRESS"
  source_ranges           = ["35.235.240.0/20"]
  target_service_accounts = [google_service_account.vm.email]
  allow {
    protocol = "tcp"
    ports    = ["22"]
  }
}

resource "google_iam_workload_identity_pool" "github" {
  workload_identity_pool_id = "poppy-github"
  display_name              = "Poppy GitHub Actions"
  depends_on                = [google_project_service.apis]
}

resource "google_iam_workload_identity_pool_provider" "github" {
  workload_identity_pool_id          = google_iam_workload_identity_pool.github.workload_identity_pool_id
  workload_identity_pool_provider_id = "github"
  attribute_mapping = {
    "google.subject"          = "assertion.sub"
    "attribute.repository_id" = "assertion.repository_id"
  }
  attribute_condition = join(" && ", [
    "assertion.repository_id == '${var.github_repository_id}'",
    "assertion.repository_owner_id == '${var.github_owner_id}'",
    "assertion.ref == 'refs/heads/main'",
    # GitHub repositories created after 2026-07-15 include immutable IDs in sub.
    # Both exact formats identify this same repository; ID checks above still apply.
    "assertion.sub in ['repo:${var.github_repository}:environment:production', 'repo:${split("/", var.github_repository)[0]}@${var.github_owner_id}/${split("/", var.github_repository)[1]}@${var.github_repository_id}:environment:production']",
    "assertion.workflow_ref == '${var.github_repository}/.github/workflows/deploy-gcp.yml@refs/heads/main'",
    "assertion.event_name == 'workflow_dispatch'"
  ])
  oidc {
    issuer_uri = "https://token.actions.githubusercontent.com"
  }
}

resource "google_service_account_iam_member" "github_wif" {
  service_account_id = google_service_account.deploy.name
  role               = "roles/iam.workloadIdentityUser"
  member             = "principalSet://iam.googleapis.com/${google_iam_workload_identity_pool.github.name}/attribute.repository_id/${var.github_repository_id}"
}

resource "google_artifact_registry_repository_iam_member" "writer" {
  location   = var.region
  repository = google_artifact_registry_repository.poppy.name
  role       = "roles/artifactregistry.writer"
  member     = "serviceAccount:${google_service_account.deploy.email}"
}

resource "google_artifact_registry_repository_iam_member" "reader" {
  location   = var.region
  repository = google_artifact_registry_repository.poppy.name
  role       = "roles/artifactregistry.reader"
  member     = "serviceAccount:${google_service_account.vm.email}"
}

resource "google_compute_instance_iam_member" "login" {
  instance_name = google_compute_instance.poppy.name
  zone          = var.zone
  role          = "roles/compute.osAdminLogin"
  member        = "serviceAccount:${google_service_account.deploy.email}"
}

resource "google_project_iam_custom_role" "lookup" {
  depends_on  = [google_project_service.apis]
  role_id     = "poppyInstanceLookup"
  title       = "Read Compute metadata for gcloud IAP SSH"
  permissions = ["compute.instances.get", "compute.instances.list", "compute.projects.get", "compute.zones.get", "compute.zones.list"]
}

resource "google_project_iam_member" "lookup" {
  project = var.project_id
  role    = google_project_iam_custom_role.lookup.name
  member  = "serviceAccount:${google_service_account.deploy.email}"
}

resource "google_iap_tunnel_instance_iam_member" "ssh" {
  zone     = var.zone
  instance = google_compute_instance.poppy.name
  role     = "roles/iap.tunnelResourceAccessor"
  member   = "serviceAccount:${google_service_account.deploy.email}"
  condition {
    title      = "ssh-only"
    expression = "destination.port == 22"
  }
}

resource "google_service_account_iam_member" "deploy_uses_vm" {
  service_account_id = google_service_account.vm.name
  role               = "roles/iam.serviceAccountUser"
  member             = "serviceAccount:${google_service_account.deploy.email}"
}

resource "google_storage_bucket_iam_member" "backup_writer" {
  bucket = google_storage_bucket.backups.name
  role   = "roles/storage.objectCreator"
  member = "serviceAccount:${google_service_account.vm.email}"
}

# gcloud storage cp checks destination objects; restore also needs read access.
# Scoped to the backup bucket. No object update/delete permissions are granted.
resource "google_storage_bucket_iam_member" "backup_reader" {
  bucket = google_storage_bucket.backups.name
  role   = "roles/storage.objectViewer"
  member = "serviceAccount:${google_service_account.vm.email}"
}
