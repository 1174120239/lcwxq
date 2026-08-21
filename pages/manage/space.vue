<template>
	<view class="userpost" :class="AppStyle">
		<view class="header" :style="[{height:CustomBar + 'px'}]">
			<view class="cu-bar bg-white" :style="{'height': CustomBar + 'px','padding-top':StatusBar + 'px'}">
				<view class="action" @tap="back">
					<text class="cuIcon-back"></text>
				</view>
				<view class="content text-bold" :style="[{top:StatusBar + 'px'}]">
					动态管理
				</view>
			</view>
		</view>
		<view :style="[{padding:NavBar + 'px 10px 0px 10px'}]"></view>
		<view class="data-box">
			<view class="cu-bar bg-white search">
				<view class="search-form round">
					<text class="cuIcon-search"></text>
					<input type="text" placeholder="输入搜索关键字" v-model="searchText"  @input="searchTag"></input>
					<view class="search-close" v-if="searchText!=''" @tap="searchClose()"><text class="cuIcon-close"></text></view>
				</view>
			</view>
			<view class="search-type grid col-3">
				<view class="search-type-box" @tap="toType(0)" :class="type==0?'active':''">
					<text>待审核</text>
				</view>
				<view class="search-type-box" @tap="toType(1)" :class="type==1?'active':''">
					<text>已发布</text>
				</view>
				<view class="search-type-box" @tap="toType(2)" :class="type==2?'active':''">
					<text>已锁定</text>
				</view>
			</view>
		</view>
		<view class="cu-card dynamic no-card square-list">
			<view class="no-data" v-if="spaceList.length==0">
				<text class="cuIcon-text"></text>暂时没有数据
			</view>
			<block  v-for="(item,index) in spaceList" :key="item.id || index" v-if="spaceList.length>0">
				<view class="cu-item">
					<view class="cu-list menu-avatar">
						<view class="cu-item">
							<view class="cu-avatar round lg" :style="'background-image:url('+item.userJson.avatar+');'" @tap="toUserContents(item.userJson)">
							</view>
							<view class="content flex-sub">
								<view>{{item.userJson.name}}
								</view>
								<view class="text-gray text-sm flex">
									{{formatDate(item.created)}}
								</view>
								<view class="presentation-status-row" v-if="item.featured==1 || configuredPinType(item)>0">
									<text class="presentation-state is-featured" v-if="item.featured==1">精华</text>
									<text class="presentation-state is-list" v-if="configuredPinType(item)==1">列表置顶</text>
									<text class="presentation-state is-banner" v-if="configuredPinType(item)==2">横幅置顶</text>
									<text class="presentation-state is-expired" v-if="configuredPinType(item)>0 && !isPinActive(item)">{{pinStatusText(item)}}</text>
								</view>
							</view>
						</view>
					</view>
					<view class="text-content break-all" @tap="toInfo(item.id)">
						<rich-text :nodes="markHtml(item.text)"></rich-text>
					</view>
					<block  v-if="item.type==0">
						
						<view class="grid flex-sub padding-lr col-3 grid-square space-image-grid" :class="imageGridClass(item.picList.length)" v-if="item.picList.length>0">
							<view class="bg-img" :style="'background-image:url('+data+');'"
							 v-for="(data,i) in item.picList" :key="i" @tap="previewImage(item.picList,data)">
							</view>
						</view>
					</block>
					<block  v-if="item.type==1">
						<view class="grid flex-sub padding-lr">
							<block v-if="item.contentJson.cid==0">
								<view class="user-post-info">
									<view class="user-post-text">
										<view class="user-post-title">
											帖子不存在
										</view>
										<view class="user-post-intro">
											该帖子已被删除或数据缺失！
										</view>
									</view>
								</view>
							</block>
							<block v-else>
								<view class="user-post-info" @tap="goContentInfo(item.contentJson)">
									<view class="user-post-pic" v-if="item.contentJson.images.length>0">
										<image :src="item.contentJson.images[0]" mode="widthFix"></image>
									</view>
									<view class="user-post-text">
										<view class="user-post-title">
											{{replaceSpecialChar(item.contentJson.title)}}
										</view>
										<view class="user-post-intro">
											{{item.contentJson.text}}
										</view>
									</view>
								</view>
							</block>
							
						</view>
					</block>
					<block  v-if="item.type==2">
						<view class="grid flex-sub padding-lr">
							<block v-if="item.forwardJson.id==0">
								<view class="user-space-info">
									<view class="user-space-text">
										该动态已被删除或数据缺失！
									</view>
								</view>
							</block>
							<block v-else>
								<view class="user-space-info" @tap="toInfo(item.forwardJson.id)">
									<view class="user-space-text">
										<text class="text-blue">@{{item.forwardJson.username}}：</text><rich-text :nodes="markHtml(item.forwardJson.text)"></rich-text>
									</view>
									
									<view class="grid flex-sub col-3 grid-square space-image-grid forward-media margin-top-xs" :class="imageGridClass(item.forwardJson.picList.length)" v-if="item.forwardJson.picList.length>0">
										<view class="bg-img" :style="'background-image:url('+data+');'"
										 v-for="(data,i) in item.forwardJson.picList" :key="i">
										</view>
									</view>
								</view>
							</block>
							
						</view>
					</block>
					<block  v-if="item.type==4">
						<view class="padding-lr spaceVideo">
							<!--  #ifdef H5 || MP-->
							<video :src="item.pic"></video>
							<!--  #endif -->
							<!--  #ifdef APP-PLUS -->
							<view class="spaceVideo-play" @tap="goPlay(item.pic)">
								<text class="cuIcon-playfill"></text>
							</view>
							<!--  #endif -->
							
						</view>
					</block>
					<block  v-if="item.type==5">
						<view class="grid flex-sub padding-lr">
							<block v-if="item.shopJson.id==0">
								<view class="user-post-info">
									<view class="user-post-text">
										<view class="user-post-title">
											该商品商品不存在或已被删除！
										</view>
										<view class="user-post-intro">
											<text class="text-red text-lg text-bold">-- {{currencyName}}</text>
											
										</view>
										<view class="user-post-intro">
											<text class="text-gray text-sm">剩余数量：0</text>
											
										</view>
									</view>
								</view>
							</block>
							<block v-else>
								<view class="user-post-info" @tap="goShopInfo(item.shopJson.id)">
									<view class="user-post-pic">
										<image :src="item.shopJson.imgurl" mode="widthFix"></image>
									</view>
									<view class="user-post-text">
										<view class="user-post-title">
											{{replaceSpecialChar(item.shopJson.title)}}
										</view>
										<view class="user-post-intro">
											<text class="text-red text-lg text-bold">{{parseInt(item.shopJson.price)}} {{currencyName}}</text>
											
										</view>
										<view class="user-post-intro">
											<text class="text-gray text-sm">剩余数量：{{item.shopJson.num}}</text>
											
										</view>
									</view>
								</view>
							</block>
							
						</view>
					</block>
					<view class="presentation-controls">
						<view class="presentation-control" :class="{'is-active is-featured':item.featured==1,'is-busy':presentationBusy[item.id],'is-disabled':!canManagePresentation(item)}" @tap.stop="toggleFeatured(item,index)">
							<text class="cuIcon-favorfill"></text><text>{{item.featured==1 ? '取消精华' : '设为精华'}}</text>
						</view>
						<view class="presentation-control" :class="{'is-active is-list':configuredPinType(item)==1,'is-busy':presentationBusy[item.id],'is-disabled':!canManagePresentation(item)}" @tap.stop="togglePin(item,1,index)">
							<text class="cuIcon-list"></text><text>{{configuredPinType(item)==1 ? '取消列表置顶' : '列表置顶'}}</text>
						</view>
						<view class="presentation-control" :class="{'is-active is-banner':configuredPinType(item)==2,'is-busy':presentationBusy[item.id],'is-disabled':!canManagePresentation(item)}" @tap.stop="togglePin(item,2,index)">
							<text class="cuIcon-picfill"></text><text>{{configuredPinType(item)==2 ? '取消横幅置顶' : '横幅置顶'}}</text>
						</view>
					</view>
					<view class="presentation-unavailable" v-if="!canManagePresentation(item)">{{presentationUnavailableText(item)}}</view>
					<view class="forum-list-operate padding-sm text-center bg-white">
						<block v-if="item.status==0">
							<text class="bg-green cu-btn xs radius" @tap="toReview(item.id,1,index)"> <text class="cuIcon-check"></text>通过</text>
							<text class="bg-red cu-btn xs radius margin-left" @tap="toReview(item.id,0,index)"><text class="cuIcon-close"></text>不通过</text>
						</block>
						<block v-if="item.status==1">
							<text class="bg-black cu-btn xs radius" @tap="toLock(item.id,2,index)"><text class="cuIcon-lock"></text>锁定</text>
						</block>
						<block v-if="item.status==2">
							<text class="bg-black cu-btn xs radius" @tap="toLock(item.id,1,index)"><text class="cuIcon-unlock"></text>解除锁定</text>
						</block>
						<text class="bg-blue cu-btn xs radius margin-left" @tap="showOwner(item)"><text class="cuIcon-attention"></text>真实发布者</text>
						<text class="bg-red cu-btn xs radius margin-left" @tap="toDelete(item.id)"><text class="cuIcon-delete"></text>删除</text>
					</view>
				</view>
			</block>
			
		</view>
		<view class="videoPlay" v-if="isPlay">
			<view class="videoPlay-bg" @tap="isPlay=false">
				<view class="videoPlay-close" @tap="isPlay=false">
					<i class="cuIcon-close"></i>
				</view>
			</view>
			<video :src="curVideo"></video>
		</view>
	</view>
