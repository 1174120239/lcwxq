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
    $output = & $mysql --protocol=TCP --host=127.0.0.1 --user=lcxqy_dev --batch --skip-column-names $Database --execute=$Sql 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "MySQL command failed: $($output -join [Environment]::NewLine)"
    }
    return ($output -join [Environment]::NewLine).Trim()
}

function Invoke-LocalApi {
    param([string]$Path, [hashtable]$Body)
    return Invoke-RestMethod -Uri ($ApiBase + $Path) -Method Get -Body $Body -ContentType 'application/x-www-form-urlencoded' -TimeoutSec 15
}

function Assert-Equal {
    param($Actual, $Expected, [string]$Message)
    if ($Actual -ne $Expected) {
        throw "$Message (expected=$Expected, actual=$Actual)"
    }
}

$marker = 'codex_ads_it_' + [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$buyerName = $marker + '_buyer'
$adminName = $marker + '_admin'
$buyerToken = $marker + '_buyer_token'
$adminToken = $marker + '_admin_token'
$buyerUid = 0
$adminUid = 0
$aid = 0

$oldMysqlPassword = $env:MYSQL_PWD
$env:MYSQL_PWD = $databasePassword
try {
    # Important: use disposable users and tokens. Never point this script at production.
    $insertUsers = 'INSERT INTO starfree_users (name,password,created,`group`,authCode,assets,experience,points) ' +
        "VALUES ('$buyerName','',UNIX_TIMESTAMP(),'contributor','$buyerToken',1000,0,0)," +
        "('$adminName','',UNIX_TIMESTAMP(),'administrator','$adminToken',0,0,0);"
    Invoke-LocalMysql $insertUsers | Out-Null
    $buyerUid = [int](Invoke-LocalMysql "SELECT uid FROM starfree_users WHERE name='$buyerName' LIMIT 1;")
    $adminUid = [int](Invoke-LocalMysql "SELECT uid FROM starfree_users WHERE name='$adminName' LIMIT 1;")

    $config = Invoke-LocalApi '/SFreeAds/adsConfig' @{}
    Assert-Equal $config.code 1 'Advertising config failed'
    Assert-Equal ([int]$config.data.pushAdsPrice) 100 'Push advertising price is incompatible'

    $adParams = @{ name = $marker; type = 0; img = 'https://example.com/ad.png'; intro = 'temporary integration advertisement'; urltype = 1; url = 'https://example.com/' } | ConvertTo-Json -Compress
    $adRequestId = $marker + '_buy'
    $added = Invoke-LocalApi '/SFreeAds/addAds' @{
        token = $buyerToken; day = 2; params = $adParams; requestId = $adRequestId
    }
    Assert-Equal $added.code 1 'Advertising creation failed'
    Assert-Equal ([int]$added.data.status) 0 'New advertisement should wait for review'
    Assert-Equal ([int]$added.data.price) 200 'Advertising charge is incorrect'
    $addedReplay = Invoke-LocalApi '/SFreeAds/addAds' @{
        token = $buyerToken; day = 2; params = $adParams; requestId = $adRequestId
    }
    Assert-Equal $addedReplay.code 1 'Advertising purchase replay failed'

    $aid = [int](Invoke-LocalMysql "SELECT aid FROM starfree_ads WHERE uid=$buyerUid AND name='$marker' LIMIT 1;")
    if ($aid -le 0) {
        throw 'Advertising insert did not create a database row.'
    }
    Assert-Equal (Invoke-LocalMysql "SELECT assets FROM starfree_users WHERE uid=$buyerUid;") '800' 'Advertising purchase did not debit assets'
    Assert-Equal (Invoke-LocalMysql "SELECT COUNT(*) FROM starfree_paylog WHERE uid=$buyerUid AND paytype='buyAds';") '1' 'Advertising purchase did not create one legacy buyAds paylog'
    Assert-Equal (Invoke-LocalMysql "SELECT COUNT(*) FROM starfree_ads WHERE uid=$buyerUid AND name='$marker';") '1' 'Advertising replay created a duplicate row'

    $mineFilters = @{ status = 0; uid = $buyerUid } | ConvertTo-Json -Compress
    $mine = Invoke-LocalApi '/SFreeAds/adsList' @{ token = $buyerToken; page = 1; limit = 8; searchParams = $mineFilters }
    Assert-Equal $mine.code 1 'My advertising list failed'
    Assert-Equal ([int]$mine.total) 1 'My advertising list total is incorrect'

    $privateInfo = Invoke-LocalApi '/SFreeAds/adsInfo' @{ token = $buyerToken; id = $aid }
    Assert-Equal $privateInfo.name $marker 'Advertising detail raw-object response is incompatible'
    $anonymousInfo = Invoke-LocalApi '/SFreeAds/adsInfo' @{ id = $aid }
    Assert-Equal $anonymousInfo.code 0 'Pending advertisement should not be public'

    $deniedAudit = Invoke-LocalApi '/SFreeAds/auditAds' @{ token = $buyerToken; id = $aid }
    Assert-Equal $deniedAudit.code 0 'Non-staff audit should be rejected'
    $approved = Invoke-LocalApi '/SFreeAds/auditAds' @{ token = $adminToken; id = $aid }
    Assert-Equal $approved.code 1 'Administrator audit failed'

    $publicList = Invoke-LocalApi '/SFreeAds/adsList' @{ page = 1; limit = 8; searchKey = $marker; searchParams = '{}' }
    Assert-Equal $publicList.code 1 'Public advertising list failed'
    Assert-Equal ([int]$publicList.total) 1 'Approved advertisement is not public'

    $editedParams = @{ aid = $aid; name = ($marker + '_edited'); type = 0; img = 'https://example.com/ad-edited.png'; intro = 'temporary edited advertisement'; urltype = 1; url = 'https://example.com/edited' } | ConvertTo-Json -Compress
    $edited = Invoke-LocalApi '/SFreeAds/editAds' @{ token = $buyerToken; params = $editedParams }
    Assert-Equal $edited.code 1 'Advertising edit failed'
    Assert-Equal ([int]$edited.data.status) 0 'User edit should return advertising to review'

    $renewed = Invoke-LocalApi '/SFreeAds/renewalAds' @{ token = $buyerToken; id = $aid; day = 1 }
    Assert-Equal $renewed.code 0 'Owner renewal must be rejected because legacy renewal is administrator-only'
    Assert-Equal (Invoke-LocalMysql "SELECT assets FROM starfree_users WHERE uid=$buyerUid;") '800' 'Rejected owner renewal changed assets'

    $beforeAdminRenewal = [long](Invoke-LocalMysql "SELECT `close` FROM starfree_ads WHERE aid=$aid;")
    $beforeAdminPrice = [int](Invoke-LocalMysql "SELECT price FROM starfree_ads WHERE aid=$aid;")
    $renewalRequest = @{
        token = $adminToken; id = $aid; day = 1; requestId = ($marker + '_renewal')
    }
    $adminRenewed = Invoke-LocalApi '/SFreeAds/renewalAds' $renewalRequest
    Assert-Equal $adminRenewed.code 1 'Administrator renewal failed'
    Assert-Equal ([int]$adminRenewed.data.price) 100 'Administrator renewal gift value is incorrect'
    Assert-Equal (Invoke-LocalApi '/SFreeAds/renewalAds' $renewalRequest).code 1 'Administrator renewal replay failed'
    $afterAdminRenewal = [long](Invoke-LocalMysql "SELECT `close` FROM starfree_ads WHERE aid=$aid;")
    Assert-Equal ($afterAdminRenewal - $beforeAdminRenewal) 86400 'Administrator renewal duration is incorrect'
    Assert-Equal ([int](Invoke-LocalMysql "SELECT price FROM starfree_ads WHERE aid=$aid;")) ($beforeAdminPrice + 100) 'Administrator renewal price was applied more than once'
    Assert-Equal (Invoke-LocalMysql "SELECT COUNT(*) FROM starfree_paylog WHERE uid=$buyerUid AND paytype='renewalAds' AND total_amount='100';") '1' 'Administrator renewal did not write one positive renewalAds paylog for the owner'

    $deleted = Invoke-LocalApi '/SFreeAds/deleteAds' @{ token = $buyerToken; id = $aid }
    Assert-Equal $deleted.code 1 'Owner deletion failed'
    Assert-Equal (Invoke-LocalMysql "SELECT COUNT(*) FROM starfree_ads WHERE aid=$aid;") '0' 'Advertising row remained after deletion'
    $aid = 0

    Write-Output 'Local advertising integration test passed.'
} finally {
    # Cleanup is idempotent and scoped to disposable IDs only.
    if ($aid -gt 0) {
        Invoke-LocalMysql "DELETE FROM starfree_ads WHERE aid=$aid;" | Out-Null
    }
    if ($buyerUid -gt 0 -and $adminUid -gt 0) {
        Invoke-LocalMysql "DELETE FROM starfree_ads WHERE uid IN ($buyerUid,$adminUid);" | Out-Null
        Invoke-LocalMysql "DELETE FROM starfree_paylog WHERE uid IN ($buyerUid,$adminUid) AND paytype IN ('buyAds','renewalAds');" | Out-Null
        Invoke-LocalMysql "DELETE FROM starfree_economy_operations WHERE actor_uid IN ($buyerUid,$adminUid) OR target_uid IN ($buyerUid,$adminUid);" | Out-Null
        Invoke-LocalMysql "DELETE FROM starfree_users WHERE uid IN ($buyerUid,$adminUid);" | Out-Null
    }
    if ($null -eq $oldMysqlPassword) {
        Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
    } else {
        $env:MYSQL_PWD = $oldMysqlPassword
    }
}
