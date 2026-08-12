<template>
	<view class="user campus-subpage campus-settings-page" :class="AppStyle">
		<view class="header" :style="[{height:CustomBar + 'px'}]">
			<view class="cu-bar bg-white" :style="{'height': CustomBar + 'px','padding-top':StatusBar + 'px'}">
				<view class="action" @tap="back">
					<text class="cuIcon-back"></text>
				</view>
				<view class="content text-bold" :style="[{top:StatusBar + 'px'}]">
					个人设置
				</view>
				<!--  #ifdef H5 || APP-PLUS -->
				<view class="action" @tap="userEdit">
					<button class="cu-btn round bg-blue">保存</button>
				</view>
				<!--  #endif -->
			</view>
		</view>
		<view :style="[{padding:NavBar + 'px 10px 0px 10px'}]"></view>
		
		<form>
			<view class="user-edit-header margin-top">
				<image :src="avatar"></image>
				<!--  #ifdef H5 || APP-PLUS -->
				<!-- <text class="cu-btn bg-blue radius" @tap="showModal" data-target="DialogModal1">设置头像</text> -->
				<text class="cu-btn bg-gradual-blue radius" @tap="toAvatar" >设置头像</text>
				<!--  #endif -->
			</view>
			<view class="cu-form-group">
			<view class="title">背景</view>
			<input :placeholder="ifurl==true ? '已上传，保存后生效' : '个人页的背景封面'" @tap="upload" name="input" disabled></input>
			<text class="text-blue" @tap="upload">上传</text>
			</view>
			<view class="cu-form-group">
				<view class="title">账号</view>
				<input name="input" disabled="disabled" :value="name"></input>
			</view>
			
			
			<view class="cu-form-group margin-top">
				<view class="title">昵称</view>
				<input placeholder="请输入昵称" name="input" v-model="screenName"></input>
			</view>
			<view class="cu-form-group">
				<view class="title">邮箱</view>
				<input placeholder="未设置" disabled="disabled" name="input" :value="mail"></input>
				<view class="text-blue" @tap="toEmail">修改</view>
			</view>
			<view class="cu-form-group align-start">
				<view class="title">个人简介</view>
				<view class="introduce-editor">
					<textarea v-model="introduce" maxlength="255" placeholder="输入个人简介，支持换行"></textarea>
					<text class="introduce-count">{{introduceCount}}/255</text>
				</view>
			</view>
			<view class="cu-form-group margin-top">
				<view class="title">性别</view>
				<picker :range="genderOptions" :value="genderIndex" @change="changeGender">
					<view class="profile-picker">{{gender || '未设置'}}<text class="cuIcon-right"></text></view>
				</picker>
				<view class="profile-visibility"><text>公开</text><switch color="#168573" :checked="showGender" @change="showGender=$event.detail.value"></switch></view>
			</view>
			<view class="cu-form-group">
				<view class="title">生日</view>
				<picker mode="date" :value="birthday" start="1900-01-01" :end="today" @change="birthday=$event.detail.value">
					<view class="profile-picker">{{birthday || '未设置'}}<text class="cuIcon-right"></text></view>
				</picker>
				<text class="profile-clear" v-if="birthday" @tap="clearBirthday">清除</text>
				<view class="profile-visibility"><text>公开</text><switch color="#168573" :checked="showBirthday" @change="showBirthday=$event.detail.value"></switch></view>
			</view>
			<view class="cu-form-group margin-top">
				<view class="title">密码</view>
				<input placeholder="请输入密码,不填则不修改" v-model="password" name="input"></input>
			</view>
			<view class="cu-form-group">
				<view class="title">确认密码</view>
				<input placeholder="请再次输入密码" v-model="repassword" name="input"></input>
			</view>
		</form>
		<!--  #ifdef H5 || APP-PLUS -->

		<!--  #endif -->
		<view class="cu-modal" :class="modalName=='DialogModal1'?'show':''">
			<view class="cu-dialog">
				<view class="cu-bar bg-white justify-end">
					<view class="content">设置头像</view>
					<view class="action" @tap="hideModal">
						<text class="cuIcon-close text-red"></text>
					</view>
				</view>
				<view class="padding-xl text-left">
					<view>Gravatar是全球最大的头像库。它广泛应用于国内外各类网站和程序，包括知名的Github。在Gravatar通过您的邮箱注册用户，并设置头像后，您在所有支持Gravatar的网站使用邮箱，都会显示您的头像。</view>
					<view>或者，您可以将将邮箱设置成QQ邮箱，将自动获取您的QQ头像。</view>
				</view>
				<view class="cu-bar bg-white justify-end">
					<view class="action">
						<button class="cu-btn bg-green margin-left" @tap="toGravatar">前往Gravatar</button>
		
					</view>
				</view>
			</view>
		</view>
		<!--  #ifdef MP -->
		<view class="post-update bg-blue" @tap="userEdit">
			<text class="cuIcon-upload"></text>
		</view>
		<!--  #endif -->
	</view>
