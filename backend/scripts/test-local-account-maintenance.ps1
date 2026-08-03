param(
    [string]$ApiBase = 'http://127.0.0.1:18082',
    [string]$Database = 'lcxqy_dev'
)

$ErrorActionPreference = 'Stop'

$backendRoot = Split-Path -Parent $PSScriptRoot
$secretFile = Join-Path $backendRoot '.local\run\application-secrets.yml'
$mysql = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'

if (-not (Test-Path -LiteralPath $secretFile)) {
    throw "Missing local database secret file: $secretFile"
}
if (-not (Test-Path -LiteralPath $mysql)) {
    throw "MySQL client not found: $mysql"
}

$passwordLine = Get-Content -LiteralPath $secretFile |
    Where-Object { $_ -match '^\s+password:' } |
    Select-Object -First 1
if (-not $passwordLine) {
    throw 'Local database password is missing.'
}
$databasePassword = ($passwordLine -replace '^\s+password:\s*', '').Trim()
if ($databasePassword.StartsWith("'") -and $databasePassword.EndsWith("'")) {
    $databasePassword = $databasePassword.Substring(1, $databasePassword.Length - 2).Replace("''", "'")
}

function Invoke-LocalMysql {
    param([string]$Sql)

    $output = & $mysql --protocol=TCP --host=127.0.0.1 --user=lcxqy_dev `
        --batch --skip-column-names $Database --execute=$Sql 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "MySQL command failed: $($output -join [Environment]::NewLine)"
    }
    return ($output -join "`n").Trim()
}

