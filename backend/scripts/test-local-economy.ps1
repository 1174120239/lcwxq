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
    return ($output -join "`n").Trim()
}

function Invoke-LocalApi {
    param(
        [string]$Path,
        [hashtable]$Body,
        [string]$Method = 'GET'
    )

    return Invoke-RestMethod -Uri ($ApiBase + $Path) -Method $Method -Body $Body `
        -ContentType 'application/x-www-form-urlencoded' -TimeoutSec 15
}

function Assert-Equal {
    param($Actual, $Expected, [string]$Message)

    if ($Actual -ne $Expected) {
        throw "$Message (expected=$Expected, actual=$Actual)"
    }
}

$marker = 'codex_it_' + [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$senderName = $marker + '_sender'
$authorName = $marker + '_author'
$senderToken = $marker + '_sender_token'
$authorToken = $marker + '_author_token'
$senderUid = 0
$authorUid = 0
$cid = 0
$videoCid = 0
$pendingCid = 0
$categoryMid = 0
$tagMid = 0
$markId = 0
$commentId = 0
$waitingCommentId = 0
$shopId = 0
$vipPackageId = 0
$firstWithdrawalId = 0
$secondWithdrawalId = 0

$oldMysqlPassword = $env:MYSQL_PWD
$env:MYSQL_PWD = $databasePassword
try {
    $insertUsers = 'INSERT INTO starfree_users (name,password,created,`group`,authCode,assets,experience,points,pay) ' +
        "VALUES ('$senderName','',UNIX_TIMESTAMP(),'contributor','$senderToken',100,0,0,'integration-pay')," +
        "('$authorName','',UNIX_TIMESTAMP(),'administrator','$authorToken',0,0,0,'integration-pay');"
    Invoke-LocalMysql $insertUsers | Out-Null
    $senderUid = [int](Invoke-LocalMysql "SELECT uid FROM starfree_users WHERE name='$senderName' LIMIT 1;")
    $authorUid = [int](Invoke-LocalMysql "SELECT uid FROM starfree_users WHERE name='$authorName' LIMIT 1;")

    Invoke-LocalMysql ('INSERT INTO starfree_metas (name,slug,type,description,count,`order`,parent,isrecommend) VALUES ' +
        "('$($marker)_category','$($marker)-category','category','integration',0,0,0,0)," +
        "('$($marker)_tag','$($marker)-tag','tag','integration',0,0,0,0);") | Out-Null
    $categoryMid = [int](Invoke-LocalMysql "SELECT mid FROM starfree_metas WHERE slug='$($marker)-category' LIMIT 1;")
    $tagMid = [int](Invoke-LocalMysql "SELECT mid FROM starfree_metas WHERE slug='$($marker)-tag' LIMIT 1;")

    $contentParams = @{ title = $marker; category = "$categoryMid,"; tag = "$tagMid,"; sid = -1 } | ConvertTo-Json -Compress
    $content = Invoke-LocalApi '/SFreeContents/contentsAdd' @{
        token = $authorToken
        params = $contentParams
        text = 'temporary integration content'
    } 'POST'
    Assert-Equal $content.code 1 'Temporary post creation failed'
    Assert-Equal ([int]$content.data) 1 'contentsAdd must keep the legacy integer data payload'
    $cid = [int](Invoke-LocalMysql "SELECT cid FROM starfree_contents WHERE authorId=$authorUid AND title='$marker' ORDER BY cid DESC LIMIT 1;")
    Assert-Equal (Invoke-LocalMysql "SELECT CONCAT(slug,'|',status,'|',type,'|',allowComment,'|',allowPing,'|',allowFeed) FROM starfree_contents WHERE cid=$cid;") ($cid.ToString() + '|publish|post|1|1|1') 'Published post columns are incompatible'
    Assert-Equal (Invoke-LocalMysql "SELECT LEFT(text,15) FROM starfree_contents WHERE cid=$cid;") '<!--markdown-->' 'Default contentsAdd must store the Markdown marker'
    Assert-Equal (Invoke-LocalMysql "SELECT GROUP_CONCAT(mid ORDER BY mid) FROM starfree_relationships WHERE cid=$cid;") ((@($categoryMid, $tagMid) | Sort-Object) -join ',') 'Published post relationships are incompatible'

    $invalidUpdateParams = @{ cid = $cid; title = ($marker + '_must_not_persist'); category = 'invalid-mid'; tag = "$tagMid,"; sid = -1 } | ConvertTo-Json -Compress
    $invalidUpdate = Invoke-LocalApi '/SFreeContents/contentsUpdate' @{
        token = $authorToken
        params = $invalidUpdateParams
        text = 'invalid relationship update'
    } 'POST'
    Assert-Equal $invalidUpdate.code 0 'Invalid relationship update should be rejected'
    Assert-Equal (Invoke-LocalMysql "SELECT title FROM starfree_contents WHERE cid=$cid;") $marker 'Rejected update changed the article row'
    Assert-Equal (Invoke-LocalMysql "SELECT GROUP_CONCAT(mid ORDER BY mid) FROM starfree_relationships WHERE cid=$cid;") ((@($categoryMid, $tagMid) | Sort-Object) -join ',') 'Rejected update changed relationships'

    $missingMid = 2147483647
    $missingUpdateParams = @{ cid = $cid; title = ($marker + '_missing_mid'); category = "$missingMid,"; tag = "$tagMid,"; sid = -1 } | ConvertTo-Json -Compress
    $missingUpdate = Invoke-LocalApi '/SFreeContents/contentsUpdate' @{
        token = $authorToken
        params = $missingUpdateParams
        text = 'missing relationship update'
    } 'POST'
    Assert-Equal $missingUpdate.code 0 'Unknown numeric relationship update should be rejected'
    Assert-Equal (Invoke-LocalMysql "SELECT title FROM starfree_contents WHERE cid=$cid;") $marker 'Unknown relationship changed the article row'
    Assert-Equal (Invoke-LocalMysql "SELECT GROUP_CONCAT(mid ORDER BY mid) FROM starfree_relationships WHERE cid=$cid;") ((@($categoryMid, $tagMid) | Sort-Object) -join ',') 'Unknown relationship changed relationships'

    $updatedTitle = $marker + '_updated'
    $updateParams = @{ cid = $cid; title = $updatedTitle; category = "$categoryMid,"; tag = "$tagMid,"; sid = -1 } | ConvertTo-Json -Compress
    $updated = Invoke-LocalApi '/SFreeContents/contentsUpdate' @{
        token = $authorToken
        params = $updateParams
        text = 'updated line one||rn||updated line two'
    } 'POST'
    Assert-Equal $updated.code 1 'Temporary post update failed'
    Assert-Equal ([int]$updated.data) 1 'contentsUpdate must keep the legacy integer data payload'
    Assert-Equal (Invoke-LocalMysql "SELECT CONCAT(title,'|',type,'|',status,'|',LEFT(text,15),'|',IF(LOCATE(CHAR(10),text)>0,1,0)) FROM starfree_contents WHERE cid=$cid;") ($updatedTitle + '|post|publish|<!--markdown-->|1') 'Updated post body or columns are incompatible'

    $videoTitle = $marker + '_video'
    $videoParams = @{ title = $videoTitle; category = "$categoryMid,"; tag = "$tagMid,"; sid = -1; type = 'video' } | ConvertTo-Json -Compress
    $video = Invoke-LocalApi '/SFreeContents/contentsAdd' @{
        token = $authorToken
        params = $videoParams
        text = 'temporary video body'
    } 'POST'
    Assert-Equal $video.code 1 'Temporary video creation failed'
    $videoCid = [int](Invoke-LocalMysql "SELECT cid FROM starfree_contents WHERE authorId=$authorUid AND title='$videoTitle' ORDER BY cid DESC LIMIT 1;")
    $videoUpdateParams = @{ cid = $videoCid; title = ($videoTitle + '_updated'); category = "$categoryMid,"; tag = "$tagMid,"; sid = -1 } | ConvertTo-Json -Compress
    $videoUpdated = Invoke-LocalApi '/SFreeContents/contentsUpdate' @{
        token = $authorToken
        params = $videoUpdateParams
        text = 'video line one||rn||video line two'
    } 'POST'
    Assert-Equal $videoUpdated.code 1 'Temporary video update failed'
    Assert-Equal ([int]$videoUpdated.data) 1 'Video update must keep the legacy integer data payload'
    Assert-Equal (Invoke-LocalMysql "SELECT CONCAT(type,'|',LEFT(text,15),'|',IF(LOCATE(CHAR(10),text)>0,1,0)) FROM starfree_contents WHERE cid=$videoCid;") 'video|<!--markdown-->|1' 'Video type or Markdown body changed during update'

    $pendingParams = @{ title = ($marker + '_pending'); category = ''; tag = '' } | ConvertTo-Json -Compress
    $pendingContent = Invoke-LocalApi '/SFreeContents/contentsAdd' @{
        token = $senderToken
        params = $pendingParams
        text = 'temporary pending content'
    } 'POST'
    Assert-Equal $pendingContent.code 1 'Pending post creation failed'
    Assert-Equal ([int]$pendingContent.data) 1 'Pending contentsAdd must keep the legacy integer data payload'
    $pendingCid = [int](Invoke-LocalMysql "SELECT cid FROM starfree_contents WHERE authorId=$senderUid AND title='$($marker)_pending' ORDER BY cid DESC LIMIT 1;")
    Assert-Equal (Invoke-LocalMysql "SELECT status FROM starfree_contents WHERE cid=$pendingCid;") 'waiting' 'Non-staff post should wait at audit level two'
    $contentAudit = Invoke-LocalApi '/SFreeContents/contentsAudit' @{
        token = $authorToken; key = $pendingCid; type = 0
    }
    Assert-Equal $contentAudit.code 1 'Content audit failed'
    Assert-Equal $contentAudit.data.status 'publish' 'Approved content status is incorrect'

    $commentParams = @{ cid = $cid; parent = 0 } | ConvertTo-Json -Compress
    $comment = Invoke-LocalApi '/SFreeComments/commentsAdd' @{
        token = $senderToken
        params = $commentParams
        text = 'temporary integration comment'
    }
    Assert-Equal $comment.code 1 'Temporary comment creation failed'
    Assert-Equal $comment.data.status 'approved' 'Comment should be approved at audit level zero'
    $commentId = [int](Invoke-LocalMysql "SELECT coid FROM starfree_comments WHERE cid=$cid AND authorId=$senderUid ORDER BY coid DESC LIMIT 1;")

    $waitingSql = 'INSERT INTO starfree_comments (cid,created,author,authorId,ownerId,text,type,status,parent,likes,pic) ' +
        "VALUES ($cid,UNIX_TIMESTAMP(),'$senderName',$senderUid,$authorUid,'temporary waiting comment','comment','waiting',0,0,''); " +
        "UPDATE starfree_contents SET commentsNum=(SELECT COUNT(*) FROM starfree_comments WHERE cid=$cid) WHERE cid=$cid;"
    Invoke-LocalMysql $waitingSql | Out-Null
    $waitingCommentId = [int](Invoke-LocalMysql "SELECT coid FROM starfree_comments WHERE cid=$cid AND status='waiting' ORDER BY coid DESC LIMIT 1;")
    $commentAudit = Invoke-LocalApi '/SFreeComments/commentsAudit' @{
        token = $authorToken; key = $waitingCommentId; type = 0
    }
    Assert-Equal $commentAudit.code 1 'Comment audit failed'
    Assert-Equal $commentAudit.data.status 'approved' 'Approved comment status is incorrect'

    $markParams = @{ cid = $cid; type = 'mark' } | ConvertTo-Json -Compress
    $mark = Invoke-LocalApi '/SFreeUserlog/addLog' @{ token = $senderToken; params = $markParams }
    Assert-Equal $mark.code 1 'Bookmark creation failed'
    $duplicateMark = Invoke-LocalApi '/SFreeUserlog/addLog' @{ token = $senderToken; params = $markParams }
    Assert-Equal $duplicateMark.code 0 'Duplicate bookmark should be rejected'
    $markStatus = Invoke-LocalApi '/SFreeUserlog/isMark' @{ token = $senderToken; cid = $cid }
    Assert-Equal $markStatus.code 1 'Bookmark status failed'
    Assert-Equal $markStatus.data.isMark 1 'Bookmark status was not set'
    $markId = [int]$markStatus.data.logid
    if ($markId -le 0) {
        throw 'Bookmark response did not return a valid logid.'
    }

    $markList = Invoke-LocalApi '/SFreeUserlog/markList' @{ token = $senderToken; page = 1; limit = 8 }
    Assert-Equal $markList.code 1 'Bookmark list failed'
    Assert-Equal $markList.count 1 'Bookmark list count is incompatible'
    Assert-Equal $markList.data[0].logid $markId 'Bookmark list did not include logid'

    $rewardRequestId = $marker + '_reward'
    $rewardParams = @{ cid = $cid; type = 'reward'; num = 10 } | ConvertTo-Json -Compress
    $reward = Invoke-LocalApi '/SFreeUserlog/addLog' @{
        token = $senderToken; params = $rewardParams; requestId = $rewardRequestId
    }
    Assert-Equal $reward.code 1 'Reward transfer failed'
    $rewardReplay = Invoke-LocalApi '/SFreeUserlog/addLog' @{
        token = $senderToken; params = $rewardParams; requestId = $rewardRequestId
    }
    Assert-Equal $rewardReplay.code 1 'Idempotent reward replay failed'
    $balances = Invoke-LocalMysql "SELECT CONCAT(assets,',',(SELECT assets FROM starfree_users WHERE uid=$authorUid)) FROM starfree_users WHERE uid=$senderUid;"
    Assert-Equal $balances '90,10' 'Reward balances are incorrect'
    Assert-Equal (Invoke-LocalMysql "SELECT COUNT(*) FROM starfree_userlog WHERE uid=$senderUid AND cid=$cid AND type='reward';") '1' 'Reward replay created a duplicate userlog'

    # The PHP seven-day sign-in feature changes assets and experience only. It must
    # never mutate the separate task/shop points column.
    $signinConfig = Invoke-LocalApi '/SFreeEconomy/signinConfig' @{}
    $signinAssets = [int]$signinConfig.assets_1day
    $signinExperience = [int]$signinConfig.experience_1day
    $beforeSignin = Invoke-LocalMysql "SELECT CONCAT(assets,',',experience,',',points) FROM starfree_users WHERE uid=$senderUid;"
    $beforeSigninParts = $beforeSignin.Split(',')
    $signin = Invoke-LocalApi '/SFreeEconomy/signin' @{ token = $senderToken }
    Assert-Equal $signin.code 1 'Seven-day sign-in failed'
    Assert-Equal ([int]$signin.data.assets) $signinAssets 'Sign-in assets award is incompatible'
    Assert-Equal ([int]$signin.data.experience) $signinExperience 'Sign-in experience award is incompatible'
    $afterSignin = Invoke-LocalMysql "SELECT CONCAT(assets,',',experience,',',points) FROM starfree_users WHERE uid=$senderUid;"
    Assert-Equal $afterSignin (([int]$beforeSigninParts[0] + $signinAssets).ToString() + ',' + ([int]$beforeSigninParts[1] + $signinExperience).ToString() + ',' + $beforeSigninParts[2]) 'Sign-in changed an unexpected balance column'
    $streak = Invoke-LocalApi '/SFreeEconomy/signinStreak' @{ token = $senderToken }
    Assert-Equal ([int]$streak.leiji) 1 'Sign-in streak is incorrect'

    # Administrator/editor adjustments support wallet assets and task points as
    # distinct columns, with the same requestId replaying instead of applying twice.
    $assetsBeforeAdjustment = [int](Invoke-LocalMysql "SELECT assets FROM starfree_users WHERE uid=$senderUid;")
    $assetAdjustment = @{
        token = $authorToken; key = $senderUid; num = 20; type = 0;
        rechargeType = 0; requestId = ($marker + '_adjust_assets')
    }
    Assert-Equal (Invoke-LocalApi '/SFreeUsers/userRecharge' $assetAdjustment).code 1 'Manual assets adjustment failed'
    Assert-Equal (Invoke-LocalApi '/SFreeUsers/userRecharge' $assetAdjustment).code 1 'Manual assets adjustment replay failed'
    Assert-Equal ([int](Invoke-LocalMysql "SELECT assets FROM starfree_users WHERE uid=$senderUid;")) ($assetsBeforeAdjustment + 20) 'Manual assets adjustment was not idempotent'

    $pointAdjustment = @{
        token = $authorToken; key = $senderUid; num = 12; type = 0;
        rechargeType = 1; requestId = ($marker + '_adjust_points')
    }
    Assert-Equal (Invoke-LocalApi '/SFreeUsers/userRecharge' $pointAdjustment).code 1 'Manual points adjustment failed'
    Assert-Equal (Invoke-LocalApi '/SFreeUsers/userRecharge' $pointAdjustment).code 1 'Manual points adjustment replay failed'
    Assert-Equal (Invoke-LocalMysql "SELECT points FROM starfree_users WHERE uid=$senderUid;") '12' 'Manual points adjustment was not idempotent'

    # One shop purchase transfers wallet assets and optional points to the seller,
    # decrements stock once, and returns the committed result on a retry.
    Invoke-LocalMysql ("INSERT INTO starfree_shop " +
        "(title,price,num,type,uid,vipDiscount,created,status,sellNum,integral) VALUES " +
        "('$($marker)_shop',10,1,0,$authorUid,'1',UNIX_TIMESTAMP(),1,0,3);") | Out-Null
    $shopId = [int](Invoke-LocalMysql "SELECT id FROM starfree_shop WHERE title='$($marker)_shop' LIMIT 1;")
    $shopBalancesBefore = Invoke-LocalMysql "SELECT CONCAT(assets,',',points,',',(SELECT assets FROM starfree_users WHERE uid=$authorUid),',',(SELECT points FROM starfree_users WHERE uid=$authorUid)) FROM starfree_users WHERE uid=$senderUid;"
    $shopParts = $shopBalancesBefore.Split(',')
    $shopRequest = @{
        token = $senderToken; sid = $shopId; isIntegral = 1;
        requestId = ($marker + '_shop_buy')
    }
    Assert-Equal (Invoke-LocalApi '/SFreeShop/buyShop' $shopRequest).code 1 'Shop purchase failed'
    Assert-Equal (Invoke-LocalApi '/SFreeShop/buyShop' $shopRequest).code 1 'Shop purchase replay failed'
    $shopBalancesAfter = Invoke-LocalMysql "SELECT CONCAT(assets,',',points,',',(SELECT assets FROM starfree_users WHERE uid=$authorUid),',',(SELECT points FROM starfree_users WHERE uid=$authorUid)) FROM starfree_users WHERE uid=$senderUid;"
    Assert-Equal $shopBalancesAfter (([int]$shopParts[0] - 7).ToString() + ',' + ([int]$shopParts[1] - 3).ToString() + ',' + ([int]$shopParts[2] + 7).ToString() + ',' + ([int]$shopParts[3] + 3).ToString()) 'Shop balances were transferred more than once or to the wrong column'
    Assert-Equal (Invoke-LocalMysql "SELECT CONCAT(num,',',sellNum) FROM starfree_shop WHERE id=$shopId;") '0,1' 'Shop stock or sales count is incorrect'

    # VIP package purchase consumes wallet assets once and extends vip without
    # changing points or experience.
    Invoke-LocalMysql "INSERT INTO starfree_vips (orderKey,name,price,day,giftDay,intro) VALUES (999,'$($marker)_vip',5,1,0,'integration');" | Out-Null
    $vipPackageId = [int](Invoke-LocalMysql "SELECT id FROM starfree_vips WHERE name='$($marker)_vip' LIMIT 1;")
    $beforeVip = Invoke-LocalMysql "SELECT CONCAT(assets,',',points,',',experience) FROM starfree_users WHERE uid=$senderUid;"
    $beforeVipParts = $beforeVip.Split(',')
    $vipRequest = @{
        token = $senderToken; id = $vipPackageId;
        requestId = ($marker + '_vip_package')
    }
    Assert-Equal (Invoke-LocalApi '/SFreeShop/buyVIPpackage' $vipRequest).code 1 'VIP package purchase failed'
    Assert-Equal (Invoke-LocalApi '/SFreeShop/buyVIPpackage' $vipRequest).code 1 'VIP package replay failed'
    Assert-Equal (Invoke-LocalMysql "SELECT CONCAT(assets,',',points,',',experience) FROM starfree_users WHERE uid=$senderUid;") (([int]$beforeVipParts[0] - 5).ToString() + ',' + $beforeVipParts[1] + ',' + $beforeVipParts[2]) 'VIP purchase changed an unexpected balance or charged twice'
    if ([long](Invoke-LocalMysql "SELECT vip FROM starfree_users WHERE uid=$senderUid;") -le [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()) {
        throw 'VIP package did not extend the user expiry.'
    }

    # Applying for withdrawal does not debit. Approval debits and sets cid=0;
    # rejection leaves the balance unchanged and sets cid=-2.
    $beforeWithdrawal = [int](Invoke-LocalMysql "SELECT assets FROM starfree_users WHERE uid=$senderUid;")
    $withdrawRequest = @{
        token = $senderToken; num = 4; requestId = ($marker + '_withdraw_approve')
    }
    Assert-Equal (Invoke-LocalApi '/SFreeUsers/userWithdraw' $withdrawRequest).code 1 'Withdrawal application failed'
    Assert-Equal (Invoke-LocalApi '/SFreeUsers/userWithdraw' $withdrawRequest).code 1 'Withdrawal application replay failed'
    Assert-Equal ([int](Invoke-LocalMysql "SELECT assets FROM starfree_users WHERE uid=$senderUid;")) $beforeWithdrawal 'Withdrawal application debited before approval'
    $firstWithdrawalId = [int](Invoke-LocalMysql "SELECT id FROM starfree_userlog WHERE uid=$senderUid AND type='withdraw' AND cid=-1 ORDER BY id DESC LIMIT 1;")
    Assert-Equal (Invoke-LocalApi '/SFreeUsers/withdrawStatus' @{ token = $authorToken; key = $firstWithdrawalId; type = 1 }).code 1 'Withdrawal approval failed'
    Assert-Equal (Invoke-LocalMysql "SELECT CONCAT(cid,',',(SELECT assets FROM starfree_users WHERE uid=$senderUid)) FROM starfree_userlog WHERE id=$firstWithdrawalId;") ('0,' + ($beforeWithdrawal - 4)) 'Withdrawal approval state or debit is incorrect'

    $beforeRejectedWithdrawal = [int](Invoke-LocalMysql "SELECT assets FROM starfree_users WHERE uid=$senderUid;")
    Assert-Equal (Invoke-LocalApi '/SFreeUsers/userWithdraw' @{
        token = $senderToken; num = 3; requestId = ($marker + '_withdraw_reject')
    }).code 1 'Second withdrawal application failed'
    $secondWithdrawalId = [int](Invoke-LocalMysql "SELECT id FROM starfree_userlog WHERE uid=$senderUid AND type='withdraw' AND cid=-1 ORDER BY id DESC LIMIT 1;")
    Assert-Equal (Invoke-LocalApi '/SFreeUsers/withdrawStatus' @{ token = $authorToken; key = $secondWithdrawalId; type = 0 }).code 1 'Withdrawal rejection failed'
    Assert-Equal (Invoke-LocalMysql "SELECT CONCAT(cid,',',(SELECT assets FROM starfree_users WHERE uid=$senderUid)) FROM starfree_userlog WHERE id=$secondWithdrawalId;") ('-2,' + $beforeRejectedWithdrawal) 'Withdrawal rejection changed the balance or wrong status'

    $walletOrders = Invoke-LocalApi '/pay/payorderList' @{ token = $senderToken }
    Assert-Equal $walletOrders.code 1 'Wallet pay order list failed'
    if ($null -eq $walletOrders.paydata -or @($walletOrders.paydata).Count -lt 6) {
        throw 'Wallet pay order list did not return the legacy top-level paydata payload.'
    }
    $finance = Invoke-LocalApi '/pay/financeList' @{
        token = $authorToken; page = 1; limit = 30;
        searchParams = (@{ uid = $senderUid } | ConvertTo-Json -Compress)
    }
    Assert-Equal $finance.code 1 'Administrator finance list failed'
    if ([int]$finance.total -lt 6) {
        throw "Administrator finance list is missing economy rows (actual=$($finance.total))"
    }
    $financeTotal = Invoke-LocalApi '/pay/financeTotal' @{ token = $authorToken }
    Assert-Equal $financeTotal.code 1 'Finance totals failed'
    foreach ($field in @('recharge','trade','withdraw','income')) {
        if ($null -eq $financeTotal.data.$field) {
            throw "Finance totals omitted field: $field"
        }
    }

    $follow = Invoke-LocalApi '/SFreeUsers/follow' @{ token = $senderToken; touid = $authorUid; type = 1 }
    Assert-Equal $follow.code 1 'Follow creation failed'
    $isFollow = Invoke-LocalApi '/SFreeUsers/isFollow' @{ token = $senderToken; touid = $authorUid }
    Assert-Equal $isFollow.code 1 'Follow status was not set'
    $followList = Invoke-LocalApi '/SFreeUsers/followList' @{ uid = $senderUid; page = 1; limit = 10 }
    Assert-Equal $followList.code 1 'Follow list failed'
    Assert-Equal $followList.total 1 'Follow list total is incorrect'
    Assert-Equal ([int]$followList.data[0].userJson.uid) $authorUid 'Follow list user is incorrect'
    $fanList = Invoke-LocalApi '/SFreeUsers/fanList' @{ touid = $authorUid; page = 1; limit = 10 }
    Assert-Equal $fanList.code 1 'Fan list failed'
    Assert-Equal $fanList.total 1 'Fan list total is incorrect'
    Assert-Equal ([int]$fanList.data[0].userJson.uid) $senderUid 'Fan list user is incorrect'

    $inbox = Invoke-LocalApi '/SFreeUsers/inbox' @{ token = $authorToken; page = 1; limit = 10 }
    Assert-Equal $inbox.code 1 'Inbox list failed'
    $commentInbox = @($inbox.data | Where-Object { $_.type -eq 'comment' }) | Select-Object -First 1
    $fanInbox = @($inbox.data | Where-Object { $_.type -eq 'fan' }) | Select-Object -First 1
    if ($null -eq $fanInbox) {
        throw 'Expected a fan inbox row after following the content author.'
    }
    if ($null -eq $commentInbox) {
        throw 'Expected a comment inbox row for the content author.'
    }
    Assert-Equal ([int]$commentInbox.contentsInfo.cid) $cid 'Comment inbox content link is incorrect'
    Assert-Equal $commentInbox.contenTitle $updatedTitle 'Comment inbox title is incorrect'
    $unread = Invoke-LocalApi '/SFreeUsers/unreadNum' @{ token = $authorToken }
    Assert-Equal $unread.code 1 'Unread notification count failed'
    if ([int]$unread.data -lt 3) {
        throw "Expected comment, finance, and fan notifications (actual=$($unread.data))"
    }
    $setRead = Invoke-LocalApi '/SFreeUsers/setRead' @{ token = $authorToken; type = 'all' }
    Assert-Equal $setRead.code 1 'Marking notifications read failed'
    $unreadAfterRead = Invoke-LocalApi '/SFreeUsers/unreadNum' @{ token = $authorToken }
    Assert-Equal $unreadAfterRead.data 0 'Notifications were not marked read'
    $unfollow = Invoke-LocalApi '/SFreeUsers/follow' @{ token = $senderToken; touid = $authorUid; type = 0 }
    Assert-Equal $unfollow.code 1 'Unfollow failed'
    $isFollowAfter = Invoke-LocalApi '/SFreeUsers/isFollow' @{ token = $senderToken; touid = $authorUid }
    Assert-Equal $isFollowAfter.code 0 'Follow status remained set after unfollow'

    $clockParams = @{ type = 'clock' } | ConvertTo-Json -Compress
    $clock = Invoke-LocalApi '/SFreeUserlog/addLog' @{ token = $senderToken; params = $clockParams }
    Assert-Equal $clock.code 1 'First clock-in failed'
    Assert-Equal $clock.clockData.award 0 'Clock award should follow current zero configuration'
    $duplicateClock = Invoke-LocalApi '/SFreeUserlog/addLog' @{ token = $senderToken; params = $clockParams }
    Assert-Equal $duplicateClock.code 1 'Idempotent clock replay failed'
    Assert-Equal (Invoke-LocalMysql "SELECT COUNT(*) FROM starfree_userlog WHERE uid=$senderUid AND type='clock';") '1' 'Clock replay created a duplicate userlog'

    $removeMark = Invoke-LocalApi '/SFreeUserlog/removeLog' @{ token = $senderToken; key = $markId }
    Assert-Equal $removeMark.code 1 'Bookmark removal failed'

    $userDelete = Invoke-LocalApi '/SFreeComments/commentsDelete' @{ token = $senderToken; key = $commentId }
    Assert-Equal $userDelete.code 0 'Comment deletion should obey allowDelete=0'
    $adminDelete = Invoke-LocalApi '/SFreeComments/commentsDelete' @{ token = $authorToken; key = $commentId }
    Assert-Equal $adminDelete.code 1 'Administrator comment deletion failed'

    $deletePost = Invoke-LocalApi '/SFreeContents/contentsDelete' @{ token = $authorToken; key = $cid }
    Assert-Equal $deletePost.code 1 'Administrator post deletion with key parameter failed'

    $deletePendingPost = Invoke-LocalApi '/SFreeContents/contentsDelete' @{ token = $authorToken; key = $pendingCid }
    Assert-Equal $deletePendingPost.code 1 'Approved temporary post deletion failed'

    $deleteVideo = Invoke-LocalApi '/SFreeContents/contentsDelete' @{ token = $authorToken; key = $videoCid }
    Assert-Equal $deleteVideo.code 1 'Administrator video deletion failed'

    $paylogCount = [int](Invoke-LocalMysql "SELECT COUNT(*) FROM starfree_paylog WHERE uid IN ($senderUid,$authorUid) AND paytype IN ('toReward','reward','clock');")
    Assert-Equal $paylogCount 3 'Expected reward and clock paylogs were not written'
    $inboxCount = [int](Invoke-LocalMysql "SELECT COUNT(*) FROM starfree_inbox WHERE uid IN ($senderUid,$authorUid) OR touid IN ($senderUid,$authorUid);")
    if ($inboxCount -lt 2) {
        throw "Expected finance/deletion inbox rows were not written (actual=$inboxCount)"
    }

    Write-Output 'Local economy and social integration test passed.'
} finally {
    if ($senderUid -gt 0 -and $authorUid -gt 0) {
        if ($cid -gt 0) {
            Invoke-LocalMysql "DELETE FROM starfree_comments WHERE cid=$cid; DELETE FROM starfree_fields WHERE cid=$cid; DELETE FROM starfree_relationships WHERE cid=$cid; DELETE FROM starfree_contents WHERE cid=$cid;" | Out-Null
        }
        if ($pendingCid -gt 0) {
            Invoke-LocalMysql "DELETE FROM starfree_comments WHERE cid=$pendingCid; DELETE FROM starfree_fields WHERE cid=$pendingCid; DELETE FROM starfree_relationships WHERE cid=$pendingCid; DELETE FROM starfree_contents WHERE cid=$pendingCid;" | Out-Null
        }
        if ($videoCid -gt 0) {
            Invoke-LocalMysql "DELETE FROM starfree_comments WHERE cid=$videoCid; DELETE FROM starfree_fields WHERE cid=$videoCid; DELETE FROM starfree_relationships WHERE cid=$videoCid; DELETE FROM starfree_contents WHERE cid=$videoCid;" | Out-Null
        }
        if ($shopId -gt 0) {
            Invoke-LocalMysql "DELETE FROM starfree_shop WHERE id=$shopId;" | Out-Null
        }
        if ($vipPackageId -gt 0) {
            Invoke-LocalMysql "DELETE FROM starfree_vips WHERE id=$vipPackageId;" | Out-Null
        }
        Invoke-LocalMysql "DELETE FROM starfree_admin_Signinlog WHERE uid IN ('$senderUid','$authorUid');" | Out-Null
        Invoke-LocalMysql "DELETE FROM starfree_userlog WHERE uid IN ($senderUid,$authorUid) OR toid IN ($senderUid,$authorUid);" | Out-Null
        Invoke-LocalMysql "DELETE FROM starfree_paylog WHERE uid IN ($senderUid,$authorUid);" | Out-Null
        Invoke-LocalMysql "DELETE FROM starfree_inbox WHERE uid IN ($senderUid,$authorUid) OR touid IN ($senderUid,$authorUid);" | Out-Null
        Invoke-LocalMysql "DELETE FROM starfree_fan WHERE uid IN ($senderUid,$authorUid) OR touid IN ($senderUid,$authorUid);" | Out-Null
        Invoke-LocalMysql "DELETE FROM starfree_economy_operations WHERE actor_uid IN ($senderUid,$authorUid) OR target_uid IN ($senderUid,$authorUid);" | Out-Null
        Invoke-LocalMysql "DELETE FROM starfree_users WHERE uid IN ($senderUid,$authorUid);" | Out-Null
    }
    if ($categoryMid -gt 0 -or $tagMid -gt 0) {
        Invoke-LocalMysql "DELETE FROM starfree_metas WHERE slug IN ('$($marker)-category','$($marker)-tag');" | Out-Null
    }
    if ($null -eq $oldMysqlPassword) {
        Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
    } else {
        $env:MYSQL_PWD = $oldMysqlPassword
    }
}
