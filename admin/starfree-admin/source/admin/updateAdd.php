<?php
require_once __DIR__ . '/session.php';
include_once 'Menu.php';
if (empty($_SESSION['update_csrf'])) {
    $_SESSION['update_csrf'] = bin2hex(random_bytes(24));
}
?>
<div class="row">

    <div class="col-lg-12">
        <div class="card">
            <div class="card-body">
                <h4 class="header-title mb-3 size_18">新增版本</h4>

                <form class="needs-validation" action="updateAddPost.php" method="post" enctype="multipart/form-data" onsubmit="return check()"
                      novalidate>
                    <input type="hidden" name="csrf" value="<?php echo htmlspecialchars($_SESSION['update_csrf'], ENT_QUOTES, 'UTF-8'); ?>">
                    <div class="form-group ">
                        <label for="validationCustom01">版本名称</label><span class="badge badge-success-lighten"style="font-size: 0.8rem;">比如：1.0.1</span>
                        <input type="text" class="form-control" id="validationCustom01" placeholder="请输入版本名称"
                               name="version" required>
                    </div>
                    <div class="form-group ">
                        <label for="validationCustom01">版本号</label><small>（只有纯数字,没有任何小数点和其他符号，请勿跟版本名称混淆）</small><span class="badge badge-success-lighten"style="font-size: 0.8rem;">版本号务必要比上版本的大 比如：101</span>
                        <input type="number" class="form-control" id="validationCustom01" placeholder="请输入版本号"
                               name="versionCode" required>
                    </div>
                    <div class="form-group mb-3">
                      <label for="notice">更新描述</label><span class="badge badge-success-lighten"style="font-size: 0.8rem;">支持html</span>
                      <textarea id="notice" class="form-control" name="versionIntro" rows="6" placeholder="请输入更新描述"></textarea>
                    </div>
                    <div class="form-group ">
                        <label for="validationCustom01">下载链接</label>
                        <input type="url" class="form-control" id="validationCustom01" placeholder="请输入下载链接"
                               name="versionUrl" required>
                    </div>
                    <div class="form-group ">
                        <label>更新类型</label>
                            <select class="form-control" id="dynamic-type" name="force">
                                    <option value="1" selected>强制更新</option>
                                    <option value="0">普通更新</option>
                            </select>
                    </div>
                    <div class="form-group ">
                        <label for="wgtFile">Android WGT 热更新包（可选）</label>
                        <input type="file" class="form-control-file" id="wgtFile" name="wgtFile" accept=".wgt,application/octet-stream">
                        <small class="form-text text-muted">可上传到专用目录并自动更新安卓 App 热更新清单。WGT 必须使用同一 AppID，且包内版本号必须与上方版本号一致，单个文件最大 200 MB。</small>
                    </div>
                    <div class="form-group ">
                        <label for="wgtUrl">Android WGT 直链（可选）</label>
                        <input type="url" class="form-control" id="wgtUrl" name="wgtUrl" placeholder="https://example.com/app-update.wgt">
                        <small class="form-text text-muted">上传文件和直链二选一。直链必须是公开 HTTPS 地址，并以 .wgt 结尾；直链包不会经过服务器压缩包校验。</small>
                    </div>
                  
                    <div class="form-group mb-3 text_right">
                        <button class="btn btn-primary" type="submit" id="updateAddPost">发布新版本</button>
                    </div>
                </form>

            </div>
        </div> 
    </div> 
</div>

<script>
    function check() {
        let version = document.getElementsByName('version')[0].value.trim();
        let versionCode = document.getElementsByName('versionCode')[0].value.trim();
        let versionIntro = document.getElementsByName('versionIntro')[0].value.trim();
        let versionUrl = document.getElementsByName('versionUrl')[0].value.trim();
        let wgtFile = document.getElementsByName('wgtFile')[0].files[0];
        let wgtUrl = document.getElementsByName('wgtUrl')[0].value.trim();
        
        if (version.length == 0) {
            alert("版本名不能为空");
            return false;
        } else if (versionCode.length == 0) {
            alert("版本号不能为空");
            return false;
        } else if (versionIntro.length == 0) {
            alert("更新描述不能为空");
            return false;
        } else if (versionUrl.length == 0) {
            alert("下载链接不能为空");
            return false;
        } else if (wgtFile && wgtUrl) {
            alert("WGT 文件和 WGT 直链只能填写一个");
            return false;
        } else if (wgtFile && (!/\.wgt$/i.test(wgtFile.name) || wgtFile.size > 200 * 1024 * 1024)) {
            alert("WGT 文件必须为 .wgt 且不超过 200 MB");
            return false;
        } else if (wgtUrl && (!/^https:\/\//i.test(wgtUrl) || !/\.wgt(?:[?#].*)?$/i.test(wgtUrl))) {
            alert("WGT 直链必须是 HTTPS 地址并以 .wgt 结尾");
            return false;
        }
    }
</script>

<?php
include_once 'Footer.php';
?>

</body>
</html>
