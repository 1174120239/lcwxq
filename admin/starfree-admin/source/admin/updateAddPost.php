<?php
require_once __DIR__ . '/session.php';
if ($_SERVER['REQUEST_METHOD'] !== 'POST' || empty($_SESSION['loginadmin'])) {
    header('Location: login.php');
    exit;
}
include_once 'connect.php';

function update_add_fail($message) {
    echo '<script>alert(' . json_encode($message, JSON_UNESCAPED_UNICODE) . ');history.back();</script>';
    exit;
}

$csrf = isset($_POST['csrf']) ? (string)$_POST['csrf'] : '';
if (empty($_SESSION['update_csrf']) || $csrf === '' || !hash_equals($_SESSION['update_csrf'], $csrf)) {
    update_add_fail('页面已过期，请刷新后重试');
}

$version = trim((string)($_POST['version'] ?? ''));
$versionCode = filter_input(INPUT_POST, 'versionCode', FILTER_VALIDATE_INT);
$versionIntro = trim((string)($_POST['versionIntro'] ?? ''));
$versionUrl = trim((string)($_POST['versionUrl'] ?? ''));
$wgtUrl = trim((string)($_POST['wgtUrl'] ?? ''));
$force = isset($_POST['force']) && (string)$_POST['force'] === '1' ? 1 : 0;

if ($version === '' || strlen($version) > 120 || $versionCode === false || $versionCode === null || $versionCode < 1 ||
    $versionIntro === '' || strlen($versionIntro) > 10000 || strlen($versionUrl) > 1000 || strlen($wgtUrl) > 2000) {
    update_add_fail('版本信息不能为空或格式不正确');
}
$urlParts = parse_url($versionUrl);
if (!filter_var($versionUrl, FILTER_VALIDATE_URL) || !is_array($urlParts) ||
    !in_array(strtolower((string)($urlParts['scheme'] ?? '')), array('http', 'https'), true) ||
    !empty($urlParts['user']) || !empty($urlParts['pass'])) {
    update_add_fail('下载链接必须是有效的 http/https 地址');
}

$maxWgtBytes = 200 * 1024 * 1024;
$upload = isset($_FILES['wgtFile']) ? $_FILES['wgtFile'] : null;
$hasUpload = $upload && (int)$upload['error'] !== UPLOAD_ERR_NO_FILE;
$uploadTemp = null;
$storedWgtTemp = null;
$storedWgt = null;
$manifestTemp = null;
$cleanup = function () use (&$uploadTemp, &$storedWgtTemp, &$storedWgt, &$manifestTemp) {
    foreach (array($uploadTemp, $storedWgtTemp, $storedWgt, $manifestTemp) as $path) {
        if ($path && is_file($path)) @unlink($path);
    }
};
$fail = function ($message) use (&$cleanup) {
    $cleanup();
    update_add_fail($message);
};

$wgtPayload = null;
$directWgtUrl = null;
if ($hasUpload && $wgtUrl !== '') $fail('WGT 文件和直链只能填写一个');
if ($wgtUrl !== '') {
    $wgtUrlParts = parse_url($wgtUrl);
    $wgtUrlPath = is_array($wgtUrlParts) ? (string)($wgtUrlParts['path'] ?? '') : '';
    if (!filter_var($wgtUrl, FILTER_VALIDATE_URL) || !is_array($wgtUrlParts) ||
        strtolower((string)($wgtUrlParts['scheme'] ?? '')) !== 'https' || empty($wgtUrlParts['host']) ||
        !empty($wgtUrlParts['user']) || !empty($wgtUrlParts['pass']) ||
        !preg_match('/\.wgt$/i', $wgtUrlPath)) {
        $fail('WGT 直链必须是公开的 HTTPS .wgt 地址');
    }
    $directWgtUrl = $wgtUrl;
}

