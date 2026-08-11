<?php
session_start();
$file = $_SERVER['PHP_SELF'];
if ($_SERVER['REQUEST_METHOD'] !== 'POST' || empty($_SESSION['loginadmin'])) {
    echo "<script>alert('非法操作，行为已记录');location.href='warning.php?route=$file';</script>";
    exit;
}
include_once 'connect.php';
$csrf = isset($_POST['csrf']) ? (string)$_POST['csrf'] : '';
if (empty($_SESSION['invitation_csrf']) || !hash_equals($_SESSION['invitation_csrf'], $csrf)) {
    echo "<script>alert('页面已过期，请刷新后重试');history.back();</script>";
    exit;
}
$enabled = isset($_POST['enabled']) && (string)$_POST['enabled'] === '1' ? 1 : 0;
$points = filter_input(INPUT_POST, 'reward_points', FILTER_VALIDATE_INT);
$experience = filter_input(INPUT_POST, 'reward_experience', FILTER_VALIDATE_INT);
$android = trim((string)($_POST['android_download_url'] ?? ''));
$ios = trim((string)($_POST['ios_download_url'] ?? ''));
$validUrl = function ($url) {
    return $url === '' || (strlen($url) <= 1000 && filter_var($url, FILTER_VALIDATE_URL)
        && in_array(strtolower((string)parse_url($url, PHP_URL_SCHEME)), array('http', 'https'), true));
};
if ($points === false || $experience === false || $points < 0 || $experience < 0
    || $points > 1000000 || $experience > 1000000 || !$validUrl($android) || !$validUrl($ios)) {
    echo "<script>alert('奖励数值或下载地址格式不正确');history.back();</script>";
    exit;
}
$stmt = $connect->prepare(
    'INSERT INTO lcxqy_invitation_config '
    . '(id,enabled,reward_points,reward_experience,android_download_url,ios_download_url,updated_at) '
    . 'VALUES(1,?,?,?,?,?,NOW()) ON DUPLICATE KEY UPDATE enabled=VALUES(enabled),'
    . 'reward_points=VALUES(reward_points),reward_experience=VALUES(reward_experience),'
    . 'android_download_url=VALUES(android_download_url),ios_download_url=VALUES(ios_download_url),'
    . 'updated_at=NOW()'
);
if (!$stmt || !$stmt->bind_param('iiiss', $enabled, $points, $experience, $android, $ios) || !$stmt->execute()) {
    if ($stmt) $stmt->close();
    echo "<script>alert('保存失败，请确认已执行邀请系统迁移');history.back();</script>";
    exit;
}
$stmt->close();
echo "<script>alert('邀请设置已保存');location.href='invitation.php';</script>";
