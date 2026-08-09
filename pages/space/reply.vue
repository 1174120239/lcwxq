<template>
	<view class="user campus-reply-page" :class="AppStyle">
		<view class="header" :style="[{height:CustomBar + 'px'}]">
			<view class="cu-bar bg-white" :style="{'height': CustomBar + 'px','padding-top':StatusBar + 'px'}">
				<view class="action" @tap="back">
					<text class="cuIcon-back"></text>
				</view>
				<view class="content text-bold" :style="[{top:StatusBar + 'px'}]">
					回复动态
				</view>
				<!--  #ifdef H5 || APP-PLUS -->
				<view class="action">
					<text class="reply-submit-button padding-lr-sm padding-tb-xs round text-shojo" :class="{'is-disabled': replySubmitting}" @tap="reply()" style="background-color: #cffff2;">{{replySubmitting ? '发送中' : '回复'}}</text>
				</view>
				<!--  #endif -->
			</view>
		</view>
		<view :style="[{padding:NavBar + 'px 10px 0px 10px'}]"></view>
		
		<form>
			<view class="cu-form-group margin-top reply-input-group">
				<textarea maxlength="-1" v-model="text" placeholder="输入回复的内容"></textarea>
			</view>
			<!--  #ifdef H5 || APP-PLUS -->
			<view class="comments-owo reply-emoji-tools">
				<text class="cuIcon-emoji" :style="{'color': isOwO ? '#3cc9a4' : ''}" @tap="OwO"></text>
				<!--表情-->
				<view class="owo reply-emoji-panel" v-if="isOwO">
					<scroll-view class="owo-list" scroll-y>
						<view class="owo-main">
							<view class="owo-lit-box" v-for="(item,index)  in owoList" @tap="setOwO(item)" :key="index">
								<image :src="'/'+item.icon" mode="aspectFill"></image>
							</view>
						</view>
						
					</scroll-view>
					<view class="owo-type">
						<view class="owo-box" @tap="toOwO('paopao')" :class="OwOtype=='paopao'?'cur':''">
							泡泡
						</view>
						<view class="owo-box" @tap="toOwO('alu')" :class="OwOtype=='alu'?'cur':''">
							阿鲁
						</view>
						<view class="owo-box" @tap="toOwO('quyinniang')" :class="OwOtype=='quyinniang'?'cur':''">
							蛆音娘
						</view>
					</view>
				</view>
			</view>
			<!--  #endif -->
			<!--  #ifdef MP -->
			<view class="all-btn">
				<view class="user-btn flex flex-direction">
					<button class="cu-btn bg-cyan margin-tb-sm lg" :disabled="replySubmitting" @tap="reply">{{replySubmitting ? '发送中' : '提交回复'}}</button>
					
				</view>
			</view>
			<!--  #endif -->
			
		</form>
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
				
				id:0,
				text:"",
				
				userinfo:{},
				token:"",
				
				isOwO:false,
				owo:owo,
				owoList:[],
				OwOtype:"paopao",
				replySubmitting:false,
				
			}
		},
		onPullDownRefresh(){
			var that = this;
			
		},
		onShow(){
			var that = this;
			// #ifdef APP-PLUS
			
			plus.navigator.setStatusBarStyle("dark")
			// #endif
			//获取用户信息
			if(localStorage.getItem('userinfo')){
				that.userinfo = JSON.parse(localStorage.getItem('userinfo'));
			}
			if(localStorage.getItem('token')){
				that.token = localStorage.getItem('token');
			}
			
		},
		onLoad(res) {
			var that = this;
			// #ifdef APP-PLUS || MP
			that.NavBar = this.CustomBar;
			// #endif
			that.id=res.id;
			// #ifdef APP-PLUS || H5
			that.owoList = that.owo.data.paopao.container;
			// #endif
		},
		methods: {
			PickerChange(e) {
				this.index = e.detail.value
			},
			back(){
				uni.navigateBack({
					delta: 1
				});
			},
			reply(){
				var that = this;
				if(that.replySubmitting){
					return false;
				}
				if(!that.text || !that.text.trim()){
					uni.showToast({title:'请输入回复内容',icon:'none'});
					return false;
				}
				if(that.token==""){
					uni.showToast({
					    title:"请先登录",
						icon:'none',
						duration: 1000,
						position:'bottom',
					});
					var timer = setTimeout(function() {
						uni.navigateTo({
						    url: '/pages/user/login'
						});
						clearTimeout('timer')
					}, 1000)
					return false
				}
				if(that.id==0){
					uni.showToast({
					    title:"参数不正确",
						icon:'none',
						duration: 1000,
						position:'bottom',
					});
					return false
				}
				var data = {
					type:3,
					text:that.text.trim(),
					toid:that.id,
					 token:that.token
				}
				that.replySubmitting = true;
				that.$Net.request({
					
					url: that.$API.addSpace(),
					data:that.$API.removeObjectEmptyKey(data),
					header:{
						'Content-Type':'application/x-www-form-urlencoded'
					},
					method: "get",
					dataType: 'json',
					timeout: 15000,
					success: function(res) {
						that.replySubmitting = false;
						uni.showToast({
							title: res.data.msg,
							icon: 'none'
						})
						if(res.data.code==1){
							var timer = setTimeout(function() {
								that.back();
							}, 500)
							
						}
					},
					fail: function(res) {
						that.replySubmitting = false;
						uni.showToast({
							title: "网络不太好哦~",
							icon: 'none'
						})
						uni.stopPullDownRefresh()
					}
				})
			},
			
			toOwO(text){
				var that = this;
				that.OwOtype = text;
				if(text=="paopao"){
					that.owoList = that.owo.data.paopao.container;
				}
				if(text=="adai"){
					that.owoList = that.owo.data.adai.container;
				}
				if(text=="alu"){
					that.owoList = that.owo.data.alu.container;
				}
				if(text=="quyinniang"){
					that.owoList = that.owo.data.quyinniang.container;
				}
			},
			setOwO(data){
				var that = this;
				var text = data.data;
				that.text+=text;
			},
			OwO(){
				var that = this;
				that.isOwO = !that.isOwO;
			}
		}
	}
