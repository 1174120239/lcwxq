<template>
	<view class="invitation-page campus-page" :class="{'campus-night': campusNight}">
		<view class="invitation-header" :style="{paddingTop: StatusBar + 'px'}">
			<view class="invitation-back" @tap="back"><text class="cuIcon-back"></text></view>
			<view class="invitation-title">邀请好友</view>
			<view class="invitation-header-space"></view>
		</view>

		<view class="invitation-shell">
			<view class="invitation-hero">
				<view class="invitation-hero-kicker">校园社区分享计划</view>
				<view class="invitation-hero-title">把喜欢的社区，分享给同学</view>
				<view class="invitation-hero-copy" v-if="inviter">
					<view class="inviter-line">
						<view class="inviter-avatar" :style="inviter.avatar ? {backgroundImage:'url('+inviter.avatar+')'} : {}"><text v-if="!inviter.avatar" class="cuIcon-my"></text></view>
						<text><text class="inviter-name">{{inviter.name}}</text> 邀请你加入论坛</text>
					</view>
				</view>
				<view class="invitation-hero-copy" v-else>记录校园生活，认识更多有趣的人。</view>
				<view class="reward-pills" v-if="config.enabled">
					<view><text class="cuIcon-moneybag"></text><text>邀请成功 +{{config.rewardPoints || 0}} 积分</text></view>
					<view><text class="cuIcon-like"></text><text>邀请成功 +{{config.rewardExperience || 0}} 经验</text></view>
				</view>
			</view>

			<view class="invitation-card dashboard-card" v-if="isLoggedIn && dashboard.inviteCode">
				<view class="card-heading"><view><text class="cuIcon-share"></text><text>我的邀请</text></view><text class="card-caption">每位新用户只奖励一次</text></view>
				<view class="code-box" @tap="copyText(dashboard.inviteCode, '邀请码已复制')">
					<text class="code-label">我的邀请码</text><text class="code-value">{{dashboard.inviteCode}}</text><text class="cuIcon-copy"></text>
				</view>
				<view class="summary-grid">
					<view><text>{{dashboard.invitationCount || 0}}</text><text>成功邀请</text></view>
					<view><text>{{dashboard.totalPoints || 0}}</text><text>累计积分</text></view>
					<view><text>{{dashboard.totalExperience || 0}}</text><text>累计经验</text></view>
				</view>
				<view class="action-row">
					<view class="primary-action" @tap="copyShareLink"><text class="cuIcon-link"></text><text>复制邀请链接</text></view>
					<view class="secondary-action" @tap="shareSystem"><text class="cuIcon-share"></text><text>系统分享</text></view>
				</view>
			</view>

			<view class="invitation-card join-card" v-else-if="!isLoggedIn">
				<view class="card-heading"><view><text class="cuIcon-people"></text><text>加入论坛</text></view></view>
				<view class="join-copy">注册后即可参与校园话题、发布动态，也能获得邀请奖励。</view>
				<view class="primary-action full-action" @tap="toRegister"><text class="cuIcon-right"></text><text>立即注册</text></view>
			</view>

			<view id="download-section" class="invitation-card download-card">
				<view class="card-heading"><view><text class="cuIcon-down"></text><text>下载与访问</text></view><text class="card-caption">选择适合你的使用方式</text></view>
				<view class="download-item" v-if="config.androidDownloadUrl">
					<view class="download-platform"><text class="platform-icon">A</text><view><text>Android</text><text class="download-url">{{config.androidDownloadUrl}}</text></view></view>
					<view class="download-actions"><view @tap="openDownload(config.androidDownloadUrl)">下载</view><view @tap="copyDownload(config.androidDownloadUrl)">复制链接</view></view>
				</view>
				<view class="download-item" v-if="config.iosDownloadUrl">
					<view class="download-platform"><text class="platform-icon ios">i</text><view><text>iPhone / iPad</text><text class="download-url">{{config.iosDownloadUrl}}</text></view></view>
					<view class="download-actions"><view @tap="openDownload(config.iosDownloadUrl)">下载</view><view @tap="copyDownload(config.iosDownloadUrl)">复制链接</view></view>
				</view>
				<view class="download-item">
					<view class="download-platform"><text class="platform-icon web">W</text><view><text>网页版</text><text class="download-url">{{webUrl}}</text></view></view>
					<view class="download-actions"><view @tap="openDownload(webUrl)">立即访问</view><view @tap="copyDownload(webUrl)">复制链接</view></view>
				</view>
				<view class="download-tip"><text class="cuIcon-info"></text><text>如果无法直接下载，请复制链接到浏览器打开</text></view>
			</view>

			<view class="invitation-empty" v-if="loaded && !config.enabled">邀请功能暂未开放</view>
		</view>
	</view>
