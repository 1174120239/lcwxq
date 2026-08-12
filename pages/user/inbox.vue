<template>
	<view class="user campus-subpage campus-messages message-center-page" :class="AppStyle">
		<view class="header" :style="[{height:CustomBar + 'px'}]">
			<view class="cu-bar bg-white" :style="{'height': CustomBar + 'px','padding-top':StatusBar + 'px'}">
				<view class="action" @tap="back">
					<text class="cuIcon-back"></text>
				</view>
				<view class="content text-bold" :style="[{top:StatusBar + 'px'}]">
					消息中心
				</view>
				<view class="action">
					<text v-if="type=='inbox' && unreadCount>0" class="message-read-all" @tap="setRead()">一键已读</text>
					<text v-else-if="privateChatEnabled" class="cuIcon-friendadd" @tap="toSearch"></text>
				</view>
				
				
			</view>
		</view>
		<view :style="[{padding:NavBar + 'px 10px 0px 10px'}]"></view>
		<view class="data-box data-inbox message-stream-card margin-left-sm margin-right-sm" style="border-radius: 20px;">
			<view class="message-overview" v-if="!privateChatEnabled">
				<view class="message-overview-icon"><text class="cuIcon-notice"></text></view>
				<view class="message-overview-copy">
					<view class="message-overview-title">校园通知</view>
					<view class="message-overview-subtitle">评论、系统提醒和互动消息都会在这里汇总</view>
				</view>
			</view>
			<view class="search-type grid parent col-2" v-if="privateChatEnabled" style="height: 220upx;line-height:40px;font-size:0px;border-radius: 20px;">
				<view class="index-sort-box">
					<view itemClass="butclass">
						<view class="index-sort-main" @tap="toType('inbox')">
							<view class="index-sort-i" style="border-radius: 20upx;background: linear-gradient(to bottom right, #aaffff, #89adff);box-shadow: #00aaff59 0px 3px 5px 0px;">
								<text class="cuIcon-goods" style="color:  #ffffff;"></text>
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
								<text class="cuIcon-goods" style="color:  #ffffff;"></text>
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
										<view class="message-unread-dot" v-if="Number(item.isread)===0"></view>
										<view class="cu-item">
											<campus-avatar class="cu-avatar round" :src="item.userJson.avatar" :name="item.userJson.name" :fallback-icon="item.type=='system' ? 'notice' : 'people'" @tap.stop="openInboxUser(item)"></campus-avatar>
											<view class="content">
												<view class="text-black" @tap.stop="openInboxUser(item)">
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
															<block v-if="item.type=='comment'">
																<block v-if="item.userJson.isvip>0">
																	<text class="userlv" style="background: linear-gradient(to bottom right, #f2ad5c, #e6216d,#901ccb);color:white;padding: 2px 5px;border-radius: 10px;">VIP</text>
																</block>
															</block>
															<block v-if="item.type=='spaceComment'">
																<text class="userlv bg-green">动态评论</text>
															</block>
													<block v-if="item.type=='spaceLike'">
														<text class="userlv bg-blue">动态点赞</text>
													</block>
													<block v-if="item.type=='qaAnswer'">
														<text class="userlv bg-green">问答回答</text>
													</block>
													<block v-if="item.type=='qaComment'">
														<text class="userlv bg-blue">问答评论</text>
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
											<view class="bg-green light padding-sm radius margin-top-sm text-sm message-source-chip" v-if="item.type=='spaceComment' || item.type=='spaceLike'">
												<view v-if="item.spaceState=='deleted'">原动态已删除</view>
												<view v-else-if="item.spaceState=='hidden'">原动态不可见</view>
												<view v-else>动态：{{item.spaceInfo && item.spaceInfo.text ? subText(item.spaceInfo.text, 80) : '查看动态'}}</view>
															</view>
											<view class="bg-green light padding-sm radius margin-top-sm text-sm message-source-chip" v-if="item.type=='qaAnswer' || item.type=='qaComment'">
												<view v-if="item.questionState=='deleted'">原问题已删除</view>
												<view v-else-if="item.questionState=='hidden'">原问题已停用</view>
												<view v-else>问题：{{item.questionInfo && item.questionInfo.title ? item.questionInfo.title : '查看问答'}}</view>
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
						<campus-avatar class="cu-avatar round lg" :src="item.userJson.avatar" :name="item.userJson.name"></campus-avatar>
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
		
	</view>
