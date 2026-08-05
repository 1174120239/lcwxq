[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [ValidateSet('doctor', 'start', 'check', 'status', 'publish', 'deploy', 'verify')]
    [string]$Command = 'status',

    [Parameter(Position = 1)]
    [string]$Target = 'all',

    [switch]$Remote,
    [switch]$ConfirmProduction
)

$ErrorActionPreference = 'Stop'
$repoRoot = $PSScriptRoot

function Invoke-Git([string[]]$Arguments) {
    $result = & git -C $repoRoot @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "git $($Arguments -join ' ') failed: $result"
    }
    return (($result | Out-String).Trim())
}

function Get-ConfiguredValue([string]$Name) {
    $value = [Environment]::GetEnvironmentVariable($Name, 'Process')
    if (-not $value) {
        $value = [Environment]::GetEnvironmentVariable($Name, 'User')
    }
    return $value
}

function Assert-Tool([string]$Name, [switch]$Optional) {
    $commandInfo = Get-Command $Name -ErrorAction SilentlyContinue
    if (-not $commandInfo -and -not $Optional) {
        throw "Required command not found: $Name"
    }
    if ($commandInfo) {
        Write-Host "tool_$Name=$($commandInfo.Source)"
    } else {
        Write-Host "tool_$Name=missing_optional"
    }
    return $commandInfo
}

function Resolve-Maven {
    $commandInfo = Get-Command mvn -ErrorAction SilentlyContinue
    if ($commandInfo) { return $commandInfo.Source }
    $candidates = @(
        (Join-Path $repoRoot 'tools/apache-maven-3.9.11/bin/mvn.cmd'),
        (Join-Path $repoRoot 'backend/.local/tools/apache-maven-3.9.11/bin/mvn.cmd'),
        (Join-Path $repoRoot 'backend/.local/tools/apache-maven-3.9.9/bin/mvn.cmd')
    )
    foreach ($candidate in $candidates) {
        if (Test-Path -LiteralPath $candidate -PathType Leaf) { return $candidate }
    }
    throw 'Maven was not found in PATH or the project-local tool directories.'
}

function Resolve-Bash {
    $gitBash = 'C:\Program Files\Git\bin\bash.exe'
    if (Test-Path -LiteralPath $gitBash -PathType Leaf) { return $gitBash }
    $commandInfo = Get-Command bash -ErrorAction SilentlyContinue
    if ($commandInfo) { return $commandInfo.Source }
    throw 'Bash was not found. Install Git for Windows.'
}

function Assert-CleanWorkingTree {
    $status = Invoke-Git @('status', '--porcelain')
    if ($status) {
        throw "The working tree is not clean:`n$status"
    }
}

function Invoke-BackendCheck {
    $maven = Resolve-Maven
    Push-Location (Join-Path $repoRoot 'backend/starfree-replacement')
    try {
        & $maven -q test
        if ($LASTEXITCODE -ne 0) { throw 'Backend tests failed.' }
    } finally {
        Pop-Location
    }
    Write-Host 'check_backend=ok'
}

function Invoke-AdminCheck {
    $php = Assert-Tool php
    $count = 0
    Get-ChildItem (Join-Path $repoRoot 'admin/starfree-admin') -Recurse -File -Filter '*.php' |
        ForEach-Object {
            & $php.Source -l $_.FullName | Out-Null
            if ($LASTEXITCODE -ne 0) { throw "PHP lint failed: $($_.FullName)" }
            $count++
        }
    Write-Host "check_admin=ok files=$count"
}

