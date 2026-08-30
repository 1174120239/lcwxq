<?php
require_once __DIR__ . '/session.php';
if ($_SERVER['REQUEST_METHOD'] !== 'POST' || empty($_SESSION['loginadmin'])) {
    header('Location: login.php');
    exit;
}
include_once 'connect.php';

function download_site_fail($message) {
    echo '<script>alert(' . json_encode($message, JSON_UNESCAPED_UNICODE) . ');history.back();</script>';
    exit;
}
$csrf = isset($_POST['csrf']) ? (string)$_POST['csrf'] : '';
if (empty($_SESSION['download_site_csrf']) || $csrf === '' || !hash_equals($_SESSION['download_site_csrf'], $csrf)) download_site_fail('页面已过期，请刷新后重试');

$kicker = trim((string)($_POST['hero_kicker'] ?? ''));
$title = trim((string)($_POST['hero_title'] ?? ''));
$intro = trim((string)($_POST['hero_intro'] ?? ''));
$webUrl = trim((string)($_POST['web_url'] ?? ''));
$originsText = trim((string)($_POST['cors_origins'] ?? ''));
if ($kicker === '' || strlen($kicker) > 120 || $title === '' || strlen($title) > 255 || $intro === '' || strlen($intro) > 2000 || strlen($webUrl) > 1000 || strlen($originsText) > 4000) download_site_fail('内容不能为空或超出长度限制');
$validUrl = function ($url) {
    $parts = parse_url($url);
    return filter_var($url, FILTER_VALIDATE_URL) && is_array($parts) && in_array(strtolower((string)($parts['scheme'] ?? '')), array('http', 'https'), true) && empty($parts['user']) && empty($parts['pass']);
};
if (!$validUrl($webUrl)) download_site_fail('网页版地址格式不正确');
$origins = preg_split('/[\r\n,]+/', $originsText, -1, PREG_SPLIT_NO_EMPTY);
$normalized = array();
foreach ($origins as $origin) {
    $origin = trim($origin);
    if (!$validUrl($origin)) download_site_fail('白名单来源必须是完整的 http/https 域名');
    $parts = parse_url($origin);
    if (!empty($parts['path']) || !empty($parts['query']) || !empty($parts['fragment']) || strpos($origin, '*') !== false || !isset($parts['host'])) download_site_fail('白名单只能填写来源，不要包含路径、参数或通配符');
    $canonical = strtolower($parts['scheme']) . '://' . strtolower($parts['host']);
    if (isset($parts['port'])) $canonical .= ':' . (int)$parts['port'];
    $normalized[$canonical] = true;
}
if (empty($normalized)) download_site_fail('请至少填写一个白名单来源');
$originsText = implode("\n", array_keys($normalized));
$table = 'lcxqy_download_site_config';
$stmt = $connect->prepare('INSERT INTO ' . $table . ' (id,hero_kicker,hero_title,hero_intro,web_url,cors_origins,updated_at) VALUES (1,?,?,?,?,?,NOW()) ON DUPLICATE KEY UPDATE hero_kicker=VALUES(hero_kicker),hero_title=VALUES(hero_title),hero_intro=VALUES(hero_intro),web_url=VALUES(web_url),cors_origins=VALUES(cors_origins),updated_at=NOW()');
if (!$stmt || !$stmt->bind_param('sssss', $kicker, $title, $intro, $webUrl, $originsText) || !$stmt->execute()) {
    if ($stmt) $stmt->close();
    download_site_fail('保存失败，请确认已执行迁移 016');
}
$stmt->close();
echo '<script>alert("下载页设置已保存");location.href="downloadSite.php";</script>';
