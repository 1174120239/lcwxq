<?php
require_once __DIR__ . '/session.php';
include_once 'Menu.php';
require_once __DIR__ . '/mutualAidCommon.php';

if (empty($_SESSION['loginadmin'])) {
    die("<script>alert('请先登录');location.href='login.php';</script>");
}
mysqli_set_charset($connect, 'utf8mb4');

if (empty($_SESSION['mutual_aid_csrf'])) {
    $_SESSION['mutual_aid_csrf'] = bin2hex(random_bytes(24));
}
$csrf = htmlspecialchars($_SESSION['mutual_aid_csrf'], ENT_QUOTES, 'UTF-8');
$tables = mutual_aid_schema_tables($db_prefix);
$schema_missing = array();
$schema_ready = mutual_aid_schema_ready($connect, $tables, $schema_missing);
$users_ready = !empty($tables['users']) && mutual_aid_table_exists($connect, $tables['users']);

$flash = isset($_SESSION['mutual_aid_flash']) ? $_SESSION['mutual_aid_flash'] : null;
unset($_SESSION['mutual_aid_flash']);

$settings = array(
    'enabled' => 1,
    'minimum_level' => 2,
    'audit_required' => 1,
    'contact_enabled' => 1,
    'daily_contact_limit' => 5,
    'item_expiry_days' => 30,
    'modified_by' => 0,
    'modified' => 0,
);
$stats = array(0 => 0, 1 => 0, 2 => 0, 3 => 0, 4 => 0);
$comment_count = 0;
$grant_count = 0;
$items = array();
$total = 0;

if ($schema_ready) {
    $config_table = mutual_aid_quote_identifier($tables['config']);
    $config_query = $connect->query(
        'SELECT enabled,minimum_level,audit_required,contact_enabled,daily_contact_limit,'
        . 'item_expiry_days,modified_by,modified FROM ' . $config_table . ' WHERE id=1 LIMIT 1'
    );
    if ($config_query && ($row = $config_query->fetch_assoc())) {
        $settings = array_merge($settings, $row);
    }

    $items_table = mutual_aid_quote_identifier($tables['items']);
    $status_query = $connect->query('SELECT status,COUNT(*) AS total FROM ' . $items_table . ' GROUP BY status');
    if ($status_query) {
        while ($row = $status_query->fetch_assoc()) {
            $stats[intval($row['status'])] = intval($row['total']);
        }
    }
    $comments_table = mutual_aid_quote_identifier($tables['comments']);
    $grants_table = mutual_aid_quote_identifier($tables['grants']);
    $count_query = $connect->query('SELECT COUNT(*) AS total FROM ' . $comments_table . ' WHERE status=1');
    if ($count_query && ($row = $count_query->fetch_assoc())) $comment_count = intval($row['total']);
    $count_query = $connect->query('SELECT COUNT(*) AS total FROM ' . $grants_table);
    if ($count_query && ($row = $count_query->fetch_assoc())) $grant_count = intval($row['total']);

    $status = isset($_GET['status']) ? intval($_GET['status']) : -1;
    if ($status < -1 || $status > 4) $status = -1;
    $kind = isset($_GET['kind']) ? intval($_GET['kind']) : 0;
    if ($kind < 0 || $kind > 2) $kind = 0;
    $keyword = trim(isset($_GET['keyword']) ? (string)$_GET['keyword'] : '');
    if (mb_strlen($keyword, 'UTF-8') > 100) $keyword = mb_substr($keyword, 0, 100, 'UTF-8');
    $page = max(1, isset($_GET['page']) ? intval($_GET['page']) : 1);
    $limit = 20;
    $where = array('1=1');
    $types = '';
    $params = array();
    if ($status >= 0) { $where[] = 'i.status=?'; $types .= 'i'; $params[] = $status; }
    if ($kind > 0) { $where[] = 'i.kind=?'; $types .= 'i'; $params[] = $kind; }
    if ($keyword !== '') {
        $where[] = '(i.title LIKE ? OR i.description LIKE ? OR i.location LIKE ?)';
        $types .= 'sss';
        $needle = '%' . $keyword . '%';
        $params[] = $needle; $params[] = $needle; $params[] = $needle;
    }
    $where_sql = implode(' AND ', $where);
    $total_sql = 'SELECT COUNT(*) AS total FROM ' . $items_table . ' i WHERE ' . $where_sql;
    $stmt = $connect->prepare($total_sql);
    if ($stmt) {
        mutual_aid_bind_params($stmt, $types, $params);
        if ($stmt->execute() && ($row = $stmt->get_result()->fetch_assoc())) $total = intval($row['total']);
        $stmt->close();
    }
    $offset = ($page - 1) * $limit;
    $params[] = $offset; $params[] = $limit; $types .= 'ii';
    $user_select = $users_ready
        ? "COALESCE(NULLIF(u.screenName,''),NULLIF(u.name,''),CONCAT('UID ',i.uid)) AS user_name"
        : "CONCAT('UID ',i.uid) AS user_name";
    $user_join = $users_ready ? ' LEFT JOIN ' . mutual_aid_quote_identifier($tables['users']) . ' u ON u.uid=i.uid' : '';
    $list_sql = 'SELECT i.id,i.uid,i.kind,i.category,i.title,i.description,i.location,i.occurred_at,'
        . 'i.status,i.review_reason,i.created,i.modified,' . $user_select
        . ' FROM ' . $items_table . ' i' . $user_join . ' WHERE ' . $where_sql
        . ' ORDER BY i.modified DESC,i.id DESC LIMIT ?,?';
    $stmt = $connect->prepare($list_sql);
    if ($stmt) {
        mutual_aid_bind_params($stmt, $types, $params);
        if ($stmt->execute()) {
            $result = $stmt->get_result();
            if ($result) while ($row = $result->fetch_assoc()) $items[] = $row;
        }
        $stmt->close();
    }
    $page_count = max(1, (int)ceil($total / $limit));
} else {
    $status = -1; $kind = 0; $keyword = ''; $page = 1; $page_count = 1;
}

