[CmdletBinding()]
param(
    [ValidateSet('replacement-backend', 'legacy-api', 'admin', 'all')]
    [string]$Component = 'replacement-backend',
    [string]$Ref = 'HEAD',
    [string]$ServerHost = $env:LCXQY_SSH_HOST,
    [string]$ServerUser = $env:LCXQY_SSH_USER,
    [int]$ServerPort = 22,
    [string]$IdentityFile = $env:LCXQY_SSH_KEY,
    [string]$RemoteRoot = '/srv/lcxqy',
    [switch]$DryRun,
    [switch]$ConfirmProduction,
    [switch]$RunMigrations,
    [switch]$BootstrapServer
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$serverEntrypoint = Join-Path $PSScriptRoot 'server/lcxqy-deploy.sh'
$rollbackEntrypoint = Join-Path $PSScriptRoot 'server/lcxqy-rollback.sh'

function Invoke-Git([string[]]$Arguments) {
    $result = & git -C $repoRoot @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw "git $($Arguments -join ' ') failed: $result" }
    return (($result | Out-String).Trim())
}

function Assert-Tool([string]$Name) {
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Required command not found: $Name"
    }
}

function Resolve-Maven {
    $command = Get-Command mvn -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }
    $candidates = @(
        (Join-Path $repoRoot 'tools/apache-maven-3.9.11/bin/mvn.cmd'),
        (Join-Path $repoRoot 'backend/.local/tools/apache-maven-3.9.11/bin/mvn.cmd'),
        (Join-Path $repoRoot 'backend/.local/tools/apache-maven-3.9.9/bin/mvn.cmd')
    )
    foreach ($candidate in $candidates) {
        if (Test-Path -LiteralPath $candidate -PathType Leaf) { return $candidate }
    }
    throw 'Maven was not found in PATH or the local project tool directories.'
}

Assert-Tool git
Assert-Tool tar
Assert-Tool ssh
Assert-Tool scp

$status = Invoke-Git @('status', '--porcelain')
if ($status) { throw "The working tree is not clean. Commit or resolve these files first:`n$status" }
$commit = Invoke-Git @('rev-parse', "$Ref^{commit}")
if ($commit -notmatch '^[0-9a-f]{40}$') { throw "Cannot resolve commit: $Ref" }
$head = Invoke-Git @('rev-parse', 'HEAD')
if ($commit -ne $head) { throw "The checked-out HEAD must be the release commit. HEAD=$head, requested=$commit" }
$branch = Invoke-Git @('branch', '--show-current')
if (-not $branch) { throw 'Detached HEAD is not accepted. Check out the release branch first.' }
$remoteCommit = Invoke-Git @('rev-parse', "origin/$branch^{commit}")
if ($remoteCommit -ne $commit) {
    throw "The release commit must already be pushed to origin/$branch. local=$commit, remote=$remoteCommit"
}

if ($RunMigrations) {
    throw 'Database migrations are not supported by the generic deploy. Follow DEPLOYMENT_GUIDE.md in a separate maintenance task.'
}
if (-not $DryRun -and -not $ConfirmProduction) {
    $DryRun = $true
    Write-Warning 'ConfirmProduction was not supplied. This invocation is now a dry-run.'
}
if (-not $DryRun -and (-not $ServerHost -or -not $ServerUser)) {
    throw 'A real deploy requires ServerHost and ServerUser or the matching LCXQY environment variables.'
}
if ($IdentityFile -and -not (Test-Path -LiteralPath $IdentityFile -PathType Leaf)) {
    throw "SSH private key does not exist: $IdentityFile"
}

