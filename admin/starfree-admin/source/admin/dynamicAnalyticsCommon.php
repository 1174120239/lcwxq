<?php

function da_scalar($connect, $sql, $default = 0) {
    $result = mysqli_query($connect, $sql);
    if (!$result) return $default;
    $row = mysqli_fetch_row($result);
    return $row && $row[0] !== null ? $row[0] : $default;
}
function da_rows($connect, $sql) {
    $rows = array();
    $result = mysqli_query($connect, $sql);
    if ($result) {
        while ($row = mysqli_fetch_assoc($result)) $rows[] = $row;
    }
    return $rows;
}

function da_row($connect, $sql) {
    $rows = da_rows($connect, $sql);
    return count($rows) > 0 ? $rows[0] : array();
}

function da_map($rows, $key, $value) {
    $result = array();
    foreach ($rows as $row) $result[(string)$row[$key]] = intval($row[$value]);
    return $result;
}

function da_table_exists($connect, $table) {
    $safe = mysqli_real_escape_string($connect, $table);
    $result = mysqli_query($connect, "SHOW TABLES LIKE '$safe'");
    return $result && mysqli_num_rows($result) > 0;
}

function da_column_exists($connect, $table, $column) {
    $safeTable = mysqli_real_escape_string($connect, $table);
    $safeColumn = mysqli_real_escape_string($connect, $column);
    $result = mysqli_query($connect, "SHOW COLUMNS FROM `$safeTable` LIKE '$safeColumn'");
    return $result && mysqli_num_rows($result) > 0;
}

function da_percent($part, $total, $precision = 1) {
    if (intval($total) <= 0) return null;
    return round(floatval($part) * 100 / floatval($total), $precision);
}

function da_percent_text($value) {
    return $value === null ? '--' : number_format($value, 1) . '%';
}

function da_number($value) {
    return number_format(intval($value));
}

function da_h($value) {
    return htmlspecialchars((string)$value, ENT_QUOTES, 'UTF-8');
}

function da_time($timestamp) {
    return intval($timestamp) > 0 ? date('Y-m-d H:i', intval($timestamp)) : '--';
}

function da_excerpt($value, $length = 60) {
    $text = trim(str_replace(array("\r", "\n", '||rn||'), ' ', (string)$value));
    if (function_exists('mb_strlen') && mb_strlen($text, 'UTF-8') > $length) {
        return mb_substr($text, 0, $length, 'UTF-8') . '...';
    }
    $characters = preg_split('//u', $text, -1, PREG_SPLIT_NO_EMPTY);
    if (is_array($characters) && count($characters) > $length) {
        return implode('', array_slice($characters, 0, $length)) . '...';
    }
    return $text;
}

function da_actual_uid_sql($spaceAlias, $anonymousAlias) {
    return "COALESCE($anonymousAlias.uid,$spaceAlias.uid)";
}

function da_reply_coverage($connect, $uid, $since = null, $anonymousReady = true) {
    $uid = intval($uid);
    $timeFilter = $since === null ? '' : ' AND p.created>=' . intval($since);
    $row = da_row($connect, "SELECT COUNT(*) posts,"
        . "SUM(CASE WHEN rc.reply_count>0 THEN 1 ELSE 0 END) replied_posts,"
        . "COALESCE(SUM(rc.reply_count),0) received_replies "
        . "FROM starfree_space p "
        . ($anonymousReady ? "LEFT JOIN starfree_anonymous_posts ap ON ap.sid=p.id " : '')
        . "LEFT JOIN (SELECT toid,COUNT(*) reply_count FROM starfree_space "
        . "WHERE type=3 AND status=1 GROUP BY toid) rc ON rc.toid=p.id "
        . "WHERE " . ($anonymousReady ? da_actual_uid_sql('p', 'ap') : 'p.uid') . "=$uid "
        . "AND p.type<>3 AND p.status=1 AND p.onlyMe=0" . $timeFilter);
    $posts = intval(isset($row['posts']) ? $row['posts'] : 0);
    $replied = intval(isset($row['replied_posts']) ? $row['replied_posts'] : 0);
    return array(
        'posts' => $posts,
        'repliedPosts' => $replied,
        'receivedReplies' => intval(isset($row['received_replies']) ? $row['received_replies'] : 0),
        'rate' => da_percent($replied, $posts)
    );
}
