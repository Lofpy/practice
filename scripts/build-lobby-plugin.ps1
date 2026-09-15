[CmdletBinding()]
param([string]$JavaHome)

$ErrorActionPreference = 'Stop'
$lobbyRepoRoot = Split-Path -Parent $PSScriptRoot
$lobbyProject = Join-Path $lobbyRepoRoot 'lobby-plugin'
$lobbyToolHome = Join-Path $env:LOCALAPPDATA 'PoppyPracticeTools'
$lobbyMaven = Join-Path $lobbyToolHome 'apache-maven-3.9.11\bin\mvn.cmd'
if (-not (Test-Path -LiteralPath $lobbyMaven)) {
    $lobbyMaven = Join-Path $lobbyRepoRoot '.tools\apache-maven-3.9.11\bin\mvn.cmd'
}
if (-not (Test-Path -LiteralPath $lobbyMaven)) {
    throw 'Maven 3.9.11 is missing. Run the existing setup-server.ps1 first.'
}
if (-not (Test-Path -LiteralPath (Join-Path $lobbyRepoRoot 'runtime\windspigot.jar'))) {
    throw 'runtime\windspigot.jar is required to build the standalone lobby.'
}
if (-not $JavaHome) {
    $JavaHome = Join-Path $lobbyRepoRoot '.tools\temurin8\jdk8u492-b09'
}
if (-not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin\javac.exe'))) {
    throw 'A valid JDK is required. Pass -JavaHome pointing to its directory.'
}
$lobbyPreviousJavaHome = $env:JAVA_HOME
try {
    $env:JAVA_HOME = $JavaHome
    & $lobbyMaven "-Dmaven.repo.local=$(Join-Path $lobbyToolHome 'm2')" -f (Join-Path $lobbyProject 'pom.xml') clean package
    if ($LASTEXITCODE -ne 0) { throw "Lobby build failed with exit code $LASTEXITCODE." }
} finally {
    $env:JAVA_HOME = $lobbyPreviousJavaHome
}
Write-Host "Lobby plugin ready: $(Join-Path $lobbyProject 'target\PoppyLobby-0.1.0.jar')"
