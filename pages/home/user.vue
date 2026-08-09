<template>
	<view class="user campus-page campus-profile" :class="{'campus-night': profileNight}">
		<view class="immersive-profile">
			<view class="profile-status-spacer" :style="{height: StatusBar + 'px'}"></view>
			<view class="profile-cover">
				<view class="profile-cover-bg" :style="avatar ? {backgroundImage:'url(' + avatar + ')'} : {}"></view>
				<view class="profile-cover-shade"></view>
				<view class="profile-top-tools">
					<view class="profile-tool" @tap="showProfileMenu=!showProfileMenu"><text class="cuIcon-sort"></text></view>
					<CampusThemeToggle class="profile-theme-toggle" :night="profileNight" :value="campusThemeMode" :compact="true" @change="handleCampusThemeMode"></CampusThemeToggle>
					<view class="profile-tool" @tap="toSetup"><text class="cuIcon-light"></text></view>
				</view>
				<view class="profile-menu" :class="{'is-open':showProfileMenu}">
					<view @tap="toLink('/pages/user/useredit');showProfileMenu=false"><text class="cuIcon-edit"></text><text>编辑资料</text></view>
					<view @tap="toSetUp();showProfileMenu=false"><text class="cuIcon-settings"></text><text>账户设置</text></view>
					<view @tap="toLink('/pages/user/myshop');showProfileMenu=false"><text class="cuIcon-goods"></text><text>我的商品</text></view>
					<view @tap="toLink('/pages/user/sellorder');showProfileMenu=false"><text class="cuIcon-form"></text><text>售出订单</text></view>
					<view @tap="toMedia();showProfileMenu=false"><text class="cuIcon-service"></text><text>联系我们</text></view>
				</view>

				<view class="profile-identity">
					<view class="profile-avatar" v-if="userInfo" :style="userInfo.style" @tap="toUserContents()"></view>
					<view class="profile-avatar profile-avatar-empty" v-else @tap="toLogin"><text class="cuIcon-my"></text></view>
					<view class="profile-name-block" v-if="userInfo">
						<view class="profile-name"><text>{{name}}</text><text class="cuIcon-unfold"></text></view>
						<view class="profile-id" @tap="copyUid"><text>{{appname}}：{{uid}}</text><text class="cuIcon-copy"></text></view>
						<view class="profile-campus" v-if="userInfo.campus">{{userInfo.campus}}</view>
						<view class="profile-grade" v-if="userInfo.grade">{{userInfo.grade}}</view>
						<view class="profile-ip">IP：校园</view>
					</view>
					<view class="profile-name-block" v-else @tap="toLogin">
						<view class="profile-name"><text>点击登录</text></view>
						<view class="profile-id"><text>登录后查看完整校园档案</text></view>
					</view>
				</view>

				<view class="profile-stats">
					<view @tap="toLink('/pages/user/followList?uid='+uid)"><text>{{fancount || 0}}</text><text>关注</text></view>
					<view @tap="toLink('/pages/user/fanList?uid='+uid)"><text>{{formatNumber(userData.fanNum || 0)}}</text><text>粉丝</text></view>
					<view @tap="toLink('/pages/user/usercomments')"><text>{{userData.commentsNum || 0}}</text><text>互动</text></view>
				</view>

				<view class="profile-signature">{{userInfo && (userInfo.intro || userInfo.description) ? (userInfo.intro || userInfo.description) : '记录校园生活，分享每一个值得留下的瞬间。'}}</view>
				<view class="profile-recommend"><text>校园档案</text><text class="cuIcon-edit" @tap="toLink('/pages/user/useredit')"></text></view>
				<view class="profile-badges" v-if="userInfo">
					<text>{{getLv(userInfo.experience)}}</text><text v-if="isvip" class="vip-badge">VIP</text><text v-if="userInfo.customize">{{userInfo.customize}}</text>
				</view>

				<view class="profile-quick-actions" :class="{'has-manage-entry': group=='administrator'||group=='editor'}">
					<view class="profile-manage-action" v-if="group=='administrator'||group=='editor'" @tap="toManage">
						<view><text class="cuIcon-settings"></text><text>管理中心</text></view><text>后台管理</text>
					</view>
					<view @tap="toLink('/pages/user/usermark')"><view><text class="cuIcon-favor"></text><text>浏览记录</text></view><text>查看内容</text></view>
					<view @tap="toLink('/pages/user/assets')"><view><text class="cuIcon-card"></text><text>钱包</text></view><text>点击查看</text></view>
					<view class="profile-clock-action" :class="{'is-clocked': isClock==1}" @tap="toClock"><view><text class="cuIcon-calendar"></text><text>{{isClock==1?'已签到':'签到'}}</text></view><text>{{isClock==1?'今日已签':'每日签到'}}</text></view>
				</view>
			</view>

			<view class="profile-content-sheet">
				<view class="profile-content-tabs profile-dynamic-tab">
					<view class="is-active">动态</view>
				</view>
				<view class="profile-dynamic-list" v-if="profileSpaceList.length > 0">
					<spaceItem :spaceList="profileSpaceList" :compact="true"></spaceItem>
				</view>
				<view class="profile-dynamic-loading" v-else-if="profileSpaceLoading"><view class="campus-loader"></view></view>
				<view class="profile-empty-state" v-else>
					<text class="cuIcon-community"></text><text>动态会记录在这里</text>
					<view @tap="toLink('/pages/space/post')">发布第一条动态</view>
				</view>
			</view>
		</view>
		
		<view v-if="false" class="homepage">
			<view class="bar">
				<u-navbar :placeholder="true" bgColor="#f4f8f8">
					<view slot="left"></view>
					<view slot="right" class="right">
						<view @tap="toLink('/pages/user/useredit')">
							<text class="tn-icon-write" style="font-size: 40upx;"></text>
						</view>
						<view @tap="toSetup">
							<text class="tn-icon-set" style="font-size: 40upx;"></text>
						</view>
						<!-- #ifdef APP-PLUS -->
						<view @tap="toScan">
							<text class="tn-icon-scan" style="font-size: 40upx;"></text>
						</view>
						<!-- #endif -->
					</view>
				</u-navbar>
			</view>
			<view class="people">
				<view class="headImg">
					<!-- <image src="../../static/image/travel/personal/tx.png"> -->
					<!-- {{userInfo}} -->
					<view class="avatar" v-if="userInfo" :style="userInfo.style" @tap="toUserContents()"></view>
					<view class="avatar" style="background-color: #ccc;" v-else></view>
				</view>
				<view class="info" v-if="userInfo != null">
					<view class="nick" style="display: flex; align-items: center;">
					  <text :style="isvip ? 'color: #db3287ed' : ''">{{name}}</text>
					 
					</view>
					<view class="grade" @click="copyUid">
						<view style="margin-right: 10upx;color: #454545ed;">{{appname}}: {{uid}}</view>
					</view>
					<view class="grade" style="margin-top: 10upx;">
					<text class="userlv" v-if="isvip" style="margin-left: 0px;background: linear-gradient(to bottom right, #f2ad5c, #e6216d,#901ccb);color:white;padding: 2upx 10upx;border-radius: 20upx;">VIP</text>
					
					<text class="userlv" :style="getLvStyle(userInfo.experience)" :class="isvip ? '' : 'xyy'" style="padding: 2upx 10upx;">{{getLv(userInfo.experience)}}</text>
					
					<text class="userlv customize" style="border: 3upx solid black;color:black;padding: 2upx 10upx;border-radius: 40upx;background-color: transparent;" v-if="userInfo.customize&&userInfo.customize!=''">{{userInfo.customize}}</text>
					</view>
				</view>
				<view class="info" v-if="userInfo == null">
					<view class="nick" @tap="toLogin">
						<text>点击登录</text>
					</view>
				</view>
				<view class="space" v-if="userInfo != null">
					<text class="padding-lr-sm padding-tb-xs" style="border-radius: 40upx; background-color: #3cc9a4;color: white;padding: 10upx 40upx;" @tap="toClock">签到</text>
				</view>
			</view>
			<view class="list" style="display: flex; justify-content: center;"  v-if="userInfo != null">
				<view class="item"  @tap="toLink('/pages/user/fanList?uid='+uid)">
					<view class="text">
						<text>{{formatNumber(userData.fanNum)}}</text>
						<text>粉丝</text>
					</view>
					
				</view>
				<view class="item">
				<u-line direction="col" color="#979797 " length="32rpx"></u-line>
				</view>
				<view class="item">
					<view class="text" @tap="toLink('/pages/user/followList?uid='+uid)">
						<text>{{fancount}}</text>
						<text>关注</text>
					</view>
					
				</view>
				<view class="item">
				<u-line direction="col" color="#979797 " length="32rpx"></u-line>
				</view>
				<view class="item"  @tap="toLink('/pages/user/usercomments')">
					<view class="text">
						<text>{{userData.commentsNum}}</text>
						<text>评论</text>
					</view>
					
				</view>
				<view class="item">
				<u-line direction="col" color="#979797 " length="32rpx"></u-line>
				</view>
				<view class="item"  @tap="toLink('/pages/user/assets')">
					<view class="text">
						<text>{{formatNumber(userData.assets)}}</text>
						<text>{{assetsname}}</text>
					</view>
					
				</view>
			</view>
			<view class="infos">
				<!--<br>
				<view class="account-pay"></view>-->
				<view v-if="isvip" class="open-vip" @tap="toLink('/pages/user/buyvip')">
					<image src="/static/image/travel/personal/vip01.png"></image>
					<text class="text" style="font-weight: bold;">已开通尊贵VIP</text>
					<image src="/static/image/travel/personal/vip03.png" style="width: 55px; height: 20px;"></image>
					
				</view>
				<view v-else class="open-vip" @tap="toLink('/pages/user/buyvip')">
						<image src="/static/image/travel/personal/vip01.png"></image>
						<text class="text" style="font-weight: bold;">开通VIP享受十余项尊贵特权</text>
						<image src="/static/image/travel/personal/vip03.png" style="width: 55px; height: 20px;"></image>
				</view>
				<view class="tool">
					<view style="display: flex;align-items: center;" @tap="toLink('/pages/user/userpost')">
						<image src="/static/image/travel/personal/member.png"></image>
							<text>帖子</text>
					</view>
					<view style="display: flex;align-items: center;" @tap="toLink('/pages/user/usermark')">
						<image src="/static/image/travel/personal/house.png" ></image>
							<text>足迹</text>
					</view>
					<view style="display: flex;align-items: center;" @tap="toLink('/pages/user/assets')">
						<image src="/static/image/travel/personal/money.png" ></image>
							<text>钱包</text>
					</view>
					<view style="display: flex;align-items: center;" @tap="toLink('/pages/user/userexp')">
						<image src="/static/image/travel/personal/task.png" ></image>
							<text>签到</text>
					</view>
					
				</view>
				
				<view class="set">
					<view @tap="toManage" v-if="group=='administrator'||group=='editor'">
						<view class="tn-flex-1 tn-flex tn-flex-col-center">
							<text class="tn-icon-set" style="margin-left:8px"></text>
							<text>管理中心</text>
						</view>
						<view class="tn-flex tn-text-justify">
							
							<text>请勿滥用权限</text>
							<image class="right" src="../../static/image/travel/personal/Clipped.png">
						</view>
					</view>
				<view @tap="toLink('/pages/user/myshop')">
					 	<text class="tn-icon-shop" style="margin-left:8px"></text>
					 	<view class="tn-flex-1">
					 		<text>我的商品</text>
					 	</view>
					 		<image class="right"  style="float: right;" src="../../static/image/travel/personal/Clipped.png">
					 </view>
					<view @tap="toLink('/pages/user/sellorder')">
						<text class="tn-icon-order" style="margin-left:8px"></text>
						<view class="tn-flex-1">
							<text>售出订单</text>
						</view>
							<image class="right"  style="float: right;" src="../../static/image/travel/personal/Clipped.png">
					</view>
					<view  @tap="toSetUp">
						<view class="tn-flex-1 tn-flex tn-text-justify">
						<text class="tn-icon-identity" style="margin-left:8px"></text>
							<text>账户设置</text>
						</view>
						<image class="right"  style="float: right;" src="../../static/image/travel/personal/Clipped.png">
					</view>
					<view @tap="toMedia">
						<text class="tn-icon-service" style="margin-left:8px"></text>
						<view class="tn-flex-1">
						
							<text>联系我们</text>
						</view>
							<image class="right"  style="float: right;" src="../../static/image/travel/personal/Clipped.png">
					</view>
					<!-- 插件检测：如果插件被打开，则显示插件页面入口 -->
					<view v-if="sy_example" @tap="toLink('/pages/plugins/sy_example/home')">
						<text class="tn-icon-set" style="margin-left:8px"></text>
						<view class="tn-flex-1">
							<text>示例插件入口</text>
						</view>
							<image class="right"  style="float: right;" src="../../static/image/travel/personal/Clipped.png">
					</view>
					
				</view>
			</view>
			
		</view>
	
		
		<!--  #ifdef APP-PLUS -->
		<view style="height: 100upx;"></view>
		<Tabbar ref="tabbar" :current="3" :night="profileNight"></Tabbar>
		<!--  #endif -->
		<!--  #ifdef H5 -->
		<PublishPanel ref="publishPanel" :visible="false" :night="profileNight" :auto-intro="false"></PublishPanel>
		<!--  #endif -->
		
		<view class="cu-modal userLoginstatus" :class="isLoginShow?'show':''">
			<view class="cu-dialog">
				<view class="padding-sm">
					<view class="padding flex flex-direction">
						<view class="userLoginstatus-i bg-red">
							<text class="cuIcon-close"></text>
						</view>
						<view class="text-bold">登录状态失效,可能是数据被自动清理或异地登陆</view>
						
						<button class="cu-btn bg-blue margin-top" @tap="isLoginShow=false">确定</button>
					</view>
				</view>
			
			</view>
		</view>
	</view>
