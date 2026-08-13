<?php
function admin_password_column_supports($connect, $database, $table, $hash) {
    $sql = "SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS "
        . "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND COLUMN_NAME = 'pw' LIMIT 1";
    $stmt = $connect->prepare($sql);
    if (!$stmt) {
        return false;
    }
    $stmt->bind_param('ss', $database, $table);
    if (!$stmt->execute()) {
        $stmt->close();
        return false;
    }
    $stmt->bind_result($maximumLength);
    $available = $stmt->fetch() && intval($maximumLength) >= strlen($hash);
    $stmt->close();
    return $available;
}

function admin_password_is_strong($password) {
    $length = strlen($password);
    return $length >= 8 && $length <= 72
        && preg_match('/[A-Za-z]/', $password)
        && preg_match('/[0-9]/', $password);
}