function Invoke-ScriptCheck {
    $parseFailures = @()
    $powerShellFiles = Invoke-Git @('ls-files', '--cached', '--others', '--exclude-standard', '--', '*.ps1')
    foreach ($relativePath in ($powerShellFiles -split "`r?`n")) {
        if (-not $relativePath) { continue }
        $tokens = $null
        $errors = $null
        [System.Management.Automation.Language.Parser]::ParseFile(
            (Join-Path $repoRoot $relativePath),
            [ref]$tokens,
            [ref]$errors
        ) | Out-Null
        if ($errors.Count) {
            $parseFailures += "$relativePath`: $($errors.Message -join '; ')"
        }
    }
    if ($parseFailures.Count) { throw "PowerShell syntax failed:`n$($parseFailures -join "`n")" }

    $bash = Resolve-Bash
    $shellFiles = Invoke-Git @('ls-files', '--cached', '--others', '--exclude-standard', '--', '*.sh')
    foreach ($relativePath in ($shellFiles -split "`r?`n")) {
        if (-not $relativePath) { continue }
        & $bash -n (Join-Path $repoRoot $relativePath)
        if ($LASTEXITCODE -ne 0) { throw "Bash syntax failed: $relativePath" }
    }
    Write-Host 'check_scripts=ok'
}

function Invoke-DocsCheck {
    $missing = @()
    $markdownFiles = @()
    $markdownFiles += Get-Item (Join-Path $repoRoot 'README.md')
    $markdownFiles += Get-Item (Join-Path $repoRoot 'AGENTS.md')
    $markdownFiles += Get-ChildItem (Join-Path $repoRoot 'markdown_docs') -File -Filter '*.md'
    foreach ($file in $markdownFiles) {
        $contents = Get-Content -Raw -Encoding utf8 $file.FullName
        [regex]::Matches($contents, '\[[^\]]+\]\(([^)#]+)(?:#[^)]+)?\)') | ForEach-Object {
            $target = $_.Groups[1].Value
            if ($target -notmatch '^(https?://|mailto:)') {
                $resolved = Join-Path $file.DirectoryName $target
                if (-not (Test-Path -LiteralPath $resolved)) {
                    $missing += "$($file.FullName) -> $target"
                }
            }
        }
    }
    if ($missing.Count) { throw "Broken Markdown links:`n$($missing -join "`n")" }

    & git -C $repoRoot diff --check
    if ($LASTEXITCODE -ne 0) { throw 'git diff --check failed.' }
    & git -C $repoRoot show --check --format= HEAD
    if ($LASTEXITCODE -ne 0) { throw 'The current commit contains whitespace errors.' }

    $sensitiveFiles = Invoke-Git @(
        'ls-files', '--cached', '--others', '--exclude-standard', '--',
        '*.pem', '*.key', '*.p12', '*.pfx', '*.jks', '*.keystore',
        'markdown_docs/private/*', '**/application-secrets.yml', '**/Config_DB.php'
    )
    if ($sensitiveFiles) {
        throw "Sensitive files are present in Git candidates:`n$sensitiveFiles"
    }
    $trackedGenerated = Invoke-Git @(
        'ls-files', '--',
        'unpackage/*', '**/target/*', 'tools/*', 'backend/.local/*'
    )
    if ($trackedGenerated) {
        throw "Generated or local tool files are tracked:`n$trackedGenerated"
    }
    Write-Host 'check_docs_and_repository=ok'
}

function Invoke-AllChecks([string]$Scope) {
    $validScopes = @('backend', 'admin', 'scripts', 'docs', 'all')
    if ($Scope -notin $validScopes) {
        throw "Check target must be one of: $($validScopes -join ', ')"
    }
    $scopes = if ($Scope -eq 'all') { @('backend', 'admin', 'scripts', 'docs') } else { @($Scope) }
    foreach ($scopeItem in $scopes) {
        switch ($scopeItem) {
            'backend' { Invoke-BackendCheck }
            'admin' { Invoke-AdminCheck }
            'scripts' { Invoke-ScriptCheck }
            'docs' { Invoke-DocsCheck }
        }
    }
    Write-Host "check=$Scope result=ok"
}

function Invoke-Doctor {
    Assert-Tool git | Out-Null
    Assert-Tool ssh | Out-Null
    Assert-Tool scp | Out-Null
    Assert-Tool tar | Out-Null
    Assert-Tool php -Optional | Out-Null
    Write-Host "tool_maven=$(Resolve-Maven)"
    Write-Host "tool_bash=$(Resolve-Bash)"
    Write-Host "branch=$(Invoke-Git @('branch', '--show-current'))"
    Write-Host "head=$(Invoke-Git @('rev-parse', 'HEAD'))"
    Write-Host "origin_main=$(Invoke-Git @('rev-parse', 'origin/main'))"
    Write-Host "git_user_name=$(Invoke-Git @('config', '--get', 'user.name'))"
    Write-Host "git_user_email=$(Invoke-Git @('config', '--get', 'user.email'))"
    $deployHost = Get-ConfiguredValue 'LCXQY_SSH_HOST'
    $deployUser = Get-ConfiguredValue 'LCXQY_SSH_USER'
    $deployKey = Get-ConfiguredValue 'LCXQY_SSH_KEY'
    Write-Host "deploy_host_configured=$([bool]$deployHost)"
    Write-Host "deploy_user=$deployUser"
    Write-Host "deploy_key_exists=$(if ($deployKey) { Test-Path -LiteralPath $deployKey } else { $false })"
}

function Start-Feature([string]$Name) {
    if ($Name -notmatch '^[a-z0-9][a-z0-9-]{1,48}$') {
        throw 'Feature name must use 2-49 lowercase letters, digits, and hyphens.'
    }
    Assert-CleanWorkingTree
    $branch = Invoke-Git @('branch', '--show-current')
    if ($branch -ne 'main') { throw "Start a feature from main, not $branch." }
    Invoke-Git @('fetch', 'origin', 'main') | Out-Null
    $head = Invoke-Git @('rev-parse', 'HEAD')
    $originMain = Invoke-Git @('rev-parse', 'origin/main')
    if ($head -ne $originMain) {
        throw "Local main is not synchronized with origin/main. local=$head remote=$originMain"
    }
    $newBranch = "codex/$Name"
    & git -C $repoRoot switch -c $newBranch
    if ($LASTEXITCODE -ne 0) { throw "Could not create branch $newBranch" }
    Write-Host "feature_branch=$newBranch"
}

function Show-Status([switch]$IncludeRemote) {
    & git -C $repoRoot status --short --branch
    Write-Host "head=$(Invoke-Git @('rev-parse', 'HEAD'))"
    Write-Host "origin_main=$(Invoke-Git @('rev-parse', 'origin/main'))"
    if (-not $IncludeRemote) { return }
    foreach ($component in @('replacement-backend', 'admin')) {
        & powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File `
            (Join-Path $repoRoot 'deploy/verify-release.ps1') -Component $component
        if ($LASTEXITCODE -ne 0) { throw "Remote verification failed: $component" }
    }
    $deployHost = Get-ConfiguredValue 'LCXQY_SSH_HOST'
    $deployUser = Get-ConfiguredValue 'LCXQY_SSH_USER'
    $deployKey = Get-ConfiguredValue 'LCXQY_SSH_KEY'
    if (-not $deployHost -or -not $deployUser -or -not $deployKey) {
        throw 'LCXQY SSH environment variables are missing.'
    }
    & ssh -i $deployKey -o IdentitiesOnly=yes -o BatchMode=yes -o StrictHostKeyChecking=yes `
        "$deployUser@$deployHost" `
        "curl -fsS -o /dev/null -w 'legacy_http=%{http_code}\n' http://127.0.0.1:8081/"
    if ($LASTEXITCODE -ne 0) { throw 'Legacy API health check failed.' }
}

switch ($Command) {
    'doctor' { Invoke-Doctor }
    'start' { Start-Feature $Target }
    'check' { Invoke-AllChecks $Target }
    'status' { Show-Status -IncludeRemote:$Remote }
    'publish' {
        if ($Target -notin @('replacement-backend', 'legacy-api', 'admin', 'all')) {
            throw 'Publish target must be replacement-backend, legacy-api, admin, or all.'
        }
        $arguments = @('-Component', $Target)
        if ($ConfirmProduction) { $arguments += '-ConfirmProduction' } else { $arguments += '-DryRun' }
        & powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File `
            (Join-Path $repoRoot 'deploy/publish-to-server.ps1') @arguments
        if ($LASTEXITCODE -ne 0) { throw "Publish command failed: $Target" }
    }
    'deploy' {
        # The normal production target is the replacement backend. Keep the
        # component-specific publish command for admin and legacy maintenance.
        $arguments = @('-Component', 'replacement-backend')
        if ($ConfirmProduction) { $arguments += '-ConfirmProduction' } else { $arguments += '-DryRun' }
        & powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File `
            (Join-Path $repoRoot 'deploy/publish-to-server.ps1') @arguments
        if ($LASTEXITCODE -ne 0) { throw 'Deploy command failed: replacement-backend' }
    }
    'verify' {
        if ($Target -notin @('replacement-backend', 'legacy-api', 'admin')) {
            throw 'Verify target must be replacement-backend, legacy-api, or admin.'
        }
        & powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File `
            (Join-Path $repoRoot 'deploy/verify-release.ps1') -Component $Target
        if ($LASTEXITCODE -ne 0) { throw "Verification failed: $Target" }
    }
}
