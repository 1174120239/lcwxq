<?php
require_once __DIR__ . '/session.php';
$captcha = substr(str_shuffle('1345689'), 0, 4);

$_SESSION['captcha'] = $captcha;

?>