</template>

<script>
	import { localStorage } from '../../js_sdk/mp-storage/mp-storage/index.js'
	import { applyCampusThemeShell, getCampusThemeMode, isDongchangfuNight, resolveCampusNight } from '@/utils/campusTheme.js'
	var API = require('../../utils/api')
	export default {
		data() {
			return {
				StatusBar: this.StatusBar,
				campusThemeMode: 'auto',
				campusThemeClock: Date.now(),
				campusThemeTimer: null,
				inviteCode: '',
				inviter: null,
				config: { enabled: true, rewardPoints: 0, rewardExperience: 0, androidDownloadUrl: '', iosDownloadUrl: '' },
				dashboard: {},
				loaded: false,
				focusDownload: false
			}
		},
		computed: {
			campusNight() { return resolveCampusNight(this.campusThemeMode, isDongchangfuNight(this.campusThemeClock)) },
			isLoggedIn() { return !!this.userToken() },
			webUrl() { return API.GetWebUrl() },
			shareLink() { return API.GetWebUrl() + '#/pages/user/invitation?invite=' + encodeURIComponent(this.dashboard.inviteCode || this.inviteCode) }
		},
		onLoad(query) {
			if (query && query.invite) this.inviteCode = String(query.invite).trim().toUpperCase()
			this.focusDownload = !!(query && String(query.download) === '1')
		},
		onReady() {
			this.scrollToDownload()
		},
		onShow() {
			if (uni.getStorageSync('invitationDownloadFocus')) {
				uni.removeStorageSync('invitationDownloadFocus')
				this.focusDownload = true
			}
			this.campusThemeMode = getCampusThemeMode()
			this.startThemeClock()
			this.loadPublic()
			if (this.userToken()) this.loadDashboard()
			this.scrollToDownload()
		},
		onHide() { this.stopThemeClock() },
		onUnload() { this.stopThemeClock() },
		methods: {
			userToken() {
				var user = localStorage.getItem('userinfo')
				if (!user) return ''
				try { return JSON.parse(user).token || '' } catch (e) { return '' }
			},
			startThemeClock() {
				this.stopThemeClock()
				this.campusThemeClock = Date.now()
				applyCampusThemeShell(this.campusThemeMode, this.campusThemeClock)
				var nextHour = (Math.floor(this.campusThemeClock / 3600000) + 1) * 3600000
				this.campusThemeTimer = setTimeout(() => this.startThemeClock(), nextHour - this.campusThemeClock + 120)
			},
			stopThemeClock() { if (this.campusThemeTimer) { clearTimeout(this.campusThemeTimer); this.campusThemeTimer = null } },
			back() { uni.navigateBack({ delta: 1 }) },
			loadPublic() {
				this.$Net.request({ url: API.invitationConfig(), method: 'get', data: { inviteCode: this.inviteCode }, dataType: 'json',
					success: (res) => {
						if (res.data.code !== 1) return
						this.config = res.data.data || this.config
						this.inviter = this.config.inviter || null
						this.loaded = true
					}, fail: () => { this.loaded = true }
				})
			},
			loadDashboard() {
				this.$Net.request({ url: API.invitationMe(), method: 'get', data: { token: this.userToken() }, dataType: 'json',
					success: (res) => { if (res.data.code === 1) { this.dashboard = res.data.data || {}; this.config = Object.assign({}, this.config, this.dashboard) } }
				})
			},
			toRegister() {
				var params = ['fromInvitation=1']
				if (this.inviteCode) params.unshift('invite=' + encodeURIComponent(this.inviteCode))
				uni.navigateTo({ url: '/pages/user/register?' + params.join('&') })
			},
			scrollToDownload() {
				if (!this.focusDownload) return
				this.$nextTick(() => {
					setTimeout(() => {
						if (!this.focusDownload) return
						this.focusDownload = false
						uni.pageScrollTo({ selector: '#download-section', duration: 300 })
					}, 80)
				})
			},
			copyShareLink() { this.copyText(this.shareLink, '邀请链接已复制，请分享给好友') },
			copyDownload(url) { this.copyText(url, '下载链接已复制，请粘贴到浏览器打开') },
			copyText(text, message) {
				if (!text) return
				uni.setClipboardData({ data: text, success: () => uni.showToast({ title: message, icon: 'none' }) })
			},
			openDownload(url) {
				if (!url) return
				// #ifdef APP-PLUS
				plus.runtime.openURL(url)
				// #endif
				// #ifdef H5
				window.open(url, '_blank')
				// #endif
			},
			shareSystem() {
				var url = this.shareLink
				var title = '邀请你加入校园社区'
				var text = '和我一起记录校园生活、参与校园话题。'
				// #ifdef APP-PLUS
				if (typeof plus !== 'undefined' && plus.share && plus.share.sendWithSystem) {
					plus.share.sendWithSystem({ type: 'text', content: title + '\n' + text, href: url },
						() => uni.showToast({ title: '已打开系统分享', icon: 'none' }),
						() => uni.showToast({ title: '分享已取消', icon: 'none' }))
					return
				}
				// #endif
				// #ifdef H5
				if (typeof navigator !== 'undefined' && navigator.share) {
					navigator.share({ title: title, text: text, url: url }).catch((error) => {
						if (!error || error.name !== 'AbortError') this.copyShareLink()
					})
					return
				}
				// #endif
				this.copyShareLink()
			}
		}
	}
