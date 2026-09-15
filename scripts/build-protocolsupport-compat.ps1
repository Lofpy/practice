[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$BaseJar,
    [Parameter(Mandatory = $true)]
    [string]$ServerJar,
    [Parameter(Mandatory = $true)]
    [string]$OutputJar,
    [string]$JavaHome
)

$ErrorActionPreference = 'Stop'
$expectedBaseHash = '45716203C4FF0CDFA63CD303161CD456E91E1CBF9ED2D1CFF81CCB9D40FA48BC4013DFE1CE77C8FC6AF22A300DD5F77BC6BE6B87241B42C7A9DC216100DE36FE'
$repoRoot = Split-Path -Parent $PSScriptRoot
$baseJarPath = (Resolve-Path -LiteralPath $BaseJar).Path
$serverJarPath = (Resolve-Path -LiteralPath $ServerJar).Path
$actualBaseHash = (Get-FileHash -LiteralPath $baseJarPath -Algorithm SHA512).Hash
if ($actualBaseHash -ne $expectedBaseHash) {
    throw 'The ProtocolSupport base JAR checksum does not match CursedPS release 5.'
}

if (-not $JavaHome) {
    $jdkRoot = Join-Path $env:LOCALAPPDATA 'PoppyPracticeTools\temurin17'
    $javacCandidate = Get-ChildItem -LiteralPath $jdkRoot -Filter javac.exe -Recurse -ErrorAction SilentlyContinue |
        Where-Object { $_.Directory.Name -eq 'bin' } | Select-Object -First 1
    if (-not $javacCandidate) {
        throw 'Managed Java 17 was not found. Run scripts/setup-server.ps1 first.'
    }
    $JavaHome = Split-Path (Split-Path $javacCandidate.FullName -Parent) -Parent
}

$javac = Join-Path $JavaHome 'bin\javac.exe'
$jar = Join-Path $JavaHome 'bin\jar.exe'
if (-not (Test-Path -LiteralPath $javac) -or -not (Test-Path -LiteralPath $jar)) {
    throw 'JavaHome does not contain javac.exe and jar.exe.'
}

$sourceRoot = Join-Path $repoRoot 'compat\protocolsupport\src'
$sources = @(
    (Join-Path $sourceRoot 'protocolsupport\protocol\transformer\v_1_7\LoginListener.java'),
    (Join-Path $sourceRoot 'protocolsupport\protocol\transformer\v_1_8\LoginListener.java')
)
$temporaryRoot = Join-Path ([IO.Path]::GetTempPath()) ("PoppyPractice-ProtocolSupport-" + [Guid]::NewGuid().ToString('N'))
$classesDirectory = Join-Path $temporaryRoot 'classes'

try {
    New-Item -ItemType Directory -Path $classesDirectory -Force | Out-Null
    & $javac -source 8 -target 8 -encoding UTF-8 `
        -classpath "$serverJarPath;$baseJarPath" -d $classesDirectory $sources
    if ($LASTEXITCODE -ne 0) {
        throw "ProtocolSupport compatibility compilation failed with exit code $LASTEXITCODE."
    }

    $outputDirectory = Split-Path -Parent $OutputJar
    if ($outputDirectory) {
        New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
    }
    Copy-Item -LiteralPath $baseJarPath -Destination $OutputJar -Force
    $outputJarPath = (Resolve-Path -LiteralPath $OutputJar).Path

    Push-Location $classesDirectory
    try {
        & $jar uf $outputJarPath `
            'protocolsupport/protocol/transformer/v_1_7/LoginListener.class' `
            'protocolsupport/protocol/transformer/v_1_8/LoginListener.class'
        if ($LASTEXITCODE -ne 0) {
            throw "Could not update ProtocolSupport JAR; jar.exe exited with $LASTEXITCODE."
        }
    } finally {
        Pop-Location
    }
} finally {
    if (Test-Path -LiteralPath $temporaryRoot) {
        $resolvedTemporaryRoot = (Resolve-Path -LiteralPath $temporaryRoot).Path
        $systemTemporaryRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\')
        if (-not $resolvedTemporaryRoot.StartsWith($systemTemporaryRoot + '\', [StringComparison]::OrdinalIgnoreCase)) {
            throw "Refusing to remove a path outside the temporary directory: $resolvedTemporaryRoot"
        }
        Remove-Item -LiteralPath $resolvedTemporaryRoot -Recurse -Force
    }
}

Write-Host "WindSpigot-compatible ProtocolSupport ready: $((Resolve-Path -LiteralPath $OutputJar).Path)"
