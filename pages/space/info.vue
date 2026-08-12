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
				<view class="action" v-if="spaceInfo.id" @tap="openSpaceActions">
					<text class="cuIcon-more"></text>
				</view>
				<view class="action" v-else></view>
			</view>
		</view>
		<view :style="[{padding:NavBar + 'px 10px 0px 10px'}]"></view>
		<view class="cu-card dynamic no-card space-info">
			<view class="cu-item">
				<view class="cu-list menu-avatar" v-if="spaceInfo.userJson">
					<view class="cu-item">
						<campus-avatar :key="'detail-avatar-' + spaceInfo.userJson.uid + '-' + spaceInfo.userJson.avatar" class="cu-avatar round lg" :src="spaceInfo.userJson.avatar" :name="spaceInfo.userJson.name" @tap.stop="toUserContents(spaceInfo.userJson)"></campus-avatar>
						<view class="content flex-sub">
							<view @tap.stop="toUserContents(spaceInfo.userJson)">{{spaceInfo.userJson.name}}
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
					
					<view class="space-image-grid space-detail-media" :class="{'is-single': spaceInfo.picList.length === 1, 'is-double': spaceInfo.picList.length === 2}" v-if="spaceInfo.picList.length>0">
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
						<view class="space-image-grid space-detail-media forward-media" :class="{'is-single': spaceInfo.forwardJson.picList.length === 1, 'is-double': spaceInfo.forwardJson.picList.length === 2}" v-if="spaceInfo.forwardJson.picList && spaceInfo.forwardJson.picList.length">
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
				<space-poll v-if="spaceInfo.poll" :poll="spaceInfo.poll" :night="campusNight" @change="$set(spaceInfo,'poll',$event)"></space-poll>

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
							<campus-avatar :key="'reply-avatar-' + item.id + '-' + item.userJson.uid + '-' + item.userJson.avatar" class="cu-avatar round" :src="item.userJson.avatar" :name="item.userJson.name" @tap.stop="toUserContents(item.userJson)"></campus-avatar>
							<view class="content">
								<view class="text-grey" @tap.stop="toUserContents(item.userJson)">
									{{item.userJson.name}}
									
									<text class="userlv" v-if="item.userJson.isvip>0" style="background: linear-gradient(to bottom right, #f2ad5c, #e6216d,#901ccb);color:white;padding: 2px 5px;border-radius: 10px;">VIP</text>
									<text class="userlv" :style="getLvStyle(item.userJson.experience)">{{getLv(item.userJson.experience)}}</text>
									
									<text class="userlv customize" v-if="item.userJson.customize&&item.userJson.customize!=''">{{item.userJson.customize}}</text>
									</view>
								<view class="text-content text-df break-all" user-select @longpress.stop="copyComment(item.text)">
									<rich-text :nodes="markHtml(item.text)"></rich-text>
								</view>
								<view class="comment-action-row">
									<text class="text-gray">{{formatDate(item.created)}}</text>
									<text class="comment-action" @tap="openReplyComposer(item)">回复</text>
									<text class="comment-action" :class="item.isLikes==1?'text-blue':''" @tap="toggleReplyLike(item)">
										<text class="cuIcon-appreciate"></text>{{formatNumber(item.likes) || ''}}
									</text>
								</view>
								<view class="space-reply-num" v-if="item.reply>0 && !item._expanded" @tap="toggleReplyThread(item)">
									<text v-if="item._loading">正在加载…</text>
									<block v-else><text>显示{{item.reply}}条回复</text><text class="cuIcon-unfold margin-left-xs"></text></block>
								</view>
								<view class="comment-thread-wrap" v-if="item._expanded">
									<space-reply-thread
										:items="item._children"
										:night="campusNight"
										:current-uid="uid"
										:group="group"
										@reply="openReplyComposer"
										@like="toggleReplyLike"
										@delete="deleteThreadReply"
										@toggle="toggleReplyThread"
										@more="loadMoreReplyThread"
										@user="toUserContents"
									></space-reply-thread>
									<view class="space-reply-num" v-if="item._childMore" @tap="loadMoreReplyThread(item)">
										<text v-if="item._loading">正在加载…</text>
										<block v-else><text>显示更多回复</text><text class="cuIcon-unfold margin-left-xs"></text></block>
									</view>
									<view class="space-reply-num is-collapse" @tap="toggleReplyThread(item)"><text>收起回复</text><text class="cuIcon-fold margin-left-xs"></text></view>
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
						
						暂时没有评论
						<view class="text-center margin-top-sm">
							<text class="cu-btn bg-blue" @tap="openReplyComposer(spaceInfo)">发布评论</text>
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
							<campus-avatar :key="'forward-avatar-' + item.id + '-' + item.userJson.uid + '-' + item.userJson.avatar" class="cu-avatar round" :src="item.userJson.avatar" :name="item.userJson.name" @tap.stop="toUserContents(item.userJson)"></campus-avatar>
							<view class="content">
								<view class="text-grey" @tap.stop="toUserContents(item.userJson)">
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
			
			<view class="space-footer-box" @tap="openReplyComposer(spaceInfo)">
				<text class="cuIcon-message"></text>
				评论
			</view>
			<view class="space-footer-box" @tap="toLike(spaceInfo.id)">
				<text class="cuIcon-appreciate"  :class="spaceInfo.isLikes==1?'text-blue':''"></text>
				点赞
			</view>
		</view>
		<view class="reply-composer-mask" v-if="replyComposerVisible" @tap="closeReplyComposer">
			<view class="reply-composer" :class="{'is-night': campusNight}" @tap.stop>
				<view class="reply-composer-head">
					<text class="reply-composer-cancel" @tap="closeReplyComposer">取消</text>
					<view class="reply-composer-title">
						<text>{{replyTargetName ? '回复评论' : '发表评论'}}</text>
						<text class="reply-composer-target" v-if="replyTargetName">@{{replyTargetName}}</text>
					</view>
					<view class="reply-composer-send" :class="{'is-disabled': !canSubmitReply}" @tap="submitReply">
						{{replySubmitting ? '发送中' : '发送'}}
					</view>
				</view>
				<textarea
					class="reply-composer-input"
					v-model="replyText"
					:focus="replyInputFocus"
					:placeholder="replyTargetName ? '回复 @' + replyTargetName : '友善交流，分享你的想法'"
					maxlength="1500"
					:adjust-position="true"
					:cursor-spacing="24"
				></textarea>
				<view class="reply-composer-meta"><text>{{replyText.length}}/1500</text></view>
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
	import { normalizeUser } from '@/utils/avatar.js'
	import { copyText } from '@/utils/clipboard.js'
	import SpacePoll from '@/components/space-poll/space-poll.vue'
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
		components:{SpacePoll},
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
					id: 0,
					likes: 0,
					reply: 0,
					text: "",
					toid: 0,
					type: 0,
					uid: 0,
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
				replyComposerVisible:false,
				replyInputFocus:false,
				replyText:'',
				replyTarget:null,
				replySubmitting:false,
				replyListRefreshPending:false,
			}
		},
		computed: {
			campusNight() {
				return resolveCampusNight(this.campusThemeMode, isDongchangfuNight(this.campusThemeClock))
			},
			replyTargetName() {
				if (!this.replyTarget || this.replyTarget.id == this.spaceInfo.id) return '';
				return this.replyTarget.userJson ? this.replyTarget.userJson.name : '';
			},
			canSubmitReply() {
				return !this.replySubmitting && this.replyText.trim().length > 0;
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
			copyComment(text){
				copyText(text, '评论已复制');
			},
			openSpaceActions(){
				if(!this.spaceInfo || !this.spaceInfo.id) return false;
				var authorUid = this.spaceInfo.userJson ? Number(this.spaceInfo.userJson.uid || 0) : 0;
				var owner = authorUid !== 0 && authorUid === Number(this.uid || 0);
				var staff = this.group === 'administrator' || this.group === 'editor';
				var isMainSpace = Number(this.spaceInfo.type || 0) !== 3;
				var actions = owner || staff ? ['编辑动态', '删除动态'] : (isMainSpace ? ['举报动态'] : []);
				if(!actions.length) return false;
				uni.showActionSheet({
					itemList: actions,
					success: (choice) => {
						if(owner || staff){
							if(choice.tapIndex === 0) this.edit(this.spaceInfo.id);
							if(choice.tapIndex === 1) this.deleteCurrentSpace();
						} else if(choice.tapIndex === 0){
							this.reportCurrentSpace();
						}
					}
				});
			},
			deleteCurrentSpace(){
				var id = this.spaceInfo.id;
				uni.showModal({
					title: '删除动态',
					content: '删除后将无法恢复，确认继续吗？',
					success: (choice) => {
						if(!choice.confirm) return;
						uni.showLoading({ title: '删除中' });
						this.$Net.request({
							url: this.$API.spaceDelete(),
							data: { id: id, token: this.token },
							header: { 'Content-Type':'application/x-www-form-urlencoded' },
							method: 'post',
							dataType: 'json',
							success: (res) => {
								if(res.data && res.data.code == 1){
									uni.showToast({ title: '动态已删除', icon: 'success' });
									setTimeout(() => uni.navigateBack({ delta: 1 }), 400);
								} else {
									uni.showToast({ title: res.data && res.data.msg ? res.data.msg : '删除失败', icon: 'none' });
								}
							},
							fail: () => uni.showToast({ title: '网络不太好哦', icon: 'none' }),
							complete: () => uni.hideLoading()
						});
					}
				});
			},
			reportCurrentSpace(){
				if(!this.token){
					uni.showToast({ title: '请先登录', icon: 'none' });
					setTimeout(() => uni.navigateTo({ url: '/pages/user/login' }), 500);
					return false;
				}
				var reasons = ['广告营销', '人身攻击', '色情低俗', '违法违规', '其他'];
				uni.showActionSheet({
					itemList: reasons,
					success: (choice) => this.submitCurrentReport(reasons[choice.tapIndex])
				});
			},
			submitCurrentReport(reason){
				uni.showLoading({ title: '提交中' });
				this.$Net.request({
					url: this.$API.spaceReportAdd(),
					data: { id: this.spaceInfo.id, reason: reason, token: this.token },
					header: { 'Content-Type':'application/x-www-form-urlencoded' },
					method: 'post',
					dataType: 'json',
					success: (res) => uni.showToast({
						title: res.data && res.data.msg ? res.data.msg : '提交失败', icon: 'none'
					}),
					fail: () => uni.showToast({ title: '网络不太好哦', icon: 'none' }),
					complete: () => uni.hideLoading()
				});
			},
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
					if(that.infoType==0){
						that.getReplyList(true)
					}
					if(that.infoType==1){
						that.getForwardList(true);
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
				var target = this.spaceInfo;
				if(id != this.spaceInfo.id){
					target = this.findReplyById(this.replyList, id) || {id:id, userJson:{name:''}};
				}
				this.openReplyComposer(target);
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
				if(that.isLoad==1){
					if(!isPage) that.replyListRefreshPending = true;
					return;
				}
				that.isLoad=1;
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
								list[i] = that.prepareReplyItem(list[i]);
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
								if(!isPage){
									that.replyList = [];
								}
							}
						}
					},
					fail: function(res) {
						
						that.moreText="加载更多";
						that.isLoad=0;
					},
					complete: function() {
						that.isLoad=0;
						if(that.replyListRefreshPending){
							that.replyListRefreshPending = false;
							that.getReplyList(false);
						}
					}
				})
			},
			prepareReplyItem(item){
				item = item || {};
				item.userJson = normalizeUser(item.userJson);
				item.renderedText = this.markHtml(item.text || '');
				item.displayTime = this.formatDate(item.created || 0);
				item._children = Array.isArray(item._children) ? item._children : [];
				item._expanded = item._expanded === true;
				item._loaded = item._loaded === true;
				item._loading = false;
				item._childPage = Number(item._childPage || 0);
				item._childMore = item._childMore === true;
				return item;
			},
			findReplyById(items,id){
				for(var i=0;i<items.length;i++){
					if(items[i].id == id) return items[i];
					var nested = this.findReplyById(items[i]._children || [],id);
					if(nested) return nested;
				}
				return null;
			},
			openReplyComposer(target){
				if(!this.token){
					uni.showToast({title:'请先登录',icon:'none'});
					setTimeout(function(){uni.navigateTo({url:'/pages/user/login'});},700);
					return;
				}
				this.replyTarget = target && target.id ? target : this.spaceInfo;
				this.replyComposerVisible = true;
				this.replyInputFocus = false;
				this.$nextTick(() => {
					setTimeout(() => { this.replyInputFocus = true; },120);
				});
			},
			closeReplyComposer(){
				if(this.replySubmitting) return;
				this.replyInputFocus = false;
				this.replyComposerVisible = false;
				this.replyTarget = null;
			},
			submitReply(){
				var that = this;
				if(!that.canSubmitReply) return;
				var text = that.replyText.trim();
				var targetId = that.replyTarget && that.replyTarget.id ? that.replyTarget.id : that.spaceInfo.id;
				that.replySubmitting = true;
				that.$Net.request({
					url: that.$API.addSpace(),
					data:{type:3,text:text,toid:targetId,token:that.token},
					header:{'Content-Type':'application/x-www-form-urlencoded'},
					method:'post',
					dataType:'json',
					timeout:15000,
					success:function(res){
						if(res.data.code==1){
							that.replySubmitting = false;
							that.replyText = '';
							that.replyInputFocus = false;
							that.replyComposerVisible = false;
							that.replyTarget = null;
							that.page = 1;
							that.getSpaceInfo();
							that.getReplyList(false);
							uni.showToast({title:res.data.msg || '评论成功',icon:'success'});
						}else{
							that.replySubmitting = false;
							uni.showToast({title:res.data.msg || '发送失败，请重试',icon:'none'});
						}
					},
					fail:function(){
						that.replySubmitting = false;
						uni.showToast({title:'发送失败，请检查网络后重试',icon:'none'});
					}
				});
			},
			toggleReplyThread(item){
				if(!item || item._loading) return;
				if(item._expanded){
					this.$set(item,'_expanded',false);
					return;
				}
				if(item._loaded){
					this.$set(item,'_expanded',true);
					return;
				}
				this.fetchReplyChildren(item,false);
			},
			loadMoreReplyThread(item){
				this.fetchReplyChildren(item,true);
			},
			fetchReplyChildren(item,append){
				var that = this;
				if(!item || item._loading) return;
				var nextPage = append ? Number(item._childPage || 1) + 1 : 1;
				that.$set(item,'_loading',true);
				that.$Net.request({
					url:that.$API.spaceList(),
					data:{
						searchParams:JSON.stringify({toid:item.id,type:3}),
						limit:3,
						page:nextPage,
						order:'created',
						token:that.token
					},
					method:'get',
					dataType:'json',
					timeout:15000,
					success:function(res){
						if(res.data.code!=1){
							uni.showToast({title:res.data.msg || '回复加载失败',icon:'none'});
							return;
						}
						var children = (res.data.data || []).map(function(child){return that.prepareReplyItem(child);});
						var merged = append ? (item._children || []).concat(children) : children;
						that.$set(item,'_children',merged);
						that.$set(item,'_childPage',nextPage);
						that.$set(item,'_loaded',true);
						that.$set(item,'_expanded',true);
						that.$set(item,'_childMore',merged.length < Number(item.reply || 0) && children.length > 0);
					},
					fail:function(){
						uni.showToast({title:'回复加载失败，请重试',icon:'none'});
					},
					complete:function(){that.$set(item,'_loading',false);}
				});
			},
			toggleReplyLike(item){
				var that = this;
				if(!that.token){uni.showToast({title:'请先登录',icon:'none'});return;}
				if(!item || item._likeSubmitting) return;
				var wasLiked = item.isLikes == 1;
				var oldLikes = Number(item.likes || 0);
				that.$set(item,'_likeSubmitting',true);
				that.$set(item,'isLikes',wasLiked ? 0 : 1);
				that.$set(item,'likes',wasLiked ? Math.max(0,oldLikes-1) : oldLikes+1);
				that.$Net.request({
					url:that.$API.spaceLikes(),data:{token:that.token,id:item.id},method:'get',dataType:'json',
					success:function(res){
						if(res.data.code==0){that.$set(item,'isLikes',wasLiked?1:0);that.$set(item,'likes',oldLikes);}
					},
					fail:function(){that.$set(item,'isLikes',wasLiked?1:0);that.$set(item,'likes',oldLikes);},
					complete:function(){that.$set(item,'_likeSubmitting',false);}
				});
			},
			deleteThreadReply(item){
				if(item && item.id) this.toDelete2(item.id);
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
				var wasLiked = that.spaceInfo.isLikes == 1;
				var oldLikes = Number(that.spaceInfo.likes || 0);
				that.spaceInfo.isLikes = wasLiked ? 0 : 1;
				that.spaceInfo.likes = wasLiked ? Math.max(0, oldLikes - 1) : oldLikes + 1;
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
							that.spaceInfo.isLikes = wasLiked ? 1 : 0;
							that.spaceInfo.likes = oldLikes;
						}else if(res.data.data === 0 || res.data.data === 1){
							that.spaceInfo.isLikes = res.data.data;
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
						that.spaceInfo.isLikes = wasLiked ? 1 : 0;
						that.spaceInfo.likes = oldLikes;
						
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
				var targetList = type=="forward" ? that.forwardList : that.replyList;
				var wasListLiked = targetList[index].isLikes == 1;
				var oldListLikes = Number(targetList[index].likes || 0);
				targetList[index].isLikes = wasListLiked ? 0 : 1;
				targetList[index].likes = wasListLiked ? Math.max(0, oldListLikes - 1) : oldListLikes + 1;
				
				
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
							targetList[index].isLikes = wasListLiked ? 1 : 0;
							targetList[index].likes = oldListLikes;
						}else if(res.data.data === 0 || res.data.data === 1){
							targetList[index].isLikes = res.data.data;
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
						targetList[index].isLikes = wasListLiked ? 1 : 0;
						targetList[index].likes = oldListLikes;
						
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
				if (!data) return false;
				var name = data.name || '用户';
				var title = name + "的信息";
				var id = Number(data.uid || data.id || 0);
				if(id==0){
					uni.showToast({
						title: "用户不存在或已注销",
						icon: 'none'
					})
					return false
				}
				uni.navigateTo({
					url: '/pages/contents/userinfo?title=' + encodeURIComponent(title) + '&name=' + encodeURIComponent(name) + '&uid=' + id + '&avatar=' + encodeURIComponent(data.avatar || ''),
					fail: function() {
						uni.showToast({ title: '无法打开用户主页', icon: 'none' });
					}
				});
			},
			toReplyInfo(id){
				var item = this.findReplyById(this.replyList,id);
				if(item) this.toggleReplyThread(item);
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
					title: '确定要删除该评论吗',
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

.space-detail-page .cu-card.dynamic.space-info > .cu-item > .text-content.space-detail-text,
.space-detail-page .cu-card.dynamic.space-info > .cu-item > .text-content.space-detail-text rich-text {
	display: block !important;
	height: auto !important;
	max-height: none !important;
	overflow: visible !important;
	white-space: normal !important;
	-webkit-box-orient: initial !important;
	-webkit-line-clamp: unset !important;
	line-clamp: unset !important;
}

.space-detail-media > .bg-img > image {
	position: absolute;
	inset: 0;
	width: 100%;
	height: 100%;
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

.comment-action-row {
	display: flex;
	align-items: center;
	gap: 28rpx;
	margin-top: 14rpx;
	font-size: 24rpx;
}

.comment-action {
	display: inline-flex;
	align-items: center;
	gap: 7rpx;
	color: #60736e;
	font-weight: 600;
}

.space-detail-page .space-reply-num {
	display: inline-flex;
	align-items: center;
	min-height: 56rpx;
	margin-top: 8rpx;
	padding: 0;
	background: transparent;
	color: #526762;
	font-size: 25rpx;
	font-weight: 600;
}

.space-detail-page .space-reply-num.is-collapse {
	color: #7c8b86;
}

.comment-thread-wrap {
	position: relative;
	margin-top: 4rpx;
}

.reply-composer-mask {
	position: fixed;
	z-index: 1200;
	top: 0;
	right: 0;
	bottom: 0;
	left: 0;
	display: flex;
	align-items: flex-end;
	background: rgba(0, 0, 0, 0.42);
	animation: reply-mask-in 160ms ease-out;
}

.reply-composer {
	width: 100%;
	padding: 0 24rpx calc(24rpx + env(safe-area-inset-bottom));
	border-radius: 22rpx 22rpx 0 0;
	background: #ffffff;
	box-sizing: border-box;
	animation: reply-sheet-in 190ms ease-out;
}

.reply-composer-head {
	display: grid;
	grid-template-columns: 100rpx 1fr 112rpx;
	align-items: center;
	min-height: 92rpx;
}

.reply-composer-cancel {
	color: #71817c;
	font-size: 27rpx;
}

.reply-composer-title {
	display: flex;
	min-width: 0;
	flex-direction: column;
	align-items: center;
	color: #263a37;
	font-size: 29rpx;
	font-weight: 600;
}

.reply-composer-target {
	max-width: 340rpx;
	margin-top: 2rpx;
	overflow: hidden;
	color: #168c80;
	font-size: 22rpx;
	font-weight: 500;
	text-overflow: ellipsis;
	white-space: nowrap;
}

.reply-composer-send {
	display: flex;
	align-items: center;
	justify-content: center;
	height: 58rpx;
	border-radius: 16rpx;
	background: #168c80;
	color: #ffffff;
	font-size: 26rpx;
	font-weight: 600;
}

.reply-composer-send.is-disabled {
	background: #dbe4e1;
	color: #96a39f;
}

.reply-composer-input {
	width: 100%;
	min-height: 230rpx;
	max-height: 430rpx;
	padding: 20rpx;
	border: 1rpx solid #dce6e3;
	border-radius: 16rpx;
	background: #f7f9f8;
	color: #263a37;
	font-size: 29rpx;
	line-height: 1.6;
	box-sizing: border-box;
}

.reply-composer-meta {
	display: flex;
	justify-content: flex-end;
	padding-top: 10rpx;
	color: #94a09c;
	font-size: 22rpx;
}

.reply-composer.is-night {
	background: #202728;
}

.reply-composer.is-night .reply-composer-title,
.reply-composer.is-night .reply-composer-input {
	color: #edf3f0;
}

.reply-composer.is-night .reply-composer-input {
	border-color: #3b4745;
	background: #171d1e;
}

.reply-composer.is-night .reply-composer-send.is-disabled {
	background: #303a39;
	color: #788681;
}

@keyframes reply-mask-in {
	from { background: rgba(0, 0, 0, 0); }
}

@keyframes reply-sheet-in {
	from { transform: translateY(100%); }
	to { transform: translateY(0); }
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

.space-detail-page.campus-night .comment-action {
	color: #b6c2be;
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
