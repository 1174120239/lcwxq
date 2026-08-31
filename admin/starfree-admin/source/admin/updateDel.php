<?php
require_once __DIR__ . '/session.php';
include_once 'connect.php';

$id = filter_input(INPUT_GET, 'id', FILTER_VALIDATE_INT);
$status = isset($_GET['status']) ? (string)$_GET['status'] : '';
$file = $_SERVER['PHP_SELF'];

function update_delete_fail($message) {
    echo '<script>alert(' . json_encode($message, JSON_UNESCAPED_UNICODE) . ');history.back();</script>';
    exit;
}

function update_wgt_dir() {
    $dir = getenv('LCXQY_WGT_DIR');
    if (!$dir) $dir = '/opt/starfree/files/static/app-updates';
    return rtrim($dir, '/\\');
}

function update_wgt_manifest_path() {
    return update_wgt_dir() . DIRECTORY_SEPARATOR . 'update.json';
}

function update_wgt_read_manifest() {
    $path = update_wgt_manifest_path();
    if (!is_file($path)) return null;
    $manifest = json_decode((string)file_get_contents($path), true);
    return is_array($manifest) ? $manifest : null;
}

function update_wgt_clear_manifest() {
    $path = update_wgt_manifest_path();
    $dir = dirname($path);
    if (!is_dir($dir) || !is_writable($dir)) return false;
    $temp = $path . '.tmp-' . bin2hex(random_bytes(8));
    $empty = array(
        'appid' => '__UNI__850911F',
        'platform' => 'android',
        'version' => '',
        'versionCode' => 0,
        'wgtUrl' => '',
        'description' => '',
        'force' => false
    );
    $json = json_encode($empty, JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT);
    if ($json === false || file_put_contents($temp, $json, LOCK_EX) === false || !rename($temp, $path)) {
        if (is_file($temp)) @unlink($temp);
        return false;
    }
    @chmod($path, 0640);
    return true;
}

function update_wgt_delete_uploaded_file($manifest) {
    if (!is_array($manifest) || !preg_match('/^[a-f0-9]{64}$/i', (string)($manifest['sha256'] ?? '')) || !is_string($manifest['wgtUrl'])) return true;
    $path = parse_url($manifest['wgtUrl'], PHP_URL_PATH);
    $name = is_string($path) ? basename(rawurldecode($path)) : '';
    if (!preg_match('/^lcxqy-v[0-9]+-[a-f0-9]{16}\.wgt$/i', $name)) return true;
    $file = update_wgt_dir() . DIRECTORY_SEPARATOR . $name;
    return !is_file($file) || @unlink($file);
}

if (empty($_SESSION['loginadmin'])) {
    echo "<script>alert('非法操作，行为已记录');location.href = 'warning.php?route=" . rawurlencode($file) . "';</script>";
    exit;
}
if ($status !== 'one' || $id === false || $id === null || $id < 1) update_delete_fail('参数错误');

$select = mysqli_prepare($connect, "SELECT versionCode FROM ".$db_prefix."_admin_update WHERE id = ? LIMIT 1");
if (!$select) update_delete_fail('读取版本失败');
mysqli_stmt_bind_param($select, 'i', $id);
mysqli_stmt_execute($select);
mysqli_stmt_store_result($select);
if (mysqli_stmt_num_rows($select) !== 1) {
    mysqli_stmt_close($select);
    update_delete_fail('版本不存在或已经删除');
}
mysqli_stmt_bind_result($select, $deletedVersionCode);
mysqli_stmt_fetch($select);
mysqli_stmt_close($select);

$currentWgt = update_wgt_read_manifest();
$stmt = mysqli_prepare($connect, "DELETE FROM ".$db_prefix."_admin_update WHERE id = ?");
if (!$stmt) update_delete_fail('删除失败');
mysqli_stmt_bind_param($stmt, 'i', $id);
$result = mysqli_stmt_execute($stmt);
$affected = mysqli_stmt_affected_rows($stmt);
mysqli_stmt_close($stmt);
if (!$result || $affected !== 1) update_delete_fail('删除失败');

$cleanupMessage = '';
if (is_array($currentWgt) && (int)($currentWgt['versionCode'] ?? 0) === (int)$deletedVersionCode && !empty($currentWgt['wgtUrl'])) {
    if (!update_wgt_clear_manifest()) {
        $cleanupMessage = '，但安卓 WGT 清单清理失败，请稍后重试';
    } elseif (!update_wgt_delete_uploaded_file($currentWgt)) {
        $cleanupMessage = '，但后台上传的 WGT 文件未能删除';
    }
}

// Version responses may be cached by the legacy admin API; invalidation is best effort.
$redisMessage = '';
if (!class_exists('Redis')) {
    $redisMessage = '，Redis 缓存未清理';
} else {
    try {
        $connectRedis = new Redis();
        if ($connectRedis->connect($redis_host, $redis_port, 2.0)) {
            if (!empty($redis_password) && !$connectRedis->auth($redis_password)) throw new Exception('auth');
            $redisKeys = $connectRedis->keys($redis_prefix . '_starapi_*');
            foreach ($redisKeys as $redisKey) $connectRedis->del($redisKey);
        } else {
            $redisMessage = '，Redis 缓存未清理';
        }
    } catch (Throwable $ignored) {
        $redisMessage = '，Redis 缓存未清理';
    }
}

echo "<script>alert(" . json_encode('删除成功' . $cleanupMessage . $redisMessage, JSON_UNESCAPED_UNICODE) . ");location.href = 'updateAdmin.php';</script>";
