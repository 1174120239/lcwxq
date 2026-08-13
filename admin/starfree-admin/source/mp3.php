<?php
include_once 'Config_DB.php';
$token = isset($_GET['token']) ? $_GET['token'] : null;
$tokenJson = json_encode(
    $token === null ? '' : $token,
    JSON_HEX_TAG | JSON_HEX_AMP | JSON_HEX_APOS | JSON_HEX_QUOT
);
?>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>上传文件</title>
    <style>
        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }
        
        body {
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
            line-height: 1.5;
            color: #333;
            background-color: #f8f9fa;
        }
        
        .container {
            max-width: 600px;
            margin: 0 auto;
            padding: 20px;
        }
        
        .mt-5 {
            margin-top: 3rem;
        }
        
        .mb-3 {
            margin-bottom: 1rem;
        }
        
        .form-label {
            display: block;
            margin-bottom: 0.5rem;
            font-weight: 500;
        }
        
        .form-control {
            display: block;
            width: 100%;
            padding: 0.375rem 0.75rem;
            font-size: 1rem;
            line-height: 1.5;
            color: #495057;
            background-color: #fff;
            border: 1px solid #ced4da;
            border-radius: 0.25rem;
            transition: border-color 0.15s ease-in-out;
        }
        
        .form-control:focus {
            border-color: #80bdff;
            outline: 0;
            box-shadow: 0 0 0 0.2rem rgba(0,123,255,.25);
        }
        
        .btn {
            display: inline-block;
            font-weight: 400;
            text-align: center;
            white-space: nowrap;
            vertical-align: middle;
            user-select: none;
            border: 1px solid transparent;
            padding: 0.375rem 0.75rem;
            font-size: 1rem;
            line-height: 1.5;
            border-radius: 0.25rem;
            transition: color 0.15s ease-in-out, background-color 0.15s ease-in-out, border-color 0.15s ease-in-out;
            cursor: pointer;
        }
        
        .btn-primary {
            color: #fff;
            background-color: #007bff;
            border-color: #007bff;
        }
        
        .btn-primary:hover {
            background-color: #0069d9;
            border-color: #0062cc;
        }
        
        .btn-secondary {
            color: #fff;
            background-color: #6c757d;
            border-color: #6c757d;
        }
        
        .btn-secondary:hover {
            background-color: #5a6268;
            border-color: #545b62;
        }
        
        input[type="text"] {
            border: 1px solid #ced4da;
            width: 100%;
            padding: 0.375rem 0.75rem;
            border-radius: 0.25rem;
            margin-bottom: 1rem;
        }
        
        #upload-message {
            margin: 1rem 0;
            color: #666;
        }
    </style>