function Invoke-LocalApi {
    param(
        [string]$Path,
        [hashtable]$Body = @{},
        [string]$Method = 'GET'
    )

    return Invoke-RestMethod -Uri ($ApiBase + $Path) -Method $Method -Body $Body `
        -ContentType 'application/x-www-form-urlencoded' -TimeoutSec 20
}

function Assert-Equal {
    param($Actual, $Expected, [string]$Message)

    if ($Actual -ne $Expected) {
        throw "$Message (expected=$Expected, actual=$Actual)"
    }
}

function New-ParamsJson {
    param([hashtable]$Values)
    return $Values | ConvertTo-Json -Compress
}

$marker = 'cam_' + [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$primaryName = $marker + '_primary'
$duplicateName = $marker + '_duplicate'
$primaryMail = $primaryName + '@example.invalid'
$duplicateMail = $duplicateName + '@example.invalid'
$originalPassword = 'correct horse battery staple'
$originalHash = '$P$Bad577wyjiXQA8T5lp182gfH.ZR1Qo/'
$newPassword = 'local-account-new-password'
$initialToken = $marker + '_token'
$primaryUid = 0
$duplicateUid = 0
$activeToken = ''
$oldMysqlPassword = $env:MYSQL_PWD
$env:MYSQL_PWD = $databasePassword

try {
    Invoke-LocalMysql (
        'INSERT INTO starfree_users ' +
        '(name,password,mail,screenName,created,`group`,authCode,assets,experience,points) VALUES ' +
        "('$primaryName','$originalHash','$primaryMail','$primaryName',UNIX_TIMESTAMP()," +
        "'contributor','$initialToken',41,42,43)," +
        "('$duplicateName','$originalHash','$duplicateMail','reserved nickname',UNIX_TIMESTAMP()," +
        "'contributor',NULL,0,0,0);") | Out-Null
    $primaryUid = [int](Invoke-LocalMysql "SELECT uid FROM starfree_users WHERE name='$primaryName' LIMIT 1;")
    $duplicateUid = [int](Invoke-LocalMysql "SELECT uid FROM starfree_users WHERE name='$duplicateName' LIMIT 1;")
    if ($primaryUid -le 0 -or $duplicateUid -le 0) {
        throw 'Could not resolve disposable account IDs.'
    }

    $config = Invoke-LocalApi '/SFreeUsers/regConfig'
    Assert-Equal $config.code 1 'regConfig failed'
    if ($null -eq $config.data.isEmail -or $null -eq $config.data.isInvite -or $null -eq $config.data.isPhone) {
        throw 'regConfig omitted a legacy field.'
    }

    $forged = Invoke-LocalApi '/SFreeUsers/userEdit' @{
        token = $initialToken
        params = New-ParamsJson @{ uid = $duplicateUid; screenName = 'forged target' }
    }
    Assert-Equal $forged.code 0 'A forged uid was accepted'

    $protected = Invoke-LocalApi '/SFreeUsers/userEdit' @{
        token = $initialToken
        params = New-ParamsJson @{
            uid = $primaryUid
            name = 'forged-name'
            screenName = $marker + '_screen'
            introduce = 'local account smoke'
            userBg = 'https://example.invalid/background.jpg'
            avatar = 'https://example.invalid/avatar.jpg'
            address = 'name|phone|address'
            pay = 'alipay|name|account|qr'
            assets = 999999
            experience = 999999
            points = 999999
            vip = 999999
            group = 'administrator'
        }
    } 'POST'
    Assert-Equal $protected.code 1 'Profile update failed'
    $projection = Invoke-LocalMysql (
        "SELECT CONCAT_WS('|',name,screenName,assets,experience,points,vip,``group``," +
        "introduce,address,pay) FROM starfree_users WHERE uid=$primaryUid;")
    Assert-Equal $projection (
        "$primaryName|$($marker)_screen|41|42|43|0|contributor|" +
        'local account smoke|name|phone|address|alipay|name|account|qr') `
        'Profile projection or protected balances differ'

    $duplicate = Invoke-LocalApi '/SFreeUsers/userEdit' @{
        token = $initialToken
        params = New-ParamsJson @{ uid = $primaryUid; screenName = 'reserved nickname' }
    }
    Assert-Equal $duplicate.code 0 'Duplicate nickname was accepted'

    $client = Invoke-LocalApi '/SFreeUsers/setClientId' @{
        token = $initialToken
        clientId = $marker + '_push'
    }
    Assert-Equal $client.code 1 'setClientId failed'
    Assert-Equal (Invoke-LocalMysql "SELECT clientId FROM starfree_users WHERE uid=$primaryUid;") `
        ($marker + '_push') 'Client ID was not stored'

    $passwordEdit = Invoke-LocalApi '/SFreeUsers/userEdit' @{
        token = $initialToken
        params = New-ParamsJson @{ uid = $primaryUid; password = $newPassword }
    } 'POST'
    Assert-Equal $passwordEdit.code 1 'Password edit failed'
    Assert-Equal (Invoke-LocalMysql "SELECT COALESCE(authCode,'') FROM starfree_users WHERE uid=$primaryUid;") `
        '' 'Password edit did not revoke the MySQL token'
    Assert-Equal (Invoke-LocalMysql "SELECT LEFT(password,4) FROM starfree_users WHERE uid=$primaryUid;") `
        '$P$B' 'Password edit did not create a legacy PHPass hash'

    $oldStatus = Invoke-LocalApi '/SFreeUsers/userStatus' @{ token = $initialToken }
    Assert-Equal $oldStatus.code 0 'Revoked token still passed userStatus'
    $login = Invoke-LocalApi '/SFreeUsers/userLogin' @{
        params = New-ParamsJson @{ name = $primaryName; password = $newPassword }
    } 'POST'
    Assert-Equal $login.code 1 'Login with the changed password failed'
    $activeToken = [string]$login.data.token
    if ([string]::IsNullOrWhiteSpace($activeToken)) {
        throw 'Changed-password login did not return a token.'
    }

    Write-Output 'account_local_mysql_audit=PASS'
    Write-Output "reg_config=$($config.data.isEmail),$($config.data.isInvite),$($config.data.isPhone)"
    Write-Output "primary_uid=$primaryUid"
}
finally {
    $cleanupErrors = @()
    if (-not [string]::IsNullOrWhiteSpace($activeToken)) {
        try {
            Invoke-LocalApi '/SFreeUsers/signOut' @{ token = $activeToken } | Out-Null
        } catch {
            $cleanupErrors += $_.Exception.Message
        }
    }
    if ($primaryUid -gt 0 -or $duplicateUid -gt 0) {
        try {
            $ids = @($primaryUid, $duplicateUid) | Where-Object { $_ -gt 0 }
            Invoke-LocalMysql ("DELETE FROM starfree_users WHERE uid IN ({0});" -f ($ids -join ',')) | Out-Null
        } catch {
            $cleanupErrors += $_.Exception.Message
        }
    }
    $env:MYSQL_PWD = $oldMysqlPassword
    if ($cleanupErrors.Count -gt 0) {
        throw "Disposable account cleanup failed: $($cleanupErrors -join '; ')"
    }
}
