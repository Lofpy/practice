[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$pvpRoot = Join-Path $repoRoot 'runtime'
$networkRoot = Join-Path $repoRoot 'network'
$templateRoot = Join-Path $repoRoot 'network-template'
$cacheRoot = Join-Path $env:LOCALAPPDATA 'PoppyPracticeTools\network-cache'
$markerPath = Join-Path $networkRoot 'enabled.json'
$pendingPath = Join-Path $networkRoot 'migration-pending.json'
if ((Test-Path -LiteralPath $markerPath) -or (Test-Path -LiteralPath $pendingPath)) { throw 'Network is enabled or an interrupted migration needs recovery. Stop all servers and run scripts/disable-network.ps1 first.' }
foreach ($port in @(25565,25566,25567,25568)) {
    if (Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue) {
        throw "Port $port is still in use. Stop the PvP, lobby, and proxy servers cleanly before migration."
    }
}
$practiceJar = Join-Path $repoRoot 'target\PoppyPractice-0.1.0.jar'
$lobbyJar = Join-Path $repoRoot 'lobby-plugin\target\PoppyLobby-0.1.0.jar'
foreach ($required in @($practiceJar,$lobbyJar,(Join-Path $networkRoot 'proxy\velocity.jar'),
    (Join-Path $networkRoot 'lobby\plugins\PoppyLobby.jar'),
    (Join-Path $networkRoot 'proxy\plugins\AscendingNetwork.jar'),
    (Join-Path $networkRoot 'survival\paper.jar'),
    (Join-Path $networkRoot 'survival\plugins\AscendingSurvival.jar'))) {
    if (-not (Test-Path -LiteralPath $required)) { throw "Missing $required; complete setup/build first." }
}
foreach ($name in @('ViaVersion.jar','ViaBackwards.jar','ViaRewind.jar')) {
    if (-not (Test-Path -LiteralPath (Join-Path $cacheRoot $name))) { throw "Missing $name; run setup-network.ps1 first." }
    if (Test-Path -LiteralPath (Join-Path $pvpRoot "plugins\$name")) { throw "Existing $name must be reviewed before migration." }
}
foreach ($name in @('pvp-lobby.yml','via-config.yml','artifacts.json')) {
    if (-not (Test-Path -LiteralPath (Join-Path $templateRoot $name) -PathType Leaf)) { throw "Missing network template: $name" }
}
$artifacts = (Get-Content -LiteralPath (Join-Path $templateRoot 'artifacts.json') -Raw | ConvertFrom-Json).artifacts
foreach ($name in @('velocity.jar','paper.jar','ViaVersion.jar','ViaBackwards.jar','ViaRewind.jar')) {
    $artifact = @($artifacts | Where-Object { $_.name -ceq $name })
    if ($artifact.Count -ne 1 -or $artifact[0].sha256 -notmatch '^[a-fA-F0-9]{64}$') { throw "Invalid artifact manifest entry: $name" }
    $installed = if ($name -eq 'velocity.jar') { Join-Path $networkRoot 'proxy\velocity.jar' }
        elseif ($name -eq 'paper.jar') { Join-Path $networkRoot 'survival\paper.jar' }
        else { Join-Path $networkRoot "lobby\plugins\$name" }
    foreach ($path in @((Join-Path $cacheRoot $name), $installed)) {
        if (-not (Test-Path -LiteralPath $path -PathType Leaf) -or (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash -ne $artifact[0].sha256) { throw "Missing or checksum-mismatched artifact: $path" }
    }
}
if ((Get-FileHash -LiteralPath (Join-Path $networkRoot 'lobby\plugins\PoppyLobby.jar') -Algorithm SHA256).Hash -ne (Get-FileHash -LiteralPath $lobbyJar -Algorithm SHA256).Hash) { throw 'Installed lobby plugin differs from the build. Review/update it before migration.' }
if (Test-Path -LiteralPath (Join-Path $pvpRoot 'plugins\PoppyLobby.jar')) { throw 'An existing PoppyLobby installation requires manual review.' }
if (Test-Path -LiteralPath (Join-Path $pvpRoot 'plugins\update\PoppyPractice.jar')) { throw 'An existing queued PvP update must be reviewed first.' }
$propertiesPath = Join-Path $pvpRoot 'server.properties'
$spigotPath = Join-Path $pvpRoot 'spigot.yml'
$properties = Get-Content -LiteralPath $propertiesPath -Raw
$spigot = Get-Content -LiteralPath $spigotPath -Raw
foreach ($property in @('server-ip','server-port','online-mode')) {
    if ([regex]::Matches($properties, "(?m)^$property=").Count -ne 1) { throw "Ambiguous $property in server.properties." }
}
if ([regex]::Matches($spigot, '(?m)^  bungeecord:').Count -ne 1) { throw 'Cannot identify settings.bungeecord in spigot.yml.' }
$backupRoot = Join-Path $networkRoot ('backups\' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff') + '-' + [Guid]::NewGuid().ToString('N').Substring(0,8))
New-Item -ItemType Directory -Path $backupRoot | Out-Null
Copy-Item -LiteralPath $propertiesPath -Destination (Join-Path $backupRoot 'server.properties')
Copy-Item -LiteralPath $spigotPath -Destination (Join-Path $backupRoot 'spigot.yml')
Copy-Item -LiteralPath (Join-Path $pvpRoot 'plugins\PoppyPractice.jar') -Destination (Join-Path $backupRoot 'PoppyPractice.jar')
$protocolSupport = Join-Path $pvpRoot 'plugins\ProtocolSupport.jar'
$gatewayConfig = Join-Path $pvpRoot 'plugins\PoppyLobby\config.yml'
$viaConfig = Join-Path $pvpRoot 'plugins\ViaVersion\config.yml'
$originals = @(
    @{relativePath='server.properties';backupName='server.properties'},
    @{relativePath='spigot.yml';backupName='spigot.yml'},
    @{relativePath='plugins\PoppyPractice.jar';backupName='PoppyPractice.jar'}
)
if (Test-Path -LiteralPath $protocolSupport) { $originals += @{relativePath='plugins\ProtocolSupport.jar';backupName='ProtocolSupport.jar'} }
if (Test-Path -LiteralPath $gatewayConfig) { $originals += @{relativePath='plugins\PoppyLobby\config.yml';backupName='PoppyLobby-config.yml'} }
foreach ($file in $originals) {
    $source = Join-Path $pvpRoot $file.relativePath
    $saved = Join-Path $backupRoot $file.backupName
    Copy-Item -LiteralPath $source -Destination $saved -Force
    $file.sha256 = (Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash
    if ((Get-FileHash -LiteralPath $saved -Algorithm SHA256).Hash -ne $file.sha256) { throw "Backup verification failed: $source" }
}
$addedFiles = @('plugins\ViaVersion.jar','plugins\ViaBackwards.jar','plugins\ViaRewind.jar','plugins\PoppyLobby.jar')
if (-not (Test-Path -LiteralPath $gatewayConfig)) { $addedFiles += 'plugins\PoppyLobby\config.yml' }
if (-not (Test-Path -LiteralPath $viaConfig)) { $addedFiles += 'plugins\ViaVersion\config.yml' }
$utf8 = New-Object System.Text.UTF8Encoding($false)
$marker = @{ schemaVersion=1; enabledAt=(Get-Date -Format o); backupDirectory=$backupRoot; publicPort=25565; pvpPort=25566; lobbyPort=25567; originalFiles=$originals; addedFiles=$addedFiles }
$journalJson = $marker | ConvertTo-Json -Depth 6
[IO.File]::WriteAllText((Join-Path $backupRoot 'migration.json'), $journalJson, $utf8)
[IO.File]::WriteAllText($pendingPath, $journalJson, $utf8)
try {
if (Test-Path -LiteralPath $protocolSupport) { Move-Item -LiteralPath $protocolSupport -Destination (Join-Path $backupRoot 'ProtocolSupport.archived.jar') }
$properties = [regex]::Replace($properties, '(?m)^server-ip=[^\r\n]*', 'server-ip=127.0.0.1')
$properties = [regex]::Replace($properties, '(?m)^server-port=[^\r\n]*', 'server-port=25566')
$properties = [regex]::Replace($properties, '(?m)^online-mode=[^\r\n]*', 'online-mode=false')
$spigot = [regex]::Replace($spigot, '(?m)^  bungeecord:[^\r\n]*', '  bungeecord: true')
[IO.File]::WriteAllText($propertiesPath, $properties, $utf8)
[IO.File]::WriteAllText($spigotPath, $spigot, $utf8)
foreach ($name in @('ViaVersion.jar','ViaBackwards.jar','ViaRewind.jar')) {
    Copy-Item -LiteralPath (Join-Path $cacheRoot $name) -Destination (Join-Path $pvpRoot "plugins\$name")
}
Copy-Item -LiteralPath $practiceJar -Destination (Join-Path $pvpRoot 'plugins\PoppyPractice.jar') -Force
Copy-Item -LiteralPath $lobbyJar -Destination (Join-Path $pvpRoot 'plugins\PoppyLobby.jar')
foreach ($name in @('PoppyLobby','ViaVersion')) { New-Item -ItemType Directory -Path (Join-Path $pvpRoot "plugins\$name") -Force | Out-Null }
Copy-Item -LiteralPath (Join-Path $templateRoot 'pvp-lobby.yml') -Destination $gatewayConfig -Force
if (-not (Test-Path -LiteralPath $viaConfig)) {
    Copy-Item -LiteralPath (Join-Path $templateRoot 'via-config.yml') -Destination $viaConfig
}
# Rename is the commit point; a pending journal never authorizes server startup.
[IO.File]::Move($pendingPath, $markerPath)
} catch {
    $activationError = $_.Exception.Message
    $recoveryErrors = New-Object System.Collections.Generic.List[string]
    foreach ($relative in $addedFiles) {
        try {
            $activeFile = Join-Path $pvpRoot $relative
            if (Test-Path -LiteralPath $activeFile -PathType Leaf) {
                $archivedFile = Join-Path $backupRoot ('failed-added\' + $relative)
                New-Item -ItemType Directory -Path (Split-Path -Parent $archivedFile) -Force | Out-Null
                Move-Item -LiteralPath $activeFile -Destination $archivedFile
            }
        } catch { $recoveryErrors.Add($_.Exception.Message) }
    }
    foreach ($file in $originals) {
        try { Copy-Item -LiteralPath (Join-Path $backupRoot $file.backupName) -Destination (Join-Path $pvpRoot $file.relativePath) -Force }
        catch { $recoveryErrors.Add($_.Exception.Message) }
    }
    if ($recoveryErrors.Count -eq 0) {
        if (Test-Path -LiteralPath $pendingPath) { Move-Item -LiteralPath $pendingPath -Destination (Join-Path $backupRoot 'activation-rolled-back.json') }
        throw "Activation failed and was rolled back. Added files were archived in $backupRoot. Cause: $activationError"
    }
    throw "Activation failed; recovery needs attention. Do NOT start servers. Use disable-network.ps1 after resolving file locks. Backups: $backupRoot. Cause: $activationError. Recovery errors: $($recoveryErrors -join '; ')"
}
Write-Host "Network enabled. Previous server files are recoverable in $backupRoot"
Write-Host 'ProtocolSupport was archived, not deleted; Via plugins now translate client versions.'
Write-Host 'PvP world, player data, and PoppyPractice configuration were not changed.'
Write-Host 'Start all four servers with run-network.bat.'
