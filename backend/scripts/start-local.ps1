param(
    [int]$Port = 18082,
    [string]$DatabasePassword = $env:DB_PASSWORD
)

$ErrorActionPreference = 'Stop'

$backendRoot = Split-Path -Parent $PSScriptRoot
$projectRoot = Split-Path -Parent $backendRoot
$jarPath = Join-Path $backendRoot 'starfree-replacement\target\starfree-replacement-0.1.0-SNAPSHOT.jar'
$runDir = Join-Path $backendRoot '.local\run'
$pidFile = Join-Path $runDir 'starfree-replacement.pid'
$logFile = Join-Path $runDir 'starfree-replacement.log'
$secretsFile = Join-Path $runDir 'application-secrets.yml'

New-Item -ItemType Directory -Force -Path $runDir | Out-Null

if ([string]::IsNullOrWhiteSpace($DatabasePassword) -and (Test-Path -LiteralPath $secretsFile)) {
    $passwordLine = Get-Content -LiteralPath $secretsFile |
        Where-Object { $_ -match '^\s+password:' } |
        Select-Object -First 1
    if ($passwordLine) {
        $DatabasePassword = ($passwordLine -replace '^\s+password:\s*', '').Trim()
        if ($DatabasePassword.StartsWith("'") -and $DatabasePassword.EndsWith("'")) {
            $unquotedPassword = $DatabasePassword.Substring(1, $DatabasePassword.Length - 2)
            $DatabasePassword = $unquotedPassword.Replace("''", "'")
        }
    }
}

if ([string]::IsNullOrWhiteSpace($DatabasePassword)) {
    throw 'Database password is required. Pass -DatabasePassword once or set DB_PASSWORD.'
}

if (-not (Test-Path -LiteralPath $jarPath)) {
    throw "Backend jar not found: $jarPath"
}

$listener = Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue
if ($listener) {
    throw "Port $Port is already in use by PID $($listener[0].OwningProcess)."
}

$escapedPassword = ([string]$DatabasePassword).Replace("'", "''")
$secrets = @(
    'spring:',
    '  datasource:',
    ("    password: '{0}'" -f $escapedPassword)
)
Set-Content -LiteralPath $secretsFile -Value $secrets -Encoding utf8

$java = (Get-Command java -ErrorAction Stop).Source
$arguments = @(
    '-jar',
    ('"{0}"' -f $jarPath),
    ('--spring.config.additional-location="file:{0}"' -f $secretsFile.Replace('\', '/')),
    ('--spring.profiles.active=local'),
    ('--server.port={0}' -f $Port),
    ('--logging.file.name="{0}"' -f $logFile)
) -join ' '

$startInfo = New-Object System.Diagnostics.ProcessStartInfo
$startInfo.FileName = $java
$startInfo.Arguments = $arguments
$startInfo.WorkingDirectory = $projectRoot
$startInfo.UseShellExecute = $true
$startInfo.CreateNoWindow = $false
$startInfo.WindowStyle = [System.Diagnostics.ProcessWindowStyle]::Hidden

$process = New-Object System.Diagnostics.Process
$process.StartInfo = $startInfo
if (-not $process.Start()) {
    throw 'Java backend process did not start.'
}

$healthUrl = "http://127.0.0.1:$Port/health"
$ready = $false
for ($attempt = 0; $attempt -lt 20; $attempt++) {
    Start-Sleep -Seconds 1
    try {
        $health = Invoke-RestMethod -Uri $healthUrl -Method Get -TimeoutSec 2
        if ($health.code -eq 1 -and $health.data.status -eq 'UP') {
            $ready = $true
            break
        }
    } catch {
        # The application is still initializing.
    }
}
if (-not $ready) {
    throw "Backend did not become ready at $healthUrl. Check $logFile"
}

$runtimePid = $process.Id
$listenerLine = netstat -ano -p tcp | Select-String -Pattern (":{0}\s+.*LISTENING\s+(\d+)$" -f $Port) | Select-Object -First 1
if ($listenerLine -and $listenerLine.Matches.Count -gt 0) {
    $runtimePid = [int]$listenerLine.Matches[0].Groups[1].Value
}

Set-Content -LiteralPath $pidFile -Value $runtimePid -Encoding ascii
Write-Output "Started starfree-replacement on http://127.0.0.1:$Port (PID $runtimePid)."
Write-Output "Log: $logFile"
