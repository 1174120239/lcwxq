<template>
	<view class="user campus-subpage campus-detail-page space-detail-page" :class="{'campus-night': campusNight}">
		<view class="header" :style="[{height:CustomBar + 'px'}]">
			<view class="cu-bar bg-white" :style="{'height': CustomBar + 'px','padding-top':StatusBar + 'px'}">
				<view class="action" @tap="back">
					<text class="cuIcon-back"></text>
				</view>
				<view class="content text-bold" :style="[{top:StatusBar + 'px'}]">
					<block v-if="replyType==0">
						动态详情
					</block>
					<block v-else>
						评论详情
					</block>
				</view>
				<!--  #ifdef H5 || APP-PLUS -->
				<view class="action" @tap="toSearch">
					<text class="cuIcon-search"></text>
				</view>
				<!--  #endif -->
			</view>
		</view>
		<view :style="[{padding:NavBar + 'px 10px 0px 10px'}]"></view>
		<view class="cu-card dynamic no-card space-info">
			<view class="cu-item">
				<view class="cu-list menu-avatar" v-if="spaceInfo.userJson">
					<view class="cu-item">
						<view class="cu-avatar round lg"  :style="'background-image:url('+spaceInfo.userJson.avatar+');'">
						
						</view>
						<view class="content flex-sub">
							<view>{{spaceInfo.userJson.name}}
							<text class="space-detail-campus" v-if="spaceInfo.userJson.campus">{{spaceInfo.userJson.campus}}</text>
							<text class="userlv" v-if="spaceInfo.userJson.isvip>0" style="background: linear-gradient(to bottom right, #f2ad5c, #e6216d,#901ccb);color:white;padding: 2px 5px;border-radius: 10px;">VIP</text>
							<text class="userlv" :style="getLvStyle(spaceInfo.userJson.experience)">{{getLv(spaceInfo.userJson.experience)}}</text>
							
							<text class="userlv customize" v-if="spaceInfo.userJson.customize&&spaceInfo.userJson.customize!=''">{{spaceInfo.userJson.customize}}</text>
							</view>
							<view class="text-gray text-sm">
								{{formatDate(spaceInfo.created)}}
								<text class="margin-left-sm">{{formatNumber(spaceInfo.views || 0)}} 次浏览</text>
								<text class="margin-left-sm" v-if="spaceInfo.created!=spaceInfo.modified">已编辑</text>
							</view>
							
						</view>
						
					</view>
				</view>
				<view class="text-content break-all space-detail-text">
					<rich-text :nodes="markHtml(spaceInfo.text)"></rich-text>
				</view>
				
				<block  v-if="spaceInfo.type==0">
					
					<view class="grid flex-sub padding-lr col-3 grid-square space-detail-media" :class="{'is-single': spaceInfo.picList.length === 1, 'is-double': spaceInfo.picList.length === 2}" v-if="spaceInfo.picList.length>0">
						<view class="bg-img" v-for="(data,i) in spaceInfo.picList" :key="data+i" @tap="previewImage(spaceInfo.picList,data)">
							<image :src="imageSource(data)" mode="aspectFill" @error="imageLoadFailed(data)"></image>
							<view class="image-load-error" v-if="imageFailures[data]" @tap.stop="retryImage(data)">
								<text class="cuIcon-refresh"></text><text>加载失败，点击重试</text>
							</view>
						</view>
					</view>
				</block>
				<view class="forward-original" v-if="spaceInfo.type==2">
					<block v-if="spaceInfo.forwardJson && spaceInfo.forwardJson.id">
						<view class="forward-author" @tap="toInfo(spaceInfo.forwardJson.id)">@{{spaceInfo.forwardJson.username}}</view>
						<view class="forward-text" @tap="toInfo(spaceInfo.forwardJson.id)">
							<rich-text :nodes="markHtml(spaceInfo.forwardJson.text || '')"></rich-text>
						</view>
						<view class="space-detail-media forward-media" :class="{'is-single': spaceInfo.forwardJson.picList.length === 1, 'is-double': spaceInfo.forwardJson.picList.length === 2}" v-if="spaceInfo.forwardJson.picList && spaceInfo.forwardJson.picList.length">
							<view class="bg-img" v-for="(data,i) in spaceInfo.forwardJson.picList" :key="'forward-'+data+i" @tap="previewImage(spaceInfo.forwardJson.picList,data)">
								<image :src="imageSource(data)" mode="aspectFill" @error="imageLoadFailed(data)"></image>
								<view class="image-load-error" v-if="imageFailures[data]" @tap.stop="retryImage(data)"><text class="cuIcon-refresh"></text><text>加载失败，点击重试</text></view>
							</view>
						</view>
					</block>
					<view class="forward-deleted" v-else>原动态已删除</view>
				</view>
				<block  v-if="spaceInfo.type==4">
					<!--  #ifdef H5 || MP-->
					<view class="paceVideo2">
					<video :src="spaceInfo.pic" @play="play(spaceInfo.pic)" ></video>
					</view>
					<!--  #endif -->
					<!--  #ifdef APP-PLUS -->
					<view class="paceVideo2">
					<view class="spaceVideo-play" :style="{ backgroundImage: 'url(' + curIMG + ')', backgroundSize: 'cover', backgroundRepeat: 'no-repeat', backgroundPosition: 'center center' }" @tap="goPlay(spaceInfo.pic,spaceInfo.text,spaceInfo.userJson.name)">
						<text class="cuIcon-playfill"></text>
						</view>
					</view>
					<!--  #endif -->
					
				</block>

			</view>
		</view>
		<view class="space-reply">
			<view class="space-reply-head">
				
				<text  @tap="setInfoType(0)" :class="infoType==0?'cur':''">评论 <block v-if="spaceInfo.reply>0"> {{formatNumber(spaceInfo.reply)}}</block></text>
				
			</view>
			<block v-if="infoType==0">
				<view class="space-reply-list">
					<view class="cu-list menu-avatar comment" v-for="(item,index) in replyList" :key="index">
						<view class="cu-item">
							<view class="cu-avatar round" :style="'background-image:url('+item.userJson.avatar+');'" @tap="toUserContents(item.userJson)"></view>
							<view class="content">
								<view class="text-grey">
									{{item.userJson.name}}
									
									<text class="userlv" v-if="item.userJson.isvip>0" style="background: linear-gradient(to bottom right, #f2ad5c, #e6216d,#901ccb);color:white;padding: 2px 5px;border-radius: 10px;">VIP</text>
									<text class="userlv" :style="getLvStyle(item.userJson.experience)">{{getLv(item.userJson.experience)}}</text>
									
									<text class="userlv customize" v-if="item.userJson.customize&&item.userJson.customize!=''">{{item.userJson.customize}}</text>
									</view>
								<view class="text-content text-df break-all">
									<rich-text :nodes="markHtml(item.text)"></rich-text>
								</view>
								<view class="space-reply-num padding-xs radius margin-top-sm  text-sm" v-if="item.reply>0" @tap="toReplyInfo(item.id)">
									<text class="text-blue">共{{item.reply}}条回复<text class="cuIcon-right margin-left-xs"></text></text>
								</view>
								<view class="margin-top-sm flex justify-between">
									<view class="text-gray text-df">{{formatDate(item.created)}}</view>
									<view>
										<text class="cuIcon-message text-gray margin-left-xl" @tap="goReply(item.id)">
											{{formatNumber(item.reply) || ''}}
										</text>
										<text class="cuIcon-appreciate  margin-left-xl" @tap="toListLike(item.id,'reply',index)" :class="item.isLikes==1?'text-blue':'text-gray'">
											{{formatNumber(item.likes) || ''}}
										</text>
									</view>
								</view>
								<view class="comment-operation">
									<block v-if="item.userJson.uid!=0&&item.userJson.uid==uid">
										<text class="text-red margin-left-sm" @tap="toDelete2(item.id)">删除</text>
									</block>
									<block v-else>
										<text v-if="group=='administrator'||group=='editor'" class="text-blue margin-left-sm" @tap="edit(item.id)">编辑</text>
										<text v-if="group=='administrator'" class="text-red margin-left-sm" @tap="toDelete(item.id)">删除</text>
									</block>
								</view>
							</view>
						</view>
					</view>
					<view class="no-data" v-if="replyList.length==0">
						
						<text class="cuIcon-text"></text>
						
						暂时没有消息
						<view class="text-center margin-top-sm">
							<text class="cu-btn bg-blue" @tap="goReply(spaceInfo.id)">发布评论</text>
						</view>
						
					</view>
					<view class="load-more" @tap="loadMore" v-if="replyList.length>0">
						<text>{{moreText}}</text>
					</view>
					
				</view>
			</block>
			<block v-if="infoType==1">
				<view class="space-reply-list">
					<view class="cu-list menu-avatar comment" v-for="(item,index) in forwardList" :key="index">
						<view class="cu-item">
							<view class="cu-avatar round" :style="'background-image:url('+item.userJson.avatar+');'" @tap="toUserContents(item.userJson)"></view>
							<view class="content">
								<view class="text-grey">
									{{item.userJson.name}}
									
									<text class="userlv" v-if="item.userJson.isvip>0" style="background: linear-gradient(to bottom right, #f2ad5c, #e6216d,#901ccb);color:white;padding: 2px 5px;border-radius: 10px;">VIP</text>
									<text class="userlv" :style="getLvStyle(item.userJson.experience)">{{getLv(item.userJson.experience)}}</text>
									
									<text class="userlv customize" v-if="item.userJson.customize&&item.userJson.customize!=''">{{item.userJson.customize}}</text>
									</view>
								<view class="text-content text-df break-all">
									<rich-text :nodes="markHtml(item.text)"></rich-text>
								</view>
								<view class="space-reply-num padding-xs radius margin-top-sm  text-sm" v-if="item.reply>0" @tap="toReplyInfo(item.id)">
									<text class="text-blue">共{{item.reply}}条回复<text class="cuIcon-right margin-left-xs"></text></text>
								</view>
								<view class="margin-top-sm flex justify-between">
									<view class="text-gray text-df">{{formatDate(item.created)}}</view>
									<view>
										<text class="cuIcon-message text-gray margin-left-xl" @tap="goReply(item.id)">
											{{formatNumber(item.reply) || ''}}
										</text>
										<text class="cuIcon-appreciate  margin-left-xl" @tap="toListLike(item.id,'reply',index)" :class="item.isLikes==1?'text-blue':'text-gray'">
											{{formatNumber(item.likes) || ''}}
										</text>
									</view>
								</view>
								<view class="comment-operation">
									<block v-if="item.userJson.uid!=0&&item.userJson.uid==uid">
										<text class="text-red margin-left-sm" @tap="toDelete2(item.id)">删除</text>
									</block>
									<block v-else>
										<text v-if="group=='administrator'||group=='editor'" class="text-blue margin-left-sm" @tap="edit(item.id)">编辑</text>
										<text v-if="group=='administrator'" class="text-red margin-left-sm" @tap="toDelete(item.id)">删除</text>
									</block>
								</view>
							</view>
						</view>
					</view>
					<view class="no-data" v-if="forwardList.length==0">
						<text class="cuIcon-text"></text>
						
						暂时还没有人转发

						
					</view>
					<view class="load-more" @tap="loadMore" v-if="forwardList.length>0">
						<text>{{moreText}}</text>
					</view>
					
				</view>
			</block>
			
		</view>
		<view class="space-footer grid " :class="replyType==0?'col-2':'col-2'" v-if="spaceInfo.status==1">
			
			<view class="space-footer-box" @tap="goReply(spaceInfo.id)">
				<text class="cuIcon-message"></text>
				评论
			</view>
			<view class="space-footer-box" @tap="toLike(spaceInfo.id)">
				<text class="cuIcon-appreciate"  :class="spaceInfo.isLikes==1?'text-blue':''"></text>
				点赞
			</view>
		</view>
		<view class="videoPlay" v-if="isPlay">
			<view class="videoPlay-bg" @tap="isPlay=false">
				
				<view class="videoPlay-close" @tap="isPlay=true">
					<i class="cuIcon-close"></i>
				</view>
			</view>
			<video :src="curVideo" http-cache="true" play-strategy="1" loop autoplay :title="mp4title"></video>
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
	import { applyCampusThemeShell, getCampusThemeMode, isDongchangfuNight, resolveCampusNight } from '@/utils/campusTheme.js'
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
				campusThemeMode: 'auto',
				campusThemeClock: Date.now(),
				campusThemeTimer: null,
				avatar:'',
				replyType:0,
				id:0,
				token:'',
				vipDiscount:0,
				isPlay:false,
				curVideo:"",
				curIMG:"",
				spaceInfo:{
					created: 0,
					forward: 0,
					id: 3,
					likes: 0,
					reply: 0,
					text: "",
					toid: 0,
					type: 0,
					uid: 1,
					modified:0,
					picList:[]
				},
				replyList:[],
				forwardList:[],
				isLoad:0,
				isLoading:0,
				page:1,
				pageSize:10,
				moreText:"加载更多",
				dataLoad:false,
				
				currencyName:"",
				mp4bt:"",
				mp4name:"",
				mp4title:"视频动态",
				
				owo:owo,
				owoList:[],
				
				infoType:0,
				group:"",
				uid:0,
				imageFailures:{},
				imageRetryVersions:{},
			}
		},
		computed: {
			campusNight() {
				return resolveCampusNight(this.campusThemeMode, isDongchangfuNight(this.campusThemeClock))
			}
		},
		onPullDownRefresh(){
			var that = this;
			if(that.id!=0){
				that.getSpaceInfo();
				that.getReplyList(false)
			}
			var timer = setTimeout(function() {
				
				uni.stopPullDownRefresh();
			}, 1000)
		},
		onReachBottom() {
		    //触底后执行的方法，比如无限加载之类的
			var that = this;
			that.loadMore();
		},
		onHide() {
			localStorage.removeItem('getuid')
			this.stopCampusThemeClock()
		},
		onUnload() {
			this.stopCampusThemeClock()
		},
		onShow(){
			var that = this;
			that.loadCampusThemeMode()
			that.startCampusThemeClock()
			// #ifdef APP-PLUS
			that.isLoad=0;
			that.page=1;
			plus.navigator.setStatusBarStyle(that.campusNight ? "light" : "dark")
			// #endif
			if(localStorage.getItem('token')){
				
				that.token = localStorage.getItem('token');
			}
			if(localStorage.getItem('getuid')){
				that.toid = localStorage.getItem('getuid');
			}
			if(that.id!=0){
				that.getSpaceInfo();
				that.getReplyList(false)
			}
			
		},
		onLoad(res) {
			var that = this;
			if(localStorage.getItem('userinfo')){
							
				var userInfo = JSON.parse(localStorage.getItem('userinfo'));
				that.group = userInfo.group;
				that.uid = userInfo.uid;
			}
			that.currencyName = that.$API.getCurrencyName();
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
			if(res.replyType){
				that.replyType = res.replyType;
			}
			if(res.id){
				that.id = res.id;
				that.getSpaceInfo();
				that.getReplyList(false)
			}
		},
		mounted(){
			var that = this;
			that.getvideoimg()
		},
		methods: {
			loadCampusThemeMode() {
				this.campusThemeMode = getCampusThemeMode()
				applyCampusThemeShell(this.campusThemeMode, this.campusThemeClock)
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
			loadMore(){
				var that = this;
				that.moreText="正在加载中...";
				if(that.isLoad==0){
					that.getReplyList(true);
					if(that.infoType==0){
						that.getReplyList(false)
					}
					if(that.infoType==1){
						that.getForwardList(false);
					}
				}
			},
			back(){
				uni.navigateBack({
					delta: 1
				});
			},
			getSpaceInfo(){
				var that = this;
				var token = "";
				if(localStorage.getItem('token')){
					
					token = localStorage.getItem('token');
				}
				var data = {
					"id":that.id,
					"token":token
				}
				that.$Net.request({
					url: that.$API.spaceInfo(),
					data:data,
					header:{
						'Content-Type':'application/x-www-form-urlencoded'
					},
					method: "get",
					dataType: 'json',
					success: function(res) {
						that.isLoading=1;
						if(res.data.code==1){
							that.spaceInfo = res.data.data;
							if(res.data.data.pic&&res.data.data.pic!=""){
								that.spaceInfo.picList = res.data.data.pic.split("||").filter(Boolean);
							}else{
								that.spaceInfo.picList = [];
							}
							if(res.data.data.forwardJson){
								if(res.data.data.forwardJson.pic&&res.data.data.forwardJson.pic!=""){
									that.spaceInfo.forwardJson.picList = res.data.data.forwardJson.pic.split("||").filter(Boolean);
								}else{
									that.spaceInfo.forwardJson.picList = [];
								}
							}
						}
					},
					fail: function(res) {
						that.isLoading=1;
					}
				});
				
			},
			previewImage(imageList,image) {
				uni.previewImage({
					urls: imageList.filter(Boolean),
					current: image,
					indicator: 'number',
					loop: true
				});
			},
			imageSource(url) {
				const version = this.imageRetryVersions[url] || 0
				if (!version) return url
				return url + (url.indexOf('?') === -1 ? '?' : '&') + 'retry=' + version
			},
			imageLoadFailed(url) {
				this.$set(this.imageFailures, url, true)
			},
			retryImage(url) {
				this.$set(this.imageFailures, url, false)
				this.$set(this.imageRetryVersions, url, Date.now())
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
			toInfo(id){
				var that = this;
				
				uni.navigateTo({
				    url: '/pages/space/info?id='+id
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
			formatNumber(num) {
			    return num >= 1e3 && num < 1e4 ? (num / 1e3).toFixed(1) + 'k' : num >= 1e4 ? (num / 1e4).toFixed(1) + 'w' : num
			},
			getUserLv(i){
				var that = this;
				if(!i){
					var i = 0;
				}
				var rankList = that.$API.GetRankList();
				return rankList[i];
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
				text =that.replaceAll(text,"||rn||","<br>");
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
			goReply(id){
				uni.navigateTo({
				    url: '/pages/space/reply?id='+id
				});
			},
			forward(id){
				var that = this;
				uni.navigateTo({
				    url: '/pages/space/post?type=2&id='+id
				});
			},
			getReplyList(isPage){
				var that = this;
				var page = that.page;
				var token = "";
				if(localStorage.getItem('userinfo')){
					var userInfo = JSON.parse(localStorage.getItem('userinfo'));
					token=userInfo.token;
				}
				var data = {
					"toid":that.id,
					"type":3
				}
				if(isPage){
					page++;
				}
				that.$Net.request({
					url: that.$API.spaceList(),
					data:{
						"searchParams":JSON.stringify(that.$API.removeObjectEmptyKey(data)),
						"limit":that.pageSize,
						"page":page,
						"order":"created",
						"token":token
					},
					method: "get",
					dataType: 'json',
					success: function(res) {
						that.isLoad=0;
						that.moreText="加载更多";
						if(!isPage){
							that.dataLoad = true;
						}
						if(res.data.code==1){
							var list = res.data.data;
							var replyList = [];
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
							replyList = list;
							if(list.length>0){
								if(isPage){
									that.page++;
									that.replyList = that.replyList.concat(replyList);
								}else{
									that.replyList = replyList;
								}
								
							}else{
								that.moreText="没有更多数据了";
							}
						}
					},
					fail: function(res) {
						
						that.moreText="加载更多";
						that.isLoad=0;
					}
				})
			},
			getForwardList(isPage){
				var that = this;
				var page = that.page;
				var token = "";
				if(localStorage.getItem('userinfo')){
					var userInfo = JSON.parse(localStorage.getItem('userinfo'));
					token=userInfo.token;
				}
				var data = {
					"toid":that.id,
					"type":2
				}
				if(isPage){
					page++;
				}
				that.$Net.request({
					url: that.$API.spaceList(),
					data:{
						"searchParams":JSON.stringify(that.$API.removeObjectEmptyKey(data)),
						"limit":that.pageSize,
						"page":page,
						"order":"created",
						"token":token
					},
					method: "get",
					dataType: 'json',
					success: function(res) {
						that.isLoad=0;
						that.moreText="加载更多";
						if(!isPage){
							that.dataLoad = true;
						}
						if(res.data.code==1){
							var list = res.data.data;
							var forwardList = [];
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
							forwardList = list;
							if(list.length>0){
								if(isPage){
									that.page++;
									that.forwardList = that.forwardList.concat(forwardList);
								}else{
									that.forwardList = forwardList;
								}
								
							}else{
								that.moreText="没有更多数据了";
							}
						}
					},
					fail: function(res) {
						
						that.moreText="加载更多";
						that.isLoad=0;
					}
				})
			},
			follow(type,uid){
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
				that.spaceInfo.isFollow = type;
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
						uni.showToast({
							title: res.data.msg,
							icon: 'none'
						})
						if(res.data.code==0){
							that.spaceInfo.isFollow = 0;
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
			},
			toLike(id){
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
				if(that.spaceInfo.isLikes==1){
					uni.showToast({
						title: "你已经点赞过了",
						icon: 'none'
					});
					return false;
				}else{
					that.spaceInfo.isLikes = 1;
				}
				
				that.spaceInfo.likes += 1;
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
							that.spaceInfo.isLikes = 0;
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
			},
			toListLike(id,type,index){
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
				if(type=="forward"){
					if(that.forwardList[index].isLikes==1){
						uni.showToast({
							title: "你已经点赞过了",
							icon: 'none'
						});
						return false;
					}else{
						that.forwardList[index].isLikes = 1;
					}
					that.forwardList[index].likes += 1;
				}
				if(type=="reply"){
					if(that.replyList[index].isLikes==1){
						uni.showToast({
							title: "你已经点赞过了",
							icon: 'none'
						});
						return false;
					}else{
						that.replyList[index].isLikes = 1;
					}
					that.replyList[index].likes += 1;
				}
				
				
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
							if(type=="forward"){
								that.forwardList[index].isLikes = 0;
							}
							if(type=="reply"){
								that.replyList[index].isLikes = 1;
							}
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
			},
			setInfoType(type){
				var that = this;
				that.page = 1;
				if(type==0){
					that.getReplyList(false)
				}
				if(type==1){
					that.getForwardList(false);
				}
				that.infoType = type;
			},
			toSearch(){
				var that = this;
				uni.navigateTo({
				    url: '/pages/contents/search'
				});
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
			toUserContents(data){
				var that = this;
				var name = data.name;
				var title = data.name+"的信息";
				var id= data.uid;
				if(id==0){
					uni.showToast({
						title: "用户不存在或已注销",
						icon: 'none'
					})
					return false
				}
				var type="user";
				uni.navigateTo({
				    url: '/pages/contents/userinfo?title='+title+"&name="+name+"&uid="+id+"&avatar="+encodeURIComponent(data.avatar)
				});
			},
			toReplyInfo(id){
				var that = this;
				
				uni.navigateTo({
				    url: '/pages/space/info?id='+id+'&replyType=1'
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
			edit(id){
				var that = this;
				uni.navigateTo({
				    url: '/pages/space/post?postType=edit&id='+id
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
  margin-left: 15px;
  margin-right: 15px;
  border-radius: 20upx;
  position: relative;
  z-index: 2; /* 将 z-index 设置为 2 */
}
  .videoPlay-close {
    z-index: 99;
    color: white;
    position: absolute;
    top: 80upx;
    right: 50upx;
    font-size: 25px;
  }
.space-detail-page {
	min-height: 100vh;
	min-height: 100dvh;
	padding-bottom: calc(132rpx + env(safe-area-inset-bottom));
	background: #f5f7f7;
	color: #263a37;
}

.space-detail-page .space-info {
	margin: 0 16rpx;
	border: 1rpx solid #e0e8e6;
	border-radius: 16rpx;
	background: #ffffff;
	box-shadow: none;
	overflow: hidden;
}

.space-detail-page .space-info > .cu-item,
.space-detail-page .space-info .cu-list.menu-avatar,
.space-detail-page .space-info .cu-list.menu-avatar > .cu-item {
	background: transparent;
}

.space-detail-page .space-info .cu-list.menu-avatar > .cu-item {
	min-height: 112rpx;
}

.space-detail-page .space-info .text-content {
	margin: 4rpx 0 20rpx;
	padding: 0 28rpx;
	font-size: 30rpx;
	line-height: 1.7;
	color: #2f4642;
}

.space-detail-campus {
	margin-left: 10rpx;
	font-size: 21rpx;
	font-weight: 500;
	color: #819190;
}

.space-detail-page .space-info .space-detail-text,
.space-detail-page .space-info .space-detail-text rich-text {
	display: block;
	height: auto;
	max-height: none;
	overflow: visible;
	white-space: normal;
	-webkit-line-clamp: unset;
}

.space-detail-media {
	display: flex !important;
	flex-wrap: wrap;
	gap: 12rpx;
	margin: 0;
	padding: 0 28rpx 26rpx !important;
}

.space-detail-media > .bg-img {
	position: relative;
	flex: 0 0 calc(33.333% - 8rpx);
	width: auto !important;
	min-width: 0;
	padding-bottom: 30%;
	border-radius: 12rpx;
	overflow: hidden;
}

.space-detail-media > .bg-img > image {
	position: absolute;
	inset: 0;
	width: 100%;
	height: 100%;
}

.space-detail-media.is-single > .bg-img {
	flex: 0 0 100%;
	max-width: 100%;
	padding-bottom: 61.8%;
}

.space-detail-media.is-double > .bg-img {
	flex: 0 0 calc(50% - 6rpx);
	padding-bottom: 46%;
}

.image-load-error {
	position: absolute;
	inset: 0;
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	gap: 8rpx;
	padding: 12rpx;
	background: #e9eeec;
	color: #68736f;
	font-size: 22rpx;
	text-align: center;
}

.forward-original { margin: 0 28rpx 26rpx; padding: 20rpx; background: #f0f4f3; border-radius: 8rpx; }
.forward-author { color: #168c80; font-weight: 600; }
.forward-text { margin-top: 10rpx; line-height: 1.65; color: #40514d; }
.forward-media { padding: 18rpx 0 0 !important; }
.forward-deleted { color: #7c8783; }
.campus-night .forward-original { background: #252c2d; }
.campus-night .forward-text { color: #d7dddb; }
.campus-night .image-load-error { background: #303738; color: #bec7c4; }

.space-detail-page .space-reply {
	margin-top: 22rpx;
	padding-bottom: 20rpx;
	background: #ffffff;
}

.space-detail-page .space-reply-head {
	padding: 0 28rpx;
	border-bottom: 1rpx solid #e6ecea;
}

.space-detail-page .space-reply-head text {
	display: inline-flex;
	min-height: 86rpx;
	align-items: center;
	border-bottom: 3rpx solid transparent;
	font-size: 31rpx;
	font-weight: 600;
	color: #405752;
}

.space-detail-page .space-reply-head text.cur {
	border-bottom-color: #168c80;
	color: #168c80;
}

.space-detail-page .space-reply-list .cu-list.menu-avatar.comment,
.space-detail-page .no-data {
	background: transparent;
}

.space-detail-page .space-footer {
	height: 96rpx;
	padding-bottom: env(safe-area-inset-bottom);
	border-top: 1rpx solid #dfe7e5;
	background: rgba(255, 255, 255, 0.98);
	box-shadow: none;
}

.space-detail-page .space-footer-box {
	color: #526863;
}

.space-detail-page.campus-night {
	background: #171d1e;
	color: #edf3f0;
}

.space-detail-page.campus-night .space-info,
.space-detail-page.campus-night .space-reply {
	border-color: #34403f;
	background: #202728;
	box-shadow: none;
}

.space-detail-page.campus-night .space-info > .cu-item,
.space-detail-page.campus-night .space-info .cu-list.menu-avatar,
.space-detail-page.campus-night .space-info .cu-list.menu-avatar > .cu-item,
.space-detail-page.campus-night .space-reply-list .cu-list.menu-avatar.comment,
.space-detail-page.campus-night .no-data {
	background: transparent !important;
}

.space-detail-page.campus-night .space-info .content,
.space-detail-page.campus-night .space-info .text-content,
.space-detail-page.campus-night .space-reply .content,
.space-detail-page.campus-night .space-reply .text-content {
	color: #edf3f0 !important;
}

.space-detail-page.campus-night .text-gray,
.space-detail-page.campus-night .text-grey,
.space-detail-page.campus-night .space-reply-num,
.space-detail-page.campus-night .space-footer-box {
	color: #b6c2be !important;
}

.space-detail-page.campus-night .space-reply-head {
	border-bottom-color: #34403f;
}

.space-detail-page.campus-night .space-reply-head text {
	color: #c9d6d1;
}

.space-detail-page.campus-night .space-reply-head text.cur {
	border-bottom-color: #61b9a8;
	color: #a9dfd1;
}

.space-detail-page.campus-night .space-footer {
	border-top-color: #34403f;
	background: #1c2324;
}

.space-detail-page.campus-night .space-detail-media > .bg-img {
	box-shadow: inset 0 0 0 1rpx #34403f;
}

.space-detail-page.campus-night .header .cu-bar {
	border-bottom: 1rpx solid rgba(218, 231, 226, 0.08) !important;
	background: #171d1e !important;
	box-shadow: none !important;
}

.space-detail-page.campus-night .header .content,
.space-detail-page.campus-night .header .action,
.space-detail-page.campus-night .header .cuIcon-back,
.space-detail-page.campus-night .header .cuIcon-search {
	color: #edf3f0 !important;
}

.space-detail-page.campus-night .space-info {
	border-color: rgba(218, 231, 226, 0.1);
	background: #202728 !important;
}

.space-detail-page.campus-night .space-info .cu-list.menu-avatar > .cu-item {
	border-bottom: 1rpx solid rgba(218, 231, 226, 0.07);
}

.space-detail-page.campus-night .space-info .cu-avatar,
.space-detail-page.campus-night .space-reply-list .cu-avatar {
	border: 2rpx solid #293233;
	box-shadow: none;
}

.space-detail-page.campus-night .space-detail-media > .bg-img,
.space-detail-page.campus-night .paceVideo2 video,
.space-detail-page.campus-night .spaceVideo-play {
	border: 1rpx solid rgba(218, 231, 226, 0.12);
	background-color: #14191a;
}

.space-detail-page.campus-night .space-reply {
	background: #1a2021 !important;
}

.space-detail-page.campus-night .space-reply-list .cu-list.menu-avatar.comment {
	margin: 0 !important;
	border: 0 !important;
	background: transparent !important;
}

.space-detail-page.campus-night .space-reply-list .cu-list.menu-avatar.comment > .cu-item {
	margin: 0 !important;
	padding-top: 26rpx;
	padding-bottom: 24rpx;
	border: 1rpx solid rgba(218, 231, 226, 0.1) !important;
	border-radius: 16rpx;
	background: #202728 !important;
	box-shadow: none !important;
}

.space-detail-page.campus-night .space-reply-list .cu-list.menu-avatar.comment > .cu-item::after {
	display: none !important;
}

.space-detail-page.campus-night .space-reply-list .cu-list.menu-avatar.comment + .cu-list.menu-avatar.comment {
	margin-top: 14rpx !important;
}

.space-detail-page.campus-night .space-reply-list {
	padding: 0 0 16rpx;
	background: transparent;
}

.space-detail-page.campus-night .space-reply-num {
	display: inline-flex;
	max-width: 100%;
	border: 1rpx solid rgba(111, 205, 191, 0.18) !important;
	border-radius: 10rpx !important;
	background: #263331 !important;
	color: #a9dfd1 !important;
}

.space-detail-page.campus-night .space-reply-num .text-blue,
.space-detail-page.campus-night .text-blue {
	color: #9dd9cd !important;
}

.space-detail-page.campus-night .load-more {
	min-height: 88rpx;
	margin: 0 !important;
	border-top: 1rpx solid rgba(218, 231, 226, 0.08) !important;
	border-bottom: 1rpx solid rgba(218, 231, 226, 0.08) !important;
	background: #1a2021 !important;
	color: #98a7a2 !important;
	line-height: 88rpx;
}

.space-detail-page.campus-night .no-data {
	padding: 58rpx 0;
	color: #98a7a2 !important;
}

.space-detail-page.campus-night .no-data .cuIcon-text {
	color: #52615d !important;
}

.space-detail-page.campus-night .no-data .cu-btn {
	background: #263331 !important;
	color: #a9dfd1 !important;
}

.space-detail-page.campus-night .comment-operation .text-red {
	color: #ff7676 !important;
}

.space-detail-page.campus-night .space-footer {
	height: 96rpx;
	border-top: 1rpx solid rgba(218, 231, 226, 0.12) !important;
	background: #171d1e !important;
	box-shadow: 0 -8rpx 22rpx rgba(0, 0, 0, 0.18);
}

.space-detail-page.campus-night .space-footer-box {
	border-color: rgba(218, 231, 226, 0.16) !important;
	color: #d5dfdb !important;
}

.space-detail-page.campus-night .space-footer-box .cuIcon-message,
.space-detail-page.campus-night .space-footer-box .cuIcon-appreciate {
	color: #cbd8d3 !important;
}

.space-detail-page.campus-night .space-footer-box .text-blue {
	color: #9dd9cd !important;
}
</style>
