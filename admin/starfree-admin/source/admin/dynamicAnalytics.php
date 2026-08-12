<?php
session_start();
include_once 'Menu.php';
include_once 'dynamicAnalyticsCommon.php';

$now = time();
$today = strtotime('today');
$day7 = $today - 6 * 86400;
$day30 = $today - 29 * 86400;
$cohortEnd = $today - 7 * 86400;
$cohortStart = $cohortEnd - 29 * 86400;
$anonymousReady = da_table_exists($connect, 'starfree_anonymous_posts');
$pollReady = da_table_exists($connect, 'starfree_space_polls');
$aiReady = da_table_exists($connect, 'starfree_space_ai_reviews');
$reportsReady = da_table_exists($connect, 'starfree_space_reports');

$allUsers = intval(da_scalar($connect, "SELECT COUNT(*) FROM starfree_users"));
$newUsers30 = intval(da_scalar($connect, "SELECT COUNT(*) FROM starfree_users WHERE created>=$day30"));
$publicPosts30 = intval(da_scalar($connect, "SELECT COUNT(*) FROM starfree_space WHERE type<>3 AND status=1 AND onlyMe=0 AND created>=$day30"));
$replies30 = intval(da_scalar($connect, "SELECT COUNT(*) FROM starfree_space WHERE type=3 AND status=1 AND created>=$day30"));
$posters30 = intval(da_scalar($connect, "SELECT COUNT(DISTINCT " . ($anonymousReady ? "COALESCE(a.uid,s.uid)" : "s.uid") . ") FROM starfree_space s " . ($anonymousReady ? "LEFT JOIN starfree_anonymous_posts a ON a.sid=s.id " : "") . "WHERE s.type<>3 AND s.status=1 AND s.onlyMe=0 AND s.created>=$day30"));
$repliers30 = intval(da_scalar($connect, "SELECT COUNT(DISTINCT s.uid) FROM starfree_space s WHERE s.type=3 AND s.status=1 AND s.created>=$day30"));
$active30 = intval(da_scalar($connect, "SELECT COUNT(DISTINCT " . ($anonymousReady ? "COALESCE(a.uid,s.uid)" : "s.uid") . ") FROM starfree_space s " . ($anonymousReady ? "LEFT JOIN starfree_anonymous_posts a ON a.sid=s.id " : "") . "WHERE s.status=1 AND s.created>=$day30"));

$replyCoverage30 = da_row($connect, "SELECT COUNT(*) posts,"
    . "SUM(CASE WHEN rc.reply_count>0 THEN 1 ELSE 0 END) replied_posts,"
    . "COALESCE(SUM(rc.reply_count),0) received_replies "
    . "FROM starfree_space p LEFT JOIN (SELECT toid,COUNT(*) reply_count FROM starfree_space "
    . "WHERE type=3 AND status=1 GROUP BY toid) rc ON rc.toid=p.id "
    . "WHERE p.type<>3 AND p.status=1 AND p.onlyMe=0 AND p.created>=$day30");
$repliedPosts30 = intval(isset($replyCoverage30['replied_posts']) ? $replyCoverage30['replied_posts'] : 0);
$replyRate30 = da_percent($repliedPosts30, intval(isset($replyCoverage30['posts']) ? $replyCoverage30['posts'] : 0));
$zeroReplyRate30 = $replyRate30 === null ? null : round(100 - $replyRate30, 1);
$receivedReplies30 = intval(isset($replyCoverage30['received_replies']) ? $replyCoverage30['received_replies'] : 0);
$averageReplies30 = $publicPosts30 > 0 ? round($receivedReplies30 / $publicPosts30, 2) : null;
$quality30 = da_row($connect, "SELECT COUNT(*) posts,COALESCE(SUM(likes),0) likes,"
    . "COALESCE(SUM(views),0) views,SUM(CASE WHEN likes>0 OR EXISTS(SELECT 1 FROM starfree_space r "
    . "WHERE r.type=3 AND r.status=1 AND r.toid=p.id) THEN 1 ELSE 0 END) engaged "
    . "FROM starfree_space p WHERE p.type<>3 AND p.status=1 AND p.onlyMe=0 AND p.created>=$day30");
