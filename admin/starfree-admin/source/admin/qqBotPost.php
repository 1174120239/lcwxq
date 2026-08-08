<?php
session_start();
$file = $_SERVER['PHP_SELF'];

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    echo "<script>alert('非法操作，行为已记录');location.href = 'warning.php?route=$file';</script>";
    exit;
}
if (!isset($_SESSION['loginadmin']) || $_SESSION['loginadmin'] === '') {
    echo "<script>alert('请先登录');window.location.href='login.php';</script>";
    exit;
}

include_once 'connect.php';

function qqbot_post_value($key, $default = '') {
    return isset($_POST[$key]) ? trim((string)$_POST[$key]) : $default;
}

function qqbot_bool($key) {
    return isset($_POST[$key]) && $_POST[$key] === '1' ? '1' : '0';
}

function qqbot_int_range($value, $default, $min, $max) {
    if ($value === '' || !is_numeric($value)) {
        return (string)$default;
    }
    $number = intval($value);
    if ($number < $min) {
        $number = $min;
    }
    if ($number > $max) {
        $number = $max;
    }
    return (string)$number;
}

function qqbot_save_config($key, $value) {
    global $connect;
    $stmt = $connect->prepare("INSERT INTO lcxqy_bot_config(config_key,config_value,updated_at) "
        . "VALUES (?, ?, NOW()) ON DUPLICATE KEY UPDATE config_value=VALUES(config_value),updated_at=VALUES(updated_at)");
    if (!$stmt) {
        throw new Exception("配置保存准备失败: " . $connect->error);
    }
    $stmt->bind_param("ss", $key, $value);
    if (!$stmt->execute()) {
        throw new Exception("配置保存失败: " . $stmt->error);
    }
    $stmt->close();
}

function qqbot_group_text($arrayKey, $id, $default = '') {
    if (!isset($_POST[$arrayKey]) || !is_array($_POST[$arrayKey])) {
        return $default;
    }
    return isset($_POST[$arrayKey][$id]) ? trim((string)$_POST[$arrayKey][$id]) : $default;
}

try {
    $configs = array(
        'enabled' => qqbot_bool('enabled'),
        'bot_secret' => qqbot_post_value('bot_secret'),
        'bot_public_base_url' => qqbot_post_value('bot_public_base_url'),
        'h5_base_url' => qqbot_post_value('h5_base_url', 'https://prev.lcxqy.cn'),
        'forum_register_url' => qqbot_post_value('forum_register_url', 'https://prev.lcxqy.cn/#/pages/user/register'),
        'deepseek_api_key' => qqbot_post_value('deepseek_api_key'),
        'deepseek_api_base' => qqbot_post_value('deepseek_api_base', 'https://api.deepseek.com'),
        'deepseek_model' => qqbot_post_value('deepseek_model', 'deepseek-chat'),
        'sync_interval_seconds' => qqbot_int_range(qqbot_post_value('sync_interval_seconds'), 45, 10, 3600),
        'sync_max_images' => qqbot_int_range(qqbot_post_value('sync_max_images'), 3, 0, 9),
        'sync_summary_length' => qqbot_int_range(qqbot_post_value('sync_summary_length'), 120, 20, 500),
        'tool_add_space' => qqbot_bool('tool_add_space'),
        'tool_update_profile' => qqbot_bool('tool_update_profile'),
        'tool_status' => qqbot_bool('tool_status'),
        'tool_signin' => qqbot_bool('tool_signin')
    );
    foreach ($configs as $key => $value) {
        qqbot_save_config($key, $value);
    }

    if (isset($_POST['group_ids']) && is_array($_POST['group_ids'])) {
        foreach ($_POST['group_ids'] as $rawId) {
            $id = intval($rawId);
            if ($id <= 0) {
                continue;
            }
            $platform = qqbot_group_text('group_platform', $id, 'qq');
            $groupId = qqbot_group_text('group_id', $id, '');
            if ($groupId === '') {
                continue;
            }
            $groupName = qqbot_group_text('group_name', $id, '');
            $origin = qqbot_group_text('unified_msg_origin', $id, '');
            $enabled = isset($_POST['group_enabled'][$id]) ? 1 : 0;
            $maxImages = intval(qqbot_int_range(qqbot_group_text('group_max_images', $id, '3'), 3, 0, 9));
            $summaryLength = intval(qqbot_int_range(qqbot_group_text('group_summary_length', $id, '120'), 120, 20, 500));
            $stmt = $connect->prepare("UPDATE lcxqy_bot_group_sync SET platform=?,group_id=?,group_name=?,"
                . "unified_msg_origin=?,enabled=?,max_images=?,summary_length=?,updated_at=NOW() WHERE id=?");
            if (!$stmt) {
                throw new Exception("群配置保存准备失败: " . $connect->error);
            }
            $stmt->bind_param("ssssiiii", $platform, $groupId, $groupName, $origin,
                $enabled, $maxImages, $summaryLength, $id);
            if (!$stmt->execute()) {
                throw new Exception("群配置保存失败: " . $stmt->error);
            }
            $stmt->close();
        }
    }

    $newGroupId = qqbot_post_value('new_group_id');
    if ($newGroupId !== '') {
        $platform = qqbot_post_value('new_platform', 'qq');
        $groupName = qqbot_post_value('new_group_name');
        $origin = qqbot_post_value('new_unified_msg_origin');
        $maxImages = intval(qqbot_int_range(qqbot_post_value('new_max_images'), 3, 0, 9));
        $summaryLength = intval(qqbot_int_range(qqbot_post_value('new_summary_length'), 120, 20, 500));
        $stmt = $connect->prepare("INSERT INTO lcxqy_bot_group_sync "
            . "(platform,group_id,group_name,unified_msg_origin,enabled,max_images,summary_length,created_at,updated_at) "
            . "VALUES (?,?,?,?,1,?,?,NOW(),NOW()) "
            . "ON DUPLICATE KEY UPDATE group_name=VALUES(group_name),unified_msg_origin=VALUES(unified_msg_origin),"
            . "enabled=1,max_images=VALUES(max_images),summary_length=VALUES(summary_length),updated_at=VALUES(updated_at)");
        if (!$stmt) {
            throw new Exception("新增群配置准备失败: " . $connect->error);
        }
        $stmt->bind_param("ssssii", $platform, $newGroupId, $groupName, $origin, $maxImages, $summaryLength);
        if (!$stmt->execute()) {
            throw new Exception("新增群配置失败: " . $stmt->error);
        }
        $stmt->close();
    }

    echo "<script>alert('QQ Bot设置已保存');window.location.href='qqBot.php';</script>";
} catch (Exception $error) {
    $message = addslashes($error->getMessage());
    echo "<script>alert('保存失败：".$message."');history.back();</script>";
}
?>
