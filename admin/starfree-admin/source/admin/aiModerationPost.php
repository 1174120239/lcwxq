<?php
require_once __DIR__ . '/session.php';
if ($_SERVER['REQUEST_METHOD'] !== 'POST' || !isset($_SESSION['loginadmin']) || $_SESSION['loginadmin']==='') { header('Location: login.php'); exit; }
if (empty($_SESSION['ai_moderation_csrf']) || empty($_POST['csrf'])
    || !hash_equals($_SESSION['ai_moderation_csrf'], (string)$_POST['csrf'])) {
    echo "<script>alert('页面已过期，请刷新后重试');history.back();</script>";
    exit;
}
include_once 'connect.php';
$enabled = isset($_POST['enabled']) && $_POST['enabled']==='1' ? 1 : 0;
$model = trim(isset($_POST['model']) ? $_POST['model'] : 'deepseek-chat');
$apiUrl = 'https://api.deepseek.com/chat/completions';
$apiKey = trim(isset($_POST['api_key']) ? $_POST['api_key'] : '');
$prompt = trim(isset($_POST['custom_prompt']) ? $_POST['custom_prompt'] : '');
if ($model==='' || strlen($model)>80 || strlen($apiKey)>512 || mb_strlen($prompt,'UTF-8')>2000) { echo "<script>alert('配置格式不正确');history.back();</script>"; exit; }
$currentKey = '';
$result = mysqli_query($connect, "SELECT api_key FROM starfree_ai_moderation_config WHERE id=1 LIMIT 1");
if ($result && ($row=mysqli_fetch_assoc($result))) $currentKey=$row['api_key'];
if ($apiKey==='') $apiKey=$currentKey;
if (!$connect) { echo "<script>alert('数据库连接失败');history.back();</script>"; exit; }
$stmt=$connect->prepare("INSERT INTO starfree_ai_moderation_config(id,enabled,provider,api_url,api_key,model,custom_prompt,modified_by,modified) VALUES(1,?,'deepseek',?,?,?,?,NULL,?) ON DUPLICATE KEY UPDATE enabled=VALUES(enabled),provider=VALUES(provider),api_url=VALUES(api_url),api_key=VALUES(api_key),model=VALUES(model),custom_prompt=VALUES(custom_prompt),modified=VALUES(modified)");
if (!$stmt) { error_log('AI moderation config prepare failed: '.$connect->error); echo "<script>alert('保存失败');history.back();</script>"; exit; }
$now=time();
$stmt->bind_param('issssi',$enabled,$apiUrl,$apiKey,$model,$prompt,$now);
if (!$stmt->execute()) { echo "<script>alert('保存失败');history.back();</script>"; exit; }
echo "<script>alert('AI 审核设置已保存');location.href='aiModeration.php';</script>";
