[CmdletBinding()]
param(
    [string]$RuntimeDirectory,
    [switch]$AcceptEula,
    [switch]$RefreshWindSpigot
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
if (-not $RuntimeDirectory) {
    $RuntimeDirectory = Join-Path $repoRoot 'runtime'
}
$toolsDir = Join-Path $env:LOCALAPPDATA 'PoppyPracticeTools'
$jdkRoot = Join-Path $toolsDir 'temurin17'
$windSpigotDir = Join-Path $toolsDir 'windspigot'
$windSpigotVersion = '2.1.3'
$windSpigotHash = '53D8553521474035762F0652E5DAB8F2C37007AF0980EECCE7A904D3AA84F484'
$windSpigotUri = "https://github.com/Wind-Development/WindSpigot/releases/download/v$windSpigotVersion/WindSpigot-$windSpigotVersion.jar"
$cachedWindSpigot = Join-Path $windSpigotDir "WindSpigot-$windSpigotVersion.jar"
$protocolSupportDir = Join-Path $toolsDir 'protocolsupport'
$protocolSupportVersion = 'cursedps-5'
$protocolSupportHash = '45716203C4FF0CDFA63CD303161CD456E91E1CBF9ED2D1CFF81CCB9D40FA48BC4013DFE1CE77C8FC6AF22A300DD5F77BC6BE6B87241B42C7A9DC216100DE36FE'
$protocolSupportUri = 'https://cdn.modrinth.com/data/2JRtbhi4/versions/MrPuwAir/ProtocolSupport.jar'
$cachedProtocolSupport = Join-Path $protocolSupportDir "ProtocolSupport-$protocolSupportVersion.jar"
$pluginJar = Join-Path $repoRoot 'target\PoppyPractice-0.1.0.jar'
$serverJar = Join-Path $RuntimeDirectory 'windspigot.jar'

New-Item -ItemType Directory -Path $toolsDir -Force | Out-Null
New-Item -ItemType Directory -Path $jdkRoot -Force | Out-Null
New-Item -ItemType Directory -Path $windSpigotDir -Force | Out-Null
New-Item -ItemType Directory -Path $protocolSupportDir -Force | Out-Null
New-Item -ItemType Directory -Path (Join-Path $RuntimeDirectory 'plugins') -Force | Out-Null

$javac = Get-ChildItem -LiteralPath $jdkRoot -Filter javac.exe -Recurse -ErrorAction SilentlyContinue |
    Select-Object -First 1
if (-not $javac) {
    $jdkArchive = Join-Path $toolsDir 'temurin17-jdk.zip'
    Write-Host 'Downloading the latest Eclipse Temurin 17 JDK...'
    Invoke-WebRequest -Uri 'https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse?project=jdk' `
        -OutFile $jdkArchive
    Expand-Archive -LiteralPath $jdkArchive -DestinationPath $jdkRoot -Force
    $javac = Get-ChildItem -LiteralPath $jdkRoot -Filter javac.exe -Recurse | Select-Object -First 1
}
if (-not $javac) {
    throw 'Could not locate javac.exe in the downloaded JDK.'
}
$jdkHome = Split-Path (Split-Path $javac.FullName -Parent) -Parent

$cachedHash = if (Test-Path -LiteralPath $cachedWindSpigot) {
    (Get-FileHash -LiteralPath $cachedWindSpigot -Algorithm SHA256).Hash
} else {
    $null
}
if ($RefreshWindSpigot -or $cachedHash -ne $windSpigotHash) {
    $downloadJar = "$cachedWindSpigot.download"
    Write-Host "Downloading official WindSpigot $windSpigotVersion..."
    Invoke-WebRequest -Uri $windSpigotUri -OutFile $downloadJar
    $downloadHash = (Get-FileHash -LiteralPath $downloadJar -Algorithm SHA256).Hash
    if ($downloadHash -ne $windSpigotHash) {
        throw 'WindSpigot checksum verification failed.'
    }
    Move-Item -LiteralPath $downloadJar -Destination $cachedWindSpigot -Force
}

Copy-Item -LiteralPath $cachedWindSpigot -Destination $serverJar -Force

$cachedProtocolSupportHash = if (Test-Path -LiteralPath $cachedProtocolSupport) {
    (Get-FileHash -LiteralPath $cachedProtocolSupport -Algorithm SHA512).Hash
} else {
    $null
}
if ($cachedProtocolSupportHash -ne $protocolSupportHash) {
    $downloadJar = "$cachedProtocolSupport.download"
    Write-Host "Downloading ProtocolSupport $protocolSupportVersion for Minecraft 1.7.10 clients..."
    Invoke-WebRequest -Uri $protocolSupportUri -OutFile $downloadJar
    $downloadHash = (Get-FileHash -LiteralPath $downloadJar -Algorithm SHA512).Hash
    if ($downloadHash -ne $protocolSupportHash) {
        throw 'ProtocolSupport checksum verification failed.'
    }
    Move-Item -LiteralPath $downloadJar -Destination $cachedProtocolSupport -Force
}
& (Join-Path $PSScriptRoot 'build-protocolsupport-compat.ps1') `
    -BaseJar $cachedProtocolSupport `
    -ServerJar $serverJar `
    -OutputJar (Join-Path $RuntimeDirectory 'plugins\ProtocolSupport.jar') `
    -JavaHome $jdkHome

& (Join-Path $PSScriptRoot 'build-plugin.ps1') -JavaHome $jdkHome

Copy-Item -LiteralPath $pluginJar -Destination (Join-Path $RuntimeDirectory 'plugins\PoppyPractice.jar') -Force
$legacyPluginJar = Join-Path $RuntimeDirectory 'plugins\PoppyPractice-0.1.0.jar'
if (Test-Path -LiteralPath $legacyPluginJar) {
    Remove-Item -LiteralPath $legacyPluginJar -Force
    Write-Host 'Removed the obsolete versioned PoppyPractice JAR.'
}
Copy-Item -LiteralPath (Join-Path $repoRoot 'server-template\start.ps1') `
    -Destination (Join-Path $RuntimeDirectory 'start.ps1') -Force
Copy-Item -LiteralPath (Join-Path $repoRoot 'server-template\run.bat') `
    -Destination (Join-Path $RuntimeDirectory 'run.bat') -Force
foreach ($template in @('server.properties', 'eula.txt', 'windspigot.yml', 'knockback.yml', 'bukkit.yml')) {
    $source = Join-Path $repoRoot "server-template\$template"
    $destination = Join-Path $RuntimeDirectory $template
    if (-not (Test-Path -LiteralPath $destination)) {
        Copy-Item -LiteralPath $source -Destination $destination
    }
}

$legacySpigotJar = Join-Path $RuntimeDirectory 'spigot.jar'
if (Test-Path -LiteralPath $legacySpigotJar) {
    Remove-Item -LiteralPath $legacySpigotJar -Force
    Write-Host 'Removed the obsolete Spigot server JAR.'
}

if ($AcceptEula) {
    Set-Content -LiteralPath (Join-Path $RuntimeDirectory 'eula.txt') -Value 'eula=true' -Encoding ASCII
    Write-Host 'Minecraft EULA marked as accepted because -AcceptEula was supplied.'
} else {
    Write-Warning 'Review https://aka.ms/MinecraftEULA, then rerun with -AcceptEula or edit runtime/eula.txt.'
}

Write-Host "Server assembled in: $RuntimeDirectory"
Write-Host "Start it with: powershell -ExecutionPolicy Bypass -File `"$(Join-Path $RuntimeDirectory 'start.ps1')`""
