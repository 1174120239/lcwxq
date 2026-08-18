<?php

function mutual_aid_table_name($prefix, $suffix) {
    $prefix = rtrim((string)$prefix, '_');
    if ($prefix === '' || !preg_match('/^[A-Za-z0-9_]+$/', $prefix)
        || !preg_match('/^[A-Za-z0-9_]+$/', $suffix)) {
        return false;
    }
    return $prefix . '_' . $suffix;
}

function mutual_aid_quote_identifier($identifier) {
    if (!is_string($identifier) || !preg_match('/^[A-Za-z0-9_]+$/', $identifier)) {
        return false;
    }
    return '`' . $identifier . '`';
}

function mutual_aid_table_exists($connect, $table_name) {
    if ($table_name === false) {
        return false;
    }
    $stmt = $connect->prepare(
        'SELECT COUNT(*) AS total FROM information_schema.tables '
        . 'WHERE table_schema=DATABASE() AND table_name=?'
    );
    if (!$stmt) {
        return false;
    }
    $stmt->bind_param('s', $table_name);
    if (!$stmt->execute()) {
        $stmt->close();
        return false;
    }
    $row = $stmt->get_result()->fetch_assoc();
    $stmt->close();
    return $row && intval($row['total']) === 1;
}

function mutual_aid_schema_tables($prefix) {
    return array(
        'items' => mutual_aid_table_name($prefix, 'lost_found_items'),
        'actions' => mutual_aid_table_name($prefix, 'lost_found_actions'),
        'comments' => mutual_aid_table_name($prefix, 'lost_found_comments'),
        'grants' => mutual_aid_table_name($prefix, 'lost_found_contact_grants'),
        'config' => mutual_aid_table_name($prefix, 'lost_found_config'),
        'users' => mutual_aid_table_name($prefix, 'users'),
        'inbox' => mutual_aid_table_name($prefix, 'inbox'),
    );
}

function mutual_aid_schema_ready($connect, $tables, &$missing) {
    $missing = array();
    foreach (array('items', 'actions', 'comments', 'grants', 'config') as $key) {
        if (empty($tables[$key]) || !mutual_aid_table_exists($connect, $tables[$key])) {
            $missing[] = isset($tables[$key]) ? $tables[$key] : $key;
        }
    }
    return count($missing) === 0;
}

function mutual_aid_bind_params($stmt, $types, &$params) {
    if ($types === '') {
        return true;
    }
    $bind = array($types);
    foreach ($params as $index => &$value) {
        $bind[] =& $value;
    }
    $result = call_user_func_array(array($stmt, 'bind_param'), $bind);
    unset($value);
    return $result;
}

function mutual_aid_status_label($status) {
    $labels = array(0 => '待审核', 1 => '进行中', 2 => '已解决', 3 => '未通过', 4 => '已关闭');
    return isset($labels[intval($status)]) ? $labels[intval($status)] : '未知状态';
}

function mutual_aid_kind_label($kind) {
    return intval($kind) === 2 ? '提供帮助' : '寻求帮助';
}

function mutual_aid_category_label($category) {
    $labels = array(1 => '失物招领', 2 => '物品借用', 3 => '学习互助', 4 => '校园生活', 5 => '其他帮助');
    return isset($labels[intval($category)]) ? $labels[intval($category)] : '其他帮助';
}

function mutual_aid_validate_settings($input, &$settings, &$error) {
    $settings = array(
        'enabled' => isset($input['enabled']) && (string)$input['enabled'] === '1' ? 1 : 0,
        'minimum_level' => isset($input['minimum_level']) ? filter_var($input['minimum_level'], FILTER_VALIDATE_INT) : false,
        'audit_required' => isset($input['audit_required']) && (string)$input['audit_required'] === '1' ? 1 : 0,
        'contact_enabled' => isset($input['contact_enabled']) && (string)$input['contact_enabled'] === '1' ? 1 : 0,
        'daily_contact_limit' => isset($input['daily_contact_limit']) ? filter_var($input['daily_contact_limit'], FILTER_VALIDATE_INT) : false,
        'item_expiry_days' => isset($input['item_expiry_days']) ? filter_var($input['item_expiry_days'], FILTER_VALIDATE_INT) : false,
    );
    if ($settings['minimum_level'] === false || $settings['minimum_level'] < 0 || $settings['minimum_level'] > 9) {
        $error = '最低参与等级必须在 Lv0 到 Lv9 之间';
        return false;
    }
    if ($settings['daily_contact_limit'] === false || $settings['daily_contact_limit'] < 1
        || $settings['daily_contact_limit'] > 50) {
        $error = '每日联系方式发送上限必须在 1 到 50 之间';
        return false;
    }
    if ($settings['item_expiry_days'] === false || $settings['item_expiry_days'] < 1
        || $settings['item_expiry_days'] > 365) {
        $error = '信息有效期必须在 1 到 365 天之间';
        return false;
    }
    $error = '';
    return true;
}

function mutual_aid_transition($action, $old_status, $reason, &$transition, &$error) {
    $old_status = intval($old_status);
    $action = is_string($action) ? $action : '';
    $reason = trim((string)$reason);
    $next_status = -1;
    if ($action === 'approve' && in_array($old_status, array(0, 3), true)) {
        $next_status = 1;
        $reason = '';
    } elseif ($action === 'reject' && in_array($old_status, array(0, 1), true)) {
        if ($reason === '') {
            $error = '拒绝时必须填写理由';
            return false;
        }
        if (mb_strlen($reason, 'UTF-8') > 500) {
            $error = '拒绝理由不能超过 500 个字';
            return false;
        }
        $next_status = 3;
    } elseif ($action === 'resolve' && $old_status === 1) {
        $next_status = 2;
        $reason = '';
    } elseif ($action === 'reopen' && $old_status === 2) {
        $next_status = 1;
        $reason = '';
    } elseif ($action === 'close' && $old_status >= 0 && $old_status <= 3) {
        $next_status = 4;
        $reason = '';
    } else {
        $error = '当前状态不能执行该操作';
        return false;
    }
    $transition = array(
        'action' => $action,
        'old_status' => $old_status,
        'next_status' => $next_status,
        'reason' => $reason,
    );
    $error = '';
    return true;
}