</head>
<body>
    <div class="container mt-5">
        <br><br><br>
        
        <form id="uploadForm" enctype="multipart/form-data">
            <div class="mb-3">
                <label for="audio" class="form-label">选择音频文件：</label>
                <input type="file" class="form-control" id="audio" name="file" accept="audio/*" onchange="uploadFile()">
            </div>

            <button type="button" class="btn btn-primary" style="width:100%" id="upload-btn">上传</button>
        </form>
        
        <br>
        <p id="upload-message">注意：上传过程中请勿离开，上传完成会生成链接</p>
        <input type="text" id="mp3url" value=""></p>
       
    </div>
    <script>
        document.addEventListener('DOMContentLoaded', function () {
            var uploadBtn = document.getElementById('upload-btn');
            var uploadForm = document.getElementById('uploadForm');
            var uploadMessage = document.getElementById('upload-message');
            var mp3url = document.getElementById('mp3url');
            var mp3url = document.getElementById('mp3url');
            
            var token = <?php echo $tokenJson; ?>;
            uploadBtn.addEventListener('click', function () {
                var fileInput = document.getElementById('audio');
                var file = fileInput.files[0];
                
                var allowedTypes = ['.mp3', '.wav', '.ogg', '.flac'];

                if (!file) {
                    alert('请先选择文件');
                    return;
                }

                var fileName = file.name.toLowerCase();
                var isValidType = allowedTypes.some(function(type) {
                    return fileName.endsWith(type);
                });

                if (!isValidType) {
                    alert('文件类型不允许。只允许上传以下类型文件：' + allowedTypes.join(', '));
                    return;
                }

                var formData = new FormData();
                formData.append('file', file);
                if (token) {
                    formData.append('token', token);
                } else {
                    alert('登录状态错误，请退出登录后重试！');
                    return;
                }
                alert('文件上传中，请勿退出或进行其他操作，请耐心稍候...');
                
                uploadMessage.innerText = '文件上传中，请勿退出或进行其他操作，请耐心稍候...';
                var uploadBtn = document.getElementById('upload-btn');
                uploadBtn.disabled = true; 
                fetch('<?php echo $API_UPLOAD_FULL; ?>', {
                    method: 'POST',
                    body: formData
                })
                .then(response => response.json())
                .then(data => {
                    if (data.code == 1) {
                        uploadMessage.innerText = '上传完成，文件链接已生成';
                        mp3url.value = data.data.url; 
                        mp3url.style.display = 'block';
                        var uploadBtn = document.getElementById('upload-btn');
                        uploadBtn.disabled = true; 
                        
                        mp3url.select();
                        try {
                            var successful = document.execCommand('copy');
                            var msg = successful ? '链接已复制到剪贴板，返回上一页粘贴即可！' : '请手动复制链接，然后返回上一页粘贴即可！';
                            alert(msg);
                        } catch (err) {
                            alert('复制失败，请手动复制链接。错误信息: ' + err.message);
                        }
                    } else {
                        uploadMessage.innerText = '上传失败，请重试: ' + data.msg;
                        alert(data.msg);
                        mp3url.value = '';
                        var uploadBtn = document.getElementById('upload-btn');
                        uploadBtn.disabled = false; 
                    }
                })
                .catch(error => {
                    console.error('上传失败', error);
                    uploadMessage.innerText = '上传失败，请重试';
                    mp3url.value = '';
                    var uploadBtn = document.getElementById('upload-btn');
                    uploadBtn.disabled = false; 
                });
            });
        });


        function uploadFile() {
            var uploadBtn = document.getElementById('upload-btn');
            uploadBtn.click(); 
        }

        /*
        * ###################################
        * ###本开源项目遵循MIT License协议###
        * ###您可以将项目用于合法的商业项目##
        * ###################################
        * ###为了项目后续更换的维护与更新####
        * ###请您保留原项目、作者版权信息####
        * ###################################
        */
            window.addEventListener('load', function() {
                console.log(
                    '%c' +
                    '   _____ __             ______              \n' +
                    '  / ___// /_____ ______/ ____/_____  _____ \n' +
                    '  \\__ \\/ __/ __ `/ ___/ /_  / ___/ _ \\/ _ \\\n' +
                    ' ___/ / /_/ /_/ / /  / __/ / /  /  __/  __/\n' +
                    '/____/\\__/\\__,_/_/  /_/   /_/   \\___/\\___/ \n',
                    'color: #f6afe0; font-weight: bold;'
                );
                console.log(
                    '%cStarFree <?php echo $currentVersion; ?>%c' + 
                    ' 一款开源的唯美论坛博客系统',
                    'background: linear-gradient(to right, #f7aed5, #f6afec); color: white; padding: 4px 8px; border-radius: 4px; font-weight: bold;',
                    'color: #666; font-size: 14px;'
                );
                console.group('%c🌟 项目信息', 'color: #f6afec; font-size: 14px; font-weight: bold;');
                console.log(
                    '%c官方文档%c https://www.yuque.com/senyun-ev0j3/starfree\n' +
                    '%c开源地址%c https://starfree.qxzhi.cn\n' +
                    '%c用户交流群%c 1021506674\n' +
                    '%c作者QQ%c 2504531378\n' +
                    '%c开源协议%c MIT License',
                    'color: #f6afe0;', 'color: #666;',
                    'color: #f6afe0;', 'color: #666;',
                    'color: #f6afe0;', 'color: #666;',
                    'color: #f6afe0;', 'color: #666;',
                    'color: #f6afe0;', 'color: #666;'
                );
                console.groupEnd();
                console.groupCollapsed('%c💫 版本信息', 'color: #f6afec; font-size: 14px; font-weight: bold;');
                console.log(
                    '%c当前版本%c <?php echo $currentVersion; ?>\n' +
                    '%c最新版本%c <?php echo $latestVersion; ?>',
                    'color: #f6afe0;', 'color: #666;',
                    'color: #f6afe0;', 'color: #666;'
                );
                console.groupEnd();
                console.log(
                    '%c如果觉得不错的话，就给个star⭐支持一下吧！',
                    'color: #666; font-style: italic;'
                );
           });
         /*
        * ###################################
        * ###本开源项目遵循MIT License协议###
        * ###您可以将项目用于合法的商业项目##
        * ###################################
        * ###为了项目后续更换的维护与更新####
        * ###请您保留原项目、作者版权信息####
        * ###################################
        */
    </script>
</body>
</html>