$engagedPosts30 = intval(isset($quality30['engaged']) ? $quality30['engaged'] : 0);
$engagementRate30 = da_percent($engagedPosts30, $publicPosts30);
$averageLikes30 = $publicPosts30 > 0 ? round(intval($quality30['likes']) / $publicPosts30, 2) : null;
$averageViews30 = $publicPosts30 > 0 ? round(intval($quality30['views']) / $publicPosts30, 1) : null;

$cohortUsers = intval(da_scalar($connect, "SELECT COUNT(*) FROM starfree_users WHERE created>=$cohortStart AND created<$cohortEnd"));
$cohortPosters = intval(da_scalar($connect, "SELECT COUNT(*) FROM starfree_users u WHERE u.created>=$cohortStart AND u.created<$cohortEnd "
    . "AND EXISTS(SELECT 1 FROM starfree_space s "
    . ($anonymousReady ? "LEFT JOIN starfree_anonymous_posts a ON a.sid=s.id " : "")
    . "WHERE " . ($anonymousReady ? "COALESCE(a.uid,s.uid)" : "s.uid") . "=u.uid AND s.type<>3 AND s.status=1 AND s.onlyMe=0 "
    . "AND s.created>=u.created AND s.created<u.created+604800)"));
$registrationPostRate = da_percent($cohortPosters, $cohortUsers);
$everPosters = intval(da_scalar($connect, "SELECT COUNT(DISTINCT " . ($anonymousReady ? "COALESCE(a.uid,s.uid)" : "s.uid") . ") FROM starfree_space s " . ($anonymousReady ? "LEFT JOIN starfree_anonymous_posts a ON a.sid=s.id " : "") . "WHERE s.type<>3 AND s.status=1 AND s.onlyMe=0"));
$everReplyUsers = intval(da_scalar($connect, "SELECT COUNT(DISTINCT uid) FROM starfree_space WHERE type=3 AND status=1"));

$pendingSpaces = intval(da_scalar($connect, "SELECT COUNT(*) FROM starfree_space WHERE type<>3 AND status=0"));
$reportedPending = $reportsReady ? intval(da_scalar($connect, "SELECT COUNT(*) FROM starfree_space_reports WHERE status=0")) : null;
$reportTotal = $reportsReady ? intval(da_scalar($connect, "SELECT COUNT(*) FROM starfree_space_reports")) : null;
$reportResolved = $reportsReady ? max(0, $reportTotal - $reportedPending) : null;
$reportResolutionRate = $reportsReady ? da_percent($reportResolved, $reportTotal) : null;
$aiPending = $aiReady ? intval(da_scalar($connect, "SELECT COUNT(*) FROM starfree_space_ai_reviews WHERE status='pending'")) : null;
$aiTotal = $aiReady ? intval(da_scalar($connect, "SELECT COUNT(*) FROM starfree_space_ai_reviews")) : null;
$aiApproved = $aiReady ? intval(da_scalar($connect, "SELECT COUNT(*) FROM starfree_space_ai_reviews WHERE status='approved'")) : null;
$aiApprovalRate = $aiReady ? da_percent($aiApproved, $aiTotal) : null;
$polls = $pollReady ? intval(da_scalar($connect, "SELECT COUNT(*) FROM starfree_space_polls")) : null;
$pollVotes = $pollReady ? intval(da_scalar($connect, "SELECT COALESCE(SUM(total_votes),0) FROM starfree_space_polls")) : null;

