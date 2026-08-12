<?php
session_start();
include_once 'Menu.php';
include_once 'dynamicAnalyticsCommon.php';

$today = strtotime('today');
$day30 = $today - 29 * 86400;
$anonymousReady = da_table_exists($connect, 'starfree_anonymous_posts');
$pollReady = da_table_exists($connect, 'starfree_space_poll_votes');
$aiReady = da_table_exists($connect, 'starfree_space_ai_reviews');
$reportsReady = da_table_exists($connect, 'starfree_space_reports');
$identityReady = da_table_exists($connect, 'starfree_identity_options')
    && da_column_exists($connect, 'starfree_users', 'campus_option_id')
    && da_column_exists($connect, 'starfree_users', 'grade_option_id');
$uid = isset($_GET['uid']) ? max(0, intval($_GET['uid'])) : 0;

function dua_space_owner_condition($anonymousReady, $spaceAlias, $anonymousAlias, $uid) {
    return ($anonymousReady ? "COALESCE($anonymousAlias.uid,$spaceAlias.uid)" : "$spaceAlias.uid") . '=' . intval($uid);
}

if ($uid > 0) {
    $identitySelect = $identityReady
        ? ",campus.name campus_name,grade.name grade_name"
        : ",'' campus_name,'' grade_name";
    $identityJoin = $identityReady
        ? " LEFT JOIN starfree_identity_options campus ON campus.id=u.campus_option_id"
            . " LEFT JOIN starfree_identity_options grade ON grade.id=u.grade_option_id"
        : '';
    $user = da_row($connect, "SELECT u.uid,u.name,u.screenName,u.created,u.logged,u.`group`,u.introduce"
        . $identitySelect . " FROM starfree_users u" . $identityJoin . " WHERE u.uid=$uid LIMIT 1");
    if (count($user) === 0) {
        echo '<div class="alert alert-danger">用户不存在或已经删除。</div>';
        include_once 'Footer.php';
        exit;
    }

    $ownerJoin = $anonymousReady ? " LEFT JOIN starfree_anonymous_posts a ON a.sid=s.id" : '';
    $ownerCondition = dua_space_owner_condition($anonymousReady, 's', 'a', $uid);
    $summary = da_row($connect, "SELECT "
        . "SUM(CASE WHEN s.type<>3 AND s.status=1 AND s.onlyMe=0 THEN 1 ELSE 0 END) posts,"
        . "SUM(CASE WHEN s.type=3 AND s.status=1 THEN 1 ELSE 0 END) replies,"
        . "SUM(CASE WHEN s.type<>3 AND s.status=0 THEN 1 ELSE 0 END) pending_posts,"
        . "SUM(CASE WHEN s.type<>3 AND s.onlyMe=1 THEN 1 ELSE 0 END) private_posts,"
        . "COALESCE(SUM(CASE WHEN s.type<>3 AND s.status=1 AND s.onlyMe=0 THEN s.likes ELSE 0 END),0) likes,"
        . "COALESCE(SUM(CASE WHEN s.type<>3 AND s.status=1 AND s.onlyMe=0 THEN s.views ELSE 0 END),0) views,"
        . "MIN(CASE WHEN s.type<>3 THEN s.created END) first_post,MAX(s.created) last_activity "
        . "FROM starfree_space s" . $ownerJoin . " WHERE $ownerCondition");
    $coverageAll = da_reply_coverage($connect, $uid, null, $anonymousReady);
    $coverage30 = da_reply_coverage($connect, $uid, $day30, $anonymousReady);
    $activeDays30 = intval(da_scalar($connect, "SELECT COUNT(DISTINCT DATE(FROM_UNIXTIME(s.created))) FROM starfree_space s"
        . $ownerJoin . " WHERE $ownerCondition AND s.status=1 AND s.created>=$day30"));
    $pollVotes = $pollReady ? intval(da_scalar($connect, "SELECT COUNT(DISTINCT poll_id) FROM starfree_space_poll_votes WHERE uid=$uid")) : null;
    $reportsFiled = $reportsReady ? intval(da_scalar($connect, "SELECT COUNT(*) FROM starfree_space_reports WHERE reporter_uid=$uid")) : null;
    $reportsReceived = $reportsReady ? intval(da_scalar($connect, "SELECT COUNT(*) FROM starfree_space_reports r JOIN starfree_space s ON s.id=r.space_id "
        . ($anonymousReady ? "LEFT JOIN starfree_anonymous_posts a ON a.sid=s.id " : '')
        . "WHERE " . dua_space_owner_condition($anonymousReady, 's', 'a', $uid))) : null;
    $aiSummary = $aiReady ? da_row($connect, "SELECT COUNT(*) reviewed,SUM(status='pending') pending,SUM(status='approved') approved FROM starfree_space_ai_reviews WHERE author_uid=$uid") : array();
    $firstPost = intval(isset($summary['first_post']) ? $summary['first_post'] : 0);
    $firstPostDelay = $firstPost > 0 ? max(0, $firstPost - intval($user['created'])) : null;
    $receivedAverage = $coverageAll['posts'] > 0 ? round($coverageAll['receivedReplies'] / $coverageAll['posts'], 2) : null;

    $daily = da_rows($connect, "SELECT DATE(FROM_UNIXTIME(s.created)) d,"
        . "SUM(CASE WHEN s.type<>3 AND s.status=1 AND s.onlyMe=0 THEN 1 ELSE 0 END) posts,"
        . "SUM(CASE WHEN s.type=3 AND s.status=1 THEN 1 ELSE 0 END) replies "
        . "FROM starfree_space s" . $ownerJoin . " WHERE $ownerCondition AND s.created>=$day30 GROUP BY d");
    $dailyPosts = da_map($daily, 'd', 'posts');
    $dailyReplies = da_map($daily, 'd', 'replies');
    $labels = array(); $postTrend = array(); $replyTrend = array();
    for ($i=29; $i>=0; $i--) {
        $key = date('Y-m-d', $today - $i * 86400);
        $labels[] = date('m-d', $today - $i * 86400);
        $postTrend[] = isset($dailyPosts[$key]) ? $dailyPosts[$key] : 0;
        $replyTrend[] = isset($dailyReplies[$key]) ? $dailyReplies[$key] : 0;
    }

    $recentPosts = da_rows($connect, "SELECT s.id,s.text,s.created,s.status,s.onlyMe,s.likes,s.views,"
        . ($anonymousReady ? "CASE WHEN a.uid IS NULL THEN 0 ELSE 1 END" : "0") . " is_anonymous,"
        . "(SELECT COUNT(*) FROM starfree_space r WHERE r.type=3 AND r.status=1 AND r.toid=s.id) reply_count "
        . "FROM starfree_space s" . $ownerJoin . " WHERE $ownerCondition AND s.type<>3 ORDER BY s.created DESC LIMIT 12");
    $recentReplies = da_rows($connect, "SELECT r.id,r.text,r.created,r.status,r.toid,p.text parent_text,p.type parent_type,"
        . "COALESCE(NULLIF(u.screenName,''),u.name) parent_author FROM starfree_space r "
        . "LEFT JOIN starfree_space p ON p.id=r.toid LEFT JOIN starfree_users u ON u.uid=p.uid "
        . "WHERE r.uid=$uid AND r.type=3 ORDER BY r.created DESC LIMIT 12");
?>
<style>.du-metric{min-height:126px}.du-value{font-size:27px;font-weight:700;color:#263b36}.du-note{font-size:12px;color:#7d8985}.du-chart{min-height:320px}.du-table td,.du-table th{vertical-align:middle}.du-wrap{max-width:360px;white-space:normal}.du-badge{font-size:11px}</style>
<div class="d-flex justify-content-between align-items-center mb-3"><div><a href="<?php echo $ADMIN_PATH;?>/dynamicUserAnalytics.php">&larr; 返回用户列表</a><h3 class="mt-2 mb-1"><?php echo da_h($user['screenName']?:$user['name']);?></h3><p class="text-muted mb-0">UID <?php echo $uid;?> · 注册于 <?php echo da_time($user['created']);?><?php if($user['campus_name']) echo ' · '.da_h($user['campus_name']);?><?php if($user['grade_name']) echo ' · '.da_h($user['grade_name']);?></p></div><a class="btn btn-outline-primary" href="<?php echo $ADMIN_PATH;?>/dynamicAnalytics.php">论坛总览</a></div>
<?php if(trim((string)$user['introduce'])!==''){?><div class="alert alert-light border">个人简介：<?php echo da_h($user['introduce']);?></div><?php }?>
<div class="row">
<?php
$cards = array(
 array('公开动态',da_number($coverageAll['posts']),'近30天 '.da_number($coverage30['posts'])),
 array('主动回复',da_number(isset($summary['replies'])?$summary['replies']:0),'近30天活跃 '.$activeDays30.' 天'),
 array('动态回复率',da_percent_text($coverageAll['rate']),'有回复 '.$coverageAll['repliedPosts'].'/'.$coverageAll['posts'].' 条'),
 array('收到直接回复',da_number($coverageAll['receivedReplies']),'平均每条 '.($receivedAverage===null?'--':number_format($receivedAverage,2))),
 array('获赞',da_number(isset($summary['likes'])?$summary['likes']:0),'公开主动态累计'),
 array('浏览',da_number(isset($summary['views'])?$summary['views']:0),'公开主动态累计'),
 array('首次发帖耗时',$firstPostDelay===null?'尚未发帖':($firstPostDelay<86400?round($firstPostDelay/3600,1).' 小时':round($firstPostDelay/86400,1).' 天'),'首次发布 '.da_time($firstPost)),
 array('最近活跃',da_time(isset($summary['last_activity'])?$summary['last_activity']:0),'仅动态与回复行为')
);
foreach($cards as $card){?><div class="col-xl-3 col-md-6"><div class="card du-metric"><div class="card-body"><div class="du-note"><?php echo da_h($card[0]);?></div><div class="du-value mt-1"><?php echo da_h($card[1]);?></div><div class="du-note mt-2"><?php echo da_h($card[2]);?></div></div></div></div><?php }?>
</div>
<div class="row"><div class="col-xl-8"><div class="card"><div class="card-body"><h4 class="header-title">近30天发布与回复</h4><div id="user-activity" class="du-chart"></div></div></div></div><div class="col-xl-4"><div class="card"><div class="card-body"><h4 class="header-title">运营状态</h4><table class="table du-table mb-0"><tbody><tr><td>待审核动态</td><td class="text-right"><?php echo da_number(isset($summary['pending_posts'])?$summary['pending_posts']:0);?></td></tr><tr><td>私密动态</td><td class="text-right"><?php echo da_number(isset($summary['private_posts'])?$summary['private_posts']:0);?></td></tr><tr><td>参与投票</td><td class="text-right"><?php echo $pollVotes===null?'--':da_number($pollVotes);?></td></tr><tr><td>提交举报</td><td class="text-right"><?php echo $reportsFiled===null?'--':da_number($reportsFiled);?></td></tr><tr><td>收到举报</td><td class="text-right"><?php echo $reportsReceived===null?'--':da_number($reportsReceived);?></td></tr><tr><td>AI审核 / 待人工</td><td class="text-right"><?php echo $aiReady?da_number(isset($aiSummary['reviewed'])?$aiSummary['reviewed']:0).' / '.da_number(isset($aiSummary['pending'])?$aiSummary['pending']:0):'--';?></td></tr></tbody></table></div></div></div></div>

<div class="row"><div class="col-xl-7"><div class="card"><div class="card-body"><h4 class="header-title">最近动态</h4><div class="table-responsive"><table class="table du-table"><thead><tr><th>内容</th><th>状态</th><th>回复</th><th>赞 / 浏览</th><th>时间</th></tr></thead><tbody><?php foreach($recentPosts as $post){?><tr><td class="du-wrap"><a href="https://prev.lcxqy.cn/#/pages/space/info?id=<?php echo intval($post['id']);?>" target="_blank"><?php echo da_h(da_excerpt($post['text'],55)?:('[动态 '.$post['id'].']'));?></a><?php if(intval($post['is_anonymous'])===1){?> <span class="badge badge-secondary du-badge">匿名发布</span><?php }?></td><td><?php echo intval($post['status'])===1?(intval($post['onlyMe'])===1?'私密':'公开'):'待审核';?></td><td><?php echo da_number($post['reply_count']);?></td><td><?php echo da_number($post['likes']).' / '.da_number($post['views']);?></td><td><?php echo date('m-d H:i',intval($post['created']));?></td></tr><?php }?><?php if(count($recentPosts)===0){?><tr><td colspan="5" class="text-muted">暂无动态</td></tr><?php }?></tbody></table></div></div></div></div><div class="col-xl-5"><div class="card"><div class="card-body"><h4 class="header-title">最近主动回复</h4><div class="table-responsive"><table class="table du-table"><thead><tr><th>回复内容</th><th>回复目标</th><th>时间</th></tr></thead><tbody><?php foreach($recentReplies as $reply){?><tr><td class="du-wrap"><?php echo da_h(da_excerpt($reply['text'],42));?></td><td class="du-wrap"><?php if(intval($reply['parent_type'])!==3){?><a href="https://prev.lcxqy.cn/#/pages/space/info?id=<?php echo intval($reply['toid']);?>" target="_blank"><?php echo da_h(da_excerpt($reply['parent_text'],24)?:('动态 '.$reply['toid']));?></a><?php }else{?>评论 #<?php echo intval($reply['toid']);?><?php }?></td><td><?php echo date('m-d H:i',intval($reply['created']));?></td></tr><?php }?><?php if(count($recentReplies)===0){?><tr><td colspan="3" class="text-muted">暂无回复</td></tr><?php }?></tbody></table></div></div></div></div></div>
<div class="alert alert-light border">口径与隐私：动态回复率和收到回复统计公开主动态的直接公开回复；主动回复包含用户对动态或评论发出的全部公开回复。本页不展示邮箱、手机号、密码、登录 Token 或投票选项选择。匿名动态只在受保护的运营后台归属真实 UID。</div>
<script src="<?php echo $ADMIN_PATH;?>/assets/js/vendor/apexcharts.min.js"></script>
<script>document.addEventListener('DOMContentLoaded',function(){new ApexCharts(document.querySelector('#user-activity'),{chart:{type:'area',height:310,toolbar:{show:false}},series:[{name:'动态',data:<?php echo json_encode($postTrend);?>},{name:'回复',data:<?php echo json_encode($replyTrend);?>}],xaxis:{categories:<?php echo json_encode($labels);?>,tickAmount:7},colors:['#168573','#d28b3f'],stroke:{curve:'smooth',width:2},fill:{opacity:.13},dataLabels:{enabled:false}}).render();});</script>
<?php
} else {
    $search = isset($_GET['q']) ? trim((string)$_GET['q']) : '';
    $sort = isset($_GET['sort']) ? (string)$_GET['sort'] : 'activity';
    $allowedSort = array('activity','posts','replies','likes','views','newest');
    if (!in_array($sort, $allowedSort, true)) $sort = 'activity';
    $page = isset($_GET['page']) ? max(1, intval($_GET['page'])) : 1;
    $limit = 30; $offset = ($page - 1) * $limit;
    $where = '';
    if ($search !== '') {
        $safe = mysqli_real_escape_string($connect, $search);
        $where = ctype_digit($search)
            ? " WHERE u.uid=" . intval($search)
            : " WHERE u.name LIKE '%$safe%' OR u.screenName LIKE '%$safe%'";
    }
    $total = intval(da_scalar($connect, "SELECT COUNT(*) FROM starfree_users u$where"));
    $ownerExpression = $anonymousReady ? 'COALESCE(a.uid,s.uid)' : 's.uid';
    $spaceJoin = $anonymousReady ? ' LEFT JOIN starfree_anonymous_posts a ON a.sid=s.id' : '';
    $orderMap = array(
        'activity'=>'last_activity DESC,u.uid DESC','posts'=>'posts DESC,last_activity DESC',
        'replies'=>'replies DESC,last_activity DESC','likes'=>'likes DESC,last_activity DESC',
        'views'=>'views DESC,last_activity DESC','newest'=>'u.created DESC'
    );
    $users = da_rows($connect, "SELECT u.uid,u.name,u.screenName,u.created,"
        . "COALESCE(x.posts,0) posts,COALESCE(x.replies,0) replies,COALESCE(x.likes,0) likes,"
        . "COALESCE(x.views,0) views,COALESCE(x.last_activity,0) last_activity "
        . "FROM starfree_users u LEFT JOIN (SELECT $ownerExpression owner_uid,"
        . "SUM(CASE WHEN s.type<>3 AND s.status=1 AND s.onlyMe=0 THEN 1 ELSE 0 END) posts,"
        . "SUM(CASE WHEN s.type=3 AND s.status=1 THEN 1 ELSE 0 END) replies,"
        . "COALESCE(SUM(CASE WHEN s.type<>3 AND s.status=1 AND s.onlyMe=0 THEN s.likes ELSE 0 END),0) likes,"
        . "COALESCE(SUM(CASE WHEN s.type<>3 AND s.status=1 AND s.onlyMe=0 THEN s.views ELSE 0 END),0) views,"
        . "MAX(s.created) last_activity FROM starfree_space s$spaceJoin GROUP BY owner_uid) x ON x.owner_uid=u.uid"
        . "$where ORDER BY " . $orderMap[$sort] . " LIMIT $offset,$limit");
    $pages = max(1, intval(ceil($total / $limit)));
?>
<style>.du-table td,.du-table th{vertical-align:middle}.du-search{display:flex;gap:8px;flex-wrap:wrap}.du-search .form-control{max-width:340px}.du-note{font-size:12px;color:#7d8985}.du-number{font-weight:600;color:#263b36}</style>
<div class="d-flex justify-content-between align-items-center mb-3"><div><h3 class="mb-1">用户论坛数据</h3><p class="text-muted mb-0">逐个查看注册、动态、回复和互动表现，不展示账号敏感信息。</p></div><a class="btn btn-outline-primary" href="<?php echo $ADMIN_PATH;?>/dynamicAnalytics.php">论坛总览</a></div>
<div class="card"><div class="card-body"><form class="du-search" method="get"><input class="form-control" name="q" value="<?php echo da_h($search);?>" placeholder="搜索 UID、用户名或昵称"><select class="form-control" name="sort" style="max-width:180px"><option value="activity" <?php echo $sort==='activity'?'selected':'';?>>最近活跃</option><option value="posts" <?php echo $sort==='posts'?'selected':'';?>>动态最多</option><option value="replies" <?php echo $sort==='replies'?'selected':'';?>>回复最多</option><option value="likes" <?php echo $sort==='likes'?'selected':'';?>>获赞最多</option><option value="views" <?php echo $sort==='views'?'selected':'';?>>浏览最多</option><option value="newest" <?php echo $sort==='newest'?'selected':'';?>>最近注册</option></select><button class="btn btn-primary" type="submit">查询</button><?php if($search!==''){?><a class="btn btn-light" href="<?php echo $ADMIN_PATH;?>/dynamicUserAnalytics.php">清除</a><?php }?></form></div></div>
<div class="card"><div class="card-body"><div class="d-flex justify-content-between mb-2"><h4 class="header-title">用户列表</h4><span class="text-muted">共 <?php echo da_number($total);?> 人</span></div><div class="table-responsive"><table class="table du-table"><thead><tr><th>用户</th><th>注册时间</th><th>公开动态</th><th>主动回复</th><th>获赞</th><th>浏览</th><th>最近活跃</th><th></th></tr></thead><tbody><?php foreach($users as $item){?><tr><td><span class="du-number"><?php echo da_h($item['screenName']?:$item['name']);?></span><div class="du-note"><?php echo da_h($item['name']);?> · UID <?php echo intval($item['uid']);?></div></td><td><?php echo da_time($item['created']);?></td><td><?php echo da_number($item['posts']);?></td><td><?php echo da_number($item['replies']);?></td><td><?php echo da_number($item['likes']);?></td><td><?php echo da_number($item['views']);?></td><td><?php echo da_time($item['last_activity']);?></td><td><a class="btn btn-sm btn-outline-primary" href="<?php echo $ADMIN_PATH;?>/dynamicUserAnalytics.php?uid=<?php echo intval($item['uid']);?>">详情</a></td></tr><?php }?><?php if(count($users)===0){?><tr><td colspan="8" class="text-muted text-center">没有匹配的用户</td></tr><?php }?></tbody></table></div>
<?php if($pages>1){?><nav><ul class="pagination mb-0"><?php if($page>1){?><li class="page-item"><a class="page-link" href="?q=<?php echo urlencode($search);?>&sort=<?php echo urlencode($sort);?>&page=<?php echo $page-1;?>">上一页</a></li><?php }?><li class="page-item disabled"><span class="page-link"><?php echo $page;?> / <?php echo $pages;?></span></li><?php if($page<$pages){?><li class="page-item"><a class="page-link" href="?q=<?php echo urlencode($search);?>&sort=<?php echo urlencode($sort);?>&page=<?php echo $page+1;?>">下一页</a></li><?php }?></ul></nav><?php }?>
</div></div>
<?php
}
include_once 'Footer.php';
?>
