<?php
require_once __DIR__ . '/session.php';
require_once __DIR__ . '/mutualAidCommon.php';

if ($_SERVER['REQUEST_METHOD'] !== 'POST' || empty($_SESSION['loginadmin'])) {
    header('Location: login.php');
    exit;
}
include_once 'ipban.php';
include_once 'connect.php';
mysqli_set_charset($connect, 'utf8mb4');

$csrf = isset($_POST['csrf']) ? (string)$_POST['csrf'] : '';
if (empty($_SESSION['mutual_aid_csrf']) || $csrf === ''
    || !hash_equals($_SESSION['mutual_aid_csrf'], $csrf)) {
    $_SESSION['mutual_aid_flash'] = array('type' => 'error', 'message' => '页面已过期，请刷新后重试');
    header('Location: mutualAid.php');
    exit;
}

function mutual_aid_post_fail($message) {
    $_SESSION['mutual_aid_flash'] = array('type' => 'error', 'message' => $message);
    header('Location: mutualAid.php');
    exit;
}

function mutual_aid_post_ok($message) {
    $_SESSION['mutual_aid_flash'] = array('type' => 'success', 'message' => $message);
    header('Location: mutualAid.php');
    exit;
}

$tables = mutual_aid_schema_tables($db_prefix);
$missing = array();
if (!mutual_aid_schema_ready($connect, $tables, $missing)) {
    mutual_aid_post_fail('互助数据表未就绪，请先确认迁移 014 已执行');
}

$action = isset($_POST['action']) ? (string)$_POST['action'] : '';
if ($action === 'settings') {
    $settings = array();
    $error = '';
    if (!mutual_aid_validate_settings($_POST, $settings, $error)) {
        mutual_aid_post_fail($error);
    }
    $config_table = mutual_aid_quote_identifier($tables['config']);
    $check = $connect->query('SELECT id FROM ' . $config_table . ' WHERE id=1 LIMIT 1');
    if (!$check || !$check->fetch_assoc()) {
        mutual_aid_post_fail('互助配置行不存在，请核对迁移 014 的执行状态');
    }
    $now = time();
    $stmt = $connect->prepare(
        'UPDATE ' . $config_table . ' SET enabled=?,minimum_level=?,audit_required=?,contact_enabled=?,'
        . 'daily_contact_limit=?,item_expiry_days=?,modified_by=0,modified=? WHERE id=1'
    );
    if (!$stmt) mutual_aid_post_fail('保存失败，请检查数据库结构');
    $stmt->bind_param('iiiiiii', $settings['enabled'], $settings['minimum_level'],
        $settings['audit_required'], $settings['contact_enabled'], $settings['daily_contact_limit'],
        $settings['item_expiry_days'], $now);
    if (!$stmt->execute()) {
        $stmt->close();
        mutual_aid_post_fail('保存互助设置失败');
    }
    $stmt->close();
    mutual_aid_post_ok('互助设置已保存');
}

if ($action !== 'item') mutual_aid_post_fail('未知操作');
$item_id = isset($_POST['item_id']) ? intval($_POST['item_id']) : 0;
$transition_action = isset($_POST['transition']) ? (string)$_POST['transition'] : '';
$reason = isset($_POST['reason']) ? trim((string)$_POST['reason']) : '';
if ($item_id <= 0) mutual_aid_post_fail('信息不存在');

$items_table = mutual_aid_quote_identifier($tables['items']);
$actions_table = mutual_aid_quote_identifier($tables['actions']);
mysqli_begin_transaction($connect);
$stmt = $connect->prepare('SELECT uid,status FROM ' . $items_table . ' WHERE id=? LIMIT 1 FOR UPDATE');
if (!$stmt) {
    mysqli_rollback($connect);
    mutual_aid_post_fail('读取互助信息失败');
}
$stmt->bind_param('i', $item_id);
if (!$stmt->execute() || !($item = $stmt->get_result()->fetch_assoc())) {
    $stmt->close();
    mysqli_rollback($connect);
    mutual_aid_post_fail('信息不存在');
}
$stmt->close();
$transition = array();
$error = '';
if (!mutual_aid_transition($transition_action, intval($item['status']), $reason, $transition, $error)) {
    mysqli_rollback($connect);
    mutual_aid_post_fail($error);
}
$now = time();
if (in_array($transition_action, array('approve', 'reject'), true)) {
    $next_status = $transition['next_status'];
    $transition_reason = $transition['reason'];
    $stmt = $connect->prepare('UPDATE ' . $items_table
        . ' SET status=?,review_reason=?,reviewed_by=0,reviewed_at=?,modified=? WHERE id=?');
    if ($stmt) {
        $stmt->bind_param('isiii', $next_status, $transition_reason, $now, $now, $item_id);
    }
} else {
    $next_status = $transition['next_status'];
    $stmt = $connect->prepare('UPDATE ' . $items_table . ' SET status=?,modified=? WHERE id=?');
    if ($stmt) $stmt->bind_param('iii', $next_status, $now, $item_id);
}
if (!$stmt || !$stmt->execute() || $stmt->affected_rows !== 1) {
    if ($stmt) $stmt->close();
    mysqli_rollback($connect);
    mutual_aid_post_fail('信息状态修改失败，可能已被其他管理员处理');
}
$stmt->close();
$operator_uid = 0;
$stmt = $connect->prepare('INSERT INTO ' . $actions_table
    . ' (item_id,operator_uid,from_status,to_status,action,reason,created) VALUES(?,?,?,?,?,?,?)');
if (!$stmt) {
    mysqli_rollback($connect);
    mutual_aid_post_fail('审计记录准备失败');
}
$old_status = $transition['old_status'];
$next_status = $transition['next_status'];
$audit_action = $transition['action'];
$audit_reason = $transition['reason'];
$stmt->bind_param('iiiissi', $item_id, $operator_uid, $old_status,
    $next_status, $audit_action, $audit_reason, $now);
if (!$stmt->execute()) {
    $stmt->close();
    mysqli_rollback($connect);
    mutual_aid_post_fail('审计记录保存失败，信息状态已回滚');
}
$stmt->close();
if (!mysqli_commit($connect)) {
    mysqli_rollback($connect);
    mutual_aid_post_fail('提交失败，信息状态可能未保存');
}

if (in_array($transition_action, array('approve', 'reject'), true)
    && intval($item['uid']) > 0 && !empty($tables['inbox'])
    && mutual_aid_table_exists($connect, $tables['inbox'])) {
    $message = $transition_action === 'approve' ? '你的校园互助信息已通过审核'
        : '你的校园互助信息未通过审核：' . $transition['reason'];
    $inbox_table = mutual_aid_quote_identifier($tables['inbox']);
    $notify = $connect->prepare('INSERT INTO ' . $inbox_table
        . ' (type,uid,text,touid,isread,value,created,cid) VALUES(\'system\',0,?,?,0,?,?,?)');
    if ($notify) {
        $owner_uid = intval($item['uid']);
        $notify->bind_param('siiii', $message, $owner_uid, $item_id, $now, $item_id);
        $notify->execute();
        $notify->close();
    }
}
mutual_aid_post_ok('互助信息状态已更新');
