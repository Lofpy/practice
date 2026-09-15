[CmdletBinding()]
param([switch]$SkipPluginBuild)
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$networkRoot = Join-Path $repoRoot 'network'
$templateRoot = Join-Path $repoRoot 'network-template'
$cacheRoot = Join-Path $env:LOCALAPPDATA 'PoppyPracticeTools\network-cache'
$java25Root = Join-Path $env:LOCALAPPDATA 'PoppyPracticeTools\temurin25-network'
$lobbyRoot = Join-Path $networkRoot 'lobby'
$proxyRoot = Join-Path $networkRoot 'proxy'
$eulaPath = Join-Path $repoRoot 'runtime\eula.txt'
$windJar = Join-Path $repoRoot 'runtime\windspigot.jar'
if (-not (Test-Path -LiteralPath $windJar)) { throw 'Existing runtime/windspigot.jar is required.' }
if (-not (Test-Path -LiteralPath $eulaPath) -or (Get-Content -LiteralPath $eulaPath -Raw) -notmatch '(?m)^eula=true\s*$') {
    throw 'The existing operator-accepted runtime/eula.txt is required; this script does not accept new terms.'
}
foreach ($directory in @($cacheRoot, $java25Root, $networkRoot, $proxyRoot, $lobbyRoot, (Join-Path $lobbyRoot 'plugins'))) {
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
}
$manifest = Get-Content -LiteralPath (Join-Path $templateRoot 'artifacts.json') -Raw | ConvertFrom-Json
foreach ($artifact in $manifest.artifacts) {
    $cachedFile = Join-Path $cacheRoot $artifact.name
    if ((Test-Path -LiteralPath $cachedFile) -and (Get-FileHash -LiteralPath $cachedFile).Hash -eq $artifact.sha256) { continue }
    Write-Host "Downloading $($artifact.name) $($artifact.version)..."
    $downloadFile = "$cachedFile.download"
    Invoke-WebRequest -UseBasicParsing -Uri $artifact.url -OutFile $downloadFile -Headers @{ 'User-Agent' = 'PoppyPracticeNetworkSetup/1.0' }
    if ((Get-FileHash -LiteralPath $downloadFile).Hash -ne $artifact.sha256) { throw "Checksum mismatch: $($artifact.name)" }
    Move-Item -LiteralPath $downloadFile -Destination $cachedFile -Force
}
if (-not (Get-ChildItem -LiteralPath $java25Root -Filter java.exe -Recurse | Select-Object -First 1)) {
    Expand-Archive -LiteralPath (Join-Path $cacheRoot 'temurin25-network.zip') -DestinationPath $java25Root
}
function Copy-Missing([string]$Source, [string]$Destination) {
    if (-not (Test-Path -LiteralPath $Destination)) { Copy-Item -LiteralPath $Source -Destination $Destination }
}
Copy-Missing (Join-Path $cacheRoot 'velocity.jar') (Join-Path $proxyRoot 'velocity.jar')
Copy-Missing $windJar (Join-Path $lobbyRoot 'windspigot.jar')
Copy-Missing $eulaPath (Join-Path $lobbyRoot 'eula.txt')
foreach ($name in @('velocity.toml', 'start.ps1', 'run.bat')) {
    Copy-Missing (Join-Path $templateRoot "proxy\$name") (Join-Path $proxyRoot $name)
}
foreach ($name in @('server.properties', 'bukkit.yml', 'spigot.yml', 'windspigot.yml', 'start.ps1', 'run.bat')) {
    Copy-Missing (Join-Path $templateRoot "lobby\$name") (Join-Path $lobbyRoot $name)
}
foreach ($name in @('ViaVersion.jar', 'ViaBackwards.jar', 'ViaRewind.jar')) {
    Copy-Missing (Join-Path $cacheRoot $name) (Join-Path $lobbyRoot "plugins\$name")
}
$viaData = Join-Path $lobbyRoot 'plugins\ViaVersion'
New-Item -ItemType Directory -Path $viaData -Force | Out-Null
Copy-Missing (Join-Path $templateRoot 'via-config.yml') (Join-Path $viaData 'config.yml')
if (-not $SkipPluginBuild) { & (Join-Path $PSScriptRoot 'build-lobby-plugin.ps1') }
$lobbyJar = Join-Path $repoRoot 'lobby-plugin\target\PoppyLobby-0.1.0.jar'
if (Test-Path -LiteralPath $lobbyJar) {
    Copy-Missing $lobbyJar (Join-Path $lobbyRoot 'plugins\PoppyLobby.jar')
} else { Write-Warning 'Lobby plugin not built yet. Run setup-network.ps1 again without -SkipPluginBuild before starting.' }
Write-Host "Network files prepared: $networkRoot"
Write-Host 'Existing PvP server was NOT modified. Stop it before running scripts/enable-network.ps1.'
