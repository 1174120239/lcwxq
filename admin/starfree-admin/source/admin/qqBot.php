<?php
session_start();
include_once 'Menu.php';

function bot_config_defaults() {
    return array(
        'enabled' => '0',
        'bot_secret' => '',
        'bot_public_base_url' => '',
        'h5_base_url' => 'https://prev.lcxqy.cn',
        'forum_register_url' => 'https://prev.lcxqy.cn/#/pages/user/register',
        'deepseek_api_key' => '',
        'deepseek_api_base' => 'https://api.deepseek.com',
        'deepseek_model' => 'deepseek-chat',
        'sync_interval_seconds' => '45',
        'sync_max_images' => '3',
        'sync_summary_length' => '120',
        'tool_add_space' => '1',
        'tool_update_profile' => '1',
        'tool_status' => '1',
        'tool_signin' => '1'
    );
}

function bot_h($value) {
    return htmlspecialchars((string)$value, ENT_QUOTES, 'UTF-8');
}

$botConfig = bot_config_defaults();
$result = mysqli_query($connect, "SELECT config_key,config_value FROM lcxqy_bot_config");
if ($result) {
    while ($row = mysqli_fetch_assoc($result)) {
        $botConfig[$row['config_key']] = $row['config_value'];
    }
}

$groups = array();
$groupResult = mysqli_query($connect, "SELECT id,platform,group_id,group_name,unified_msg_origin,enabled,"
    . "cursor_space_id,max_images,summary_length,last_success_at,last_error "
    . "FROM lcxqy_bot_group_sync ORDER BY id DESC");
