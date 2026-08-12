<?php
session_start();
include_once 'Menu.php';
function da_scalar($connect,$sql){$r=mysqli_query($connect,$sql);if(!$r)return 0;$row=mysqli_fetch_row($r);return intval($row[0]);}
function da_rows($connect,$sql){$out=array();$r=mysqli_query($connect,$sql);if($r)while($row=mysqli_fetch_assoc($r))$out[]=$row;return $out;}
function da_map($rows,$key,$value){$out=array();foreach($rows as $row)$out[$row[$key]]=intval($row[$value]);return $out;}
function da_table_exists($connect,$table){$safe=mysqli_real_escape_string($connect,$table);$r=mysqli_query($connect,"SHOW TABLES LIKE '$safe'");return $r&&mysqli_num_rows($r)>0;}
$now=time(); $today=strtotime('today'); $day7=$today-6*86400; $day30=$today-29*86400; $month=$today-29*86400;
$pollTablesReady=da_table_exists($connect,'starfree_space_polls');
$aiTablesReady=da_table_exists($connect,'starfree_space_ai_reviews');
$metrics=array(
 'published'=>da_scalar($connect,"SELECT COUNT(*) FROM starfree_space WHERE status=1 AND type<>3"),
 'today'=>da_scalar($connect,"SELECT COUNT(*) FROM starfree_space WHERE status=1 AND type<>3 AND created>=$today"),
 'comments30'=>da_scalar($connect,"SELECT COUNT(*) FROM starfree_space WHERE type=3 AND status=1 AND created>=$day30"),
 'polls'=>$pollTablesReady?da_scalar($connect,"SELECT COUNT(*) FROM starfree_space_polls"):null,
 'participants'=>$pollTablesReady?da_scalar($connect,"SELECT COALESCE(SUM(total_votes),0) FROM starfree_space_polls"):null,
 'pending'=>$aiTablesReady?da_scalar($connect,"SELECT COUNT(*) FROM starfree_space_ai_reviews WHERE status='pending'"):null
);
$dau=da_scalar($connect,"SELECT COUNT(DISTINCT COALESCE(a.uid,s.uid)) FROM starfree_space s LEFT JOIN starfree_anonymous_posts a ON a.sid=s.id WHERE s.created>=$today");
$wau=da_scalar($connect,"SELECT COUNT(DISTINCT COALESCE(a.uid,s.uid)) FROM starfree_space s LEFT JOIN starfree_anonymous_posts a ON a.sid=s.id WHERE s.created>=$day7");
$mau=da_scalar($connect,"SELECT COUNT(DISTINCT COALESCE(a.uid,s.uid)) FROM starfree_space s LEFT JOIN starfree_anonymous_posts a ON a.sid=s.id WHERE s.created>=$month");
$postMap=da_map(da_rows($connect,"SELECT DATE(FROM_UNIXTIME(created)) d,COUNT(*) c FROM starfree_space WHERE type<>3 AND created>=$day30 GROUP BY d"),'d','c');
$replyMap=da_map(da_rows($connect,"SELECT DATE(FROM_UNIXTIME(created)) d,COUNT(*) c FROM starfree_space WHERE type=3 AND created>=$day30 GROUP BY d"),'d','c');
$labels=array();$postTrend=array();$replyTrend=array();for($i=29;$i>=0;$i--){$key=date('Y-m-d',$today-$i*86400);$labels[]=date('m-d',$today-$i*86400);$postTrend[]=isset($postMap[$key])?$postMap[$key]:0;$replyTrend[]=isset($replyMap[$key])?$replyMap[$key]:0;}
$hourMap=da_map(da_rows($connect,"SELECT HOUR(FROM_UNIXTIME(created)) h,COUNT(*) c FROM starfree_space WHERE created>=$day30 GROUP BY h"),'h','c');$hours=array();for($h=0;$h<24;$h++)$hours[]=isset($hourMap[(string)$h])?$hourMap[(string)$h]:0;
$types=da_map(da_rows($connect,"SELECT type,COUNT(*) c FROM starfree_space WHERE status=1 GROUP BY type"),'type','c');
$moderation=$aiTablesReady?da_rows($connect,"SELECT status,COUNT(*) c FROM starfree_space_ai_reviews GROUP BY status"):array();
$authors=da_rows($connect,"SELECT s.uid,COALESCE(NULLIF(u.screenName,''),u.name) name,COUNT(*) posts,SUM(s.likes) likes,SUM(s.views) views FROM starfree_space s LEFT JOIN starfree_users u ON u.uid=s.uid WHERE s.status=1 AND s.type<>3 AND NOT EXISTS(SELECT 1 FROM starfree_anonymous_posts a WHERE a.sid=s.id) GROUP BY s.uid,name ORDER BY posts DESC LIMIT 10");
$popularPolls=$pollTablesReady?da_rows($connect,"SELECT p.title,p.total_votes,s.id space_id FROM starfree_space_polls p JOIN starfree_space s ON s.id=p.space_id ORDER BY p.total_votes DESC LIMIT 8"):array();
?>
<style>.da-metric{min-height:124px}.da-value{font-size:30px;font-weight:700;color:#263b36}.da-note{font-size:12px;color:#88938f}.da-chart{min-height:320px}.da-hour{display:grid;grid-template-columns:repeat(12,1fr);gap:5px}.da-hour-cell{height:46px;border-radius:3px;display:flex;align-items:center;justify-content:center;font-size:10px;color:#263b36}.da-table td,.da-table th{vertical-align:middle}</style>
<div class="row">
<?php foreach(array('公开动态'=>$metrics['published'],'今日发布'=>$metrics['today'],'30天评论'=>$metrics['comments30'],'投票参与人次'=>$metrics['participants'],'AI待人工'=>$metrics['pending']) as $label=>$value){?><div class="col-xl col-md-4"><div class="card da-metric"><div class="card-body"><div class="da-note"><?php echo $label;?></div><div class="da-value"><?php echo $value===null?'--':number_format($value);?></div></div></div></div><?php }?>
</div>
<?php if(!$pollTablesReady||!$aiTablesReady){?><div class="alert alert-warning">数据库迁移 010 尚未完整执行，投票或 AI 审核指标暂不可用；基础动态趋势仍可正常查看。</div><?php }?>
<div class="row"><div class="col-xl-8"><div class="card"><div class="card-body"><h4 class="header-title">动态与评论趋势（30天）</h4><div id="dynamic-trend" class="da-chart"></div></div></div></div><div class="col-xl-4"><div class="card"><div class="card-body"><h4 class="header-title">活跃用户</h4><div id="active-users" class="da-chart"></div><p class="da-note">按发生过动态或评论行为的去重用户计算。DAU/WAU/MAU 分别为今天、近7天、近30天。</p></div></div></div></div>
<div class="row"><div class="col-xl-7"><div class="card"><div class="card-body"><h4 class="header-title">用户活跃时间（近30天）</h4><div class="da-hour"><?php $maxHour=max(array_merge(array(1),$hours));foreach($hours as $h=>$count){$alpha=.1+.8*$count/$maxHour;?><div class="da-hour-cell" title="<?php echo $h;?>时：<?php echo $count;?>次" style="background:rgba(22,133,115,<?php echo number_format($alpha,2);?>)"><?php echo $h;?></div><?php }?></div><p class="da-note mt-2">颜色越深代表该小时发布动态或评论越多。</p></div></div></div><div class="col-xl-5"><div class="card"><div class="card-body"><h4 class="header-title">内容与互动构成</h4><div id="content-mix" class="da-chart"></div></div></div></div></div>
<div class="row"><div class="col-xl-7"><div class="card"><div class="card-body"><h4 class="header-title">动态作者</h4><div class="table-responsive"><table class="table da-table"><thead><tr><th>作者</th><th>动态</th><th>获赞</th><th>浏览</th></tr></thead><tbody><?php foreach($authors as $a){?><tr><td><?php echo htmlspecialchars($a['name']?:('UID '.$a['uid']),ENT_QUOTES,'UTF-8');?></td><td><?php echo intval($a['posts']);?></td><td><?php echo intval($a['likes']);?></td><td><?php echo intval($a['views']);?></td></tr><?php }?></tbody></table></div></div></div></div><div class="col-xl-5"><div class="card"><div class="card-body"><h4 class="header-title">热门投票</h4><div class="table-responsive"><table class="table da-table"><thead><tr><th>投票</th><th>参与</th></tr></thead><tbody><?php foreach($popularPolls as $p){?><tr><td><?php echo htmlspecialchars($p['title'],ENT_QUOTES,'UTF-8');?></td><td><?php echo intval($p['total_votes']);?></td></tr><?php }?><?php if(!$pollTablesReady){?><tr><td colspan="2" class="text-muted">迁移 010 尚未执行</td></tr><?php }?></tbody></table></div><p class="da-note">投票仅统计参与人数，不展示参与者身份。</p></div></div></div></div>
<div class="alert alert-light border">留存率暂不展示：当前历史登录/访问事件不足以严谨推导用户留存。新迁移已预留隐私最小化事件表，积累完整事件后再启用。</div>
<script src="<?php echo $ADMIN_PATH;?>/assets/js/vendor/apexcharts.min.js"></script>
<script>
document.addEventListener('DOMContentLoaded',function(){
new ApexCharts(document.querySelector('#dynamic-trend'),{chart:{type:'area',height:310,toolbar:{show:false}},series:[{name:'动态',data:<?php echo json_encode($postTrend);?>},{name:'评论',data:<?php echo json_encode($replyTrend);?>}],xaxis:{categories:<?php echo json_encode($labels);?>,tickAmount:7},colors:['#168573','#d28b3f'],stroke:{curve:'smooth',width:2},dataLabels:{enabled:false},fill:{opacity:.14}}).render();
new ApexCharts(document.querySelector('#active-users'),{chart:{type:'bar',height:300,toolbar:{show:false}},series:[{name:'用户',data:[<?php echo $dau.','.$wau.','.$mau;?>]}],xaxis:{categories:['DAU','WAU','MAU']},colors:['#168573'],dataLabels:{enabled:true}}).render();
new ApexCharts(document.querySelector('#content-mix'),{chart:{type:'bar',height:310,toolbar:{show:false}},series:[{name:'数量',data:<?php echo json_encode(array_merge(array(intval(isset($types['0'])?$types['0']:0),intval(isset($types['4'])?$types['4']:0),intval(isset($types['3'])?$types['3']:0)),$pollTablesReady?array(intval($metrics['polls'])):array()));?>}],xaxis:{categories:<?php echo json_encode(array_merge(array('普通动态','视频动态','评论'),$pollTablesReady?array('投票'):array()),JSON_UNESCAPED_UNICODE);?>},colors:['#4d7ea8'],dataLabels:{enabled:true}}).render();
});
</script>
<?php include_once 'Footer.php'; ?>
