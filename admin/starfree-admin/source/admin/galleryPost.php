<?php
require_once __DIR__ . '/session.php';
if ($_SERVER['REQUEST_METHOD'] !== 'POST' || empty($_SESSION['loginadmin'])) { header('Location: login.php'); exit; }
include_once 'ipban.php'; include_once 'connect.php'; mysqli_set_charset($connect, 'utf8mb4');
function gallery_post_fail($message) { $_SESSION['gallery_flash'] = $message; header('Location: gallery.php'); exit; }
$csrf = isset($_POST['csrf']) ? (string)$_POST['csrf'] : '';
if (empty($_SESSION['gallery_csrf']) || $csrf === '' || !hash_equals($_SESSION['gallery_csrf'], $csrf)) gallery_post_fail('页面已过期，请刷新后重试');
$id = isset($_POST['id']) ? intval($_POST['id']) : 0; $status = isset($_POST['status']) ? (string)$_POST['status'] : '';
$allowed = array('submitted','published','rejected','hidden','deleted');
if ($id <= 0 || !in_array($status, $allowed, true)) gallery_post_fail('请求参数不正确');
$reason = $status === 'rejected' ? '管理员审核未通过' : '';
mysqli_begin_transaction($connect);
$stmt = $connect->prepare('SELECT status FROM starfree_gallery_items WHERE id=? LIMIT 1 FOR UPDATE');
if (!$stmt) { mysqli_rollback($connect); gallery_post_fail('数据库结构未就绪，请先执行迁移 020'); }
$stmt->bind_param('i', $id);
if (!$stmt->execute() || !($row = $stmt->get_result()->fetch_assoc())) { $stmt->close(); mysqli_rollback($connect); gallery_post_fail('图集不存在'); }
$stmt->close(); $old = (string)$row['status'];
if ($old === 'deleted' || !in_array($old, $allowed, true)) { mysqli_rollback($connect); gallery_post_fail('已删除图集不能恢复或变更'); }
$now = time();
$stmt = $connect->prepare("UPDATE starfree_gallery_items SET status=?,review_reason=?,modified=?,published_at=CASE WHEN ?='published' THEN IF(published_at=0,?,published_at) ELSE 0 END WHERE id=?");
if (!$stmt) { mysqli_rollback($connect); gallery_post_fail('状态更新准备失败'); }
$stmt->bind_param('ssisii', $status, $reason, $now, $status, $now, $id);
if (!$stmt->execute() || $stmt->affected_rows !== 1) { $stmt->close(); mysqli_rollback($connect); gallery_post_fail('图集状态未变更，请刷新后重试'); }
$stmt->close(); $operator = 0; $action = $status === 'deleted' ? 'delete' : 'review';
$stmt = $connect->prepare('INSERT INTO starfree_gallery_actions(gallery_id,operator_uid,operator_name,from_status,to_status,action,reason,created) VALUES(?,?,?,?,?,?,?,?)');
if (!$stmt) { mysqli_rollback($connect); gallery_post_fail('审计表未就绪，请先执行迁移 020'); }
$operator_name = isset($_SESSION['loginadmin']) ? (string)$_SESSION['loginadmin'] : '';
$stmt->bind_param('iisssssi', $id, $operator, $operator_name, $old, $status, $action, $reason, $now);
if (!$stmt->execute()) { $stmt->close(); mysqli_rollback($connect); gallery_post_fail('审核记录未保存，状态更新已回滚'); }
$stmt->close();
if (!mysqli_commit($connect)) { mysqli_rollback($connect); gallery_post_fail('提交失败，请检查数据库连接'); }
$_SESSION['gallery_flash'] = '图集状态已更新'; header('Location: gallery.php'); exit;
