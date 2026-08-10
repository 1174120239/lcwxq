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

function qqbot_color($key, $default) {
    $value = strtoupper(qqbot_post_value($key, $default));
    return preg_match('/^#[0-9A-F]{6}$/', $value) ? $value : $default;
}

function qqbot_limited_text($key, $default, $maxLength) {
    $value = qqbot_post_value($key, $default);
    if (function_exists('mb_strlen') ? mb_strlen($value, 'UTF-8') > $maxLength : strlen($value) > $maxLength * 3) {
        throw new Exception($key . " 内容过长");
    }
    return $value;
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

function qqbot_group_origin() {
    return '';
}

try {
    $newBotSecret = qqbot_post_value('bot_secret');
    if ($newBotSecret !== '') {
        $secretLength = strlen($newBotSecret);
        if ($secretLength < 16 || $secretLength > 128) {
            throw new Exception("Bot Secret 长度必须为 16 到 128 个字符");
        }
        qqbot_save_config('bot_secret', $newBotSecret);
    }

    $qzonePublishTime = qqbot_post_value('qzone_publish_time', '20:30');
    if (!preg_match('/^(?:[01][0-9]|2[0-3]):[0-5][0-9]$/', $qzonePublishTime)) {
        throw new Exception("QQ 空间发布时间格式不正确");
    }
    $qzoneUgcRight = qqbot_post_value('qzone_ugc_right', '1');
    if (!in_array($qzoneUgcRight, array('1', '4', '64'), true)) {
        throw new Exception("QQ 空间可见范围不正确");
    }
    $qzoneBackgroundImageUrl = qqbot_post_value('qzone_background_image_url');
    if ($qzoneBackgroundImageUrl !== ''
        && (!filter_var($qzoneBackgroundImageUrl, FILTER_VALIDATE_URL)
            || !preg_match('/^https?:\/\//i', $qzoneBackgroundImageUrl))) {
        throw new Exception("QQ 空间背景图必须是 HTTP 或 HTTPS 地址");
    }

    $configs = array(
        'enabled' => qqbot_bool('enabled'),
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
        'tool_signin' => qqbot_bool('tool_signin'),
        'chat_in_groups' => qqbot_bool('chat_in_groups'),
        'qzone_enabled' => qqbot_bool('qzone_enabled'),
        'qzone_publish_time' => $qzonePublishTime,
        'qzone_batch_limit' => qqbot_int_range(qqbot_post_value('qzone_batch_limit'), 6, 1, 9),
        'qzone_summary_length' => qqbot_int_range(qqbot_post_value('qzone_summary_length'), 80, 20, 200),
        'qzone_include_source_images' => qqbot_bool('qzone_include_source_images'),
        'qzone_show_campus' => qqbot_bool('qzone_show_campus'),
        'qzone_show_topics' => qqbot_bool('qzone_show_topics'),
        'qzone_ugc_right' => $qzoneUgcRight,
        'qzone_title' => qqbot_limited_text('qzone_title', '聊一今日动态', 40),
        'qzone_subtitle' => qqbot_limited_text('qzone_subtitle', '校园里今天发生了什么', 80),
        'qzone_footer' => qqbot_limited_text('qzone_footer', '更多动态，来聊一看看', 80),
        'qzone_post_text' => qqbot_limited_text('qzone_post_text', "今天的校园动态整理好了。\nhttps://prev.lcxqy.cn/", 500),
        'qzone_background_color' => qqbot_color('qzone_background_color', '#F4F7F5'),
        'qzone_accent_color' => qqbot_color('qzone_accent_color', '#1E7258'),
        'qzone_text_color' => qqbot_color('qzone_text_color', '#18211E'),
        'qzone_card_color' => qqbot_color('qzone_card_color', '#FFFFFF'),
        'qzone_background_image_url' => $qzoneBackgroundImageUrl
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
            $platform = 'qq';
            $groupId = qqbot_group_text('group_id', $id, '');
            if ($groupId === '') {
                continue;
            }
            if (!ctype_digit($groupId)) {
                throw new Exception("QQ群号只能填写数字");
            }
            $groupName = qqbot_group_text('group_name', $id, '');
            $origin = qqbot_group_origin();
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
        if (!ctype_digit($newGroupId)) {
            throw new Exception("QQ群号只能填写数字");
        }
        $platform = 'qq';
        $groupName = qqbot_post_value('new_group_name');
        $origin = qqbot_group_origin();
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
