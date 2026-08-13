<?php
error_reporting(0);
require_once __DIR__ . '/session.php';
include_once "connect.php";
require_once __DIR__ . '/password.php';
if (!isset($_SESSION['login_attempts'])) {
    $_SESSION['login_attempts'] = 0;
    $_SESSION['first_attempt_time'] = time();
}

if ($_SESSION['login_attempts'] >= 5 && (time() - $_SESSION['first_attempt_time']) < 900) {
    die("<script>alert('登录尝试次数过多，请15分钟后再试');location.href = '".$ADMIN_PATH."/login.php';</script>");
}

if ((time() - $_SESSION['first_attempt_time']) >= 900) {
    $_SESSION['login_attempts'] = 0;
    $_SESSION['first_attempt_time'] = time();
}

if (!isset($_POST['captcha']) || !isset($_SESSION['captcha']) || !isset($_SESSION['captcha_time'])) {
    die("<script>alert('验证码无效');location.href = '".$ADMIN_PATH."/login.php';</script>");
}

if (time() - $_SESSION['captcha_time'] > 300) {
    unset($_SESSION['captcha']);
    unset($_SESSION['captcha_time']);
    die("<script>alert('验证码已过期，请重新获取');location.href = '".$ADMIN_PATH."/login.php';</script>");
}

$captcha = strtoupper(trim($_POST['captcha']));
$captcha_session = strtoupper(trim($_SESSION['captcha']));

$valid_captcha = ($captcha === $captcha_session);
unset($_SESSION['captcha']);
unset($_SESSION['captcha_time']);

if (!$valid_captcha) {
    $_SESSION['login_attempts']++;
    die("<script>alert('验证码错误');location.href = '".$ADMIN_PATH."/login.php';</script>");
}


$user = $_POST['adminName'];
$pw = $_POST['pw'];
if (empty($user) || empty($pw)) {
    die("<script>alert('用户名和密码不能为空');location.href = '".$ADMIN_PATH."/login.php';</script>");
}

if (!preg_match('/^[a-zA-Z0-9_]{3,16}$/', $user)) {
    die("<script>alert('用户名只能包含3-16位字母、数字和下划线');location.href = '".$ADMIN_PATH."/login.php';</script>");
}

if (strlen($pw) < 5 || strlen($pw) > 72) {
    die("<script>alert('密码长度不正确');location.href = '".$ADMIN_PATH."/login.php';</script>");
}

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $sql = "SELECT * FROM ".$db_prefix."_admin_login WHERE user = ?";
    $stmt = $connect->prepare($sql);
    $stmt->bind_param("s", $user);
    $stmt->execute();
    $stmt->store_result();

    if ($stmt->num_rows > 0) {
        $stmt->bind_result($id, $Login_user, $Login_pw);
        $stmt->fetch();
        
        $passwordInfo = password_get_info($Login_pw);
        $modernHash = !empty($passwordInfo['algo']);
        $passwordValid = $modernHash
            ? password_verify($pw, $Login_pw)
            : hash_equals($Login_pw, md5($pw));

        if ($user === $Login_user && $passwordValid) {
            if (!$modernHash || password_needs_rehash($Login_pw, PASSWORD_DEFAULT)) {
                $upgradedHash = password_hash($pw, PASSWORD_DEFAULT);
                $loginTable = $db_prefix . '_admin_login';
                if ($upgradedHash !== false
                    && admin_password_column_supports(
                        $connect, $db_name, $loginTable, $upgradedHash
                    )) {
                    $upgrade = $connect->prepare(
                        "UPDATE ".$db_prefix."_admin_login SET pw = ? WHERE id = ?"
                    );
                    if ($upgrade) {
                        $upgrade->bind_param("si", $upgradedHash, $id);
                        if (!$upgrade->execute()) {
                            error_log('Could not upgrade the administrator password hash');
                        }
                        $upgrade->close();
                    }
                } else {
                    error_log('Administrator password column is too short for password_hash');
                }
            }
            session_regenerate_id(true);
            $_SESSION['login_attempts'] = 0;
            $_SESSION['loginadmin'] = $user;
            $_SESSION['login_time'] = time();
            $_SESSION['login_ip'] = $_SERVER["REMOTE_ADDR"];
            
            $ip = $_SERVER["REMOTE_ADDR"];
            $gsd = get_ip_city($ip);
            if (empty($gsd)) {
                $gsd = '未知';
            }
            
            $time = gmdate("Y-m-d H:i:s", time() + 8 * 3600);
            $stmt = $connect->prepare("INSERT INTO ".$db_prefix."_admin_ip (ipAdd, Time, State) VALUES (?, ?, ?)");
            $stmt->bind_param("sss", $gsd, $time, $ip);
            
            if (!$stmt->execute()) {
                error_log("Failed to log login attempt: " . $stmt->error);
            }
            
            echo "<script>alert('登录成功');location.href = '".$ADMIN_PATH."/index.php';</script>";
        } else {
            $_SESSION['login_attempts']++;

            error_log("Failed login attempt for user: " . $user . " from IP: " . $_SERVER["REMOTE_ADDR"]);
            die("<script>alert('用户名或密码错误');location.href = '".$ADMIN_PATH."/login.php';</script>");
        }
    } else {
        $_SESSION['login_attempts']++;

        error_log("Login attempt with non-existent user: " . $user . " from IP: " . $_SERVER["REMOTE_ADDR"]);
        die("<script>alert('用户名不存在');location.href = '".$ADMIN_PATH."/login.php';</script>");
    }
}

$connect->close();

function get_ip_city($ip) {
    $ch = curl_init();
    $url = 'http://ip-api.com/json/'.$ip.'?lang=zh-CN';
    curl_setopt($ch, CURLOPT_URL, $url);
    curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
    curl_setopt($ch, CURLOPT_SSL_VERIFYPEER, false);
    curl_setopt($ch, CURLOPT_SSL_VERIFYHOST, false);
    curl_setopt($ch, CURLOPT_TIMEOUT, 5);
    
    $location = curl_exec($ch);
    if(curl_errno($ch)) {
        error_log('Curl error: ' . curl_error($ch));
        curl_close($ch);
        return '未知';
    }
    curl_close($ch);
    
    $location = mb_convert_encoding($location, 'utf-8', 'GB2312');
    $data = json_decode($location, true);
    
    return isset($data['city']) ? $data['city'] : '未知';
}
?>
