<?php
require_once __DIR__ . '/session.php';
include_once 'Menu.php';
if (empty($_SESSION['ai_moderation_csrf'])) {
    $_SESSION['ai_moderation_csrf'] = bin2hex(random_bytes(24));
}
function ai_h($value) { return htmlspecialchars((string)$value, ENT_QUOTES, 'UTF-8'); }
$config = array('enabled'=>0,'provider'=>'deepseek','api_url'=>'https://api.deepseek.com/chat/completions','api_key'=>'','model'=>'deepseek-chat','custom_prompt'=>'');
$result = mysqli_query($connect, "SELECT enabled,provider,api_url,api_key,model,custom_prompt FROM starfree_ai_moderation_config WHERE id=1 LIMIT 1");
if ($result && ($row = mysqli_fetch_assoc($result))) $config = array_merge($config, $row);
?>
<div class="row"><div class="col-lg-12"><div class="card"><div class="card-body">
<h4 class="header-title mb-3">AI 动态审核</h4>
<p class="text-muted">默认使用 DeepSeek。模型通过的动态自动公开；风险、超时或无法解析的结果进入动态举报审核，由审核员或超级管理员处理。内置的违法、对立和学生隐私规则不可被补充提示词覆盖。</p>
<form action="aiModerationPost.php" method="post">
<input type="hidden" name="csrf" value="<?php echo ai_h($_SESSION['ai_moderation_csrf']);?>">
<div class="form-group mb-3"><label>启用 AI 审核</label><input type="checkbox" name="enabled" id="ai_enabled" value="1" data-switch="success" <?php echo intval($config['enabled'])===1?'checked':'';?>><label style="display:block" for="ai_enabled" data-on-label="开启" data-off-label="关闭"></label></div>
<div class="form-row"><div class="form-group col-md-6"><label>服务商</label><input class="form-control" value="DeepSeek" disabled><input type="hidden" name="provider" value="deepseek"></div><div class="form-group col-md-6"><label>模型</label><input class="form-control" name="model" maxlength="80" required value="<?php echo ai_h($config['model']);?>"></div></div>
<div class="form-group"><label>API 地址</label><input class="form-control" value="https://api.deepseek.com/chat/completions" disabled><small class="form-text text-muted">为降低服务端请求风险，审核仅连接 DeepSeek 官方接口。</small></div>
<div class="form-group"><label>API Key</label><input class="form-control" type="password" name="api_key" maxlength="512" autocomplete="new-password" placeholder="留空保持不变"><small class="form-text text-muted">当前：<?php echo $config['api_key']===''?'未配置':'已配置';?>。密钥不会回显。</small></div>
<div class="form-group"><label>补充审核提示词</label><textarea class="form-control" name="custom_prompt" maxlength="2000" rows="7" placeholder="例如：禁止发布课程群二维码和代课广告"><?php echo ai_h($config['custom_prompt']);?></textarea></div>
<button class="btn btn-primary" type="submit">保存设置</button>
</form></div></div></div></div>
<?php include_once 'Footer.php'; ?>