</template>

<script>
	import waves from '@/components/xxley-waves/waves.vue';
	import CampusThemeToggle from '@/pages/components/CampusThemeToggle.vue'
	import { applyCampusThemeShell, getCampusThemeMode, isDongchangfuNight, resolveCampusNight } from '@/utils/campusTheme.js'
	// #ifdef APP-PLUS
	import Tabbar from '@/pages/components/tabBar.vue'
	// #endif
	import { localStorage } from '../../js_sdk/mp-storage/mp-storage/index.js'
import { data } from '../../static/app-plus/owo/OwO.js';
	export default {
		data() {
			return {
				profileTab: 1,
				profileSpaceList: [],
				profileSpaceLoading: false,
				showProfileMenu: false,
				StatusBar: this.StatusBar,
				CustomBar: this.CustomBar,
				NavBar:this.StatusBar +  this.CustomBar,
				AppStyle:this.$store.state.AppStyle,
				userInfo:null,
				name:"",
				uid:0,
				token:"",
				userData:{},
				isClock:0,
				group:"",
				avatar:"",
				fancount:0,
				isvip:0,
				vip:0,
				vipDiscount:0,
				vipPrice:0,
				userlvStyle:"",
				lvStyle:"",
				assetsname:"",
				appname:this.$API.GetappJC(),
				isLoginShow:false,
				sy_example:false,//插件变量定义
				noticeSum:0,
				profileThemeClock: Date.now(),
				profileThemeTimer: null,
				campusThemeMode: 'auto',

				
			}
		},
		computed: {
			profileNight() {
				return resolveCampusNight(this.campusThemeMode, isDongchangfuNight(this.profileThemeClock))
			}
		},
		onPullDownRefresh(){
			var that = this;
			
		},
		onShow(){
			var that = this;
			that.loadCampusThemeMode();
			that.startProfileThemeClock();
			that.$nextTick(function() {
				// #ifdef APP-PLUS
				if (that.$refs.tabbar) that.$refs.tabbar.activate()
				// #endif
				// #ifdef H5
				if (that.$refs.publishPanel) that.$refs.publishPanel.activatePage()
				// #endif
			})
			// #ifdef APP-PLUS
			uni.hideTabBar({
				animation: false
			})
			
			plus.navigator.setStatusBarStyle(that.profileNight ? "light" : "dark")
			// #endif
			if(localStorage.getItem('userinfo')){
				
				that.userInfo = JSON.parse(localStorage.getItem('userinfo'));
				that.userInfo.style = "background-image:url("+that.userInfo.avatar+");"
				that.avatar = that.userInfo.avatar;
				that.uid = that.userInfo.uid;
				that.group = that.userInfo.group;
				if(that.userInfo.screenName){
					that.name = that.userInfo.screenName;
				}else{
					that.name = that.userInfo.name;
				}
			}else{
				that.userInfo =null;
				that.uid = 0;
				that.avatar = '';
			}
			if(localStorage.getItem('token')){
				
				that.token = localStorage.getItem('token');
			}else{
				that.token = "";
			}
			that.getUserData();
			that.userStatus();
			that.unreadNum();
			that.getProfileSpaceList();
			
		},
		onHide() {
			this.stopProfileThemeClock();
		},
		onUnload() {
			uni.$off('campus-signin-updated', this.handleSigninUpdated);
			this.stopProfileThemeClock();
		},


		onLoad() {
			var that = this;
			// #ifdef APP-PLUS || MP
			that.NavBar = this.CustomBar;
			// #endif
			uni.$on('campus-signin-updated', that.handleSigninUpdated);
			//插件检测
			var cachedPlugins = localStorage.getItem('getPlugins');
			if (cachedPlugins) {
				const pluginList = JSON.parse(cachedPlugins);
				// 检查插件是否存在于插件列表中
				that.sy_example = pluginList.includes('sy_example'); // 检测'sy_example'插件是否安装并开启
			}
			
		},
		mounted() {
				 this.getset();
				 
				},
		methods: {
			handleSigninUpdated() {
				this.isClock = 1;
				this.getUserData();
				this.userStatus();
			},
			loadCampusThemeMode() {
				this.campusThemeMode = getCampusThemeMode()
				applyCampusThemeShell(this.campusThemeMode, this.profileThemeClock)
			},
			handleCampusThemeMode(mode) {
				this.campusThemeMode = mode
				// #ifdef APP-PLUS
				this.$nextTick(() => plus.navigator.setStatusBarStyle(this.profileNight ? 'light' : 'dark'))
				// #endif
			},
			startProfileThemeClock() {
				this.stopProfileThemeClock()
				this.profileThemeClock = Date.now()
				applyCampusThemeShell(this.campusThemeMode, this.profileThemeClock)
				const nextHour = (Math.floor(this.profileThemeClock / (60 * 60 * 1000)) + 1) * 60 * 60 * 1000
				this.profileThemeTimer = setTimeout(() => this.startProfileThemeClock(), nextHour - this.profileThemeClock + 120)
			},
			stopProfileThemeClock() {
				if (!this.profileThemeTimer) return
				clearTimeout(this.profileThemeTimer)
				this.profileThemeTimer = null
			},
			getProfileSpaceList() {
				const that = this
				if (!that.uid) {
					that.profileSpaceList = []
					that.profileSpaceLoading = false
					return
				}
				that.profileSpaceLoading = true
				const searchParams = { uid: that.uid }
				that.$Net.request({
					url: that.$API.spaceList(),
					data: {
						searchParams: JSON.stringify(that.$API.removeObjectEmptyKey(searchParams)),
						limit: 20,
						page: 1,
						order: 'created',
						token: that.token
					},
					method: 'get',
					dataType: 'json',
					success(res) {
						if (res.data.code === 1) {
							const list = res.data.data || []
							list.forEach((item) => {
								if (item.type === 0) item.picList = item.pic ? item.pic.split('||') : []
							})
							that.profileSpaceList = list.filter((item) => item.type === 0 || item.type === 4)
						} else {
							that.profileSpaceList = []
						}
						that.profileSpaceLoading = false
					},
					fail() {
						that.profileSpaceList = []
						that.profileSpaceLoading = false
					}
				})
			},
			getfsgz() {
			  var that = this;
			      uni.request({
			        url:that.$API.SMlikeall(),
			        method:'GET',
					data:{
						uid:that.uid
					},
			        dataType:"json",
			        success(res) {
					  that.fancount = res.data.fancount;
			        },
			        fail(error) {
			          console.log(error);
			        }
			      })
				  
			},
			getset() {
			  var that = this;
			      uni.request({
			        url:that.$API.SMset(),
			        method:'GET',
			        dataType:"json",
			        success(res) {
					  that.assetsname = res.data.assetsname;
			        },
			        fail(error) {
			          console.log(error);
			        }
			      })
			},
			
			toLogin(){
				var that = this;
				
				// #ifdef MP-WEIXIN
				//that.toWexinlogin();
				//return false;
				// #endif
				// #ifdef MP-QQ
				// that.toQQlogin();
				// return false;
				// #endif
				uni.navigateTo({
					url: '/pages/user/login'
				});
				
			},
			tonlink(url){
									var url='http://'+url;
									uni.navigateTo({
									url:'/pages/user/webview?url='+url
									})
						
								},
			toPost() {
				if(!this.userInfo) {
					uni.showToast({
						title: "请先登录哦",
						icon: 'none'
					})
					return;
				}
				uni.navigateTo({
					url: '/pages/user/post'
				})
			},
			toSetup() {
				uni.navigateTo({
					url: '/pages/user/setup'
				})
			},
			toWexinlogin(){
				//微信登陆
				//后端直接根据unionId来判断用户的唯一性。
				uni.showLoading({
					title: "加载中"
				});
				uni.login({
					provider: 'weixin',
					success: res => {
						uni.getUserInfo({
							provider: 'weixin',
							success: function(infoRes) {
								console.log(JSON.stringify(infoRes));
								let formdata = {
									nickName: infoRes.userInfo.nickName,
									//gender: infoRes.userInfo.gender,
									appLoginType:"weixin",
				                    headImgUrl: infoRes.userInfo.avatarUrl,
									openId: infoRes.userInfo.openId,
									accessToken: infoRes.userInfo.unionId
								};
								that.$Net.request({
									
									url: that.$API.userApi(),
									data:{"params":JSON.stringify(that.$API.removeObjectEmptyKey(formdata))},
									header:{
										'Content-Type':'application/x-www-form-urlencoded'
									},
									method: "get",
									dataType: 'json',
									success: function(res) {
										setTimeout(function () {
											uni.hideLoading();
										}, 1000);
										uni.showToast({
											title: res.data.msg,
											icon: 'none'
										})
										if(res.data.code==1){
											//保存用户信息
											localStorage.setItem('userinfo',JSON.stringify(res.data.data));
											localStorage.setItem('token',res.data.data.token);
											var timer = setTimeout(function() {
												uni.reLaunch({
													url: '/pages/home/home'
												})
												clearTimeout('timer')
											}, 1000)
										}
									},
									fail: function(res) {
										setTimeout(function () {
											uni.hideLoading();
										}, 1000);
										uni.showToast({
											title: "网络不太好哦~",
											icon: 'none'
										})
										uni.stopPullDownRefresh()
									}
								})
								
							}
						});
					},
					fail: err => {
						uni.showToast({
							title: '请求出错啦！',
							icon: 'none',
							duration: 3000
						});
						setTimeout(function () {
							uni.hideLoading();
						}, 1000);
					}
				});
			},
			toQQlogin(){
				//QQ登陆
				//后端直接根据access_token来判断用户的唯一性。
				uni.showLoading({
					title: "加载中"
				});
				uni.login({
					provider: 'qq',
					success: resp => {
						var access_token = resp.authResult.access_token;
						uni.getUserInfo({
							provider: 'qq',
							success: function(infoRes) {
								
								var formdata = {
									nickName: infoRes.userInfo.nickname,
									//gender: infoRes.userInfo.gender == '男' ? 1 : 2,
									appLoginType:"qq",
				                    headImgUrl: infoRes.userInfo.figureurl_qq_2,
									openId: infoRes.userInfo.openId,
									accessToken: access_token
								};
								
								that.$Net.request({
									
									url: that.$API.userApi(),
									data:{"params":JSON.stringify(that.$API.removeObjectEmptyKey(formdata))},
									header:{
										'Content-Type':'application/x-www-form-urlencoded'
									},
									method: "get",
									dataType: 'json',
									success: function(res) {
										setTimeout(function () {
											uni.hideLoading();
										}, 1000);
										uni.showToast({
											title: res.data.msg,
											icon: 'none'
										})
										if(res.data.code==1){
											//保存用户信息
											localStorage.setItem('userinfo',JSON.stringify(res.data.data));
											localStorage.setItem('token',res.data.data.token);
											var timer = setTimeout(function() {
												uni.reLaunch({
													url: '/pages/home/home'
												})
												clearTimeout('timer')
											}, 1000)
										}
									},
									fail: function(res) {
										setTimeout(function () {
											uni.hideLoading();
										}, 1000);
										uni.showToast({
											title: "网络不太好哦~",
											icon: 'none'
										})
										uni.stopPullDownRefresh()
									}
								})
								
							}
						});
					},
					fail: err => {
						uni.showToast({
							title: '请求出错啦！',
							icon: 'none',
							duration: 3000
						});
						setTimeout(function () {
							uni.hideLoading();
						}, 1000);
					}
				});
			},
			getUserLv(i){
				var that = this;
				var rankList = that.$API.GetRankList();
				var rankStyle = that.$API.GetRankStyle();
				that.userlvStyle ="color:#fff;background-color: "+rankStyle[i];
				return rankList[i];
			},
			getLv(i){
				var that = this;
				var lv  = that.$API.getLever(i);
				var leverList = that.$API.GetLeverList();
				var rankStyle = that.$API.GetRankStyle();
				that.lvStyle ="color:#fff;background-color: "+rankStyle[lv];
				return leverList[lv];
			},
			toLink(text){
				var that = this;
				
				if(!localStorage.getItem('token')||localStorage.getItem('token')==""){
					uni.showToast({
						title: "请先登录哦",
						icon: 'none'
					})
					return false;
				}
				uni.navigateTo({
					url: text
				});
			},
			toPage(title,cid){
				var that = this;
				
				uni.navigateTo({
				    url: '/pages/contents/info?cid='+cid+"&title="+title
				});
			},
			goPage(url){
				var that = this;
				
				uni.navigateTo({
				    url: url
				});
			},
			toGroup(){
				var url = that.$API.GetGroupUrl();
				// #ifdef APP-PLUS
				plus.runtime.openURL(url) 
				// #endif
				// #ifdef H5
				window.open(url)
				// #endif
			},
			getUserData() {
				var that = this;
				that.$Net.request({
					
					url: that.$API.getUserData(),
					data:{
						"token":that.token
					},
					header:{
						'Content-Type':'application/x-www-form-urlencoded'
					},
					method: "get",
					dataType: 'json',
					success: function(res) {
						//console.log(JSON.stringify(res));
						that.syncDynamicCommentCount();
						if(res.data.code==1){
							var profileData = res.data.data || {};
							that.userData = profileData;
							// Use the same database-backed counter as followList.
							if(profileData.followNum != null){
								that.fancount = profileData.followNum;
							}else if(profileData.follow != null){
								that.fancount = profileData.follow;
							}
							that.isClock = profileData.isClock;
						}
					},
					fail: function(res) {
						uni.showToast({
							title: "网络不太好哦~",
							icon: 'none'
						})
					}
				})
			},
			syncDynamicCommentCount() {
				var that = this;
				if (!that.uid) return;
				that.$Net.request({
					url: that.$API.spaceList(),
					data: {
						searchParams: JSON.stringify({uid: that.uid, type: 3}),
						token: that.token,
						page: 1,
						limit: 1
					},
					header: {'Content-Type':'application/x-www-form-urlencoded'},
					method: 'get',
					dataType: 'json',
					success: function(res) {
						if (res.data.code == 1 && res.data.total != null) {
							that.userData.comments = res.data.total;
							that.userData.commentsNum = res.data.total;
						}
					}
				});
			},
			formatNumber(num) {
			    return num >= 1e3 && num < 1e4 ? (num / 1e3).toFixed(1) + 'k' : num >= 1e4 ? (num / 1e4).toFixed(1) + 'w' : num
			},
			toClock(){
				var that = this;
				
				uni.navigateTo({
				    url: '/pages/user/userexp'
				});
			},
			toScan(){
				var that = this;
				uni.scanCode({
					onlyFromCamera: false,
					scanType: ['barCode', 'qrCode'],
					success: function(res) {
						var text = res.result;
						var strUrl= "^((https|http|ftp|rtsp|mms)?://)" +
					   "?(([0-9a-z_!~*'().&=+$%-]+: )?[0-9a-z_!~*'().&=+$%-]+@)?"+
					   "(([0-9]{1,3}\.){3}[0-9]{1,3}" +
					   "|"+
					   "([0-9a-z_!~*'()-]+\.)*" +
					   "([0-9a-z][0-9a-z-]{0,61})?[0-9a-z]\." +
					   "[a-z]{2,6})" +
					   "(:[0-9]{1,4})?"+
					   "((/?)|"+
					   "(/[0-9a-z_!~*'().;?:@&=+$,%#-]+)+/?)$";
						var urlDemo = new RegExp(strUrl); 
						if(urlDemo.test(text)) {
							var linkStar = that.$API.GetlinkStar();
							var linkStarArr = linkStar.split("{cid}");
							if(text.indexOf(linkStarArr[0])!=-1){
								//是本站链接
								var cid = text;
								for(var i in linkStarArr){
									cid = cid.replace(linkStarArr[i],"");
								}
								uni.navigateTo({
									url: '/pages/contents/info?cid='+cid
								});
							}else{
								// #ifdef MP
								uni.setClipboardData({
								  data: href,
								  success: () =>
									uni.showToast({
									  title: '链接已复制'
									})
								})
								// #endif
								// #ifdef APP-PLUS
								plus.runtime.openWeb(href)
								// #endif
							}
						}else{
							that.scanLogin(text);
						}
					}
				});
			},
			scanLogin(text){
				var that = this;
				if(that.token==""){
					uni.showToast({
						title: "请先登录",
						icon: 'none'
					})
					return false;
				}
				if(that.isJSON(text)){
					text = JSON.parse(text);
				}else{
					uni.showToast({
						title: "无法解析的内容！",
						icon: 'none'
					})
					return false;
				}
				if(text.type){
					if(text.type!="Scan"){
						uni.showToast({
							title: "无法解析的内容！",
							icon: 'none'
						})
						return false;
					}
				}
				uni.navigateTo({
				    url: '/pages/user/scan?text='+text.data
				});
				
			},
			toSearch(){
				var that = this;
				
				uni.navigateTo({
				    url: '/pages/contents/search'
				});
			},
			
			userStatus() {
				var that = this;
				that.$Net.request({
					
					url: that.$API.userStatus(),
					data:{
						"token":that.token
					},
					header:{
						'Content-Type':'application/x-www-form-urlencoded'
					},
					method: "get",
					dataType: 'json',
					success: function(res) {
						if(res.data.code==1){
							var latestUser = Object.assign({}, that.userInfo || {}, res.data.data || {});
							latestUser.campus = res.data.data.campus || '';
							latestUser.grade = res.data.data.grade || '';
							that.userInfo = latestUser;
							that.name = latestUser.screenName || latestUser.name || that.name;
							that.assets = latestUser.assets;
							that.vip = latestUser.vip;
							that.isvip = latestUser.isvip;
							localStorage.setItem('userinfo', JSON.stringify(latestUser));
						}else if(res.data.code==0){
							if(that.userInfo != null){
								that.isLoginShow = true;
							}
							localStorage.removeItem('userinfo');
							localStorage.removeItem('token');
							that.userInfo = null;
						}
					},
					fail: function(res) {
						uni.showToast({
							title: "网络不太好哦",
							icon: 'none'
						})
					}
				})
			},
			getVipInfo(){
				var that = this;
				that.$Net.request({
					url: that.$API.getVipInfo(),
					header:{
						'Content-Type':'application/x-www-form-urlencoded'
					},
					method: "get",
					dataType: 'json',
					success: function(res) {
						if(res.data.code==1){
							that.vipDiscount=res.data.data.vipDiscount;
							that.vipPrice=res.data.data.vipPrice;
							that.scale=res.data.data.scale;
						}
						var timer = setTimeout(function() {
							that.isLoading=1;
							clearTimeout('timer')
						}, 300)
					},
					fail: function(res) {
						var timer = setTimeout(function() {
							that.isLoading=1;
							clearTimeout('timer')
						}, 300)
					}
				})
			},
			tovipDiscount(num){
				if(Number(num)<=0){
					return 0;
				}else{
					num = num.toString();
					num = num.replace("0.","");
					return num;
				}
				},
			getLvStyle(i){
				var that = this;
				if(!i){
					var i = 0;
				}
				var lv  = that.$API.getLever(i);
				var rankStyle = that.$API.GetRankStyle();
				var userlvStyle ="border: 3upx solid " + rankStyle[lv] + ";color:" + rankStyle[lv] + ";background-color: transparent;border-radius: 40upx;";
				return userlvStyle;
			},
			toMedia(){
				uni.navigateTo({
				    url: '/pages/user/media'
				});
			},
			toSetUp(){
				var that = this;
				
				uni.navigateTo({
				    url: '/pages/user/setup'
				});
			},
			toManage(){
				uni.navigateTo({
				    url: '/pages/user/manage'
				});
			},
			toScan(){
				var that = this;
				uni.scanCode({
					onlyFromCamera: false,
					scanType: ['barCode', 'qrCode'],
					success: function(res) {
						var text = res.result;
						var strUrl= "^((https|http|ftp|rtsp|mms)?://)" +
					   "?(([0-9a-z_!~*'().&=+$%-]+: )?[0-9a-z_!~*'().&=+$%-]+@)?"+
					   "(([0-9]{1,3}\.){3}[0-9]{1,3}" +
					   "|"+
					   "([0-9a-z_!~*'()-]+\.)*" +
					   "([0-9a-z][0-9a-z-]{0,61})?[0-9a-z]\." +
					   "[a-z]{2,6})" +
					   "(:[0-9]{1,4})?"+
					   "((/?)|"+
					   "(/[0-9a-z_!~*'().;?:@&=+$,%#-]+)+/?)$";
						var urlDemo = new RegExp(strUrl); 
						if(urlDemo.test(text)) {
							var linkStar = that.$API.GetlinkStar();
							var linkStarArr = linkStar.split("{cid}");
							if(text.indexOf(linkStarArr[0])!=-1){
								//是本站链接
								var cid = text;
								for(var i in linkStarArr){
									cid = cid.replace(linkStarArr[i],"");
								}
								uni.navigateTo({
									url: '/pages/contents/info?cid='+cid
								});
							}else{
								// #ifdef MP
								uni.setClipboardData({
								  data: href,
								  success: () =>
									uni.showToast({
									  title: '链接已复制'
									})
								})
								// #endif
								// #ifdef APP-PLUS
								plus.runtime.openWeb(href)
								// #endif
							}
						}else{
							that.scanLogin(text);
						}
					}
				});
			},
			scanLogin(text){
				var that = this;
				if(that.token==""){
					uni.showToast({
						title: "请先登录",
						icon: 'none'
					})
					return false;
				}
				if(that.isJSON(text)){
					text = JSON.parse(text);
				}else{
					uni.showToast({
						title: "无法解析的内容！",
						icon: 'none'
					})
					return false;
				}
				if(text.type){
					if(text.type!="Scan"){
						uni.showToast({
							title: "无法解析的内容！",
							icon: 'none'
						})
						return false;
					}
				}
				uni.navigateTo({
				    url: '/pages/user/scan?text='+text.data
				});
				
			},
			toPage(title,cid){
				var that = this;
				
				uni.navigateTo({
				    url: '/pages/contents/info?cid='+cid+"&title="+title
				});
			},
			goStyle(){
				var that = this;
				
				uni.navigateTo({
				    url: '/pages/user/clothes'
				});
			},
			isJSON(str) {
			
			    if (typeof str == 'string') {
			        try {
			            var obj=JSON.parse(str);
			            if(typeof obj == 'object' && obj ){
			                return true;
			            }else{
			                return false;
			            }
			        } catch(e) {
			            console.log('error：'+str+'!!!'+e);
			            return false;
			        }
			    }
			},
			unreadNum() {
				var that = this;
				that.$Net.request({
					
					url: that.$API.unreadNum(),
					data:{
						"token":that.token
					},
					header:{
						'Content-Type':'application/x-www-form-urlencoded'
					},
					method: "get",
					dataType: 'json',
					success: function(res) {
						if(res.data.code==1){
							that.noticeSum = res.data.data;
						}
					},
					fail: function(res) {
						uni.showToast({
							title: "网络不太好哦~",
							icon: 'none'
						})
					}
				})
			},
			goFanList(uid){
				var that = this;
				
				uni.navigateTo({
				    url: '/pages/user/fanList?uid='+uid
				});
			},
			tonlink41(url){
									var url='https://'+url;
									uni.navigateTo({
									url:'/pages/user/webview?url='+url
									})
						
								},

			toWeb(url) {
				// #ifdef APP-PLUS
				plus.runtime.openURL(url)
				// #endif
				// #ifdef H5
				window.open(url)
				// #endif
			},
			toUserContents(){
				var that = this;
				var name = that.name;
				var title = that.name+"的信息";
				var id= that.uid;
				var type="user";
				uni.navigateTo({
				    url: '/pages/contents/userinfo?title='+title+"&name="+name+"&uid="+id+"&avatar="+encodeURIComponent(that.avatar)
				});
			},
			toAssets() {
				uni.navigateTo({
					url: '/pages/user/assets'
				})
			}
		},
		// #ifdef APP-PLUS
		components: {
			waves,
			Tabbar,
			CampusThemeToggle
		},
		// #endif

		// #ifdef H5
		components: {
			CampusThemeToggle
		},
		// #endif
		
		// #ifdef MP
		components: {
			waves,
			CampusThemeToggle
		},
		// #endif
	}
