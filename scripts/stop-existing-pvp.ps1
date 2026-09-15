[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$runtimeRoot = Join-Path $repoRoot 'runtime'
$properties = Get-Content -LiteralPath (Join-Path $runtimeRoot 'server.properties') -Raw
if ($properties -notmatch '(?m)^server-port=(\d+)') { throw 'Cannot identify current PvP port.' }
$serverPort = [int]$Matches[1]
$listeners = @(Get-NetTCPConnection -State Listen -LocalPort $serverPort -ErrorAction SilentlyContinue)
if ($listeners.Count -eq 0) { Write-Host 'PvP is already stopped.'; return }
$processIds = @($listeners.OwningProcess | Select-Object -Unique)
if ($processIds.Count -ne 1) { throw 'Ambiguous listener ownership; stop the server manually.' }
$serverProcess = Get-CimInstance Win32_Process -Filter "ProcessId = $($processIds[0])"
if ($serverProcess.Name -ne 'java.exe' -or $serverProcess.CommandLine -notmatch '-jar\s+windspigot\.jar') {
    throw 'Listener is not the expected WindSpigot JVM; stop it manually.'
}
$managedJdk = Join-Path $env:LOCALAPPDATA 'PoppyPracticeTools\temurin17'
$compiler = Get-ChildItem -LiteralPath $managedJdk -Filter javac.exe -Recurse | Select-Object -First 1
if (-not $compiler) { throw 'Managed Java 17 JDK missing.' }
$javaBin = $compiler.Directory.FullName
$buildRoot = Join-Path $repoRoot 'tmp\network-process-control'
New-Item -ItemType Directory -Path $buildRoot -Force | Out-Null
& $compiler.FullName -encoding UTF-8 -d $buildRoot (Join-Path $PSScriptRoot 'java\NetworkGracefulStop.java')
if ($LASTEXITCODE -ne 0) { throw 'Shutdown helper compilation failed.' }
$agentJar = Join-Path $buildRoot 'NetworkGracefulStop.jar'
& (Join-Path $javaBin 'jar.exe') cfm $agentJar (Join-Path $PSScriptRoot 'java\network-stop-manifest.mf') -C $buildRoot NetworkGracefulStop.class
if ($LASTEXITCODE -ne 0) { throw 'Shutdown helper packaging failed.' }
& (Join-Path $javaBin 'java.exe') --add-modules jdk.attach -cp $agentJar NetworkGracefulStop $processIds[0] $agentJar $runtimeRoot $serverPort
if ($LASTEXITCODE -ne 0) { throw 'Graceful stop request failed; server was not forcibly killed.' }
$deadline = (Get-Date).AddSeconds(60)
while (Get-Process -Id $processIds[0] -ErrorAction SilentlyContinue) {
    if ((Get-Date) -gt $deadline) { throw 'PvP is still saving; wait or inspect its console. It was not forcibly killed.' }
    Start-Sleep -Milliseconds 250
}
Write-Host 'PvP saved and stopped normally.'
