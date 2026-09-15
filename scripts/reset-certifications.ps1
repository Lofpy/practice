[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [string]$RuntimeDirectory = (Join-Path (Split-Path -Parent $PSScriptRoot) 'runtime'),
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[a-fA-F0-9]{64}$')]
    [string]$ExpectedRatingsSha256,
    [ValidateRange(1, 65535)]
    [int]$ExpectedPort = 25566,
    [string]$JavaExecutable
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$repoDirectory = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot)).TrimEnd('\')
$runtimePath = [IO.Path]::GetFullPath($RuntimeDirectory).TrimEnd('\')
$repoPrefix = $repoDirectory + '\'
if (-not $runtimePath.StartsWith($repoPrefix, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'The runtime must be an existing directory strictly inside this repository.'
}

function Assert-PlainPath([string]$Path, [bool]$Directory) {
    $entry = Get-Item -LiteralPath $Path -Force
    if ($entry.PSIsContainer -ne $Directory) { throw "Unexpected file/directory type: $Path" }
    $current = $entry
    while ($null -ne $current) {
        if (($current.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
            throw "Symbolic links/junctions are not permitted in reset targets: $($current.FullName)"
        }
        $current = if ($current -is [IO.DirectoryInfo]) { $current.Parent } else { $current.Directory }
    }
}

function Assert-PortClosed {
    $listeners = [Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners()
    if (@($listeners | Where-Object { $_.Port -eq $ExpectedPort }).Count -ne 0) {
        throw "Port $ExpectedPort is still listening. Stop only the target PvP server normally and wait for its JVM to exit."
    }
}

function Assert-RatingsUnchanged {
    if ((Get-FileHash -LiteralPath $ratingsPath -Algorithm SHA256).Hash -ne $ExpectedRatingsSha256) {
        throw 'ratings.yml differs from the explicitly reviewed SHA-256. Reinspect it before resetting.'
    }
}

Assert-PlainPath $runtimePath $true
$propertiesPath = Join-Path $runtimePath 'server.properties'
$serverJar = Join-Path $runtimePath 'windspigot.jar'
$pluginJar = Join-Path $runtimePath 'plugins\PoppyPractice.jar'
$dataPath = Join-Path $runtimePath 'plugins\PoppyPractice'
$ratingsPath = Join-Path $dataPath 'ratings.yml'
$assessmentsPath = Join-Path $dataPath 'tier-assessments'
foreach ($path in @($propertiesPath, $serverJar, $pluginJar, $ratingsPath, (Join-Path $runtimePath 'run.bat'))) {
    Assert-PlainPath $path $false
}
Assert-PlainPath $dataPath $true
$portLines = @(Get-Content -LiteralPath $propertiesPath | Where-Object { $_ -match '^\s*server-port\s*=' })
if ($portLines.Count -ne 1 -or $portLines[0] -notmatch '^\s*server-port\s*=\s*(\d+)\s*$' -or [int]$Matches[1] -ne $ExpectedPort) {
    throw 'The explicit expected port must match the single server-port entry in server.properties.'
}
Assert-PortClosed
Assert-RatingsUnchanged

if (-not $JavaExecutable) {
    $managedJdk = Join-Path $env:LOCALAPPDATA 'PoppyPracticeTools\temurin17'
    $java = Get-ChildItem -LiteralPath $managedJdk -Filter java.exe -Recurse |
        Where-Object { $_.Directory.Name -eq 'bin' } | Select-Object -First 1
    if (-not $java) { throw 'Managed Java 17 was not found. Supply -JavaExecutable with a JDK 17+ java.exe.' }
    $JavaExecutable = $java.FullName
}
$validator = Join-Path $PSScriptRoot 'java\ValidateCertificationReset.java'
& $JavaExecutable --add-modules jdk.attach --class-path ($pluginJar + ';' + $serverJar) $validator $runtimePath
if ($LASTEXITCODE -ne 0) { throw 'Read-only certification validation failed. No player data was moved.' }

$assessmentFiles = @()
if (Test-Path -LiteralPath $assessmentsPath) {
    Assert-PlainPath $assessmentsPath $true
    # Walk one directory at a time; never follow a reparse point recursively.
    $pending = New-Object 'System.Collections.Generic.Queue[string]'
    $pending.Enqueue($assessmentsPath)
    while ($pending.Count -gt 0) {
        $directory = $pending.Dequeue()
        foreach ($entry in Get-ChildItem -LiteralPath $directory -Force) {
            Assert-PlainPath $entry.FullName $entry.PSIsContainer
            if ($entry.PSIsContainer) {
                $pending.Enqueue($entry.FullName)
            } else {
                $relative = $entry.FullName.Substring($assessmentsPath.Length + 1)
                $uuidPattern = '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}'
                if ($relative -cnotmatch ('^' + $uuidPattern + '\\[a-z0-9][a-z0-9_-]{0,63}\\' + $uuidPattern + '\.yml$')) {
                    throw "Unexpected file inside tier-assessments; inspect it first: $relative"
                }
                $assessmentFiles += [PSCustomObject]@{ Relative = $relative; Sha256 = (Get-FileHash -LiteralPath $entry.FullName -Algorithm SHA256).Hash }
            }
        }
    }
}

$backupParent = Join-Path $runtimePath 'backups'
if (Test-Path -LiteralPath $backupParent) { Assert-PlainPath $backupParent $true }
$backupPath = Join-Path $backupParent ('certifications-reset-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff') + '-' + [Guid]::NewGuid().ToString('N').Substring(0, 8))
$backupPrefix = [IO.Path]::GetFullPath($backupParent).TrimEnd('\') + '\'
if (-not [IO.Path]::GetFullPath($backupPath).StartsWith($backupPrefix, [StringComparison]::OrdinalIgnoreCase) -or
    (Test-Path -LiteralPath $backupPath)) { throw 'Invalid or pre-existing backup destination.' }

Write-Host "Targets: $ratingsPath and $assessmentsPath ($($assessmentFiles.Count) assessment files)."
if (-not $PSCmdlet.ShouldProcess($dataPath, "Archive all certifications and their initial ELO to $backupPath")) { return }
Assert-PortClosed
Assert-RatingsUnchanged
New-Item -ItemType Directory -Path $backupPath | Out-Null
Assert-PlainPath $backupPath $true
$ratingsBackup = Join-Path $backupPath 'ratings.yml'
$assessmentsBackup = Join-Path $backupPath 'tier-assessments'
$movedRatings = $false
$movedAssessments = $false
try {
    Move-Item -LiteralPath $ratingsPath -Destination $ratingsBackup
    $movedRatings = $true
    if ((Get-FileHash -LiteralPath $ratingsBackup -Algorithm SHA256).Hash -ne $ExpectedRatingsSha256) {
        throw 'Archived ratings checksum differs.'
    }
    if (Test-Path -LiteralPath $assessmentsPath) {
        # Both the recursive source and destination were resolved/validated above.
        Move-Item -LiteralPath $assessmentsPath -Destination $assessmentsBackup
        $movedAssessments = $true
        foreach ($file in $assessmentFiles) {
            if ((Get-FileHash -LiteralPath (Join-Path $assessmentsBackup $file.Relative) -Algorithm SHA256).Hash -ne $file.Sha256) {
                throw "Archived assessment checksum differs: $($file.Relative)"
            }
        }
    }
    if ((Test-Path -LiteralPath $ratingsPath) -or (Test-Path -LiteralPath $assessmentsPath)) {
        throw 'A reset target reappeared. Keep the server stopped and inspect concurrent access.'
    }
} catch {
    $originalFailure = $_.Exception.Message
    $rollbackFailures = New-Object 'System.Collections.Generic.List[string]'
    if ($movedAssessments) {
        try {
            if (Test-Path -LiteralPath $assessmentsPath) { throw 'Original assessments path has reappeared; rollback will not overwrite it.' }
            Move-Item -LiteralPath $assessmentsBackup -Destination $assessmentsPath
        } catch { $rollbackFailures.Add($_.Exception.Message) }
    }
    if ($movedRatings) {
        try {
            if (Test-Path -LiteralPath $ratingsPath) { throw 'Original ratings path has reappeared; rollback will not overwrite it.' }
            Move-Item -LiteralPath $ratingsBackup -Destination $ratingsPath
        } catch { $rollbackFailures.Add($_.Exception.Message) }
    }
    throw "Reset failed: $originalFailure. Rollback errors: $($rollbackFailures -join '; '). Keep PvP stopped and preserve $backupPath."
}
Write-Host 'All certification progress and certification-derived initial ELO were reset. Each kit now needs three placements again.'
Write-Host "Recoverable backup: $backupPath"
Write-Host 'Configuration, kit layouts, cosmetic preferences, other player data, and worlds were not changed. Start through run.bat.'
