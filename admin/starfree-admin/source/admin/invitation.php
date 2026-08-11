<?php
session_start();
include_once 'Menu.php';

if (!$connect) {
    die("<div class='alert alert-danger'>数据库连接失败</div>");
}

$settings = array(
    'enabled' => 1,
    'reward_points' => 10,
    'reward_experience' => 20,
    'android_download_url' => '',
    'ios_download_url' => '',
);
$query = $connect->prepare(
    'SELECT enabled,reward_points,reward_experience,android_download_url,ios_download_url '
    . 'FROM lcxqy_invitation_config WHERE id=1 LIMIT 1'
);
if ($query && $query->execute()) {
    $row = $query->get_result()->fetch_assoc();
    if ($row) {
        $settings = array_merge($settings, $row);
    }
}
if ($query) {
    $query->close();
}

$stats = array('count' => 0, 'points' => 0, 'experience' => 0);
$statQuery = $connect->query(
    'SELECT COUNT(*) AS count,COALESCE(SUM(reward_points),0) AS points,'
    . 'COALESCE(SUM(reward_experience),0) AS experience FROM lcxqy_invitation_records'
);
if ($statQuery) {
    $stats = array_merge($stats, $statQuery->fetch_assoc());
}
$records = array();
$recordQuery = $connect->query(
    'SELECT r.inviter_uid,r.invitee_uid,r.reward_points,r.reward_experience,r.created_at,'
    . 'COALESCE(inviter.screenName,inviter.name) AS inviter_name,'
    . 'COALESCE(invitee.screenName,invitee.name) AS invitee_name '
    . 'FROM lcxqy_invitation_records r '
    . 'LEFT JOIN starfree_users inviter ON inviter.uid=r.inviter_uid '
    . 'LEFT JOIN starfree_users invitee ON invitee.uid=r.invitee_uid '
    . 'ORDER BY r.id DESC LIMIT 50'
);
if ($recordQuery) {
    while ($item = $recordQuery->fetch_assoc()) {
        $records[] = $item;
    }
}
if (empty($_SESSION['invitation_csrf'])) {
    $_SESSION['invitation_csrf'] = bin2hex(random_bytes(16));
}
?>
<div class="row">
    <div class="col-lg-12">
        <div class="card">
            <div class="card-body">
                <h4 class="header-title mb-2">邀请分享设置</h4>
                <p class="text-muted mb-4">注册成功后，每位新用户只会为邀请人发放一次积分和经验。</p>
                <form action="invitationPost.php" method="post">
                    <input type="hidden" name="csrf" value="<?php echo htmlspecialchars($_SESSION['invitation_csrf'], ENT_QUOTES, 'UTF-8'); ?>">
                    <div class="form-group mb-3">
                        <label for="invitationEnabled">启用邀请功能</label>
                        <input type="hidden" name="enabled" value="0">
                        <input id="invitationEnabled" type="checkbox" name="enabled" value="1" data-switch="success" <?php echo ((int)$settings['enabled'] === 1) ? 'checked' : ''; ?>>
                        <label for="invitationEnabled" data-on-label="打开" data-off-label="关闭"></label>
                    </div>
                    <div class="form-row">
                        <div class="form-group col-md-6">
                            <label for="rewardPoints">每次邀请奖励积分</label>
                            <input id="rewardPoints" name="reward_points" class="form-control" type="number" min="0" max="1000000" required value="<?php echo (int)$settings['reward_points']; ?>">
                        </div>
                        <div class="form-group col-md-6">
                            <label for="rewardExperience">每次邀请奖励经验</label>
                            <input id="rewardExperience" name="reward_experience" class="form-control" type="number" min="0" max="1000000" required value="<?php echo (int)$settings['reward_experience']; ?>">
                        </div>
                    </div>
                    <div class="form-group mb-3">
                        <label for="androidDownloadUrl">Android 下载地址</label>
                        <input id="androidDownloadUrl" name="android_download_url" class="form-control" type="url" maxlength="1000" placeholder="https://example.com/app.apk" value="<?php echo htmlspecialchars($settings['android_download_url'], ENT_QUOTES, 'UTF-8'); ?>">
                    </div>
                    <div class="form-group mb-3">
                        <label for="iosDownloadUrl">iOS 下载地址</label>
                        <input id="iosDownloadUrl" name="ios_download_url" class="form-control" type="url" maxlength="1000" placeholder="https://apps.apple.com/..." value="<?php echo htmlspecialchars($settings['ios_download_url'], ENT_QUOTES, 'UTF-8'); ?>">
                    </div>
                    <button class="btn btn-success" type="submit">保存邀请设置</button>
                </form>
            </div>
        </div>
    </div>
</div>
<div class="row">
    <div class="col-lg-4"><div class="card"><div class="card-body"><span class="text-muted">成功邀请</span><h3><?php echo (int)$stats['count']; ?></h3></div></div></div>
    <div class="col-lg-4"><div class="card"><div class="card-body"><span class="text-muted">已发放积分</span><h3><?php echo (int)$stats['points']; ?></h3></div></div></div>
    <div class="col-lg-4"><div class="card"><div class="card-body"><span class="text-muted">已发放经验</span><h3><?php echo (int)$stats['experience']; ?></h3></div></div></div>
</div>
<div class="card">
    <div class="card-body">
        <h4 class="header-title mb-3">最近邀请记录</h4>
        <div class="table-responsive">
            <table class="table table-centered table-striped mb-0">
                <thead><tr><th>邀请人</th><th>新用户</th><th>积分</th><th>经验</th><th>时间</th></tr></thead>
                <tbody>
                <?php if (empty($records)): ?><tr><td colspan="5" class="text-muted">暂无邀请记录</td></tr><?php endif; ?>
                <?php foreach ($records as $record): ?>
                    <tr>
                        <td><?php echo htmlspecialchars($record['inviter_name'] ?: ('UID '.$record['inviter_uid']), ENT_QUOTES, 'UTF-8'); ?></td>
                        <td><?php echo htmlspecialchars($record['invitee_name'] ?: ('UID '.$record['invitee_uid']), ENT_QUOTES, 'UTF-8'); ?></td>
                        <td><?php echo (int)$record['reward_points']; ?></td>
                        <td><?php echo (int)$record['reward_experience']; ?></td>
                        <td><?php echo htmlspecialchars($record['created_at'], ENT_QUOTES, 'UTF-8'); ?></td>
                    </tr>
                <?php endforeach; ?>
                </tbody>
            </table>
        </div>
    </div>
</div>
<?php include_once 'Footer.php'; ?>
