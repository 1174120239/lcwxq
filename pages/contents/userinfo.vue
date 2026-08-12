<template>
	<view class="userpost userIndex campus-subpage campus-profile-page" :class="AppStyle">
		<view class="header" :style="[{height:CustomBar*2 + 'upx'}]"  :class="scrollTop>40?'goScroll':''">
			<view class="cu-bar" :style="{'height': CustomBar*2 + 'upx','padding-top':StatusBar*2 + 'upx'}">
				<view :class="scrollTop<40 && isLoading !== 0 ?'action2 cu-bar2':''" class="action" @tap="back">
					<text class="cuIcon-back"></text>
				</view>
				<view class="content text-bold" style="height: 60upx;" :style="[{top:StatusBar*2 + 'upx'}]">
					<block v-if="scrollTop>40">
					<block v-if="uid==vid">
						<text :class="isvip ? 'text-shojo2' : ''">
							{{myname}}
						</text>
					</block>
					<block v-else>
						<text :class="isvip ? 'text-shojo2' : ''">
							{{name}}
						</text>
					</block>
					
					</block>
				</view>
				<!--  #ifdef H5 || APP-PLUS -->
				<view :class="scrollTop<40 && isLoading !== 0 ?'action2 cu-bar2':''" class="action" @tap="toSearch">
					<text class="cuIcon-search"></text>
				</view>
				<!--  #endif -->
			</view>
		</view>
		<!-- <view :style="[{padding:NavBar + 'px 10px 0px 10px'}]"></view> -->
		<view class="all-box" style="margin-top:-10upx">
			
			<view class="user-info" :style="'padding-top:'+NavBar*2 + 'upx;height:480upx;position:initial'">
				<view class="user-info-bg">
					<image :src="bgurl || avatar" mode="aspectFill" style="height:450upx"></image>
				</view>
				<view class="user-info-main" style="background-color: transparent;">
				  <view class="user-info-content" style="background: transparent;padding: 0upx 0upx 0upx 60upx;z-index: 99;">
				    <view class="user-info-col" style="overflow:visible">
				      <view class="user-header" style="width: 180upx;">
				        <image :src="avatar" style="width: 180upx;height: 180upx;border: 6upx solid white;border-radius:90upx;background-color:white;"></image>
				      </view>
				    </view>
					<div class="user-text" style="width: calc(100% - 200upx);color: black;text-align: center;">
					  <div class="user-info-data col-3" style="padding:65upx 20upx 0upx 20upx;">
					    <div class="user-info-data-box">
					      <div class="user-data-num">{{formatNumber(fanNum)}}<text v-if="fanNum>9999" class="sup-script">万</text></div>
					      <div class="user-data-label" style="opacity:1">粉丝</div>
					    </div>
					    <div class="user-info-data-box">
					      <div class="user-data-num">{{formatNumber(fancount)}}<text v-if="fancount>9999" class="sup-script">万</text></div>
					      <div class="user-data-label" style="opacity:1">关注</div>
					    </div>
					    <div class="user-info-data-box">
					      <div class="user-data-num">{{formatNumber(likesall)}}<text v-if="likesall>9999" class="sup-script">万</text></div>
					      <div class="user-data-label" style="opacity:1">获赞</div>
					    </div>
					  </div>
					</div>
				  </view>
				  <view class="bg-white padding-tb bg-white" style="position: absolute; width: 100%; bottom:0upx;border-radius: 32upx 32upx 0 0;height:120upx;"></view>
				</view>
			</view>
			<!--  #ifdef H5 || APP-PLUS -->
			<view class="user-name" style="padding: 10upx 60upx 0upx 60upx;">
			<text class="user-info-name" style="font-size: 45upx;font-weight: 600;color: black;" @click="copyName">
				<block v-if="uid==vid">
					<text :class="isvip ? 'text-shojo2' : ''">
						{{myname}}
					</text>
				</block>
				<block v-else>
					<text :class="isvip ? 'text-shojo2' : ''">
						{{name}}
					</text>
				</block>
			</text>
			<view class="user-info-data-box" style="text-align: left;margin-top: 8upx;">
				<text class="user-data-label" @click="copyUid">{{appname}}: </text>
				<text class="user-data-label" style="margin-right: 10upx;" @click="copyUid">{{uid}}</text>
				<text class="tn-icon-copy mirror" @click="copyUid"></text>
			</view>
			<view class="profile-campus" v-if="campus">{{campus}}</view>
			<view class="profile-grade" v-if="grade">{{grade}}</view>
			<view class="profile-private-fields" v-if="gender || birthday">
				<text v-if="gender">{{gender}}</text><text v-if="birthday">生日 {{birthday}}</text>
			</view>
			<view class="user-data-label profile-introduce" style="margin-top: 10upx;word-wrap: break-word">
				<block v-if="introduce!=''&&introduce">
					{{subText(introduce,60)}}
				</block>
				<block v-else>
					Ta还没有个人介绍哦
				</block>
			</view>
			<view class="user-info-data-box" style="margin-top: 10upx;text-align: left;">
				<text class="userlv" v-if="isvip" style="margin-left: 0px;background: linear-gradient(to bottom right, #f2ad5c, #e6216d,#901ccb);color:white;padding: 2upx 10upx;border-radius: 20upx;">VIP</text>
				<text class="userlv" :style="getLvStyle(experience)"  :class="isvip ? '' : 'xyy'" style="padding: 2upx 10upx;">{{getLv(experience)}}</text>
				<text class="userlv customize" style="border: 3upx solid black;color:black;padding: 2upx 10upx;border-radius: 40upx;background-color: transparent;" v-if="customize&&customize!=''">{{customize}}</text>
			</view>
			<view class="user-info-data-box" style="margin-top: 10upx;text-align: center;margin-right: 0upx;">
				<view v-if="uid==vid" class="userInfo-bottom-main grid2 col-2">
					<view class="userInfo-bottom-box" style="padding:0upx 15upx 0upx 0upx;width: 70%;">
						<button class="cu-btn bg-gray" style="border-radius: 100upx" @tap="toLink('/pages/user/useredit?backif=1')"><text class="tn-icon-edit-form"></text>编辑资料</button>
					</view>
					<view class="userInfo-bottom-box" style="padding:0upx 0upx 0upx 15upx;width: 30%;">
						<button class="cu-btn" style="border-radius: 100upx;background-color: #f0f0f0;color: #333333;" @tap="toSetup"><text class="tn-icon-set"></text>设置</button>
					</view>
				</view>
				<view v-else class="userInfo-bottom-main grid col-2">
					<view class="userInfo-bottom-box" style="padding:0upx 15upx 0upx 0upx;">
						<button class="cu-btn bg-gray" style="border-radius: 100upx;" @tap="follow(0)" v-if="isFollow==1"><text class="cuIcon-check"></text>已关注</button>
						<button class="cu-btn bg-shojo" style="border-radius: 100upx;" @tap="follow(1)" v-else><text class="cuIcon-add"></text>关注</button>
					</view>
					<view class="userInfo-bottom-box" v-if="privateChatEnabled" style="padding:0upx 0upx 0upx 15upx;">
						<button class="cu-btn" style="border-radius: 100upx;background-color: #f0f0f0;color: #333333;" @tap="getPrivateChat()"><text class="cuIcon-mark"></text>私聊</button>
					</view>
				</view>
			</view>
			</view>
			
			<!-- <view class="userinfo-lv"> -->
				<!--  #ifdef H5 || APP-PLUS -->
			<!-- <text class="userlv" :style="getUserLvStyle(lv)">{{getUserLv(lv)}}</text> -->
			<!--  #endif -->
			
			
			<!-- </view> -->
			
			<view class="search-type grid" :class="sy_appbox?'col-3':'col-2'">
				<view class="search-type-box" v-if="sy_appbox&&appModOrder==1" @tap="toType(4)"
					:class="type==4?'active':''">
					<text>应用</text>
				</view>
				<view class="search-type-box" v-if="sy_appbox&&appModOrder==0" @tap="toType(4)"
					:class="type==4?'active':''">
					<text>应用</text>
				</view>
				<view class="search-type-box" @tap="toType(2)" :class="type==2?'active':''">
					<text>动态</text>
				</view>
				<view class="search-type-box" @tap="toType(1)" :class="type==1?'active':''">
					<text>评论</text>
				</view>
				
			</view>
			<!--  #endif -->
			<!--  #ifdef MP -->
			<view class="search-type grid col-1">
				<view class="search-type-box" @tap="toType(2)" :class="type==2?'active':''">
					<text>动态</text>
				</view>
			</view>
			<!--  #endif -->
			
			<view class="cu-card article no-card" v-if="type==0">
				<block v-for="(item,index) in contentsList" :key="index" v-if="type==0">
					<articleItem :item="item"></articleItem>
				</block>
				<view class="load-more" @tap="loadMore" v-if="contentsList.length>0">
					<text>{{moreText}}</text>
				</view>
				<view class="no-data" v-if="contentsList.length==0">
					<text class="cuIcon-text"></text>
					暂时没有数据
				</view>

			</view>
			<view class="search-space" v-if="type==2">
				<view class="no-data" v-if="spaceList.length==0">
					<text class="cuIcon-text"></text>
					暂时没有动态
				</view>
				<spaceItem :spaceList="spaceList" :night="AppStyle === 'campus-night'"></spaceItem>
				<view class="load-more" @tap="loadMore" v-if="spaceList.length>0">
					<text>{{moreText}}</text>
				</view>
			</view>
			<!--评论-->
			<view class="cu-list menu-avatar" v-if="type==1">
				<view class="no-data" v-if="commentsList.length==0">
					<text class="cuIcon-text"></text>
					暂时没有评论
				</view>
				<view class="cu-card dynamic no-card" style="margin-top: 20upx;">
					<block  v-for="(item,index) in commentsList" :key="index" v-if="commentsList.length>0">
						<spaceReplyHistoryItem :item="item"></spaceReplyHistoryItem>
					</block>
					
				</view>
				
				<view class="load-more" @tap="loadMore" v-if="commentsList.length>0">
					<text>{{moreText}}</text>
				</view>
			</view>
			<!--评论结束-->
			<view class="margin-top-sm padding-sm" :class="'bg-white'" :style="{'background-color':'#ffffff'}" v-if="type==4">
				<!-- 添加加载动画 -->
				<view class="loading-container" v-if="apploading">
					<u-loading mode="circle" size="36"></u-loading>
				</view>
				
				<!-- 应用列表 -->
				<block v-if="applist.length>0">
					<view class="app-box" v-for="(item, index) in applist" :key="index">
						<view class="app-box-body" @tap="toAppInfo(item.id)">
							<view class="app-box-logo">
								<u-image :src="item.logo" width="110rpx" height="110rpx" mode="aspectFill" 
									:lazy-load="true" :fade="true" duration="450" border-radius="28rpx">
									<u-loading slot="loading"></u-loading>
								</u-image>
							</view>
							<view class="app-box-content">
								<view class="app-box-title text-cut">{{item.name}}</view>
								<view class="app-box-info">
									<text :style="{color: item.tagInfo.color}" 
										:class="item.score>=3?'tn-icon-star-fill':'tn-icon-star'"></text>
									<text :style="{color: item.tagInfo.color}">{{item.score}}</text>
									<text>{{item.size}}</text>
									<text :class="item.system=='ios'?'tn-icon-iphone':''"></text>
									</view>
									<view class="app-box-tags">
										<text class="app-tag"
											:style="{backgroundColor: item.tagInfo.color}">{{item.tagInfo.text}}</text>
										<text v-for="(category, idx) in item.sortJson" :key="idx"
											class="app-category-tag">{{category.name}}</text>
									</view>
							</view>
						</view>
						<view class="app-box-down" @tap="toAppInfo(item.id)">下载</view>
					</view>
				</block>
				
				<!-- 空数据提示 -->
				<block v-else>
					<view class="margin-top-sm">
						<u-empty text="暂无应用" mode="data" icon-size="100" font-size="24"></u-empty>
					</view>
				</block>
				
				<!-- 加载更多 -->
				<view class="load-more" @tap="loadMore" v-if="applist.length>0">
					<text>{{moreText}}</text>
				</view>
			</view>
			
			<!--占位区域-->
			<view class="profile-bottom-spacer"></view>
		</view>
		
		<!--加载遮罩-->
		<view class="loading" v-if="isLoading==0">
			<view class="loading-main">
				<view class="campus-loader"></view>
			</view>
		</view>
		<!--加载遮罩结束-->
	</view>
