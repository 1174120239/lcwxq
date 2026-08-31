<?php
session_start();

include_once 'Menu.php';
$sql = "select * from ".$db_prefix."_admin_update order by id desc";
$contents = mysqli_query($connect, $sql);
$wgtDir = getenv('LCXQY_WGT_DIR');
if (!$wgtDir) $wgtDir = '/opt/starfree/files/static/app-updates';
$wgtManifest = null;
$wgtManifestPath = rtrim($wgtDir, '/\\') . DIRECTORY_SEPARATOR . 'update.json';
if (is_file($wgtManifestPath)) {
    $wgtManifestData = json_decode((string)file_get_contents($wgtManifestPath), true);
    if (is_array($wgtManifestData)) $wgtManifest = $wgtManifestData;
}


?>

<link href="<?php echo $ADMIN_PATH;?>/assets/css/vendor/dataTables.bootstrap4.css" rel="stylesheet" type="text/css"/>
<link href="<?php echo $ADMIN_PATH;?>/assets/css/vendor/responsive.bootstrap4.css" rel="stylesheet" type="text/css"/>
<link href="<?php echo $ADMIN_PATH;?>/assets/css/vendor/buttons.bootstrap4.css" rel="stylesheet" type="text/css"/>
<link href="<?php echo $ADMIN_PATH;?>/assets/css/vendor/select.bootstrap4.css" rel="stylesheet" type="text/css"/>

<div class="row">
    <div class="col-12">
        <div class="card">
            <div class="card-body">
                <h4 class="header-title mb-3">版本管理<a class="fabu" href="updateAdd.php">
                        <button type="button" class="btn btn-success2 btn-sm btn-rounded right_10">
                            <i class="dripicons-plus"></i> 添加新版本
                        </button>
                    </a></h4>
                <?php if ($wgtManifest && !empty($wgtManifest['wgtUrl'])): ?>
                    <div class="alert alert-info py-2">
                        当前安卓 WGT：版本 <?php echo htmlspecialchars((string)($wgtManifest['version'] ?? ''), ENT_QUOTES, 'UTF-8'); ?>
                        （<?php echo (int)($wgtManifest['versionCode'] ?? 0); ?>）
                        <a href="<?php echo htmlspecialchars((string)$wgtManifest['wgtUrl'], ENT_QUOTES, 'UTF-8'); ?>" target="_blank" rel="noopener">查看文件</a>
                        <span class="ml-2">来源：<?php echo empty($wgtManifest['sha256']) ? '直链' : '后台上传'; ?>；清单地址：/app-updates/update.json</span>
                    </div>
                <?php else: ?>
                    <div class="alert alert-secondary py-2">尚未发布安卓 WGT。新增版本时可上传文件或填写 HTTPS 直链，下载页的 APK 地址仍按原有配置同步。</div>
                <?php endif; ?>
                
                <table id="basic-update" class="table dt-responsive nowrap" width="100%">
                    <thead>
                    <tr>
                        <th>id</th>
                        <th>版本名</th>
                        <th>版本号</th>
                        <th>描述</th>
                        <th>下载链接</th>
                        <th>WGT</th>
                        <th>类型</th>
                        <th style="width: 125px;">操作</th>
                    </tr>
                    </thead>

                    <tbody>
                    <?php
                    while ($articledata = mysqli_fetch_array($contents)) {
                        ?>
                        <tr>
                            <td><?php echo $articledata['id'] ?></td>
                            <td><?php echo $articledata['version'] ?></td>
                            <td><?php echo $articledata['versionCode'] ?></td>
                            <td><?php echo $articledata['versionIntro'] ?></td>
                             <td>
                                <?php echo $articledata['versionUrl'] ?>
                            </td>
                            <td>
                                <?php if ($wgtManifest && (int)($wgtManifest['versionCode'] ?? 0) === (int)$articledata['versionCode'] && !empty($wgtManifest['wgtUrl'])): ?>
                                    <a href="<?php echo htmlspecialchars((string)$wgtManifest['wgtUrl'], ENT_QUOTES, 'UTF-8'); ?>" target="_blank" rel="noopener">已发布</a>
                                <?php else: ?>
                                    <span class="text-muted">-</span>
                                <?php endif; ?>
                            </td>
                            <td>
                                 <h5>
                                    <?php if ($articledata['force']== 0) { ?>
                                    <span class="badge badge-success-lighten">普通更新</span>
                                    <?php } else { ?>
                                    <span class="badge badge-info-lighten">强制更新</span>
                                    <?php }?>
                                </h5>
                                
                            </td>
                            <td>
                                <a href="javascript:del(<?php echo $articledata['id']; ?>);">
                                    <button style="white-space: nowrap;" type="button"
                                            class="btn btn-danger btn-rounded">
                                        <i class="mdi mdi-delete-empty mr-1"></i>删除
                                    </button>
                                </a>
                            </td>
                        </tr>
                        <?php
                    }
                    ?>
                    </tbody>
                </table>

            </div>  
        </div>  
    </div> 
</div>


<script>
    function del(id) {
        if (confirm('您确认要删除id为' + id + '的版本吗？')) {
            location.href = 'updateDel.php?id=' + id +'&status=one';
        }
    }
</script>


<?php
include_once 'Footer.php';
?>

<script src="<?php echo $ADMIN_PATH;?>/assets/js/vendor/jquery.dataTables.min.js"></script>
<script src="<?php echo $ADMIN_PATH;?>/assets/js/vendor/dataTables.bootstrap4.js"></script>
<script src="<?php echo $ADMIN_PATH;?>/assets/js/vendor/dataTables.responsive.min.js"></script>
<script src="<?php echo $ADMIN_PATH;?>/assets/js/vendor/responsive.bootstrap4.min.js"></script>
<script src="<?php echo $ADMIN_PATH;?>/assets/js/vendor/dataTables.buttons.min.js"></script>
<script src="<?php echo $ADMIN_PATH;?>/assets/js/vendor/buttons.bootstrap4.min.js"></script>
<script src="<?php echo $ADMIN_PATH;?>/assets/js/vendor/buttons.html5.min.js"></script>
<script src="<?php echo $ADMIN_PATH;?>/assets/js/vendor/buttons.flash.min.js"></script>
<script src="<?php echo $ADMIN_PATH;?>/assets/js/vendor/buttons.print.min.js"></script>
<script src="<?php echo $ADMIN_PATH;?>/assets/js/vendor/dataTables.keyTable.min.js"></script>
<script src="<?php echo $ADMIN_PATH;?>/assets/js/vendor/dataTables.select.min.js"></script>
<script src="<?php echo $ADMIN_PATH;?>/assets/js/pages/demo.datatable-init.js"></script>



</body>
</html>
