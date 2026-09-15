[CmdletBinding()]
param(
    [string]$JavaHome
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$toolsDir = Join-Path $env:LOCALAPPDATA 'PoppyPracticeTools'
$mavenVersion = '3.9.11'
$mavenHome = Join-Path $toolsDir "apache-maven-$mavenVersion"
$mavenCommand = Join-Path $mavenHome 'bin\mvn.cmd'
$windSpigotJar = Join-Path $repoRoot 'runtime\windspigot.jar'

if (-not (Test-Path -LiteralPath $windSpigotJar)) {
    throw 'runtime\windspigot.jar is required to build the player NPC bot. Run setup-server.ps1 first.'
}

if (-not (Test-Path -LiteralPath $mavenCommand)) {
    New-Item -ItemType Directory -Path $toolsDir -Force | Out-Null
    $archive = Join-Path $toolsDir "apache-maven-$mavenVersion-bin.zip"
    $checksumFile = "$archive.sha512"
    $baseUri = "https://archive.apache.org/dist/maven/maven-3/$mavenVersion/binaries"
    Write-Host "Downloading Apache Maven $mavenVersion..."
    Invoke-WebRequest -Uri "$baseUri/apache-maven-$mavenVersion-bin.zip" -OutFile $archive
    Invoke-WebRequest -Uri "$baseUri/apache-maven-$mavenVersion-bin.zip.sha512" -OutFile $checksumFile
    $expected = ((Get-Content -LiteralPath $checksumFile -Raw).Trim() -split '\s+')[0]
    $actual = (Get-FileHash -LiteralPath $archive -Algorithm SHA512).Hash
    if ($actual -ne $expected) {
        throw 'Apache Maven checksum verification failed.'
    }
    Expand-Archive -LiteralPath $archive -DestinationPath $toolsDir -Force
}

if ($JavaHome) {
    $env:JAVA_HOME = $JavaHome
} elseif (-not $env:JAVA_HOME -or -not (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\javac.exe'))) {
    $javac = Get-Command javac.exe -ErrorAction SilentlyContinue
    if (-not $javac) {
        throw 'A JDK is required to build the plugin. Run setup-server.ps1 or pass -JavaHome.'
    }
    $env:JAVA_HOME = Split-Path (Split-Path $javac.Source -Parent) -Parent
}

Write-Host "Building from: $repoRoot"
Push-Location -LiteralPath $repoRoot
try {
    & $mavenCommand "-Dmaven.repo.local=$(Join-Path $toolsDir 'm2')" clean package
    if ($LASTEXITCODE -ne 0) {
        throw "Maven failed with exit code $LASTEXITCODE."
    }
} finally {
    Pop-Location
}

Write-Host "Plugin ready: $(Join-Path $repoRoot 'target\PoppyPractice-0.1.0.jar')"
