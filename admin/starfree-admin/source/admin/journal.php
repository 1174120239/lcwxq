<?php
require_once __DIR__ . '/session.php';
if (empty($_SESSION['loginadmin'])) { header('Location: login.php'); exit; }
include_once 'connect.php';

function journal_h($value) { return htmlspecialchars((string)$value, ENT_QUOTES, 'UTF-8'); }
if (empty($_SESSION['journal_csrf'])) $_SESSION['journal_csrf'] = bin2hex(random_bytes(24));
$message = '';
if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $csrf = (string)($_POST['csrf'] ?? '');
    if ($csrf === '' || !hash_equals($_SESSION['journal_csrf'], $csrf)) {
        $message = '页面已过期，请刷新后重试';
    } else {
        $action = (string)($_POST['action'] ?? '');
        if ($action === 'save') {
            $id = (int)($_POST['id'] ?? 0);
            $name = trim((string)($_POST['name'] ?? ''));
            $slug = trim((string)($_POST['slug'] ?? ''));
            $description = trim((string)($_POST['description'] ?? ''));
            $tags = trim((string)($_POST['tags'] ?? ''));
            $cover = trim((string)($_POST['cover_url'] ?? ''));
            $banner = trim((string)($_POST['banner_url'] ?? ''));
            $theme = trim((string)($_POST['theme'] ?? 'editorial'));
            $sort = (int)($_POST['sort_order'] ?? 0);
            $featured = isset($_POST['featured']) ? 1 : 0;
            $hot = (int)($_POST['hot_weight'] ?? 0);
            if ($name === '' || mb_strlen($name) > 80) $message = '刊物名称不能为空且不能超过 80 字';
            else {
                if ($slug === '') $slug = 'journal-' . time();
                if ($id > 0) {
                    $stmt = $connect->prepare('UPDATE starfree_journals SET name=?,slug=?,description=?,tags=?,cover_url=?,banner_url=?,theme=?,sort_order=?,featured=?,hot_weight=?,modified=? WHERE id=?');
                    $now = time(); $stmt->bind_param('sssssssiiiii', $name,$slug,$description,$tags,$cover,$banner,$theme,$sort,$featured,$hot,$now,$id);
                } else {
                    $stmt = $connect->prepare('INSERT INTO starfree_journals (name,slug,description,tags,cover_url,banner_url,theme,status,sort_order,featured,hot_weight,created_by,created,modified) VALUES (?,?,?,?,?,?,?,1,?,?,?,?,?,?,?)');
                    $now = time(); $adminUid = 0; $stmt->bind_param('sssssssiiiiii', $name,$slug,$description,$tags,$cover,$banner,$theme,$sort,$featured,$hot,$adminUid,$now,$now);
                }
                if ($stmt && $stmt->execute()) $message = '刊物已保存'; else $message = '保存失败，请确认 017 迁移已执行';
                if ($stmt) $stmt->close();
            }
        } elseif ($action === 'article_status') {
            $articleId = (int)($_POST['article_id'] ?? 0);
            $status = (string)($_POST['status'] ?? '');
            if ($articleId > 0 && in_array($status, array('published','rejected','hidden'), true)) {
                $stmt = $connect->prepare("UPDATE starfree_journal_articles SET status=?, review_reason=?, modified=?, published_at=? WHERE id=?");
                $reason = trim((string)($_POST['reason'] ?? '')); $now = time(); $publishedAt = $status === 'published' ? $now : 0;
                $stmt->bind_param('ssiii', $status,$reason,$now,$publishedAt,$articleId); $message = ($stmt->execute() ? '文章状态已更新' : '文章状态更新失败'); $stmt->close();
            }
        }
    }
}
$edit = array('id'=>0,'name'=>'','slug'=>'','description'=>'','tags'=>'','cover_url'=>'','banner_url'=>'','theme'=>'editorial','sort_order'=>0,'featured'=>0,'hot_weight'=>0);
if (isset($_GET['edit'])) { $id = (int)$_GET['edit']; $stmt = $connect->prepare('SELECT id,name,slug,description,tags,cover_url,banner_url,theme,sort_order,featured,hot_weight FROM starfree_journals WHERE id=?'); $stmt->bind_param('i',$id); $stmt->execute(); $row = $stmt->get_result()->fetch_assoc(); if ($row) $edit = array_merge($edit,$row); $stmt->close(); }
$journals = array(); $result = $connect->query('SELECT id,name,description,tags,cover_url,banner_url,status,sort_order,featured,hot_weight,(SELECT COUNT(*) FROM starfree_journal_articles a WHERE a.journal_id=j.id AND a.status="published") article_count FROM starfree_journals j ORDER BY featured DESC,sort_order DESC,modified DESC,id DESC'); if ($result) while ($row = $result->fetch_assoc()) $journals[] = $row;
$articles = array(); $result = $connect->query("SELECT a.id,a.title,a.status,a.created,j.name journal_name FROM starfree_journal_articles a JOIN starfree_journals j ON j.id=a.journal_id WHERE a.status IN ('submitted','rejected') ORDER BY a.modified DESC,a.id DESC LIMIT 50"); if ($result) while ($row = $result->fetch_assoc()) $articles[] = $row;
include_once 'Menu.php';
?>
<div class="row"><div class="col-lg-12"><div class="card"><div class="card-body">
<h4 class="header-title mb-2">见字 · 刊物管理</h4>
<p class="text-muted">刊物是独立于普通帖子的内容体系。封面图和横幅图填写已上传图片地址；排序值越大越靠前，推荐和热门权重由编辑部控制。</p>
<?php if ($message !== ''): ?><div class="alert alert-info"><?php echo journal_h($message); ?></div><?php endif; ?>
<form method="post" class="mb-4"><input type="hidden" name="csrf" value="<?php echo journal_h($_SESSION['journal_csrf']); ?>"><input type="hidden" name="action" value="save"><input type="hidden" name="id" value="<?php echo (int)$edit['id']; ?>">
<div class="row"><div class="col-md-6 form-group"><label>刊物名称</label><input class="form-control" name="name" maxlength="80" required value="<?php echo journal_h($edit['name']); ?>"></div><div class="col-md-6 form-group"><label>Slug</label><input class="form-control" name="slug" maxlength="100" value="<?php echo journal_h($edit['slug']); ?>"></div></div>
<div class="form-group"><label>简介</label><textarea class="form-control" name="description" maxlength="500" rows="2"><?php echo journal_h($edit['description']); ?></textarea></div>
<div class="form-group"><label>标签（逗号分隔）</label><input class="form-control" name="tags" maxlength="500" value="<?php echo journal_h($edit['tags']); ?>"></div>
<div class="row"><div class="col-md-6 form-group"><label>封面图地址</label><input class="form-control" type="url" name="cover_url" maxlength="500" value="<?php echo journal_h($edit['cover_url']); ?>"></div><div class="col-md-6 form-group"><label>横幅图地址</label><input class="form-control" type="url" name="banner_url" maxlength="500" value="<?php echo journal_h($edit['banner_url']); ?>"></div></div>
<div class="row"><div class="col-md-3 form-group"><label>排序</label><input class="form-control" type="number" name="sort_order" value="<?php echo (int)$edit['sort_order']; ?>"></div><div class="col-md-3 form-group"><label>热门权重</label><input class="form-control" type="number" name="hot_weight" value="<?php echo (int)$edit['hot_weight']; ?>"></div><div class="col-md-3 form-group"><label>主题</label><select class="form-control" name="theme"><option value="editorial">编辑部</option><option value="paper">杂志米白</option><option value="night">深夜黑</option></select></div><div class="col-md-3 form-group"><label class="mt-4"><input type="checkbox" name="featured" <?php echo $edit['featured'] ? 'checked' : ''; ?>> 推荐刊物</label></div></div>
<button class="btn btn-success" type="submit"><?php echo $edit['id'] ? '保存刊物' : '新增刊物'; ?></button><?php if ($edit['id']): ?><a class="btn btn-light ml-2" href="journal.php">新建</a><?php endif; ?></form>
<h5 class="mt-4">刊物列表</h5><div class="table-responsive"><table class="table table-striped"><thead><tr><th>刊物</th><th>标签</th><th>文章</th><th>推荐/排序/热门</th><th>操作</th></tr></thead><tbody><?php foreach ($journals as $row): ?><tr><td><strong><?php echo journal_h($row['name']); ?></strong><br><small><?php echo journal_h($row['description']); ?></small></td><td><?php echo journal_h($row['tags']); ?></td><td><?php echo (int)$row['article_count']; ?></td><td><?php echo $row['featured'] ? '推荐' : '—'; ?> / <?php echo (int)$row['sort_order']; ?> / <?php echo (int)$row['hot_weight']; ?></td><td><a class="btn btn-sm btn-outline-primary" href="journal.php?edit=<?php echo (int)$row['id']; ?>">编辑</a></td></tr><?php endforeach; ?></tbody></table></div>
<h5 class="mt-4">投稿审核</h5><div class="table-responsive"><table class="table table-striped"><thead><tr><th>文章</th><th>刊物</th><th>状态</th><th>操作</th></tr></thead><tbody><?php foreach ($articles as $row): ?><tr><td><?php echo journal_h($row['title']); ?></td><td><?php echo journal_h($row['journal_name']); ?></td><td><?php echo journal_h($row['status']); ?></td><td><form method="post" class="d-inline"><input type="hidden" name="csrf" value="<?php echo journal_h($_SESSION['journal_csrf']); ?>"><input type="hidden" name="action" value="article_status"><input type="hidden" name="article_id" value="<?php echo (int)$row['id']; ?>"><button class="btn btn-sm btn-success" name="status" value="published">发布</button><button class="btn btn-sm btn-outline-danger ml-1" name="status" value="rejected">退回</button></form></td></tr><?php endforeach; ?></tbody></table></div>
</div></div></div></div><?php include_once 'Footer.php'; ?>
