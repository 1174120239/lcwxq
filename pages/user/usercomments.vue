<template>
	<view class="user" :class="$store.state.AppStyle">
		<view class="header" :style="[{height:CustomBar + 'px'}]">
			<view class="cu-bar bg-white" :style="{'height': CustomBar + 'px','padding-top':StatusBar + 'px'}">
				<view class="action" @tap="back">
					<text class="cuIcon-back"></text>
				</view>
				<view class="content text-bold" :style="[{top:StatusBar + 'px'}]">
					我的评论
				</view>
				<view class="action">
					
				</view>
			</view>
		</view>
		<view :style="[{padding:NavBar + 'px 10px 0px 10px'}]"></view>
		
		<view class="cu-card dynamic no-card" style="margin-top: 20upx;">
			
			<view class="cu-item">
				<view class="cu-list menu-avatar comment">
					<view class="no-data" v-if="commentsList.length==0">
						暂时没有动态评论
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
			</view>
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
				
				commentsList:[],
				
				moreText:"加载更多",
				page:1,
				isLoad:0,
				noMore:false,
				
				isLoading:0,
				
				owo:owo,
				owoList:[],
				allowDelete:0,
				uid:0,
				
			}
		},
		onPullDownRefresh(){
			this.page = 1;
			this.noMore = false;
			this.moreText = "加载更多";
			this.getCommentsList(false);
		},
		onReachBottom() {
			if(!this.noMore) this.loadMore();
		},
		onShow(){
			var that = this;
			// #ifdef APP-PLUS
			
			//plus.navigator.setStatusBarStyle("dark")
			// #endif
		},
		onLoad() {
			var that = this;
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
			that.getCommentsList(false);
		},
		methods:{
			back(){
				uni.navigateBack({
					delta: 1
				});
			},
			loadMore(){
				var that = this;
				if(that.isLoad!=0 || that.noMore) return;
				that.moreText="正在加载中...";
				that.getCommentsList(true);
			},

			toInfo(cid,title){
				var that = this;
				
				uni.navigateTo({
				    url: '/pages/contents/info?cid='+cid+"&title="+title
				});
			},
			contentConfig(){
				var that = this;
				that.$Net.request({
					
					url: that.$API.contentConfig(),
					method: "get",
					dataType: 'json',
					success: function(res) {
						if(res.data.code==1){
							that.allowDelete = res.data.data.allowDelete;
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
			getCommentsList(isPage){
				var that = this;
				var userInfo = null;
				if(localStorage.getItem('userinfo')){
					var userInfo = JSON.parse(localStorage.getItem('userinfo'));
					that.uid = userInfo.uid;
				}else{
					uni.showToast({
						title: "请先登录哦",
						icon: 'none'
					})
					that.isLoading=1;
					uni.stopPullDownRefresh();
					return false;
				}
				var token = localStorage.getItem('token') || userInfo.token || '';
				if(!token){
					uni.showToast({ title: "请先登录哦", icon: 'none' });
					that.isLoading=1;
					uni.stopPullDownRefresh();
					return false;
				}
				var page = that.page;
				if(isPage){
					page++;
				}
				that.isLoad=1;
				that.$Net.request({
					url: that.$API.spaceList(),
					data:{
						"searchParams": JSON.stringify({uid:userInfo.uid,type:3}),
						"token":token,
						"limit":5,
						"page":page,
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
								that.noMore = list.length < 5;
								that.moreText = that.noMore ? "没有更多评论了" : "加载更多";
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
								that.noMore = true;
								if(!isPage) that.commentsList = [];
								that.moreText="没有更多评论了";
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

			toDelete(id){
				var that = this;
				var token = "";
				
				if(localStorage.getItem('userinfo')){
					var userInfo = JSON.parse(localStorage.getItem('userinfo'));
					token=userInfo.token;
				}
				var data = {
					"key":id,
					"token":token
				}
				uni.showModal({
				    title: '确定要删除该评论吗',
				    success: function (res) {
				        if (res.confirm) {
				            uni.showLoading({
				            	title: "加载中"
				            });
				            
				            that.$Net.request({
				            	url: that.$API.commentsDelete(),
				            	data:data,
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
				            			that.getCommentsList();
				            		}
				            		
				            	},
				            	fail: function(res) {
				            		setTimeout(function () {
				            			uni.hideLoading();
				            		}, 1000);
				            		uni.showToast({
				            			title: "网络不太好哦",
				            			icon: 'none'
				            		})
				            	}
				            })
				        } else if (res.cancel) {
				            console.log('用户点击取消');
				        }
				    }
				});
			},
		}
	}
</script>

<style>
</style>
