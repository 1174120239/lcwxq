<?php
//error_reporting(0); 运营时取消注释
header("Content-Type:text/html; charset=utf8");

//StarFreeApi站点地址
$api_site = 'http://127.0.0.1:8081/';
$api_key = 'CHANGE_ME_TO_THE_SAME_VALUE_AS_webinfo.key';

//StarFree后台配置
$ADMIN_PATH = '/admin';

//数据库配置（与StarFreeApi共用一个数据库）
$db_address = '127.0.0.1';
$db_username = 'CHANGE_ME';
$db_name = 'lcxqy';
$db_password = 'CHANGE_ME';
$db_prefix = 'starfree_';

//redis配置（与StarFreeApi共用一个redis）
$redis_password = '';
$redis_host = '127.0.0.1';
$redis_port = 6379;
$redis_prefix = 'starfree';


//以下勿动  以下勿动    以下勿动    以下勿动
$API_IS_INSTALL = $api_site.'StarFreeInstall/isInstall';
$API_TYPECHO_INSTALL = $api_site.'StarFreeInstall/typechoInstall';
$API_TO_UTF8MB4 = $api_site.'StarFreeInstall/toUtf8mb4';
$API_NEW_VERSION = $api_site.'StarFreeSystem/apiNewVersion';
$API_NEW_INSTALL = $api_site.'StarFreeInstall/newInstall';
$API_IS_KEY = $api_site.'StarFreeSystem/isKey';
$API_GET_CONFIG = $api_site.'StarFreeSystem/getConfig';
$API_GET_API_CONFIG = $api_site.'StarFreeSystem/getApiConfig';
$API_UPDATE_ALLOWED_EXTENSIONS = $api_site.'StarFreeSystem/updateAllowedExtensions';
$API_CONFIG_UPDATE = $api_site.'StarFreeSystem/apiConfigUpdate';
$API_SETUP_MYSQL = $api_site.'StarFreeSystem/setupMysql';
$API_SETUP_REDIS = $api_site.'StarFreeSystem/setupRedis';
$API_SETUP_EMAIL = $api_site.'StarFreeSystem/setupEmail';
$API_SETUP_CACHE = $api_site.'StarFreeSystem/setupCache';
$API_SETUP_WEB_KEY = $api_site.'StarFreeSystem/setupWebKey';
$API_SETUP_CONFIG = $api_site.'StarFreeSystem/setupConfig';
$API_ALL_CONFIG = $api_site.'StarFreeSystem/allConfig';
$API_ADD_VIP_TYPE = $api_site.'StarFreeSystem/addVipType';
$API_UPDATE_VIP_TYPE = $api_site.'StarFreeSystem/updateVipType';
$API_DELETE_VIP_TYPE = $api_site.'StarFreeSystem/deleteVipType';
$API_VIP_TYPE_LIST = $api_site.'StarFreeSystem/vipTypeList';
$API_ADD_APP = $api_site.'StarFreeSystem/addApp';
$API_UPDATE_APP = $api_site.'StarFreeSystem/updateApp';
$API_DELETE_APP = $api_site.'StarFreeSystem/deleteApp';
$API_APP_LIST = $api_site.'StarFreeSystem/appList';
$API_GET_EMAIL_TEMPLATE_CONFIG = $api_site.'StarFreeSystem/getEmailTemplateConfig';
$API_EMAIL_TEMPLATE_CONFIG_UPDATE = $api_site.'StarFreeSystem/emailTemplateConfigUpdate';
$API_UPLOAD_FULL = $api_site.'upload/full';
$API_UPLOAD_BASE64 = $api_site.'upload/base64';
