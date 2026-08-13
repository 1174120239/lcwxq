/*
聊一论坛由聊一论坛开发组维护
联系 qq638978650
*/

//APP配置
var API_URL = 'https://api.lcxqy.cn/'; // 生产 API；本地联调可临时改为 http://localhost:18082/，正式发行前必须改回本值。
var STAR_URL = 'https://admin.lcxqy.cn/';//Star后台站点，最后要带“/” 比如：https://baidu.com/
var WEB_URL = "https://prev.lcxqy.cn/";
//H5地址，有目录也要填写目录，最后要带“/” 比如：https://baidu.com/ 或https://baidu.com/h5/ 用于生成分享链接

//全局数据调用部分【重要】
var appName = "XX社区";//社区名称 
var appJC = "UID";//用户号名称  比如：UID、社区号...
var currencyName = "积分";//货币名称
var appEmail = "XXX@qq.com";//站长邮箱
var requestIdSequence = 0;

//评论等级头衔
var rankList = ["新人","潜水","冒泡","活跃","话痨","大佬","巨佬","传说","古神","越神"];
//经验等级头衔
var leverList = ["Lv0","Lv1","Lv2","Lv3","Lv4","Lv5","Lv6","Lv7","Lv8","Lv9"];
//头衔对应的背景颜色
var rankStyle = ["#dddddd","#c9c9c9","#73c9a1","#66c9c6","#6797d3","#616fe6","#8764dd","#5e2dc9","#4c1c87","#000000"];

//是否禁止网络代理，为1时开启（可以在安卓和苹果APP中防止抓包，但同时也会禁止VPN环境使用APP）
var banVPN = 0;//由于uniapp官方的问题，只能拦截部分条件



//以下勿动 以下勿动 以下勿动 以下勿动 以下勿动 以下勿动

