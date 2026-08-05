<?php
session_start();
include_once 'ipban.php';
include_once 'connect.php';
if (!isset($_SESSION['loginadmin'])) {
    die("<script>alert('请先登录');location.href='login.php';</script>");
}
if (empty($_SESSION['identity_csrf']) || empty($_POST['csrf'])
    || !hash_equals($_SESSION['identity_csrf'], $_POST['csrf'])) {
    die("<script>alert('请求已失效，请刷新后重试');history.back();</script>");
}

$type = isset($_POST['type']) ? trim($_POST['type']) : '';
$name = isset($_POST['name']) ? trim($_POST['name']) : '';
$id = isset($_POST['id']) ? intval($_POST['id']) : 0;
$sort = isset($_POST['sort_order']) ? intval($_POST['sort_order']) : 0;
$enabled = isset($_POST['enabled']) && intval($_POST['enabled']) === 0 ? 0 : 1;
if (!in_array($type, array('campus', 'grade'), true) || $name === '' || mb_strlen($name, 'UTF-8') > 40) {
    die("<script>alert('参数不正确');history.back();</script>");
}
if ($type === 'grade' && !preg_match('/^\\d{4}级$/u', $name)) {
    die("<script>alert('年级名称请使用“2024级”格式');history.back();</script>");
}
$now = time();
$options_table = '`'.$db_prefix.'identity_options`';
if ($id > 0) {
    $stmt = $connect->prepare("UPDATE ".$options_table." SET name=?,sort_order=?,enabled=?,modified=? WHERE id=? AND type=?");
    $stmt->bind_param('siiiis', $name, $sort, $enabled, $now, $id, $type);
} else {
    $stmt = $connect->prepare("INSERT INTO ".$options_table."(type,name,sort_order,enabled,created,modified) VALUES(?,?,?,?,?,?)");
    $stmt->bind_param('ssiiii', $type, $name, $sort, $enabled, $now, $now);
}
if (!$stmt->execute()) {
    $message = htmlspecialchars($stmt->error, ENT_QUOTES, 'UTF-8');
    die("<script>alert('保存失败：{$message}');history.back();</script>");
}
$stmt->close();
header('Location: identityOptions.php');
exit;
