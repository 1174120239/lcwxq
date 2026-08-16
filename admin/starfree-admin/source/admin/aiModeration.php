<?php
require_once __DIR__ . '/session.php';
include_once 'Menu.php';
if (empty($_SESSION['ai_moderation_csrf'])) {
    $_SESSION['ai_moderation_csrf'] = bin2hex(random_bytes(24));
}
function ai_h($value) { return htmlspecialchars((string)$value, ENT_QUOTES, 'UTF-8'); }
function ai_time($value) { return intval($value) > 0 ? date('Y-m-d H:i', intval($value)) : '-'; }
function ai_short($value, $length) {
    $text = trim((string)$value);
    return mb_strimwidth($text, 0, $length, '...', 'UTF-8');
}
function ai_type_label($value) {
    $labels = array('space'=>'动态','question'=>'提问','space_comment'=>'动态评论',
        'qa_answer'=>'问答回答','qa_comment'=>'问答评论');
    return isset($labels[$value]) ? $labels[$value] : $value;
}
function ai_decision_label($value) {
    $labels = array('approved'=>'通过','rejected'=>'拒绝','locked'=>'锁定','error'=>'异常','disabled'=>'未执行');
    return isset($labels[$value]) ? $labels[$value] : $value;
}

$config = array('enabled'=>0,'space_enabled'=>1,'question_enabled'=>1,'comment_enabled'=>0,
    'comment_review_time'=>'03:30','comment_action'=>'hide','provider'=>'deepseek',
    'api_url'=>'https://api.deepseek.com/chat/completions','api_key'=>'','model'=>'deepseek-chat',
    'custom_prompt'=>'','last_comment_review_date'=>'','last_comment_review_started'=>0,
    'last_comment_review_finished'=>0,'last_comment_review_error'=>'');
$globalAudit = 0;
$globalResult = mysqli_query($connect, "SELECT spaceAudit FROM starfree_apiconfig ORDER BY id LIMIT 1");
if ($globalResult && ($globalRow = mysqli_fetch_assoc($globalResult))) {
    $globalAudit = intval($globalRow['spaceAudit']);
}
$configResult = mysqli_query($connect, "SELECT enabled,space_enabled,question_enabled,comment_enabled,"
    ."comment_review_time,comment_action,provider,api_url,api_key,model,custom_prompt,"
    ."last_comment_review_date,last_comment_review_started,last_comment_review_finished,"
    ."last_comment_review_error FROM starfree_ai_moderation_config WHERE id=1 LIMIT 1");
if ($configResult && ($configRow = mysqli_fetch_assoc($configResult))) {
    $config = array_merge($config, $configRow);
}

$allowedTypes = array('','space','question','space_comment','qa_answer','qa_comment');
$allowedDecisions = array('','approved','rejected','error');
$filterType = isset($_GET['content_type']) && in_array($_GET['content_type'], $allowedTypes, true)
    ? $_GET['content_type'] : '';
$filterDecision = isset($_GET['decision']) && in_array($_GET['decision'], $allowedDecisions, true)
    ? $_GET['decision'] : '';
$recordPage = max(1, isset($_GET['page']) ? intval($_GET['page']) : 1);
$recordLimit = 30;
$recordOffset = ($recordPage - 1) * $recordLimit;
$where = " WHERE 1=1";
if ($filterType !== '') $where .= " AND content_type='".mysqli_real_escape_string($connect, $filterType)."'";
if ($filterDecision !== '') $where .= " AND ai_decision='".mysqli_real_escape_string($connect, $filterDecision)."'";
$recordTotal = 0;
$totalResult = mysqli_query($connect, "SELECT COUNT(*) AS total FROM starfree_ai_moderation_reviews".$where);
if ($totalResult && ($totalRow = mysqli_fetch_assoc($totalResult))) $recordTotal = intval($totalRow['total']);
$records = array();
$recordResult = mysqli_query($connect, "SELECT id,content_type,content_id,author_uid,review_source,"
    ."content_snapshot,attachment_summary,ai_decision,risk_category,reason,content_status,"
    ."human_decision,reviewer_uid,review_note,reviewed,created,modified "
    ."FROM starfree_ai_moderation_reviews".$where." ORDER BY created DESC,id DESC LIMIT "
    .intval($recordOffset).",".intval($recordLimit));
