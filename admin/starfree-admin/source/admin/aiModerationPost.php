<?php
require_once __DIR__ . '/session.php';
if ($_SERVER['REQUEST_METHOD'] !== 'POST' || empty($_SESSION['loginadmin'])) {
    header('Location: login.php'); exit;
}
if (empty($_SESSION['ai_moderation_csrf']) || empty($_POST['csrf'])
    || !hash_equals($_SESSION['ai_moderation_csrf'], (string)$_POST['csrf'])) {
    echo "<script>alert('页面已过期，请刷新后重试');history.back();</script>"; exit;
}
include_once 'connect.php';
mysqli_set_charset($connect, 'utf8mb4');
function ai_post_fail($message) {
    echo "<script>alert(".json_encode($message, JSON_UNESCAPED_UNICODE).");history.back();</script>"; exit;
}
function ai_post_ok($message) {
    echo "<script>alert(".json_encode($message, JSON_UNESCAPED_UNICODE).");location.href='aiModeration.php';</script>"; exit;
}

$action = isset($_POST['action']) ? (string)$_POST['action'] : 'config';
if ($action === 'config') {
    $globalAudit = 0;
    $globalResult = mysqli_query($connect, "SELECT spaceAudit FROM starfree_apiconfig ORDER BY id LIMIT 1");
    if ($globalResult && ($globalRow = mysqli_fetch_assoc($globalResult))) $globalAudit = intval($globalRow['spaceAudit']);
    if ($globalAudit !== 1) ai_post_fail('请先在审核设置中开启动态审核');

    $enabled = isset($_POST['enabled']) && $_POST['enabled']==='1' ? 1 : 0;
    $spaceEnabled = isset($_POST['space_enabled']) && $_POST['space_enabled']==='1' ? 1 : 0;
    $questionEnabled = isset($_POST['question_enabled']) && $_POST['question_enabled']==='1' ? 1 : 0;
    $commentEnabled = isset($_POST['comment_enabled']) && $_POST['comment_enabled']==='1' ? 1 : 0;
    $reviewTime = trim(isset($_POST['comment_review_time']) ? $_POST['comment_review_time'] : '03:30');
    $commentAction = isset($_POST['comment_action']) && $_POST['comment_action']==='record' ? 'record' : 'hide';
    $model = trim(isset($_POST['model']) ? $_POST['model'] : 'deepseek-chat');
    $apiUrl = 'https://api.deepseek.com/chat/completions';
    $apiKey = trim(isset($_POST['api_key']) ? $_POST['api_key'] : '');
    $prompt = trim(isset($_POST['custom_prompt']) ? $_POST['custom_prompt'] : '');
    if (!preg_match('/^(?:[01]\d|2[0-3]):[0-5]\d$/', $reviewTime)
        || $model==='' || strlen($model)>80 || strlen($apiKey)>512
        || mb_strlen($prompt,'UTF-8')>2000) ai_post_fail('配置格式不正确');
    $currentKey = '';
    $result = mysqli_query($connect, "SELECT api_key FROM starfree_ai_moderation_config WHERE id=1 LIMIT 1");
    if ($result && ($row=mysqli_fetch_assoc($result))) $currentKey=$row['api_key'];
    if ($apiKey==='') $apiKey=$currentKey;
    if ($enabled===1 && $apiKey==='') ai_post_fail('开启 AI 审核前必须配置 API Key');
    $stmt=$connect->prepare("INSERT INTO starfree_ai_moderation_config"
        ."(id,enabled,space_enabled,question_enabled,comment_enabled,comment_review_time,comment_action,"
        ."provider,api_url,api_key,model,custom_prompt,modified_by,modified) "
        ."VALUES(1,?,?,?,?,?,?,'deepseek',?,?,?,?,0,?) ON DUPLICATE KEY UPDATE "
        ."enabled=VALUES(enabled),space_enabled=VALUES(space_enabled),question_enabled=VALUES(question_enabled),"
        ."comment_enabled=VALUES(comment_enabled),comment_review_time=VALUES(comment_review_time),"
        ."comment_action=VALUES(comment_action),provider=VALUES(provider),api_url=VALUES(api_url),"
        ."api_key=VALUES(api_key),model=VALUES(model),custom_prompt=VALUES(custom_prompt),modified=VALUES(modified)");
    if (!$stmt) ai_post_fail('保存失败');
    $now=time();
    $stmt->bind_param('iiiissssssi',$enabled,$spaceEnabled,$questionEnabled,$commentEnabled,
        $reviewTime,$commentAction,$apiUrl,$apiKey,$model,$prompt,$now);
    if (!$stmt->execute()) ai_post_fail('保存失败');
    ai_post_ok('AI 审核设置已保存');
}