if ($groupResult) {
    while ($row = mysqli_fetch_assoc($groupResult)) {
        $groups[] = $row;
    }
}
?>
<div class="row">
    <div class="col-lg-12">
        <div class="card">
            <div class="card-body">
                <h4 class="header-title mb-3">QQ Bot设置</h4>
                <p class="text-muted">
                    这里配置 NapCat / AstrBot 动态助手。Bot 只面向动态，不开放帖子或文章能力。
                    Bot Secret 由部署配置托管，后台不显示或修改密钥原文。
                </p>
                <form class="needs-validation" action="qqBotPost.php" method="post" novalidate>
                    <div class="row">
                        <div class="col-md-6">
                            <h5 class="mb-3">基础配置</h5>
                            <div class="form-group mb-3">
                                <label>启用 Bot</label>
                                <input type="checkbox" name="enabled" id="enabled" value="1" data-switch="success" <?php echo $botConfig['enabled'] === '1' ? 'checked' : ''; ?>>
                                <label style="display:block;" for="enabled" data-on-label="开启" data-off-label="关闭"></label>
                            </div>
                            <div class="form-group mb-3">
                                <label for="bot_secret_status">Bot Secret</label>
                                <input class="form-control" type="text" id="bot_secret_status" readonly
                                       value="<?php echo $botConfig['bot_secret'] === '' ? '未配置' : '已由部署托管'; ?>">
                                <small class="form-text text-muted">轮换 Secret 需同步更新后端和 AstrBot，不在此页操作。</small>
                            </div>
                            <div class="form-group mb-3">
                                <label for="bot_public_base_url">绑定页公网地址</label>
                                <input name="bot_public_base_url" class="form-control" type="text" id="bot_public_base_url"
                                       placeholder="例如 https://api.lcxqy.cn" value="<?php echo bot_h($botConfig['bot_public_base_url']); ?>">
                            </div>
                            <div class="form-group mb-3">
                                <label for="h5_base_url">动态 H5 地址</label>
                                <input name="h5_base_url" class="form-control" type="text" id="h5_base_url"
                                       value="<?php echo bot_h($botConfig['h5_base_url']); ?>">
                            </div>
                            <div class="form-group mb-3">
                                <label for="forum_register_url">论坛注册地址</label>
                                <input name="forum_register_url" class="form-control" type="text" id="forum_register_url"
                                       value="<?php echo bot_h($botConfig['forum_register_url']); ?>">
                            </div>
                        </div>
                        <div class="col-md-6">
                            <h5 class="mb-3">DeepSeek 与工具</h5>
                            <div class="form-group mb-3">
                                <label for="deepseek_api_key">DeepSeek API Key</label>
                                <input name="deepseek_api_key" class="form-control" type="password" id="deepseek_api_key"
                                       value="<?php echo bot_h($botConfig['deepseek_api_key']); ?>">
                            </div>
                            <div class="form-group mb-3">
                                <label for="deepseek_api_base">DeepSeek API Base</label>
                                <input name="deepseek_api_base" class="form-control" type="text" id="deepseek_api_base"
                                       value="<?php echo bot_h($botConfig['deepseek_api_base']); ?>">
                            </div>
                            <div class="form-group mb-3">
                                <label for="deepseek_model">模型</label>
                                <input name="deepseek_model" class="form-control" type="text" id="deepseek_model"
                                       value="<?php echo bot_h($botConfig['deepseek_model']); ?>">
                            </div>
                            <div class="row">
                                <?php
                                $tools = array(
                                    'tool_add_space' => '发动态',
                                    'tool_update_profile' => '修改资料',
                                    'tool_status' => '查询积分/签到状态',
                                    'tool_signin' => '签到'
                                );
                                foreach ($tools as $key => $label) {
                                    echo '<div class="col-md-6"><div class="form-group mb-3">';
                                    echo '<label>'.bot_h($label).'</label>';
                                    echo '<input type="checkbox" name="'.bot_h($key).'" id="'.bot_h($key).'" value="1" data-switch="success" '
                                        .($botConfig[$key] === '1' ? 'checked' : '').'>';
                                    echo '<label style="display:block;" for="'.bot_h($key).'" data-on-label="开" data-off-label="关"></label>';
                                    echo '</div></div>';
                                }
                                ?>
                            </div>
                            <div class="row">
                                <div class="col-md-4">
                                    <div class="form-group mb-3">
                                        <label for="sync_interval_seconds">同步间隔秒</label>
                                        <input name="sync_interval_seconds" class="form-control" type="number" min="10" max="3600"
                                               value="<?php echo bot_h($botConfig['sync_interval_seconds']); ?>">
                                    </div>
                                </div>
                                <div class="col-md-4">
                                    <div class="form-group mb-3">
                                        <label for="sync_max_images">最多图片</label>
                                        <input name="sync_max_images" class="form-control" type="number" min="0" max="9"
                                               value="<?php echo bot_h($botConfig['sync_max_images']); ?>">
                                    </div>
                                </div>
                                <div class="col-md-4">
                                    <div class="form-group mb-3">
                                        <label for="sync_summary_length">摘要字数</label>
                                        <input name="sync_summary_length" class="form-control" type="number" min="20" max="500"
                                               value="<?php echo bot_h($botConfig['sync_summary_length']); ?>">
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>

                    <hr>
                    <h5 class="mb-3">群动态同步</h5>
                    <p class="text-muted">只需填写 QQ 群号；群名可选。平台、消息来源标识和同步游标由系统自动维护。</p>
                    <div class="table-responsive">
                        <table class="table table-centered table-sm">
                            <thead>
                            <tr>
                                <th>启用</th>
                                <th>群号</th>
                                <th>群名</th>
                                <th>游标</th>
                                <th>最多图片</th>
                                <th>摘要字数</th>
                                <th>最近错误</th>
                            </tr>
                            </thead>
                            <tbody>
                            <?php foreach ($groups as $group) { ?>
                                <tr>
                                    <td>
                                        <input type="hidden" name="group_ids[]" value="<?php echo intval($group['id']); ?>">
                                        <input type="checkbox" name="group_enabled[<?php echo intval($group['id']); ?>]" value="1" <?php echo intval($group['enabled']) === 1 ? 'checked' : ''; ?>>
                                    </td>
                                    <td><input class="form-control form-control-sm" name="group_id[<?php echo intval($group['id']); ?>]" value="<?php echo bot_h($group['group_id']); ?>" inputmode="numeric" required></td>
                                    <td><input class="form-control form-control-sm" name="group_name[<?php echo intval($group['id']); ?>]" value="<?php echo bot_h($group['group_name']); ?>"></td>
                                    <td><?php echo intval($group['cursor_space_id']); ?></td>
                                    <td><input class="form-control form-control-sm" type="number" min="0" max="9" name="group_max_images[<?php echo intval($group['id']); ?>]" value="<?php echo intval($group['max_images']); ?>"></td>
                                    <td><input class="form-control form-control-sm" type="number" min="20" max="500" name="group_summary_length[<?php echo intval($group['id']); ?>]" value="<?php echo intval($group['summary_length']); ?>"></td>
                                    <td><?php echo bot_h($group['last_error']); ?></td>
                                </tr>
                            <?php } ?>
                            <tr>
                                <td>新增</td>
                                <td><input class="form-control form-control-sm" name="new_group_id" placeholder="必填：QQ群号" inputmode="numeric"></td>
                                <td><input class="form-control form-control-sm" name="new_group_name" placeholder="选填：便于识别"></td>
                                <td>0</td>
                                <td><input class="form-control form-control-sm" type="number" min="0" max="9" name="new_max_images" value="<?php echo bot_h($botConfig['sync_max_images']); ?>"></td>
                                <td><input class="form-control form-control-sm" type="number" min="20" max="500" name="new_summary_length" value="<?php echo bot_h($botConfig['sync_summary_length']); ?>"></td>
                                <td></td>
                            </tr>
                            </tbody>
                        </table>
                    </div>
                    <div class="form-group mb-3 text_right">
                        <button class="btn btn-success" type="submit">保存 QQ Bot 设置</button>
                    </div>
                </form>
            </div>
        </div>
    </div>
</div>
<?php include_once 'Footer.php'; ?>
</body>
</html>
