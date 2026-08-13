<script>
	// #ifdef H5
	import pageAnimation from './components/page-animation-pro'
	// #endif
	import Vue from 'vue'
	import { applyCampusThemeShell, getCampusThemeMode } from '@/utils/campusTheme.js'
	import { refreshSession } from '@/utils/session.js'
	// #ifdef APP-PLUS
	const CAMPUS_HOME_TAB = '/pages/home/home'
	const CAMPUS_TAB_ROUTES = [
		CAMPUS_HOME_TAB,
		'/pages/home/square',
		'/pages/home/find',
		'/pages/home/user'
	]
	let campusLastBackTime = 0
	let campusBackButtonInstalled = false

	function campusNormalizeRoute(route) {
		if (!route) return ''
		return route.charAt(0) === '/' ? route : '/' + route
	}

	function campusCurrentRoute() {
		const pages = getCurrentPages()
		if (!pages || !pages.length) return ''
		return campusNormalizeRoute(pages[pages.length - 1].route)
	}

	function campusGoHome() {
		uni.switchTab({
			url: CAMPUS_HOME_TAB,
			fail() {
				uni.reLaunch({
					url: CAMPUS_HOME_TAB
				})
			}
		})
	}

	function campusQuitOnDoubleBack() {
		const now = Date.now()
		if (now - campusLastBackTime < 1600) {
			if (typeof plus !== 'undefined' && plus.runtime) {
				plus.runtime.quit()
			}
			return
		}
		campusLastBackTime = now
	}

	function campusHandleBackButton() {
		const pages = getCurrentPages()
		const currentRoute = campusCurrentRoute()
		if (pages && pages.length > 1) {
			uni.navigateBack({
				delta: 1,
				fail: campusGoHome
			})
			return
		}
		if (currentRoute && CAMPUS_TAB_ROUTES.indexOf(currentRoute) === -1) {
			campusGoHome()
			return
		}
		if (currentRoute !== CAMPUS_HOME_TAB) {
			campusGoHome()
			return
		}
		campusQuitOnDoubleBack()
	}

	function campusInstallBackButtonHandler() {
		if (campusBackButtonInstalled || typeof plus === 'undefined' || !plus.key) return
		campusBackButtonInstalled = true
		plus.key.addEventListener('backbutton', campusHandleBackButton, false)
	}
	// #endif
	export default {
		// #ifdef H5
		mixins: [pageAnimation],
		// #endif
			onLaunch: function() {
			applyCampusThemeShell(getCampusThemeMode())
			refreshSession({ force: true })
			// #ifdef APP-PLUS
			campusInstallBackButtonHandler()
			
			// UniPush 点击和前台接收统一通知消息中心；payload 允许是字符串或对象。
			const campusPushPayload = function(msg) {
				const payload = msg && msg.payload != null ? msg.payload : '';
				const value = typeof payload === 'string' ? payload : JSON.stringify(payload);
				uni.$emit('campus:push', {
					payload: value,
					title: msg && (msg.title || msg.aps && msg.aps.alert && msg.aps.alert.title),
					content: msg && (msg.content || msg.aps && msg.aps.alert && msg.aps.alert.body)
				});
				return value;
			};
			plus.push.addEventListener("receive", function(msg) {
				const payload = campusPushPayload(msg);
				// App 前台时系统不会总是弹出通知，补一条本地通知，后台仍由 UniPush 展示。
				if (msg && msg.type === 'receive' && plus.os.name === 'Android') {
					plus.push.createMessage(
						msg.content || '收到一条新的动态评论消息',
						payload || 'spaceComment',
						{title: msg.title || '校园通知'}
					);
				}
			}, false);

			// 点击系统通知的推送跳转到消息中心
			plus.push.addEventListener("click", function(msg) {
				var payload = campusPushPayload(msg);
				if (typeof payload === 'string' && payload.indexOf('qa:') === 0) {
					var questionId = parseInt(payload.substring(3), 10);
					if (questionId > 0) {
						setTimeout(function() {
							uni.navigateTo({ url: '/pages/qa/info?id=' + questionId })
						}, 1000)
					}
				}
				if(payload=="finance"){
					setTimeout(function() {
						uni.navigateTo({
							url: '/pages/user/inbox'
						})
					}, 1000)
				}
				if(payload=="system"){
					setTimeout(function() {
						uni.navigateTo({
							url: '/pages/user/inbox'
						})
					}, 1000)
				}
				if (typeof payload === 'string' && payload.toLowerCase().indexOf("comment") !== -1){
					setTimeout(function() {
						uni.navigateTo({
							url: '/pages/user/inbox'
						})
					}, 1000)
				}
				plus.push.clear();
			}, false);
			
			//app禁用默认tab
			uni.hideTabBar({
				animation: false
			})
			// #endif
			uni.getSystemInfo({
				success: function(e) {
					// #ifndef MP
					Vue.prototype.StatusBar = e.statusBarHeight;
					if (e.platform == 'android') {
						Vue.prototype.CustomBar = e.statusBarHeight + 50;
					} else {
						Vue.prototype.CustomBar = e.statusBarHeight + 45;
					};
					// #endif

					// #ifdef MP-WEIXIN
					Vue.prototype.StatusBar = e.statusBarHeight;
					let custom = wx.getMenuButtonBoundingClientRect();
					Vue.prototype.Custom = custom;
					Vue.prototype.CustomBar = custom.bottom + custom.top - e.statusBarHeight;
					// #endif		

					// #ifdef MP-ALIPAY
					Vue.prototype.StatusBar = e.statusBarHeight;
					Vue.prototype.CustomBar = e.statusBarHeight + e.titleBarHeight;
					// #endif
					
					// #ifdef MP-QQ
					Vue.prototype.StatusBar = e.statusBarHeight;
					Vue.prototype.CustomBar = e.statusBarHeight + 45;
					// #endif
					
					// #ifdef MP-BAIDU
					Vue.prototype.StatusBar = e.statusBarHeight;
					Vue.prototype.CustomBar = e.statusBarHeight + 45;
					// #endif
					
					// #ifdef MP-TOUTIAO
					Vue.prototype.StatusBar = e.statusBarHeight;
					Vue.prototype.CustomBar = e.statusBarHeight + 45;
					// #endif
				}
			})

			Vue.prototype.ColorList = [{
					title: '嫣红',
					name: 'red',
					color: '#e54d42'
				},
				{
					title: '桔橙',
					name: 'orange',
					color: '#f37b1d'
				},
				{
					title: '明黄',
					name: 'yellow',
					color: '#fbbd08'
				},
				{
					title: '橄榄',
					name: 'olive',
					color: '#8dc63f'
				},
				{
					title: '森绿',
					name: 'green',
					color: '#39b54a'
				},
				{
					title: '天青',
					name: 'cyan',
					color: '#1cbbb4'
				},
				{
					title: '海蓝',
					name: 'blue',
					color: '#0081ff'
				},
				{
					title: '姹紫',
					name: 'purple',
					color: '#6739b6'
				},
				{
					title: '木槿',
					name: 'mauve',
					color: '#9c26b0'
				},
				{
					title: '桃粉',
					name: 'pink',
					color: '#e03997'
				},
				{
					title: '棕褐',
					name: 'brown',
					color: '#a5673f'
				},
				{
					title: '玄灰',
					name: 'grey',
					color: '#8799a3'
				},
				{
					title: '草灰',
					name: 'gray',
					color: '#aaaaaa'
				},
				{
					title: '墨黑',
					name: 'black',
					color: '#333333'
				},
				{
					title: '雅白',
					name: 'white',
					color: '#ffffff'
				},
			]

		},
		onShow: function() {
			applyCampusThemeShell(getCampusThemeMode())
			refreshSession()
			console.log('App Show')
		},
		onHide: function() {
			console.log('App Hide')
		}

	}