if ($hasUpload) {
    if ((int)$upload['error'] !== UPLOAD_ERR_OK) $fail('WGT 上传失败，请重新选择文件');
    if ((int)$upload['size'] < 1 || (int)$upload['size'] > $maxWgtBytes) $fail('WGT 文件不能为空且不能超过 200 MB');
    if (strtolower(pathinfo((string)$upload['name'], PATHINFO_EXTENSION)) !== 'wgt') $fail('只能上传 .wgt 文件');
    if (!class_exists('ZipArchive')) $fail('服务器未启用 ZipArchive，暂时无法校验 WGT');

    $uploadTemp = tempnam(sys_get_temp_dir(), 'lcxqy-wgt-');
    if (!$uploadTemp || !move_uploaded_file($upload['tmp_name'], $uploadTemp)) $fail('WGT 临时文件保存失败');
    $zip = new ZipArchive();
    if ($zip->open($uploadTemp) !== true) $fail('WGT 不是有效的压缩包');
    $manifestJson = $zip->getFromName('manifest.json');
    $zip->close();
    $manifest = is_string($manifestJson) ? json_decode($manifestJson, true) : null;
    $manifestAppid = is_array($manifest)
        ? (string)($manifest['appid'] ?? $manifest['appId'] ?? $manifest['id'] ?? '') : '';
    $manifestVersion = 0;
    if (is_array($manifest)) {
        if (isset($manifest['version']) && is_array($manifest['version'])) {
            $manifestVersion = (int)($manifest['version']['code'] ?? 0);
        }
        if ($manifestVersion < 1 && isset($manifest['versionCode'])) {
            $manifestVersion = (int)$manifest['versionCode'];
        }
    }
    if (!is_array($manifest) || $manifestAppid !== '__UNI__850911F' || $manifestVersion !== (int)$versionCode) {
        $fail('WGT 的 AppID 或版本号与表单不一致');
    }

    $wgtDir = getenv('LCXQY_WGT_DIR');
    if (!$wgtDir) $wgtDir = '/opt/starfree/files/static/app-updates';
    if (!is_dir($wgtDir) && !mkdir($wgtDir, 0750, true)) $fail('无法创建 WGT 发布目录');
    if (!is_writable($wgtDir)) $fail('WGT 发布目录不可写');
    $manifestPath = rtrim($wgtDir, '/\\') . DIRECTORY_SEPARATOR . 'update.json';
    if (is_file($manifestPath)) {
        $currentManifest = json_decode((string)file_get_contents($manifestPath), true);
        $currentVersion = is_array($currentManifest) ? (int)($currentManifest['versionCode'] ?? 0) : 0;
        if ($currentVersion >= (int)$versionCode) $fail('WGT 版本号必须高于当前已发布版本');
    }
    $sha256 = hash_file('sha256', $uploadTemp);
    if (!$sha256) $fail('无法计算 WGT 文件校验值');
    $fileName = 'lcxqy-v' . (int)$versionCode . '-' . substr($sha256, 0, 16) . '.wgt';
    $storedWgt = rtrim($wgtDir, '/\\') . DIRECTORY_SEPARATOR . $fileName;
    if (is_file($storedWgt)) $fail('该版本的 WGT 已经存在，请勿重复上传');
    $storedWgtTemp = $storedWgt . '.tmp-' . bin2hex(random_bytes(8));
    if (!copy($uploadTemp, $storedWgtTemp) || !rename($storedWgtTemp, $storedWgt)) $fail('WGT 发布文件保存失败');
    @chmod($storedWgt, 0640);
    $storedWgtTemp = null;
    @unlink($uploadTemp);
    $uploadTemp = null;
    $wgtBaseUrl = getenv('LCXQY_WGT_BASE_URL');
    if (!$wgtBaseUrl) $wgtBaseUrl = 'https://frp.lcxqy.cn/app-updates';
    $wgtPayload = array(
        'appid' => '__UNI__850911F',
        'platform' => 'android',
        'version' => $version,
        'versionCode' => (int)$versionCode,
        'wgtUrl' => rtrim($wgtBaseUrl, '/') . '/' . $fileName,
        'description' => $versionIntro,
        'force' => $force === 1,
        'sha256' => $sha256
    );
} elseif ($directWgtUrl !== null) {
    $wgtDir = getenv('LCXQY_WGT_DIR');
    if (!$wgtDir) $wgtDir = '/opt/starfree/files/static/app-updates';
    if (!is_dir($wgtDir) && !mkdir($wgtDir, 0750, true)) $fail('无法创建 WGT 发布目录');
    if (!is_writable($wgtDir)) $fail('WGT 发布目录不可写');
    $manifestPath = rtrim($wgtDir, '/\\') . DIRECTORY_SEPARATOR . 'update.json';
    if (is_file($manifestPath)) {
        $currentManifest = json_decode((string)file_get_contents($manifestPath), true);
        $currentVersion = is_array($currentManifest) ? (int)($currentManifest['versionCode'] ?? 0) : 0;
        if ($currentVersion >= (int)$versionCode) $fail('WGT 版本号必须高于当前已发布版本');
    }
    $wgtPayload = array(
        'appid' => '__UNI__850911F',
        'platform' => 'android',
        'version' => $version,
        'versionCode' => (int)$versionCode,
        'wgtUrl' => $directWgtUrl,
        'description' => $versionIntro,
        'force' => $force === 1
    );
}

$connectRedis = new Redis();
if (!$connectRedis->connect($redis_host, $redis_port)) $fail('连接 Redis 失败，请检查后台配置');
if (!empty($redis_password) && !$connectRedis->auth($redis_password)) $fail('Redis 认证失败，请检查后台配置');
$redisKeys = $connectRedis->keys($redis_prefix . '_starapi_*');
foreach ($redisKeys as $redisKey) $connectRedis->del($redisKey);

$stmt = mysqli_prepare($connect, "INSERT INTO ".$db_prefix."_admin_update (`version`, `versionCode`, `versionIntro`, `versionUrl`, `force`) VALUES (?, ?, ?, ?, ?)");
if (!$stmt) $fail('新增版本失败，请检查数据库结构');
mysqli_stmt_bind_param($stmt, 'sissi', $version, $versionCode, $versionIntro, $versionUrl, $force);
$result = mysqli_stmt_execute($stmt);
$insertId = mysqli_insert_id($connect);
mysqli_stmt_close($stmt);
if (!$result) $fail('新增版本失败，请稍后重试');

if ($wgtPayload) {
    $manifestPath = rtrim($wgtDir, '/\\') . DIRECTORY_SEPARATOR . 'update.json';
    $manifestTemp = $manifestPath . '.tmp-' . bin2hex(random_bytes(8));
    $manifestJson = json_encode($wgtPayload, JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT);
    if ($manifestJson === false || file_put_contents($manifestTemp, $manifestJson, LOCK_EX) === false || !rename($manifestTemp, $manifestPath)) {
        if (is_file($manifestTemp)) @unlink($manifestTemp);
        $delete = mysqli_prepare($connect, "DELETE FROM ".$db_prefix."_admin_update WHERE id = ?");
        if ($delete) {
            mysqli_stmt_bind_param($delete, 'i', $insertId);
            mysqli_stmt_execute($delete);
            mysqli_stmt_close($delete);
        }
        $cleanup();
        update_add_fail('WGT 清单写入失败，本次版本未发布');
    }
    $manifestTemp = null;
    @chmod($manifestPath, 0640);
}

echo "<script>alert('添加成功');location.href = 'updateAdmin.php';</script>";
