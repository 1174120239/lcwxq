<?php
require_once __DIR__ . '/session.php';
include_once 'Menu.php';
if (empty($_SESSION['loginadmin'])) die("<script>alert('请先登录');location.href='login.php';</script>");
mysqli_set_charset($connect, 'utf8mb4');
if (empty($_SESSION['gallery_csrf'])) $_SESSION['gallery_csrf'] = bin2hex(random_bytes(24));
$csrf = htmlspecialchars($_SESSION['gallery_csrf'], ENT_QUOTES, 'UTF-8');
$status = isset($_GET['status']) ? (string)$_GET['status'] : '';
$allowed = array('submitted', 'published', 'rejected', 'hidden', 'deleted');
if (!in_array($status, $allowed, true)) $status = '';
$tag = isset($_GET['tag']) ? trim((string)$_GET['tag']) : '';
if (function_exists('mb_substr')) $tag = mb_substr($tag, 0, 24, 'UTF-8'); else $tag = substr($tag, 0, 24);
$page = max(1, isset($_GET['page']) ? intval($_GET['page']) : 1);
$limit = 20;
$where = ' WHERE 1=1';
$params = array(); $types = '';
if ($status !== '') { $where .= ' AND g.status=?'; $params[] = $status; $types .= 's'; }
if ($tag !== '') { $where .= ' AND FIND_IN_SET(?,g.tags)>0'; $params[] = $tag; $types .= 's'; }
function gallery_bind_params($stmt, $types, &$params) { if ($types === '') return true; $bind = array($types); foreach ($params as $index => &$value) $bind[] = &$value; $ok = call_user_func_array(array($stmt, 'bind_param'), $bind); unset($value); return $ok; }
$total = 0; $count = $connect->prepare('SELECT COUNT(*) AS total FROM starfree_gallery_items g' . $where);
if ($count) { gallery_bind_params($count, $types, $params); if ($count->execute()) { $row = $count->get_result()->fetch_assoc(); $total = intval($row['total']); } $count->close(); }
$rows = array();
$stmt = $connect->prepare('SELECT g.*,u.name AS author_name,u.screenName AS screen_name FROM starfree_gallery_items g LEFT JOIN starfree_users u ON u.uid=g.author_uid' . $where . " ORDER BY FIELD(g.status,'submitted','published','rejected','hidden','deleted'),g.modified DESC,g.id DESC LIMIT ?,?");
if ($stmt) { $bindTypes = $types . 'ii'; $bindParams = $params; $bindParams[] = ($page - 1) * $limit; $bindParams[] = $limit; gallery_bind_params($stmt, $bindTypes, $bindParams); if ($stmt->execute()) { $result = $stmt->get_result(); while ($row = $result->fetch_assoc()) $rows[] = $row; } $stmt->close(); }
$flash = isset($_SESSION['gallery_flash']) ? $_SESSION['gallery_flash'] : ''; unset($_SESSION['gallery_flash']);
$labels = array('submitted' => '待审核', 'published' => '已发布', 'rejected' => '未通过', 'hidden' => '已隐藏', 'deleted' => '已删除');
?>
<!DOCTYPE html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>图集管理</title><link href="<?php echo $ADMIN_PATH;?>/assets/css/icons.min.css" rel="stylesheet"><link href="<?php echo $ADMIN_PATH;?>/assets/css/app.min.css" rel="stylesheet"><style>.gallery-thumb{width:78px;height:92px;object-fit:cover;border-radius:4px;background:#eef1ee}.gallery-tags{max-width:220px;color:#648170;font-size:12px}.gallery-title{font-weight:600;max-width:260px}.gallery-action{display:inline-block;margin:2px}.gallery-status{white-space:nowrap}.gallery-reason{max-width:240px;font-size:12px;color:#78847d}.gallery-filter{display:flex;gap:8px;align-items:center;flex-wrap:wrap}.gallery-filter input,.gallery-filter select{max-width:200px}.gallery-row td{vertical-align:middle}.gallery-empty{text-align:center;padding:40px;color:#87928c}</style></head><body>
<div class="wrapper"><div class="container-fluid"><div class="content-page"><div class="content"><div class="container-fluid">
<div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">图集管理</h4><div class="page-title-right text-muted">独立图库内容审核与运营</div></div></div></div>
<?php if ($flash !== ''): ?><div class="alert alert-info"><?php echo htmlspecialchars($flash, ENT_QUOTES, 'UTF-8'); ?></div><?php endif; ?>
<div class="card"><div class="card-body"><form method="get" class="gallery-filter mb-3"><select name="status" class="form-control"><option value="">全部状态</option><?php foreach ($allowed as $value): ?><option value="<?php echo $value; ?>" <?php echo $status === $value ? 'selected' : ''; ?>><?php echo $labels[$value]; ?></option><?php endforeach; ?></select><input class="form-control" name="tag" value="<?php echo htmlspecialchars($tag, ENT_QUOTES, 'UTF-8'); ?>" placeholder="按标签筛选"><button class="btn btn-primary" type="submit">筛选</button></form>
<div class="table-responsive"><table class="table table-centered table-hover"><thead><tr><th>封面</th><th>图集</th><th>作者</th><th>标签</th><th>状态</th><th>浏览</th><th>提交时间</th><th>操作</th></tr></thead><tbody>
<?php foreach ($rows as $item): $images = json_decode($item['image_urls'], true); $cover = is_array($images) && count($images) ? $images[0] : ''; $author = !empty($item['screen_name']) ? $item['screen_name'] : $item['author_name']; ?>
<tr class="gallery-row"><td><?php if ($cover !== ''): ?><a href="<?php echo htmlspecialchars($cover, ENT_QUOTES, 'UTF-8'); ?>" target="_blank" rel="noopener"><img class="gallery-thumb" src="<?php echo htmlspecialchars($cover, ENT_QUOTES, 'UTF-8'); ?>" alt="图集封面"></a><?php endif; ?></td><td><div class="gallery-title"><?php echo htmlspecialchars($item['title'], ENT_QUOTES, 'UTF-8'); ?></div><small class="text-muted">#<?php echo intval($item['id']); ?> · <?php echo is_array($images) ? count($images) : 0; ?> 张</small><div class="gallery-reason"><?php echo htmlspecialchars($item['review_reason'], ENT_QUOTES, 'UTF-8'); ?></div></td><td><?php echo htmlspecialchars((string)$author, ENT_QUOTES, 'UTF-8'); ?><br><small class="text-muted">UID <?php echo intval($item['author_uid']); ?></small></td><td class="gallery-tags"><?php echo htmlspecialchars(str_replace(',', ' · ', $item['tags']), ENT_QUOTES, 'UTF-8'); ?></td><td class="gallery-status"><span class="badge badge-secondary"><?php echo $labels[$item['status']] ?? '未知'; ?></span></td><td><?php echo intval($item['views']); ?></td><td><?php echo date('Y-m-d H:i', intval($item['created'])); ?></td><td>
<?php foreach (array('published'=>'通过并发布','rejected'=>'拒绝','hidden'=>'隐藏','submitted'=>'恢复待审') as $next=>$text): if ($item['status'] === 'deleted' || $item['status'] === $next) continue; ?><form class="gallery-action" method="post" action="galleryPost.php" onsubmit="return confirm('确认执行：<?php echo $text; ?>？');"><input type="hidden" name="csrf" value="<?php echo $csrf; ?>"><input type="hidden" name="id" value="<?php echo intval($item['id']); ?>"><input type="hidden" name="status" value="<?php echo $next; ?>"><button class="btn btn-sm <?php echo $next === 'published' ? 'btn-success' : ($next === 'rejected' ? 'btn-warning' : 'btn-outline-secondary'); ?>" type="submit"><?php echo $text; ?></button></form><?php endforeach; ?>
<?php if ($item['status'] !== 'deleted'): ?><form class="gallery-action" method="post" action="galleryPost.php" onsubmit="return confirm('软删除这条图集？记录和图片地址会保留，可审计但不再公开。');"><input type="hidden" name="csrf" value="<?php echo $csrf; ?>"><input type="hidden" name="id" value="<?php echo intval($item['id']); ?>"><input type="hidden" name="status" value="deleted"><button class="btn btn-sm btn-outline-danger" type="submit">软删除</button></form><?php endif; ?></td></tr>
<?php endforeach; if (!$rows): ?><tr><td colspan="8" class="gallery-empty">暂无图集</td></tr><?php endif; ?></tbody></table></div>
<?php $pages = max(1, (int)ceil($total / $limit)); if ($pages > 1): ?><nav><ul class="pagination"><?php for ($i=max(1,$page-2); $i<=min($pages,$page+2); $i++): ?><li class="page-item <?php echo $i===$page?'active':''; ?>"><a class="page-link" href="?status=<?php echo urlencode($status); ?>&tag=<?php echo urlencode($tag); ?>&page=<?php echo $i; ?>"><?php echo $i; ?></a></li><?php endfor; ?></ul></nav><?php endif; ?>
</div></div></div></div></div></body></html>
