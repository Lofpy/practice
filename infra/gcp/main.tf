resource "google_project_service" "apis" {
  for_each           = toset(["artifactregistry.googleapis.com", "compute.googleapis.com", "iam.googleapis.com", "iamcredentials.googleapis.com", "iap.googleapis.com", "sts.googleapis.com"])
  service            = each.value
  disable_on_destroy = false
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
}
resource "google_service_account" "deploy" {
  account_id   = "poppy-github-deploy"
  display_name = "Poppy GitHub deployment"
}
resource "google_compute_address" "poppy" {
  name   = "poppy-ip"
  region = var.region
}
resource "google_compute_instance" "poppy" {
  name         = "poppy-production"
  machine_type = var.machine_type
  tags         = ["poppy-minecraft", "poppy-iap-ssh"]
  boot_disk {
    initialize_params {
      image = "debian-cloud/debian-12"
      size  = var.disk_size_gb
      type  = "pd-balanced"
    }
  }
  network_interface {
    network = "default"
    access_config {
      nat_ip = google_compute_address.poppy.address
    }
  }
  service_account {
    email  = google_service_account.vm.email
    scopes = ["cloud-platform"]
  }
  metadata                = { enable-oslogin = "TRUE", block-project-ssh-keys = "TRUE" }
  metadata_startup_script = <<-EOF
    #!/bin/bash
    set -euxo pipefail
    apt-get update
    apt-get install -y ca-certificates curl
    install -m 0755 -d /etc/apt/keyrings
    curl -fsSL https://download.docker.com/linux/debian/gpg -o /etc/apt/keyrings/docker.asc
    chmod a+r /etc/apt/keyrings/docker.asc
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/debian $(. /etc/os-release && echo $VERSION_CODENAME) stable" > /etc/apt/sources.list.d/docker.list
    apt-get update
    apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin google-cloud-cli
    mkdir -p /srv/poppy/{pvp,lobby,proxy,backups} /opt/poppy/deploy
  EOF
  depends_on = [google_project_service.apis]
}
resource "google_compute_firewall" "minecraft" {
  name          = "poppy-minecraft"
  network       = "default"
  direction     = "INGRESS"
  source_ranges = ["0.0.0.0/0"]
  target_tags   = ["poppy-minecraft"]
  allow {
    protocol = "tcp"
    ports    = ["25565"]
  }
}
resource "google_compute_firewall" "iap_ssh" {
  name          = "poppy-iap-ssh"
  network       = "default"
  direction     = "INGRESS"
  source_ranges = ["35.235.240.0/20"]
  target_tags   = ["poppy-iap-ssh"]
  allow {
    protocol = "tcp"
    ports    = ["22"]
  }
}
resource "google_iam_workload_identity_pool" "github" {
  workload_identity_pool_id = "github-actions"
  display_name              = "GitHub Actions"
}
resource "google_iam_workload_identity_pool_provider" "github" {
  workload_identity_pool_id          = google_iam_workload_identity_pool.github.workload_identity_pool_id
  workload_identity_pool_provider_id = "github"
  display_name                       = "GitHub"
  attribute_mapping                  = { "google.subject" = "assertion.sub", "attribute.repository" = "assertion.repository", "attribute.repository_owner" = "assertion.repository_owner" }
  attribute_condition                = "assertion.repository == '${var.github_repository}'"
  oidc {
    issuer_uri = "https://token.actions.githubusercontent.com"
  }
}
resource "google_service_account_iam_member" "github_wif" {
  service_account_id = google_service_account.deploy.name
  role               = "roles/iam.workloadIdentityUser"
  member             = "principalSet://iam.googleapis.com/${google_iam_workload_identity_pool.github.name}/attribute.repository/${var.github_repository}"
}
resource "google_project_iam_member" "deploy_roles" {
  for_each = toset(["roles/artifactregistry.writer", "roles/compute.osAdminLogin", "roles/iap.tunnelResourceAccessor"])
  project  = var.project_id
  role     = each.value
  member   = "serviceAccount:${google_service_account.deploy.email}"
}
resource "google_service_account_iam_member" "deploy_uses_vm" {
  service_account_id = google_service_account.vm.name
  role               = "roles/iam.serviceAccountUser"
  member             = "serviceAccount:${google_service_account.deploy.email}"
}
resource "google_project_iam_member" "vm_registry" {
  project = var.project_id
  role    = "roles/artifactregistry.reader"
  member  = "serviceAccount:${google_service_account.vm.email}"
}