</template>

<script>
	import { localStorage } from '../../js_sdk/mp-storage/mp-storage/index.js'
	import featureFlags from '@/utils/featureFlags.js'
	import { normalizeUser } from '@/utils/avatar.js'
	// #ifdef APP-PLUS
	import owo from '../../static/app-plus/owo/OwO.js'
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
				
				inboxList:[],
				chatList:[],
				oldChatList:[],
				uid:0,
				type:"inbox",
				
				moreText:"加载更多",
				page:1,
				token:"",
				unreadCount:0,
				
				isLoading:0,
				isLoad:0,
				// 防止翻页、切换标签和定时轮询在慢网络下发出重叠请求。
				inboxRequesting:false,
				chatRequesting:false,
				
				owo:owo,
				owoList:[],
				
				chatLoading:null,
				pushRefreshHandler:null,
				
			}
		},
		onPullDownRefresh(){
			var that = this;
			
		},
		onHide() {
			this.stopChatPolling();
		},
		onUnload() {
			// onHide 并非所有销毁路径都会可靠触发，卸载时再做一次幂等清理。
			this.stopChatPolling();
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
			that.page=1;
			if(!that.privateChatEnabled && that.type=="chat"){
				that.type = "inbox";
				that.stopChatPolling();
			}
			// #ifdef APP-PLUS
			
			plus.navigator.setStatusBarStyle("dark")
			// #endif
			if(localStorage.getItem('userinfo')){
				
				var userInfo = JSON.parse(localStorage.getItem('userinfo'));
				that.uid = userInfo.uid;
			}
			if(localStorage.getItem('token')){
				
				that.token = localStorage.getItem('token');
				if(that.type=="chat"&&that.privateChatEnabled){
					that.getMyChat(false);
					that.startChatPolling();
				}else{
					that.getInboxList(false);
				}
				that.loadUnreadCount();
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
			loadUnreadCount(){
				if(!this.token) return;
				this.$Net.request({url:this.$API.unreadNum(),data:{token:this.token},method:'get',success:(res)=>{
					if(res.data.code==1){
						this.unreadCount=Number(res.data.data||0)
						uni.$emit('campus:unread-changed',this.unreadCount)
					}
				}})
			},
			stopChatPolling(){
				if(this.chatLoading!==null){
					clearInterval(this.chatLoading);
					this.chatLoading = null;
				}
			},
			startChatPolling(){
				var that = this;
				that.stopChatPolling();
				// getMyChat 内部还有请求锁；单次请求超过 3 秒时不会叠加下一轮。
				that.chatLoading = setInterval(function() {
					if(that.type=="chat"){
						that.getMyChat(false);
					}
				}, 3000);
			},
			back(){
				var that = this;
				that.stopChatPolling();
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
				that.markInboxRead(data);
				if(data.type=="comment"){
					that.toInfo(data.contentsInfo.cid,data.contenTitle);
				}
				if((data.type=="spaceComment" || data.type=="spaceLike") && data.spaceState=="visible"){
					clearInterval(that.chatLoading);
					that.chatLoading = null;
					uni.navigateTo({
						url: '/pages/space/info?id='+data.value
					});
				}
				if((data.type=="qaAnswer" || data.type=="qaComment") && data.questionState=="visible"){
					clearInterval(that.chatLoading);
					that.chatLoading = null;
					uni.navigateTo({ url: '/pages/qa/info?id=' + data.value });
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
			markInboxRead(item){
				if(!item || Number(item.isread)!==0) return;
				var that = this;
				that.$set(item,'isread',1);
				that.unreadCount=Math.max(0,that.unreadCount-1);
				uni.$emit('campus:unread-changed',that.unreadCount);
				var rollback = function(){
					that.$set(item,'isread',0);
					that.loadUnreadCount();
				};
				that.$Net.request({
					url:that.$API.setRead(),
					data:{token:that.token,id:item.id},
					method:'post',
					header:{'Content-Type':'application/x-www-form-urlencoded'},
					success:function(res){ if(!res.data || res.data.code!=1) rollback(); },
					fail:rollback
				});
			},
			openInboxUser(item){
				if(!item || item.type == 'system' || item.type == 'finance') return false;
				var user = item.userJson || {};
				var uid = Number(user.uid || 0);
				if(!uid){
					uni.showToast({ title: '用户不存在或已注销', icon: 'none' });
					return false;
				}
				var name = user.name || '用户';
				uni.navigateTo({
					url: '/pages/contents/userinfo?title=' + encodeURIComponent(name + '的信息')
						+ '&name=' + encodeURIComponent(name) + '&uid=' + uid
						+ '&avatar=' + encodeURIComponent(user.avatar || ''),
					fail: function(){
						uni.showToast({ title: '无法打开用户主页', icon: 'none' });
					}
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
			toType(i){
				var that = this;
				if(i=="chat" && !that.privateChatEnabled){
					that.type = "inbox";
					that.stopChatPolling();
					that.getInboxList(false);
					return false;
				}
				that.stopChatPolling();
				that.type=i;
				that.page=1;
				that.moreText="加载更多";
				that.isLoad=0;
				if(i=="inbox"){
					that.getInboxList(false);
				}else{
					that.getMyChat(false);
					that.startChatPolling();
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
									arr.userJson = normalizeUser(arr.userJson, arr.type == 'system' ? '系统通知' : '已注销用户');
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
									arr.userJson = normalizeUser(arr.userJson);
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
						"token":that.token,
						"type":"all"
					},
					header:{
						'Content-Type':'application/x-www-form-urlencoded'
					},
					method: "get",
					dataType: 'json',
					timeout: 15000,
					success: function(res) {
						if(res.data.code==1){
							that.inboxList.forEach(item=>that.$set(item,'isread',1)); that.unreadCount=0; uni.$emit('campus:unread-changed',0);
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
					that.stopChatPolling();
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
	}
</script>

<style>
	.message-read-all{color:#168573;font-size:24rpx;white-space:nowrap}.campus-message-row{position:relative}.message-unread-dot{position:absolute;z-index:3;left:18rpx;top:16rpx;width:15rpx;height:15rpx;border-radius:50%;background:#e5484d;box-shadow:0 0 0 3rpx #fff}.campus-night .message-unread-dot{box-shadow:0 0 0 3rpx #1d2523}
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

	.message-center-page {
		min-height: 100vh;
		min-height: 100dvh;
		padding-bottom: calc(164rpx + env(safe-area-inset-bottom));
		background:
			linear-gradient(180deg, rgba(221, 246, 247, 0.72) 0%, rgba(244, 249, 248, 0.96) 36%, #f5f8f7 100%);
		color: #263a3e;
	}

	.message-center-page .data-inbox {
		border: 1rpx solid rgba(255, 255, 255, 0.86);
		background: rgba(255, 255, 255, 0.58) !important;
		box-shadow: 0 16rpx 42rpx rgba(36, 77, 81, 0.08);
		backdrop-filter: blur(16px) saturate(1.15);
		-webkit-backdrop-filter: blur(16px) saturate(1.15);
	}

	.message-center-page .search-type {
		height: 164rpx !important;
		margin: 0;
		border: 1rpx solid rgba(255, 255, 255, 0.72);
		border-radius: 22rpx !important;
		background: linear-gradient(135deg, rgba(226, 249, 247, 0.78), rgba(235, 243, 255, 0.72)) !important;
	}

	.message-center-page .search-type .index-sort-main {
		padding: 12rpx 0;
	}

	.message-center-page .search-type .index-sort-i {
		width: 62rpx !important;
		height: 62rpx !important;
		line-height: 62rpx !important;
		border-radius: 18rpx !important;
		background: linear-gradient(145deg, #62c8c0, #348f9e) !important;
		box-shadow: 0 8rpx 18rpx rgba(45, 139, 145, 0.16) !important;
	}

	.message-center-page .search-type-box {
		margin-top: 8rpx;
		font-size: 25rpx;
		color: #55716f;
	}

	.message-center-page .search-type-box.tab-wrap-index {
		color: #168c83;
		font-weight: 700;
	}

	.message-center-page .tab-wrap-index::after {
		height: 8rpx;
		bottom: 0;
		background: rgba(80, 194, 176, 0.34);
	}

	.message-center-page .cu-card.dynamic.no-card {
		margin-top: 18rpx;
		background: transparent;
	}

	.message-center-page .data-inbox .cu-list.menu-avatar {
		overflow: visible;
		background: transparent;
	}

	.message-center-page .data-inbox .cu-list.menu-avatar > .cu-item {
		min-height: 112rpx;
		margin-bottom: 10rpx;
		border: 1rpx solid rgba(221, 234, 231, 0.92);
		border-radius: 18rpx;
		background: rgba(255, 255, 255, 0.82);
		box-shadow: 0 7rpx 20rpx rgba(39, 79, 81, 0.05);
	}

	.message-center-page .data-inbox .cu-list.menu-avatar > .cu-item:last-child {
		margin-bottom: 0;
	}

	.message-center-page .data-inbox .cu-list.menu-avatar > .cu-item .content {
		min-width: 0;
		color: #2e4547;
	}

	.message-center-page .data-inbox .cu-list.menu-avatar > .cu-item .content > view:first-child {
		font-size: 28rpx;
		font-weight: 700;
	}

	.message-center-page .data-inbox .text-content {
		color: #617571;
		font-size: 25rpx;
		line-height: 1.5;
	}

	.message-center-page .data-inbox .text-gray,
	.message-center-page .data-inbox .text-grey {
		color: #8b9b98 !important;
	}

	.message-center-page .data-inbox .bg-blue.light {
		border: 1rpx solid rgba(102, 187, 193, 0.18);
		background: rgba(226, 247, 246, 0.86) !important;
		color: #477b7a;
	}

	.message-center-page .data-inbox .load-more {
		margin: 16rpx 0 4rpx;
		border: 1rpx solid rgba(103, 179, 170, 0.18);
		border-radius: 16rpx;
		background: rgba(255, 255, 255, 0.58);
		color: #6c8581;
	}

	.message-center-page.campus-night {
		background:
			linear-gradient(180deg, #182325 0%, #171d1f 42%, #15191b 100%) !important;
		color: #edf3f0;
	}

	.message-center-page.campus-night .data-inbox,
	.message-center-page.campus-night .data-inbox .cu-card.dynamic.no-card > .cu-item {
		border-color: rgba(217, 231, 226, 0.1) !important;
		background: rgba(31, 40, 42, 0.92) !important;
		box-shadow: none;
	}

	.message-center-page.campus-night .search-type {
		border-color: rgba(217, 231, 226, 0.1);
		background: linear-gradient(135deg, #243335, #253039) !important;
	}

	.message-center-page.campus-night .search-type .index-sort-i {
		background: linear-gradient(145deg, #419b98, #376f83) !important;
		box-shadow: none !important;
	}

	.message-center-page.campus-night .search-type-box,
	.message-center-page.campus-night .data-inbox .content,
	.message-center-page.campus-night .data-inbox .text-black {
		color: #edf3f0 !important;
	}

	.message-center-page.campus-night .search-type-box.tab-wrap-index {
		color: #8fe0d1 !important;
	}

	.message-center-page.campus-night .data-inbox .cu-list.menu-avatar > .cu-item {
		border-color: rgba(217, 231, 226, 0.1);
		background: #242e30;
		box-shadow: none;
	}

	.message-center-page.campus-night .data-inbox .text-content {
		color: #b5c4c0 !important;
	}

	.message-center-page.campus-night .data-inbox .text-gray,
	.message-center-page.campus-night .data-inbox .text-grey {
		color: #9daea8 !important;
	}

	.message-center-page.campus-night .data-inbox .bg-blue.light {
		border-color: rgba(105, 193, 190, 0.16);
		background: #29393a !important;
		color: #b2d8d2;
	}

	.message-center-page.campus-night .data-inbox .load-more {
		border-color: rgba(217, 231, 226, 0.1);
		background: #20292b;
		color: #a9bab5;
	}
</style>
