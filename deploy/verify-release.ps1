[CmdletBinding()]
param(
    [ValidateSet('replacement-backend', 'legacy-api', 'admin')]
    [string]$Component = 'replacement-backend',
    [string]$ServerHost = $env:LCXQY_SSH_HOST,
    [string]$ServerUser = $env:LCXQY_SSH_USER,
    [int]$ServerPort = 22,
    [string]$IdentityFile = $env:LCXQY_SSH_KEY
)

$ErrorActionPreference = 'Stop'
if (-not $ServerHost -or -not $ServerUser) {
    throw 'Set LCXQY_SSH_HOST and LCXQY_SSH_USER first.'
}
$sshArgs = @('-p', "$ServerPort", '-o', 'BatchMode=yes', '-o', 'StrictHostKeyChecking=yes')
if ($IdentityFile) { $sshArgs += @('-i', $IdentityFile) }
$remote = "$ServerUser@$ServerHost"
& ssh @sshArgs $remote "sudo /usr/local/sbin/lcxqy-deploy --verify --component $Component"
if ($LASTEXITCODE -ne 0) { throw "Verification failed for $Component." }
