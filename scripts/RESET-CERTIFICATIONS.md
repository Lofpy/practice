# Certification-only reset

For normal administration, use the in-server `/tierreset <player|UUID|all> [kit|all]`
command instead. It previews the target, requires a 60-second confirmation, backs up the data,
and resets the selected certification and ELO without a restart. Unlike this older offline
script, the command also supports players with ranked history and preserves old match IDs
for duplicate prevention. See [docs/RANKED.md](../docs/RANKED.md) for usage and recovery cautions.

The procedure below is the offline, all-player reset for certification-derived ELO only.

Stop the PvP server normally and wait for its JVM to exit. The lobby and proxy may remain running.
Inspect `runtime/plugins/PoppyPractice/ratings.yml`, then record its SHA-256 with `Get-FileHash`.

Run from `C:\pvp` (replace the placeholder with the reviewed 64-character hash):

```powershell
.\scripts\reset-certifications.ps1 -ExpectedRatingsSha256 <reviewed-sha256> -WhatIf
.\scripts\reset-certifications.ps1 -ExpectedRatingsSha256 <reviewed-sha256>
```

For a nondefault isolated runtime use `-RuntimeDirectory` and `-ExpectedPort`; the runtime must remain inside this repository.
The preflight loads the ledger using the plugin's own validation, refuses any ranked games or non-initial ELO,
checks the exact port, and checks running WindSpigot JVM working directories even after the port has closed.

Only `ratings.yml` and `tier-assessments` are moved into a unique `runtime/backups/certifications-reset-*`
directory. Every archived file is checksum-checked; failure attempts rollback without overwriting new files.
No configuration, inventory layout, cosmetic preference, or world is changed. There is no permanent deletion.

Start PvP through `run.bat`. Everyone must complete three new certifications for each kit.
To recover, stop PvP again and restore both archived targets together. If new results have since been created,
archive them separately first; never overwrite or merge ledgers without review.
