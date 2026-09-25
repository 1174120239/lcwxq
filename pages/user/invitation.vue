<template>
	<view class="invitation-page campus-page" :class="{'campus-night': campusNight, 'motion-active': motionActive}">
		<view class="invitation-header" :style="{paddingTop: StatusBar + 'px'}">
			<view class="invitation-back" @tap="back"><text class="cuIcon-back"></text></view>
			<view class="invitation-title">邀请好友</view>
			<view class="invitation-header-space"></view>
		</view>

		<view class="invitation-shell">
			<view class="invitation-hero">
				<view class="invitation-hero-topline"><view class="invitation-hero-mark"><text class="cuIcon-share"></text></view><text class="invitation-hero-kicker">校园社区分享计划</text></view>
				<view class="invitation-hero-title">把喜欢的社区，分享给同学</view>
				<view class="invitation-hero-copy" v-if="inviter">
					<view class="inviter-line">
						<view class="inviter-avatar" :style="inviter.avatar ? {backgroundImage:'url('+inviter.avatar+')'} : {}"><text v-if="!inviter.avatar" class="cuIcon-my"></text></view>
						<text><text class="inviter-name">{{inviter.name}}</text> 邀请你加入论坛</text>
					</view>
				</view>
				<view class="invitation-hero-copy" v-else>记录校园生活，认识更多有趣的人。</view>
				<view class="invite-invalid" v-if="inviteCode && loaded && !config.validInvite"><text class="cuIcon-warn"></text><text>邀请码无效，请确认邀请链接</text></view>
				<view class="reward-pills" v-if="config.enabled">
					<view><text class="cuIcon-moneybag"></text><text>邀请成功 +{{config.rewardPoints || 0}} 积分</text></view>
					<view><text class="cuIcon-like"></text><text>邀请成功 +{{config.rewardExperience || 0}} 经验</text></view>
				</view>
			</view>

			<view class="invitation-card dashboard-card" v-if="isLoggedIn && dashboard.inviteCode">
				<view class="card-heading"><view><text class="cuIcon-share"></text><text>我的邀请</text></view><text class="card-caption">每位新用户只奖励一次</text></view>
				<view class="code-box" :class="{'is-copying': copyingCode}" @tap="copyInvitationCode">
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
				<view class="invite-history" v-if="dashboard.invitees && dashboard.invitees.length">
					<view class="history-heading"><text>最近邀请</text><text>奖励已到账</text></view>
					<view class="history-item" v-for="item in dashboard.invitees.slice(0, 3)" :key="item.uid + '-' + item.created">
						<view class="history-avatar" :style="item.avatar ? {backgroundImage:'url('+item.avatar+')'} : {}"><text v-if="!item.avatar" class="cuIcon-my"></text></view>
						<view class="history-main"><text class="history-name">{{item.name || '新用户'}}</text><text class="history-time">{{formatInviteTime(item.created)}}</text></view>
						<view class="history-reward">+{{item.rewardPoints || 0}} 积分</view>
					</view>
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
				focusDownload: false,
				motionActive: false,
				copyingCode: false
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
			this.restartMotion()
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
			restartMotion() {
				this.motionActive = false
				this.$nextTick(() => setTimeout(() => { this.motionActive = true }, 30))
			},
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
			copyInvitationCode() {
				this.copyingCode = true
				this.copyText(this.dashboard.inviteCode, '邀请码已复制')
				setTimeout(() => { this.copyingCode = false }, 420)
			},
			copyShareLink() { this.copyText(this.shareLink, '邀请链接已复制，请分享给好友') },
			formatInviteTime(timestamp) {
				if (!timestamp) return '刚刚'
				var date = new Date(Number(timestamp) * 1000)
				if (isNaN(date.getTime())) return '刚刚'
				var month = date.getMonth() + 1
				var day = date.getDate()
				return month + '月' + day + '日'
			},
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
	.invitation-page { min-height: 100vh; background: var(--campus-bg, #f5f8f7); color: var(--campus-text, #243633); }
	.invitation-page.campus-night { background: var(--campus-night-bg, #15191b); color: var(--campus-night-text, #edf3f0); }
	.invitation-header { height: 96rpx; display:flex; align-items:center; justify-content:space-between; padding-left: 28rpx; padding-right: 28rpx; background: var(--campus-surface, #fff); border-bottom: 1px solid var(--campus-border, #dde8e5); position:relative; z-index:2; }
	.campus-night .invitation-header { background: var(--campus-night-surface, #202527); border-color: var(--campus-night-border, rgba(226,234,231,.1)); }
	.invitation-back { width: 64rpx; height:64rpx; display:flex; align-items:center; justify-content:center; border-radius:20rpx; font-size: 40rpx; color: var(--campus-text, #243633); transition: background-color 180ms ease, transform 180ms ease; }
	.invitation-back:active { background: var(--campus-primary-soft, #e6f3f1); transform: translateX(-4rpx); }
	.campus-night .invitation-back { color: var(--campus-night-text, #edf3f0); }
	.campus-night .invitation-back:active { background: var(--campus-night-input, #293032); }
	.invitation-title { font-size: 34rpx; font-weight: 700; letter-spacing: 1rpx; }
	.invitation-header-space { width:64rpx; }
	.invitation-shell { max-width: 920rpx; margin: 0 auto; padding: 28rpx 28rpx 90rpx; }
	.invitation-hero { position:relative; overflow:hidden; padding: 42rpx 40rpx 42rpx; border-radius: 28rpx; color:#fff; background: var(--campus-primary, #237c74); box-shadow: 0 16rpx 30rpx rgba(35, 89, 82, .16); }
	.campus-night .invitation-hero { background:#24534f; box-shadow: 0 16rpx 30rpx rgba(0,0,0,.2); }
	.invitation-hero-topline { position:relative; z-index:1; display:flex; align-items:center; gap:14rpx; }
	.invitation-hero-mark { width:48rpx; height:48rpx; display:flex; align-items:center; justify-content:center; border:1px solid rgba(255,255,255,.3); border-radius:16rpx; background:rgba(255,255,255,.12); font-size:26rpx; }
	.invitation-hero-kicker { position:relative; font-size: 23rpx; opacity:.82; letter-spacing: 2rpx; }
	.invitation-hero-title { position:relative; margin-top: 18rpx; font-size: 48rpx; line-height:1.24; font-weight: 800; }
	.invitation-hero-copy { position:relative; margin-top: 22rpx; font-size: 27rpx; line-height:1.65; color: rgba(255,255,255,.82); }
	.inviter-line { display:flex; align-items:center; gap: 16rpx; }
	.inviter-avatar { width: 64rpx; height:64rpx; border-radius:50%; background:#edf5f0 center/cover; display:flex; align-items:center; justify-content:center; color:#2e6561; }
	.inviter-name { color:#ffe39a; font-weight:700; }
	.invite-invalid { position:relative; display:flex; align-items:center; gap:10rpx; margin-top:20rpx; color:#ffe0b2; font-size:23rpx; }
	.reward-pills { position:relative; display:flex; flex-wrap:wrap; gap:14rpx; margin-top:30rpx; }
	.reward-pills view { display:flex; align-items:center; gap:10rpx; padding: 12rpx 18rpx; border-radius: 99rpx; background: rgba(255,255,255,.13); font-size: 23rpx; }
	.invitation-card { margin-top: 24rpx; padding: 30rpx; border-radius: 24rpx; background: var(--campus-surface, #fff); border: 1px solid var(--campus-border, #dde8e5); box-shadow: 0 10rpx 24rpx rgba(44, 74, 78, .06); }
	.campus-night .invitation-card { background: var(--campus-night-surface, #202527); border-color: var(--campus-night-border, rgba(226,234,231,.1)); box-shadow:none; }
	.card-heading { display:flex; justify-content:space-between; align-items:center; gap:20rpx; font-size:30rpx; font-weight:700; }
	.card-heading view { display:flex; align-items:center; gap:12rpx; }
	.card-heading view text:first-child { color:var(--campus-primary, #237c74); }
	.card-caption { font-size:22rpx; color:var(--campus-muted, #738581); font-weight:400; }
	.campus-night .card-heading view text:first-child { color:var(--campus-primary, #6ebaae); }
	.campus-night .card-caption { color:var(--campus-night-muted, #a9b5b0); }
	.code-box { display:flex; align-items:center; gap:16rpx; margin-top:28rpx; padding: 22rpx 24rpx; border-radius:16rpx; background:var(--campus-primary-soft, #e6f3f1); color:var(--campus-primary, #237c74); transition: transform 180ms ease, background-color 180ms ease; }
	.code-box:active { transform:scale(.985); }
	.code-box.is-copying { background:#d5eee7; }
	.campus-night .code-box { background:var(--campus-night-input, #293032); color:var(--campus-primary, #6ebaae); }
	.campus-night .code-box.is-copying { background:#31514b; }
	.code-label { font-size:23rpx; color:var(--campus-muted, #738581); }
	.code-value { flex:1; font-size:38rpx; font-weight:800; letter-spacing:4rpx; }
	.summary-grid { display:grid; grid-template-columns:repeat(3,1fr); margin-top:30rpx; }
	.summary-grid view { display:flex; flex-direction:column; align-items:center; gap:8rpx; border-right:1px solid var(--campus-border, #dde8e5); }
	.summary-grid view:last-child { border-right:0; }
	.summary-grid text:first-child { font-size:38rpx; font-weight:800; color:var(--campus-primary, #237c74); }
	.campus-night .summary-grid text:first-child { color:var(--campus-primary, #6ebaae); }
	.summary-grid text:last-child { font-size:22rpx; color:var(--campus-muted, #738581); }
	.action-row { display:flex; gap:14rpx; margin-top:30rpx; }
	.primary-action,.secondary-action { flex:1; min-height:78rpx; border-radius:14rpx; display:flex; align-items:center; justify-content:center; gap:10rpx; font-size:26rpx; font-weight:700; }
	.primary-action { color:#fff; background:var(--campus-primary, #237c74); transition:transform 180ms ease, filter 180ms ease; }
	.primary-action:active { transform:translateY(2rpx) scale(.985); filter:brightness(.94); }
	.secondary-action { color:var(--campus-primary, #237c74); border:1px solid var(--campus-primary, #237c74); transition:transform 180ms ease, background-color 180ms ease; }
	.secondary-action:active { transform:translateY(2rpx) scale(.985); background:var(--campus-primary-soft, #e6f3f1); }
	.campus-night .secondary-action { color:var(--campus-primary, #6ebaae); border-color:var(--campus-primary, #6ebaae); }
	.campus-night .secondary-action:active { background:var(--campus-night-input, #293032); }
	.full-action { margin-top:28rpx; }
	.join-copy { margin-top:24rpx; color:var(--campus-muted, #738581); font-size:25rpx; line-height:1.7; }
	.campus-night .join-copy { color:var(--campus-night-muted, #a9b5b0); }
	.download-item { display:flex; align-items:center; justify-content:space-between; gap:18rpx; padding:22rpx 0; border-bottom:1px solid var(--campus-border, #dde8e5); transition:background-color 180ms ease; }
	.download-item:active { background:var(--campus-primary-soft, #e6f3f1); }
	.campus-night .download-item { border-color:var(--campus-night-border, rgba(226,234,231,.1)); }
	.campus-night .download-item:active { background:var(--campus-night-input, #293032); }
	.download-item:last-of-type { border-bottom:0; }
	.download-platform { display:flex; align-items:center; gap:15rpx; min-width:0; }
	.download-platform > view { display:flex; flex-direction:column; gap:6rpx; min-width:0; font-size:26rpx; font-weight:700; }
	.platform-icon { width:54rpx; height:54rpx; border-radius:15rpx; display:flex; align-items:center; justify-content:center; background:var(--campus-primary, #237c74); color:#fff; font-size:30rpx; font-weight:800; }
	.platform-icon.ios { background:#333c42; }
	.platform-icon.web { background:#b9822d; }
	.download-url { max-width:310rpx; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; color:#84928e; font-size:20rpx; font-weight:400; }
	.download-actions { display:flex; gap:12rpx; flex-shrink:0; }
	.download-actions view { padding:12rpx 16rpx; border-radius:10rpx; color:var(--campus-primary, #237c74); border:1px solid var(--campus-primary, #237c74); font-size:22rpx; transition:transform 180ms ease, background-color 180ms ease; }
	.download-actions view:active { transform:scale(.95); }
	.download-actions view:first-child { color:#fff; background:var(--campus-primary, #237c74); }
	.campus-night .download-actions view { color:var(--campus-primary, #6ebaae); border-color:var(--campus-primary, #6ebaae); }
	.campus-night .download-actions view:first-child { color:#17201f; background:var(--campus-primary, #6ebaae); }
	.download-tip { display:flex; align-items:center; gap:10rpx; margin-top:18rpx; color:#9b6d2c; font-size:22rpx; line-height:1.5; }
	.invitation-empty { padding:90rpx 20rpx; text-align:center; color:#7b8986; font-size:25rpx; }
	.invite-history { margin-top:28rpx; padding-top:24rpx; border-top:1px solid var(--campus-border, #dde8e5); }
	.history-heading { display:flex; justify-content:space-between; align-items:center; margin-bottom:12rpx; color:var(--campus-muted, #738581); font-size:22rpx; }
	.history-item { display:flex; align-items:center; gap:14rpx; min-height:70rpx; }
	.history-avatar { width:48rpx; height:48rpx; flex:none; display:flex; align-items:center; justify-content:center; border-radius:50%; background:var(--campus-primary-soft, #e6f3f1) center/cover; color:var(--campus-primary, #237c74); font-size:22rpx; }
	.history-main { min-width:0; flex:1; display:flex; flex-direction:column; gap:4rpx; }
	.history-name { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; color:var(--campus-text, #243633); font-size:24rpx; }
	.history-time { color:var(--campus-muted, #738581); font-size:20rpx; }
	.history-reward { color:var(--campus-primary, #237c74); font-size:21rpx; }
	.campus-night .invite-history { border-color:var(--campus-night-border, rgba(226,234,231,.1)); }
	.campus-night .history-name { color:var(--campus-night-text, #edf3f0); }
	.campus-night .history-avatar { background:var(--campus-night-input, #293032); color:var(--campus-primary, #6ebaae); }
	.campus-night .history-reward { color:var(--campus-primary, #6ebaae); }

	.motion-active .invitation-header { animation: invitation-header-in 360ms ease both; }
	.motion-active .invitation-hero { animation: invitation-hero-in 520ms cubic-bezier(.2,.8,.2,1) both; }
	.motion-active .invitation-hero-topline { animation: invitation-content-in 420ms 80ms ease both; }
	.motion-active .invitation-hero-title { animation: invitation-content-in 480ms 140ms ease both; }
	.motion-active .invitation-hero-copy,
	.motion-active .invite-invalid { animation: invitation-content-in 420ms 220ms ease both; }
	.motion-active .reward-pills { animation: invitation-content-in 420ms 300ms ease both; }
	.motion-active .invitation-card { animation: invitation-card-in 460ms cubic-bezier(.2,.75,.2,1) both; }
	.motion-active .dashboard-card { animation-delay: 90ms; }
	.motion-active .join-card { animation-delay: 90ms; }
	.motion-active .download-card { animation-delay: 160ms; }
	.motion-active .invite-history { animation: invitation-content-in 400ms 220ms ease both; }

	@keyframes invitation-header-in { from { opacity:0; transform:translateY(-12rpx); } to { opacity:1; transform:none; } }
	@keyframes invitation-hero-in { from { opacity:0; transform:translateY(18rpx) scale(.985); } to { opacity:1; transform:none; } }
	@keyframes invitation-card-in { from { opacity:0; transform:translateY(22rpx); } to { opacity:1; transform:none; } }
	@keyframes invitation-content-in { from { opacity:0; transform:translateY(12rpx); } to { opacity:1; transform:none; } }

	@media (prefers-reduced-motion: reduce) {
		.invitation-page *, .invitation-page *::after { animation-duration:1ms !important; animation-iteration-count:1 !important; transition-duration:1ms !important; }
	}
	@media screen and (max-width: 380px) { .invitation-shell { padding-left:20rpx; padding-right:20rpx; } .invitation-hero { padding-left:28rpx; padding-right:28rpx; } .invitation-hero-title { font-size:42rpx; } .action-row { flex-direction:column; } .download-item { align-items:flex-start; flex-direction:column; } .download-actions { width:100%; } .download-actions view { flex:1; text-align:center; } }
</style>
