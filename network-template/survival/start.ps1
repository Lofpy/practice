[CmdletBinding()]
param([string]$MaximumMemory = '2G')
$ErrorActionPreference = 'Stop'
if ($MaximumMemory -notmatch '^[1-9][0-9]*[MG]$') { throw 'Invalid memory size.' }
$survivalJavaRoot = Join-Path $env:LOCALAPPDATA 'PoppyPracticeTools\temurin25-network'
$survivalJava = Get-ChildItem -LiteralPath $survivalJavaRoot -Filter java.exe -Recurse -ErrorAction SilentlyContinue |
    Where-Object { $_.Directory.Name -eq 'bin' } | Select-Object -First 1
if (-not $survivalJava) { throw 'Managed Java 25 is missing. Run scripts/setup-network.ps1.' }
$survivalEula = Join-Path $PSScriptRoot 'eula.txt'
if (-not (Test-Path -LiteralPath $survivalEula) -or
    (Get-Content -LiteralPath $survivalEula -Raw) -notmatch '(?m)^eula=true\s*$') {
    throw 'Operator EULA acceptance required. This script never accepts terms automatically.'
}
$survivalPlugin = Join-Path $PSScriptRoot 'plugins\AscendingSurvival.jar'
if (-not (Test-Path -LiteralPath $survivalPlugin -PathType Leaf)) {
    throw 'Managed AscendingSurvival.jar is missing. Run scripts/setup-network.ps1 before starting.'
}
Push-Location -LiteralPath $PSScriptRoot
try {
    & $survivalJava.FullName '-Dfile.encoding=UTF-8' '-cp' $survivalPlugin 'com.ascendingmc.survival.OfflineWorldRegenerator' $PSScriptRoot
    if ($LASTEXITCODE -ne 0) { throw "Offline world regeneration failed with code $LASTEXITCODE. Paper was not started." }
    & $survivalJava.FullName '-Dfile.encoding=UTF-8' -Xms256M "-Xmx$MaximumMemory" -XX:+UseG1GC -jar paper.jar nogui
    if ($LASTEXITCODE -ne 0) { throw "Survival exited with code $LASTEXITCODE" }
} finally { Pop-Location }