</template>

<script>
	import { localStorage } from '../../js_sdk/mp-storage/mp-storage/index.js'
	import { renderRichContent } from '@/utils/richContent.js'
	export default {
		data() {
			return {
				StatusBar: this.StatusBar,
				CustomBar: this.CustomBar,
				NavBar:this.StatusBar +  this.CustomBar,
			AppStyle:this.$store.state.AppStyle,
				
				page:1,
				moreText:"加载更多",
				
				isLoad:0,
				token:"",
				spaceList:[],
				
				searchText:"",
				
				type:0,
				vipDiscount:0,
				currencyName:"",
				group:"",
				uid:0,
				isPlay:false,
				curVideo:"",
				presentationBusy:{},
				
			}
		},
		onPullDownRefresh(){
			var that = this;
			that.page=1;
			that.getSpaceList(false);
			var timer = setTimeout(function() {
				uni.stopPullDownRefresh();
			}, 1000)
			
		},
		onReachBottom() {
		    //触底后执行的方法，比如无限加载之类的
			var that = this;
			if(that.isLoad==0){
				that.loadMore();
			}
		},
		onShow(){
			var that = this;
			// #ifdef APP-PLUS
			
			plus.navigator.setStatusBarStyle("dark")
			// #endif
			that.page = 1;
			if(localStorage.getItem('token')){
				that.token=localStorage.getItem('token');
				that.getSpaceList(false);
			}
			if(localStorage.getItem('userinfo')){
				
				var userInfo = JSON.parse(localStorage.getItem('userinfo'));
				that.group = userInfo.group;
			}
			
			
		},
		onLoad() {
			var that = this;
			// #ifdef APP-PLUS || MP
			that.NavBar = this.CustomBar;
			// #endif
			that.currencyName = that.$API.getCurrencyName();
			if(localStorage.getItem('token')){
				that.token=localStorage.getItem('token');
				that.getSpaceList(false);
			}
			
		},
		methods: {
			canManagePresentation(item){
				return !!item && Number(item.status) === 1 && Number(item.onlyMe) !== 1 && Number(item.type) !== 3;
			},
			presentationUnavailableText(item){
				if(!item) return '当前动态不能设置展示状态';
				if(Number(item.status) === 0) return '待审核动态通过后才能设置展示状态';
				if(Number(item.status) === 2) return '已锁定动态解锁后才能设置展示状态';
				if(Number(item.onlyMe) === 1) return '私密动态不能设置公开展示状态';
				if(Number(item.type) === 3) return '评论回复不能设置精华或置顶';
				return '当前动态不能设置展示状态';
			},
			ensurePresentationAvailable(item){
				if(this.canManagePresentation(item)) return true;
				uni.showToast({ title:this.presentationUnavailableText(item), icon:'none' });
				return false;
			},
			configuredPinType(item){
				if(!item) return 0;
				return Number(item.pinConfiguredType != null ? item.pinConfiguredType : item.pinType) || 0;
			},
			isPinActive(item){
				if(this.configuredPinType(item) === 0) return false;
				var now = Math.floor(Date.now() / 1000);
				var start = Number(item.pinStartTime || 0);
				var end = Number(item.pinEndTime || 0);
				return (!start || start <= now) && (!end || end > now);
			},
			pinStatusText(item){
				var now = Math.floor(Date.now() / 1000);
				return Number(item.pinStartTime || 0) > now ? '待生效' : '已到期';
			},
			imageGridClass(count){
				if(count === 1){
					return 'is-single';
				}
				if(count === 2){
					return 'is-double';
				}
				return 'is-multi';
			},
			back(){
				uni.navigateBack({
					delta: 1
				});
			},
			loadMore(){
				var that = this;
				that.moreText="正在加载中...";
				that.isLoad=1;
				that.getSpaceList(true);
			},
			toType(i){
				var that = this;
				that.type=i;
				that.page=1;
				that.moreText="加载更多";
				that.isLoad=0;
				that.spaceList = [];
				that.getSpaceList(false);
			},
			previewImage(imageList,image) {
				//预览图片
				uni.previewImage({
					urls: imageList,
					current: image
				});
			},
			goPlay(url){
				var that = this;
				that.curVideo =url;
				that.isPlay=true;
			},
			subText(text,num){
				if(text.length < null){
					return text.substring(0,num)+"……"
				}else{
					return text;
				}
				
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
			formatDate(datetime) {
				var datetime = new Date(parseInt(datetime * 1000));
				var year = datetime.getFullYear(),
					month = ("0" + (datetime.getMonth() + 1)).slice(-2),
					date = ("0" + datetime.getDate()).slice(-2),
					hour = ("0" + datetime.getHours()).slice(-2),
					minute = ("0" + datetime.getMinutes()).slice(-2);
				var result = year + "-" + month + "-" + date + " " + hour + ":" + minute;
				return result;
			},
			formatNumber(num) {
			    return num >= 1e3 && num < 1e4 ? (num / 1e3).toFixed(1) + 'k' : num >= 1e4 ? (num / 1e4).toFixed(1) + 'w' : num
			},
			markHtml(text){
				return renderRichContent(this.replaceSpecialChar(String(text || '')), { emojiList: this.owoList || [] })
			},
			TransferString(content)
			{  
			    var string = content;  
			    try{  
			        string=string.replace(/\r\n/g,"<br>")  
			        string=string.replace(/\n/g,"<br>");  
			    }catch(e) {  
			        return content;
			    }  
			    return string;  
			},
			replaceAll(string, search, replace) {
			  return string.split(search).join(replace);
			},
			searchTag(){
				var that = this;
				var searchText = that.searchText;
				that.page=1;
				that.getSpaceList();
			
			},
			searchClose(){
				var that = this;
				that.searchText = "";
				that.page=1;
				that.getSpaceList();
			
			},
			getSpaceList(isPage){
				var that = this;
				var page = that.page;
				if(isPage){
					page++;
				}
				var data = {
					"status":that.type
				}
				that.$Net.request({
					url: that.$API.spaceList(),
					data:{
						"searchParams":JSON.stringify(that.$API.removeObjectEmptyKey(data)),
						"limit":10,
						"page":page,
						"order":"created",
						"token":that.token,
						"isManage":1
					},
					method: "get",
					dataType: 'json',
					success: function(res) {
						that.isLoading = 1;
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
					},
					fail: function(res) {
						that.isLoading = 1;
						that.moreText="加载更多";
						that.isLoad=0;
					}
				})
			},
			toggleFeatured(item,index){
				if(!item || this.presentationBusy[item.id] || !this.ensurePresentationAvailable(item)) return false;
				this.updatePresentation(item,index,{
					featured:Number(item.featured) === 1 ? 0 : 1
				});
			},
			togglePin(item,pinType,index){
				var that = this;
				if(!item || that.presentationBusy[item.id] || !that.ensurePresentationAvailable(item)) return false;
				var currentType = that.configuredPinType(item);
				if(currentType === pinType){
					uni.showModal({
						title: pinType === 2 ? '取消横幅置顶' : '取消列表置顶',
						content: '取消后该动态会回到普通动态流。',
						success: function(result){
							if(result.confirm) that.updatePresentation(item,index,{ pinType:0 });
						}
					});
					return;
				}
				var durationValues = [0,86400,604800,2592000];
				uni.showActionSheet({
					itemList:['长期有效','24 小时','7 天','30 天'],
					success:function(choice){
						var now = Math.floor(Date.now() / 1000);
						var duration = durationValues[choice.tapIndex] || 0;
						that.updatePresentation(item,index,{
							pinType:pinType,
							pinOrder:now,
							pinStartTime:0,
							pinEndTime:duration ? now + duration : 0
						});
					}
				});
			},
			updatePresentation(item,index,changes){
				var that = this;
				if(!item || that.presentationBusy[item.id]) return false;
				var data = {
					id:item.id,
					token:that.token,
					featured:Number(item.featured || 0),
					pinType:that.configuredPinType(item),
					pinOrder:Number(item.pinOrder || 0),
					pinStartTime:Number(item.pinStartTime || 0),
					pinEndTime:Number(item.pinEndTime || 0)
				};
				Object.keys(changes || {}).forEach(function(key){ data[key] = changes[key]; });
				that.$set(that.presentationBusy,item.id,true);
				uni.showLoading({ title:'正在保存' });
				that.$Net.request({
					url:that.$API.spacePresentation(),
					data:data,
					header:{ 'Content-Type':'application/x-www-form-urlencoded' },
					method:'post',
					dataType:'json',
					success:function(res){
						if(res.data.code==1){
							var state = res.data.data || {};
							Object.keys(state).forEach(function(key){ that.$set(that.spaceList[index],key,state[key]); });
						}
						uni.showToast({ title:res.data.msg || (res.data.code==1 ? '保存成功' : '保存失败'), icon:'none' });
					},
					fail:function(){
						uni.showToast({ title:'网络开小差了，请稍后重试', icon:'none' });
					},
					complete:function(){
						uni.hideLoading();
						that.$set(that.presentationBusy,item.id,false);
					}
				});
			},
			toLock(id,type,index){
				var that = this;
				
				var typeText = "确定要锁定动态吗？";
				if(type==1){
					typeText = "确定要取消锁定动态吗？";
				}
				var token = "";
				if(localStorage.getItem('userinfo')){
					var userInfo = JSON.parse(localStorage.getItem('userinfo'));
					token=userInfo.token;
				}
				var data = {
					"id":id,
					"type":type,
					"token":token
				}
				uni.showModal({
				    title: typeText,
				    success: function (res) {
				        if (res.confirm) {
							
				            uni.showLoading({
				            	title: "加载中"
				            });
				            
				            that.$Net.request({
				            	url: that.$API.spaceLock(),
				            	data:data,
				            	header:{
				            		'Content-Type':'application/x-www-form-urlencoded'
				            	},
				            	method: "post",
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
				            			that.page=1;
				            			that.getSpaceList(false);
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
			toReview(id,type,index){
				var that = this;
				var typeText = "确定要审核通过动态吗？";
				if(type==0){
					typeText = "确定要不过审动态吗？";
				}
				var token = "";
				if(localStorage.getItem('userinfo')){
					var userInfo = JSON.parse(localStorage.getItem('userinfo'));
					token=userInfo.token;
				}
				var data = {
					"id":id,
					"type":type,
					"token":token
				}
				uni.showModal({
				    title: typeText,
				    success: function (res) {
				        if (res.confirm) {
							
				            uni.showLoading({
				            	title: "加载中"
				            });
				            
				            that.$Net.request({
				            	url: that.$API.spaceReview(),
				            	data:data,
				            	header:{
				            		'Content-Type':'application/x-www-form-urlencoded'
				            	},
				            	method: "post",
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
				            			that.page=1;
				            			that.getSpaceList(false);
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
			toDelete(id){
				var that = this;
				var token = "";
				
				if(localStorage.getItem('userinfo')){
					var userInfo = JSON.parse(localStorage.getItem('userinfo'));
					token=userInfo.token;
				}
				var data = {
					"id":id,
					"token":token
				}
				uni.showModal({
					title: '确定要删除该动态吗',
					success: function (res) {
						if (res.confirm) {
							uni.showLoading({
								title: "加载中"
							});
							
							that.$Net.request({
								url: that.$API.spaceDelete(),
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
										that.page=1;
										that.getSpaceList(false);
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
			showOwner(item){
				var that = this;
				var token = "";
				if(localStorage.getItem('userinfo')){
					var userInfo = JSON.parse(localStorage.getItem('userinfo'));
					token = userInfo.token;
				}
				if(!item || !item.id){
					return false;
				}
				uni.showLoading({
					title: "加载中"
				});
				that.$Net.request({
					url: that.$API.anonymousOwner(),
					data: {
						"sid": item.id,
						"token": token
					},
					header: {
						'Content-Type': 'application/x-www-form-urlencoded'
					},
					method: "get",
					dataType: 'json',
					success: function(res) {
						setTimeout(function() {
							uni.hideLoading();
						}, 1000);
						if (res.data.code == 1) {
							var publisher = res.data.data || {};
							var name = publisher.screenName || publisher.name || "";
							uni.showModal({
								title: '真实发布者',
								content: 'UID：' + publisher.uid + (name ? '\n昵称：' + name : ''),
								showCancel: false
							});
						} else {
							uni.showToast({
								title: res.data.msg || '该动态不是匿名动态',
								icon: 'none'
							});
						}
					},
					fail: function() {
						setTimeout(function() {
							uni.hideLoading();
						}, 1000);
						uni.showToast({
							title: "网络不太好哦",
							icon: 'none'
						});
					}
				});
			},
			toInfo(id){
				var that = this;
				
				uni.navigateTo({
				    url: '/pages/space/info?id='+id
				});
			},
			forward(id){
				var that = this;
				uni.navigateTo({
				    url: '/pages/space/post?type=2&id='+id
				});
			},
			
			goShopInfo(sid){
				var that = this;
				uni.navigateTo({
				    url: '/pages/contents/shopinfo?sid='+sid
				});
			},
			toUserContents(data){
				var that = this;
				var name = data.name;
				var title = data.name+"的信息";
				var id= data.uid;
				var type="user";
				uni.navigateTo({
				    url: '/pages/contents/userinfo?title='+title+"&name="+name+"&uid="+id+"&avatar="+encodeURIComponent(data.avatar)
				});
			},
			goContentInfo(data){
				var that = this;
				if(data.status!="publish"){
					uni.showToast({
						title:"帖子正在审核中，请稍后再试！",
						icon:'none',
						duration: 1000,
						position:'bottom',
					});
					return false;
				}
				uni.navigateTo({
				    url: '/pages/contents/info?cid='+data.cid+"&title="+data.title
				});
			},
		},
	}
</script>

<style>
.presentation-status-row {
	display: flex;
	align-items: center;
	flex-wrap: wrap;
	gap: 8rpx;
	margin-top: 8rpx;
}

.presentation-state {
	display: inline-flex;
	align-items: center;
	min-height: 30rpx;
	padding: 0 10rpx;
	border-radius: 6rpx;
	font-size: 19rpx;
	line-height: 30rpx;
	color: #48605d;
	background: #edf3f1;
}

.presentation-state.is-featured {
	color: #a85b11;
	background: #fff1dc;
}

.presentation-state.is-list {
	color: #316865;
	background: #e4f3f0;
}

.presentation-state.is-banner {
	color: #9b4a30;
	background: #fbe8df;
}

.presentation-state.is-expired {
	color: #7c868b;
	background: #f1f3f4;
}

.presentation-controls {
	display: grid;
	grid-template-columns: repeat(3, minmax(0, 1fr));
	gap: 10rpx;
	margin: 20rpx 24rpx 4rpx;
	padding-top: 18rpx;
	border-top: 1rpx solid #edf0ef;
}

.presentation-control {
	display: flex;
	align-items: center;
	justify-content: center;
	gap: 7rpx;
	min-width: 0;
	min-height: 62rpx;
	padding: 8rpx 6rpx;
	border: 1rpx solid #dfe7e5;
	border-radius: 8rpx;
	font-size: 22rpx;
	color: #5f6e70;
	background: #fff;
}

.presentation-control .cuIcon-favorfill,
.presentation-control .cuIcon-list,
.presentation-control .cuIcon-picfill {
	font-size: 25rpx;
}

.presentation-control.is-active.is-featured {
	color: #9b5b18;
	border-color: #efce9e;
	background: #fff5e6;
}

.presentation-control.is-active.is-list {
	color: #28645f;
	border-color: #aad0ca;
	background: #edf8f6;
}

.presentation-control.is-active.is-banner {
	color: #994d34;
	border-color: #e6b8a7;
	background: #fff0e9;
}

.presentation-control.is-busy {
	opacity: .52;
}

.presentation-control.is-disabled,
.presentation-control.is-active.is-disabled {
	border-color: #e4e8e7;
	background: #f5f7f7;
	color: #98a2a2;
	opacity: .7;
}

.presentation-unavailable {
	margin: 9rpx 24rpx 2rpx;
	font-size: 20rpx;
	line-height: 1.4;
	color: #8b9695;
}

@media screen and (min-width: 768px) {
	.presentation-controls {
		max-width: 560px;
	}
}
</style>