$dailyUsers = da_map(da_rows($connect, "SELECT DATE(FROM_UNIXTIME(created)) d,COUNT(*) c FROM starfree_users WHERE created>=$day30 GROUP BY d"), 'd', 'c');
$dailyPosts = da_map(da_rows($connect, "SELECT DATE(FROM_UNIXTIME(created)) d,COUNT(*) c FROM starfree_space WHERE type<>3 AND status=1 AND onlyMe=0 AND created>=$day30 GROUP BY d"), 'd', 'c');
$dailyReplies = da_map(da_rows($connect, "SELECT DATE(FROM_UNIXTIME(created)) d,COUNT(*) c FROM starfree_space WHERE type=3 AND status=1 AND created>=$day30 GROUP BY d"), 'd', 'c');
$dailyReplyCoverage = da_rows($connect, "SELECT DATE(FROM_UNIXTIME(p.created)) d,COUNT(*) posts,"
    . "SUM(CASE WHEN EXISTS(SELECT 1 FROM starfree_space r WHERE r.type=3 AND r.status=1 AND r.toid=p.id) THEN 1 ELSE 0 END) replied "
    . "FROM starfree_space p WHERE p.type<>3 AND p.status=1 AND p.onlyMe=0 AND p.created>=$day30 GROUP BY d");
$dailyReplyRates = array();
foreach ($dailyReplyCoverage as $row) $dailyReplyRates[$row['d']] = da_percent($row['replied'], $row['posts']);
$labels = array(); $registrationTrend = array(); $postTrend = array(); $replyTrend = array(); $replyRateTrend = array();
for ($i=29; $i>=0; $i--) {
    $key = date('Y-m-d', $today - $i * 86400);
    $labels[] = date('m-d', $today - $i * 86400);
    $registrationTrend[] = isset($dailyUsers[$key]) ? $dailyUsers[$key] : 0;
    $postTrend[] = isset($dailyPosts[$key]) ? $dailyPosts[$key] : 0;
    $replyTrend[] = isset($dailyReplies[$key]) ? $dailyReplies[$key] : 0;
    $replyRateTrend[] = isset($dailyReplyRates[$key]) && $dailyReplyRates[$key] !== null ? $dailyReplyRates[$key] : 0;
}

$dau = intval(da_scalar($connect, "SELECT COUNT(DISTINCT " . ($anonymousReady ? "COALESCE(a.uid,s.uid)" : "s.uid") . ") FROM starfree_space s " . ($anonymousReady ? "LEFT JOIN starfree_anonymous_posts a ON a.sid=s.id " : "") . "WHERE s.status=1 AND s.created>=$today"));
$wau = intval(da_scalar($connect, "SELECT COUNT(DISTINCT " . ($anonymousReady ? "COALESCE(a.uid,s.uid)" : "s.uid") . ") FROM starfree_space s " . ($anonymousReady ? "LEFT JOIN starfree_anonymous_posts a ON a.sid=s.id " : "") . "WHERE s.status=1 AND s.created>=$day7"));
$hourMap = da_map(da_rows($connect, "SELECT HOUR(FROM_UNIXTIME(created)) h,COUNT(*) c FROM starfree_space WHERE status=1 AND created>=$day30 GROUP BY h"), 'h', 'c');
$hours = array(); for ($h=0; $h<24; $h++) $hours[] = isset($hourMap[(string)$h]) ? $hourMap[(string)$h] : 0;
$types = da_map(da_rows($connect, "SELECT type,COUNT(*) c FROM starfree_space WHERE status=1 GROUP BY type"), 'type', 'c');

$topOwnerExpression = $anonymousReady ? 'COALESCE(a.uid,s.uid)' : 's.uid';
$topOwnerJoin = $anonymousReady ? ' LEFT JOIN starfree_anonymous_posts a ON a.sid=s.id' : '';
$topUsers = da_rows($connect, "SELECT u.uid,u.name,u.screenName,u.created,"
    . "COALESCE(x.posts,0) posts,COALESCE(x.replies,0) replies,COALESCE(x.likes,0) likes,COALESCE(x.views,0) views "
    . "FROM starfree_users u JOIN (SELECT $topOwnerExpression owner_uid,"
    . "SUM(CASE WHEN s.type<>3 AND s.status=1 AND s.onlyMe=0 THEN 1 ELSE 0 END) posts,"
    . "SUM(CASE WHEN s.type=3 AND s.status=1 THEN 1 ELSE 0 END) replies,"
    . "COALESCE(SUM(CASE WHEN s.type<>3 AND s.status=1 AND s.onlyMe=0 THEN s.likes ELSE 0 END),0) likes,"
    . "COALESCE(SUM(CASE WHEN s.type<>3 AND s.status=1 AND s.onlyMe=0 THEN s.views ELSE 0 END),0) views "
    . "FROM starfree_space s$topOwnerJoin GROUP BY owner_uid) x ON x.owner_uid=u.uid "
    . "WHERE x.posts>0 OR x.replies>0 ORDER BY x.posts DESC,x.replies DESC LIMIT 12");
