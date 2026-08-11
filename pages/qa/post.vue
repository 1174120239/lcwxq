<template>
	<view class="qa-post-page" :class="{'qa-post-night': campusNight}">
		<view class="qa-post-nav" :style="{paddingTop: StatusBar + 'px'}">
			<view class="qa-post-nav-bar">
				<view class="qa-post-nav-button" @tap="back"><text class="cuIcon-close"></text></view>
				<text class="qa-post-nav-title">提出问题</text>
				<view class="qa-post-nav-placeholder"></view>
			</view>
		</view>

		<view class="qa-post-form" :style="{paddingTop: (StatusBar + 56) + 'px'}">
			<view class="qa-post-field">
				<view class="qa-post-field-head">
					<text class="qa-post-label">问题标题</text>
					<text class="qa-post-count">{{title.length}}/160</text>
				</view>
				<textarea class="qa-post-title-input" v-model="title" maxlength="160" auto-height
					placeholder="清楚地写下你想问的问题"></textarea>
				<text class="qa-post-help">标题至少 4 个字</text>
			</view>

			<view class="qa-post-field">
				<view class="qa-post-field-head">
					<text class="qa-post-label">问题说明</text>
					<text class="qa-post-count">{{description.length}}/5000</text>
				</view>
				<textarea class="qa-post-description-input" v-model="description" maxlength="5000"
					placeholder="补充背景、已经尝试过的方法或希望得到的答案"></textarea>
			</view>

			<view class="qa-post-field">
				<view class="qa-post-field-head">
					<text class="qa-post-label">话题</text>
					<text class="qa-post-count">{{topic.length}}/80</text>
				</view>
				<input class="qa-post-topic-input" v-model="topic" maxlength="80" placeholder="例如：校园生活（选填）" />
			</view>

			<view class="qa-post-review-tip">
				<text class="cuIcon-info"></text>
				<text>问题提交后将进入审核，通过后显示在提问区。</text>
			</view>

			<button class="qa-post-submit" :disabled="!canSubmit" @tap="submitQuestion">
				{{submitting ? '提交中' : '提交问题'}}
			</button>
		</view>
	</view>
</template>

<script>
	import { localStorage } from '../../js_sdk/mp-storage/mp-storage/index.js'
	import { applyCampusThemeShell, getCampusThemeMode, isDongchangfuNight, resolveCampusNight } from '@/utils/campusTheme.js'

	export default {
		data() {
			return {
				StatusBar: this.StatusBar,
				campusThemeMode: 'auto',
				themeClock: Date.now(),
				token: '',
				title: '',
				description: '',
				topic: '',
				submitting: false,
				submitted: false,
				loginPrompted: false
			}
		},
		computed: {
			campusNight() {
				return resolveCampusNight(this.campusThemeMode, isDongchangfuNight(this.themeClock))
			},
			canSubmit() {
				return !this.submitting && !this.submitted && this.title.trim().length >= 4
			}
		},
		onLoad() {
			this.loadTheme()
		},
		onShow() {
			this.token = localStorage.getItem('token') || ''
			if (!this.token && !this.loginPrompted) {
				this.loginPrompted = true
				uni.showToast({ title: '请先登录后提问', icon: 'none' })
				setTimeout(() => uni.navigateTo({ url: '/pages/user/login' }), 500)
			}
			// #ifdef APP-PLUS
			plus.navigator.setStatusBarStyle(this.campusNight ? 'light' : 'dark')
			// #endif
		},
		methods: {
			loadTheme() {
				this.campusThemeMode = getCampusThemeMode()
				this.themeClock = Date.now()
				applyCampusThemeShell(this.campusThemeMode, this.themeClock)
			},
			back() {
				uni.navigateBack({ delta: 1 })
			},
			submitQuestion() {
				if (!this.canSubmit) return
				if (!this.token) {
					uni.showToast({ title: '请先登录', icon: 'none' })
					return
				}
				this.submitting = true
				this.$Net.request({
					url: this.$API.qaQuestionAdd(),
					data: {
						token: this.token,
						params: JSON.stringify({
							title: this.title.trim(),
							description: this.description.trim(),
							topic: this.topic.trim()
						})
					},
					header: { 'Content-Type': 'application/x-www-form-urlencoded' },
					method: 'post',
					dataType: 'json',
					success: (res) => {
						if (!res.data || res.data.code != 1) {
							uni.showToast({ title: res.data && res.data.msg ? res.data.msg : '提交失败', icon: 'none' })
							return
						}
						this.submitted = true
						uni.showToast({ title: '问题已提交，等待审核', icon: 'success', duration: 1800 })
						setTimeout(() => uni.navigateBack({ delta: 1 }), 1800)
					},
					fail: () => uni.showToast({ title: '网络异常，请稍后重试', icon: 'none' }),
					complete: () => { this.submitting = false }
				})
			}
		}
	}
