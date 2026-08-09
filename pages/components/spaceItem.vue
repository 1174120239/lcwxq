<template>
	<view class="space-feed" :class="{'space-feed-compact': compact}">
		<view class="cu-card dynamic no-card square-list">
			
			<block  v-for="(item,index) in spaceList" :key="item.id || index" v-if="spaceList.length>0">
			<block v-if="item.type==0||item.type==4">
				<view class="cu-item cu-item2" :class="{'is-entering': index < 3}" :style="index < 3 ? {'animation-delay': (index * 55) + 'ms'} : null" @tap="toInfo(item.id,index)">
					<view class="cu-list menu-avatar">
						<view class="cu-item cu-item2">
							<campus-avatar class="cu-avatar round lg" :src="item.userJson.avatar" :name="item.userJson.name" @tap.stop="toUserContents(item.userJson)"></campus-avatar>
							<view class="content flex-sub space-author-content">
								<view class="space-author-line"><text class="space-author-name" :class="{'is-vip': item.userJson.isvip>0}">{{item.userJson.name}}</text>
								<text class="space-campus-badge" v-if="item.userJson.campus">{{item.userJson.campus}}</text>
								<block v-if="item.userJson.uid!=0">
									<text class="userlv space-vip-badge" v-if="item.userJson.isvip>0">VIP</text>
									<text class="userlv space-level-badge" :style="getLvStyle(item.userJson.experience)">{{getLv(item.userJson.experience)}}</text>
									<!-- <text class="userlv" :style="getUserLvStyle(item.userJson.lv)">{{getUserLv(item.userJson.lv)}}</text>
									
									<text class="userlv customize" v-if="item.userJson.customize&&item.userJson.customize!=''">{{item.userJson.customize}}</text> -->
									
								</block>
								</view>
								<view class="text-gray text-sm flex">
									{{formatDate(item.created)}}
									<block v-if="item.userJson.uid!=0&&item.userJson.uid==uid">
										<text class="text-blue margin-left-sm" @tap.stop="edit(item.id)">编辑</text>
										<text class="text-red margin-left-sm" @tap.stop="toDelete2(item.id)">删除</text>
									</block>
									<block v-else>
										<text v-if="group=='administrator'||group=='editor'" class="text-blue margin-left-sm" @tap.stop="edit(item.id)">编辑</text>
										<text v-if="group=='administrator'" class="text-red margin-left-sm" @tap.stop="toDelete(item.id)">删除</text>
									</block>
										
									
								
								</view>
							</view>
							<text class="space-more cuIcon-moreandroid"></text>
						</view>
					</view>
					
					<block  v-if="item.type==0">
						<view class="space-topic-row" v-if="item.topics && item.topics.length>0">
							<text class="space-topic-tag" v-for="topic in item.topics" :key="topic.mid">#{{topic.name}}</text>
						</view>
						<view class="text-content break-all space-text-preview" :class="{'space-text-preview-long': isLongText(item.text)}" @tap.stop="toInfo(item.id,index)">
							<rich-text :nodes="markHtml(item.text)"></rich-text>
						</view>
						<view class="space-read-more" v-if="isLongText(item.text)" @tap.stop="toInfo(item.id,index)">
							<text>查看全文</text><text class="cuIcon-right margin-left-xs"></text>
						</view>
						<view class="space-image-grid" :class="imageGridClass(item.picList.length)" v-if="item.picList.length>0">
							<view class="bg-img" :style="'background-image:url('+data+');'"
							 v-for="(data,i) in item.picList" :key="i" @tap.stop="previewImage(item.picList,data)">
							</view>
						</view>
					</block>
					
					
					<block  v-if="item.type==4">
						<view class="space-topic-row" v-if="item.topics && item.topics.length>0">
							<text class="space-topic-tag" v-for="topic in item.topics" :key="topic.mid">#{{topic.name}}</text>
						</view>
						<view class="text-content break-all space-text-preview" :class="{'space-text-preview-long': isLongText(item.text)}" @tap.stop="toInfo(item.id,index)">
							<rich-text :nodes="markHtml(item.text)"></rich-text>
						</view>
						<view class="space-read-more" v-if="isLongText(item.text)" @tap.stop="toInfo(item.id,index)">
							<text>查看全文</text><text class="cuIcon-right margin-left-xs"></text>
						</view>
						<view class="padding-lr spaceVideo" @tap.stop="noop">
							<!--  #ifdef H5 || MP-->
							<video :src="item.pic" @play="play(item.pic)" @tap.stop="noop"></video>
							<!--  #endif -->
							<!--  #ifdef APP-PLUS -->
							<view class="paceVideo2">
													<view class="spaceVideo-play" :style="{ backgroundImage: 'url(' + curIMG + ')', backgroundSize: 'cover', backgroundRepeat: 'no-repeat', backgroundPosition: 'center center' }" @tap.stop="goPlay(item.pic,item.text,item.userJson.name)">
													<text class="cuIcon-playfill"></text>
													</view>
												</view>
							<!--  #endif -->
							
							
						</view>
					</block>
					<block v-if="item.userJson.uid!=0&&item.userJson.uid==uid">
					
						<view class="text-center grid col-3 padding-xs">
							<view class="square-post-btn"><text class="cuIcon-attention"></text>{{formatNumber(item.views || 0)}}</view>
							<view class="square-post-btn"  @tap.stop="toInfo(item.id,index)">
								<text class="cuIcon-community"></text>
								<block v-if="item.reply>0">
									{{formatNumber(item.reply)}}
								</block>
								<block v-else>
									评论
								</block>
							</view>
							<view class="square-post-btn" @tap.stop="toLike(item.id,index)">
								<text class="cuIcon-appreciate" :class="item.isLikes==1?'text-blue':''"></text>
								<block v-if="item.likes>0">
									{{formatNumber(item.likes)}}
								</block>
								<block v-else>
									点赞
								</block>
							</view>
						</view>
					</block>
					<block v-else>
					<view class="text-center grid col-3 padding-xs">
						<view class="square-post-btn"><text class="cuIcon-attention"></text>{{formatNumber(item.views || 0)}}</view>
						<view class="square-post-btn"  @tap.stop="toInfo(item.id,index)">
							<text class="cuIcon-community"></text>
							<block v-if="item.reply>0">
								{{formatNumber(item.reply)}}
							</block>
							<block v-else>
								评论
							</block>
						</view>
						<view class="square-post-btn" @tap.stop="toLike(item.id,index)">
							<text class="cuIcon-appreciate" :class="item.isLikes==1?'text-blue':''"></text>
							<block v-if="item.likes>0">
								{{formatNumber(item.likes)}}
							</block>
							<block v-else>
								点赞
							</block>
						</view>
					</view>
					</block>
				</view>
			</block>
			</block>
		</view>
		<view class="videoPlay" v-if="isPlay">
			<view class="videoPlay-bg" @tap="isPlay=false">
				<view class="videoPlay-close" @tap="isPlay=true">
					<i class="cuIcon-close"></i>
				</view>
			</view>
			<video :src="curVideo" http-cache="true" play-strategy="0" loop autoplay :title="mp4title"></video>
		</view>
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
	    props: {
	        spaceList: {
			  type: Array,
			  default: () => []
			},
			isHead: {
			  type: Boolean,
			  default: true
			},
			compact: {
			  type: Boolean,
			  default: false
			}
	    },
		name: "spaceItem",
		data() {
			return {
				owo:owo,
				owoList:[],
				vipDiscount:0,
				currencyName:"",
				curIMG:"",
				group:"",
				mp4bt:"",
				mp4name:"",
				mp4title:"视频动态",
				uid:0,
				isPlay:false,
				curVideo:"",
			};
		},
		created(){
			var that = this;
			that.getvideoimg()
			if(localStorage.getItem('userinfo')){
							
				var userInfo = JSON.parse(localStorage.getItem('userinfo'));
				that.group = userInfo.group;
				that.uid = userInfo.uid;
			}
			that.currencyName = that.$API.getCurrencyName();
			// #ifdef APP-PLUS || H5
			var owo = that.owo.data;
			var owoList=[];
			for(var i in owo){
				owoList = owoList.concat(owo[i].container);
			}
			that.owoList = owoList;
			// #endif
		},
		methods: {
			
			noop(){},
			
			imageGridClass(count){
				if(count === 1){
					return 'is-single';
				}
				if(count === 2){
					return 'is-double';
				}
				return 'is-multi';
			},
			getvideoimg(){
				var that = this;
				      uni.request({
				        url:that.$API.SMgonggao(),
				        method:'GET',
				        dataType:"json",
				        success(res) {
						  that.curIMG = res.data.videoimg;
				        },
				        fail(error) {
				          console.log(error);
				        }
				      })
				},
			previewImage(imageList,image) {
				//预览图片
				uni.previewImage({
					urls: imageList,
					current: image
				});
			},
			subText(text,num){
				if(text.length < null){
					return text.substring(0,num)+"……"
				}else{
					return text;
				}
				
			},
			isLongText(text){
				var content = String(text || '')
					.replace(/<br\s*\/?\s*>/gi, '\n')
					.replace(/<\/(p|div|li)>/gi, '\n')
					.replace(/<[^>]*>/g, '')
					.replace(/&nbsp;|&#160;/gi, ' ')
					.replace(/&#10;|&#x0a;/gi, '\n')
					.replace(/&#13;|&#x0d;/gi, '\r')
					.replace(/\|\|rn\|\||\/r\/n|\\r\\n|\\n/gi, '\n')
					.trim();
				var lineBreaks = (content.match(/\r\n|\r|\n/g) || []).length;
				var textLimit = this.compact ? 44 : 90;
				var lineLimit = this.compact ? 2 : 4;
				return content.length > textLimit || lineBreaks >= lineLimit;
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
			  var now = new Date();
			  var diff = now - new Date(datetime * 1000);
			  var minuteDiff = Math.floor(diff / 60000);
			  var hourDiff = Math.floor(diff / 3600000);
			  var dayDiff = Math.floor(diff / 86400000);
			  var weekDiff = Math.floor(dayDiff / 7);
			  var monthDiff = Math.floor(diff / 2592000000);
			  var yearDiff = Math.floor(diff / 31536000000);
			
			  if (diff < 60000) {
			    return Math.floor(diff / 1000) + "秒前";
			  } else if (diff < 3600000) {
			    return minuteDiff + "分钟前";
			  } else if (hourDiff < 24) {
			    return hourDiff + "小时前";
			  } else if (dayDiff < 7 && dayDiff > 0) {
			    return dayDiff + "天前";
			  } else if (weekDiff > 0 && monthDiff <= 1) {
			    return weekDiff + "周前";
			  } else if (monthDiff > 1 && monthDiff < 12) {
			    return monthDiff + "个月前";
			  } else if (yearDiff >= 1) {
			    return yearDiff + "年前";
			  } else {
			    return "刚刚";
			  }
			},
			formatNumber(num) {
			    return num >= 1e3 && num < 1e4 ? (num / 1e3).toFixed(1) + 'k' : num >= 1e4 ? (num / 1e4).toFixed(1) + 'w' : num
			},
			toInfo(id,index){
				var that = this;
				
				uni.navigateTo({
				    url: '/pages/space/info?id='+id,
					success: function() {
						if(typeof index === 'number' && that.spaceList[index]){
							var views = Number(that.spaceList[index].views) || 0;
							that.$set(that.spaceList[index], 'views', views + 1);
						}
					}
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
			goAds(data){
				var that = this;
				var url = data.url;
				var type = data.urltype;
				// #ifdef APP-PLUS
				if(type==1){
					plus.runtime.openURL(url);
				}
				if(type==0){
					plus.runtime.openWeb(url);
				}
				// #endif
				// #ifdef H5
				window.open(url)
				// #endif
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
			markHtml(text){
				var that = this;
				text = that.replaceAll(text,"<","&lt;");
				text = that.replaceAll(text,">","&gt;");
				var owoList=that.owoList;
				for(var i in owoList){
				
					if(that.replaceSpecialChar(text).indexOf(owoList[i].data) != -1){
						text = that.replaceAll(that.replaceSpecialChar(text),owoList[i].data,"<img src='/"+owoList[i].icon+"' class='tImg' />")
						
					}
				}
				text = that.replaceAll(text,"/r/n","<br>");
				text = that.replaceAll(text,"||rn||","<br>");
				text = that.replaceAll(text,"\\r\\n","<br>");
				text = that.replaceAll(text,"\\n","<br>");
				text = that.TransferString(text);
				return text;
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
			getLv(i){
				var that = this;
				if(!i){
					var i = 0;
				}
				var lv  = that.$API.getLever(i);
				var leverList = that.$API.GetLeverList();
				return leverList[lv];
			},
			getLvStyle(i){
				var that = this;
				if(!i){
					var i = 0;
				}
				var lv  = that.$API.getLever(i);
				var rankStyle = that.$API.GetRankStyle();
				var userlvStyle ="color:#fff;background-color: "+rankStyle[lv];
				return userlvStyle;
			},
			follow(type,uid,index){
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
				that.spaceList[index].isFollow = type;
				var data = {
					token:token,
					touid:uid,
					type:type,
				}
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
						if(res.data.code==0){
							that.spaceList[index].isFollow = 0;
						}else{
							var spaceList = that.spaceList;
							for(var i in spaceList){
								if(spaceList[i].userJson.uid==uid){
									spaceList[i].isFollow = type;
								}
							}
							that.spaceList = spaceList;
						}
					},
					fail: function(res) {
						setTimeout(function () {
							uni.hideLoading();
						}, 1000);
						uni.showToast({
							title: "网络开小差了哦",
							icon: 'none'
						})
						
					}
				})
			},
			toLike(id,index){
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
				var wasLiked = that.spaceList[index].isLikes == 1;
				var oldLikes = Number(that.spaceList[index].likes || 0);
				that.spaceList[index].isLikes = wasLiked ? 0 : 1;
				that.spaceList[index].likes = wasLiked ? Math.max(0, oldLikes - 1) : oldLikes + 1;
				var data = {
					token:token,
					id:id,
				}
				uni.showLoading({
					title: "加载中"
				});
				that.$Net.request({
					
					url: that.$API.spaceLikes(),
					data:data,
					header:{
						'Content-Type':'application/x-www-form-urlencoded'
					},
					method: "post",
					dataType: 'json',
					success: function(res) {
						//console.log(JSON.stringify(res))
						setTimeout(function () {
							uni.hideLoading();
						}, 1000);
						uni.showToast({
							title: res.data.msg,
							icon: 'none'
						})
						if(res.data.code==0){
							that.spaceList[index].isLikes = wasLiked ? 1 : 0;
							that.spaceList[index].likes = oldLikes;
						}else if(res.data.data === 0 || res.data.data === 1){
							that.spaceList[index].isLikes = res.data.data;
						}
						
					},
					fail: function(res) {
						setTimeout(function () {
							uni.hideLoading();
						}, 1000);
						uni.showToast({
							title: "网络开小差了哦",
							icon: 'none'
						})
						that.spaceList[index].isLikes = wasLiked ? 1 : 0;
						that.spaceList[index].likes = oldLikes;
						
					}
				})
			},
			edit(id){
				var that = this;
				uni.navigateTo({
				    url: '/pages/space/post?postType=edit&id='+id
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
			toBan(uid){
				if(uid==0){
					uni.showToast({
						title: "该用户不存在",
						icon: 'none'
					})
					return false;
				}
				uni.navigateTo({
					url: '/pages/manage/banuser?uid='+uid
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
									if(res.data.code==1){
										uni.showToast({
											title: res.data.msg,
											icon: 'none'
										})
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
										title: "网络开小差了哦",
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
			toDelete2(id){
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
									if(res.data.code==1){
										//执行积分程序
										if (localStorage.getItem('userinfo')) {
										  var userInfo = JSON.parse(localStorage.getItem('userinfo'));
										  that.username = userInfo.name;
										
											}
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
										title: "网络开小差了哦",
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
			
			
			
			play (e) {
			  this.root.$emit('play')
			  // #ifndef APP-PLUS
			  if (this.root.pauseVideo) {
			    let flag = false
			    const id = e.target.id
			    for (let i = this.root._videos.length; i--;) {
			      if (this.root._videos[i].id === id) {
			        flag = true
			      } else {
			        this.root._videos[i].pause() // 自动暂停其他视频
			      }
			    }
			    // 将自己加入列表
			    if (!flag) {
			      const ctx = uni.createVideoContext(id
			        // #ifndef MP-BAIDU
			        , this
			        // #endif
			      )
			      ctx.id = id
			      if (this.root.playbackRate) {
			        ctx.playbackRate(this.root.playbackRate)
			      }
			      this.root._videos.push(ctx)
			    }
			  }
			  // #endif
			},
			goPlay(url,title,name){
				var that = this;
				that.curVideo = url;
				that.mp4bt = title;
				that.mp4name = name;
				that.mp4title = that.mp4bt + ' - ' + that.mp4name + ' | ' + that.$API.GetAppName();
				that.isPlay=true;
			}
		}
	}
</script>

<style>

.space-feed {
	padding: 0 8rpx 20rpx;
}

.space-feed .cu-card.dynamic {
	margin: 0;
}

.space-feed .square-list > .cu-item2 {
	margin: 0 0 22rpx !important;
	padding: 10rpx 0 2rpx;
	border: 1rpx solid #e2e8e7;
	border-radius: 16rpx !important;
	background: #ffffff;
	box-shadow: none;
	overflow: hidden;
	transition: background-color 180ms ease, border-color 180ms ease;
}

.space-feed .square-list > .cu-item2.is-entering {
	animation: spaceCardIn 180ms ease-out both;
}

.space-feed .square-list > .cu-item2:active {
	background: #f8fbfa;
}

.space-feed .square-list .cu-list.menu-avatar {
	background: transparent !important;
}

.space-feed .square-list .cu-list.menu-avatar > .cu-item2 {
	position: relative;
	min-height: 116rpx;
	margin: 0 !important;
	padding-right: 30rpx;
	background: transparent !important;
	box-shadow: none;
}

.space-more {
	position: absolute;
	top: 34rpx;
	right: 30rpx;
	font-size: 34rpx;
	color: #879198;
}

.space-feed .square-list .cu-avatar.lg {
	width: 80rpx;
	height: 80rpx;
	border: 1rpx solid #e2e9e7;
	box-shadow: none;
}

.space-feed .square-list .content.flex-sub > view:first-child {
	font-size: 29rpx;
	font-weight: 700;
	color: #304150;
}

.space-author-content {
	min-width: 0;
	padding-right: 42rpx !important;
}

.space-author-line {
	display: flex;
	align-items: center;
	flex-wrap: wrap;
	gap: 6rpx 10rpx;
	min-width: 0;
}

.space-author-name {
	max-width: min(46vw, 360rpx);
	min-width: 0;
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
}

.space-author-name.is-vip {
	color: #e96282;
}

.space-campus-badge {
	flex: 0 0 auto;
	max-width: 150rpx;
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
	font-size: 20rpx;
	font-weight: 500;
	line-height: 28rpx;
	color: #788b8c;
}

.space-vip-badge,
.space-level-badge {
	flex: 0 0 auto;
	margin: 0 !important;
	font-size: 18rpx;
	line-height: 28rpx;
}

.space-vip-badge {
	padding: 3rpx 10rpx !important;
	border-radius: 12rpx !important;
	background: linear-gradient(135deg, #f2ad5c, #e6216d 62%, #901ccb);
	color: #fff !important;
}

.space-feed .square-list .text-gray.text-sm {
	margin-top: 6rpx;
	font-size: 22rpx;
	color: #8a99a7 !important;
}

.space-category {
	display: inline-flex;
	align-items: center;
	min-height: 42rpx;
	margin: 8rpx 30rpx 16rpx;
	padding: 0 14rpx;
	border: 1rpx solid #d9e7e3;
	border-radius: 10rpx;
	background: #f2f8f6;
	font-size: 22rpx;
	color: #4e746c;
}

.space-topic-row {
	display: flex;
	flex-wrap: wrap;
	gap: 10rpx;
	margin: 8rpx 30rpx 14rpx;
}

.space-topic-tag {
	display: inline-flex;
	align-items: center;
	min-height: 42rpx;
	padding: 0 14rpx;
	border: 1rpx solid #d9e7e3;
	border-radius: 10rpx;
	background: #f2f8f6;
	font-size: 22rpx;
	color: #287d69;
}

.space-feed .cu-card.dynamic > .cu-item > .text-content {
	position: relative;
	margin-bottom: 22rpx;
	padding: 0 30rpx;
	font-size: 29rpx;
	font-weight: 400;
	line-height: 1.75;
	color: #354a59;
	overflow: hidden;
	cursor: pointer;
	word-break: break-word;
}

.space-feed .cu-card.dynamic > .cu-item > .space-text-preview-long {
	max-height: 9em;
	margin-bottom: 10rpx;
	overflow: hidden;
}

.space-read-more {
	display: inline-flex;
	align-items: center;
	margin: 0 30rpx 18rpx;
	padding: 8rpx 16rpx;
	border-radius: 999rpx;
	background: #edf7f4;
	font-size: 23rpx;
	font-weight: 600;
	line-height: 1.3;
	color: #168a7e;
	cursor: pointer;
}

.space-read-more:active {
	background: #dff0ec;
	color: #0f7268;
}

.space-feed .text-center.grid {
	min-height: 86rpx;
	margin: 26rpx 30rpx 0;
	padding: 0 !important;
	border-top: 1rpx solid #edf1f3;
	color: #7b8994;
}

.space-feed .square-post-btn {
	display: flex;
	align-items: center;
	justify-content: center;
	padding: 0;
	font-size: 24rpx;
	transition: color 180ms ease, transform 180ms ease;
}

.space-feed .square-post-btn:active {
	transform: scale(0.94);
	color: #169c92;
}

.space-feed .square-post-btn text {
	font-size: 31rpx;
}

@keyframes spaceCardIn {
	from { opacity: 0; transform: translate3d(0, 8rpx, 0); }
	to { opacity: 1; transform: translate3d(0, 0, 0); }
}

.space-feed-compact {
	padding-right: 4rpx;
	padding-left: 4rpx;
}

.space-feed-compact .square-list {
	display: grid !important;
	grid-template-columns: repeat(2, minmax(0, 1fr));
	gap: 14rpx;
}

.space-feed-compact .square-list > .cu-item2 {
	min-width: 0;
	margin: 0 !important;
	padding-top: 8rpx;
	border-radius: 12rpx !important;
}

.space-feed-compact .square-list .cu-list.menu-avatar > .cu-item2 {
	min-height: 78rpx;
	padding-right: 14rpx;
}

.space-feed-compact .square-list .cu-avatar.lg {
	width: 58rpx;
	height: 58rpx;
}

.space-feed-compact .space-author-content {
	padding-right: 16rpx !important;
}

.space-feed-compact .space-author-name {
	max-width: 92rpx;
	font-size: 24rpx;
}

.space-feed-compact .space-vip-badge,
.space-feed-compact .space-level-badge,
.space-feed-compact .space-more,
.space-feed-compact .square-list .text-gray.text-sm .margin-left-sm {
	display: none;
}

.space-feed-compact .square-list .text-gray.text-sm {
	margin-top: 3rpx;
	font-size: 20rpx;
	line-height: 1.3;
	white-space: nowrap;
	overflow: hidden;
	text-overflow: ellipsis;
}

.space-feed-compact .space-category {
	min-height: 36rpx;
	margin: 6rpx 16rpx 12rpx;
	padding: 0 10rpx;
	font-size: 19rpx;
}

.space-feed-compact .space-topic-row {
	margin: 6rpx 16rpx 12rpx;
}

.space-feed-compact .space-topic-tag {
	min-height: 36rpx;
	padding: 0 10rpx;
	font-size: 19rpx;
}

.space-feed-compact .cu-card.dynamic > .cu-item > .text-content {
	margin-bottom: 14rpx;
	padding: 0 16rpx;
	font-size: 24rpx;
	line-height: 1.5;
	overflow: visible;
}

.space-feed-compact .cu-card.dynamic > .cu-item > .space-text-preview-long {
	display: -webkit-box;
	max-height: 4.8em;
	-webkit-box-orient: vertical;
	-webkit-line-clamp: 2;
	overflow: hidden;
}

.space-feed-compact .space-read-more {
	margin: 0 16rpx 12rpx;
	padding: 5rpx 10rpx;
	font-size: 18rpx;
}

.space-feed-compact .text-center.grid {
	min-height: 64rpx;
	margin: 16rpx 16rpx 0;
}

.space-feed-compact .square-post-btn {
	font-size: 20rpx;
}

.space-feed-compact .square-post-btn text {
	font-size: 25rpx;
}

@media (max-width: 360px) {
	.space-feed-compact .square-list {
		gap: 10rpx;
	}
	.space-feed-compact .square-list .cu-list.menu-avatar > .cu-item2 {
		min-height: 70rpx;
		padding-right: 10rpx;
	}
	.space-feed-compact .square-list .cu-avatar.lg {
		width: 50rpx;
		height: 50rpx;
	}
	.space-feed-compact .space-author-name {
		max-width: 88rpx;
		font-size: 22rpx;
	}
	.space-feed-compact .space-campus-badge {
		max-width: 120rpx;
		font-size: 18rpx;
	}
	.space-feed-compact .cu-card.dynamic > .cu-item > .text-content {
		padding-right: 14rpx;
		padding-left: 14rpx;
		font-size: 23rpx;
	}
	.space-feed-compact .space-category,
	.space-feed-compact .space-image-grid {
		margin-right: 14rpx;
		margin-left: 14rpx;
	}
	.space-feed-compact .space-image-grid {
		padding-right: 0;
		padding-left: 0;
	}
}

.paceVideo2 {
  width: 100%;
  display: flex;
  justify-content: center;
  position: relative;
  z-index: 1; /* 将 z-index 设置为 1 */
}

.spaceVideo-play::before {
  content: "";
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.5);
  border-radius: 20upx;
  z-index: 0; /* 将 z-index 设置为 0 */
}

.cuIcon-playfill {
  position: relative;
  z-index: 2; /* 将 z-index 设置为 2 */
}

.spaceVideo-play {
	width: 100%;
	aspect-ratio: 1.618 / 1;
	border-radius: 20upx;
	position: relative;
	z-index: 2; /* 将 z-index 设置为 2 */
}

.space-feed .spaceVideo video {
	width: 100%;
	height: auto;
	aspect-ratio: 1.618 / 1;
	border-radius: 20rpx;
}
  .videoPlay-close {
    z-index: 99;
    color: white;
    position: absolute;
    top: 80upx;
    right: 50upx;
    font-size: 25px;
  }

.campus-night .space-feed .square-list > .cu-item2 {
	border-color: #34403f;
	background: #202728;
}

.campus-night .space-feed .square-list > .cu-item2:active {
	background: #263031;
}

.campus-night .space-feed .square-list .cu-list.menu-avatar,
.campus-night .space-feed .square-list .cu-list.menu-avatar > .cu-item2 {
	background: transparent !important;
}

.campus-night .space-feed .square-list .content.flex-sub > view:first-child,
.campus-night .space-feed .cu-card.dynamic > .cu-item > .text-content {
	color: #edf3f0;
}

.campus-night .space-read-more {
	background: rgba(69, 170, 124, 0.13);
	color: #7fcead;
}

.campus-night .space-read-more:active {
	background: rgba(69, 170, 124, 0.2);
	color: #9be1c1;
}

.campus-night .space-feed .square-list .text-gray.text-sm,
.campus-night .space-feed .square-post-btn,
.campus-night .space-feed .space-more {
	color: #b4c0bc !important;
}

.campus-night .space-category {
	border-color: #456158;
	background: #263633;
	color: #b9d5cc;
}

.campus-night .space-topic-tag {
	border-color: #456158;
	background: #263633;
	color: #b9d5cc;
}

.campus-night .space-feed .text-center.grid {
	border-top-color: #34403f;
}

.campus-night .space-feed .square-list .cu-avatar.lg {
	border-color: #3a4745;
}

@media (prefers-reduced-motion: reduce) {
	.space-feed .square-list > .cu-item2.is-entering { animation: none; }
}

@media (max-width: 360px) {
	.space-feed { padding-right: 12rpx; padding-left: 12rpx; }
	.space-feed .square-list .cu-list.menu-avatar > .cu-item2 { padding-right: 24rpx; }
	.space-feed .cu-card.dynamic > .cu-item > .text-content,
	.space-feed .space-image-grid { padding-right: 24rpx; padding-left: 24rpx; }
	.space-read-more { margin-right: 24rpx; margin-left: 24rpx; }
	.space-feed .text-center.grid { margin-right: 24rpx; margin-left: 24rpx; }
	.space-category { margin-right: 24rpx; margin-left: 24rpx; }
	.space-topic-row { margin-right: 24rpx; margin-left: 24rpx; }
	.space-author-name { max-width: 40vw; }
}
</style>