</script>

<style>
	.invitation-page { min-height: 100vh; background: #f3f6f4; color: #243033; }
	.invitation-page.campus-night { background: #151b1d; color: #f2f6f4; }
	.invitation-header { height: 96rpx; display:flex; align-items:center; justify-content:space-between; padding-left: 28rpx; padding-right: 28rpx; background: rgba(255,255,255,.88); border-bottom: 1px solid rgba(36,48,51,.08); position:relative; z-index:2; }
	.campus-night .invitation-header { background: rgba(25,32,34,.92); border-color: rgba(255,255,255,.08); }
	.invitation-back { width: 64rpx; height:64rpx; display:flex; align-items:center; justify-content:center; font-size: 40rpx; }
	.invitation-title { font-size: 34rpx; font-weight: 700; letter-spacing: 1rpx; }
	.invitation-header-space { width:64rpx; }
	.invitation-shell { max-width: 920rpx; margin: 0 auto; padding: 28rpx 28rpx 90rpx; }
	.invitation-hero { position:relative; overflow:hidden; padding: 54rpx 40rpx 42rpx; border-radius: 28rpx; color:#fff; background:#2d5755; box-shadow: 0 20rpx 45rpx rgba(23,51,52,.18); }
	.campus-night .invitation-hero { background:#203f40; }
	.invitation-hero-glow { display:none; }
	.invitation-hero-kicker { position:relative; font-size: 23rpx; opacity:.78; letter-spacing: 3rpx; }
	.invitation-hero-title { position:relative; margin-top: 18rpx; font-size: 48rpx; line-height:1.24; font-weight: 800; }
	.invitation-hero-copy { position:relative; margin-top: 22rpx; font-size: 27rpx; line-height:1.65; color: rgba(255,255,255,.82); }
	.inviter-line { display:flex; align-items:center; gap: 16rpx; }
	.inviter-avatar { width: 64rpx; height:64rpx; border-radius:50%; background:#edf5f0 center/cover; display:flex; align-items:center; justify-content:center; color:#2e6561; }
	.inviter-name { color:#ffe39a; font-weight:700; }
	.reward-pills { position:relative; display:flex; flex-wrap:wrap; gap:14rpx; margin-top:30rpx; }
	.reward-pills view { display:flex; align-items:center; gap:10rpx; padding: 12rpx 18rpx; border-radius: 99rpx; background: rgba(255,255,255,.13); font-size: 23rpx; }
	.invitation-card { margin-top: 24rpx; padding: 30rpx; border-radius: 22rpx; background:#fff; border: 1px solid rgba(36,48,51,.08); box-shadow: 0 12rpx 30rpx rgba(35,60,60,.06); }
	.campus-night .invitation-card { background:#20282a; border-color:rgba(255,255,255,.08); box-shadow:none; }
	.card-heading { display:flex; justify-content:space-between; align-items:center; gap:20rpx; font-size:30rpx; font-weight:700; }
	.card-heading view { display:flex; align-items:center; gap:12rpx; }
	.card-heading view text:first-child { color:#d29f31; }
	.card-caption { font-size:22rpx; color:#7c8987; font-weight:400; }
	.campus-night .card-caption { color:#a5b0ad; }
	.code-box { display:flex; align-items:center; gap:16rpx; margin-top:28rpx; padding: 22rpx 24rpx; border-radius:14rpx; background:#eef5f1; color:#244c4b; }
	.campus-night .code-box { background:#293638; color:#dbeee6; }
	.code-label { font-size:23rpx; color:#72827d; }
	.code-value { flex:1; font-size:38rpx; font-weight:800; letter-spacing:4rpx; }
	.summary-grid { display:grid; grid-template-columns:repeat(3,1fr); margin-top:30rpx; }
	.summary-grid view { display:flex; flex-direction:column; align-items:center; gap:8rpx; border-right:1px solid rgba(36,48,51,.1); }
	.summary-grid view:last-child { border-right:0; }
	.summary-grid text:first-child { font-size:38rpx; font-weight:800; color:#2f746b; }
	.campus-night .summary-grid text:first-child { color:#f0c65d; }
	.summary-grid text:last-child { font-size:22rpx; color:#788681; }
	.action-row { display:flex; gap:14rpx; margin-top:30rpx; }
	.primary-action,.secondary-action { flex:1; min-height:78rpx; border-radius:14rpx; display:flex; align-items:center; justify-content:center; gap:10rpx; font-size:26rpx; font-weight:700; }
	.primary-action { color:#fff; background:#2f746b; }
	.secondary-action { color:#2f746b; border:1px solid #2f746b; }
	.campus-night .secondary-action { color:#afd5c3; border-color:#7ab29d; }
	.full-action { margin-top:28rpx; }
	.join-copy { margin-top:24rpx; color:#72807c; font-size:25rpx; line-height:1.7; }
	.campus-night .join-copy { color:#b3bfba; }
	.download-item { display:flex; align-items:center; justify-content:space-between; gap:18rpx; padding:22rpx 0; border-bottom:1px solid rgba(36,48,51,.08); }
	.campus-night .download-item { border-color:rgba(255,255,255,.08); }
	.download-item:last-of-type { border-bottom:0; }
	.download-platform { display:flex; align-items:center; gap:15rpx; min-width:0; }
	.download-platform > view { display:flex; flex-direction:column; gap:6rpx; min-width:0; font-size:26rpx; font-weight:700; }
	.platform-icon { width:54rpx; height:54rpx; border-radius:15rpx; display:flex; align-items:center; justify-content:center; background:#2f746b; color:#fff; font-size:30rpx; font-weight:800; }
	.platform-icon.ios { background:#333c42; }
	.platform-icon.web { background:#b9822d; }
	.download-url { max-width:310rpx; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; color:#84928e; font-size:20rpx; font-weight:400; }
	.download-actions { display:flex; gap:12rpx; flex-shrink:0; }
	.download-actions view { padding:12rpx 16rpx; border-radius:10rpx; color:#2f746b; border:1px solid #2f746b; font-size:22rpx; }
	.download-actions view:first-child { color:#fff; background:#2f746b; }
	.campus-night .download-actions view { color:#afd5c3; border-color:#7ab29d; }
	.campus-night .download-actions view:first-child { color:#17201f; background:#9ed2b9; }
	.download-tip { display:flex; align-items:center; gap:10rpx; margin-top:18rpx; color:#9b6d2c; font-size:22rpx; line-height:1.5; }
	.invitation-empty { padding:90rpx 20rpx; text-align:center; color:#7b8986; font-size:25rpx; }
	@media screen and (max-width: 380px) { .invitation-shell { padding-left:20rpx; padding-right:20rpx; } .invitation-hero { padding-left:28rpx; padding-right:28rpx; } .invitation-hero-title { font-size:42rpx; } .download-item { align-items:flex-start; flex-direction:column; } .download-actions { width:100%; } .download-actions view { flex:1; text-align:center; } }
</style>
