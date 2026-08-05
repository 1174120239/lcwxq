<?php
session_start();
include_once 'Menu.php';
if (empty($_SESSION['identity_csrf'])) {
    $_SESSION['identity_csrf'] = bin2hex(random_bytes(24));
}

function identity_rows($connect, $prefix, $type, $column) {
    $options_table = '`'.$prefix.'identity_options`';
    $users_table = '`'.$prefix.'users`';
    $sql = "SELECT o.id,o.name,o.sort_order,o.enabled,COUNT(u.uid) AS user_count "
         . "FROM ".$options_table." o LEFT JOIN ".$users_table." u ON u.`".$column."`=o.id "
         . "WHERE o.type=? GROUP BY o.id,o.name,o.sort_order,o.enabled "
         . "ORDER BY o.sort_order DESC,o.id DESC";
    $stmt = $connect->prepare($sql);
    $stmt->bind_param('s', $type);
    $stmt->execute();
    return $stmt->get_result()->fetch_all(MYSQLI_ASSOC);
}

$campuses = identity_rows($connect, $db_prefix, 'campus', 'campus_option_id');
$grades = identity_rows($connect, $db_prefix, 'grade', 'grade_option_id');

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
<div class="row">
<?php render_identity_table('校区管理', 'campus', $campuses); ?>
<?php render_identity_table('年级管理', 'grade', $grades); ?>
</div>
<?php include_once 'Footer.php'; ?>
</body></html>