$stage = Join-Path ([IO.Path]::GetTempPath()) ("lcxqy-release-" + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $stage -Force | Out-Null
try {
    $manifest = @(
        "COMPONENT=$Component",
        "COMMIT=$commit",
        "CREATED_UTC=$([DateTime]::UtcNow.ToString('o'))"
    )
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [IO.File]::WriteAllText(
        (Join-Path $stage 'manifest.env'),
        (($manifest -join "`n") + "`n"),
        $utf8NoBom
    )

    $effectiveComponents = if ($Component -eq 'all') {
        @('replacement-backend', 'legacy-api', 'admin')
    } else {
        @($Component)
    }
    foreach ($item in $effectiveComponents) {
        switch ($item) {
            'replacement-backend' {
                $maven = Resolve-Maven
                $backend = Join-Path $repoRoot 'backend/starfree-replacement'
                Push-Location $backend
                try {
                    Write-Host 'Running replacement backend tests...'
                    & $maven -q test
                    if ($LASTEXITCODE -ne 0) { throw 'Replacement backend tests failed.' }
                    & $maven -q package -DskipTests
                    if ($LASTEXITCODE -ne 0) { throw 'Replacement backend package failed.' }
                } finally {
                    Pop-Location
                }
                $jar = Get-ChildItem (Join-Path $backend 'target/*.jar') |
                    Where-Object { $_.Name -notmatch 'original' } |
                    Select-Object -First 1
                if (-not $jar) { throw 'Replacement backend JAR not found.' }
                Copy-Item $jar.FullName (Join-Path $stage 'replacement-backend.jar')
            }
            'legacy-api' {
                $jar = Join-Path $repoRoot 'backend/legacy-api/dist/StarFreeApi.jar'
                if (-not (Test-Path -LiteralPath $jar)) { throw "Legacy API JAR not found: $jar" }
                $hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $jar).Hash.ToLowerInvariant()
                $expected = 'c2daa75c2c6a2968bea2d72783fc4a6844c666306daeacdf936e31dc9cb89c26'
                if ($hash -ne $expected) { throw "Legacy API JAR SHA-256 mismatch: $hash" }
                Copy-Item $jar (Join-Path $stage 'legacy-api.jar')
            }
            'admin' {
                $admin = Join-Path $repoRoot 'admin/starfree-admin'
                $php = Get-Command php -ErrorAction SilentlyContinue
                if ($php) {
                    Get-ChildItem $admin -Recurse -File -Filter '*.php' | ForEach-Object {
                        & $php.Source -l $_.FullName | Out-Null
                        if ($LASTEXITCODE -ne 0) { throw "PHP lint failed: $($_.FullName)" }
                    }
                } else {
                    Write-Warning 'Local PHP CLI is unavailable; the server installer will run PHP lint before changing files.'
                }
                & tar -czf (Join-Path $stage 'admin.tar.gz') -C (Split-Path $admin) 'starfree-admin'
                if ($LASTEXITCODE -ne 0) { throw 'Admin archive failed.' }
            }
        }
    }

    $archive = Join-Path ([IO.Path]::GetTempPath()) ("lcxqy-release-$commit.tgz")
    if (Test-Path -LiteralPath $archive) { Remove-Item -LiteralPath $archive -Force }
    & tar -czf $archive -C $stage .
    if ($LASTEXITCODE -ne 0) { throw 'Release archive failed.' }
    $archiveHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $archive).Hash.ToLowerInvariant()
    Write-Host "commit=$commit"
    Write-Host "component=$Component"
    Write-Host "archive_sha256=$archiveHash"
    Write-Host "archive=$archive"

    if ($DryRun) {
        Write-Host 'dry_run=true; no server connection or production change was made.'
        return
    }

    $sshOptions = @('-p', "$ServerPort", '-o', 'BatchMode=yes', '-o', 'StrictHostKeyChecking=yes')
    $scpOptions = @('-P', "$ServerPort", '-o', 'BatchMode=yes', '-o', 'StrictHostKeyChecking=yes')
    if ($IdentityFile) {
        $sshOptions += @('-i', $IdentityFile)
        $scpOptions += @('-i', $IdentityFile)
    }
    $remote = "$ServerUser@$ServerHost"
    $remoteName = "/tmp/lcxqy-release-$commit.tgz"
    & scp @scpOptions $archive "$remote`:$remoteName"
    if ($LASTEXITCODE -ne 0) { throw 'SCP upload failed.' }

    if ($BootstrapServer) {
        & scp @scpOptions $serverEntrypoint "$remote`:/tmp/lcxqy-deploy.sh"
        if ($LASTEXITCODE -ne 0) { throw 'Bootstrap deploy entrypoint upload failed.' }
        & scp @scpOptions $rollbackEntrypoint "$remote`:/tmp/lcxqy-rollback.sh"
        if ($LASTEXITCODE -ne 0) { throw 'Bootstrap rollback entrypoint upload failed.' }
        & ssh @sshOptions $remote 'sudo install -m 0755 /tmp/lcxqy-deploy.sh /usr/local/sbin/lcxqy-deploy && sudo install -m 0755 /tmp/lcxqy-rollback.sh /usr/local/sbin/lcxqy-rollback'
        if ($LASTEXITCODE -ne 0) { throw 'Bootstrap installation failed.' }
    }

    & ssh @sshOptions $remote "sudo /usr/local/sbin/lcxqy-deploy --archive $remoteName --expected-sha256 $archiveHash --remote-root $RemoteRoot"
    if ($LASTEXITCODE -ne 0) { throw 'Server deployment failed; inspect server output and backup path.' }
    & ssh @sshOptions $remote "rm -f $remoteName"
    if ($LASTEXITCODE -ne 0) { Write-Warning "Deployment succeeded, but the temporary upload remains at $remoteName" }
    Write-Host 'deployment=success'
} finally {
    if (Test-Path -LiteralPath $stage) { Remove-Item -LiteralPath $stage -Recurse -Force }
}