var fields = "abcimg";
var key = "";
var isComment = 1;
var version = "1.2正式版";
var linkStar = WEB_URL + "#/pages/contents/info?cid={cid}&title=liaoyiluntan"
var appStar = WEB_URL + "#/pages/plugins/sy_appbox/info?id={id}&title=liaoyiluntan";
var GroupUrl = STAR_URL + '/Api/api.php?act=qqqun';
var GithubUrl = STAR_URL + '/Api/api.php?act=kefu';
import { localStorage } from '@/js_sdk/mp-storage/mp-storage/index.js'
var cachedPlugins = localStorage.getItem('getPlugins');
if (cachedPlugins) {
    var pluginList = JSON.parse(cachedPlugins);
}
uni.request({
	url: STAR_URL + 'Api/api.php?act=getPlugins',
	header: {
	  'Content-Type': 'application/x-www-form-urlencoded'
	},
	method: 'GET',
	dataType: 'json',
	success: (res) => {
		if (res.data.code === 200) {
			pluginList = res.data.data;
			localStorage.setItem('getPlugins', JSON.stringify(pluginList));
			console.log(pluginList);
		}
	},
	fail: (res) => {
		uni.showToast({
			title: '获取应用配置的信息失败.',
			icon: 'none'
		});
	}
});
module.exports = {
	createRequestId(prefix){
		requestIdSequence = (requestIdSequence + 1) % 1000000;
		var scope = prefix || 'economy';
		return scope + '-' + Date.now().toString(36) + '-'
			+ requestIdSequence.toString(36) + '-'
			+ Math.random().toString(36).substring(2, 12);
	},
	getKey(){
		return key;
	},
	getAppVersion(){
		return version;
	},
	getBanVPN(){
		return banVPN;
	},
	getCurrencyName(){
		return currencyName;
	},
	
	GetIsComment(){
		return isComment;
	},
	GetRankList(){
		return rankList;
	},
	GetLeverList(){
		return leverList;
	},
	GetRankStyle(){
		return rankStyle;
	},
	GetAppName:function(){
		return appName;
	},
	GetappJC:function(){
		return appJC;
	},
	GetAppEmail:function(){
		return appEmail;
	},
	GetlinkStar:function(){
		return linkStar;
	},
	GetPageRule:function(){
		return pageRule;
	},
	GetRaiders:function(){
		return raiders;
	},
	GetFeedback:function(){
		return feedback;
	},
	GetAboutme:function(){
		return aboutme;
	},
	GetFields:function(){
		return fields;
	},
	GetUpdateUrl:function(){
		return STAR_URL + 'Api/api.php?update=1';
	},
	GetAds:function(){
		return STAR_URL + 'Api/api.php?getAds=1';
	},
	GetAppStart:function(){
		return STAR_URL + 'Api/api.php?appStart=1';
	},
	GetGithubUrl:function(){
		return GithubUrl;
	},
	GetWebUrl:function(){
		return WEB_URL;
	},
	GetGroupUrl:function(){
		return GroupUrl;
	},
	userLogin:function(){
		return API_URL + 'SFreeUsers/userLogin';
	},
	phoneLogin:function(){
		return API_URL + 'SFreeUsers/phoneLogin';
	},
	RegSendCode:function(){
		return API_URL + 'SFreeUsers/RegSendCode';
	},
	sendSMS:function(){
		return API_URL + 'SFreeUsers/sendSMS';
	},
	SendCode:function(){
		return API_URL + 'SFreeUsers/SendCode';
	},
	userApi:function(){
		return API_URL + 'SFreeUsers/apiLogin';
	},
	userRegister:function(){
		return API_URL + 'SFreeUsers/userRegister';
	},
	userFoget:function(){
		return API_URL + 'SFreeUsers/userFoget';
	},
	getUserInfo:function(){
		return API_URL + 'SFreeUsers/userInfo';
	},
	getUserList:function(){
		return API_URL + 'SFreeUsers/userList';
	},
	userEdit:function(){
		return API_URL + 'SFreeUsers/userEdit';
	},
	getUserData:function(){
		return API_URL + 'SFreeUsers/userData';
	},
	userDelete:function(){
		return API_URL + 'SFreeUsers/userDelete';
	},
	userRecharge:function(){
		return API_URL + 'SFreeUsers/userRecharge';
	},
	vipTypeListStar: function() {
		return API_URL + 'StarFreeSystem/vipTypeList';
	},
	userWithdraw:function(){
		return API_URL + 'SFreeUsers/userWithdraw';
	},
	buyVIPpackage: function() {
		return API_URL + 'SFreeShop/buyVIPpackage';
	},
	withdrawList:function(){
		return API_URL + 'SFreeUsers/withdrawList';
	},
	withdrawStatus:function(){
		return API_URL + 'SFreeUsers/withdrawStatus';
	},
	manageUserEdit:function(){
		return API_URL + 'SFreeUsers/manageUserEdit';
	},
	apiBind:function(){
		return API_URL + 'SFreeUsers/apiBind';
	},
	userBindStatus:function(){
		return API_URL + 'SFreeUsers/userBindStatus';
	},
	setScan:function(){
		return API_URL + 'SFreeUsers/setScan';
	},
	userStatus:function(){
		return API_URL + 'SFreeUsers/userStatus';
	},
	regConfig:function(){
		return API_URL + 'SFreeUsers/regConfig';
	},
	campusIdentityOptions:function(){
		return API_URL + 'SFreeUsers/campusIdentityOptions';
	},
	campusIdentityManage:function(){
		return API_URL + 'SFreeUsers/campusIdentityManage';
	},
	campusIdentitySave:function(){
		return API_URL + 'SFreeUsers/campusIdentitySave';
	},
	signOut:function(){
		return API_URL + 'SFreeUsers/signOut';
	},
	
	//邀请码注册相关
	madeInvitation:function(){
		return API_URL + 'SFreeUsers/madeInvitation';
	},
	invitationList:function(){
		return API_URL + 'SFreeUsers/invitationList';
	},
	invitationExcel:function(){
		return API_URL + 'SFreeUsers/invitationExcel';
	},
	//轻量邀请分享
	invitationConfig:function(){
		return API_URL + 'SFreeInvitation/config';
	},
	invitationMe:function(){
		return API_URL + 'SFreeInvitation/me';
	},
	setClientId:function(){
		return API_URL + 'SFreeUsers/setClientId';
	},
	//消息相关
	getInbox:function(){
		return API_URL + 'SFreeUsers/inbox';
	},
	unreadNum:function(){
		return API_URL + 'SFreeUsers/unreadNum';
	},
	setRead:function(){
		return API_URL + 'SFreeUsers/setRead';
	},
	pollVote:function(){
		return API_URL + 'SFreeSpace/pollVote';
	},
	sendUser:function(){
		return API_URL + 'SFreeUsers/sendUser';
	},
	//关注
	follow:function(){
		return API_URL + 'SFreeUsers/follow';
	},
	isFollow:function(){
		return API_URL + 'SFreeUsers/isFollow';
	},
	followList:function(){
		return API_URL + 'SFreeUsers/followList';
	},
	fanList:function(){
		return API_URL + 'SFreeUsers/fanList';
	},
	//封禁
	banUser:function(){
		return API_URL + 'SFreeUsers/banUser';
	},
	unblockUser:function(){
		return API_URL + 'SFreeUsers/unblockUser';
	},
	violationList:function(){
		return API_URL + 'SFreeUsers/violationList';
	},
	userClean:function(){
		return API_URL + 'SFreeUsers/userClean';
	},
	restrict:function(){
		return API_URL + 'SFreeUsers/restrict';
	},
	
	giftVIP:function(){
		return API_URL + 'SFreeUsers/giftVIP';
	},
	
	getMarkList:function(){
		return API_URL + 'SFreeUserlog/markList';
	},
	getIsMark:function(){
		return API_URL + 'SFreeUserlog/isMark';
	},
	
	addLog:function(){
		return API_URL + 'SFreeUserlog/addLog';
	},
	removeLog:function(){
		return API_URL + 'SFreeUserlog/removeLog';
	},
	removeLog:function(){
		return API_URL + 'SFreeUserlog/removeLog';
	},
	dataClean:function(){
		return API_URL + 'SFreeUserlog/dataClean';
	},
	
	
	getCommentsList:function(){
		return API_URL + 'SFreeComments/commentsList';
	},
	setComments:function(){
		return API_URL + 'SFreeComments/commentsAdd';
	},
	commentsDelete:function(){
		return API_URL + 'SFreeComments/commentsDelete';
	},
	commentsAudit:function(){
		return API_URL + 'SFreeComments/commentsAudit';
	},
	
	
	//根据标签或者分类获取帖子
	getMetaContents:function(){
		return API_URL + 'SFreeMetas/selectContents';
	},
	getMetasList:function(){
		return API_URL + 'SFreeMetas/metasList';
	},
	geMetaInfo:function(){
		return API_URL + 'SFreeMetas/metaInfo';
	},
	editMeta:function(){
		return API_URL + 'SFreeMetas/editMeta';
	},
	addMeta:function(){
		return API_URL + 'SFreeMetas/addMeta';
	},
	
	getContentsList:function(){
		return API_URL + 'SFreeContents/contentsList';
	},
	getContentsInfo:function(){
		return API_URL + 'SFreeContents/contentsInfo';
	},
	// 校园问答
	qaQuestionList:function(){
		return API_URL + 'SFreeQa/questionList';
	},
	qaQuestionAdd:function(){
		return API_URL + 'SFreeQa/questionAdd';
	},
	qaQuestionInfo:function(){
		return API_URL + 'SFreeQa/questionInfo';
	},
	qaAnswerList:function(){
		return API_URL + 'SFreeQa/answerList';
	},
	qaAnswerAdd:function(){
		return API_URL + 'SFreeQa/answerAdd';
	},
	qaAnswerEdit:function(){
		return API_URL + 'SFreeQa/answerEdit';
	},
	qaAnswerDelete:function(){
		return API_URL + 'SFreeQa/answerDelete';
	},
	qaAnswerLike:function(){
		return API_URL + 'SFreeQa/answerLike';
	},
	qaCommentList:function(){
		return API_URL + 'SFreeQa/commentList';
	},
	qaCommentAdd:function(){
		return API_URL + 'SFreeQa/commentAdd';
	},
	qaCommentDelete:function(){
		return API_URL + 'SFreeQa/commentDelete';
	},
	qaQuestionManage:function(){
		return API_URL + 'SFreeQa/questionManage';
	},
	qaQuestionSave:function(){
		return API_URL + 'SFreeQa/questionSave';
	},
	qaQuestionStatus:function(){
		return API_URL + 'SFreeQa/questionStatus';
	},
	contentsAdd:function(){
		return API_URL + 'SFreeContents/contentsAdd';
	},
	//匿名动态（ng_music 插件本土化）
	anonymousConfig:function(){
		return API_URL + 'SFreeAnonymous/config';
	},
	anonymousPost:function(){
		return API_URL + 'SFreeAnonymous/post';
	},
	anonymousOwner:function(){
		return API_URL + 'SFreeAnonymous/owner';
	},
	anonymousAdminConfig:function(){
		return API_URL + 'SFreeAnonymous/admin/config';
	},
	
	contentsUpdate:function(){
		return API_URL + 'SFreeContents/contentsUpdate';
	},
	contentsImage:function(){
		return API_URL + 'SFreeContents/ImagePexels';
	},
	allData:function(){
		return API_URL + 'SFreeContents/allData';
	},
	contentsDelete:function(){
		return API_URL + 'SFreeContents/contentsDelete';
	},
	contentsAudit:function(){
		return API_URL + 'SFreeContents/contentsAudit';
	},
	getForeverblog:function(){
		return API_URL + 'SFreeContents/foreverblog';
	},
	deleteMeta: function() {
		return API_URL + 'SFreeMetas/deleteMeta';
	},
	metaRecommend: function() {
		return API_URL + 'SFreeMetas/toRecommend';
	},
	//帖子是否评论过
	isCommnet:function(){
		return API_URL + 'SFreeContents/isCommnet';
	},
	//帖子推荐
	toRecommend:function(){
		return API_URL + 'SFreeContents/toRecommend';
	},
	//帖子置顶
	toTop:function(){
		return API_URL + 'SFreeContents/addTop';
	},
	//帖子轮播
	toSwiper:function(){
		return API_URL + 'SFreeContents/addSwiper';
	},
	//设置自定义字段
	setFields:function(){
		return API_URL + 'SFreeContents/setFields';
	},
	contentConfig:function(){
		return API_URL + 'SFreeContents/contentConfig';
	},
	rewardList:function(){
		return API_URL + 'SFreeContents/rewardList';
	},
	upload:function(){
		return API_URL + 'upload/full';
	},
	shopList:function(){
		return API_URL + 'SFreeShop/shopList';
	},
	shopInfo:function(){
		return API_URL + 'SFreeShop/shopInfo';
	},
	addShop:function(){
		return API_URL + 'SFreeShop/addShop';
	},
	editShop:function(){
		return API_URL + 'SFreeShop/editShop';
	},
	deleteShop:function(){
		return API_URL + 'SFreeShop/deleteShop';
	},
	buyShop:function(){
		return API_URL + 'SFreeShop/buyShop';
	},
	isBuyShop:function(){
		return API_URL + 'SFreeShop/isBuyShop';
	},
	auditShop:function(){
		return API_URL + 'SFreeShop/auditShop';
	},
	getVipInfo:function(){
		return API_URL + 'SFreeShop/vipInfo';
	},
	buyVIP:function(){
		return API_URL + 'SFreeShop/buyVIP';
	},
	orderList:function(){
		return API_URL + 'SFreeUserlog/orderList';
	},
	orderSellList:function(){
		return API_URL + 'SFreeUserlog/orderSellList';
	},
	
	//帖子挂载商品
	mountShop:function(){
		return API_URL + 'SFreeShop/mountShop';
	},
	//支付宝当面付
	scancodePayStar:function(){
		return API_URL + 'pay/scancodePayStar';
	},
	//微信支付（官方）
	WxPayStar:function(){
		return API_URL + 'pay/WxPayStar';
	},
	//卡密充值
	tokenPay:function(){
		return API_URL + 'pay/tokenPay';
	},
	//易支付
	EPayStar:function(){
		return API_URL + 'pay/EPayStar';
	},
	//充值二维码生成
	qrCodeStar:function(){
		return API_URL + 'pay/qrCodeStar';
	},
	payLogList:function(){
		return API_URL + 'pay/payorderList';
	},
	//卡密充值相关
	tokenPayList:function(){
		return API_URL + 'pay/tokenPayStar';
	},
	KamiList:function(){
		return API_URL + 'pay/tokenPayList';
	},
	tokenPayExcel:function(){
		return API_URL + 'pay/tokenPayExcel';
	},
	
	//生成卡密
	madetoken:function(){
		return API_URL + 'pay/madetoken';
	},
	//财务记录
	financeList:function(){
		return API_URL + 'pay/financeList';
	},
	financeTotal:function(){
		return API_URL + 'pay/financeTotal';
	},
	//付费广告
	adsConfig:function(){
		return API_URL + 'SFreeAds/adsConfig';
	},
	adsInfo:function(){
		return API_URL + 'SFreeAds/adsInfo';
	},
	
	addAds:function(){
		return API_URL + 'SFreeAds/addAds';
	},
	adsList:function(){
		return API_URL + 'SFreeAds/adsList';
	},
	editAds:function(){
		return API_URL + 'SFreeAds/editAds';
	},
	deleteAds:function(){
		return API_URL + 'SFreeAds/deleteAds';
	},
	auditAds:function(){
		return API_URL + 'SFreeAds/auditAds';
	},
	renewalAds:function(){
		return API_URL + 'SFreeAds/renewalAds';
	},
	renewalAds:function(){
		return API_URL + 'SFreeAds/renewalAds';
	},
	//聊天
	getPrivateChat:function(){
		return API_URL + 'SFreeChat/getPrivateChat';
	},
	sendMsg:function(){
		return API_URL + 'SFreeChat/sendMsg';
	},
	myChat:function(){
		return API_URL + 'SFreeChat/myChat';
	},
	msgList:function(){
		return API_URL + 'SFreeChat/msgList';
	},
	deleteChat:function(){
		return API_URL + 'SFreeChat/deleteChat';
	},
	deleteMsg:function(){
		return API_URL + 'SFreeChat/deleteMsg';
	},
	createGroup:function(){
		return API_URL + 'SFreeChat/createGroup';
	},
	editGroup:function(){
		return API_URL + 'SFreeChat/editGroup';
	},
	allChat:function(){
		return API_URL + 'SFreeChat/allChat';
	},
	banChat:function(){
		return API_URL + 'SFreeChat/banChat';
	},
	groupInfo:function(){
		return API_URL + 'SFreeChat/groupInfo';
	},
	//动态开始
	
	addSpace:function(){
		return API_URL + 'SFreeSpace/addSpace';
	},
	editSpace:function(){
		return API_URL + 'SFreeSpace/editSpace';
	},
	spaceInfo:function(){
		return API_URL + 'SFreeSpace/spaceInfo';
	},
	spaceList:function(){
		return API_URL + 'SFreeSpace/spaceList';
	},
	myFollowSpace:function(){
		return API_URL + 'SFreeSpace/myFollowSpace';
	},
	spaceDelete:function(){
		return API_URL + 'SFreeSpace/spaceDelete';
	},
	spaceLikes:function(){
		return API_URL + 'SFreeSpace/spaceLikes';
	},
	spaceReview:function(){
		return API_URL + 'SFreeSpace/spaceReview';
	},
	spaceLock:function(){
		return API_URL + 'SFreeSpace/spaceLock';
	},
	topicList:function(){
		return API_URL + 'SFreeSpace/topicList';
	},
	topicCreate:function(){
		return API_URL + 'SFreeSpace/topicCreate';
	},
	topicFollow:function(){
		return API_URL + 'SFreeSpace/topicFollow';
	},
	spaceUserReplies:function(){
		return API_URL + 'SFreeSpace/userReplies';
	},
	spaceReportAdd:function(){
		return API_URL + 'SFreeSpace/reportAdd';
	},
	spaceReportList:function(){
		return API_URL + 'SFreeSpace/reportList';
	},
	spaceReportReview:function(){
		return API_URL + 'SFreeSpace/reportReview';
	},
	
	SMuser:function(){
		return 	STAR_URL + 'Api/api.php?act=usercount'
	},
	SMgonggao:function(){
		return 	STAR_URL + 'Api/api.php?act=appdata'
	},
	SMset:function(){
		return 	STAR_URL + 'Api/api.php?act=opset'
	},
	SMfenlei:function(){
		return 	STAR_URL + 'Api/api.php?act=fenlei'
	},
	SMhy:function(){
		return 	STAR_URL + 'Api/api.php?act=vip'
	},
	SMadimg:function(){
		return 	STAR_URL + 'Api/api.php?act=adimg2'
	},
	SMqiandaojl:function(){
		return 	API_URL + 'SFreeEconomy/signinConfig'
	},
	SMqiandao:function(){
		return 	API_URL + 'SFreeEconomy/signin'
	},
	SMqqlogin:function(){
		return 	STAR_URL + 'Api/api.php?act=logininfo'
	},
	SMchongzhiset:function(){
		return 	STAR_URL + 'Api/api.php?act=chongzhiset'
	},
	SMviphide:function(){
		return 	STAR_URL + 'Api/api.php?act=viphide'
	},
	SMqzxz:function(){
		return 	STAR_URL + 'Api/api.php?act=qzxz'
	},
	SMmusicpic:function(){
		return 	STAR_URL + 'Api/api.php?act=musicpic'
	},
	SMleiji:function(){
		return 	API_URL + 'SFreeEconomy/signinStreak'
	},
	SMlikeall:function(){
		return 	STAR_URL + 'Api/api.php?act=likeall'
	},
	GetMp3Url:function(){
		return STAR_URL + 'mp3.php';
	},
	SFgetAppInfoUrl: function() {
		return STAR_URL + 'Plugins/sy_appbox/Pages/getAppInfo.php';
	},
	GetappStar: function() {
		return appStar;
	},
	//插件
	PluginLoad(pluginName) {
		const timestamp = Date.now();
		return `${STAR_URL}Plugins/${pluginName}/index.php?timestamp=${timestamp}&plugin=${pluginName}&`;
	},
	
	IsNull(obj) {
		return (obj != null && obj != undefined);
	},
	randomHexColor() { 
		var hex = Math.floor(Math.random() * 16777216).toString(16); 
		while (hex.length < 6) { 
			hex = '0' + hex;
		}
		return '#' + hex;
	},
	//获取日期
	formatDate(datetime) {
		var datetime = new Date(parseInt(datetime * 1000));
		// 获取年月日时分秒值  slice(-2)过滤掉大于10日期前面的0
		var year = datetime.getFullYear(),
			month = ("0" + (datetime.getMonth() + 1)).slice(-2),
			date = ("0" + datetime.getDate()).slice(-2),
			hour = ("0" + datetime.getHours()).slice(-2),
			minute = ("0" + datetime.getMinutes()).slice(-2);
		//second = ("0" + date.getSeconds()).slice(-2);

		var result = year + "-" + month + "-" + date + " " + hour + ":" + minute;

		return result;
	},
	//移除数据中的空对象
	removeObjectEmptyKey(json) {
	    var value;
	    for (var key in json) {
	        if (json.hasOwnProperty(key)) {
	            value = json[key];
	            if (value === undefined || value === '' || value === null) {
	                delete json[key]
	            }
	        }
	    }
	    return json;
	},
	//获取用户当前等级
	getLever(num){
		var lv = 0;
		if (num < 10) {
			lv = 0;
		} else if (num >= 10 && num < 50) {
			lv = 1;
		} else if (num >= 50 && num < 200) {
			lv = 2;
		} else if (num >= 200 && num < 500) {
			lv = 3;
		} else if (num >= 500 && num < 1000) {
			lv = 4;
		} else if (num >= 1000 && num < 2000) {
			lv = 5;
		} else if (num >= 2000 && num < 5000) {
			lv = 6;
		} else if (num >= 5000 && num < 10000) {
			lv = 7;
		} else if (num >= 10000 && num < 20000) {
			lv = 8;
		} else if (num >= 20000) {
			lv = 9;
		}
		return lv;
	},
	getLeverExp(){
		return [
			0,
			10,    
			50,   
			200,   
			500,  
			1000,  
			2000, 
			5000,
			10000,
			20000,
			9999999
		];
	},

}
