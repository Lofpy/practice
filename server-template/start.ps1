[CmdletBinding()]
param(
    [string]$MaximumMemory = '2G'
)

$ErrorActionPreference = 'Stop'
$serverDirectory = $PSScriptRoot
$jdkRoot = Join-Path $env:LOCALAPPDATA 'PoppyPracticeTools\temurin17'
$java = Get-ChildItem -LiteralPath $jdkRoot -Filter java.exe -Recurse -ErrorAction SilentlyContinue |
    Where-Object { $_.Directory.Name -eq 'bin' } | Select-Object -First 1
if (-not $java) {
    throw 'The managed Java 17 runtime was not found. Run scripts/setup-server.ps1 first.'
}
$javaPath = $java.FullName

Push-Location $serverDirectory
try {
    & $javaPath -Xms512M "-Xmx$MaximumMemory" -XX:+UseG1GC -jar windspigot.jar nogui
} finally {
    Pop-Location
}
