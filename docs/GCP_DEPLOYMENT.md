# GCP production deployment

Velocity, Lobby, and PvP run on one Compute Engine VM. Only TCP 25565 is public. Ports 25566 and 25567 use loopback, SSH is restricted to IAP, and GitHub Actions uses short-lived Workload Identity Federation credentials.

## One-time bootstrap

1. Create a GCP project, attach billing, and install gcloud and Terraform locally.
2. Copy `infra/gcp/terraform.tfvars.example` to `terraform.tfvars` and set the project ID.
3. Run:

```bash
gcloud auth application-default login
terraform -chdir=infra/gcp init
terraform -chdir=infra/gcp plan
terraform -chdir=infra/gcp apply
```

Do not commit tfvars or state. Move state to a locked remote backend before another administrator manages infrastructure.

## GitHub configuration

Create the `production` Environment and require your approval. Add: `GCP_PROJECT_ID`, `GCP_REGION`, `GCP_VM_NAME`, `GCP_VM_ZONE`, `GCP_WORKLOAD_IDENTITY_PROVIDER`, and `GCP_DEPLOY_SERVICE_ACCOUNT`. Values come from Terraform inputs/outputs. No JSON service-account key is needed.

## First boot and migration

Images create `eula=false`. Review the Minecraft EULA yourself, then set `eula=true` in both persistent data directories. Before the first public deployment, copy the stopped Windows runtime data into `/srv/poppy/pvp`, `/srv/poppy/lobby`, and `/srv/poppy/proxy`. Preserve worlds, plugin data, player data, KB, and operational settings; do not copy old server/plugin JAR files.

Verify PvP is `127.0.0.1:25566`, Lobby is `127.0.0.1:25567`, both backends are offline behind Velocity, and Velocity keeps online-mode enabled.

## Release and recovery

Run **Deploy production to GCP**, enter a reviewed commit/tag, then approve the production Environment. The workflow pushes SHA-tagged images, archives data, normally stops the network, starts the release, and returns to the previous image tags if health checks fail.

The archive is on the same disk. Add scheduled disk snapshots or an encrypted, separately retained Cloud Storage backup for disaster recovery. Restore data only while all containers are stopped. Never expose ports 25566 or 25567 publicly.

