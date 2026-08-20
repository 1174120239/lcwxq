<template>
	<view class="qa-post-page" :class="{'qa-post-night': campusNight}">
		<view class="qa-post-nav" :style="{paddingTop: StatusBar + 'px'}">
			<view class="qa-post-nav-bar">
				<view class="qa-post-nav-button" @tap="back"><text class="cuIcon-close"></text></view>
				<text class="qa-post-nav-title">提出问题</text>
				<view class="qa-post-nav-submit" :class="{'is-disabled': !canSubmit}" @tap="submitQuestion">{{submitting ? '提交中' : '提交'}}</view>
			</view>
		</view>

		<view class="qa-post-form" :style="{paddingTop: (StatusBar + 56) + 'px'}">
			<view class="qa-post-intro">
				<view class="qa-post-intro-icon"><text class="cuIcon-question"></text></view>
				<view class="qa-post-intro-copy">
					<text class="qa-post-intro-title">把问题说清楚，更容易得到回答</text>
					<text class="qa-post-intro-desc">标题写结论，说明补充背景；提交后会先进入审核。</text>
				</view>
			</view>

			<view class="qa-post-field" :class="{'is-invalid': title.trim().length > 0 && title.trim().length < 4}">
				<view class="qa-post-field-head">
					<text class="qa-post-label">问题标题</text>
					<text class="qa-post-count">{{title.trim().length}}/160</text>
				</view>
				<textarea class="qa-post-title-input" v-model="title" maxlength="160" auto-height
					placeholder="清楚地写下你想问的问题"></textarea>
				<text class="qa-post-help" :class="{'is-error': title.trim().length > 0 && title.trim().length < 4}">{{title.trim().length > 0 && title.trim().length < 4 ? '还需要输入 '+(4-title.trim().length)+' 个字' : '标题至少 4 个字'}}</text>
			</view>

			<view class="qa-post-field qa-post-description-field">
				<view class="qa-post-field-head">
					<text class="qa-post-label">问题说明</text>
					<text class="qa-post-count">{{description.length}}/5000</text>
				</view>
				<rich-composer v-model="description" :maxlength="5000" :night="campusNight"
					placeholder="补充背景、已经尝试过的方法或希望得到的答案" :show-status="false" @media="chooseImage"></rich-composer>
				<view class="qa-post-media" v-if="imageUrls.length">
					<view class="qa-post-media-item" v-for="(url,index) in imageUrls" :key="url"><image :src="url" mode="aspectFill"></image><text class="media-index">{{index+1}}</text><text class="media-remove cuIcon-close" @tap="removeImage(index)"></text><view class="media-move"><text class="cuIcon-back" @tap="moveImage(index,-1)"></text><text class="cuIcon-right" @tap="moveImage(index,1)"></text></view></view>
				</view>
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
				<text>提交后进入审核，通过后显示在提问区；审核期间可以放心离开页面。</text>
			</view>
			<view class="qa-post-submit-state" v-if="submitting">
				<view class="qa-post-submit-spinner"></view><text>正在提交问题，请稍候</text>
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
	import RichComposer from '@/components/rich-composer/rich-composer'

	export default {
		components: { RichComposer },
		data() {
			return {
				StatusBar: this.StatusBar,
				campusThemeMode: 'auto',
				themeClock: Date.now(),
				token: '',
				title: '',
				description: '',
				imageUrls: [],
				uploadingImages: false,
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
				return !this.submitting && !this.submitted && !this.uploadingImages && this.title.trim().length >= 4
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
			chooseImage() {
				if (!this.token || this.uploadingImages || this.imageUrls.length >= 9) return
				uni.chooseImage({ count: 9 - this.imageUrls.length, sizeType: ['compressed'], sourceType: ['album', 'camera'], success: (res) => {
					this.uploadingImages = true
					Promise.all((res.tempFilePaths || []).map(path => new Promise((resolve, reject) => uni.uploadFile({ url: this.$API.upload(), filePath: path, name: 'file', formData: { token: this.token }, success: r => { try { const body = JSON.parse(r.data); body.code === 1 && body.data && body.data.url ? resolve(body.data.url) : reject(body.msg || '上传失败') } catch (e) { reject(e) } }, fail: reject }))).then(urls => { this.imageUrls = this.imageUrls.concat(urls) }).catch(() => uni.showToast({ title: '部分图片上传失败', icon: 'none' })).then(() => { this.uploadingImages = false })
				})
			},
			removeImage(index) { this.imageUrls.splice(index, 1) },
			moveImage(index, offset) { const target = index + offset; if (target < 0 || target >= this.imageUrls.length) return; const next = this.imageUrls.slice(); const item = next.splice(index, 1)[0]; next.splice(target, 0, item); this.imageUrls = next },
			loadTheme() {
				this.campusThemeMode = getCampusThemeMode()
				this.themeClock = Date.now()
				applyCampusThemeShell(this.campusThemeMode, this.themeClock)
			},
			back() {
				uni.navigateBack({ delta: 1 })
			},
			submitQuestion() {
				if (this.submitting || !this.canSubmit) return
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
								topic: this.topic.trim(),
								imageUrls: this.imageUrls
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
						var published = res.data.data && Number(res.data.data.status) === 1
						uni.showToast({ title: published ? 'AI 审核通过，已发布' : '已提交，等待人工复核', icon: 'success', duration: 1800 })
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
		animation: qa-post-page-in 260ms ease both;
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

	.qa-post-nav-submit {
		display: flex;
		align-items: center;
		justify-content: center;
		width: 86rpx;
		height: 56rpx;
		border-radius: 999rpx;
		background: #238267;
		color: #fff;
		font-size: 24rpx;
		font-weight: 700;
		transition: opacity 180ms ease, transform 180ms ease;
	}

	.qa-post-nav-submit:active {
		transform: scale(.96);
	}

	.qa-post-nav-submit.is-disabled {
		background: #d8e0dd;
		color: #9aa6a1;
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

	.qa-post-intro {
		display: flex;
		align-items: center;
		gap: 18rpx;
		margin-top: 20rpx;
		padding: 22rpx 24rpx;
		border: 1rpx solid #dceae5;
		border-radius: 14rpx;
		background: linear-gradient(135deg, #f0faf6, #f8fbfa);
		animation: qa-post-rise 300ms 40ms ease both;
	}

	.qa-post-intro-icon {
		display: flex;
		align-items: center;
		justify-content: center;
		flex: 0 0 auto;
		width: 66rpx;
		height: 66rpx;
		border-radius: 18rpx;
		background: #dcefe8;
		color: #238267;
		font-size: 34rpx;
	}

	.qa-post-intro-copy {
		display: flex;
		flex: 1;
		min-width: 0;
		flex-direction: column;
		gap: 6rpx;
	}

	.qa-post-intro-title {
		color: #2e5147;
		font-size: 25rpx;
		font-weight: 700;
	}

	.qa-post-intro-desc {
		color: #72847d;
		font-size: 22rpx;
		line-height: 1.45;
	}

	.qa-post-field {
		margin-top: 24rpx;
		padding: 26rpx;
		border: 1rpx solid #e1e7e5;
		border-radius: 8rpx;
		background: #fff;
		animation: qa-post-rise 300ms ease both;
	}

	.qa-post-field.is-invalid {
		border-color: #e6a19a;
		background: #fffaf9;
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

	.qa-post-help.is-error {
		color: #c45e58;
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
	.qa-post-media { display:grid; grid-template-columns:repeat(3,1fr); gap:12rpx; margin-top:16rpx; }
	.qa-post-media-item { position:relative; aspect-ratio:1; overflow:hidden; border-radius:10rpx; background:#edf2f0; }
	.qa-post-media-item image { width:100%; height:100%; }
	.qa-post-media-item .media-index { position:absolute; left:8rpx; top:8rpx; padding:2rpx 8rpx; border-radius:8rpx; background:rgba(20,35,31,.72); color:#fff; font-size:20rpx; }
	.qa-post-media-item .media-remove { position:absolute; right:8rpx; top:8rpx; color:#fff; font-size:24rpx; }
	.qa-post-media-item .media-move { position:absolute; bottom:8rpx; left:8rpx; display:flex; gap:6rpx; }
	.qa-post-media-item .media-move text { display:flex; align-items:center; justify-content:center; width:42rpx; height:42rpx; border-radius:50%; background:rgba(20,35,31,.72); color:#fff; }

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

	.qa-post-submit-state {
		display: flex;
		align-items: center;
		justify-content: center;
		gap: 12rpx;
		margin: -8rpx 0 20rpx;
		color: #4b7569;
		font-size: 23rpx;
	}

	.qa-post-submit-spinner {
		width: 26rpx;
		height: 26rpx;
		border: 4rpx solid #cfe2db;
		border-top-color: #238267;
		border-radius: 50%;
		animation: qa-post-spin 800ms linear infinite;
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

	.qa-post-night .qa-post-intro {
		border-color: #2f4840;
		background: linear-gradient(135deg, #21332e, #1e2928);
	}

	.qa-post-night .qa-post-intro-icon {
		background: #2b453c;
		color: #9dd2be;
	}

	.qa-post-night .qa-post-intro-title {
		color: #d7ebe2;
	}

	.qa-post-night .qa-post-intro-desc,
	.qa-post-night .qa-post-submit-state {
		color: #9aafa7;
	}

	.qa-post-night .qa-post-field.is-invalid {
		border-color: #8f5b57;
		background: #2a2323;
	}

	.qa-post-night .qa-post-help.is-error {
		color: #f0a59e;
	}

	.qa-post-night .qa-post-nav-submit.is-disabled {
		background: #37423f;
		color: #7c8984;
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

	@keyframes qa-post-page-in {
		from { opacity: 0; }
		to { opacity: 1; }
	}

	@keyframes qa-post-rise {
		from { opacity: 0; transform: translateY(12rpx); }
		to { opacity: 1; transform: translateY(0); }
	}

	@keyframes qa-post-spin {
		to { transform: rotate(360deg); }
	}
</style>
