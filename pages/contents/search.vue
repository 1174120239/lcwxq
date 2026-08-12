<template>
	<view class="userpost campus-subpage campus-search-page" :class="AppStyle">
		<view class="header" :style="[{height:CustomBar + 'px'}]">
			<view class="cu-bar bg-white" :style="{'height': CustomBar + 'px','padding-top':StatusBar + 'px'}">
				<view class="action" @tap="back">
					<text class="cuIcon-back"></text>
				</view>
				<!--  #ifdef MP -->
				<view class="content text-bold" :style="[{top:StatusBar + 'px'}]">
					搜索
				</view>
				<!--  #endif -->
				<!--  #ifdef H5 || APP-PLUS -->
				<view class="search-form radius" :style="[{top:StatusBar + 'px'}]">
					<text class="cuIcon-search"></text>
					<input v-model="searchText" :adjust-position="false" type="text" placeholder="你想搜点什么？" confirm-type="search" @confirm="searchTag"></input>
					<view class="search-close" v-if="searchText!=''" @tap="searchClose()"><text class="cuIcon-close"></text></view>
				</view>
				<view class="action">
					<text class="text-shojo" @tap="searchTag()">搜索</text>
				</view>
				<!--  #endif -->
			</view>
		</view>
		<view :style="[{padding:NavBar + 'px 10px 0px 10px'}]"></view>
		<view class="all-box">
			<!--  #ifdef MP -->
			<view class="cu-bar bg-white search">
				<view class="search-form round">
					<text class="cuIcon-search"></text>
					<input type="text" placeholder="输入搜索关键字" v-model="searchText"  @input="searchTag"></input>
					<view class="search-close" v-if="searchText!=''" @tap="searchClose()"><text class="cuIcon-close"></text></view>
				</view>
			</view>
			<!--  #endif -->
			<view class="search-type grid" :class="'col-' + typeColumns">
				<view class="search-type-box" v-if="sy_appbox&&appModOrder==1" @tap="toType(5)" :class="type==5?'active':''">
					<text>应用</text>
				</view>
				<view class="search-type-box" v-if="sy_appbox&&appModOrder==0" @tap="toType(5)" :class="type==5?'active':''">
					<text>应用</text>
				</view>
				<!--  #ifdef H5 || APP-PLUS -->
			
				<view class="search-type-box" @tap="toType(3)" :class="type==3?'active':''">
					<text>动态</text>
				</view>
				<!--  #endif -->
				<view class="search-type-box" @tap="toType(2)" :class="type==2?'active':''">
					<text>用户</text>
				</view>
			</view>
			<view class="cu-card article no-card" v-if="type==0">
				<block v-for="(item,index) in contentsList" :key="index" v-if="type==0">
					<articleItem :item="item"></articleItem>
				</block>
				<view class="load-more search-load-more" @tap="loadMore" v-if="contentsList.length>0">
					<text>{{moreText}}</text>
				</view>
				<view class="no-data" v-if="contentsList.length==0">
					<text class="cuIcon-text"></text>
					暂时没有数据
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
						<commentItem :item="item"></commentItem>
					</block>
				</view>
				
				<view class="load-more search-load-more" @tap="loadMore" v-if="commentsList.length>0">
					<text>{{moreText}}</text>
				</view>
			</view>
			<!--评论结束-->
			<!--动态-->
			<view class="search-space" v-if="type==3">
				<view class="no-data" v-if="spaceList.length==0">
					<text class="cuIcon-text"></text>
					暂时没有动态
				</view>
				<spaceItem :spaceList="spaceList" :night="AppStyle === 'campus-night'"></spaceItem>
				<view class="load-more search-load-more" @tap="loadMore" v-if="spaceList.length>0">
					<text>{{moreText}}</text>
				</view>
			</view>
			
			<!--动态结束-->
			<view class="cu-list menu-avatar userList" style="margin-top: 4upx;" v-if="type==2">
				<view class="no-data" v-if="userList.length==0">
					<text class="cuIcon-text"></text>
					暂时没有数据
				</view>
				<view class="cu-item" v-for="(item,index) in userList" :key="index" @tap="toUserContents(item)">
					<view class="cu-avatar round lg" :style="item.style"></view>
					<view class="content">
						<block v-if="item.isvip>0">
							<view class="text-shojo" v-if="item.screenName">{{item.screenName}}
								<text v-if="item.groupKey=='contributor'||item.groupKey=='administrator'" class="cuIcon-lightfill"></text>
							</view>
							<view class="text-shojo" v-else>{{item.name}}
								<text v-if="item.groupKey=='contributor'||item.groupKey=='administrator'" class="cuIcon-lightfill"></text>
							</view>
						</block>
						<block v-else>
							<view class="text-black" v-if="item.screenName">{{item.screenName}}
								<text v-if="item.groupKey=='contributor'||item.groupKey=='administrator'" class="cuIcon-lightfill"></text>
							</view>
							<view class="text-black" v-else>{{item.name}}
								<text v-if="item.groupKey=='contributor'||item.groupKey=='administrator'" class="cuIcon-lightfill"></text>
							</view>
						</block>
						
						<view class="text-gray text-sm flex">
							<view class="text-cut">
								{{subText(item.introduce,100)}}
							</view> </view>
					</view>
					<view class="action goUserIndex">
						<view class="padding-lr-sm padding-tb-xs text-shojo round" style="background-color: #cbffea;">主页</view>
						
					</view>
				</view>
				<view class="load-more search-load-more" @tap="loadMore">
					<text>{{moreText}}</text>
				</view>
			
			</view>
			<!--用户结束-->
			<!-- 添加应用列表部分 -->
			<view :class="'bg-white'" :style="{'background-color':'#ffffff'}" v-if="type==5">
				<view class="no-data" v-if="appList.length==0">
					<text class="cuIcon-text"></text>
					暂时没有应用
				</view>
				<block v-else>
					<view class="app-box" :style="{'padding': '20rpx','border-bottom-color':'#f5f5f5'}" v-for="(item, index) in appList" :key="index">
						<view class="app-box-body" @tap="toAppInfo(item.id)">
							<view class="app-box-logo">
								<u-image :src="item.logo" width="110rpx" height="110rpx" mode="aspectFill" 
									:lazy-load="true" :fade="true" duration="450" border-radius="28rpx">
									<u-loading slot="loading"></u-loading>
								</u-image>
							</view>
							<view class="app-box-content">
								<view class="app-box-title text-cut" :style="{'color':'#333'}">{{item.name}}</view>
								<view class="app-box-info" :style="{'color':'#666'}">
									<text :style="{color: item.tagInfo.color}" 
										:class="item.score>=3?'tn-icon-star-fill':'tn-icon-star'"></text>
									<text :style="{color: item.tagInfo.color}">{{item.score}}</text>
									<text>{{item.size}}</text>
									<text>v{{item.version}}</text>
									<text :class="item.system=='ios'?'tn-icon-iphone':''"></text>
								</view>
								<view class="app-box-tags">
									<text class="app-tag" 
										:style="{backgroundColor: item.tagInfo.color}">{{item.tagInfo.text}}</text>
									<text v-for="(category, idx) in item.sortJson" :key="idx" 
										class="app-category-tag" :style="{'background-color':'#f5f5f5','color':'#666666'}">{{category.name}}</text>
								</view>
							</view>
						</view>
						<view class="app-box-down" @tap="toAppInfo(item.id)">下载</view>
					</view>
				</block>
				<view class="load-more search-load-more" @tap="loadMore" v-if="appList.length>0">
					<text>{{moreText}}</text>
				</view>
			</view>
		</view>
		
		<!--加载遮罩-->
		<view class="loading" v-if="isLoading==0||changeLoading==0">
			<view class="loading-main">
				<view class="campus-loader"></view>
			</view>
		</view>
		<!--加载遮罩结束-->
	</view>