function mutual_aid_h($value) {
    return htmlspecialchars((string)$value, ENT_QUOTES, 'UTF-8');
}
function mutual_aid_time($value) {
    $timestamp = intval($value);
    return $timestamp > 0 ? date('Y-m-d H:i', $timestamp) : '-';
}
function mutual_aid_page_url($page, $status, $kind, $keyword) {
    return '?page=' . intval($page) . '&status=' . intval($status) . '&kind=' . intval($kind)
        . '&keyword=' . urlencode($keyword);
}
?>
<div class="row">
    <div class="col-lg-12">
        <?php if ($flash): ?><div class="alert alert-<?php echo $flash['type'] === 'success' ? 'success' : 'danger'; ?>"><?php echo mutual_aid_h($flash['message']); ?></div><?php endif; ?>
        <?php if (!$schema_ready): ?>
            <div class="alert alert-warning">
                互助数据表尚未就绪，本页不会自动修改数据库。请先确认已执行迁移 014（缺少：<?php echo mutual_aid_h(implode(', ', $schema_missing)); ?>）。
            </div>
        <?php else: ?>
            <div class="card"><div class="card-body">
                <div class="d-flex justify-content-between align-items-center mb-3"><div><h4 class="header-title mb-1">校园互助设置</h4><p class="text-muted mb-0">控制发布、审核、联系方式授权和信息保留期限。</p></div><span class="badge badge-<?php echo intval($settings['enabled']) === 1 ? 'success' : 'secondary'; ?> p-2"><?php echo intval($settings['enabled']) === 1 ? '已开放' : '已关闭'; ?></span></div>
                <form action="mutualAidPost.php" method="post">
                    <input type="hidden" name="csrf" value="<?php echo $csrf; ?>"><input type="hidden" name="action" value="settings">
                    <div class="form-row">
                        <div class="form-group col-md-3"><label>开放校园互助</label><input type="hidden" name="enabled" value="0"><input id="mutualAidEnabled" type="checkbox" name="enabled" value="1" data-switch="success" <?php echo intval($settings['enabled']) === 1 ? 'checked' : ''; ?>><label for="mutualAidEnabled" data-on-label="开启" data-off-label="关闭"></label></div>
                        <div class="form-group col-md-3"><label>最低参与等级</label><select class="form-control" name="minimum_level"><?php for ($level = 0; $level <= 9; $level++): ?><option value="<?php echo $level; ?>" <?php echo intval($settings['minimum_level']) === $level ? 'selected' : ''; ?>>Lv<?php echo $level; ?></option><?php endfor; ?></select></div>
                        <div class="form-group col-md-3"><label>发布后审核</label><input type="hidden" name="audit_required" value="0"><input id="mutualAidAudit" type="checkbox" name="audit_required" value="1" data-switch="success" <?php echo intval($settings['audit_required']) === 1 ? 'checked' : ''; ?>><label for="mutualAidAudit" data-on-label="开启" data-off-label="关闭"></label></div>
                        <div class="form-group col-md-3"><label>定向发送 QQ</label><input type="hidden" name="contact_enabled" value="0"><input id="mutualAidContact" type="checkbox" name="contact_enabled" value="1" data-switch="success" <?php echo intval($settings['contact_enabled']) === 1 ? 'checked' : ''; ?>><label for="mutualAidContact" data-on-label="开启" data-off-label="关闭"></label></div>
                    </div>
                    <div class="form-row">
                        <div class="form-group col-md-4"><label>每日联系方式发送上限</label><input class="form-control" type="number" name="daily_contact_limit" min="1" max="50" required value="<?php echo intval($settings['daily_contact_limit']); ?>"></div>
                        <div class="form-group col-md-4"><label>信息有效期（天）</label><input class="form-control" type="number" name="item_expiry_days" min="1" max="365" required value="<?php echo intval($settings['item_expiry_days']); ?>"></div>
                        <div class="form-group col-md-4"><label>最后修改</label><div class="form-control-plaintext"><?php echo mutual_aid_time($settings['modified']); ?></div></div>
                    </div>
                    <button class="btn btn-primary" type="submit">保存互助设置</button>
                </form>
            </div></div>
            <div class="row">
                <?php foreach (array(0 => '待审核', 1 => '进行中', 2 => '已解决', 3 => '未通过', 4 => '已关闭') as $status_key => $status_name): ?><div class="col-md-2 col-6"><div class="card"><div class="card-body py-3"><span class="text-muted"><?php echo $status_name; ?></span><h3 class="mb-0"><?php echo intval($stats[$status_key]); ?></h3></div></div></div><?php endforeach; ?>
                <div class="col-md-2 col-6"><div class="card"><div class="card-body py-3"><span class="text-muted">公开评论</span><h3 class="mb-0"><?php echo $comment_count; ?></h3></div></div></div>
                <div class="col-md-2 col-6"><div class="card"><div class="card-body py-3"><span class="text-muted">授权记录</span><h3 class="mb-0"><?php echo $grant_count; ?></h3></div></div></div>
            </div>
            <div class="card"><div class="card-body">
                <div class="d-flex justify-content-between align-items-center mb-3"><div><h4 class="header-title mb-1">互助信息</h4><p class="text-muted mb-0">操作会写入互助审计记录；后台不读取或展示联系方式。</p></div><span class="text-muted">共 <?php echo $total; ?> 条</span></div>
                <form method="get" class="form-row align-items-end mb-3">
                    <div class="form-group col-md-3"><label>状态</label><select class="form-control" name="status"><option value="-1">全部状态</option><?php foreach (array(0 => '待审核', 1 => '进行中', 2 => '已解决', 3 => '未通过', 4 => '已关闭') as $key => $label): ?><option value="<?php echo $key; ?>" <?php echo $status === $key ? 'selected' : ''; ?>><?php echo $label; ?></option><?php endforeach; ?></select></div>
                    <div class="form-group col-md-3"><label>类型</label><select class="form-control" name="kind"><option value="0">全部类型</option><option value="1" <?php echo $kind === 1 ? 'selected' : ''; ?>>寻求帮助</option><option value="2" <?php echo $kind === 2 ? 'selected' : ''; ?>>提供帮助</option></select></div>
                    <div class="form-group col-md-4"><label>搜索标题、说明或地点</label><input class="form-control" name="keyword" maxlength="100" value="<?php echo mutual_aid_h($keyword); ?>"></div>
                    <div class="form-group col-md-2"><button class="btn btn-secondary btn-block" type="submit">筛选</button></div>
                </form>
                <div class="table-responsive"><table class="table table-centered table-striped mb-0 mutual-aid-table"><thead><tr><th>信息</th><th>发布者</th><th>状态</th><th>时间</th><th>操作</th></tr></thead><tbody>
                <?php if (empty($items)): ?><tr><td colspan="5" class="text-center text-muted py-4">暂无互助信息</td></tr><?php endif; ?>
                <?php foreach ($items as $item): $item_status = intval($item['status']); $expired = intval($item['created']) + intval($settings['item_expiry_days']) * 86400 < time(); ?>
                    <tr>
                        <td><strong><?php echo mutual_aid_h($item['title']); ?></strong><div class="text-muted small"><span class="badge badge-light"><?php echo mutual_aid_h(mutual_aid_kind_label($item['kind'])); ?></span> <?php echo mutual_aid_h(mutual_aid_category_label($item['category'])); ?> · <?php echo mutual_aid_h($item['location']); ?></div><div class="mutual-aid-description"><?php echo mutual_aid_h(mb_substr((string)$item['description'], 0, 120, 'UTF-8')); ?><?php echo mb_strlen((string)$item['description'], 'UTF-8') > 120 ? '…' : ''; ?></div></td>
                        <td><?php echo mutual_aid_h($item['user_name']); ?><div class="text-muted small">UID <?php echo intval($item['uid']); ?></div></td>
                        <td><span class="badge badge-<?php echo $item_status === 1 ? 'success' : ($item_status === 0 ? 'warning' : ($item_status === 3 ? 'danger' : 'secondary')); ?>"><?php echo mutual_aid_h(mutual_aid_status_label($item_status)); ?></span><?php if ($expired && $item_status === 1): ?><div class="text-danger small mt-1">已过期</div><?php endif; ?><?php if ((string)$item['review_reason'] !== ''): ?><div class="text-muted small mt-1"><?php echo mutual_aid_h($item['review_reason']); ?></div><?php endif; ?></td>
                        <td class="text-nowrap"><?php echo mutual_aid_time($item['created']); ?><div class="text-muted small">更新 <?php echo mutual_aid_time($item['modified']); ?></div></td>
                        <td><div class="mutual-aid-actions">
                            <?php if (in_array($item_status, array(0, 3), true)): ?><form action="mutualAidPost.php" method="post"><input type="hidden" name="csrf" value="<?php echo $csrf; ?>"><input type="hidden" name="action" value="item"><input type="hidden" name="item_id" value="<?php echo intval($item['id']); ?>"><input type="hidden" name="transition" value="approve"><button class="btn btn-sm btn-outline-success" type="submit">通过</button></form><?php endif; ?>
                            <?php if (in_array($item_status, array(0, 1), true)): ?><form action="mutualAidPost.php" method="post" class="mutual-aid-reject"><input type="hidden" name="csrf" value="<?php echo $csrf; ?>"><input type="hidden" name="action" value="item"><input type="hidden" name="item_id" value="<?php echo intval($item['id']); ?>"><input type="hidden" name="transition" value="reject"><input class="form-control form-control-sm" name="reason" maxlength="500" required placeholder="拒绝理由"><button class="btn btn-sm btn-outline-danger" type="submit">拒绝</button></form><?php endif; ?>
                            <?php if ($item_status === 1): ?><form action="mutualAidPost.php" method="post"><input type="hidden" name="csrf" value="<?php echo $csrf; ?>"><input type="hidden" name="action" value="item"><input type="hidden" name="item_id" value="<?php echo intval($item['id']); ?>"><input type="hidden" name="transition" value="resolve"><button class="btn btn-sm btn-outline-primary" type="submit">标记解决</button></form><?php endif; ?>
                            <?php if ($item_status === 2): ?><form action="mutualAidPost.php" method="post"><input type="hidden" name="csrf" value="<?php echo $csrf; ?>"><input type="hidden" name="action" value="item"><input type="hidden" name="item_id" value="<?php echo intval($item['id']); ?>"><input type="hidden" name="transition" value="reopen"><button class="btn btn-sm btn-outline-primary" type="submit">重新开放</button></form><?php endif; ?>
                            <?php if ($item_status !== 4): ?><form action="mutualAidPost.php" method="post" onsubmit="return confirm('确认关闭这条互助信息？关闭后将停止公开展示。');"><input type="hidden" name="csrf" value="<?php echo $csrf; ?>"><input type="hidden" name="action" value="item"><input type="hidden" name="item_id" value="<?php echo intval($item['id']); ?>"><input type="hidden" name="transition" value="close"><button class="btn btn-sm btn-outline-secondary" type="submit">关闭</button></form><?php endif; ?>
                        </div></td>
                    </tr>
                <?php endforeach; ?></tbody></table></div>
                <?php if ($page_count > 1): ?><nav class="mt-3"><ul class="pagination pagination-sm mb-0"><?php for ($index = 1; $index <= $page_count; $index++): ?><li class="page-item <?php echo $index === $page ? 'active' : ''; ?>"><a class="page-link" href="<?php echo mutual_aid_h(mutual_aid_page_url($index, $status, $kind, $keyword)); ?>"><?php echo $index; ?></a></li><?php endfor; ?></ul></nav><?php endif; ?>
            </div></div>
        <?php endif; ?>
    </div>
</div>
<style>
.mutual-aid-table td{vertical-align:top}.mutual-aid-description{max-width:390px;white-space:normal;line-height:1.45;color:#667085}.mutual-aid-actions{display:flex;flex-wrap:wrap;gap:6px;min-width:185px}.mutual-aid-actions form{display:flex;align-items:center;gap:5px}.mutual-aid-reject{width:100%}.mutual-aid-reject .form-control{min-width:140px}@media(max-width:900px){.mutual-aid-description{max-width:240px}.mutual-aid-actions{min-width:145px}}
</style>
<?php include_once 'Footer.php'; ?>