</script>

<style lang="scss">
	@import "colorui/main.css";
	@import "colorui/icon.css";
	@import "static/base.css";
	@import '@/uni_modules/tuniao-ui/theme.scss';
	@import '@/uni_modules/tuniao-ui/index.scss';
	@import '@/uni_modules/tuniao-ui/iconfont.css';
	/* uview scss */
	@import "@/uni_modules/uview-ui/index.scss";
	page {
		background-color: #f4f8f8;
		color: #20312f;
	}

	/* Keep the renderer shell on the selected theme while a new page is mounting. */
	html,
	body,
	#app,
	uni-app,
	uni-page,
	uni-page-body {
		background-color: #f4f8f8;
	}

	html.campus-system-night,
	body.campus-system-night,
	html.campus-system-night #app,
	html.campus-system-night uni-app,
	html.campus-system-night uni-page,
	html.campus-system-night uni-page-body {
		background-color: #15191b !important;
	}

	/* Legacy pages use several different root classes. The shell class is the
	 * final theme source, so keep their shared surfaces in the same night palette. */
	html.campus-system-night,
	body.campus-system-night,
	html.campus-system-night page,
	html.campus-system-night uni-page-body {
		--campus-night-bg: #15191b;
		--campus-night-surface: #202527;
		--campus-night-input: #293032;
		--campus-night-border: rgba(226, 234, 231, 0.1);
		--campus-night-text: #edf3f0;
		--campus-night-muted: #a9b5b0;
		background-color: var(--campus-night-bg) !important;
		color: var(--campus-night-text);
	}

	html.campus-system-night .user,
	html.campus-system-night .userpost,
	html.campus-system-night .post,
	html.campus-system-night .usermarks,
	html.campus-system-night .buyvippage,
	html.campus-system-night .campus-page,
	html.campus-system-night .campus-subpage,
	html.campus-system-night .qa-page {
		background-color: var(--campus-night-bg) !important;
		color: var(--campus-night-text) !important;
	}

	html.campus-system-night .header,
	html.campus-system-night .header2,
	html.campus-system-night .cu-bar,
	html.campus-system-night .cu-bar.bg-white,
	html.campus-system-night .bg-white,
	html.campus-system-night .bg-white-solid,
	html.campus-system-night .data-box,
	html.campus-system-night .all-box:not(.home-feed),
	html.campus-system-night .cu-card.no-card > .cu-item,
	html.campus-system-night .cu-list.menu,
	html.campus-system-night .cu-list.menu-avatar,
	html.campus-system-night .cu-list.menu > .cu-item,
	html.campus-system-night .cu-list.menu-avatar > .cu-item,
	html.campus-system-night .u-cell,
	html.campus-system-night .u-cell-item,
	html.campus-system-night .u-mode-center-box,
	html.campus-system-night .u-popup__content,
	html.campus-system-night .tn-popup,
	html.campus-system-night .tn-popup__content,
	html.campus-system-night .tn-popup__wrapper,
	html.campus-system-night .cu-modal .cu-dialog {
		border-color: var(--campus-night-border) !important;
		background-color: var(--campus-night-surface) !important;
		color: var(--campus-night-text) !important;
		box-shadow: none !important;
		transition: background-color 220ms ease, border-color 220ms ease, color 160ms ease;
	}

	html.campus-system-night [style*="background: white"],
	html.campus-system-night [style*="background-color: white"],
	html.campus-system-night [style*="background:#fff"],
	html.campus-system-night [style*="background: #fff"],
	html.campus-system-night [style*="background-color:#fff"],
	html.campus-system-night [style*="background-color: #fff"] {
		background-color: var(--campus-night-surface) !important;
		color: var(--campus-night-text) !important;
	}

	html.campus-system-night .cu-form-group,
	html.campus-system-night .search-form,
	html.campus-system-night .info-input-box,
	html.campus-system-night .reply-composer,
	html.campus-system-night .qa-composer,
	html.campus-system-night .edit-tool,
	html.campus-system-night .space-owo {
		border-color: var(--campus-night-border) !important;
		background-color: var(--campus-night-input) !important;
		color: var(--campus-night-text) !important;
	}

	html.campus-system-night input,
	html.campus-system-night textarea,
	html.campus-system-night .uni-input-input {
		background-color: transparent !important;
		color: var(--campus-night-text) !important;
	}

	html.campus-system-night .uni-input-placeholder,
	html.campus-system-night .placeholder {
		color: #899692 !important;
	}

	html.campus-system-night .header .content,
	html.campus-system-night .header .action,
	html.campus-system-night .header2 .content,
	html.campus-system-night .header2 .action,
	html.campus-system-night .text-black,
	html.campus-system-night .color-black,
	html.campus-system-night .text-content,
	html.campus-system-night .data-box-title,
	html.campus-system-night .item-title,
	html.campus-system-night .title,
	html.campus-system-night .content {
		color: var(--campus-night-text) !important;
	}

	html.campus-system-night .text-gray,
	html.campus-system-night .text-grey,
	html.campus-system-night .more text,
	html.campus-system-night .no-data,
	html.campus-system-night .load-more,
	html.campus-system-night .data-time,
	html.campus-system-night .uni-list-item__extra,
	html.campus-system-night .u-line-1,
	html.campus-system-night .u-line-2 {
		color: var(--campus-night-muted) !important;
	}

	html.campus-system-night .loading,
	html.campus-system-night .loading-main,
	html.campus-system-night .dataLoad,
	html.campus-system-night .load-more,
	html.campus-system-night .no-data {
		background-color: var(--campus-night-bg) !important;
	}

	html.campus-system-night .info-footer,
	html.campus-system-night .space-footer,
	html.campus-system-night .qa-write-bar {
		border-color: var(--campus-night-border) !important;
		background-color: #1b2123 !important;
		color: var(--campus-night-text) !important;
		box-shadow: 0 -8rpx 24rpx rgba(0, 0, 0, 0.16) !important;
	}

	html.campus-system-night .reply-composer-mask,
	html.campus-system-night .qa-composer-mask {
		background-color: rgba(6, 9, 9, 0.54) !important;
	}

	/* APP-PLUS pages receive the reactive class even before a WebView shell is available. */
	.campus-night {
		--campus-night-bg: #15191b;
		--campus-night-surface: #202527;
		--campus-night-input: #293032;
		--campus-night-border: rgba(226, 234, 231, 0.1);
		--campus-night-text: #edf3f0;
		--campus-night-muted: #a9b5b0;
	}

	.campus-night.user,
	.campus-night.userpost,
	.campus-night.post,
	.campus-night.usermarks,
	.campus-night.buyvippage {
		background-color: var(--campus-night-bg) !important;
		color: var(--campus-night-text) !important;
	}

	.campus-night .header,
	.campus-night .header2,
	.campus-night .cu-bar,
	.campus-night .cu-bar.bg-white,
	.campus-night .bg-white,
	.campus-night .bg-white-solid,
	.campus-night .data-box,
	.campus-night .all-box:not(.home-feed),
	.campus-night .cu-card.no-card > .cu-item,
	.campus-night .cu-list.menu,
	.campus-night .cu-list.menu-avatar,
	.campus-night .cu-list.menu > .cu-item,
	.campus-night .cu-list.menu-avatar > .cu-item,
	.campus-night .u-cell,
	.campus-night .u-cell-item,
	.campus-night .u-popup__content,
	.campus-night .tn-popup,
	.campus-night .tn-popup__content,
	.campus-night .tn-popup__wrapper,
	.campus-night .cu-modal .cu-dialog {
		border-color: var(--campus-night-border) !important;
		background-color: var(--campus-night-surface) !important;
		color: var(--campus-night-text) !important;
		box-shadow: none !important;
	}

	.campus-night [style*="background: white"],
	.campus-night [style*="background-color: white"],
	.campus-night [style*="background:#fff"],
	.campus-night [style*="background: #fff"],
	.campus-night [style*="background-color:#fff"],
	.campus-night [style*="background-color: #fff"] {
		background-color: var(--campus-night-surface) !important;
		color: var(--campus-night-text) !important;
	}

	.campus-night .cu-form-group,
	.campus-night .search-form,
	.campus-night .info-input-box,
	.campus-night .reply-composer,
	.campus-night .qa-composer,
	.campus-night .edit-tool,
	.campus-night .space-owo {
		border-color: var(--campus-night-border) !important;
		background-color: var(--campus-night-input) !important;
		color: var(--campus-night-text) !important;
	}

	.campus-night .text-black,
	.campus-night .color-black,
	.campus-night .text-content,
	.campus-night .data-box-title,
	.campus-night .item-title,
	.campus-night .title,
	.campus-night .content {
		color: var(--campus-night-text) !important;
	}

	.campus-night .text-gray,
	.campus-night .text-grey,
	.campus-night .more text,
	.campus-night .no-data,
	.campus-night .load-more,
	.campus-night .data-time {
		color: var(--campus-night-muted) !important;
	}

	.campus-night .loading,
	.campus-night .loading-main,
	.campus-night .dataLoad,
	.campus-night .load-more,
	.campus-night .no-data {
		background-color: var(--campus-night-bg) !important;
	}

	.campus-night .loading {
		background-color: #15191b !important;
	}

	.campus-night .loading-main,
	.campus-night .dataLoad {
		background-color: transparent !important;
	}

	/* Lightweight shared loading state. It replaces the multi-megabyte GIF. */
	.campus-loader {
		display: block;
		width: 42rpx;
		height: 42rpx;
		margin: 28rpx auto;
		border: 5rpx solid rgba(22, 156, 146, 0.14);
		border-top-color: #169c92;
		border-right-color: #65bce8;
		border-radius: 50%;
		box-sizing: border-box;
		animation: campusLoaderSpin 720ms linear infinite;
	}

	.dataLoad,
	.loading-main {
		display: flex;
		min-height: 120rpx;
		align-items: center;
		justify-content: center;
	}

	@keyframes campusLoaderSpin {
		to { transform: rotate(360deg); }
	}

	@media (prefers-reduced-motion: reduce) {
		.campus-loader { animation-duration: 1.4s; }
	}
	.nav-list {
		display: flex;
		flex-wrap: wrap;
		padding: 0px 40upx 0px;
		justify-content: space-between;
	}

	.nav-li {
		padding: 30upx;
		border-radius: 12upx;
		width: 45%;
		margin: 0 2.5% 40upx;
		/* background-image: url(https://cdn.nlark.com/yuque/0/2019/png/280374/1552996358352-assets/web-upload/cc3b1807-c684-4b83-8f80-80e5b8a6b975.png); */
		background-size: cover;
		background-position: center;
		position: relative;
		z-index: 1;
	}

	.nav-li::after {
		content: "";
		position: absolute;
		z-index: -1;
		background-color: inherit;
		width: 100%;
		height: 100%;
		left: 0;
		bottom: -10%;
		border-radius: 10upx;
		opacity: 0.2;
		transform: scale(0.9, 0.9);
	}

	.nav-li.cur {
		color: #fff;
		background: rgb(94, 185, 94);
		box-shadow: 4upx 4upx 6upx rgba(94, 185, 94, 0.4);
	}

	.nav-title {
		font-size: 32upx;
		font-weight: 300;
	}

	.nav-title::first-letter {
		font-size: 40upx;
		margin-right: 4upx;
	}

	.nav-name {
		font-size: 28upx;
		text-transform: Capitalize;
		margin-top: 20upx;
		position: relative;
	}

	.nav-name::before {
		content: "";
		position: absolute;
		display: block;
		width: 40upx;
		height: 6upx;
		background: #fff;
		bottom: 0;
		right: 0;
		opacity: 0.5;
	}

	.nav-name::after {
		content: "";
		position: absolute;
		display: block;
		width: 100upx;
		height: 1px;
		background: #fff;
		bottom: 0;
		right: 40upx;
		opacity: 0.3;
	}

	.nav-name::first-letter {
		font-weight: bold;
		font-size: 36upx;
		margin-right: 1px;
	}

	.nav-li text {
		position: absolute;
		right: 30upx;
		top: 30upx;
		font-size: 52upx;
		width: 60upx;
		height: 60upx;
		text-align: center;
		line-height: 60upx;
	}

	.text-light {
		font-weight: 300;
	}
	
	
	.uni-swiper-dot{
		background-color: rgba(255,255,255,.7)!important;
		width: 10upx !important;
		height: 10upx !important;
		border-radius: 5upx !important;
	}
	.uni-swiper-dot.uni-swiper-dot-active{
		background-color: #3cc9a4 !important;
		opacity: 0.8;
		
		
	}
	.uni-swiper-dot.uni-swiper-dot-active::after{
		background-color: #3cc9a4 !important;
		height: 8upx !important;
	}
	.uni-swiper-dots-horizontal{
		bottom: 40upx !important;
	}
	.tags .tags-box span{
		white-space:nowrap;
	}
	/* #ifdef MP */
	.screen-swiper image, .screen-swiper video, .swiper-item image, .swiper-item video{
		height: 360upx;
		border-radius: 30upx;
	}
	swiper-item{
		padding: 15upx 25upx;
		box-sizing: border-box;
		
	}
	swiper-item .swiper-text{
		width: calc(100% - 50upx);
		top: 15upx;
		height: 360upx;
		border-radius: 30upx;
	}
	/* #endif	 */
	@keyframes show {
		0% {
			transform: translateY(-50px);
		}

		60% {
			transform: translateY(40upx);
		}

		100% {
			transform: translateY(0px);
		}
	}

	@-webkit-keyframes show {
		0% {
			transform: translateY(-50px);
		}

		60% {
			transform: translateY(40upx);
		}

		100% {
			transform: translateY(0px);
		}
	}
	@font-face {
	     font-family: my-font;
		 src: url('~@/static/HarmonyOS_Sans_SC_Medium.subset.woff2');
	}
	*{
		font-family: my-font, "PingFang SC", "Microsoft YaHei", sans-serif;
		letter-spacing: 0;
	}
	/* 点击更多的样式 */
</style>
