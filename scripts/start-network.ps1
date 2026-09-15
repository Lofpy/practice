[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$networkRoot = Join-Path $repoRoot 'network'
if (-not (Test-Path -LiteralPath (Join-Path $networkRoot 'enabled.json'))) {
    throw 'Run scripts/setup-network.ps1, stop the existing server, then run scripts/enable-network.ps1 first.'
}
$definitions = @(
    @{ Name='pvp'; Directory=(Join-Path $repoRoot 'runtime'); Port=25566; Stop='stop' },
    @{ Name='lobby'; Directory=(Join-Path $networkRoot 'lobby'); Port=25567; Stop='stop' },
    @{ Name='proxy'; Directory=(Join-Path $networkRoot 'proxy'); Port=25565; Stop='end' }
)
foreach ($definition in $definitions) {
    if (Get-NetTCPConnection -State Listen -LocalPort $definition.Port -ErrorAction SilentlyContinue) {
        throw "Port $($definition.Port) is already used; no duplicate servers were started."
    }
}
$running = @{}
$eventSources = @()
if (-not ('PoppyNetworkConsoleInput' -as [type])) {
    Add-Type -TypeDefinition @'
using System;
using System.Threading.Tasks;
public static class PoppyNetworkConsoleInput {
    public static Task<string> ReadLineAsync() { return Task.Run(() => Console.ReadLine()); }
}
'@
}
$logRoot = Join-Path $networkRoot 'console'
New-Item -ItemType Directory -Path $logRoot -Force | Out-Null
try {
    foreach ($definition in $definitions) {
        $startInfo = New-Object System.Diagnostics.ProcessStartInfo
        $startInfo.FileName = $env:ComSpec
        $startInfo.Arguments = '/d /c run.bat'
        $startInfo.WorkingDirectory = $definition.Directory
        $startInfo.UseShellExecute = $false
        $startInfo.CreateNoWindow = $true
        $startInfo.WindowStyle = [System.Diagnostics.ProcessWindowStyle]::Hidden
        $startInfo.RedirectStandardInput = $true
        $startInfo.RedirectStandardOutput = $true
        $startInfo.RedirectStandardError = $true
        $startInfo.StandardOutputEncoding = New-Object System.Text.UTF8Encoding($false)
        $startInfo.StandardErrorEncoding = New-Object System.Text.UTF8Encoding($false)
        $startInfo.EnvironmentVariables['JAVA_TOOL_OPTIONS'] = '-Dfile.encoding=UTF-8'
        $process = New-Object System.Diagnostics.Process
        $process.StartInfo = $startInfo
        $logPath = Join-Path $logRoot ($definition.Name + '.log')
        $process.EnableRaisingEvents = $true
        foreach ($stream in @('OutputDataReceived','ErrorDataReceived')) {
            $eventSource = 'PoppyNetwork-' + $definition.Name + '-' + $stream + '-' + $PID
            $eventSources += $eventSource
            Register-ObjectEvent -InputObject $process -EventName $stream -SourceIdentifier $eventSource -MessageData @{Name=$definition.Name;Path=$logPath} -Action {
                if ($null -ne $EventArgs.Data) {
                    $line = '[' + $Event.MessageData.Name + '] ' + $EventArgs.Data
                    Add-Content -LiteralPath $Event.MessageData.Path -Value $line -Encoding UTF8
                    Write-Host $line
                }
            } | Out-Null
        }
        if (-not $process.Start()) { throw "Could not start $($definition.Name)." }
        $process.BeginOutputReadLine()
        $process.BeginErrorReadLine()
        $running[$definition.Name] = @{ Process=$process; Stop=$definition.Stop }
        $deadline = (Get-Date).AddSeconds(60)
        $nativeLog = Join-Path $definition.Directory 'logs\latest.log'
        while ($true) {
            if ($process.HasExited) { throw "$($definition.Name) exited before opening its port. See $logPath" }
            if ((Get-Date) -gt $deadline) { throw "Timed out starting $($definition.Name). See $logPath" }
            $listening = Get-NetTCPConnection -State Listen -LocalPort $definition.Port -ErrorAction SilentlyContinue
            $logFile = Get-Item -LiteralPath $nativeLog -ErrorAction SilentlyContinue
            if ($listening -and $logFile -and $logFile.LastWriteTimeUtc -ge $process.StartTime.ToUniversalTime() -and
                (Select-String -LiteralPath $nativeLog -Pattern 'Done \(' -Quiet)) { break }
            # WindSpigot binds its port before loading worlds/plugins; do not open the entry point early.
            Start-Sleep -Milliseconds 250
        }
        Write-Host "$($definition.Name) started through run.bat on port $($definition.Port)."
    }
    Write-Host 'Poppy Network running. Commands: pvp <command>, lobby <command>, proxy <command>, status, stopall'
    while ($true) {
        Write-Host 'network> ' -NoNewline
        $inputTask = [PoppyNetworkConsoleInput]::ReadLineAsync()
        # Read-Host blocks PowerShell event actions; polling lets stdout/stderr logs keep draining.
        while (-not $inputTask.IsCompleted) { Start-Sleep -Milliseconds 100 }
        $line = $inputTask.GetAwaiter().GetResult()
        if ($null -eq $line -or $line -eq 'stopall') { break }
        if ($line -eq 'status') {
            foreach ($name in @('pvp','lobby','proxy')) { Write-Host "$name running=$(-not $running[$name].Process.HasExited)" }
            continue
        }
        if ($line -match '^(pvp|lobby|proxy)\s+(.+)$') {
            $child = $running[$Matches[1]].Process
            if ($child.HasExited) { Write-Warning 'That server has stopped.'; continue }
            try {
                $child.StandardInput.WriteLine($Matches[2])
                $child.StandardInput.Flush()
            } catch { Write-Warning "That server's console closed: $_" }
        } elseif ($line) { Write-Host 'Use: pvp list | lobby list | proxy velocity info | status | stopall' }
    }
} finally {
    # Close the entry point first, then save both backends. Never forcibly kill a world writer.
    foreach ($name in @('proxy','lobby','pvp')) {
        if ($running.ContainsKey($name) -and -not $running[$name].Process.HasExited) {
            try {
                $running[$name].Process.StandardInput.WriteLine($running[$name].Stop)
                $running[$name].Process.StandardInput.Flush()
            } catch { Write-Warning "Could not send $name its normal stop command: $_" }
        }
    }
    foreach ($name in @('proxy','lobby','pvp')) {
        if ($running.ContainsKey($name)) {
            $saveDeadline = (Get-Date).AddSeconds(60)
            while (-not $running[$name].Process.HasExited -and (Get-Date) -lt $saveDeadline) {
                Start-Sleep -Milliseconds 250
            }
            if (-not $running[$name].Process.HasExited) { Write-Warning "$name is still saving. It was NOT forcibly terminated." }
        }
    }
    Start-Sleep -Milliseconds 200
    foreach ($eventSource in $eventSources) { Unregister-Event -SourceIdentifier $eventSource -ErrorAction SilentlyContinue }
    foreach ($server in $running.Values) { if ($server.Process.HasExited) { $server.Process.Dispose() } }
}
