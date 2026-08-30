<?php
require_once __DIR__ . '/session.php';
include_once 'Menu.php';

function download_site_h($value) {
    return htmlspecialchars((string)$value, ENT_QUOTES, 'UTF-8');
}

$settings = array(
    'hero_kicker' => '聊城一中 · 校园社区',
    'hero_title' => '让校园里的每一次连接都有回响',
    'hero_intro' => '在这里，分享动态、发现同好、互相帮助。聊城一中论坛，把真实的校园生活留在同学们共同的空间里。',
    'web_url' => 'https://prev.lcxqy.cn/',
    'cors_origins' => "https://prev.lcxqy.cn\nhttps://lcyz.site\nhttps://www.lcyz.site"
);
$table = $db_prefix . '_download_site_config';
$query = $connect->prepare('SELECT hero_kicker,hero_title,hero_intro,web_url,cors_origins FROM ' . $table . ' WHERE id=1 LIMIT 1');
$tableReady = false;
if ($query && $query->execute()) {
    $tableReady = true;
    $row = $query->get_result()->fetch_assoc();
    if ($row) $settings = array_merge($settings, $row);
}
if ($query) $query->close();
if (empty($_SESSION['download_site_csrf'])) {
    $_SESSION['download_site_csrf'] = bin2hex(random_bytes(24));
}
?>
<div class="row">
  <div class="col-lg-12">
    <div class="card">
      <div class="card-body">
        <h4 class="header-title mb-2">下载页设置</h4>
        <p class="text-muted mb-4">修改独立下载/介绍站的标题、简介、网页版地址和 PHP 接口跨域白名单。版本日志与 Android 下载地址仍由原有版本管理、邀请分享设置维护；新增域名后，若访问的是 api.lcxqy.cn，还需在后端环境变量 CORS_ALLOWED_ORIGIN_PATTERNS 中加入该来源。</p>
        <?php if (!$tableReady): ?><div class="alert alert-warning">配置表尚未就绪，请先执行迁移 016。</div><?php endif; ?>
        <form class="needs-validation" action="downloadSitePost.php" method="post" novalidate>
          <input type="hidden" name="csrf" value="<?php echo download_site_h($_SESSION['download_site_csrf']); ?>">
          <div class="form-group mb-3"><label for="heroKicker">页面副标题</label><input id="heroKicker" name="hero_kicker" class="form-control" maxlength="120" required value="<?php echo download_site_h($settings['hero_kicker']); ?>"></div>
          <div class="form-group mb-3"><label for="heroTitle">页面主标题</label><input id="heroTitle" name="hero_title" class="form-control" maxlength="255" required value="<?php echo download_site_h($settings['hero_title']); ?>"></div>
          <div class="form-group mb-3"><label for="heroIntro">页面简介</label><textarea id="heroIntro" name="hero_intro" class="form-control" rows="4" maxlength="2000" required><?php echo download_site_h($settings['hero_intro']); ?></textarea></div>
          <div class="form-group mb-3"><label for="webUrl">网页版地址</label><input id="webUrl" name="web_url" class="form-control" type="url" maxlength="1000" required value="<?php echo download_site_h($settings['web_url']); ?>"><small class="form-text text-muted">填写完整的 http/https 地址，下载页的“网页版”按钮会使用它。</small></div>
          <div class="form-group mb-3"><label for="corsOrigins">跨域白名单</label><textarea id="corsOrigins" name="cors_origins" class="form-control" rows="5" maxlength="4000" required><?php echo download_site_h($settings['cors_origins']); ?></textarea><small class="form-text text-muted">每行一个来源，例如 https://lcyz.site。只能填写 http/https 的域名来源，不要加路径、参数、账号密码或 *。</small></div>
          <button class="btn btn-success" type="submit">保存下载页设置</button>
        </form>
      </div>
    </div>
  </div>
</div>
<?php include_once 'Footer.php'; ?>
