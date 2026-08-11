<?php
session_start();
include_once 'Menu.php';
include_once 'identityOptionsCommon.php';
if (empty($_SESSION['identity_csrf'])) {
    $_SESSION['identity_csrf'] = bin2hex(random_bytes(24));
}

function identity_rows($connect, $options_table_name, $users_table_name, $type, $column, &$error) {
    $options_table = identity_quote_identifier($options_table_name);
    $users_table = identity_quote_identifier($users_table_name);
    if ($options_table === false || ($column !== null && $users_table === false)) {
        $error = '数据表名配置不正确';
        return array();
    }

    if ($column !== null) {
        $sql = "SELECT o.id,o.name,o.sort_order,o.enabled,COUNT(u.uid) AS user_count "
             . "FROM ".$options_table." o LEFT JOIN ".$users_table." u ON u.`".$column."`=o.id "
             . "WHERE o.type=? GROUP BY o.id,o.name,o.sort_order,o.enabled "
             . "ORDER BY o.sort_order DESC,o.id DESC";
    } else {
        $sql = "SELECT o.id,o.name,o.sort_order,o.enabled,0 AS user_count "
             . "FROM ".$options_table." o WHERE o.type=? "
             . "ORDER BY o.sort_order DESC,o.id DESC";
    }
    $stmt = $connect->prepare($sql);
    if (!$stmt) {
        error_log('identity options list prepare failed: '.$connect->error);
        $error = '读取校园身份选项失败，请检查数据库结构';
        return array();
    }
    $stmt->bind_param('s', $type);
    if (!$stmt->execute()) {
        error_log('identity options list execute failed: '.$stmt->error);
        $error = '读取校园身份选项失败，请检查数据库结构';
        $stmt->close();
        return array();
    }
    $result = $stmt->get_result();
    if (!$result) {
        error_log('identity options result failed: '.$stmt->error);
        $error = '读取校园身份选项失败';
        $stmt->close();
        return array();
    }
    $rows = $result->fetch_all(MYSQLI_ASSOC);
    $stmt->close();
    return $rows;
}

$options_table_name = identity_table_name($db_prefix, 'identity_options');
$users_table_name = identity_table_name($db_prefix, 'users');
$schema_errors = array();
$read_error = '';
$options_ready = $options_table_name !== false && identity_table_exists($connect, $options_table_name);
$users_ready = $users_table_name !== false && identity_table_exists($connect, $users_table_name);

if (!$options_ready) {
    $schema_errors[] = '缺少校园身份选项表，请由运维人员核对并执行 004_campus_identity.sql。本页不会自动修改数据库。';
    $campuses = array();
    $grades = array();
} else {
    $campus_column_ready = $users_ready
        && identity_column_exists($connect, $users_table_name, 'campus_option_id');
    $grade_column_ready = $users_ready
        && identity_column_exists($connect, $users_table_name, 'grade_option_id');
    if (!$campus_column_ready || !$grade_column_ready) {
        $schema_errors[] = '用户表缺少校区或年级关联字段，暂时无法统计使用人数，但仍可管理选项。';
    }
    $campuses = identity_rows(
        $connect,
        $options_table_name,
        $users_table_name,
        'campus',
        $campus_column_ready ? 'campus_option_id' : null,
        $read_error
    );
    $grades = identity_rows(
        $connect,
        $options_table_name,
        $users_table_name,
        'grade',
        $grade_column_ready ? 'grade_option_id' : null,
        $read_error
    );
    if ($read_error !== '') {
        $schema_errors[] = $read_error;
    }
}

function render_identity_table($title, $type, $rows) {
    $csrf = htmlspecialchars($_SESSION['identity_csrf'], ENT_QUOTES, 'UTF-8');
    echo '<div class="col-lg-6"><div class="card"><div class="card-body">';
    echo '<h4 class="header-title mb-3">'.htmlspecialchars($title, ENT_QUOTES, 'UTF-8').'</h4>';
    echo '<form action="identityOptionsPost.php" method="post" class="form-inline mb-3">';
    echo '<input type="hidden" name="csrf" value="'.$csrf.'"><input type="hidden" name="type" value="'.$type.'">';
    echo '<input class="form-control mr-2 mb-2" name="name" maxlength="40" required placeholder="新增名称">';
    echo '<input class="form-control mr-2 mb-2" name="sort_order" type="number" value="0" placeholder="排序">';
    echo '<button class="btn btn-success mb-2" type="submit">新增</button></form>';
    echo '<div class="table-responsive"><table class="table table-sm"><thead><tr><th>名称</th><th>排序</th><th>状态</th><th>用户数</th><th>操作</th></tr></thead><tbody>';
    foreach ($rows as $row) {
        $form_id = 'identity-option-'.intval($row['id']);
        echo '<tr><td><form id="'.$form_id.'" action="identityOptionsPost.php" method="post">';
        echo '<input type="hidden" name="csrf" value="'.$csrf.'"><input type="hidden" name="type" value="'.$type.'"><input type="hidden" name="id" value="'.intval($row['id']).'"></form>';
        echo '<input form="'.$form_id.'" class="form-control form-control-sm" name="name" maxlength="40" required value="'.htmlspecialchars($row['name'], ENT_QUOTES, 'UTF-8').'"></td>';
        echo '<td><input form="'.$form_id.'" class="form-control form-control-sm" name="sort_order" type="number" value="'.intval($row['sort_order']).'"></td>';
        echo '<td><select form="'.$form_id.'" class="form-control form-control-sm" name="enabled"><option value="1"'.($row['enabled'] ? ' selected' : '').'>启用</option><option value="0"'.(!$row['enabled'] ? ' selected' : '').'>停用</option></select></td>';
        echo '<td>'.intval($row['user_count']).'</td><td><button form="'.$form_id.'" class="btn btn-primary btn-sm" type="submit">保存</button></td></tr>';
    }
    echo '</tbody></table></div><p class="text-muted mb-0">已被用户选择的选项请停用，不应删除；改名会同步影响所有用户显示。</p></div></div></div>';
}
?>
<?php foreach (array_unique($schema_errors) as $schema_error) { ?>
<div class="alert alert-warning" role="alert">
    <?php echo htmlspecialchars($schema_error, ENT_QUOTES, 'UTF-8'); ?>
</div>
<?php } ?>
<div class="row">
<?php if ($options_ready) { ?>
<?php render_identity_table('校区管理', 'campus', $campuses); ?>
<?php render_identity_table('年级管理', 'grade', $grades); ?>
<?php } ?>
</div>
<?php include_once 'Footer.php'; ?>
</body></html>