</template>

<script>
	import { localStorage } from '../../js_sdk/mp-storage/mp-storage/index.js'
	import featureFlags from '@/utils/featureFlags.js'
	// #ifdef APP-PLUS
	import owo, { data } from '../../static/app-plus/owo/OwO.js'
	// #endif
	// #ifdef H5
	import owo from '../../static/h5/owo/OwO.js'
	// #endif
	// #ifdef MP
	var owo = [];
	// #endif
	export default {
		data() {
			return {
				StatusBar: this.StatusBar,
				CustomBar: this.CustomBar,
				NavBar:this.StatusBar +  this.CustomBar,
				AppStyle:this.$store.state.AppStyle,
				privateChatEnabled: featureFlags.privateChat,
				
				contentsList:[],
				commentsList:[],
				spaceList:[],
				likesall:0,
				fancount:0,
				userList:[],
				owo:owo,
				owoList:[],
				uhname:"",
				type:2,
				appname:this.$API.GetappJC(),
				page:1,
				moreText:"加载更多",
				experience:0,
				isLoad:0,
				
				isLoading:0,
				bgurl:"",
				title:"",
				uid:0,
				avatar:"",
				name:"",
				customize:"",
				lv:"",
				vip:"",
				isvip:"",
				introduce:"",
				campus:"",
				grade:"",
				gender:"",
				birthday:"",
				fanNum:0,
				contentsNum:0,
				commentsNum:0,
				myname:"",
				scrollTop:0,
				isFollow:0,
				vid:"",
				sy_appbox: false, 
				appModOrder: 2, 
				applist:[],
				apppage: 1,
				apploading: false, 

				
				
			} 
			
		},
		onPageScroll(res){
			var that = this;
			that.scrollTop = res.scrollTop;
		},
		onShow(){
			var that = this;
			if (that.type == 0) that.type = 2;
			if(localStorage.getItem('userinfo')){
				
				var userInfo = JSON.parse(localStorage.getItem('userinfo'));
				that.vid = userInfo.uid;
			}
			var i = that.type;
			that.page=1;
			that.moreText="加载更多";
			that.isLoad=0;
			that.getIsFollow();
			that.getUserInfo();
			if(i==0){
				that.getContentsList(false);
			}else if(i==1){
				that.getCommentsList(false)
			}else{
				that.getSpaceList(false)
			}
			// #ifdef APP-PLUS
			
			plus.navigator.setStatusBarStyle(that.AppStyle === 'campus-night' ? "light" : "dark")
			// #endif
			
			
			
		},
		onReachBottom() {
		    //触底后执行的方法，比如无限加载之类的
			var that = this;
			if(that.isLoad==0){
				that.loadMore();
			}
			
		},
			onPullDownRefresh(){
			var that = this;
			if (that.type == 0) that.type = 2;
			var i = that.type;
			that.page=1;
			that.moreText="加载更多";
			that.isLoad=0;
			that.getIsFollow();
			that.getUserInfo();
			that.getUserData();
			that.getgg();
			if(i==0){
				that.getContentsList(false);
			}else if(i==1){
				that.getCommentsList(false)
			}else if(i == 4) {
				that.getAppList(false);
			}else{
				that.getSpaceList(false)
			}
			that.getgg();
		},
			onLoad(res) {
			var that = this;
			if (that.type == 0) that.type = 2;
			// #ifdef APP-PLUS || MP
			that.NavBar = this.CustomBar;
			// #endif
			
			that.title = res.title;
			that.uid =  res.uid;
			that.avatar =  res.avatar;
			that.name =  res.name;
			that.getIsFollow();
			that.getUserInfo();
			that.getUserData();
			// #ifdef APP-PLUS || H5
			var owo = that.owo.data;
			var owoList=[];
			for(var i in owo){
				owoList = owoList.concat(owo[i].container);
			}
			that.owoList = owoList;
			// #endif
			if(that.type == 0) {
				that.getContentsList(false);
			} else if(that.type == 1) {
				that.getCommentsList(false);
			} else if(that.type == 4) {
				that.getAppList(false);
			} else {
				that.getSpaceList(false);
			}
			var cachedPlugins = localStorage.getItem('getPlugins');
			if (cachedPlugins) {
				var pluginList = JSON.parse(cachedPlugins);
				// 检查插件是否存在于插件列表中
				that.sy_appbox = pluginList.includes('sy_appbox');
			}
			if(that.sy_appbox){
				that.getAppBoxInfo();
			}
			
		},
		methods: {
			getgg() {
			  var that = this;
			      uni.request({
			        url:that.$API.SMlikeall(),
			        method:'GET',
					data:{
						uid:that.uid
					},
			        dataType:"json",
			        success(res) {
					  that.likesall = res.data.likesall;
			        },
			        fail(error) {
			          console.log(error);
			        }
			      })
				  
			},
			copyName() {
			    var that = this;
			      uni.setClipboardData({
			        data: that.name,
			        success: function () {
			          uni.showToast({
			            title: '昵称已复制',
			            icon: 'success'
			          })
			        }
			      })
			    },
			copyUid() {
			    var that = this;
			      uni.setClipboardData({
			        data: that.uid,
			        success: function () {
			          uni.showToast({
			            title: 'ID已复制',
			            icon: 'success'
			          })
			        }
			      })
			    },
			toType(i){
				var that = this;
				if (i == 0) i = 2;
				that.type=i;
				that.page=1;
				that.moreText="加载更多";
				that.isLoad=0;
				if(i == 0) {
					that.getContentsList(false);
				} else if(i == 1) {
					that.getCommentsList(false);
				} else if(i == 4) {
					that.getAppList(false);
				} else {
					that.getSpaceList(false);
				}
			},
			goFanList(uid){
				var that = this;
				
				uni.navigateTo({
				    url: '/pages/user/fanList?uid='+uid
				});
			},
			back(){
				uni.navigateBack({
					delta: 1
				});
			},
			getUserLv(i){
				var that = this;
				if(!i){
					var i = 0;
				}
				var rankList = that.$API.GetRankList();
				return rankList[i];
			},
			getUserLvStyle(i){
				var that = this;
				if(!i){
					var i = 0;
				}
				var rankStyle = that.$API.GetRankStyle();
				var userlvStyle ="color:#fff;background-color: "+rankStyle[i];
				return userlvStyle;
			},
			getUserInfo(){
				var that = this;
				that.$Net.request({
					
					url: that.$API.getUserInfo(),
					data:{
						"key":that.uid,
						"uid":that.uid,
						"token":localStorage.getItem('token') || ''
					},
					header:{
						'Content-Type':'application/x-www-form-urlencoded'
					},
					method: "get",
					dataType: 'json',
					success: function(res) {
						if(res.data.code==1){
							that.vip = res.data.data.vip;
							that.isvip = res.data.data.isvip;
							that.lv = res.data.data.lv;
							that.avatar = res.data.data.avatar;
							that.bgurl = res.data.data.userBg;
							that.customize = res.data.data.customize;
							that.experience = res.data.data.experience;
							that.introduce = res.data.data.introduce;
							that.campus = res.data.data.campus || '';
							that.grade = res.data.data.grade || '';
							that.gender = res.data.data.gender || '';
							that.birthday = res.data.data.birthday || '';
							if(res.data.data.screenName){
								that.myname = res.data.data.screenName;
							}else{
								that.myname = res.data.data.name;
							}
							
							
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
			loadMore(){
				var that = this;
				that.moreText="加载中...";
				that.isLoad=1;
				if(that.type==0){
					that.getContentsList(true);
				}else if(that.type==1){
					that.getCommentsList(true)
				}else{
					that.getSpaceList(true)
				}
				
			},
			reload(){
				var that = this;
				if (that.type == 0) {
					that.getContentsList(false);
				} else if (that.type == 1) {
					that.getCommentsList(false)
				}else if (that.type == 4) {
					that.getAppList(false)
				} else {
					that.getSpaceList(false)
				}
				
			},
			getLv(i){
				var that = this;
				if(!i){
					var i = 0;
				}
				var lv  = that.$API.getLever(i);
				var leverList = that.$API.GetLeverList();
				return leverList[lv];
			},
			getPrivateChat(){
				var that = this;
				if(!that.privateChatEnabled){
					return false;
				}
				var token = "";
				if(localStorage.getItem('userinfo')){
					var userInfo = JSON.parse(localStorage.getItem('userinfo'));
					token=userInfo.token;
				}else{
					uni.showToast({
						title: "请先登录",
						icon: 'none'
					})
					uni.navigateTo({
						url: '/pages/user/login'
					});
					return false;
				}
				var touid = that.uid;
				var data={
					"touid":touid,
					"token":token
				}
				uni.showLoading({
					title: "加载中"
				});
				that.$Net.request({
					
					url: that.$API.getPrivateChat(),
					data:data,
					header:{
						'Content-Type':'application/x-www-form-urlencoded'
					},
					method: "get",
					dataType: 'json',
					success: function(res) {
						//console.log(JSON.stringify(res));
						setTimeout(function () {
							uni.hideLoading();
						}, 1000);
						
						if(res.data.code==1){
							var name = that.name;
							var uid = that.uid;
							var chatid = res.data.data
							uni.redirectTo({
							    url: '/pages/chat/chat?uid='+uid+"&name="+name+"&chatid="+chatid
							});
						}else{
							uni.showToast({
								title: res.data.msg,
								icon: 'none'
							})
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
					}
				})
			},
			getUserData() {
				var that = this;
				that.$Net.request({
					
					url: that.$API.getUserData(),
					data:{
						"uid":that.uid
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
							that.fanNum = profileData.fanNum != null ? profileData.fanNum : profileData.fans;
							that.fancount = profileData.followNum != null ? profileData.followNum : profileData.follow;
							that.contentsNum = profileData.contentsNum != null ? profileData.contentsNum : profileData.contents;
							that.commentsNum = profileData.commentsNum != null ? profileData.commentsNum : profileData.comments;
							
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
						token: localStorage.getItem('token') || '',
						page: 1,
						limit: 1
					},
					header: {'Content-Type':'application/x-www-form-urlencoded'},
					method: 'get',
					dataType: 'json',
					success: function(res) {
						if (res.data.code == 1 && res.data.total != null) {
							that.commentsNum = res.data.total;
						}
					}
				});
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
			getContentsList(isPage){
				var that = this;
				var data = {
					"type":"post",
					"authorId":that.uid,
				}
				var page = that.page;
				if(isPage){
					page++;
				}
				that.$Net.request({
					url: that.$API.getContentsList(),
					data:{
						"searchParams":JSON.stringify(that.$API.removeObjectEmptyKey(data)),
						"limit":8,
						"page":page,
						"order":"created"
					},
					header:{
						'Content-Type':'application/x-www-form-urlencoded'
					},
					method: "get",
					dataType: 'json',
					success: function(res) {
						uni.stopPullDownRefresh();
						that.isLoad=0;
						that.moreText="加载更多";
						if(res.data.code==1){
							var list = res.data.data;
							if(list.length>0){
								//that.contentsList = list;
								if(isPage){
									that.page++;
									that.contentsList = that.contentsList.concat(list);
								}else{
									that.contentsList = list;
								}
								
								
							}else{
								that.moreText="没有更多数据了";
							}
						}
						var timer = setTimeout(function() {
							that.isLoading=1;
							clearTimeout('timer')
						}, 300)
					},
					fail: function(res) {
						uni.stopPullDownRefresh();
						that.moreText="加载更多";
						that.isLoad=0;
						var timer = setTimeout(function() {
							that.isLoading=1;
							clearTimeout('timer')
						}, 300)
					}
				})
			},
			toSetup() {
				uni.navigateTo({
					url: '/pages/user/setup'
				})
			},
			getAppBoxInfo(){
				var that = this;
				uni.request({
					url: that.$API.PluginLoad('sy_appbox'),
					data: {
						"action": "getConfig"
					},
					method: 'GET',
					dataType: "json",
					success(res) {
						if(res.data.code == 200) {
							that.appModOrder = res.data.data.appModOrder;
							if (that.sy_appbox && that.appModOrder == 1) {
								that.type = 4;
							} else {
								that.type = 2;
							}
							if(that.type == 0) {
								that.getContentsList(false);
							} else if(that.type == 1) {
								that.getCommentsList(false);
							} else if(that.type == 4) {
								that.getAppList(false);
							} else {
								that.getSpaceList(false);
							}
						} else {
							console.log(res.data.msg)
						}
						setTimeout(function() {
							that.isLoading = 1;
						}, 180);
					},
					fail(error) {
						that.apploading = false;
						that.isLoading = 1;
						console.log(error);
						uni.showToast({
							title: "网络开小差了",
							icon: 'none'
						});
					}
				});
			},
			getAppList(isPage) {
				const that = this;
				if(that.apploading) return;
				
				if(!isPage) {
					that.apppage = 1;
					that.dataLoad = true;
					that.apploading = true;
				}
				
				let page = that.apppage;
				if(isPage) {
					page++;
				}

				const data = {
					authorId: that.uid
				};

				uni.request({
					url: that.$API.PluginLoad('sy_appbox'),
					data: {
						"action": "getAppList",
						"getapp_page": page,
						"getapp_limit": 10,
						"getapp_order": "created",
						"getapp_if": JSON.stringify(that.$API.removeObjectEmptyKey(data))
					},
					method: 'GET',
					dataType: "json",
					success(res) {
						if(res.data.code == 200) {
							const list = res.data.data || []; // 添加空数组作为默认值
							if(list.length > 0) {
								const mappedList = list.map(item => {
									return {
										...item,
										tagInfo: {
											text: item.type == 1 ? '搬运' : 
												item.type == 2 ? '原创' : 
												item.type == 3 ? '金标' : 
												item.type == 4 ? '官方' : '未知',
											color: item.type == 1 ? '#7c72ff' : 
												item.type == 2 ? '#19be6b' : 
												item.type == 3 ? '#ff6600' : 
												item.type == 4 ? '#2979ff' : '#999'
										},
										size: that.formatSize(item.size)
									};
								});
								
								that.isLoad = 0;
								if(isPage) {
									if(list.length < 1) {
										that.moreText = "没有更多数据了";
									}
									that.applist = [...that.applist, ...mappedList];
									that.apppage = page;
								} else {
									that.applist = mappedList;
									that.apppage = 1;
								}
							} else {
								if(!isPage) {
									that.applist = [];
								}
								that.moreText = "没有更多数据了";
							}
						} else {
							uni.showToast({
								title: res.data.msg,
								icon: 'none'
							});
						}
						that.apploading = false;
						setTimeout(function() {
							that.isLoading = 1;
						}, 180);
					},
					fail(error) {
						that.apploading = false;
						that.isLoading = 1;
						console.log(error);
						uni.showToast({
							title: "网络开小差了",
							icon: 'none'
						});
					}
				});
			},
			formatSize(size) {
				if(!size) return '未知大小';
				if(size >= 1024 * 1024) {
					return (size / (1024 * 1024)).toFixed(1) + 'Gb';
				} else if(size >= 1024) {
					return (size / 1024).toFixed(1) + 'Mb';  
				} else {
					return size + 'Kb';
				}
			},
			toAppInfo(id) {
				uni.navigateTo({
					url: '/pages/plugins/sy_appbox/info?id=' + id
				});
			},
			getCommentsList(isPage){
				var that = this;
				var page = that.page;
				if(isPage){
					page++;
				}
				that.$Net.request({
					url: that.$API.spaceList(),
					data:{
						"searchParams": JSON.stringify({uid:that.uid,type:3}),
						"token":localStorage.getItem('token') || '',
						"limit":5,
						"page":page,
						"order":"created"
					},
					header:{
						'Content-Type':'application/x-www-form-urlencoded'
					},
					method: "get",
					dataType: 'json',
					success: function(res) {
						uni.stopPullDownRefresh();
						that.isLoad=0;
						if(res.data.code==1){
							var list = res.data.data;
							if(list.length>0){
							var commentsList = list.map(function(item) {
								var parent = item.parentJson;
								if (parent && parent.id) {
									item.originalState = 'visible';
									item.original = {
										id: parent.id,
										text: parent.text,
										userJson: {name: parent.username || '用户'}
									};
								} else {
									item.originalState = 'deleted';
									item.original = null;
								}
								return item;
							});
								if(isPage){
									that.page++;
									that.commentsList = that.commentsList.concat(commentsList);
								}else{
									that.commentsList = commentsList;
								}
							}else{
								if(!isPage) that.commentsList = [];
								that.moreText="没有更多数据了";
							}
							
						}
						var timer = setTimeout(function() {
							that.isLoading=1;
							clearTimeout('timer')
						}, 300)
					},
					fail: function(res) {
						uni.stopPullDownRefresh();
						that.isLoad=0;
						that.moreText="加载更多";
						var timer = setTimeout(function() {
							that.isLoading=1;
							clearTimeout('timer')
						}, 300)
					}
				})
			},
			getIsFollow(){
				var that = this;
				var token = "";
				if(localStorage.getItem('userinfo')){
					var userInfo = JSON.parse(localStorage.getItem('userinfo'));
					token=userInfo.token;
				}
				var data = {
					token:token,
					touid:that.uid,
				}
				that.$Net.request({
					
					url: that.$API.isFollow(),
					data:data,
					header:{
						'Content-Type':'application/x-www-form-urlencoded'
					},
					method: "get",
					dataType: 'json',
					success: function(res) {
						that.isFollow = res.data.code;
					},
					fail: function(res) {
						uni.showToast({
							title: "网络不太好哦~",
							icon: 'none'
						})
					}
				})
			},
			follow(type){
				var that = this;
				var token = "";
				if(localStorage.getItem('userinfo')){
					var userInfo = JSON.parse(localStorage.getItem('userinfo'));
					token=userInfo.token;
				}else{
					uni.showToast({
						title: "请先登录",
						icon: 'none'
					})
					uni.navigateTo({
						url: '/pages/user/login'
					});
					return false;
				}
				var data = {
					token:token,
					touid:that.uid,
					type:type,
				}
				that.isFollow = type;
				uni.showLoading({
					title: "加载中"
				});
				that.$Net.request({
					
					url: that.$API.follow(),
					data:data,
					header:{
						'Content-Type':'application/x-www-form-urlencoded'
					},
					method: "get",
					dataType: 'json',
					success: function(res) {
						//console.log(JSON.stringify(res))
						setTimeout(function () {
							uni.hideLoading();
						}, 1000);
						uni.showToast({
							title:res.data.msg,
						    icon:'none'
						});
						if(type == 1){
						    if (localStorage.getItem('userinfo')) {
						      var userInfo = JSON.parse(localStorage.getItem('userinfo'));
						      that.username = userInfo.name;
						    		
						    		
						    	}
						}else if(type == 0){
						    if (localStorage.getItem('userinfo')) {
						      var userInfo = JSON.parse(localStorage.getItem('userinfo'));
						      that.username = userInfo.name;
						    		
						    	}
						}
						that.getIsFollow();
					},
					fail: function(res) {
						setTimeout(function () {
							uni.hideLoading();
						}, 1000);
						uni.showToast({
							title: "网络不太好哦~",
							icon: 'none'
						})
						that.getIsFollow();
					}
				})
			},
			commentsAdd(title,coid,reply){
				var that = this;
				var cid = that.cid;
				uni.navigateTo({
				    url: '/pages/contents/commentsadd?cid='+cid+"&coid="+coid+"&title="+title+"&isreply="+reply
				});
			},
			toPost(){
				var that = this;
				
				uni.navigateTo({
					url: '/pages/user/post'
				});
			},
			toEdit(cid){
				var that = this;
				
				uni.navigateTo({
					url: '/pages/user/post?type=edit'+'&cid='+cid
				});
			},
			getUserLv(i){
				var that = this;
				var rankList = that.$API.GetRankList();
				return rankList[i];
			},
			getUserLvStyle(i){
				var that = this;
				var rankStyle = that.$API.GetRankStyle();
				var userlvStyle ="color:#fff;background-color: "+rankStyle[i];
				return userlvStyle;
			},
			toInfo(data){
				var that = this;
				
				uni.navigateTo({
				    url: '/pages/contents/info?cid='+data.cid+"&title="+data.title
				});
			},
			toInfoComment(cid,title){
				var that = this;
				
				uni.navigateTo({
				    url: '/pages/contents/info?cid='+cid+"&title="+title
				});
			},
			toSearch(){
				var that = this;
				
				uni.redirectTo({
				    url: '/pages/contents/search'
				});
			},
			ToCopy(text) {
				var that = this;
				// #ifdef APP-PLUS
				uni.setClipboardData({
					data: text,
					success: () => { //复制成功的回调函数
						uni.showToast({ //提示
							title: "复制成功"
						})
					}
				});
				// #endif
				// #ifdef H5 
				let textarea = document.createElement("textarea");
				textarea.value = text;
				textarea.readOnly = "readOnly";
				document.body.appendChild(textarea);
				textarea.select();
				textarea.setSelectionRange(0, text.length) ;
				uni.showToast({ //提示
					title: "复制成功"
				})
				var result = document.execCommand("copy") 
				textarea.remove();
				
			// #endif
			},
			formatNumber(num) {
			    return num >= 1e4 ? (num / 1e4).toFixed(1) + '' : num
			},
			replaceSpecialChar(text) {
				if(!text){
					return false;
				}
				text = text.replace(/&quot;/g, '"');
				text = text.replace(/&amp;/g, '&');
				text = text.replace(/&lt;/g, '<');
				text = text.replace(/&gt;/g, '>');
				text = text.replace(/&nbsp;/g, ' ');
				text = text.replace("||rn||","\n");
				return text;
			},
			subText(text,num){
				if(text){
					if(text.length>num){
						text = text.substring(0,num);
						return text+"……";
					}else{
						return text;
					}
				}else{
					return "Ta还没有个人介绍哦"
				}
			},
			getSpaceList(isPage){
				var that = this;
				var token = "";
				if(localStorage.getItem('userinfo')){
					var userInfo = JSON.parse(localStorage.getItem('userinfo'));
					token=userInfo.token;

				}
				var page = that.page;
				if(isPage){
					page++;
				}
				var data = {
					"uid":that.uid 
				}
				that.$Net.request({
					url: that.$API.spaceList(),
					data:{
						"searchParams":JSON.stringify(that.$API.removeObjectEmptyKey(data)),
						"limit":10,
						"page":page,
						"order":"created",
						"token":token
					},
					method: "get",
					dataType: 'json',
					success: function(res) {
						that.changeLoading = 1;
						that.isLoad=0;
						that.moreText="加载更多";
						if(!isPage){
							that.dataLoad = true;
						}
						if(res.data.code==1){
							var list = res.data.data;
							var spaceList = [];
							for(var i in list){
								if(list[i].type==0){
									if(list[i].pic){
										var pic = list[i].pic;
										list[i].picList = pic.split("||");
									}else{
										list[i].picList = [];
									}
									
								}
								if(list[i].type==2){
									if(!list[i].forwardJson) list[i].forwardJson = {};
									if(list[i].forwardJson.pic){
										var pic = list[i].forwardJson.pic;
										list[i].forwardJson.picList = pic.split("||");
									}else{
										list[i].forwardJson.picList = [];
									}
									
								}
							}
							spaceList = list;
							if(list.length>0){
								if(isPage){
									that.page++;
									that.spaceList = that.spaceList.concat(spaceList);
								}else{
									that.spaceList = spaceList;
								}
								
							}else{
								that.moreText="没有更多动态了";
							}
						}
						setTimeout(function() {
							that.isLoading=1;
						}, 180);
					},
					fail: function(res) {
						
						that.changeLoading = 1;
						that.isLoad=0;
						that.moreText="加载更多";
						var timer = setTimeout(function() {
							that.isLoading=1;
							clearTimeout('timer')
						}, 300)
					}
				})
			},
		}
	}
</script>

<style>
.profile-introduce { white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.55; }
.profile-campus { margin-top: 8upx; font-size: 24upx; color: #7b8b89; }
.profile-grade { margin-top: 2upx; font-size: 22upx; color: #899794; }
.profile-private-fields { display: flex; flex-wrap: wrap; gap: 10upx; margin-top: 8upx; }
.profile-private-fields text { padding: 4upx 12upx; border-radius: 8upx; background: #edf4f2; color: #60706c; font-size: 22upx; }
.campus-profile-page.campus-night .profile-private-fields text { background: #293132; color: #c3ceca; }
.profile-bottom-spacer { width: 100%; height: 100upx; background: #f6f6f6; }
.campus-profile-page { min-height: 100vh; background: #f4f7f6; }
.campus-profile-page.campus-night,
.campus-profile-page.campus-night .all-box { background: #15191b !important; color: #e9eeec !important; }
.campus-profile-page.campus-night .user-info,
.campus-profile-page.campus-night .user-info-main,
.campus-profile-page.campus-night .user-name,
.campus-profile-page.campus-night .search-type,
.campus-profile-page.campus-night .search-space,
.campus-profile-page.campus-night .cu-list.menu-avatar,
.campus-profile-page.campus-night .cu-card.no-card,
.campus-profile-page.campus-night .cu-card.no-card > .cu-item,
.campus-profile-page.campus-night [style*="background-color:#ffffff"],
.campus-profile-page.campus-night [style*="background-color: #ffffff"],
.campus-profile-page.campus-night [class*="bg-white"] { background-color: #202527 !important; border-color: #333b3c !important; }
.campus-profile-page.campus-night .user-info-main > .bg-white { background: #202527 !important; }
.campus-profile-page.campus-night .user-name,
.campus-profile-page.campus-night .user-info-name,
.campus-profile-page.campus-night .user-text,
.campus-profile-page.campus-night .user-data-num,
.campus-profile-page.campus-night .user-data-label,
.campus-profile-page.campus-night .search-type-box,
.campus-profile-page.campus-night .app-box-title { color: #edf2f0 !important; }
.campus-profile-page.campus-night .search-type-box.active { color: #5bc49e !important; border-bottom-color: #5bc49e !important; }
.campus-profile-page.campus-night .app-box-info,
.campus-profile-page.campus-night .app-category-tag { color: #aeb9b5 !important; }
.campus-profile-page.campus-night .app-category-tag,
.campus-profile-page.campus-night .loading-container { background: #293031 !important; }
.campus-profile-page.campus-night .loading,
.campus-profile-page.campus-night > view:last-child { background-color: #15191b !important; }
.campus-profile-page.campus-night .profile-bottom-spacer { background: #15191b !important; }
.campus-profile-page.campus-night .userInfo-bottom-box .cu-btn { background: #293132 !important; color: #edf2f0 !important; }
.campus-profile-page.campus-night .userlv.customize { border-color: #7d8b87 !important; color: #dce4e1 !important; }
.user-info-data {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.xyy{
	margin-left: 0px;
}
.search-type{
	display: flex;
	    position: relative;
	    align-items: center;
	    justify-content: space-around;
		border-bottom: solid 2px #f3f3f3;
}
.search-type-box.active {
    border-bottom: solid 2px #000000;
    color: #000000;
}
.user-info-data-box {
 flex-grow: 1;
 text-align: center;
	}
.user-data-num {
	margin-right:0px;
  font-size: 36upx;
}
.user-data-label {
  font-size: 28upx;
}
.sup-script {
  font-size: 28upx;
  font-weight: 400;
}
.cu-bar .action2 {
    background: #00000057;
    width: 40px;
    height: 40px;
    border-radius: 50%;
    /* text-align: center; */
    display: flex;
    align-items: center;
    justify-content: center;
}
.grid2 {
    display: flex;
    flex-direction: row;
	justify-content: normal;
}
.cu-bar2{
	margin-top: 20upx;
}
.app-box {
		display: flex;
		justify-content: space-between;
		align-items: center;
		margin-bottom: 20rpx;
	}

	.app-box-body {
		flex: 1;
		display: flex;
		margin-right: 20rpx;
		min-width: 0;
		align-items: center;
	}

	.app-box-logo {
		width: 110rpx;
		height: 110rpx;
		flex-shrink: 0;
	}

	.app-box-content {
		flex: 1;
		margin-left: 20rpx;
		min-width: 0;
	}

	.app-box-title {
		font-size: 30rpx;
		font-weight: bold;
		margin-bottom: 8rpx;
		width: 400rpx;
	}

	.app-box-info {
		font-size: 26rpx;
		color: #666;
		margin-bottom: 8rpx;
	}

	.app-box-info text {
		margin-right: 10rpx;
	}

	.app-box-down {
		background-color: #3cc9a4;
		color: #fff;
		padding: 10rpx 30rpx;
		border-radius: 100rpx;
		white-space: nowrap;
	}

	.app-box-tags {
		display: flex;
		align-items: center;
		flex-wrap: wrap;
		font-size: 28rpx;
	}

	.app-tag {
		padding: 4rpx 12rpx;
		border-radius: 8rpx;
		color: #ffffff;
		margin-right: 12rpx;
		font-size: 24rpx;
	}

	.app-category-tag {
		padding: 4rpx 12rpx;
		border-radius: 8rpx;
		background-color: #f5f5f5;
		color: #666666;
		margin-right: 12rpx;
		font-size: 24rpx;
	}
</style>
