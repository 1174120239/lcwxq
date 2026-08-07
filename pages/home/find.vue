<template>
	<view class="campus-page campus-messages" :class="{'campus-night': campusNight}">
		<view class="header" :style="[{height:CustomBar + 'px'}]">
			<view class="cu-bar bg-white" :style="{'height': CustomBar + 'px','padding-top':StatusBar + 'px'}">
				<view class="action" v-if="privateChatEnabled" @tap="toUserList">
					<text class="cuIcon-friend"></text>
				</view>
				<view class="content text-bold" :style="[{top:StatusBar + 'px'}]">
					消息中心
				</view>
				<view class="action" v-if="privateChatEnabled" @tap="toSearch">
					<text class="cuIcon-friendadd"></text>
				</view>
				
				
			</view>
		</view>
		<view :style="[{padding:NavBar + 5 + 'px 10px 0px 10px'}]"></view>
		<view class="data-box data-inbox message-stream-card margin-left-sm margin-right-sm" style="border-radius: 20px;">
			<view class="message-overview" v-if="!privateChatEnabled">
				<view class="message-overview-icon"><text class="cuIcon-notice"></text></view>
				<view class="message-overview-copy">
					<view class="message-overview-title">校园通知</view>
					<view class="message-overview-subtitle">评论、系统提醒和互动消息都会在这里汇总</view>
				</view>
			</view>
			<view class="search-type grid parent col-2" v-if="privateChatEnabled" style="height: 220upx;line-height:40px;font-size:0px;border-radius: 100px;">
				<view class="index-sort-box">
					<view itemClass="butclass">
						<view class="index-sort-main" @tap="toType('inbox')">
							<view class="index-sort-i" style="border-radius: 20upx;background: linear-gradient(to right bottom, #c8ff95, #44d35c);box-shadow: #00ff2659 0px 3px 5px 0px;">
								<text class="cuIcon-notice" style="color:  #ffffff;"></text>
							</view>
							<view class="search-type-box index-sort-text" :class="type=='inbox'?'tab-wrap-index square-box':'square-box2'" style="display: inline-block;">
								系统通知
							</view>
						</view>
					</view>
				</view>
				<view class="index-sort-box">
					<view itemClass="butclass">
						<view class="index-sort-main" @tap="toType('chat')">
							<view class="index-sort-i" style="border-radius: 20upx;background: linear-gradient(to bottom right, #aaffff, #89adff);box-shadow: #00aaff59 0px 3px 5px 0px;">
								<text class="cuIcon-friend" style="color:  #ffffff;"></text>
							</view>
							<view class="search-type-box index-sort-text" :class="type=='chat'?'tab-wrap-index square-box':'square-box2'" style="display: inline-block;">
								用户私聊
							</view>
						</view>
					</view>
				</view>
			</view>
			<view v-if="token">
			
			<block v-if="type=='inbox'">
				
				<view class="cu-card dynamic no-card">
					<view class="cu-item">
						<view class="cu-list menu-avatar comment" style="border-radius: 20px;">
							<view class="no-data" v-if="inboxList.length==0">
								暂时没有消息
							</view>
							<view class="cu-card dynamic no-card" style="margin-top: 20upx;">
								<view class="cu-item" v-for="(item,index) in inboxList" :key="index" v-if="inboxList.length>0">
									<view class="cu-list menu-avatar comment campus-message-row" @tap="goInbox(item)">
										<view class="cu-item">
											<view class="cu-avatar round" :style="item.style"></view>
											<view class="content">
												<view class="text-black">
													<block v-if="item.userJson.isvip>0">
													<text class="text-shojo2">{{item.userJson.name}}</text>
													</block>
													<block v-else>{{item.userJson.name}}</block>
													<block  v-if="item.type=='system'">
														<text class="userlv bg-red">系统通知</text>
													</block>
													<block  v-if="item.type=='finance'">
														<text class="userlv bg-gradual-orange">财务通知</text>
													</block>
													<block  v-if="item.type=='comment'">
												<block v-if="item.userJson.isvip>0">
													<block v-if="item.userJson.vip==1">
														<text class="userlv" style="background: linear-gradient(to bottom right, #f2ad5c, #e6216d,#901ccb);color:white;padding: 2px 5px;border-radius: 10px;">VIP</text>
													</block>
													<block v-else>
														<text class="userlv" style="background: linear-gradient(to bottom right, #f2ad5c, #e6216d,#901ccb);color:white;padding: 2px 5px;border-radius: 10px;">VIP</text>
													</block>
												</block>
													</block>
													<block v-if="item.type=='spaceComment'">
														<text class="userlv bg-green">动态评论</text>
													</block>
												</view>
												<view class="text-content text-df break-all">
													<rich-text :nodes="markHtml(item.text)"></rich-text>
												</view>
												<view class="bg-blue light padding-sm radius margin-top-sm text-sm message-source-chip" v-if="item.type=='comment'">
													<view class="flex">
														<view>{{item.contenTitle}}</view>
														
													</view>
												</view>
												<view class="bg-green light padding-sm radius margin-top-sm text-sm message-source-chip" v-if="item.type=='spaceComment'">
													<view v-if="item.spaceState=='deleted'">原动态已删除</view>
													<view v-else-if="item.spaceState=='hidden'">原动态不可见</view>
													<view v-else>动态：{{item.spaceInfo && item.spaceInfo.text ? subText(item.spaceInfo.text, 80) : '查看动态'}}</view>
												</view>
												<view class="margin-top-sm flex justify-between message-meta">
													<view class="text-gray text-df">{{formatDate(item.created)}}</view>
													<view>
													</view>
												</view>
											</view>
										</view>
							
										
									</view>
								</view>
							</view>
							
							<view class="load-more" @tap="loadMore" v-if="inboxList.length>0">
								<text>{{moreText}}</text>
							</view>
							
						</view>
					</view>
				</view>
				
			</block>
			<block v-if="type=='chat'&&privateChatEnabled">
				
				<view class="cu-list menu-avatar"  style="border-radius: 20px;" v-if="chatList.length>0">
					<block v-for="(item,index) in chatList" :key="index">
					<view class="cu-item" @tap="goChat(item)">
						<view class="cu-avatar round lg" :style="'background-image:url('+item.userJson.avatar+');'"></view>
						<view class="content">
							<view><view class="text-cut">{{item.userJson.name}}</view></view>
							<view class="text-gray text-sm flex">
								<view class="text-cut">
									<block v-if="item.lastMsg!=null">
										
										<block v-if="item.lastMsg.type!=4">
											<block v-if="item.lastMsg.uid==uid">
												我: 
											</block>
											<block v-else>
												{{item.userJson.name}}: 
											</block>
											<block v-if="item.lastMsg.type==0">
												{{item.lastMsg.text}}
											</block>
											<block v-if="item.lastMsg.type==1">
												[图片]
											</block>
										</block>
										<block v-else>
											<block v-if="item.lastMsg.text=='ban'">
												<block v-if="item.lastMsg.uid==uid">
													
													<text class="text-blue">[你屏蔽了对方]</text>
												</block>
												<block v-else>
													<text class="text-blue">[对方屏蔽了你]</text>
													
												</block>
											</block>
											<block v-else>
												<block v-if="item.lastMsg.uid==uid">
													
													<text class="text-blue">[你解除了屏蔽]</text>
												</block>
												<block v-else>
													<text class="text-blue">[对方解除了屏蔽]</text>
													
												</block>
											</block>
											
										</block>
									</block>
									<block v-else>暂无消息</block>
								</view>
							</view>
						</view>
						<view class="action">
							<view class="text-grey text-xs">{{chatFormatDate(item.lastTime)}}</view>
							<block v-if="item.lastMsg!=null">
								<block v-if="item.lastMsg.uid==uid">
									<view class="cu-tag sm" style="background: none;">&nbsp</view>
								</block>
								<block v-else>
									<view class="cu-tag sm" style="background: none;" v-if="item.isNew==0">&nbsp</view>
									<view class="cu-tag round bg-red sm" v-else>{{item.unRead}}</view>
								</block>
							</block>
							<block v-else>
								<view class="cu-tag sm" style="background: none;">&nbsp</view>
							</block>
						</view>
					</view>
					
					</block>
					
				</view>
				
			</block>
			</view>
			<view v-else>
			  <view class="no-data">
			    <text class="cuIcon-community"></text>
			    请先登录哦！
			    <view class="text-center margin-top-sm">
			      <text class="cu-btn bg-shojo radius" style="border-radius: 50px;" @tap="goLogin()">登录</text>
			      <text class="cu-btn bg-shojo margin-left-sm" style="border-radius: 50px;" @tap="goRegister()">注册</text>
			    </view>
			  </view>
			</view>
		</view>
		
		<!--  #ifdef APP-PLUS -->
		<view style="height: 100upx;"></view>
		<Tabbar ref="tabbar" :current="2" :night="campusNight"></Tabbar>
		<!--  #endif -->
		<!--  #ifdef H5 -->
		<PublishPanel ref="publishPanel" :visible="false" :night="campusNight" :auto-intro="false"></PublishPanel>
		<!--  #endif -->
		
	</view>