</script>

<style scoped>
	.qa-post-page {
		min-height: 100vh;
		background: #f5f7f8;
		color: #253632;
	}

	.qa-post-nav {
		position: fixed;
		z-index: 30;
		top: 0;
		left: 0;
		right: 0;
		border-bottom: 1rpx solid #e4e9e8;
		background: rgba(255, 255, 255, .98);
	}

	.qa-post-nav-bar {
		display: grid;
		grid-template-columns: 88rpx minmax(0, 1fr) 88rpx;
		align-items: center;
		height: 112rpx;
		padding: 0 18rpx;
		box-sizing: border-box;
	}

	.qa-post-nav-button {
		display: flex;
		align-items: center;
		justify-content: center;
		width: 72rpx;
		height: 72rpx;
		font-size: 36rpx;
	}

	.qa-post-nav-title {
		font-size: 32rpx;
		font-weight: 700;
		text-align: center;
	}

	.qa-post-form {
		width: 100%;
		max-width: 760px;
		margin: 0 auto;
		padding-right: 24rpx;
		padding-bottom: calc(64rpx + env(safe-area-inset-bottom));
		padding-left: 24rpx;
		box-sizing: border-box;
	}

	.qa-post-field {
		margin-top: 24rpx;
		padding: 26rpx;
		border: 1rpx solid #e1e7e5;
		border-radius: 8rpx;
		background: #fff;
	}

	.qa-post-field-head {
		display: flex;
		align-items: center;
		justify-content: space-between;
		margin-bottom: 18rpx;
	}

	.qa-post-label {
		font-size: 28rpx;
		font-weight: 700;
	}

	.qa-post-count,
	.qa-post-help {
		font-size: 22rpx;
		color: #8b9793;
	}

	.qa-post-help {
		display: block;
		margin-top: 12rpx;
	}

	.qa-post-title-input,
	.qa-post-description-input,
	.qa-post-topic-input {
		width: 100%;
		font-size: 28rpx;
		line-height: 1.55;
		color: #253632;
		box-sizing: border-box;
	}

	.qa-post-title-input {
		min-height: 88rpx;
	}

	.qa-post-description-input {
		height: 340rpx;
	}

	.qa-post-topic-input {
		height: 72rpx;
	}

	.qa-post-review-tip {
		display: flex;
		align-items: center;
		gap: 12rpx;
		margin: 24rpx 4rpx;
		font-size: 23rpx;
		color: #6f7e79;
	}

	.qa-post-submit {
		display: flex;
		align-items: center;
		justify-content: center;
		height: 88rpx;
		border-radius: 8rpx;
		background: #238267;
		font-size: 29rpx;
		font-weight: 700;
		color: #fff;
	}

	.qa-post-submit::after {
		border: 0;
	}

	.qa-post-submit[disabled] {
		background: #b9c5c1;
		color: #eef2f0;
	}

	.qa-post-night {
		background: #171d1e;
		color: #edf2f0;
	}

	.qa-post-night .qa-post-nav,
	.qa-post-night .qa-post-field {
		border-color: #303a3b;
		background: #1f2728;
	}

	.qa-post-night .qa-post-title-input,
	.qa-post-night .qa-post-description-input,
	.qa-post-night .qa-post-topic-input {
		color: #edf2f0;
	}

	.qa-post-night .qa-post-count,
	.qa-post-night .qa-post-help,
	.qa-post-night .qa-post-review-tip {
		color: #96a39f;
	}
</style>
