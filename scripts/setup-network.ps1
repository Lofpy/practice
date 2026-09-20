[CmdletBinding()]
param([switch]$SkipPluginBuild, [switch]$UpdateExisting)
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$networkRoot = Join-Path $repoRoot 'network'
$templateRoot = Join-Path $repoRoot 'network-template'
$cacheRoot = Join-Path $env:LOCALAPPDATA 'PoppyPracticeTools\network-cache'
$java25Root = Join-Path $env:LOCALAPPDATA 'PoppyPracticeTools\temurin25-network'
$lobbyRoot = Join-Path $networkRoot 'lobby'
$proxyRoot = Join-Path $networkRoot 'proxy'
$survivalRoot = Join-Path $networkRoot 'survival'
$survivalJdkRoot = Join-Path $env:LOCALAPPDATA 'PoppyPracticeTools\temurin25-survival-jdk'
$updateBackup = Join-Path $networkRoot ('backups\survival-update-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
if ($UpdateExisting) {
    foreach ($port in @(25565,25566,25567,25568)) {
        if (Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue) {
            throw "Stop every network server cleanly before updating managed files. Port $port is in use."
        }
    }
}
$eulaPath = Join-Path $repoRoot 'runtime\eula.txt'
$windJar = Join-Path $repoRoot 'runtime\windspigot.jar'
if (-not (Test-Path -LiteralPath $windJar)) { throw 'Existing runtime/windspigot.jar is required.' }
if (-not (Test-Path -LiteralPath $eulaPath) -or (Get-Content -LiteralPath $eulaPath -Raw) -notmatch '(?m)^eula=true\s*$') {
    throw 'The existing operator-accepted runtime/eula.txt is required; this script does not accept new terms.'
}
foreach ($directory in @($cacheRoot, $java25Root, $survivalJdkRoot, $networkRoot, $proxyRoot, $lobbyRoot,
    $survivalRoot, (Join-Path $survivalRoot 'config'), (Join-Path $survivalRoot 'plugins'),
    (Join-Path $proxyRoot 'plugins\ascending-network'), (Join-Path $lobbyRoot 'plugins'))) {
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
}
$manifest = Get-Content -LiteralPath (Join-Path $templateRoot 'artifacts.json') -Raw | ConvertFrom-Json
foreach ($artifact in $manifest.artifacts) {
    $cachedFile = Join-Path $cacheRoot $artifact.name
    if ((Test-Path -LiteralPath $cachedFile) -and (Get-FileHash -LiteralPath $cachedFile).Hash -eq $artifact.sha256) { continue }
    Write-Host "Downloading $($artifact.name) $($artifact.version)..."
    $downloadFile = "$cachedFile.download"
    Invoke-WebRequest -UseBasicParsing -Uri $artifact.url -OutFile $downloadFile -Headers @{ 'User-Agent' = 'AscendingMC-Setup/1.0 (https://github.com/Lofpy/practice)' }
    if ((Get-FileHash -LiteralPath $downloadFile).Hash -ne $artifact.sha256) { throw "Checksum mismatch: $($artifact.name)" }
    Move-Item -LiteralPath $downloadFile -Destination $cachedFile -Force
}
if (-not (Get-ChildItem -LiteralPath $java25Root -Filter java.exe -Recurse | Select-Object -First 1)) {
    Expand-Archive -LiteralPath (Join-Path $cacheRoot 'temurin25-network.zip') -DestinationPath $java25Root
}
if (-not (Get-ChildItem -LiteralPath $survivalJdkRoot -Filter javac.exe -Recurse | Select-Object -First 1)) {
    Expand-Archive -LiteralPath (Join-Path $cacheRoot 'temurin25-survival-jdk.zip') -DestinationPath $survivalJdkRoot
}
function Copy-Missing([string]$Source, [string]$Destination) {
    if (-not (Test-Path -LiteralPath $Destination)) { Copy-Item -LiteralPath $Source -Destination $Destination }
}
function Install-Managed([string]$Source, [string]$Destination, [string]$BackupName) {
    if (-not (Test-Path -LiteralPath $Destination)) { Copy-Item -LiteralPath $Source -Destination $Destination; return }
    if ((Get-FileHash -LiteralPath $Source).Hash -eq (Get-FileHash -LiteralPath $Destination).Hash) { return }
    if (-not $UpdateExisting) { throw "Managed file differs: $Destination. Stop all servers and rerun with -UpdateExisting to back up and update it." }
    $saved = Join-Path $updateBackup $BackupName
    New-Item -ItemType Directory -Path (Split-Path -Parent $saved) -Force | Out-Null
    Copy-Item -LiteralPath $Destination -Destination $saved
    Copy-Item -LiteralPath $Source -Destination $Destination -Force
}
Install-Managed (Join-Path $cacheRoot 'velocity.jar') (Join-Path $proxyRoot 'velocity.jar') 'proxy\velocity.jar'
Install-Managed (Join-Path $cacheRoot 'paper.jar') (Join-Path $survivalRoot 'paper.jar') 'survival\paper.jar'
Copy-Missing $windJar (Join-Path $lobbyRoot 'windspigot.jar')
Copy-Missing $eulaPath (Join-Path $lobbyRoot 'eula.txt')
Copy-Missing $eulaPath (Join-Path $survivalRoot 'eula.txt')
foreach ($name in @('velocity.toml', 'start.ps1', 'run.bat')) {
    Copy-Missing (Join-Path $templateRoot "proxy\$name") (Join-Path $proxyRoot $name)
}
foreach ($name in @('server.properties', 'bukkit.yml', 'spigot.yml', 'windspigot.yml', 'start.ps1', 'run.bat')) {
    Copy-Missing (Join-Path $templateRoot "lobby\$name") (Join-Path $lobbyRoot $name)
}
foreach ($name in @('ViaVersion.jar', 'ViaBackwards.jar', 'ViaRewind.jar')) {
    Install-Managed (Join-Path $cacheRoot $name) (Join-Path $lobbyRoot "plugins\$name") "lobby\plugins\$name"
    if (Test-Path -LiteralPath (Join-Path $networkRoot 'enabled.json')) {
        Install-Managed (Join-Path $cacheRoot $name) (Join-Path $repoRoot "runtime\plugins\$name") "pvp\plugins\$name"
    }
}
foreach ($name in @('server.properties', 'spigot.yml', 'start.ps1', 'run.bat')) {
    Copy-Missing (Join-Path $templateRoot "survival\$name") (Join-Path $survivalRoot $name)
}
Copy-Missing (Join-Path $templateRoot 'survival\config\paper-global.yml') (Join-Path $survivalRoot 'config\paper-global.yml')
Copy-Missing (Join-Path $repoRoot 'proxy-plugin\src\main\resources\config.properties') (Join-Path $proxyRoot 'plugins\ascending-network\config.properties')
$velocityConfig = Join-Path $proxyRoot 'velocity.toml'
$velocityText = Get-Content -LiteralPath $velocityConfig -Raw
if ($velocityText -notmatch '(?m)^survival\s*=') {
    if (-not $UpdateExisting) { throw 'Existing proxy configuration needs Survival registration. Stop all servers and rerun with -UpdateExisting.' }
    if ([regex]::Matches($velocityText, '(?m)^\[servers\]\s*$').Count -ne 1) { throw 'Cannot identify the proxy servers section safely.' }
    New-Item -ItemType Directory -Path (Join-Path $updateBackup 'proxy') -Force | Out-Null
    Copy-Item -LiteralPath $velocityConfig -Destination (Join-Path $updateBackup 'proxy\velocity.toml')
    $velocityText = [regex]::Replace($velocityText, '(?m)^(\[servers\][ \t]*)(\r?\n)', '$1$2survival = "127.0.0.1:25568"$2')
    [IO.File]::WriteAllText($velocityConfig, $velocityText, (New-Object System.Text.UTF8Encoding($false)))
}
if ([regex]::Matches($velocityText, '(?m)^survival\s*=').Count -ne 1 -or
    $velocityText -notmatch '(?m)^survival\s*=\s*["'']127\.0\.0\.1:25568["'']\s*(?:#.*)?$') {
    throw 'Existing Survival address differs from the managed localhost backend; review velocity.toml manually.'
}
$viaData = Join-Path $lobbyRoot 'plugins\ViaVersion'
New-Item -ItemType Directory -Path $viaData -Force | Out-Null
Copy-Missing (Join-Path $templateRoot 'via-config.yml') (Join-Path $viaData 'config.yml')
if (-not $SkipPluginBuild) {
    & (Join-Path $PSScriptRoot 'build-lobby-plugin.ps1')
    & (Join-Path $PSScriptRoot 'build-modern-plugins.ps1')
}
$lobbyJar = Join-Path $repoRoot 'lobby-plugin\target\PoppyLobby-0.1.0.jar'
if (Test-Path -LiteralPath $lobbyJar) {
    Install-Managed $lobbyJar (Join-Path $lobbyRoot 'plugins\PoppyLobby.jar') 'lobby\plugins\PoppyLobby.jar'
} else { Write-Warning 'Lobby plugin not built yet. Run setup-network.ps1 again without -SkipPluginBuild before starting.' }
foreach ($plugin in @(
    @{ Source='survival-plugin\target\AscendingSurvival-0.1.0.jar'; Target='survival\plugins\AscendingSurvival.jar' },
    @{ Source='proxy-plugin\target\AscendingNetwork-0.1.0.jar'; Target='proxy\plugins\AscendingNetwork.jar' }
)) {
    $built = Join-Path $repoRoot $plugin.Source
    if (Test-Path -LiteralPath $built) { Install-Managed $built (Join-Path $networkRoot $plugin.Target) $plugin.Target }
    else { Write-Warning "Missing $built. Build modern plugins before starting." }
}
Write-Host "Network files prepared: $networkRoot"
Write-Host 'Survival uses only Paper and AscendingSurvival, with no protocol translation plugins.'
if ($UpdateExisting) { Write-Host "Changed managed files were backed up to $updateBackup. No worlds or player data were replaced." }
else { Write-Host 'For initial activation, stop the existing PvP server before running scripts/enable-network.ps1.' }
