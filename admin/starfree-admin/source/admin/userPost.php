<?php
require_once __DIR__ . '/session.php';
?>

<?php
$adminName = trim($_POST['adminName']);
$pw = trim($_POST['pw']);
$file = $_SERVER['PHP_SELF'];
include_once 'connect.php';
require_once __DIR__ . '/password.php';

if (isset($_SESSION['loginadmin']) && $_SESSION['loginadmin'] <> '') {
    
    if ($pw) {
        if (!admin_password_is_strong($pw)) {
            die("<script>alert('密码至少8位，且必须同时包含字母和数字');location.href = 'user.php';</script>");
        }
        $password = password_hash($pw, PASSWORD_DEFAULT);
        $loginTable = $db_prefix . '_admin_login';
        if ($password === false
            || !admin_password_column_supports($connect, $db_name, $loginTable, $password)) {
            error_log('Administrator password column is too short for password_hash');
            die("<script>alert('密码存储列长度不足，请先完成数据库安全升级');location.href = 'user.php';</script>");
        }
        $stmt = $connect->prepare("UPDATE " . $db_prefix . "_admin_login SET user=?, pw=? WHERE id=1");
        $stmt->bind_param("ss", $adminName, $password);
        session_destroy();
    } else {
        $stmt = $connect->prepare("UPDATE " . $db_prefix . "_admin_login SET user=? WHERE id=1");
        $stmt->bind_param("s", $adminName);
    }

    if ($stmt->execute()) {
        echo "<script>alert('修改成功');location.href = 'login.php';</script>";
    } else {
        echo "<script>alert('修改失败');location.href = 'login.php';</script>";
    }
    $stmt->close();

} else {
    echo "<script>alert('非法操作，行为已记录');location.href = 'warning.php?route=$file';</script>";
}
