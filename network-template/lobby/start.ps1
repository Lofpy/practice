[CmdletBinding()]
param([string]$MaximumMemory = '1G')
$ErrorActionPreference = 'Stop'
$runtimeRoot = Join-Path $env:LOCALAPPDATA 'PoppyPracticeTools\temurin17'
$javaBinary = Get-ChildItem -LiteralPath $runtimeRoot -Filter java.exe -Recurse -ErrorAction SilentlyContinue |
    Where-Object { $_.Directory.Name -eq 'bin' } | Select-Object -First 1
if (-not $javaBinary) { throw 'Managed Java 17 is missing. Run scripts/setup-server.ps1.' }
Push-Location -LiteralPath $PSScriptRoot
try {
    & $javaBinary.FullName '-Dfile.encoding=UTF-8' -Xms256M "-Xmx$MaximumMemory" -XX:+UseG1GC -jar windspigot.jar nogui
    if ($LASTEXITCODE -ne 0) { throw "Lobby exited with code $LASTEXITCODE" }
} finally { Pop-Location }
