[CmdletBinding()]
param([string]$JavaHome)
$ErrorActionPreference = 'Stop'
$modernRepoRoot = Split-Path -Parent $PSScriptRoot
$modernToolRoot = Join-Path $env:LOCALAPPDATA 'PoppyPracticeTools'
$modernMaven = Join-Path $modernToolRoot 'apache-maven-3.9.11\bin\mvn.cmd'
if (-not (Test-Path -LiteralPath $modernMaven)) {
    $modernMaven = Join-Path $modernRepoRoot '.tools\apache-maven-3.9.11\bin\mvn.cmd'
}
if (-not (Test-Path -LiteralPath $modernMaven)) { throw 'Maven is missing. Run scripts/setup-server.ps1 first.' }
if (-not $JavaHome) {
    $modernCompiler = Get-ChildItem -LiteralPath (Join-Path $modernToolRoot 'temurin25-survival-jdk') -Filter javac.exe -Recurse -ErrorAction SilentlyContinue |
        Where-Object { $_.Directory.Name -eq 'bin' } | Select-Object -First 1
    if ($modernCompiler) { $JavaHome = $modernCompiler.Directory.Parent.FullName }
}
if (-not $JavaHome -or -not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin\javac.exe'))) {
    throw 'A Java 25 JDK is required. Run setup-network.ps1 or pass -JavaHome.'
}
$modernPreviousJavaHome = $env:JAVA_HOME
try {
    $env:JAVA_HOME = $JavaHome
    foreach ($module in @('survival-plugin','proxy-plugin')) {
        & $modernMaven "-Dmaven.repo.local=$(Join-Path $modernToolRoot 'm2')" -f (Join-Path $modernRepoRoot "$module\pom.xml") clean verify
        if ($LASTEXITCODE -ne 0) { throw "$module build failed with exit code $LASTEXITCODE." }
    }
} finally { $env:JAVA_HOME = $modernPreviousJavaHome }
Write-Host 'Survival and protocol-gate plugins are ready.'
