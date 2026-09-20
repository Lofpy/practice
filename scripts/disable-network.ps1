[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$pvpRoot = Join-Path $repoRoot 'runtime'
$networkRoot = Join-Path $repoRoot 'network'
$markerPath = Join-Path $networkRoot 'enabled.json'
$pendingPath = Join-Path $networkRoot 'migration-pending.json'
foreach ($port in @(25565,25566,25567,25568)) {
    if (Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue) { throw "Port $port is in use. Stop all servers and wait for their processes to exit cleanly first." }
}
$journalPath = if (Test-Path -LiteralPath $markerPath -PathType Leaf) { $markerPath } elseif (Test-Path -LiteralPath $pendingPath -PathType Leaf) { $pendingPath } else { throw 'No enabled network or interrupted migration was found.' }
$journal = Get-Content -LiteralPath $journalPath -Raw | ConvertFrom-Json
if ($journal.schemaVersion -ne 1) { throw 'Unknown migration journal version. Review backups manually.' }
$backupRoot = [IO.Path]::GetFullPath([string]$journal.backupDirectory)
$allowedRoot = [IO.Path]::GetFullPath((Join-Path $networkRoot 'backups')).TrimEnd('\') + '\'
if (-not $backupRoot.StartsWith($allowedRoot, [StringComparison]::OrdinalIgnoreCase) -or -not (Test-Path -LiteralPath $backupRoot -PathType Container)) { throw 'Backup directory must exist inside this network/backups directory.' }
$allowedOriginals = @{
    'server.properties'='server.properties'; 'spigot.yml'='spigot.yml';
    'plugins\PoppyPractice.jar'='PoppyPractice.jar'; 'plugins\ProtocolSupport.jar'='ProtocolSupport.jar';
    'plugins\PoppyLobby\config.yml'='PoppyLobby-config.yml'
}
$seen = @{}
foreach ($file in @($journal.originalFiles)) {
    $relative = [string]$file.relativePath
    if (-not $allowedOriginals.ContainsKey($relative) -or $seen.ContainsKey($relative) -or $file.backupName -cne $allowedOriginals[$relative]) { throw "Unexpected or duplicate original file: $relative" }
    $seen[$relative] = $true
    $saved = Join-Path $backupRoot $file.backupName
    if (-not (Test-Path -LiteralPath $saved -PathType Leaf) -or $file.sha256 -notmatch '^[a-fA-F0-9]{64}$' -or (Get-FileHash -LiteralPath $saved -Algorithm SHA256).Hash -ne $file.sha256) { throw "Original backup is missing or checksum differs: $saved" }
}
foreach ($required in @('server.properties','spigot.yml','plugins\PoppyPractice.jar')) { if (-not $seen.ContainsKey($required)) { throw "Missing required original: $required" } }
$updateRoot = Join-Path $pvpRoot 'plugins\update'
if (Test-Path -LiteralPath $updateRoot) {
    $queued = @(Get-ChildItem -LiteralPath $updateRoot -File | Where-Object { $_.Name -match '^(PoppyPractice|ProtocolSupport|PoppyLobby|ViaVersion|ViaBackwards|ViaRewind).*\.jar$' })
    if ($queued.Count) { throw 'Queued plugin updates must be reviewed before restoration.' }
}
$addedJars = @('plugins\ViaVersion.jar','plugins\ViaBackwards.jar','plugins\ViaRewind.jar','plugins\PoppyLobby.jar')
$archiveRoot = Join-Path $networkRoot ('backups\disabled-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff') + '-' + [Guid]::NewGuid().ToString('N').Substring(0,8))
New-Item -ItemType Directory -Path $archiveRoot -Force | Out-Null
$changedPaths = @(@($journal.originalFiles | ForEach-Object { [string]$_.relativePath }) + $addedJars | Select-Object -Unique)
$beforeRestore = @{}
foreach ($relative in $changedPaths) {
    $activeFile = Join-Path $pvpRoot $relative
    if (Test-Path -LiteralPath $activeFile -PathType Leaf) {
        $snapshot = Join-Path $archiveRoot ('before-restore\' + $relative)
        New-Item -ItemType Directory -Path (Split-Path -Parent $snapshot) -Force | Out-Null
        Copy-Item -LiteralPath $activeFile -Destination $snapshot
        if ((Get-FileHash -LiteralPath $activeFile -Algorithm SHA256).Hash -ne (Get-FileHash -LiteralPath $snapshot -Algorithm SHA256).Hash) { throw "Pre-restore snapshot checksum differs: $activeFile" }
        $beforeRestore[$relative] = $snapshot
    } elseif (Test-Path -LiteralPath $activeFile) { throw "Target is not a regular file: $activeFile" }
}
Copy-Item -LiteralPath $journalPath -Destination (Join-Path $archiveRoot 'migration.json')
try {
    foreach ($relative in $addedJars) {
        $activeFile = Join-Path $pvpRoot $relative
        if (Test-Path -LiteralPath $activeFile -PathType Leaf) {
            $archivedFile = Join-Path $archiveRoot ('archived-plugins\' + [IO.Path]::GetFileName($relative))
            New-Item -ItemType Directory -Path (Split-Path -Parent $archivedFile) -Force | Out-Null
            Move-Item -LiteralPath $activeFile -Destination $archivedFile
        }
    }
    foreach ($file in @($journal.originalFiles)) {
        $destination = Join-Path $pvpRoot $file.relativePath
        New-Item -ItemType Directory -Path (Split-Path -Parent $destination) -Force | Out-Null
        Copy-Item -LiteralPath (Join-Path $backupRoot $file.backupName) -Destination $destination -Force
        if ((Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash -ne $file.sha256) { throw "Restored checksum differs: $destination" }
    }
    Move-Item -LiteralPath $journalPath -Destination (Join-Path $archiveRoot ([IO.Path]::GetFileName($journalPath)))
} catch {
    $restoreError = $_.Exception.Message
    $recoveryErrors = New-Object System.Collections.Generic.List[string]
    foreach ($relative in $changedPaths) {
        try {
            $activeFile = Join-Path $pvpRoot $relative
            if ($beforeRestore.ContainsKey($relative)) {
                New-Item -ItemType Directory -Path (Split-Path -Parent $activeFile) -Force | Out-Null
                Copy-Item -LiteralPath $beforeRestore[$relative] -Destination $activeFile -Force
            } elseif (Test-Path -LiteralPath $activeFile -PathType Leaf) {
                $failedFile = Join-Path $archiveRoot ('failed-restoration\' + $relative)
                New-Item -ItemType Directory -Path (Split-Path -Parent $failedFile) -Force | Out-Null
                Move-Item -LiteralPath $activeFile -Destination $failedFile
            }
        } catch { $recoveryErrors.Add($_.Exception.Message) }
    }
    throw "Restoration failed; pre-restore files were recovered where possible. Keep servers stopped. Cause: $restoreError. Recovery errors: $($recoveryErrors -join '; '). Archives: $archiveRoot"
}
Write-Host 'Standalone PvP settings and original plugins restored. Start runtime/run.bat.'
Write-Host "Network plugins and pre-restore files were archived, not deleted: $archiveRoot"
Write-Host 'All worlds, player data, and generated plugin configuration folders remain intact. A pre-existing custom PoppyLobby config was restored if present.'