</script>

<style>
.campus-reply-page {
	min-height: 100vh;
	min-height: 100dvh;
	padding-bottom: env(safe-area-inset-bottom);
	background: #f4f8f8;
	color: #263a37;
	box-sizing: border-box;
}

.campus-reply-page .header .cu-bar {
	border-bottom: 1rpx solid #e2ece9;
	background: rgba(250, 252, 252, 0.98) !important;
	box-shadow: none !important;
}

.campus-reply-page .header .content,
.campus-reply-page .header .action,
.campus-reply-page .header .cuIcon-back {
	color: #263a37 !important;
}

.campus-reply-page .reply-submit-button {
	display: inline-flex;
	align-items: center;
	justify-content: center;
	min-width: 96rpx;
	height: 58rpx;
	padding: 0 22rpx !important;
	border: 1rpx solid rgba(29, 142, 123, 0.12);
	border-radius: 18rpx !important;
	background: #d9f7ee !important;
	color: #087d6e !important;
	font-size: 26rpx;
	font-weight: 700;
	line-height: 58rpx;
	box-sizing: border-box;
}

.campus-reply-page .reply-submit-button.is-disabled {
	opacity: 0.55;
}

.campus-reply-page .reply-input-group {
	min-height: 300rpx;
	margin: 22rpx 20rpx 0 !important;
	padding: 24rpx !important;
	border: 1rpx solid #dce9e6;
	border-radius: 18rpx;
	background: #ffffff !important;
	box-shadow: 0 8rpx 24rpx rgba(35, 76, 70, 0.06);
	box-sizing: border-box;
	align-items: flex-start;
}

.campus-reply-page .reply-input-group::after {
	display: none;
}

.campus-reply-page .reply-input-group textarea {
	width: 100%;
	min-height: 248rpx;
	padding: 0 !important;
	background: transparent !important;
	color: #263a37 !important;
	font-size: 30rpx;
	line-height: 1.6;
	box-sizing: border-box;
}