</template>

<script>
	import { localStorage } from '../../js_sdk/mp-storage/mp-storage/index.js'
	// #ifdef H5 || APP-PLUS 
	import { pathToBase64, base64ToPath } from '../../js_sdk/mmmm-image-tools/index.js'
	// #endif
	export default {
		data() {
			return {
				StatusBar: this.StatusBar,
				CustomBar: this.CustomBar,
				NavBar:this.StatusBar +  this.CustomBar,
			AppStyle:this.$store.state.AppStyle,
				ifurl:false,
				uid:0,
				name:'',
				screenName:'',
				password:'',
				repassword:'',
				mail:'',
				userBg:"",
				avatar:"",
				avatarNew:"",
				introduce:"",
				genderOptions:['保密', '男', '女'],
				gender:'',
				birthday:'',
				showGender:false,
				showBirthday:false,
				backif:0,
				cacheLoaded:false,
				modalName: null,
				
				token:'',
			}
		},
		computed: {
			introduceCount() {
				return (this.introduce || '').length
			},
			genderIndex() {
				var index = this.genderOptions.indexOf(this.gender)
				return index < 0 ? 0 : index
			},
			today() {
				var date = new Date()
				var month = String(date.getMonth() + 1).padStart(2, '0')
				var day = String(date.getDate()).padStart(2, '0')
				return date.getFullYear() + '-' + month + '-' + day
			}
		},
		onPullDownRefresh(){
			var that = this;
			
		},
		onShow(){
			var that = this;
			// #ifdef APP-PLUS
			
			plus.navigator.setStatusBarStyle(that.$store.state.AppStyle === 'campus-night' ? "light" : "dark")
			// #endif
			
			that.getCacheInfo(false);
			that.getProfileInfo();
			
			if(localStorage.getItem('toAvatar')){
				var toAvatar = JSON.parse(localStorage.getItem('toAvatar'));
				that.avatarUpload(toAvatar.dataUrl);
			}else{
				console.log("没有头像缓存")
			}
			
		},
		onLoad(res) {
			var that = this;
			that.backif = res.backif;
			// #ifdef APP-PLUS || MP
			that.NavBar = this.CustomBar;
			// #endif
		},
		methods: {
			clearBirthday(){
				this.birthday = ''
				this.showBirthday = false
			},
			changeGender(e){
				this.gender = this.genderOptions[Number(e.detail.value)] || '保密'
			},
			getProfileInfo(){
				var that = this
				if(!that.token) return
				that.$Net.request({
					url: that.$API.getUserInfo(),
					data:{token:that.token},
					header:{'Content-Type':'application/x-www-form-urlencoded'},
					method:'get',
					success:function(res){
						if(res.data.code!=1) return
						var profile = res.data.data || {}
						that.gender = profile.gender || ''
						that.birthday = profile.birthday || ''
						that.showGender = Number(profile.showGender || 0) === 1
						that.showBirthday = Number(profile.showBirthday || 0) === 1
					}
				})
			},
			back(){
				uni.navigateBack({
					delta: 1
				});
			},
			profileEditParams(data) {
				var params = this.$API.removeObjectEmptyKey(data);
				// Empty introduction is meaningful: it clears the previously saved profile text.
				params.introduce = this.introduce || "";
				// An empty birthday is also meaningful: it clears the optional profile field.
				params.birthday = this.birthday || "";
				return params;
			},
			showModal(e) {
				this.modalName = e.currentTarget.dataset.target
			},
			hideModal(e) {
				this.modalName = null
			},
			getCacheInfo(force){
				var that = this;
				if(that.cacheLoaded && !force){
					return false;
				}
				if(localStorage.getItem('userinfo')){
					var userInfo = JSON.parse(localStorage.getItem('userinfo'));
					that.uid=userInfo.uid || 0;
					that.screenName=userInfo.screenName || '';
					that.name=userInfo.name || '';
					that.mail=userInfo.mail || '';
					that.userBg=userInfo.userBg || '';
					that.token=userInfo.token || '';
					that.avatar=userInfo.avatar || '';
					that.introduce = userInfo.introduce || '';
					that.cacheLoaded = true;
				}
			},
			userEdit() {
				var that = this;
				if (that.password != "") {
					if (that.password != that.repassword) {
						uni.showToast({
						    title:"两次密码不一致",
							icon:'none',
							duration: 1000,
							position:'bottom',
						});
						return false
					}
					
				}
				
				var data = {
					uid:that.uid,
					name:that.name,
					screenName:that.screenName,
					password:that.password,
					introduce:that.introduce,
					userBg:that.userBg,
					gender:that.gender,
					birthday:that.birthday,
					showGender:that.showGender ? 1 : 0,
					showBirthday:that.showBirthday ? 1 : 0,
				}
				if(that.avatarNew!=''){
					data.avatar = that.avatarNew;
				}
				uni.showLoading({
					title: "加载中"
				});
				that.$Net.request({
					
					url: that.$API.userEdit(),
					data:{
						"params":JSON.stringify(that.profileEditParams(data)),
						"token":that.token
					},
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
						if(res.data.code==1){
							//保存用户信息
							if(that.password!=""){
								localStorage.removeItem('userinfo');
								localStorage.removeItem('token');
								var timer = setTimeout(function() {
									uni.reLaunch({
										url: '/pages/home/home'
									})
									clearTimeout('timer')
								}, 1000)
							}else{
								var userInfo = JSON.parse(localStorage.getItem('userinfo') || '{}');
								userInfo.screenName=that.screenName;
								userInfo.userBg=that.userBg;
								userInfo.introduce = that.introduce;
								userInfo.gender = that.gender;
								userInfo.birthday = that.birthday;
								userInfo.showGender = that.showGender ? 1 : 0;
								userInfo.showBirthday = that.showBirthday ? 1 : 0;
								if(that.avatarNew!=''){
									userInfo.avatar = that.avatarNew;
								}
								that.avatarNew = '';
								localStorage.setItem('userinfo',JSON.stringify(userInfo));
								that.cacheLoaded = false;
								that.getCacheInfo(true);
							}
							if(that.backif==1){
								that.back();
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
						uni.stopPullDownRefresh()
					}
				})
				
			},
			upload() {
			  let that = this
			  uni.chooseImage({
			    count: 1,
			    sourceType: ['album', 'camera'],
			    sizeType: ['compressed'],
			    crop: {
			      width: 470,
			      height: 270,
			      quality: 90
			    },
			    success: function(res) {
			      const tempFilePaths = res.tempFilePaths;
			      uni.showLoading({
			        title: "加载中"
			      });
			      const uploadTask = uni.uploadFile({
			        url: that.$API.upload(),
			        filePath: tempFilePaths[0],
			        name: 'file',
			        formData: {
			          'token': that.token
			        },
					success: function(uploadFileRes) {
					  uni.hideLoading();
					  var data;
					  try {
					    data = JSON.parse(uploadFileRes.data);
					  } catch (error) {
					    uni.showToast({ title: '背景图上传失败，请重试', icon: 'none' });
					    return;
					  }
					  if (data.code == 1 && data.data && data.data.url) {
					    that.userBg = data.data.url;
						that.ifurl = true;
						uni.showToast({ title: '背景图已上传，请点击保存', icon: 'none' });
					  } else {
						uni.showToast({ title: data.msg || '背景图上传失败，请重试', icon: 'none' });
					  }
					},
					fail: function() {
					  uni.hideLoading();
					  uni.showToast({ title: '背景图上传失败，请重试', icon: 'none' });
					}
			      });
			    },
			    fail: function() {
			      setTimeout(function() {
			        uni.hideLoading();
			      }, 1000);
			    }
			  })
			},
			toEmail(){
				var that = this;
				
				uni.navigateTo({
				    url: '/pages/user/mailedit'
				});
			},
			toAddress(){
				var that = this;
				
				uni.navigateTo({
				    url: '/pages/user/address'
				});
			},
			toPay(){
				var that = this;
				
				uni.navigateTo({
				    url: '/pages/user/pay'
				});
			},
			toBind(){
				var that = this;
				
				uni.navigateTo({
				    url: '/pages/user/userbind'
				});
			},
			toGravatar(){
				var that = this;
				that.hideModal();
				var url = "https://cn.gravatar.com/";
				// #ifdef APP-PLUS
				plus.runtime.openURL(url) 
				// #endif
				// #ifdef H5
				window.open(url)
				// #endif
			},
			toAvatar(){
				// #ifdef APP-PLUS || H5
				const that = this;
				  uni.navigateTo({
					url: "../../uni_modules/buuug7-img-cropper/pages/cropper",
					events: {
					  imgCropped(event) {
						console.log(event);
					  },
					},
				  });
				// #endif
			},
			avatarUpload(base64){
				
				var that = this;
				base64ToPath(base64)
				  .then(path => {
					var file = path;
					const uploadTask = uni.uploadFile({
					  url : that.$API.upload(),
					  filePath:file,
					 //  header: {
						// "Content-Type": "multipart/form-data",
					 // },
					  name: 'file',
					  formData: {
					   'token': that.token
					  },
					  success: function (uploadFileRes) {
							uni.hideLoading();
							var data;
							try {
								data = JSON.parse(uploadFileRes.data);
							} catch (error) {
								uni.showToast({ title: '头像上传失败，请重试', icon: 'none' });
								return;
							}
							if(data.code==1 && data.data && data.data.url){
								that.avatar = data.data.url;
								that.avatarNew = data.data.url;
								localStorage.removeItem('toAvatar');
								uni.showToast({ title: '头像已上传，请点击保存', icon: 'none' });
							}else{
								uni.showToast({
									title: data.msg || "头像上传失败，请重试",
									icon: 'none'
								})
							}
						},fail:function(){
							uni.hideLoading();
							uni.showToast({ title: '头像上传失败，请重试', icon: 'none' });
						}
						
					   
					});
				  })
				  .catch(error => {
					console.error("失败"+error)
				  })
			}
		}
	}
</script>

<style>
.introduce-editor { flex: 1; min-width: 0; }
.introduce-editor textarea { width: 100%; min-height: 180rpx; line-height: 1.55; white-space: pre-wrap; }
.introduce-count { display: block; padding: 4rpx 0 12rpx; color: #87918e; font-size: 22rpx; text-align: right; }
.profile-picker { min-width: 180rpx; color: #34423f; text-align: right; }
.profile-picker .cuIcon-right { margin-left: 8rpx; color: #95a19e; }
.profile-clear { margin-left: 16rpx; color: #168573; font-size: 24rpx; }
.profile-visibility { display: flex; align-items: center; gap: 10rpx; margin-left: 22rpx; color: #788582; font-size: 24rpx; }
.profile-visibility switch { transform: scale(.72); transform-origin: right center; }
.campus-settings-page.campus-night .profile-picker { color: #e7ecea; }
.campus-settings-page.campus-night .profile-clear { color: #79b9a7; }
</style>
