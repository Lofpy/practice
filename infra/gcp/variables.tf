variable "project_id" {
  type = string
  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{4,28}[a-z0-9]$", var.project_id))
    error_message = "Use a valid existing GCP project ID."
  }
}

variable "github_repository" {
  type    = string
  default = "Lofpy/practice"
}

variable "github_repository_id" {
  type    = string
  default = "1371761482"
}

variable "github_owner_id" {
  type    = string
  default = "133192866"
}

variable "region" {
  type    = string
  default = "asia-northeast1"
}

variable "zone" {
  type    = string
  default = "asia-northeast1-b"
}

variable "machine_type" {
  type    = string
  default = "e2-standard-4"
}

variable "disk_size_gb" {
  type    = number
  default = 100
}

variable "player_cidrs" {
  type        = list(string)
  description = "Start with your test IP/32; deliberately change to 0.0.0.0/0 only after acceptance."
  default     = []
}