</template>

<script>
	import { localStorage } from '../../js_sdk/mp-storage/mp-storage/index.js'
	import { applyCampusThemeShell, getCampusThemeMode, isDongchangfuNight, resolveCampusNight } from '@/utils/campusTheme.js'
	import featureFlags from '@/utils/featureFlags.js'
	// #ifdef APP-PLUS
	import owo from '../../static/app-plus/owo/OwO.js'
	// #endif
	// #ifdef APP-PLUS
	import Tabbar from '@/pages/components/tabBar.vue'
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
				campusThemeClock: Date.now(),
				campusThemeTimer: null,
				campusThemeMode: 'auto',
				
				inboxList:[],
				chatList:[],
				oldChatList:[],
				uid:0,
				type:"inbox",
				zb_info:0,
				moreText:"加载更多",
				page:1,
				token:"",
				
				isLoading:0,
				isLoad:0,
				// 请求锁避免页面重复显示或快速切换时产生并发列表请求。
				inboxRequesting:false,
				chatRequesting:false,
				
				owo:owo,
				owoList:[],
				
				chatLoading:null,
				pushRefreshHandler:null,
				
			}
		},
		computed: {
			campusNight() {
				return resolveCampusNight(this.campusThemeMode, isDongchangfuNight(this.campusThemeClock))
			}
		},
		onPullDownRefresh(){
			var that = this;
			
		},
		onHide() {
			var that = this
			clearInterval(that.chatLoading);
			that.chatLoading = null
			that.stopCampusThemeClock();
		},
		onUnload() {
			this.stopCampusThemeClock();
			if (this.pushRefreshHandler) {
				uni.$off('campus:push', this.pushRefreshHandler);
				this.pushRefreshHandler = null;
			}
		},
		onReachBottom() {
		    //触底后执行的方法，比如无限加载之类的
			var that = this;
			that.loadMore();
		},
		onShow(){
			var that = this;
			that.loadCampusThemeMode();
			that.startCampusThemeClock();
			if(!that.privateChatEnabled && that.type=="chat"){
				that.type = "inbox";
				clearInterval(that.chatLoading);
				that.chatLoading = null;
			}
			that.$nextTick(function() {
				// #ifdef APP-PLUS
				if (that.$refs.tabbar) that.$refs.tabbar.activate()
				// #endif
				// #ifdef H5
				if (that.$refs.publishPanel) that.$refs.publishPanel.activatePage()
				// #endif
			})
			that.page=1;
			// #ifdef APP-PLUS
			
			
			plus.navigator.setStatusBarStyle(that.campusNight ? "light" : "dark")
			// #endif
			if(localStorage.getItem('userinfo')){
				
				var userInfo = JSON.parse(localStorage.getItem('userinfo'));
				that.uid = userInfo.uid;
			}
			if(localStorage.getItem('token')){
				
				that.token = localStorage.getItem('token');
				that.getInboxList(false);
				that.setRead();
			}
			if(localStorage.getItem('chatList')){
				that.oldChatList = JSON.parse(localStorage.getItem('chatList'));
				// that.chatList = JSON.parse(localStorage.getItem('chatList'));
			}
			
			
		},
		onLoad() {
			var that = this;
			that.pushRefreshHandler = function() {
				if (that.type == "inbox" && that.token) {
					that.page = 1;
					that.getInboxList(false);
				}
			};
			uni.$on('campus:push', that.pushRefreshHandler);
			
			// #ifdef APP-PLUS || MP
			that.NavBar = this.CustomBar;
			// #endif
			// #ifdef APP-PLUS || H5
			var owo = that.owo.data;
			var owoList=[];
			for(var i in owo){
				owoList = owoList.concat(owo[i].container);
			}
			that.owoList = owoList;
			// #endif
			
		},
		methods:{
			loadCampusThemeMode() {
				this.campusThemeMode = getCampusThemeMode()
				applyCampusThemeShell(this.campusThemeMode, this.campusThemeClock)
			},
			handleCampusThemeMode(mode) {
				this.campusThemeMode = mode
				// #ifdef APP-PLUS
				this.$nextTick(() => plus.navigator.setStatusBarStyle(this.campusNight ? 'light' : 'dark'))
				// #endif
			},
			startCampusThemeClock() {
				this.stopCampusThemeClock()
				this.campusThemeClock = Date.now()
				applyCampusThemeShell(this.campusThemeMode, this.campusThemeClock)
				const nextHour = (Math.floor(this.campusThemeClock / (60 * 60 * 1000)) + 1) * 60 * 60 * 1000
				this.campusThemeTimer = setTimeout(() => this.startCampusThemeClock(), nextHour - this.campusThemeClock + 120)
			},
			stopCampusThemeClock() {
				if (!this.campusThemeTimer) return
				clearTimeout(this.campusThemeTimer)
				this.campusThemeTimer = null
			},
			back(){
				var that = this;
				clearInterval(that.chatLoading);
				that.chatLoading = null
				uni.navigateBack({
					delta: 1
				});
			},
			loadMore(){
				var that = this;
				
				if(that.isLoad==0){
					if(that.type=="inbox"){
						that.moreText="加载中...";
						that.getInboxList(true);
					}
				}
			},
			markHtml(text){
				var that = this;
				var owoList=that.owoList;
				for(var i in owoList){
				
					if(that.replaceSpecialChar(text).indexOf(owoList[i].data) != -1){
						text = that.replaceAll(that.replaceSpecialChar(text),owoList[i].data,"<img src='/"+owoList[i].icon+"' class='tImg' />")
						
					}
				}
				return text;
			},
			replaceAll(string, search, replace) {
			  return string.split(search).join(replace);
			},
			subText(text, num) {
				var value = text == null ? '' : String(text);
				return value.length > num ? value.substring(0, num) + '...' : value;
			},
			toInfo(cid,title){
				var that = this;
				clearInterval(that.chatLoading);
				that.chatLoading = null
				uni.navigateTo({
				    url: '/pages/contents/info?cid='+cid+"&title="+title
				});
			},
			goInbox(data){
				var that = this;
				if(data.type=="comment"){
					that.toInfo(data.contentsInfo.cid,data.contenTitle);
				}
				if(data.type=="spaceComment" && data.spaceState=="visible"){
					clearInterval(that.chatLoading);
					that.chatLoading = null;
					uni.navigateTo({
						url: '/pages/space/info?id='+data.value
					});
				}
				if(data.type=="finance"){
					clearInterval(that.chatLoading);
					that.chatLoading = null
					uni.navigateTo({
					    url: '/pages/user/assets'
					});
				}
				if(data.type=="system"){
					return false;
				}
				
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
			
			toType(i){
				var that = this;
				if(i=="chat" && !that.privateChatEnabled){
					that.type = "inbox";
					that.getInboxList(false);
					return false;
				}
				that.type=i;
				that.page=1;
				that.moreText="加载更多";
				that.isLoad=0;
				if(i=="inbox"){
					clearInterval(that.chatLoading);
					that.chatLoading = null
					that.getInboxList(false);
				}else{
					that.getMyChat(false);
					
				}
				
				
			},
			getInboxList(isPage){
				var that = this;
				var page = that.page;
				if(isPage){
					page++;
				}
				if(that.token=="" || that.inboxRequesting){
					
					return false
				}
				that.inboxRequesting = true;
				that.isLoad = 1;
				that.$Net.request({
					url: that.$API.getInbox(),
					data:{
						"token":that.token,
						"limit":8,
						"page":page,
					},
					header:{
						'Content-Type':'application/x-www-form-urlencoded'
					},
					method: "get",
					dataType: 'json',
					timeout: 15000,
					success: function(res) {
						that.isLoad=0;
						if(res.data.code==1){
							var list = res.data.data;
							if(list.length>0){
								var inboxList = [];
								for(var i in list){
									var arr = list[i];
									arr.style = "background-image:url("+list[i].userJson.avatar+");"
									inboxList.push(arr);
								}
								if(isPage){
									that.page++;
									that.inboxList = that.inboxList.concat(inboxList);
								}else{
									that.inboxList = inboxList;
								}
							}else{
								that.moreText="没有更多消息了";
							}
							
						}
						var timer = setTimeout(function() {
							that.isLoading=1;
							clearTimeout('timer')
						}, 300)
					},
					fail: function(res) {
						that.isLoad=0;
						that.moreText="加载更多";
						var timer = setTimeout(function() {
							that.isLoading=1;
							clearTimeout('timer')
						}, 300)
					},
					complete: function() {
						that.inboxRequesting = false;
						that.isLoad = 0;
					}
				})
			},
			toUserList() {
				uni.navigateTo({
					url: '/pages/user/userlist'
				})
			},
			toSearch() {
				var that = this;
			
				uni.navigateTo({
					url: '/pages/contents/searchuser'
				});
			},
			//为了性能考虑，只显示最近30条聊天
			getMyChat(isPage){
				var that = this;
				var page = that.page;
				if(isPage){
					page++;
				}
				if(that.token=="" || that.chatRequesting){
					
					return false
				}
				that.chatRequesting = true;
				that.$Net.request({
					url: that.$API.myChat(),
					data:{
						"token":that.token,
						"limit":30,
						"page":page,
						"order":"lastTime"
					},
					header:{
						'Content-Type':'application/x-www-form-urlencoded'
					},
					method: "get",
					dataType: 'json',
					timeout: 15000,
					success: function(res) {
						that.isLoad=0;
						if(res.data.code==1){
							var list = res.data.data;
							if(list.length>0){
								var chatList = [];
								for(var i in list){
									var arr = list[i];
									arr.isNew =0;
									arr.unRead =0;
									chatList.push(arr);
								}
								if(isPage){
									that.page++;
									that.chatList = that.chatList.concat(chatList);
								}else{
									var oldChatList = [];
									if(that.oldChatList!=null){
										oldChatList = that.oldChatList;
									}
									if(oldChatList.length>0){
										
										if(!that.arraysEqual(oldChatList,chatList)){
											console.log("开始对比")
											for(var c in chatList){
												for(var d in oldChatList){
													if(oldChatList[d].id == chatList[c].id){
														if(oldChatList[d].lastTime < chatList[c].lastTime){
															console.log("赋值完成")
															chatList[c].isNew = 1;
															
															var unRead = chatList[c].msgNum - oldChatList[d].msgNum;
															if(unRead <= 0){
																unRead = 0;
															}
															chatList[c].unRead = unRead;
														}
													}
													
												}
											}
											that.oldChatList = chatList;
											that.chatList = chatList;
											localStorage.setItem('chatList',JSON.stringify(chatList));
										}
										
										
									}else{
										that.oldChatList = chatList;
										that.chatList = chatList;
										localStorage.setItem('chatList',JSON.stringify(chatList));
									}
									// 
									
									
								}
							}else{
								that.moreText="没有更多消息了";
							}
							
						}
						var timer = setTimeout(function() {
							that.isLoading=1;
							clearTimeout('timer')
						}, 300)
					},
					fail: function(res) {
						that.isLoad=0;
						that.moreText="加载更多";
						var timer = setTimeout(function() {
							that.isLoading=1;
							clearTimeout('timer')
						}, 300)
					},
					complete: function() {
						that.chatRequesting = false;
					}
				})
			},
			arraysEqual(a, b) {
				if (a === b) return true;
				if (a == null || b == null) return false;
				if (a.length != b.length) return false;
				for(var c in a){
					for(var d in b){
						if(b[d].id == a[c].id){
							if(b[d].lastTime != a[c].lastTime){
								return false;
							}
						}
						
					}
				}
			},
			commentsAdd(title,coid,reply,cid){
				var that = this;
				clearInterval(that.chatLoading);
				that.chatLoading = null
				uni.navigateTo({
				    url: '/pages/contents/commentsadd?cid='+cid+"&coid="+coid+"&title="+title+"&isreply="+reply
				});
			},
			goLogin() {
				uni.navigateTo({
					url: '/pages/user/login'
				});
			},
			goRegister() {
				uni.navigateTo({
					url: '/pages/user/register'
				});
			},
			formatDate(datetime) {
				var datetime = new Date(parseInt(datetime * 1000));
				// 获取年月日时分秒值  slice(-2)过滤掉大于10日期前面的0
				var year = datetime.getFullYear(),
					month = ("0" + (datetime.getMonth() + 1)).slice(-2),
					date = ("0" + datetime.getDate()).slice(-2),
					hour = ("0" + datetime.getHours()).slice(-2),
					minute = ("0" + datetime.getMinutes()).slice(-2);
				//second = ("0" + date.getSeconds()).slice(-2);
				// 拼接
				var result = year + "-" + month + "-" + date + " " + hour + ":" + minute;
				// 返回
				return result;
			},
			chatFormatDate(datetime) {
				var datetime = new Date(parseInt(datetime * 1000));
				// 获取年月日时分秒值  slice(-2)过滤掉大于10日期前面的0
				var year = datetime.getFullYear();
				var month = ("0" + (datetime.getMonth() + 1)).slice(-2);
				var date = ("0" + datetime.getDate()).slice(-2);
				var hour = ("0" + datetime.getHours()).slice(-2);
				var minute = ("0" + datetime.getMinutes()).slice(-2);
				var time = year+""+month+""+date;
				
				var result = hour + ":" + minute;
				var curDate = new Date();
				var curYear = curDate.getFullYear(); //获取完整的年份(4位)
				var curMonth = ("0" + (curDate.getMonth() + 1)).slice(-2);
				var curDay = ("0" + curDate.getDate()).slice(-2); //获取当前日(1-31)
				var curTime = curYear+""+curMonth+""+curDay;
				if(year==curYear){
					if(year==curYear){
						if(date==curDay){
							result = hour + ":" + minute;
						}else{
							result = month + "-" + date;
						}
					}else{
						result = month + "-" + date;
					}
				}else{
					result = month + "-" + date;
				}
				return result;
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
			setRead() {
				var that = this;
				that.$Net.request({
					
					url: that.$API.setRead(),
					data:{
						"token":that.token
					},
					header:{
						'Content-Type':'application/x-www-form-urlencoded'
					},
					method: "get",
					dataType: 'json',
					timeout: 15000,
					success: function(res) {
						if(res.data.code==1){
							
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
			goChat(data){
				var that = this;
				if(!that.privateChatEnabled){
					that.type = "inbox";
					clearInterval(that.chatLoading);
					that.chatLoading = null;
					return false;
				}
				clearInterval(that.chatLoading);
				that.chatLoading = null
				var chatid = data.id;
				//去除未读标志
				var chatList = that.chatList;
				for(var i in chatList){
					if(chatList[i].id==chatid){
						chatList[i].isNew =0;
						chatList[i].unRead =0;
					}
				}
				that.chatList = chatList;
				that.oldChatList = chatList;
				localStorage.setItem('chatList',JSON.stringify(chatList));
				//结束
				var name = data.userJson.name;
				var uid = data.userJson.uid;
				
				clearInterval(that.chatLoading);
				that.chatLoading = null
				uni.navigateTo({
				    url: '/pages/chat/chat?uid='+uid+"&name="+name+"&chatid="+chatid
				});
			}
			
		},
		// #ifdef APP-PLUS
		components: {
			Tabbar
		},
		// #endif
		
		// #ifdef MP
		
		components: {},
		// #endif
	}
</script>

<style>
	.parent {
  display: flex;
  justify-content: center;
}

.tab-wrap-index {
  display: inline-block;
  position: relative;
  z-index: 1;
}

.tab-wrap-index::after {
	line-height: 16px;
  position: absolute;
  border-radius: 50px;
  z-index: -1;
  display: block;
  content: "";
  margin: 0 auto;
  width: 100%;
  height:13rpx;
  background-color: #3cc9a4;
}

.square-box {
	line-height: 16px;
  font-weight: normal;
  font-size: 13px;
  margin-right: 0px;
  margin-top: 0px;
}

.square-box2 {
	line-height: 16px;
  font-weight: normal;
  font-size: 12px;
  margin-right: 0px;
  margin-top: 0px;
}

.square-box, .square-box2 {
  transition: font-size 0.5s ease-in-out
}

.search-type-box.active2 {
  border-bottom: solid 2px #3cc9a4;
  color: #3cc9a4;
}
	.no-data .cuIcon-community {
	    display: block;
	    font-size: 32px;
	    color: #ddd;
	    margin-bottom: 6px;
	}

.campus-messages.campus-night {
  min-height: 100vh;
  background: #15191b !important;
  color: #edf0ef;
}

.campus-messages.campus-night .header .cu-bar,
.campus-messages.campus-night .data-inbox,
.campus-messages.campus-night .search-type,
.campus-messages.campus-night .cu-list.menu-avatar,
.campus-messages.campus-night .cu-card.dynamic.no-card > .cu-item,
.campus-messages.campus-night .cu-list.menu-avatar.comment {
  border-color: rgba(226, 232, 230, 0.09) !important;
  background: #212628 !important;
  color: #edf0ef !important;
  box-shadow: 0 10rpx 28rpx rgba(0, 0, 0, 0.18);
}

.campus-messages.campus-night .search-type {
  border-bottom-color: rgba(226, 232, 230, 0.09) !important;
  background: #1d2224 !important;
  box-shadow: none;
}

.campus-messages.campus-night .search-type .index-sort-main {
  color: #edf0ef;
}

.campus-messages.campus-night .header .action {
  border-color: rgba(226, 232, 230, 0.1) !important;
  background: #292f31 !important;
  color: #edf0ef !important;
  box-shadow: none !important;
  transition: transform 180ms ease, background-color 180ms ease;
}

.campus-messages.campus-night .header .action:active {
  transform: scale(0.92);
  background: #32393b !important;
}

.campus-messages.campus-night .content,
.campus-messages.campus-night .text-black,
.campus-messages.campus-night .search-type-box {
  color: #edf0ef !important;
}

.campus-messages.campus-night .text-gray,
.campus-messages.campus-night .text-grey,
.campus-messages.campus-night .text-content,
.campus-messages.campus-night .no-data {
  color: #99a39f !important;
}

.campus-messages.campus-night .tab-wrap-index::after,
.campus-messages.campus-night .search-type-box.active2 {
  background-color: #f4c95b;
  border-color: #f4c95b;
}

</style>
