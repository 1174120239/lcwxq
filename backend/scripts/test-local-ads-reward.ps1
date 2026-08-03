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
        --batch --raw --skip-column-names $Database --execute=$Sql 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "MySQL command failed: $($output -join [Environment]::NewLine)"
    }
    return ($output -join "`n").Trim()
}

function Invoke-LocalApi {
    param([string]$Path, [hashtable]$Body)

    return Invoke-RestMethod -Uri ($ApiBase + $Path) -Method Get -Body $Body `
        -ContentType 'application/x-www-form-urlencoded' -TimeoutSec 15
}

function Assert-Equal {
    param($Actual, $Expected, [string]$Message)

    if ($Actual -ne $Expected) {
        throw "$Message (expected=$Expected, actual=$Actual)"
    }
}

function Get-Sha256 {
    param([string]$Value)

    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($Value)
        return -join ($sha.ComputeHash($bytes) | ForEach-Object { $_.ToString('x2') })
    } finally {
        $sha.Dispose()
    }
}

$millis = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds().ToString()
$marker = 'cari_' + $millis.Substring([Math]::Max(0, $millis.Length - 10))
$ownerName = $marker + '_owner'
$otherName = $marker + '_other'
$ownerToken = $marker + '_owner_token'
$otherToken = $marker + '_other_token'
$ownerUid = 0
$otherUid = 0
$pendingId = 0
$configId = 0
$originalConfig = $null
$oldMysqlPassword = $env:MYSQL_PWD
$env:MYSQL_PWD = $databasePassword

try {
    $configId = [int](Invoke-LocalMysql 'SELECT id FROM starfree_apiconfig ORDER BY id LIMIT 1;')
    if ($configId -le 0) {
        throw 'Advertising reward configuration is missing.'
    }
    $configRaw = Invoke-LocalMysql (
        "SELECT CONCAT_WS(CHAR(9),adsVideoType,adsGiftNum,adsGiftAward," +
        "HEX(COALESCE(adsSecuritykey,'')),banRobots) FROM starfree_apiconfig WHERE id=$configId;")
    $configParts = $configRaw -split "`t", 5
    $originalConfig = @{
        VideoType = [int]$configParts[0]
        GiftNum = [int]$configParts[1]
        GiftAward = [int]$configParts[2]
        SecurityHex = $configParts[3]
        BanRobots = [int]$configParts[4]
    }

    Invoke-LocalMysql (
        'INSERT INTO starfree_users (name,password,created,`group`,authCode,assets,experience,points) ' +
        "VALUES ('$ownerName','',UNIX_TIMESTAMP(),'contributor','$ownerToken',100,0,0)," +
        "('$otherName','',UNIX_TIMESTAMP(),'contributor','$otherToken',50,0,0);") | Out-Null
    $ownerUid = [int](Invoke-LocalMysql "SELECT uid FROM starfree_users WHERE name='$ownerName' LIMIT 1;")
    $otherUid = [int](Invoke-LocalMysql "SELECT uid FROM starfree_users WHERE name='$otherName' LIMIT 1;")

    # Client verification mode: an authenticated user may settle only their own pending log.
    Invoke-LocalMysql (
        "UPDATE starfree_apiconfig SET adsVideoType=0,adsGiftNum=10,adsGiftAward=5," +
        "banRobots=0 WHERE id=$configId;") | Out-Null
    Invoke-LocalMysql (
        "INSERT INTO starfree_userlog (uid,cid,type,num,created,toid) " +
        "VALUES ($ownerUid,0,'adsGift',0,UNIX_TIMESTAMP(),0);") | Out-Null
    $pendingId = [int](Invoke-LocalMysql (
        "SELECT id FROM starfree_userlog WHERE uid=$ownerUid AND type='adsGift' " +
        'ORDER BY id DESC LIMIT 1;'))

    $stolen = Invoke-LocalApi '/SFreeUserlog/adsGiftNotify' @{
        token = $otherToken; logid = $pendingId
    }
    Assert-Equal ([int]$stolen.code) 0 'Another user claimed the pending reward log'
    Assert-Equal ([int](Invoke-LocalMysql "SELECT cid FROM starfree_userlog WHERE id=$pendingId;")) 0 `
        'Rejected claim changed the pending log'
    Assert-Equal ([int](Invoke-LocalMysql "SELECT assets FROM starfree_users WHERE uid=$otherUid;")) 50 `
        'Rejected claim changed the other user balance'

    $clientReward = Invoke-LocalApi '/SFreeUserlog/adsGiftNotify' @{
        token = $ownerToken; logid = $pendingId
    }
    Assert-Equal ([int]$clientReward.code) 1 'Owner client reward failed'
    Assert-Equal ([int]$clientReward.data.award) 5 'Client reward amount is incorrect'
    $clientReplay = Invoke-LocalApi '/SFreeUserlog/adsGiftNotify' @{
        token = $ownerToken; logid = $pendingId
    }
    Assert-Equal ([int]$clientReplay.code) 1 'Client reward replay failed'
    Assert-Equal ([int](Invoke-LocalMysql "SELECT assets FROM starfree_users WHERE uid=$ownerUid;")) 105 `
        'Client reward was not applied exactly once'
    Assert-Equal ([int](Invoke-LocalMysql (
        "SELECT COUNT(*) FROM starfree_paylog WHERE uid=$ownerUid AND paytype='adsGift';"))) 1 `
        'Client reward paylog is not idempotent'

    # Server verification mode: trans_id is global, signed, and consumed exactly once.
    $securityKey = $marker + '_secret'
    $securityHex = -join ([System.Text.Encoding]::UTF8.GetBytes($securityKey) |
        ForEach-Object { $_.ToString('x2') })
    Invoke-LocalMysql (
        "UPDATE starfree_apiconfig SET adsVideoType=1,adsGiftNum=1,adsGiftAward=7," +
        "adsSecuritykey=UNHEX('$securityHex'),banRobots=0 WHERE id=$configId;") | Out-Null

    $transactionId = $marker + '_tx_1'
    $signature = Get-Sha256 ($securityKey + ':' + $transactionId)
    $invalid = Invoke-LocalApi '/SFreeUserlog/adsServerNotify' @{
        trans_id = $transactionId; user_id = $otherUid; sign = ('0' * 64)
        adpid = 'integration'; provider = 'integration'; extra = ''
    }
    Assert-Equal ([bool]$invalid.isValid) $false 'Invalid server signature was accepted'

    $serverRequest = @{
        trans_id = $transactionId; user_id = $otherUid; sign = $signature
        adpid = 'integration'; provider = 'integration'; extra = ''
    }
    $serverReward = Invoke-LocalApi '/SFreeUserlog/adsServerNotify' $serverRequest
    Assert-Equal ([bool]$serverReward.isValid) $true 'Signed server reward failed'
    $serverReplay = Invoke-LocalApi '/SFreeUserlog/adsServerNotify' $serverRequest
    Assert-Equal ([bool]$serverReplay.isValid) $true 'Signed server reward replay failed'
    Assert-Equal ([int](Invoke-LocalMysql "SELECT assets FROM starfree_users WHERE uid=$otherUid;")) 57 `
        'Server reward was not applied exactly once'
    Assert-Equal ([int](Invoke-LocalMysql (
        "SELECT COUNT(*) FROM starfree_userlog WHERE uid=$otherUid AND type='adsGift' AND cid=1;"))) 1 `
        'Server reward daily log is not idempotent'

    $sameTransactionOtherUser = Invoke-LocalApi '/SFreeUserlog/adsServerNotify' @{
        trans_id = $transactionId; user_id = $ownerUid; sign = $signature
        adpid = 'integration'; provider = 'integration'; extra = ''
    }
    Assert-Equal ([bool]$sameTransactionOtherUser.isValid) $true `
        'Provider replay should remain acknowledged'
    Assert-Equal ([int](Invoke-LocalMysql "SELECT assets FROM starfree_users WHERE uid=$ownerUid;")) 105 `
        'A replayed transaction credited a different user'

    $limitedTransaction = $marker + '_tx_2'
    $limited = Invoke-LocalApi '/SFreeUserlog/adsServerNotify' @{
        trans_id = $limitedTransaction; user_id = $otherUid
        sign = (Get-Sha256 ($securityKey + ':' + $limitedTransaction))
        adpid = 'integration'; provider = 'integration'; extra = ''
    }
    Assert-Equal ([bool]$limited.isValid) $false 'Daily reward limit was bypassed'
    Assert-Equal ([int](Invoke-LocalMysql "SELECT assets FROM starfree_users WHERE uid=$otherUid;")) 57 `
        'Daily-limit rejection changed the balance'

    Invoke-LocalMysql "UPDATE starfree_apiconfig SET adsSecuritykey='' WHERE id=$configId;" | Out-Null
    $emptyKeyTransaction = $marker + '_tx_empty'
    $emptyKey = Invoke-LocalApi '/SFreeUserlog/adsServerNotify' @{
        trans_id = $emptyKeyTransaction; user_id = $otherUid
        sign = (Get-Sha256 (':' + $emptyKeyTransaction))
        adpid = 'integration'; provider = 'integration'; extra = ''
    }
    Assert-Equal ([bool]$emptyKey.isValid) $false 'Empty security key was accepted'

    Assert-Equal ([int](Invoke-LocalMysql (
        "SELECT COUNT(*) FROM starfree_economy_operations WHERE " +
        "(actor_uid IN ($ownerUid,$otherUid) OR target_uid IN ($ownerUid,$otherUid)) " +
        "AND state='needs_review';"))) 0 'Advertising reward needs manual reconciliation'

    Write-Output 'ads_reward_integration=passed'
} finally {
    if ($configId -gt 0 -and $null -ne $originalConfig) {
        Invoke-LocalMysql (
            "UPDATE starfree_apiconfig SET adsVideoType=$($originalConfig.VideoType)," +
            "adsGiftNum=$($originalConfig.GiftNum),adsGiftAward=$($originalConfig.GiftAward)," +
            "adsSecuritykey=UNHEX('$($originalConfig.SecurityHex)')," +
            "banRobots=$($originalConfig.BanRobots) WHERE id=$configId;") | Out-Null
    }
    if ($ownerUid -gt 0 -and $otherUid -gt 0) {
        Invoke-LocalMysql (
            "DELETE FROM starfree_userlog WHERE uid IN ($ownerUid,$otherUid) OR toid IN ($ownerUid,$otherUid);" +
            "DELETE FROM starfree_paylog WHERE uid IN ($ownerUid,$otherUid);" +
            "DELETE FROM starfree_economy_operations WHERE actor_uid IN ($ownerUid,$otherUid) " +
            "OR target_uid IN ($ownerUid,$otherUid);" +
            "DELETE FROM starfree_users WHERE uid IN ($ownerUid,$otherUid);") | Out-Null
    } else {
        Invoke-LocalMysql "DELETE FROM starfree_users WHERE name IN ('$ownerName','$otherName');" | Out-Null
    }
    $env:MYSQL_PWD = $oldMysqlPassword
}