if ($action === 'status') {
    $reviewId = isset($_POST['review_id']) ? intval($_POST['review_id']) : 0;
    $targetStatus = isset($_POST['target_status']) ? intval($_POST['target_status']) : -1;
    $note = trim(isset($_POST['note']) ? $_POST['note'] : '');
    if ($reviewId <= 0 || mb_strlen($note, 'UTF-8') > 1000) ai_post_fail('改判参数不正确');
    $stmt = $connect->prepare("SELECT content_type,content_id FROM starfree_ai_moderation_reviews WHERE id=? LIMIT 1");
    $stmt->bind_param('i', $reviewId); $stmt->execute(); $review = $stmt->get_result()->fetch_assoc();
    if (!$review) ai_post_fail('审核记录不存在');
    $type = $review['content_type']; $contentId = intval($review['content_id']);
    $tables = array('space'=>array('starfree_space','id',array(0,1,2),'1=1'),
        'question'=>array('starfree_qa_questions','id',array(0,1),'1=1'),
        'space_comment'=>array('starfree_space','id',array(0,1),'type=3'),
        'qa_answer'=>array('starfree_qa_answers','id',array(0,1),'1=1'),
        'qa_comment'=>array('starfree_qa_comments','id',array(0,1),'1=1'));
    if (!isset($tables[$type]) || !in_array($targetStatus, $tables[$type][2], true)) ai_post_fail('目标状态不正确');
    $table=$tables[$type][0]; $idColumn=$tables[$type][1]; $condition=$tables[$type][3];
    $currentResult=mysqli_query($connect,"SELECT status FROM `".$table."` WHERE `".$idColumn."`=".$contentId." AND ".$condition." LIMIT 1");
    $current=$currentResult?mysqli_fetch_assoc($currentResult):false;
    if (!$current) ai_post_fail('原内容不存在');
    $oldStatus=intval($current['status']);
    if ($oldStatus===$targetStatus) ai_post_fail('内容已经是所选状态');
    mysqli_begin_transaction($connect);
    $updated=mysqli_query($connect,"UPDATE `".$table."` SET status=".$targetStatus.",modified=".time()." WHERE `".$idColumn."`=".$contentId." AND ".$condition);
    if (!$updated || mysqli_affected_rows($connect)!==1) {
        mysqli_rollback($connect); ai_post_fail('内容状态修改失败');
    }
    $humanDecision=$targetStatus===1?'approved':($targetStatus===2?'locked':'rejected');
    $now=time(); $operatorUid=0;
    $stmt=$connect->prepare("UPDATE starfree_ai_moderation_reviews SET content_status=?,human_decision=?,reviewer_uid=?,review_note=?,reviewed=?,modified=? WHERE id=?");
    $reviewSaved=false;
    if ($stmt) { $stmt->bind_param('isisiii',$targetStatus,$humanDecision,$operatorUid,$note,$now,$now,$reviewId); $reviewSaved=$stmt->execute() && $stmt->affected_rows===1; }
    $stmt=$connect->prepare("INSERT INTO starfree_ai_moderation_actions(review_id,content_type,content_id,operator_uid,from_status,to_status,action,note,created) VALUES(?,?,?,?,?,?,?,?,?)");
    $actionSaved=false;
    if ($stmt) { $stmt->bind_param('isiiiissi',$reviewId,$type,$contentId,$operatorUid,$oldStatus,$targetStatus,$humanDecision,$note,$now); $actionSaved=$stmt->execute(); }
    if (!$reviewSaved || !$actionSaved) {
        mysqli_rollback($connect);
        mysqli_query($connect,"UPDATE `".$table."` SET status=".$oldStatus.",modified=".time()." WHERE `".$idColumn."`=".$contentId." AND ".$condition);
        ai_post_fail('改判记录保存失败，内容状态已恢复');
    }
    if ($type==='space') {
        $legacyStatus=$targetStatus===1?'approved':'rejected';
        $stmt=$connect->prepare("UPDATE starfree_space_ai_reviews SET status=?,reviewer_uid=0,review_note=?,modified=? WHERE space_id=?");
        if ($stmt) { $stmt->bind_param('ssii',$legacyStatus,$note,$now,$contentId); $stmt->execute(); }
    }
    if (!mysqli_commit($connect)) {
        mysqli_query($connect,"UPDATE `".$table."` SET status=".$oldStatus.",modified=".time()." WHERE `".$idColumn."`=".$contentId." AND ".$condition);
        ai_post_fail('改判提交失败，内容状态已恢复');
    }
    ai_post_ok('内容状态和人工改判记录已保存');
}
ai_post_fail('未知操作');