if ($recordResult) while ($row = mysqli_fetch_assoc($recordResult)) $records[] = $row;

$summaries = array();
$summaryResult = mysqli_query($connect, "SELECT review_date,range_start,range_end,scanned_count,"
    ."approved_count,risk_count,hidden_count,failed_count,space_comment_count,qa_answer_count,"
    ."qa_comment_count,category_summary,summary_text,last_error,modified "
    ."FROM starfree_ai_comment_daily_summaries ORDER BY review_date DESC LIMIT 31");
if ($summaryResult) while ($row = mysqli_fetch_assoc($summaryResult)) $summaries[] = $row;
$actions = array();
$actionResult = mysqli_query($connect, "SELECT id,review_id,content_type,content_id,operator_uid,"
    ."from_status,to_status,action,note,created FROM starfree_ai_moderation_actions "
    ."ORDER BY created DESC,id DESC LIMIT 50");
if ($actionResult) while ($row = mysqli_fetch_assoc($actionResult)) $actions[] = $row;
?>
<style>
.ai-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:12px}.ai-stat{border:1px solid #e7e9ee;padding:14px;background:#fff}.ai-stat strong{display:block;font-size:22px}.ai-record-text{max-width:360px;white-space:normal;line-height:1.5}.ai-reason{color:#b42318}.ai-ok{color:#067647}.ai-muted{color:#667085}.ai-config-disabled{opacity:.6;pointer-events:none}.ai-table td{vertical-align:top}@media(max-width:900px){.ai-grid{grid-template-columns:repeat(2,minmax(0,1fr))}}
</style>
<div class="row"><div class="col-lg-12">
<div class="card"><div class="card-body">
<div class="d-flex justify-content-between align-items-center mb-3"><div><h4 class="header-title mb-1">AI 风险审核</h4><div class="text-muted">动态、提问实时审核；动态评论、问答回答和问答评论按日巡检。图片和视频仅审核附带文字。</div></div><span class="badge badge-<?php echo $globalAudit===1?'success':'secondary';?> p-2">审核总开关：<?php echo $globalAudit===1?'已开启':'已关闭';?></span></div>
<?php if ($globalAudit !== 1): ?><div class="alert alert-warning">请先在“审核设置”开启“动态审核”。当前 AI 配置和历史不会被删除，但所有 AI 审核任务均停止。</div><?php endif; ?>
<form action="aiModerationPost.php" method="post">
<input type="hidden" name="csrf" value="<?php echo ai_h($_SESSION['ai_moderation_csrf']);?>"><input type="hidden" name="action" value="config">
<fieldset class="<?php echo $globalAudit===1?'':'ai-config-disabled';?>" <?php echo $globalAudit===1?'':'disabled';?>>
<div class="form-row">
<div class="form-group col-md-3"><label>AI 审核总开关</label><div><input type="checkbox" name="enabled" id="ai_enabled" value="1" data-switch="success" <?php echo intval($config['enabled'])===1?'checked':'';?>><label style="display:block" for="ai_enabled" data-on-label="开启" data-off-label="关闭"></label></div></div>
<div class="form-group col-md-3"><label>动态实时审核</label><div><input type="checkbox" name="space_enabled" id="space_enabled" value="1" data-switch="success" <?php echo intval($config['space_enabled'])===1?'checked':'';?>><label style="display:block" for="space_enabled" data-on-label="开启" data-off-label="关闭"></label></div></div>
<div class="form-group col-md-3"><label>提问实时审核</label><div><input type="checkbox" name="question_enabled" id="question_enabled" value="1" data-switch="success" <?php echo intval($config['question_enabled'])===1?'checked':'';?>><label style="display:block" for="question_enabled" data-on-label="开启" data-off-label="关闭"></label></div></div>
<div class="form-group col-md-3"><label>每日评论巡检</label><div><input type="checkbox" name="comment_enabled" id="comment_enabled" value="1" data-switch="success" <?php echo intval($config['comment_enabled'])===1?'checked':'';?>><label style="display:block" for="comment_enabled" data-on-label="开启" data-off-label="关闭"></label></div></div>
</div>
<div class="form-row"><div class="form-group col-md-4"><label>模型</label><input class="form-control" name="model" maxlength="80" required value="<?php echo ai_h($config['model']);?>"></div><div class="form-group col-md-4"><label>每日巡检时间（北京时间）</label><input class="form-control" type="time" name="comment_review_time" required value="<?php echo ai_h($config['comment_review_time']);?>"></div><div class="form-group col-md-4"><label>风险评论处理</label><select class="form-control" name="comment_action"><option value="hide" <?php echo $config['comment_action']==='hide'?'selected':'';?>>自动隐藏</option><option value="record" <?php echo $config['comment_action']==='record'?'selected':'';?>>只记录，不隐藏</option></select></div></div>
<div class="form-group"><label>API Key</label><input class="form-control" type="password" name="api_key" maxlength="512" autocomplete="new-password" placeholder="留空保持不变"><small class="form-text text-muted">当前：<?php echo $config['api_key']===''?'未配置':'已配置';?>。密钥不会回显，关闭配置也不会清空。</small></div>
<div class="form-group"><label>补充审核规则</label><textarea class="form-control" name="custom_prompt" maxlength="2000" rows="5" placeholder="只能补充更严格的校园社区规则"><?php echo ai_h($config['custom_prompt']);?></textarea></div>
<button class="btn btn-primary" type="submit">保存 AI 审核设置</button>
</fieldset></form>
<hr><div class="ai-grid"><div class="ai-stat"><span>最近巡检日期</span><strong><?php echo ai_h($config['last_comment_review_date']?:'-');?></strong></div><div class="ai-stat"><span>开始时间</span><strong style="font-size:16px"><?php echo ai_h(ai_time($config['last_comment_review_started']));?></strong></div><div class="ai-stat"><span>完成时间</span><strong style="font-size:16px"><?php echo ai_h(ai_time($config['last_comment_review_finished']));?></strong></div><div class="ai-stat"><span>最近任务状态</span><strong class="<?php echo $config['last_comment_review_error']===''?'ai-ok':'ai-reason';?>" style="font-size:16px"><?php echo $config['last_comment_review_error']===''?'正常':ai_h(ai_short($config['last_comment_review_error'],60));?></strong></div></div>
</div></div>

<div class="card"><div class="card-body"><h4 class="header-title mb-3">审核记录</h4>
<form method="get" class="form-inline mb-3"><select name="content_type" class="form-control mr-2"><option value="">全部类型</option><?php foreach(array('space','question','space_comment','qa_answer','qa_comment') as $type):?><option value="<?php echo ai_h($type);?>" <?php echo $filterType===$type?'selected':'';?>><?php echo ai_h(ai_type_label($type));?></option><?php endforeach;?></select><select name="decision" class="form-control mr-2"><option value="">全部结论</option><?php foreach(array('approved','rejected','error') as $decision):?><option value="<?php echo ai_h($decision);?>" <?php echo $filterDecision===$decision?'selected':'';?>><?php echo ai_h(ai_decision_label($decision));?></option><?php endforeach;?></select><button class="btn btn-secondary" type="submit">筛选</button></form>
<div class="table-responsive"><table class="table table-sm ai-table"><thead><tr><th>时间/类型</th><th>内容快照</th><th>AI 结论</th><th>当前状态</th><th>人工改判</th></tr></thead><tbody>
<?php if (empty($records)):?><tr><td colspan="5" class="text-center text-muted">暂无审核记录</td></tr><?php endif;?>
<?php foreach($records as $record):?><tr><td><?php echo ai_h(ai_time($record['created']));?><br><span class="badge badge-light"><?php echo ai_h(ai_type_label($record['content_type']));?> #<?php echo intval($record['content_id']);?></span><br><small>UID <?php echo intval($record['author_uid']);?> · <?php echo ai_h($record['review_source']);?></small></td><td class="ai-record-text"><?php echo nl2br(ai_h(ai_short($record['content_snapshot'],260)));?><?php if($record['attachment_summary']!==''):?><div class="ai-muted mt-1"><?php echo ai_h($record['attachment_summary']);?></div><?php endif;?></td><td><strong class="<?php echo $record['ai_decision']==='approved'?'ai-ok':'ai-reason';?>"><?php echo ai_h(ai_decision_label($record['ai_decision']));?></strong><br><?php echo ai_h($record['risk_category']);?><div class="ai-record-text"><?php echo ai_h($record['reason']);?></div></td><td><?php echo intval($record['content_status'])===1?'公开':(intval($record['content_status'])===2?'锁定':'隐藏');?><?php if($record['human_decision']!==''):?><br><small>人工：<?php echo ai_h(ai_decision_label($record['human_decision']));?><br><?php echo ai_h($record['review_note']);?></small><?php endif;?></td><td>
<form action="aiModerationPost.php" method="post"><input type="hidden" name="csrf" value="<?php echo ai_h($_SESSION['ai_moderation_csrf']);?>"><input type="hidden" name="action" value="status"><input type="hidden" name="review_id" value="<?php echo intval($record['id']);?>"><select class="form-control form-control-sm mb-1" name="target_status"><?php if($record['content_type']==='space'):?><option value="2">锁定</option><?php endif;?><option value="1">公开</option><option value="0">隐藏</option></select><input class="form-control form-control-sm mb-1" name="note" maxlength="1000" placeholder="改判说明"><button class="btn btn-sm btn-outline-primary" type="submit">保存状态</button></form>
</td></tr><?php endforeach;?></tbody></table></div>
<?php $pageCount=max(1,intval(ceil($recordTotal/$recordLimit))); if($pageCount>1):?><nav><ul class="pagination pagination-sm"><?php for($i=1;$i<=$pageCount;$i++):?><li class="page-item <?php echo $i===$recordPage?'active':'';?>"><a class="page-link" href="?content_type=<?php echo urlencode($filterType);?>&decision=<?php echo urlencode($filterDecision);?>&page=<?php echo $i;?>"><?php echo $i;?></a></li><?php endfor;?></ul></nav><?php endif;?>
</div></div>

<div class="card"><div class="card-body"><h4 class="header-title mb-3">人工改判记录</h4><div class="table-responsive"><table class="table table-sm"><thead><tr><th>时间</th><th>内容</th><th>操作人</th><th>状态变化</th><th>说明</th></tr></thead><tbody><?php if(empty($actions)):?><tr><td colspan="5" class="text-center text-muted">暂无人工改判记录</td></tr><?php endif;?><?php foreach($actions as $item):?><tr><td><?php echo ai_h(ai_time($item['created']));?></td><td><?php echo ai_h(ai_type_label($item['content_type']));?> #<?php echo intval($item['content_id']);?><br><small>审核记录 #<?php echo intval($item['review_id']);?></small></td><td><?php echo intval($item['operator_uid'])>0?'UID '.intval($item['operator_uid']):'PHP 后台管理员';?></td><td><?php echo intval($item['from_status']);?> → <?php echo intval($item['to_status']);?><br><?php echo ai_h(ai_decision_label($item['action']));?></td><td><?php echo ai_h($item['note']);?></td></tr><?php endforeach;?></tbody></table></div></div></div>

<div class="card"><div class="card-body"><h4 class="header-title mb-3">每日评论巡检总结</h4><div class="table-responsive"><table class="table table-sm"><thead><tr><th>日期</th><th>扫描范围</th><th>数量</th><th>总结</th><th>风险类别</th></tr></thead><tbody><?php if(empty($summaries)):?><tr><td colspan="5" class="text-center text-muted">暂无每日总结</td></tr><?php endif;?><?php foreach($summaries as $summary):?><tr><td><?php echo ai_h($summary['review_date']);?></td><td><?php echo ai_h(ai_time($summary['range_start']));?><br>至 <?php echo ai_h(ai_time($summary['range_end']));?></td><td>扫描 <?php echo intval($summary['scanned_count']);?><br><span class="ai-ok">正常 <?php echo intval($summary['approved_count']);?></span> / <span class="ai-reason">风险 <?php echo intval($summary['risk_count']);?></span><br>隐藏 <?php echo intval($summary['hidden_count']);?> / 失败 <?php echo intval($summary['failed_count']);?></td><td class="ai-record-text"><?php echo ai_h($summary['summary_text']);?><?php if($summary['last_error']!==''):?><div class="ai-reason"><?php echo ai_h($summary['last_error']);?></div><?php endif;?></td><td class="ai-record-text"><?php echo ai_h($summary['category_summary']);?></td></tr><?php endforeach;?></tbody></table></div></div></div>
</div></div>
<?php include_once 'Footer.php'; ?>