$recentUnanswered = da_rows($connect, "SELECT p.id,p.text,p.created,COALESCE(NULLIF(u.screenName,''),u.name) author,"
    . ($anonymousReady ? "CASE WHEN a.uid IS NULL THEN 0 ELSE 1 END" : "0") . " is_anonymous "
    . "FROM starfree_space p "
    . ($anonymousReady ? "LEFT JOIN starfree_anonymous_posts a ON a.sid=p.id " : '')
    . "LEFT JOIN starfree_users u ON u.uid=" . ($anonymousReady ? "COALESCE(a.uid,p.uid) " : "p.uid ")
    . "WHERE p.type<>3 AND p.status=1 AND p.onlyMe=0 AND p.created>=$day7 "
    . "AND NOT EXISTS(SELECT 1 FROM starfree_space r WHERE r.type=3 AND r.status=1 AND r.toid=p.id) "
    . "ORDER BY p.created DESC LIMIT 8");
?>
<style>
.da-metric{min-height:128px}.da-value{font-size:28px;font-weight:700;color:#263b36;line-height:1.25}.da-note{font-size:12px;color:#7d8985}.da-chart{min-height:320px}.da-hour{display:grid;grid-template-columns:repeat(12,1fr);gap:5px}.da-hour-cell{height:46px;border-radius:3px;display:flex;align-items:center;justify-content:center;font-size:10px;color:#263b36}.da-table td,.da-table th{vertical-align:middle}.da-rate{font-size:13px;color:#168573;font-weight:600}.da-nowrap{white-space:nowrap}.da-filter{display:flex;gap:8px;align-items:center;flex-wrap:wrap}.da-filter .form-control{max-width:320px}
</style>
<div class="d-flex justify-content-between align-items-center mb-3"><div><h3 class="mb-1">论坛动态数据</h3><p class="text-muted mb-0">注册、发布、回复、互动和审核使用同一套可追溯口径。</p></div><a class="btn btn-primary" href="<?php echo $ADMIN_PATH;?>/dynamicUserAnalytics.php">查看用户明细</a></div>

<div class="row">
<?php
$cards = array(
    array('30天新注册', da_number($newUsers30), '累计用户 '.da_number($allUsers)),
    array('7天注册发帖率', da_percent_text($registrationPostRate), '已观察满7天：'.$cohortPosters.'/'.$cohortUsers.' 人'),
    array('30天发帖用户', da_number($posters30), '发布 '.da_number($publicPosts30).' 条公开动态'),
    array('动态回复率', da_percent_text($replyRate30), '有回复 '.$repliedPosts30.'/'.$publicPosts30.' 条'),
    array('平均直接回复', $averageReplies30===null?'--':number_format($averageReplies30,2), '全部互动回复 '.da_number($replies30)),
    array('有互动动态', da_percent_text($engagementRate30), '获赞或收到直接回复'),
    array('平均获赞', $averageLikes30===null?'--':number_format($averageLikes30,2), '按30天公开动态'),
    array('平均浏览', $averageViews30===null?'--':number_format($averageViews30,1), '按30天公开动态'),
    array('30天活跃用户', da_number($active30), '发动态或回复去重用户'),
    array('待审核动态', da_number($pendingSpaces), 'AI待人工 '.($aiPending===null?'--':da_number($aiPending))),
    array('举报处理率', da_percent_text($reportResolutionRate), $reportsReady?'已处理 '.$reportResolved.'/'.$reportTotal:'举报表不可用'),
    array('AI自动通过率', da_percent_text($aiApprovalRate), $aiReady?'自动通过 '.$aiApproved.'/'.$aiTotal:'AI审核表不可用'),
    array('投票参与', $pollVotes===null?'--':da_number($pollVotes), '投票数 '.($polls===null?'--':da_number($polls)))
);
foreach($cards as $card){?>
<div class="col-xl-3 col-md-6"><div class="card da-metric"><div class="card-body"><div class="da-note"><?php echo da_h($card[0]);?></div><div class="da-value mt-1"><?php echo da_h($card[1]);?></div><div class="da-note mt-2"><?php echo da_h($card[2]);?></div></div></div></div>
<?php }?>
</div>

<div class="row"><div class="col-xl-8"><div class="card"><div class="card-body"><h4 class="header-title">注册、动态与回复趋势（30天）</h4><div id="growth-trend" class="da-chart"></div></div></div></div><div class="col-xl-4"><div class="card"><div class="card-body"><h4 class="header-title">用户转化漏斗</h4><div id="user-funnel" class="da-chart"></div><p class="da-note">累计发帖/回复用户按历史行为去重；注册后7天发帖率使用已观察满7天的新用户 cohort。</p></div></div></div></div>

<div class="row"><div class="col-xl-7"><div class="card"><div class="card-body"><h4 class="header-title">动态回复率趋势（按发布日期）</h4><div id="reply-rate-trend" class="da-chart"></div><p class="da-note">某日公开主动态中，当前至少有一条公开回复的动态占比。近期数据会随后续回复继续变化。</p></div></div></div><div class="col-xl-5"><div class="card"><div class="card-body"><h4 class="header-title">回复质量概览（30天）</h4><div id="reply-quality" class="da-chart"></div><div class="row text-center"><div class="col-4"><div class="da-value"><?php echo da_number($repliers30);?></div><div class="da-note">回复用户</div></div><div class="col-4"><div class="da-value"><?php echo da_percent_text($zeroReplyRate30);?></div><div class="da-note">零回复率</div></div><div class="col-4"><div class="da-value"><?php echo $reportedPending===null?'--':da_number($reportedPending);?></div><div class="da-note">待处理举报</div></div></div></div></div></div></div>

<div class="row"><div class="col-xl-7"><div class="card"><div class="card-body"><h4 class="header-title">用户活跃时间（近30天）</h4><div class="da-hour"><?php $maxHour=max(array_merge(array(1),$hours));foreach($hours as $h=>$count){$alpha=.1+.8*$count/$maxHour;?><div class="da-hour-cell" title="<?php echo $h;?>时：<?php echo $count;?>次" style="background:rgba(22,133,115,<?php echo number_format($alpha,2);?>)"><?php echo $h;?></div><?php }?></div><p class="da-note mt-2">颜色越深，表示该小时发生的公开动态或回复越多。</p></div></div></div><div class="col-xl-5"><div class="card"><div class="card-body"><h4 class="header-title">活跃用户与内容构成</h4><div id="active-content" class="da-chart"></div><p class="da-note">DAU <?php echo da_number($dau);?>，WAU <?php echo da_number($wau);?>，MAU <?php echo da_number($active30);?>。</p></div></div></div></div>

<div class="row"><div class="col-xl-7"><div class="card"><div class="card-body"><div class="d-flex justify-content-between align-items-center"><h4 class="header-title">用户贡献排行</h4><a href="<?php echo $ADMIN_PATH;?>/dynamicUserAnalytics.php">搜索全部用户</a></div><div class="table-responsive"><table class="table da-table"><thead><tr><th>用户</th><th>动态</th><th>回复</th><th>获赞</th><th>浏览</th><th></th></tr></thead><tbody><?php foreach($topUsers as $user){?><tr><td><?php echo da_h($user['screenName']?:$user['name']);?><div class="da-note">UID <?php echo intval($user['uid']);?></div></td><td><?php echo da_number($user['posts']);?></td><td><?php echo da_number($user['replies']);?></td><td><?php echo da_number($user['likes']);?></td><td><?php echo da_number($user['views']);?></td><td><a href="<?php echo $ADMIN_PATH;?>/dynamicUserAnalytics.php?uid=<?php echo intval($user['uid']);?>">详情</a></td></tr><?php }?></tbody></table></div></div></div></div><div class="col-xl-5"><div class="card"><div class="card-body"><h4 class="header-title">近7天尚无回复动态</h4><div class="table-responsive"><table class="table da-table"><thead><tr><th>动态</th><th>作者</th><th>发布</th></tr></thead><tbody><?php foreach($recentUnanswered as $post){?><tr><td title="<?php echo da_h($post['text']);?>"><?php echo da_h(da_excerpt($post['text'],24));?></td><td><?php echo da_h($post['author']?:'已注销用户');?><?php if(intval($post['is_anonymous'])===1){?> <span class="badge badge-secondary">匿名</span><?php }?></td><td class="da-nowrap"><?php echo date('m-d H:i',intval($post['created']));?></td></tr><?php }?><?php if(count($recentUnanswered)===0){?><tr><td colspan="3" class="text-muted">近7天公开动态均已有回复</td></tr><?php }?></tbody></table></div></div></div></div></div>

<div class="alert alert-light border">口径说明：动态回复率只计算公开主动态与公开回复；注册发帖率只看注册后7天内的主动态。历史登录/访问事件不足，因此不把“最后登录时间”推算成留存率。匿名动态仅在此受登录保护的后台按真实 UID 聚合。</div>
<script src="<?php echo $ADMIN_PATH;?>/assets/js/vendor/apexcharts.min.js"></script>
<script>
document.addEventListener('DOMContentLoaded',function(){
new ApexCharts(document.querySelector('#growth-trend'),{chart:{type:'area',height:310,toolbar:{show:false}},series:[{name:'注册',data:<?php echo json_encode($registrationTrend);?>},{name:'动态',data:<?php echo json_encode($postTrend);?>},{name:'回复',data:<?php echo json_encode($replyTrend);?>}],xaxis:{categories:<?php echo json_encode($labels);?>,tickAmount:7},colors:['#4d7ea8','#168573','#d28b3f'],stroke:{curve:'smooth',width:2},dataLabels:{enabled:false},fill:{opacity:.12}}).render();
new ApexCharts(document.querySelector('#user-funnel'),{chart:{type:'bar',height:310,toolbar:{show:false}},series:[{name:'用户',data:[<?php echo $allUsers.','.$everPosters.','.$everReplyUsers;?>]}],xaxis:{categories:['注册用户','发过动态','发过回复']},plotOptions:{bar:{horizontal:true,borderRadius:2}},colors:['#168573'],dataLabels:{enabled:true}}).render();
new ApexCharts(document.querySelector('#reply-rate-trend'),{chart:{type:'line',height:310,toolbar:{show:false}},series:[{name:'回复率',data:<?php echo json_encode($replyRateTrend);?>}],xaxis:{categories:<?php echo json_encode($labels);?>,tickAmount:7},yaxis:{min:0,max:100,labels:{formatter:function(v){return Math.round(v)+'%';}}},colors:['#d28b3f'],stroke:{curve:'smooth',width:3},dataLabels:{enabled:false},tooltip:{y:{formatter:function(v){return v.toFixed(1)+'%';}}}}).render();
new ApexCharts(document.querySelector('#reply-quality'),{chart:{type:'donut',height:255},series:[<?php echo $publicPosts30>0?$repliedPosts30.','.max(0,$publicPosts30-$repliedPosts30):'0,1';?>],labels:<?php echo $publicPosts30>0?"['已有回复','尚无回复']":"['暂无动态','']";?>,colors:['#168573','#d7ddda'],legend:{position:'bottom'},dataLabels:{enabled:<?php echo $publicPosts30>0?'true':'false';?>,formatter:function(v){return v.toFixed(1)+'%';}}}).render();
new ApexCharts(document.querySelector('#active-content'),{chart:{type:'bar',height:310,toolbar:{show:false}},series:[{name:'数量',data:[<?php echo $dau.','.$wau.','.$active30.','.intval(isset($types['0'])?$types['0']:0).','.intval(isset($types['4'])?$types['4']:0).','.intval(isset($types['3'])?$types['3']:0);?>]}],xaxis:{categories:['DAU','WAU','MAU','普通动态','视频动态','回复']},colors:['#4d7ea8'],dataLabels:{enabled:true}}).render();
});
</script>
<?php include_once 'Footer.php'; ?>
