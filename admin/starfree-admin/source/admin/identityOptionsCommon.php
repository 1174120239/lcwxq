<?php

function identity_table_name($prefix, $suffix) {
    $prefix = rtrim((string) $prefix, '_');
    if (!preg_match('/^[A-Za-z0-9_]+$/', $prefix)
        || !preg_match('/^[A-Za-z0-9_]+$/', $suffix)) {
        return false;
    }
    return $prefix.'_'.$suffix;
}

function identity_quote_identifier($identifier) {
    if (!is_string($identifier) || !preg_match('/^[A-Za-z0-9_]+$/', $identifier)) {
        return false;
    }
    return '`'.$identifier.'`';
}

function identity_table_exists($connect, $table) {
    $stmt = $connect->prepare(
        'SELECT 1 FROM information_schema.TABLES '
        .'WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? LIMIT 1'
    );
    if (!$stmt) {
        error_log('identity options table check failed: '.$connect->error);
        return false;
    }
    $stmt->bind_param('s', $table);
    if (!$stmt->execute()) {
        error_log('identity options table check execute failed: '.$stmt->error);
        $stmt->close();
        return false;
    }
    $result = $stmt->get_result();
    $exists = $result && $result->num_rows > 0;
    $stmt->close();
    return $exists;
}

function identity_column_exists($connect, $table, $column) {
    $stmt = $connect->prepare(
        'SELECT 1 FROM information_schema.COLUMNS '
        .'WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND COLUMN_NAME=? LIMIT 1'
    );
    if (!$stmt) {
        error_log('identity options column check failed: '.$connect->error);
        return false;
    }
    $stmt->bind_param('ss', $table, $column);
    if (!$stmt->execute()) {
        error_log('identity options column check execute failed: '.$stmt->error);
        $stmt->close();
        return false;
    }
    $result = $stmt->get_result();
    $exists = $result && $result->num_rows > 0;
    $stmt->close();
    return $exists;
}
