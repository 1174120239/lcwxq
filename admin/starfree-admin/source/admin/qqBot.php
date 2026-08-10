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
        'tool_signin' => '1',
        'chat_in_groups' => '1',
        'qzone_enabled' => '0',
        'qzone_publish_mode' => 'scheduled',
        'qzone_publish_time' => '20:30',
        'qzone_batch_limit' => '6',
        'qzone_summary_length' => '80',
        'qzone_include_source_images' => '1',
        'qzone_show_campus' => '1',
        'qzone_show_topics' => '1',
        'qzone_ugc_right' => '1',
        'qzone_title' => '聊一今日动态',
        'qzone_subtitle' => '校园里今天发生了什么',
        'qzone_footer' => '更多动态，来聊一看看',
        'qzone_post_text' => '今日校园动态',
        'qzone_background_color' => '#F4F7F5',
        'qzone_accent_color' => '#1E7258',
        'qzone_text_color' => '#18211E',
        'qzone_card_color' => '#FFFFFF',
        'qzone_background_image_url' => '',
        'qzone_cursor_space_id' => '0',
        'qzone_last_run_date' => '',
        'qzone_last_tid' => '',
        'qzone_last_success_at' => '',
        'qzone_last_error' => '',
        'qzone_publish_now_token' => '',
        'qzone_publish_now_handled_token' => ''
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
                    Bot Secret 可在此页设置；后台只显示是否已配置，不回显密钥原文。
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
                                <label for="bot_secret">Bot Secret</label>
                                <input name="bot_secret" class="form-control" type="password" id="bot_secret"
                                       minlength="16" maxlength="128" autocomplete="new-password"
                                       placeholder="留空保持不变；与 AstrBot 插件填写相同密码">
                                <small class="form-text text-muted">
                                    当前：<?php echo $botConfig['bot_secret'] === '' ? '未配置' : '已配置'; ?>。
                                    输入新密码才会更新，后台不会回显原密码。
                                </small>
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
                                <div class="col-md-6">
                                    <div class="form-group mb-3">
                                        <label>群聊普通对话</label>
                                        <input type="checkbox" name="chat_in_groups" id="chat_in_groups" value="1" data-switch="success" <?php echo $botConfig['chat_in_groups'] === '1' ? 'checked' : ''; ?>>
                                        <label style="display:block;" for="chat_in_groups" data-on-label="开启" data-off-label="关闭"></label>
                                        <small class="form-text text-muted">关闭后群里闲聊不回复；动态同步、引用动态评论和明确的论坛操作仍可用。</small>
                                    </div>
                                </div>
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
                    <h5 class="mb-3">QQ 空间同步</h5>
                    <p class="text-muted">
                        云云会把游标之后的公开动态各生成一张 P1-P9 编号图片，并通过本机 NapCat 个人 QQ 发布一条空间说说。
                        没有新动态时不会发布；发布成功后才推进游标。
                    </p>
                    <div class="row">
                        <div class="col-md-2">
                            <div class="form-group mb-3">
                                <label>启用 QQ 空间同步</label>
                                <input type="checkbox" name="qzone_enabled" id="qzone_enabled" value="1" data-switch="success" <?php echo $botConfig['qzone_enabled'] === '1' ? 'checked' : ''; ?>>
                                <label style="display:block;" for="qzone_enabled" data-on-label="开启" data-off-label="关闭"></label>
                            </div>
                        </div>
                        <div class="col-md-3">
                            <div class="form-group mb-3">
                                <label for="qzone_publish_mode">发布模式</label>
                                <select name="qzone_publish_mode" id="qzone_publish_mode" class="form-control">
                                    <option value="scheduled" <?php echo $botConfig['qzone_publish_mode'] === 'scheduled' ? 'selected' : ''; ?>>按时间发布</option>
                                    <option value="realtime" <?php echo $botConfig['qzone_publish_mode'] === 'realtime' ? 'selected' : ''; ?>>随时发布</option>
                                </select>
                                <small class="form-text text-muted">随时发布会在发现新动态后立即生成下一批。</small>
                            </div>
                        </div>
                        <div class="col-md-2">
                            <div class="form-group mb-3">
                                <label for="qzone_publish_time">每天发布时间</label>
                                <input name="qzone_publish_time" class="form-control" type="time" id="qzone_publish_time"
                                       value="<?php echo bot_h($botConfig['qzone_publish_time']); ?>" required>
                                <small class="form-text text-muted">北京时间；到点后下一轮轮询执行。</small>
                            </div>
                        </div>
                        <div class="col-md-2">
                            <div class="form-group mb-3">
                                <label for="qzone_batch_limit">每批动态数</label>
                                <input name="qzone_batch_limit" class="form-control" type="number" min="1" max="9"
                                       value="<?php echo bot_h($botConfig['qzone_batch_limit']); ?>">
                                <small class="form-text text-muted">一条动态一张图，单条说说最多 9 张。</small>
                            </div>
                        </div>
                        <div class="col-md-3">
                            <div class="form-group mb-3">
                                <label for="qzone_summary_length">每条摘要字数</label>
                                <input name="qzone_summary_length" class="form-control" type="number" min="20" max="200"
                                       value="<?php echo bot_h($botConfig['qzone_summary_length']); ?>">
                            </div>
                        </div>
                    </div>
                    <div class="row">
                        <div class="col-md-4">
                            <div class="form-group mb-3">
                                <label>图片内容</label><br>
                                <label class="mr-3"><input type="checkbox" name="qzone_include_source_images" value="1" <?php echo $botConfig['qzone_include_source_images'] === '1' ? 'checked' : ''; ?>> 使用动态首图</label>
                                <label class="mr-3"><input type="checkbox" name="qzone_show_campus" value="1" <?php echo $botConfig['qzone_show_campus'] === '1' ? 'checked' : ''; ?>> 显示校区</label>
                                <label><input type="checkbox" name="qzone_show_topics" value="1" <?php echo $botConfig['qzone_show_topics'] === '1' ? 'checked' : ''; ?>> 显示话题</label>
                            </div>
                        </div>
                        <div class="col-md-4">
                            <div class="form-group mb-3">
                                <label for="qzone_ugc_right">空间可见范围</label>
                                <select name="qzone_ugc_right" id="qzone_ugc_right" class="form-control">
                                    <?php
                                    $qzoneRights = array('1' => '所有人', '4' => 'QQ 好友', '64' => '仅自己');
                                    foreach ($qzoneRights as $value => $label) {
                                        echo '<option value="'.bot_h($value).'" '.($botConfig['qzone_ugc_right'] === $value ? 'selected' : '').'>'.bot_h($label).'</option>';
                                    }
                                    ?>
                                </select>
                            </div>
                        </div>
                        <div class="col-md-4">
                            <div class="form-group mb-3">
                                <label for="qzone_background_image_url">自定义背景图 URL</label>
                                <input name="qzone_background_image_url" class="form-control" type="url" id="qzone_background_image_url"
                                       placeholder="选填；留空使用背景色" value="<?php echo bot_h($botConfig['qzone_background_image_url']); ?>">
                            </div>
                        </div>
                    </div>
                    <div class="row">
                        <div class="col-md-4">
                            <div class="form-group mb-3">
                                <label for="qzone_title">图片标题</label>
                                <input name="qzone_title" class="form-control" maxlength="40" value="<?php echo bot_h($botConfig['qzone_title']); ?>">
                            </div>
                        </div>
                        <div class="col-md-4">
                            <div class="form-group mb-3">
                                <label for="qzone_subtitle">图片副标题</label>
                                <input name="qzone_subtitle" class="form-control" maxlength="80" value="<?php echo bot_h($botConfig['qzone_subtitle']); ?>">
                            </div>
                        </div>
                        <div class="col-md-4">
                            <div class="form-group mb-3">
                                <label for="qzone_footer">图片底部文案</label>
                                <input name="qzone_footer" class="form-control" maxlength="80" value="<?php echo bot_h($botConfig['qzone_footer']); ?>">
                            </div>
                        </div>
                    </div>
                    <div class="row">
                        <div class="col-md-6">
                            <div class="form-group mb-3">
                                <label for="qzone_post_text">空间简短正文</label>
                                <textarea name="qzone_post_text" id="qzone_post_text" class="form-control" rows="2" maxlength="120"><?php echo bot_h($botConfig['qzone_post_text']); ?></textarea>
                                <small class="form-text text-muted">只发送这里填写的文字，不会再自动追加 P 编号、作者和摘要。</small>
                            </div>
                        </div>
                        <div class="col-md-6">
                            <div class="row">
                                <?php
                                $qzoneColors = array(
                                    'qzone_background_color' => '背景色',
                                    'qzone_accent_color' => '强调色',
                                    'qzone_text_color' => '文字色',
                                    'qzone_card_color' => '卡片色'
                                );
                                foreach ($qzoneColors as $key => $label) {
                                    echo '<div class="col-md-3"><div class="form-group mb-3">';
                                    echo '<label for="'.bot_h($key).'">'.bot_h($label).'</label>';
                                    echo '<input name="'.bot_h($key).'" id="'.bot_h($key).'" class="form-control" type="color" value="'.bot_h($botConfig[$key]).'">';
                                    echo '</div></div>';
                                }
                                ?>
                            </div>
                        </div>
                    </div>
                    <div class="alert alert-secondary py-2">
                        当前游标：<?php echo intval($botConfig['qzone_cursor_space_id']); ?>；
                        最近发布：<?php echo bot_h($botConfig['qzone_last_success_at'] === '' ? '暂无' : $botConfig['qzone_last_success_at']); ?>；
                        最近 TID：<?php echo bot_h($botConfig['qzone_last_tid'] === '' ? '暂无' : $botConfig['qzone_last_tid']); ?>；
                        最近错误：<?php echo bot_h($botConfig['qzone_last_error'] === '' ? '无' : $botConfig['qzone_last_error']); ?>
                        ；立即发布任务：<?php echo ($botConfig['qzone_publish_now_token'] !== '' && $botConfig['qzone_publish_now_token'] !== $botConfig['qzone_publish_now_handled_token']) ? '等待执行' : '无'; ?>
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
                        <button class="btn btn-warning mr-2" type="submit" name="action" value="publish_now">立刻发布未同步动态</button>
                        <button class="btn btn-success" type="submit" name="action" value="save">保存 QQ Bot 设置</button>
                        <small class="form-text text-muted">只发布空间游标之后未发过的动态；不足批量上限时按实际数量发布。</small>
                    </div>
                </form>
            </div>
        </div>
    </div>
</div>
<?php include_once 'Footer.php'; ?>
</body>
</html>