</script>
<style scoped>
	.list-margin{
		margin: 0 20rpx;
	}
	.margin-ver {
		margin: 20rpx 20rpx;
	}
	::v-deep .cu-list.menu>.cu-item{
		background-color: transparent;
	}
	
	.account-pay {
		background-image: url('../../static/page/bg_my_vip.png');
		background-repeat: no-repeat;
		background-size: 100% 100%; 
		height: 96rpx;
		line-height: 72rpx;
		border-radius: 49rpx;
		/* color: #fff; */
	}
	::v-deep .account-pay .cu-item {
		min-height: auto;
		height: 72rpx;
	}
	::v-deep .cu-list.menu-avatar>.cu-item:after, .cu-list.menu>.cu-item:after {
		border: none;
	}
</style>
<style lang="scss" scoped>
	.homepage {
		width: 100%;

		& text {
			color: #333333;
			font-family: PingFangSC-Semibold, PingFang SC;
		}
		
		& text.cuIcon {
			font-family: 'cuIcon';
		}

		.bar {
			.right {
				display: flex;

				& view {
					width: 52rpx;
					height: 52rpx;
					margin-left: 26rpx;
					border-radius: 26rpx;
					display: flex;
					justify-content: center;
					align-items: center;
					box-shadow: 0rpx -2rpx 2rpx 4rpx rgba(255, 255, 255, 0.5000), 0rpx 4rpx 4rpx 0rpx rgba(197, 183, 211, 0.5000), inset 0rpx 2rpx 6rpx 0rpx rgba(255, 255, 255, 0.5000);

					>image {
						width: 32rpx;
						height: 32rpx;
					}
				}
			}
		}

		.people {
			padding: 0 42rpx 28rpx 32rpx;
			display: flex;
			align-items: center;

			.headImg {
				// >image {
				// 	width: 166rpx;
				// 	height: 166rpx;
				// 	border-radius: 83rpx;
				// }
				width: 166rpx;
				height: 166rpx;
				border-radius: 83rpx;;
				overflow: hidden;
				margin-right: 20rpx;
			}

			.info {
				flex: 1;

				.nick {
					display: flex;

					>text {
						font-size: 36rpx;
						font-weight: 600;
						line-height: 50rpx;
					}

					.sex {
						width: 24rpx;
						height: 24rpx;
						border-radius: 12rpx;
						background: #61C9FD;
					}
				}

				.grade {
					display: flex;
					align-items: center;

					>view {
						display: flex;
						align-items: center;
						margin-right: 12rpx;

						& text {
							font-size: 20rpx;
							font-weight: 600;
							color: #FFFFFF;
							line-height: 28rpx;
							text-shadow: 0rpx 2rpx 4rpx #cbffea;
						}

						& image {
							width: 28rpx;
							height: 30rpx;
						}

						&:last-child {
							>image {
								width: 40rpx;
								height: 40rpx;
							}

							>text {
								margin-left: -6rpx;
							}
						}
					}
				}

				.userId {
					width: 220rpx;
					display: flex;
					background: #F5F5FF;
					border-radius: 8rpx;
					box-shadow: 0rpx 2rpx 6rpx 0rpx rgba(0, 0, 0, 0.1400), 0rpx -4rpx 6rpx 0rpx #FFFFFF;

					>image {
						width: 36rpx;
						height: 40rpx;
					}

					.number {
						flex: 1;
						display: flex;
						justify-content: center;

						>text {
							font-size: 24rpx;
							font-weight: 600;
							line-height: 40rpx;

							&:last-child {
								font-weight: 500;
								font-size: 22rpx;
								margin-left: 8rpx;
							}
						}
					}
				}
			}

			.space {
				display: flex;
				align-items: center;

				>text {
					font-size: 28rpx;
					line-height: 40rpx;
				}
			}
		}

		.list {
			width: 100%;
			display: flex;
			padding: 0 44rpx;
			box-sizing: border-box;

			.item {
				width: 25%;
				display: flex;
				justify-content: space-evenly;
				align-items: center;

				.text {
					display: flex;
					flex-direction: column;
					align-items: center;

					>text:first-child {
						font-size: 36rpx;
						font-family: CloudHeiChaoGBK;
						line-height: 48rpx;
						font-weight: 600;
					}

					>text:last-child {
						font-size: 24rpx;
						color: #999999;
						line-height: 34rpx;
					}
				}
			}
		}
.xyy{
	margin-left: 0px;
}
		.infos {
			padding: 0 40rpx;

			.open-vip {
				width: 100%;
				height: 72rpx;
				background: linear-gradient(180deg, #F7E5B4 0%, #FFE6AF 2%, #EBC075 100%);
				border-radius: 49rpx;
				display: flex;
				align-items: center;
				margin-top: 36rpx;
				padding: 0 24rpx 0 34rpx;
				box-sizing: border-box;

				>image {
					width: 48rpx;
					height: 48rpx;
				}

				.text {
					flex: 1;
					font-size: 24rpx;
					line-height: 34rpx;
					margin-left: 14rpx;
				}

				.button {
					width: 128rpx;
					height: 42rpx;
					background: linear-gradient(90deg, #4D4D4D 0%, #151515 100%);
					border-radius: 22rpx;
					font-size: 22rpx;
					color: #FFDFA9;
					line-height: 42rpx;
					text-align: center;
				}
			}

			.tool {
				display: flex;
				width: 100%;
				height: 172rpx;
				background: #FFFFFF;
				box-shadow:  0rpx 2rpx 28rpx 0rpx #c2c2c257;
				border-radius: 28rpx;
				justify-content: space-evenly;
				margin: 22rpx 0;

				>view {
					display: flex;
					flex-direction: column;

					& text {
						font-size: 22rpx;
						font-weight: 600;
						color: #666666;
						line-height: 32rpx;
					}

					& image {
						width: 98rpx;
						height: 96rpx;
						margin-top: 10rpx;
					}
				}
			}

			.set {
				width: 100%;
				padding: 34rpx 24rpx 44rpx 34rpx;
				background: #FFFFFF;
				box-shadow: 0rpx 2rpx 28rpx 0rpx #c2c2c257;
				border-radius: 28rpx;
				display: flex;
				flex-direction: column;
				justify-content: space-between;
				box-sizing: border-box;

				>view {
					display: flex;
					align-items: center;
					margin-bottom: 40rpx;
					
					&:last-child {
						margin-bottom: 0;
					}

					& text {
						font-size: 28rpx;
						line-height: 40rpx;
						margin-left: 30rpx;
					}

					.icon {
						width: 36rpx;
						height: 36rpx;
					}

					.right {
						width: 40rpx;
						height: 40rpx;
					}
				}
			}

			.service {
				background: #FFFFFF;
				box-shadow: 0rpx 2rpx 28rpx 0rpx rgba(142, 146, 230, 0.2700);
				border-radius: 28rpx;
				margin-top: 26rpx;
				padding: 34rpx 24rpx 44rpx 34rpx;
				display: flex;
				flex-direction: column;
				justify-content: space-between;

				>view {
					display: flex;
					align-items: center;
					margin-bottom: 40rpx;

					& text {
						flex: 1;
						font-size: 28rpx;
						line-height: 40rpx;
						margin-left: 30rpx;
					}

					.icon {
						width: 36rpx;
						height: 36rpx;
					}

					.right {
						width: 40rpx;
						height: 40rpx;
					}

				}
			}
		}
		.text-blue {
			color: #0081ff;
		}
		.margin-0 {
			margin-left: 0 !important;
		}
		.avatar {
			width: 100%;
			height: 100%;
			background-size: 100%;
		}
	}

	.campus-profile {
		position: relative;
		min-height: 100vh;
		background: #fff !important;
		color: #242628;
	}

	.immersive-profile {
		min-height: 100vh;
		padding-bottom: 164rpx;
		padding-bottom: calc(164rpx + env(safe-area-inset-bottom));
		background: #fff;
	}

	.profile-status-spacer {
		background: #fff;
	}

	.profile-cover {
		position: relative;
		min-height: 700rpx;
		padding: 0 34rpx 62rpx;
		background: #26302f;
		color: #fff;
		box-sizing: border-box;
		overflow: hidden;
	}

	.profile-cover-bg {
		position: absolute;
		inset: -34rpx;
		background-color: #2a3332;
		background-repeat: no-repeat;
		background-position: center;
		background-size: cover;
		filter: blur(24rpx) saturate(0.72) brightness(0.68);
		transform: scale(1.12);
		transform-origin: center;
	}

	.profile-cover-shade {
		position: absolute;
		inset: 0;
		background: linear-gradient(180deg, rgba(18, 25, 25, 0.44) 0%, rgba(20, 29, 29, 0.66) 54%, rgba(19, 25, 26, 0.92) 100%);
	}

	.profile-top-tools {
		position: relative;
		z-index: 2;
		display: flex;
		align-items: center;
		justify-content: space-between;
		padding-top: 18rpx;
	}

	.profile-tool {
		display: flex;
		align-items: center;
		justify-content: center;
		width: 66rpx;
		height: 66rpx;
		border-radius: 50%;
		background: rgba(20, 22, 23, 0.16);
		font-size: 36rpx;
		color: #fff;
		transition: transform 180ms ease, background-color 180ms ease;
	}

	.profile-tool:active {
		transform: scale(0.9);
		background: rgba(255, 255, 255, 0.16);
	}

	.profile-menu {
		position: absolute;
		z-index: 8;
		top: calc(92rpx + env(safe-area-inset-top));
		left: 28rpx;
		width: 280rpx;
		max-width: calc(100% - 56rpx);
		padding: 14rpx;
		border: 1rpx solid rgba(255, 255, 255, 0.22);
		border-radius: 22rpx;
		background: rgba(27, 29, 30, 0.94);
		box-shadow: 0 18rpx 50rpx rgba(0, 0, 0, 0.25);
		opacity: 0;
		visibility: hidden;
		transform: translateY(-12rpx) scale(0.96);
		transform-origin: top left;
		transition: opacity 180ms ease, transform 280ms cubic-bezier(0.22, 1, 0.36, 1), visibility 0s linear 280ms;
	}

	.profile-menu.is-open {
		opacity: 1;
		visibility: visible;
		transform: translateY(0) scale(1);
		transition-delay: 0s;
	}

	.profile-menu > view {
		display: flex;
		align-items: center;
		gap: 16rpx;
		height: 68rpx;
		padding: 0 16rpx;
		border-radius: 15rpx;
		font-size: 24rpx;
		color: rgba(255, 255, 255, 0.9);
	}

	.profile-menu > view:active {
		background: rgba(255, 255, 255, 0.1);
	}

	.profile-identity {
		position: relative;
		z-index: 2;
		display: flex;
		align-items: center;
		gap: 24rpx;
		margin-top: 76rpx;
	}

	.profile-avatar {
		flex: 0 0 auto;
		width: 134rpx;
		height: 134rpx;
		border: 4rpx solid rgba(255, 255, 255, 0.9);
		border-radius: 50%;
		background-position: center;
		background-size: cover;
		box-shadow: 0 12rpx 34rpx rgba(0, 0, 0, 0.22);
		box-sizing: border-box;
	}

	.profile-avatar-empty {
		display: flex;
		align-items: center;
		justify-content: center;
		background: rgba(255, 255, 255, 0.2);
		font-size: 52rpx;
	}

	.profile-name-block {
		flex: 1 1 auto;
		min-width: 0;
	}

	.profile-name {
		display: flex;
		align-items: center;
		gap: 10rpx;
		font-size: 34rpx;
		font-weight: 700;
		line-height: 48rpx;
	}

	.profile-name > text:first-child {
		min-width: 0;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.profile-name .cuIcon-unfold {
		font-size: 24rpx;
		font-weight: 400;
	}

	.profile-id,
	.profile-ip {
		display: flex;
		align-items: center;
		gap: 10rpx;
		margin-top: 8rpx;
		font-size: 23rpx;
		line-height: 32rpx;
		color: rgba(255, 255, 255, 0.68);
	}

	.profile-campus {
		margin-top: 6rpx;
		font-size: 22rpx;
		line-height: 30rpx;
		color: rgba(255, 255, 255, 0.62);
	}

	.profile-grade {
		margin-top: 2rpx;
		font-size: 21rpx;
		line-height: 29rpx;
		color: rgba(255, 255, 255, 0.56);
	}

	.profile-stats {
		position: relative;
		z-index: 2;
		display: flex;
		flex-wrap: wrap;
		gap: 34rpx;
		margin-top: 36rpx;
	}

	.profile-stats > view {
		display: flex;
		align-items: baseline;
		gap: 8rpx;
	}

	.profile-stats > view text:first-child {
		font-size: 32rpx;
		font-weight: 700;
		color: #fff;
	}

	.profile-stats > view text:last-child {
		font-size: 24rpx;
		color: rgba(255, 255, 255, 0.68);
	}

	.profile-signature,
	.profile-recommend,
	.profile-badges {
		position: relative;
		z-index: 2;
	}

	.profile-signature {
		margin-top: 34rpx;
		font-size: 26rpx;
		line-height: 1.6;
		color: rgba(255, 255, 255, 0.88);
		overflow-wrap: anywhere;
	}

	.profile-recommend {
		display: flex;
		align-items: center;
		gap: 10rpx;
		margin-top: 24rpx;
		font-size: 24rpx;
		color: rgba(255, 255, 255, 0.74);
	}

	.profile-badges {
		display: flex;
		flex-wrap: wrap;
		gap: 10rpx;
		margin-top: 18rpx;
	}

	.profile-badges text {
		padding: 5rpx 13rpx;
		border-radius: 15rpx;
		background: rgba(255, 255, 255, 0.12);
		font-size: 21rpx;
		color: rgba(255, 255, 255, 0.8);
	}

	.profile-badges .vip-badge {
		background: rgba(255, 186, 30, 0.86);
		color: #fff;
	}

	.profile-quick-actions {
		position: relative;
		z-index: 2;
		display: grid;
		grid-template-columns: repeat(3, minmax(0, 1fr));
		gap: 14rpx;
		margin-top: 30rpx;
	}

	.profile-quick-actions.has-manage-entry {
		grid-template-columns: repeat(4, minmax(0, 1fr));
		gap: 10rpx;
	}

	.profile-quick-actions.has-manage-entry > view {
		padding-right: 10rpx;
		padding-left: 10rpx;
	}

	.profile-quick-actions.has-manage-entry > view > view {
		gap: 6rpx;
		font-size: 22rpx;
	}

	.profile-quick-actions > view {
		display: flex;
		flex-direction: column;
		justify-content: center;
		min-width: 0;
		min-height: 104rpx;
		padding: 0 16rpx;
		border: 1rpx solid rgba(255, 255, 255, 0.08);
		border-radius: 18rpx;
		background: rgba(255, 255, 255, 0.13);
		box-sizing: border-box;
		transition: transform 180ms ease, background-color 180ms ease;
	}

	.profile-quick-actions > view:active {
		transform: scale(0.96);
		background: rgba(255, 255, 255, 0.16);
	}

	.profile-quick-actions > .profile-manage-action {
		border-color: rgba(133, 232, 215, 0.3);
		background: linear-gradient(145deg, rgba(87, 208, 188, 0.34), rgba(113, 171, 205, 0.24));
	}

	.profile-quick-actions > .profile-clock-action.is-clocked {
		border-color: rgba(255, 255, 255, 0.12);
		background: rgba(255, 255, 255, 0.1);
	}

	.profile-quick-actions > view > view {
		display: flex;
		align-items: center;
		gap: 9rpx;
		font-size: 24rpx;
		font-weight: 600;
		color: #fff;
	}

	.profile-quick-actions > view > text {
		margin-top: 8rpx;
		font-size: 20rpx;
		color: rgba(255, 255, 255, 0.5);
	}

	.profile-content-sheet {
		position: relative;
		z-index: 3;
		min-height: 61.8vh;
		margin-top: -36rpx;
		border-radius: 36rpx 36rpx 0 0;
		background: #fff;
		box-shadow: 0 -8rpx 30rpx rgba(0, 0, 0, 0.07);
		overflow: hidden;
	}

	.profile-content-tabs {
		display: flex;
		align-items: center;
		height: 86rpx;
		padding: 0 34rpx;
		border-bottom: 1rpx solid #eceff1;
		gap: 46rpx;
	}

	.profile-content-tabs > view {
		position: relative;
		height: 86rpx;
		font-size: 27rpx;
		line-height: 86rpx;
		color: #9a9da0;
		transition: color 180ms ease;
	}

	.profile-content-tabs > view.is-active {
		font-weight: 700;
		color: #252729;
	}

	.profile-content-tabs > view.is-active::after {
		position: absolute;
		left: 50%;
		bottom: 0;
		width: 40rpx;
		height: 6rpx;
		border-radius: 4rpx;
		background: #252729;
		content: '';
		transform: translateX(-50%);
	}

	.profile-dynamic-tab {
		gap: 0;
	}

	.profile-dynamic-list {
		padding: 14rpx 0 30rpx;
	}

	.profile-dynamic-list ::v-deep .space-feed {
		padding-right: 10rpx;
		padding-left: 10rpx;
	}

	.profile-dynamic-loading {
		display: flex;
		min-height: 360rpx;
		align-items: center;
		justify-content: center;
	}

	.profile-empty-state {
		display: flex;
		flex-direction: column;
		align-items: center;
		padding-top: 120rpx;
		color: #b4b7b9;
	}

	.profile-empty-state > text:first-child {
		font-size: 86rpx;
		color: #dadddf;
	}

	.profile-empty-state > text:nth-child(2) {
		margin-top: 26rpx;
		font-size: 26rpx;
	}

	.profile-empty-state > view {
		margin-top: 26rpx;
		padding: 14rpx 24rpx;
		border-radius: 20rpx;
		background: #f3f5f6;
		font-size: 22rpx;
		color: #767b7e;
	}


	/* Night mode uses quiet graphite surfaces with restrained school accents. */
	.campus-profile.campus-night {
		background: #15191b !important;
		color: #edf0ef;
	}

	.campus-profile.campus-night .immersive-profile,
	.campus-profile.campus-night .profile-status-spacer {
		background: #15191b;
	}

	.campus-profile.campus-night .profile-cover {
		background-color: #1b2022;
	}

	.campus-profile.campus-night .profile-cover-bg {
		filter: blur(26rpx) saturate(0.62) brightness(0.54);
	}

	.campus-profile.campus-night .profile-cover-shade {
		background: linear-gradient(180deg, rgba(17, 23, 24, 0.38) 0%, rgba(18, 26, 27, 0.7) 54%, rgba(20, 27, 28, 0.96) 100%);
	}

	.campus-profile.campus-night .profile-tool,
	.campus-profile.campus-night .profile-quick-actions > view {
		border-color: rgba(226, 232, 230, 0.1);
		background: rgba(35, 41, 43, 0.94);
		box-shadow: 0 10rpx 26rpx rgba(0, 0, 0, 0.18);
	}

	.campus-profile.campus-night .profile-quick-actions > .profile-manage-action {
		border-color: rgba(195, 164, 93, 0.22);
		background: #29332f;
	}

	.campus-profile.campus-night .profile-content-sheet {
		background: #1b2022;
		box-shadow: 0 -10rpx 30rpx rgba(0, 0, 0, 0.2);
	}

	.campus-profile.campus-night .profile-content-tabs {
		border-bottom-color: rgba(226, 232, 230, 0.09);
	}

	.campus-profile.campus-night .profile-content-tabs > view {
		color: #929c99;
	}

	.campus-profile.campus-night .profile-content-tabs > view.is-active {
		color: #edf0ef;
	}

	.campus-profile.campus-night .profile-content-tabs > view.is-active::after {
		background: #f4c95b;
	}

	.campus-profile.campus-night .profile-empty-state {
		color: #929c99;
	}

	.campus-profile.campus-night .profile-empty-state > text:first-child {
		color: rgba(111, 154, 132, 0.38);
	}

	.campus-profile.campus-night .profile-empty-state > view {
		background: #292f31;
		color: #dfe4e2;
	}

	.campus-profile.campus-night .profile-dynamic-list ::v-deep .space-feed .square-list > .cu-item2 {
		border-color: #34403f;
		background: #202728;
	}

	@media (max-width: 360px) {
		.profile-cover { padding-right: 24rpx; padding-left: 24rpx; }
		.profile-identity { gap: 18rpx; margin-top: 58rpx; }
		.profile-avatar { width: 116rpx; height: 116rpx; }
		.profile-name { font-size: 30rpx; }
		.profile-stats { gap: 24rpx; }
		.profile-quick-actions { gap: 10rpx; }
		.profile-quick-actions > view { min-height: 96rpx; padding: 0 12rpx; }
		.profile-quick-actions > view > view { font-size: 22rpx; }
	}

	@media (max-height: 700px) {
		.profile-cover { min-height: 650rpx; padding-bottom: 52rpx; }
		.profile-identity { margin-top: 54rpx; }
		.profile-stats { margin-top: 26rpx; }
		.profile-signature { margin-top: 26rpx; }
	}

	@media (prefers-reduced-motion: reduce) {
		.profile-menu,
		.profile-tool,
		.profile-quick-actions > view {
			transition: none;
		}
	}
</style>
