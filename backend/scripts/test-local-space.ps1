param(
    [string]$ApiBase = 'http://127.0.0.1:18082',
    [string]$Database = 'lcxqy_dev'
)

$ErrorActionPreference = 'Stop'

$backendRoot = Split-Path -Parent $PSScriptRoot
$secretFile = Join-Path $backendRoot '.local\run\application-secrets.yml'
$mysql = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'
$groupColumn = [char]96 + 'group' + [char]96

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

function Quote-Sql {
    param([string]$Value)
    if ($null -eq $Value) { return 'NULL' }
    return "'" + $Value.Replace("'", "''") + "'"
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

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) {
        throw $Message
    }
}

$stamp = ([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() % 10000000000)
$marker = 'cs' + $stamp
$authorName = $marker + '_a'
$followerName = $marker + '_f'
$adminName = $marker + '_m'
$authorToken = $marker + '_author_token'
$followerToken = $marker + '_follower_token'
$adminToken = $marker + '_admin_token'
$authorUid = 0
$followerUid = 0
$adminUid = 0
$oldConfig = $null
$oldMysqlPassword = $env:MYSQL_PWD
$env:MYSQL_PWD = $databasePassword

try {
    # Important: this script is scoped to local disposable users and restores config in finally.
    $oldConfig = Invoke-LocalMysql "SELECT CONCAT_WS('|',IFNULL(spaceMinExp,0),IFNULL(spaceAudit,0),IFNULL(postMax,999),IFNULL(postExp,0),IFNULL(identifysmPost,0),IFNULL(identifylvPost,0),TO_BASE64(IFNULL(forbidden,''))) FROM starfree_apiconfig ORDER BY id LIMIT 1;"
    Invoke-LocalMysql "UPDATE starfree_apiconfig SET spaceMinExp=0,spaceAudit=0,postMax=999,postExp=5,identifysmPost=0,identifylvPost=0,forbidden='' ORDER BY id LIMIT 1;" | Out-Null

    $insertUsers = 'INSERT INTO starfree_users (name,password,mail,created,' + $groupColumn + ',authCode,assets,experience,points) VALUES ' +
        "($(Quote-Sql $authorName),'',$(Quote-Sql ($authorName + '@example.com')),UNIX_TIMESTAMP(),'contributor',$(Quote-Sql $authorToken),0,50,0)," +
        "($(Quote-Sql $followerName),'',$(Quote-Sql ($followerName + '@example.com')),UNIX_TIMESTAMP(),'contributor',$(Quote-Sql $followerToken),0,50,0)," +
        "($(Quote-Sql $adminName),'',$(Quote-Sql ($adminName + '@example.com')),UNIX_TIMESTAMP(),'administrator',$(Quote-Sql $adminToken),0,50,0);"
    Invoke-LocalMysql $insertUsers | Out-Null
    $authorUid = [int](Invoke-LocalMysql "SELECT uid FROM starfree_users WHERE name=$(Quote-Sql $authorName) LIMIT 1;")
    $followerUid = [int](Invoke-LocalMysql "SELECT uid FROM starfree_users WHERE name=$(Quote-Sql $followerName) LIMIT 1;")
    $adminUid = [int](Invoke-LocalMysql "SELECT uid FROM starfree_users WHERE name=$(Quote-Sql $adminName) LIMIT 1;")
    Invoke-LocalMysql "INSERT INTO starfree_fan (created,uid,touid) VALUES (UNIX_TIMESTAMP(),$followerUid,$authorUid);" | Out-Null

    $publicText = $marker + ' public gallery'
    $added = Invoke-LocalApi '/SFreeSpace/addSpace' @{ token = $authorToken; type = 0; text = $publicText; pic = 'https://example.com/a.png||https://example.com/b.png'; onlyMe = 0 }
    Assert-Equal $added.code 1 'Public space creation failed'
    $publicId = [int](Invoke-LocalMysql "SELECT id FROM starfree_space WHERE uid=$authorUid AND text=$(Quote-Sql $publicText) ORDER BY id DESC LIMIT 1;")
    Assert-True ($publicId -gt 0) 'Public space row was not created'

    $info = Invoke-LocalApi '/SFreeSpace/spaceInfo' @{ id = $publicId }
    Assert-Equal $info.code 1 'Anonymous public spaceInfo failed'
    Assert-Equal ([int]$info.data.id) $publicId 'spaceInfo returned the wrong space'
    Assert-Equal ([int]$info.data.isLikes) 0 'Anonymous isLikes should be 0'

    $editedPublicText = $marker + ' public gallery edited'
    $edited = Invoke-LocalApi '/SFreeSpace/editSpace' @{ token = $authorToken; id = $publicId; type = 0; text = $editedPublicText; pic = 'https://example.com/edited.png'; toid = 0; onlyMe = 0 }
    Assert-Equal $edited.code 1 'Owner space edit failed'
    Assert-Equal (Invoke-LocalMysql "SELECT CONCAT(uid,'|',type,'|',text) FROM starfree_space WHERE id=$publicId;") ($authorUid.ToString() + '|0|' + $editedPublicText) 'Owner/type changed during edit'
    $deniedEdit = Invoke-LocalApi '/SFreeSpace/editSpace' @{ token = $followerToken; id = $publicId; type = 0; text = ($marker + ' unauthorized edit'); toid = 0; onlyMe = 0 }
    Assert-Equal $deniedEdit.code 0 'Non-owner edit should be rejected'

    $privateText = $marker + ' private note'
    $privateAdded = Invoke-LocalApi '/SFreeSpace/addSpace' @{ token = $authorToken; type = 0; text = $privateText; pic = 'https://example.com/private.png'; onlyMe = 1 }
    Assert-Equal $privateAdded.code 1 'Private space creation failed'
    $privateId = [int](Invoke-LocalMysql "SELECT id FROM starfree_space WHERE uid=$authorUid AND text=$(Quote-Sql $privateText) ORDER BY id DESC LIMIT 1;")
    $privateAnon = Invoke-LocalApi '/SFreeSpace/spaceInfo' @{ id = $privateId }
    Assert-Equal $privateAnon.code 0 'Anonymous private spaceInfo should be rejected'
    $privateOwner = Invoke-LocalApi '/SFreeSpace/spaceInfo' @{ token = $authorToken; id = $privateId }
    Assert-Equal $privateOwner.code 1 'Owner private spaceInfo should be allowed'

    $replyText = $marker + ' reply text'
    $reply = Invoke-LocalApi '/SFreeSpace/addSpace' @{ token = $followerToken; type = 3; text = $replyText; toid = $publicId }
    Assert-Equal $reply.code 1 'Reply creation failed'
    $replyId = [int](Invoke-LocalMysql "SELECT id FROM starfree_space WHERE uid=$followerUid AND text=$(Quote-Sql $replyText) ORDER BY id DESC LIMIT 1;")

    $forwardText = $marker + ' forward text'
    $forward = Invoke-LocalApi '/SFreeSpace/addSpace' @{ token = $followerToken; type = 2; text = $forwardText; toid = $publicId }
    Assert-Equal $forward.code 1 'Forward creation failed'
    $forwardId = [int](Invoke-LocalMysql "SELECT id FROM starfree_space WHERE uid=$followerUid AND text=$(Quote-Sql $forwardText) ORDER BY id DESC LIMIT 1;")

    $pluginText = $marker + ' plugin payload'
    $plugin = Invoke-LocalApi '/SFreeSpace/addSpace' @{ token = $authorToken; type = 6; text = $pluginText; onlyMe = 0 }
    Assert-Equal $plugin.code 0 'Plugin Space type 6 must remain disabled'
    Assert-Equal (Invoke-LocalMysql "SELECT COUNT(*) FROM starfree_space WHERE uid=$authorUid AND text=$(Quote-Sql $pluginText);") '0' 'Plugin Space row must not be created'

    $list = Invoke-LocalApi '/SFreeSpace/spaceList' @{ page = 1; limit = 20; searchKey = $marker }
    Assert-Equal $list.code 1 'Anonymous spaceList failed'
    Assert-True (@($list.data | Where-Object { $_.id -eq $replyId }).Count -eq 0) 'Default spaceList leaked replies'
    Assert-True (@($list.data | Where-Object { $_.id -eq $privateId }).Count -eq 0) 'Anonymous spaceList leaked private rows'
    Assert-True (@($list.data | Where-Object { $_.id -eq $publicId }).Count -eq 1) 'Anonymous spaceList did not include public row'

    $replyFilters = @{ type = 3 } | ConvertTo-Json -Compress
    $replyList = Invoke-LocalApi '/SFreeSpace/spaceList' @{ page = 1; limit = 20; searchKey = $marker; searchParams = $replyFilters }
    Assert-True (@($replyList.data | Where-Object { $_.id -eq $replyId }).Count -eq 1) 'Explicit reply filter did not return reply'

    $like = Invoke-LocalApi '/SFreeSpace/spaceLikes' @{ token = $followerToken; id = $publicId }
    Assert-Equal $like.code 1 'Space like failed'
    $duplicateLike = Invoke-LocalApi '/SFreeSpace/spaceLikes' @{ token = $followerToken; id = $publicId }
    Assert-Equal $duplicateLike.code 0 'Duplicate like should be rejected'
    Assert-Equal (Invoke-LocalMysql "SELECT likes FROM starfree_space WHERE id=$publicId;") '1' 'Like counter should be 1'
    Assert-Equal (Invoke-LocalMysql "SELECT COUNT(*) FROM starfree_userlog WHERE uid=$followerUid AND cid=$publicId AND type='spaceLike';") '1' 'spaceLike log count should be 1'

    $followed = Invoke-LocalApi '/SFreeSpace/followSpace' @{ token = $followerToken; page = 1; limit = 20 }
    Assert-Equal $followed.code 1 'followSpace failed'
    Assert-True (@($followed.data | Where-Object { $_.id -eq $publicId }).Count -eq 1) 'followSpace missing followed public row'
    Assert-True (@($followed.data | Where-Object { $_.id -eq $privateId }).Count -eq 0) 'followSpace leaked private row'
    Assert-True (@($followed.data | Where-Object { $_.id -eq $replyId }).Count -eq 0) 'followSpace leaked reply row'
    $followedAlias = Invoke-LocalApi '/SFreeSpace/myFollowSpace' @{ token = $followerToken; page = 1; limit = 20 }
    Assert-Equal $followedAlias.code 1 'myFollowSpace alias failed'

    Invoke-LocalMysql "UPDATE starfree_apiconfig SET spaceAudit=1 ORDER BY id LIMIT 1;" | Out-Null
    $pendingText = $marker + ' pending review'
    $pendingAdd = Invoke-LocalApi '/SFreeSpace/addSpace' @{ token = $authorToken; type = 0; text = $pendingText; pic = 'https://example.com/pending.png'; onlyMe = 0 }
    Assert-Equal $pendingAdd.code 1 'Pending space creation failed'
    $pendingId = [int](Invoke-LocalMysql "SELECT id FROM starfree_space WHERE uid=$authorUid AND text=$(Quote-Sql $pendingText) ORDER BY id DESC LIMIT 1;")
    Assert-Equal (Invoke-LocalMysql "SELECT status FROM starfree_space WHERE id=$pendingId;") '0' 'Pending status should be 0'
    $pendingAnon = Invoke-LocalApi '/SFreeSpace/spaceInfo' @{ id = $pendingId }
    Assert-Equal $pendingAnon.code 0 'Anonymous pending spaceInfo should be rejected'
    $approved = Invoke-LocalApi '/SFreeSpace/spaceReview' @{ token = $adminToken; id = $pendingId; type = 1 }
    Assert-Equal $approved.code 1 'Space review approval failed'
    Assert-Equal (Invoke-LocalMysql "SELECT status FROM starfree_space WHERE id=$pendingId;") '1' 'Reviewed status should be 1'

    $rejectedText = $marker + ' pending rejection'
    $rejectedAdd = Invoke-LocalApi '/SFreeSpace/addSpace' @{ token = $authorToken; type = 0; text = $rejectedText; onlyMe = 0 }
    Assert-Equal $rejectedAdd.code 1 'Second pending space creation failed'
    $rejectedId = [int](Invoke-LocalMysql "SELECT id FROM starfree_space WHERE uid=$authorUid AND text=$(Quote-Sql $rejectedText) ORDER BY id DESC LIMIT 1;")
    $nonStaffReview = Invoke-LocalApi '/SFreeSpace/spaceReview' @{ token = $followerToken; id = $rejectedId; type = 1 }
    Assert-Equal $nonStaffReview.code 0 'Non-staff Space review should be rejected'
    $noticeCountBefore = [int](Invoke-LocalMysql "SELECT COUNT(*) FROM starfree_inbox WHERE uid=$adminUid AND touid=$authorUid;")
    $rejected = Invoke-LocalApi '/SFreeSpace/spaceReview' @{ token = $adminToken; id = $rejectedId; type = 0 }
    Assert-Equal $rejected.code 1 'Pending Space rejection failed'
    Assert-Equal (Invoke-LocalMysql "SELECT COUNT(*) FROM starfree_space WHERE id=$rejectedId;") '0' 'Rejected pending Space should be deleted'
    Assert-Equal ([int](Invoke-LocalMysql "SELECT COUNT(*) FROM starfree_inbox WHERE uid=$adminUid AND touid=$authorUid;")) ($noticeCountBefore + 1) 'Space rejection notice was not written'

    $lock = Invoke-LocalApi '/SFreeSpace/spaceLock' @{ token = $adminToken; id = $publicId; type = 2 }
    Assert-Equal $lock.code 1 'Space lock failed'
    $lockedReply = Invoke-LocalApi '/SFreeSpace/addSpace' @{ token = $followerToken; type = 3; text = ($marker + ' locked reply'); toid = $publicId }
    Assert-Equal $lockedReply.code 0 'Reply to locked space should be rejected'
    $unlock = Invoke-LocalApi '/SFreeSpace/spaceLock' @{ token = $adminToken; id = $publicId; type = 1 }
    Assert-Equal $unlock.code 1 'Space unlock failed'

    $deniedDelete = Invoke-LocalApi '/SFreeSpace/spaceDelete' @{ token = $followerToken; id = $publicId }
    Assert-Equal $deniedDelete.code 0 'Non-owner delete should be rejected'
    $ownerDelete = Invoke-LocalApi '/SFreeSpace/spaceDelete' @{ token = $authorToken; id = $privateId }
    Assert-Equal $ownerDelete.code 1 'Owner delete failed'

    Write-Output 'Local space integration test passed.'
} finally {
    if ($oldConfig) {
        $parts = $oldConfig.Split('|', 7)
        if ($parts.Count -eq 7) {
            $forbidden = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($parts[6]))
            Invoke-LocalMysql ("UPDATE starfree_apiconfig SET spaceMinExp={0},spaceAudit={1},postMax={2},postExp={3},identifysmPost={4},identifylvPost={5},forbidden={6} ORDER BY id LIMIT 1;" -f $parts[0],$parts[1],$parts[2],$parts[3],$parts[4],$parts[5],(Quote-Sql $forbidden)) | Out-Null
        }
    }
    if ($authorUid -gt 0 -or $followerUid -gt 0 -or $adminUid -gt 0) {
        $ids = @($authorUid,$followerUid,$adminUid) | Where-Object { $_ -gt 0 }
        if ($ids.Count -gt 0) {
            $idList = ($ids -join ',')
            Invoke-LocalMysql "DELETE FROM starfree_userlog WHERE uid IN ($idList) OR toid IN ($idList);" | Out-Null
            Invoke-LocalMysql "DELETE FROM starfree_inbox WHERE uid IN ($idList) OR touid IN ($idList);" | Out-Null
            Invoke-LocalMysql "DELETE FROM starfree_fan WHERE uid IN ($idList) OR touid IN ($idList);" | Out-Null
            Invoke-LocalMysql "DELETE FROM starfree_space WHERE uid IN ($idList) OR text LIKE $(Quote-Sql ($marker + '%'));" | Out-Null
            Invoke-LocalMysql "DELETE FROM starfree_users WHERE uid IN ($idList);" | Out-Null
        }
    }
    if ($null -eq $oldMysqlPassword) {
        Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
    } else {
        $env:MYSQL_PWD = $oldMysqlPassword
    }
}