</template>

<script>
	import { localStorage } from '../../js_sdk/mp-storage/mp-storage/index.js'
	export default {
		data() {
			return {
				StatusBar: this.StatusBar,
				CustomBar: this.CustomBar,
				NavBar:this.StatusBar +  this.CustomBar,
			AppStyle:this.$store.state.AppStyle,
				
				contentsList:[],
				
				commentsList:[],
				
				userList:[],
				
				spaceList:[],
				
				searchText:"",
				
				// H5/App search starts with dynamic content; MP starts with users because the dynamic tab is unavailable there.
				type:3,
				typeColumns:2,
				
				page:1,
				moreText:"加载更多",
				
				isLoad:0,
				
				isLoading:0,
				
				changeLoading:1,
				sy_appbox:false,
				appModOrder:0,
				appList: [], // 应用列表数据
				tagMap: {
					1: {
						text: '搬运',
						color: '#7c72ff'
					},
					2: {
						text: '原创',
						color: '#19be6b'
					},
					3: {
						text: '金标',
						color: '#ff6600'
					},
					4: {
						text: '官方',
						color: '#2979ff'
					}
				},
			}
		},
		onPullDownRefresh(){
			var that = this;
			that.page=1;
			that.moreText="加载更多";
			that.isLoad=0;
			if(that.type==0){
				that.getContentsList(false);
			}else if(that.type==1){
				that.getCommentsList(false)
			}else if(that.type==2){
				that.getUserList(false)
			}else if(that.type==5&&that.sy_appbox){
				that.getAppList(false)
			}else{
				that.getSpaceList(false)
			}
			var timer = setTimeout(function() {
				uni.stopPullDownRefresh();
			}, 1000)
			
		},
		onShow(){
			var that = this;
			that.page=1;
			that.moreText="加载更多";
			that.isLoad=0;
			// #ifdef APP-PLUS
			
			plus.navigator.setStatusBarStyle("dark")
			// #endif
			that.reload();
			
			
		},
		onReachBottom() {
		    //触底后执行的方法，比如无限加载之类的
			var that = this;
			if(that.isLoad==0){
				that.loadMore();
			}
			
		},
		onLoad() {
			var that = this;
			// #ifdef APP-PLUS || MP
			that.NavBar = this.CustomBar;
			// #endif
			// #ifdef MP
			that.type = 2;
			// #endif
			//插件检测
			var cachedPlugins = localStorage.getItem('getPlugins');
			if (cachedPlugins) {
				const pluginList = JSON.parse(cachedPlugins);
				// 检查插件是否存在于插件列表中
				that.sy_appbox = pluginList.includes('sy_appbox'); 
				// #ifdef H5 || APP-PLUS
				that.typeColumns = that.sy_appbox ? 3 : 2;
				// #endif
			}
			if(that.sy_appbox){
				that.getAppBoxInfo();
			}
			
		},
		methods: {
			toType(i){
				var that = this;
				that.type=i;
				that.page=1;
				that.moreText="加载更多";
				that.isLoad=0;
				that.clearCurrentList(i);
				if(i==0){
					that.getContentsList(false);
				}else if(i==1){
					that.getCommentsList(false)
				}else if(i==2){
					that.getUserList(false)
				}else if(i==5&&that.sy_appbox){
					that.getAppList(false)
				}else{
					that.getSpaceList(false)
				}
			},
			back(){
				uni.navigateBack({
					delta: 1
				});
			},
			loadMore(){
				var that = this;
				that.moreText="加载中...";
				that.isLoad=1;
				if(that.type==0){
					that.getContentsList(true);
				}else if(that.type==1){
					that.getCommentsList(true)
				}else if(that.type==2){
					that.getUserList(true)
				}else if(that.type==5&&that.sy_appbox){
					that.getAppList(true)
				}else{
					that.getSpaceList(true)
				}
				
			},
			reload(){
				var that = this;
				if(that.type==0){
					that.getContentsList();
				}else if(that.type==1){
					that.getCommentsList()
				}else if(that.type==2){
					that.getUserList()
				}else if(that.type==5&&that.sy_appbox){
					that.getAppList(false)
				}else{
					that.getSpaceList()
				}
				
			},
			searchTag(){
				var that = this;
				that.changeLoading = 0;
				that.page=1;
				that.moreText="加载更多";
				that.clearCurrentList(that.type);
				if(that.type==0){
					that.getContentsList();
				}else if(that.type==1){
					that.getCommentsList()
				}else if(that.type==2){
					that.getUserList()
				}else if(that.type==5&&that.sy_appbox){
					that.getAppList(false)
				}else{
					that.getSpaceList()
				}

			},
			searchClose(){
				var that = this;
				that.searchText = "";
				that.page=1;
				that.moreText="加载更多";
				that.clearCurrentList(that.type);
				if(that.type==0){
					that.getContentsList();
				}else if(that.type==1){
					that.getCommentsList()
				}else if(that.type==2){
					that.getUserList()
				}else{
					that.getSpaceList()
				}
			},
			clearCurrentList(type){
				if(type==0){
					this.contentsList = [];
				}else if(type==1){
					this.commentsList = [];
				}else if(type==2){
					this.userList = [];
				}else if(type==5){
					this.appList = [];
				}else{
					this.spaceList = [];
				}
			},
			getContentsList(isPage){
				var that = this;
				var data = {
					"type":"post",
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
						"searchKey":that.searchText,
						"order":"created"
					},
					header:{
						'Content-Type':'application/x-www-form-urlencoded'
					},
					method: "get",
					dataType: 'json',
					success: function(res) {
						that.changeLoading = 1;
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
								if(!isPage) that.contentsList = [];
							}
						}
						var timer = setTimeout(function() {
							that.isLoading=1;
							clearTimeout('timer')
						}, 300)
					},
					fail: function(res) {
						that.changeLoading = 1;
						that.moreText="加载更多";
						that.isLoad=0;
						var timer = setTimeout(function() {
							that.isLoading=1;
							clearTimeout('timer')
						}, 300)
					}
				})
			},
			getAppList(isPage) {
				const that = this;
				if(that.submitStatus1){
					return false;
				}
				that.submitStatus1 = true;
				let page = that.page;
				if(isPage){
					page++;
				}
				
				uni.request({
					url: that.$API.PluginLoad('sy_appbox'),
					data: {
						"action": "getAppList",
						"getapp_page": page,
						"getapp_limit": 10,
						"getapp_order": "created",
						"getapp_searchKey": that.searchText
					},
					method: 'GET',
					dataType: "json",
					success(res) {
						that.submitStatus1 = false;
						that.changeLoading = 1;
						that.isLoad = 0;
						if(res.data.code == 200){
							const data = res.data.data || [];
							const list = data.map(item => {
								return {
									...item,
									tagInfo: that.tagMap[item.type] || {
										text: '未知',
										color: '#999'
									},
									size: that.formatSize(item.size)
								};
							});
							
							if(list && list.length > 0){
								if(isPage){
									that.page++;
									that.appList = that.appList.concat(list);
								}else{
									that.appList = list;
								}
							}else{
								if(isPage){
									that.moreText = "没有更多应用了";
								}else{
									that.appList = [];
								}
							}
						}
						var timer = setTimeout(function() {
							that.isLoading = 1;
							clearTimeout('timer')
						}, 300)
					},
					fail(error) {
						that.submitStatus1 = false;
						that.changeLoading = 1;
						that.moreText = "加载更多";
						that.isLoad = 0;
						var timer = setTimeout(function() {
							that.isLoading = 1;
							clearTimeout('timer')
						}, 300)
					}
				});
			},
			toAppInfo(id) {
				uni.navigateTo({
					url: '/pages/plugins/sy_appbox/info?id=' + id
				});
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
								that.type = 5;
							} else {
								that.type = 0;
							}
							if(that.type==0){
								that.getContentsList();
							}else if(that.type==1){
								that.getCommentsList()
							}else if(that.type==2){
								that.getUserList()
							}else if(that.type==5&&that.sy_appbox){
								that.getAppList(false)
							}else{
								that.getSpaceList()
							}
						} else {
							console.log(res.data.msg)
						}
					},
					fail(error) {
						that.apploading = false;
						console.log(error);
						uni.showToast({
							title: "网络开小差了",
							icon: 'none'
						});
					}
				});
			},
			formatSize(size) {
				if (!size) {
					return '未知大小';
				}
			
				if (size >= 1024 * 1024) {
					return (size / (1024 * 1024)).toFixed(1) + 'Gb';
				} else if (size >= 1024) {
					return (size / 1024).toFixed(1) + 'Mb';
				} else {
					return size + 'Kb';
				}
			},
			getCommentsList(isPage){
				var that = this;
				var data = {
					"type":"comment",
				}
				var page = that.page;
				if(isPage){
					page++;
				}
				that.$Net.request({
					url: that.$API.getCommentsList(),
					data:{
						"searchParams":JSON.stringify(that.$API.removeObjectEmptyKey(data)),
						"limit":5,
						"page":page,
						"searchKey":that.searchText,
						"order":"created"
					},
					header:{
						'Content-Type':'application/x-www-form-urlencoded'
					},
					method: "get",
					dataType: 'json',
					success: function(res) {
						that.changeLoading = 1;
						that.isLoad=0;
						if(res.data.code==1){
							var list = res.data.data;
							if(list.length>0){
								var commentsList = [];
								for(var i in list){
									var arr = list[i];
									arr.style = "background-image:url("+list[i].avatar+");"
									commentsList.push(arr);
								}
								if(isPage){
									that.page++;
									that.commentsList = that.commentsList.concat(commentsList);
								}else{
									that.commentsList = commentsList;
								}
							}else{
								that.moreText="没有更多数据了";
								if(!isPage) that.commentsList = [];
							}
							
						}
						var timer = setTimeout(function() {
							that.isLoading=1;
							clearTimeout('timer')
						}, 300)
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
			getUserList(isPage){
				var that = this;
				var page = that.page;
				if(isPage){
					page++;
				}
				that.$Net.request({
					url: that.$API.getUserList(),
					data:{
						"searchParams":"",
						"limit":10,
						"page":page,
						"token":localStorage.getItem('token'),
						"searchKey":that.searchText,
						"order":"created"
					},
					header:{
						'Content-Type':'application/x-www-form-urlencoded'
					},
					method: "get",
					dataType: 'json',
					success: function(res) {
						that.changeLoading = 1;
						that.isLoad=0;
						if(res.data.code==1){
							var list = res.data.data;
							if(list.length>0){
								
								var userList = [];
								for(var i in list){
									var arr = list[i];
									arr.style = "background-image:url("+list[i].avatar+");"
									userList.push(arr);
								}
								if(isPage){
									that.page++;
									that.userList = that.userList.concat(userList);
								}else{
									that.userList = userList;
								}
							}else{
								that.moreText="没有更多数据了";
								if(!isPage) that.userList = [];
							}
						}
						var timer = setTimeout(function() {
							that.isLoading=1;
							clearTimeout('timer')
						}, 300)
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
				that.$Net.request({
					url: that.$API.spaceList(),
					data:{
						"limit":10,
						"page":page,
						"order":"created",
						"searchKey":that.searchText,
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
								if(!isPage) that.spaceList = [];
							}
						}
						var timer = setTimeout(function() {
							that.isLoading=1;
							clearTimeout('timer')
						}, 300)
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
			toUserContents(data){
				var that = this;
				var name = data.name;
				var title = data.name+"的信息";
				if(data.screenName){
					title = data.screenName+" 的信息";
					name = data.screenName
				}
				var id= data.uid;
				var type="user";
				uni.navigateTo({
				    url: '/pages/contents/userinfo?title='+title+"&name="+name+"&uid="+id+"&avatar="+encodeURIComponent(data.avatar)
				});
			},
			commentsAdd(title,coid,reply){
				var that = this;
				var cid = that.cid;
				uni.navigateTo({
				    url: '/pages/contents/commentsadd?cid='+cid+"&coid="+coid+"&title="+title+"&isreply="+reply
				});
			},
			subText(text,num){
				if(text.length < null){
					return text.substring(0,num)+"……"
				}else{
					return text;
				}
				
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
			    return num >= 1e3 && num < 1e4 ? (num / 1e3).toFixed(1) + 'k' : num >= 1e4 ? (num / 1e4).toFixed(1) + 'w' : num
			},
			replaceSpecialChar(text) {
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
			}
		}
	}
</script>

<style>
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

	.campus-search-page {
		min-height: 100vh;
		min-height: 100dvh;
		background: #f4f8f8;
		color: #263a37;
	}

	.campus-search-page .all-box {
		padding-bottom: calc(40rpx + env(safe-area-inset-bottom));
	}

	.campus-search-page .header .cu-bar {
		border-bottom: 1rpx solid #e2ece9;
		background: rgba(250, 252, 252, 0.98) !important;
		box-shadow: none !important;
	}

	.campus-search-page .header .search-form {
		background: #edf4f3 !important;
		border: 1rpx solid #dbe8e5;
		box-shadow: none;
	}

	.campus-search-page .search-type {
		margin: 18rpx 14rpx 0;
		border: 1rpx solid #dce8e5;
		border-radius: 16rpx;
		background: #ffffff;
		overflow: hidden;
	}

	.campus-search-page .search-type-box {
		min-height: 72rpx;
		line-height: 72rpx;
		color: #758581;
		font-weight: 600;
	}

	.campus-search-page .search-type-box.active {
		border-bottom-color: #168c80;
		color: #168c80;
	}

	.campus-search-page .load-more,
	.campus-search-page .search-load-more {
		min-height: 84rpx;
		margin: 0 !important;
		background: transparent !important;
		background-color: transparent !important;
		color: #7f918c !important;
		line-height: 84rpx;
		text-align: center;
	}

	.campus-search-page .userList {
		margin: 18rpx 14rpx 0 !important;
		border: 1rpx solid #dce8e5;
		border-radius: 18rpx;
		background: #ffffff;
		overflow: hidden;
	}

	.campus-search-page .userList > .cu-item {
		background: transparent;
	}

	.campus-search-page.campus-night {
		background: #15191b !important;
		color: #edf3f0;
	}

	.campus-search-page.campus-night .header .cu-bar,
	.campus-search-page.campus-night .header .cu-bar.bg-white {
		border-bottom-color: rgba(218, 231, 226, 0.1) !important;
		background: #171d1e !important;
	}

	.campus-search-page.campus-night .header .action,
	.campus-search-page.campus-night .header .content,
	.campus-search-page.campus-night .header .cuIcon-back,
	.campus-search-page.campus-night .header .cuIcon-search,
	.campus-search-page.campus-night .text-shojo {
		color: #a9dfd1 !important;
	}

	.campus-search-page.campus-night .header .search-form,
	.campus-search-page.campus-night .search-form {
		border-color: rgba(218, 231, 226, 0.12) !important;
		background: #202728 !important;
		color: #edf3f0 !important;
		box-shadow: none !important;
	}

	.campus-search-page.campus-night .header .search-form input,
	.campus-search-page.campus-night .search-form input {
		color: #edf3f0 !important;
	}

	.campus-search-page.campus-night .header .search-form .uni-input-placeholder,
	.campus-search-page.campus-night .search-form .uni-input-placeholder,
	.campus-search-page.campus-night input::placeholder {
		color: #8fa09a !important;
	}

	.campus-search-page.campus-night .search-close,
	.campus-search-page.campus-night .search-close text {
		color: #a8bbb5 !important;
	}

	.campus-search-page.campus-night .search-type {
		border-color: rgba(218, 231, 226, 0.78);
		background: transparent !important;
		box-shadow: none;
	}

	.campus-search-page.campus-night .search-type-box {
		color: #a9b7b2;
	}

	.campus-search-page.campus-night .search-type-box.active {
		border-bottom-color: #69c7b2;
		color: #61d1bd;
	}

	.campus-search-page.campus-night .all-box,
	.campus-search-page.campus-night .search-space,
	.campus-search-page.campus-night .search-space .space-feed,
	.campus-search-page.campus-night .search-space .square-list,
	.campus-search-page.campus-night .search-space .cu-card.dynamic.no-card,
	.campus-search-page.campus-night .cu-list.menu-avatar,
	.campus-search-page.campus-night .cu-list.menu-avatar.comment {
		background: #15191b !important;
		background-color: #15191b !important;
	}

	.campus-search-page.campus-night .search-space .space-feed .square-list > .cu-item2 {
		background: #202728 !important;
	}

	.campus-search-page.campus-night .search-space .space-feed .square-list > .cu-item2 + .cu-item2 {
		margin-top: 16rpx !important;
	}

	.campus-search-page.campus-night .search-space .space-feed .text-center.grid {
		border-top-color: rgba(218, 231, 226, 0.12) !important;
	}

	.campus-search-page.campus-night .cu-card.article.no-card,
	.campus-search-page.campus-night .cu-card.article.no-card > .cu-item,
	.campus-search-page.campus-night .article-item-shell,
	.campus-search-page.campus-night .home-article-card,
	.campus-search-page.campus-night .space-feed .square-list > .cu-item2,
	.campus-search-page.campus-night .userList,
	.campus-search-page.campus-night .userList > .cu-item {
		border-color: rgba(218, 231, 226, 0.1) !important;
		background: #202728 !important;
		box-shadow: none !important;
	}

	.campus-search-page.campus-night .home-article-title,
	.campus-search-page.campus-night .article-title,
	.campus-search-page.campus-night .home-article-copy,
	.campus-search-page.campus-night .text-black,
	.campus-search-page.campus-night .userList .content {
		color: #edf3f0 !important;
	}

	.campus-search-page.campus-night .home-article-desc,
	.campus-search-page.campus-night .home-article-clock,
	.campus-search-page.campus-night .home-article-meta,
	.campus-search-page.campus-night .home-article-meta text,
	.campus-search-page.campus-night .text-gray,
	.campus-search-page.campus-night .text-grey,
	.campus-search-page.campus-night .text-content {
		color: #b8c6c1 !important;
	}

	.campus-search-page.campus-night .home-article-pill,
	.campus-search-page.campus-night .app-category-tag,
	.campus-search-page.campus-night .goUserIndex > view {
		background: #263331 !important;
		color: #a9dfd1 !important;
	}

	.campus-search-page.campus-night .home-article-thumb,
	.campus-search-page.campus-night .home-article-thumb image,
	.campus-search-page.campus-night .space-feed .grid.grid-square > .bg-img {
		border-color: rgba(218, 231, 226, 0.12) !important;
		background-color: #14191a !important;
	}

	.campus-search-page.campus-night .load-more,
	.campus-search-page.campus-night .search-load-more,
	.campus-system-night .campus-search-page .load-more,
	.campus-system-night .campus-search-page .search-load-more {
		min-height: 86rpx;
		border-top: 1rpx solid rgba(218, 231, 226, 0.08);
		border-bottom: 1rpx solid rgba(218, 231, 226, 0.08);
		background: #171d1e !important;
		background-color: #171d1e !important;
		color: #98a7a2 !important;
		line-height: 86rpx;
	}

	.campus-search-page.campus-night .no-data {
		color: #98a7a2 !important;
	}

	.campus-search-page.campus-night [style*="background-color:'"],
	.campus-search-page.campus-night [style*="background-color:#ffffff"],
	.campus-search-page.campus-night [style*="background-color: #ffffff"] {
		background-color: #202728 !important;
	}

	.campus-search-page.campus-night .app-box {
		border-bottom-color: rgba(218, 231, 226, 0.08) !important;
		background: #202728 !important;
	}

	.campus-search-page.campus-night .app-box-title {
		color: #edf3f0 !important;
	}

	.campus-search-page.campus-night .app-box-info {
		color: #b8c6c1 !important;
	}

	.campus-search-page.campus-night .app-box-down {
		background: #28665b !important;
		color: #eaf6f2 !important;
	}
</style>