.campus-reply-page .reply-input-group textarea::placeholder {
	color: #879a95;
}

.campus-reply-page .reply-emoji-tools {
	position: relative;
	min-height: 74rpx;
	margin: 0 20rpx;
	padding: 18rpx 8rpx;
	box-sizing: border-box;
}

.campus-reply-page .reply-emoji-tools > .cuIcon-emoji {
	display: inline-flex;
	align-items: center;
	justify-content: center;
	width: 54rpx;
	height: 54rpx;
	border-radius: 14rpx;
	color: #6e817c !important;
	font-size: 38rpx;
}

.campus-reply-page .reply-emoji-tools > .cuIcon-emoji:active {
	background: #e1efeb;
}

.campus-reply-page .reply-emoji-panel {
	margin-top: 12rpx;
	border: 1rpx solid #dce9e6;
	border-radius: 16rpx;
	background: #ffffff;
	box-shadow: 0 8rpx 22rpx rgba(35, 76, 70, 0.08);
	overflow: hidden;
}

.campus-reply-page .reply-emoji-panel .owo-list {
	max-height: 340rpx;
	background: transparent;
}

.campus-reply-page .reply-emoji-panel .owo-type {
	border-top: 1rpx solid #e2ece9;
	background: #f6faf9;
}

.campus-reply-page .reply-emoji-panel .owo-box {
	color: #647973;
}

.campus-reply-page .reply-emoji-panel .owo-box.cur {
	border-bottom-color: #168c80;
	color: #168c80;
}

.campus-reply-page .all-btn {
	margin: 0 20rpx;
}

.campus-reply-page .all-btn .cu-btn {
	border-radius: 16rpx;
	background: #168c80 !important;
	color: #ffffff !important;
}

.campus-reply-page.campus-night {
	background: #15191b !important;
	color: #edf3f0;
}

.campus-reply-page.campus-night .header .cu-bar {
	border-bottom-color: rgba(218, 231, 226, 0.1);
	background: #1b2224 !important;
}

.campus-reply-page.campus-night .header .content,
.campus-reply-page.campus-night .header .action,
.campus-reply-page.campus-night .header .cuIcon-back {
	color: #edf3f0 !important;
}

.campus-reply-page.campus-night .reply-submit-button {
	border-color: rgba(115, 211, 190, 0.2);
	background: #29423d !important;
	color: #a9dfd1 !important;
}

.campus-reply-page.campus-night .reply-input-group {
	border-color: rgba(218, 231, 226, 0.12);
	background: #202728 !important;
	box-shadow: none;
}

.campus-reply-page.campus-night .reply-input-group textarea {
	color: #edf3f0 !important;
}

.campus-reply-page.campus-night .reply-input-group textarea::placeholder {
	color: #9aa9a4;
}

.campus-reply-page.campus-night .reply-emoji-tools > .cuIcon-emoji {
	color: #a8bbb5 !important;
}

.campus-reply-page.campus-night .reply-emoji-tools > .cuIcon-emoji:active {
	background: #263431;
}

.campus-reply-page.campus-night .reply-emoji-panel {
	border-color: rgba(218, 231, 226, 0.12);
	background: #202728;
	box-shadow: none;
}

.campus-reply-page.campus-night .reply-emoji-panel .owo-list {
	background: #202728;
}

.campus-reply-page.campus-night .reply-emoji-panel .owo-type {
	border-top-color: rgba(218, 231, 226, 0.1);
	background: #1a2021;
}

.campus-reply-page.campus-night .reply-emoji-panel .owo-box {
	color: #9eaea8;
}

.campus-reply-page.campus-night .reply-emoji-panel .owo-box.cur {
	border-bottom-color: #69c7b2;
	color: #a9dfd1;
}

.campus-reply-page.campus-night .all-btn .cu-btn {
	background: #28665b !important;
	color: #eaf6f2 !important;
}
</style>
