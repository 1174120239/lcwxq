<template>
	<view class="userpost campus-subpage" :class="AppStyle" style="min-height: 100vh; background: #f6f6f6;">
		<view class="header" :style="[{height:CustomBar + 'px'}]">
			<view class="cu-bar bg-white" :style="{'height': CustomBar + 'px','padding-top':StatusBar + 'px'}">
				<view class="action" @tap="back">
					<text class="cuIcon-back"></text>
				</view>
				<view class="content text-bold" :style="[{top:StatusBar + 'px'}]">
					匿名动态配置
				</view>
				<view class="action">
				</view>
			</view>
		</view>
		<view :style="[{padding:NavBar + 'px 10px 0px 10px'}]"></view>

		<view class="margin-sm bg-white" style="border-radius: 20upx; padding: 10upx 20upx;">
			<view class="cu-form-group" style="border-bottom: 1upx solid #f0f0f0;">
				<view class="title">匿名账号UID</view>
				<input type="number" placeholder="请输入用于匿名发布的账号UID" v-model="fid"></input>
			</view>
			<view class="config-tip" v-if="configLoaded">
				<text v-if="anonymousExists && anonymousName!=''">当前账号：{{anonymousName}}（UID {{fid}}）</text>
				<text v-else-if="anonymousExists && anonymousName==''">当前账号 UID {{fid}} 存在</text>
				<text v-else-if="fid>0" class="text-red">该账号不存在，请先确认账号 UID</text>
			</view>
		</view>

		<view class="margin-sm bg-white" style="border-radius: 20upx; padding: 10upx 20upx;">
			<view class="config-row">
				<view class="config-label">匿名动态审核</view>
				<view class="config-options">
					<view class="config-option" :class="review==1?'active':''" @tap="review=1">开启审核</view>
					<view class="config-option" :class="review==0?'active':''" @tap="review=0">直接发布</view>
				</view>
			</view>
		</view>

		<view class="margin-sm margin-top-lg">
			<button class="cu-btn bg-blue round block" @tap="save">保存配置</button>
		</view>
		<view class="margin-sm config-tip">
			<text>说明：匿名动态使用专用账号发布，真实发布者只保存在服务端映射表，不对外展示。建议匿名账号不要设置管理员权限。</text>
		</view>
	</view>
</template>

<script>
	import {
		localStorage
	} from '../../../../js_sdk/mp-storage/mp-storage/index.js'
	export default {
		data() {
			return {
				StatusBar: this.StatusBar,
				CustomBar: this.CustomBar,
				NavBar: this.StatusBar + this.CustomBar,
				AppStyle: this.$store.state.AppStyle,
				token: "",
				fid: 0,
				review: 0,
				anonymousName: "",
				anonymousExists: false,
				configLoaded: false
			}
		},
		onLoad() {
			var that = this;
			// #ifdef APP-PLUS || MP
			that.NavBar = this.CustomBar;
			// #endif
			if (localStorage.getItem('userinfo')) {
				var userInfo = JSON.parse(localStorage.getItem('userinfo'));
				that.token = userInfo.token;
			}
			if (!that.token) {
				uni.showToast({
					title: "请先登录",
					icon: 'none'
				});
				setTimeout(function() {
					that.back();
				}, 1000);
				return false;
			}
			that.loadConfig();
		},
		methods: {
			back() {
				uni.navigateBack({
					delta: 1
				});
			},
			loadConfig() {
				var that = this;
				that.$Net.request({
					url: that.$API.anonymousAdminConfig(),
					data: {
						"token": that.token
					},
					header: {
						'Content-Type': 'application/x-www-form-urlencoded'
					},
					method: "get",
					dataType: 'json',
					success: function(res) {
						if (res.data.code == 1) {
							var config = res.data.data;
							that.fid = config.fid;
							that.review = config.review;
							that.anonymousName = config.anonymousName || "";
							that.anonymousExists = config.anonymousExists == true;
							that.configLoaded = true;
						} else {
							uni.showToast({
								title: res.data.msg || "无权查看匿名配置",
								icon: 'none'
							});
							var timer = setTimeout(function() {
								that.back();
								clearTimeout(timer);
							}, 1200);
						}
					},
					fail: function() {
						uni.showToast({
							title: "网络开小差了",
							icon: 'none'
						});
					}
				});
			},
			save() {
				var that = this;
				if (!that.fid || Number(that.fid) <= 0) {
					uni.showToast({
						title: "请填写匿名账号UID",
						icon: 'none'
					});
					return false;
				}
				uni.showLoading({
					title: "加载中"
				});
				that.$Net.request({
					url: that.$API.anonymousAdminConfig(),
					data: {
						"token": that.token,
						"fid": that.fid,
						"review": that.review
					},
					header: {
						'Content-Type': 'application/x-www-form-urlencoded'
					},
					method: "post",
					dataType: 'json',
					success: function(res) {
						setTimeout(function() {
							uni.hideLoading();
						}, 1000);
						uni.showToast({
							title: res.data.msg,
							icon: 'none'
						});
						if (res.data.code == 1) {
							var timer = setTimeout(function() {
								that.back();
								clearTimeout(timer);
							}, 1000);
						}
					},
					fail: function() {
						setTimeout(function() {
							uni.hideLoading();
						}, 1000);
						uni.showToast({
							title: "网络不太好哦，保存失败",
							icon: 'none'
						});
					}
				});
			}
		}
	}
</script>

<style>
	.config-tip {
		font-size: 24upx;
		color: #888;
		padding: 14upx 4upx;
		line-height: 1.6;
	}

	.config-row {
		display: flex;
		align-items: center;
		justify-content: space-between;
		padding: 22upx 0;
		border-bottom: 1upx solid #f0f0f0;
	}

	.config-label {
		font-size: 28upx;
		color: #333;
	}

	.config-options {
		display: flex;
	}

	.config-option {
		padding: 8upx 26upx;
		margin-left: 16upx;
		border-radius: 30upx;
		background: #f3f3f3;
		color: #666;
		font-size: 26upx;
	}

	.config-option.active {
		background: #169c92;
		color: #fff;
	}
</style>
