[CmdletBinding()]
param([string]$MaximumMemory = '512M')
$ErrorActionPreference = 'Stop'
$runtimeRoot = Join-Path $env:LOCALAPPDATA 'PoppyPracticeTools\temurin25-network'
$javaBinary = Get-ChildItem -LiteralPath $runtimeRoot -Filter java.exe -Recurse -ErrorAction SilentlyContinue |
    Where-Object { $_.Directory.Name -eq 'bin' } | Select-Object -First 1
if (-not $javaBinary) { throw 'Managed Java 25 is missing. Run scripts/setup-network.ps1.' }
Push-Location -LiteralPath $PSScriptRoot
try {
    & $javaBinary.FullName -Xms128M "-Xmx$MaximumMemory" -XX:+UseG1GC -jar velocity.jar
    if ($LASTEXITCODE -ne 0) { throw "Velocity exited with code $LASTEXITCODE" }
} finally { Pop-Location }
