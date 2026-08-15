<template>

	<view class="tabbar-system"
		:class="['tabbar-current-' + current, {'has-publish': current < 2, 'is-ready': dockReady, 'is-night': night, 'is-editor-hidden': editorOpen, 'is-scroll-hidden': scrollHidden, 'transition-from-publish': transitionOrigin === 'publish', 'transition-from-plain': transitionOrigin === 'plain'}]">
		<PublishPanel ref="publishPanel" :visible="current < 2" :night="night" :auto-intro="false"></PublishPanel>
		<view class="tabbar-dock" :style="dockChromeStyle">
			<block v-for="(item, index) in list" :key="item.path">
				<view class="tabbar-item animation-reverse" :class="{'is-active': current == index}" @tap="tabbarChange(item.path)">
					<view class="item-icon-wrap">
						<image class="item-img" :src="item.icon_a" v-if="current == index"></image>
						<image class="item-img" :src="item.icon" v-else></image>
						<view class="tabbar-unread-dot" v-if="index === 2 && unreadCount > 0"></view>
					</view>
					<view class="item-name" :class="{'tabbarActive': current == index}" v-if="item.text">{{item.text}}</view>
				</view>
			</block>
		</view>
	</view>
</template>

<script>
	import { CAMPUS_CHROME_EVENT } from '@/utils/campusChrome.js'
	import { CAMPUS_UNREAD_EVENT, getUnreadBadgeCount, refreshUnreadBadge } from '@/utils/unreadBadge.js'
	import { localStorage } from '@/js_sdk/mp-storage/mp-storage/index.js'

	let lastCampusTab = null

	export default {
		props: {
			current: Number,
			night: {
				type: Boolean,
				default: false
			}
		},
		data() {
			return {
				unreadCount: getUnreadBadgeCount(),
				pageActive: false,
				unreadRefreshAt: 0,
				unreadPushTimer: null,
				dockReady: true,
				dockTimer: null,
				publishTimer: null,
				transitionTimer: null,
				transitionOrigin: '',
				editorOpen: false,
				scrollHidden: false,
				chromeProgress: 0,
				list: [{
					text: '此刻',
					icon: '/static/tabbar/home_line.png',
					icon_a: '/static/tabbar/home_line_cur.png',
					path: "/pages/home/home",
				}, {
					text: '动态',
					icon: '/static/tabbar/square_line.png',
					icon_a: '/static/tabbar/square_line_cur.png',
					path: "/pages/home/square",
				}, {
					text: '消息',
					icon: '/static/tabbar/find_line.png',
					icon_a: '/static/tabbar/find_line_cur.png',
					path: '/pages/home/find',
				}, {
					text: '我的',
					icon: '/static/tabbar/user_line.png',
					icon_a: '/static/tabbar/user_line_cur.png',
					path: "/pages/home/user",
				}, ]
			};
		},
		mounted() {
			uni.$on(CAMPUS_UNREAD_EVENT, this.handleUnreadBadge)
			uni.$on('campus:push', this.handleUnreadPush)
			this.activate()
			this.$root.$on('campus-editor-visibility', this.handleEditorVisibility)
			uni.$on(CAMPUS_CHROME_EVENT, this.handleChromeVisibility)
		},
		beforeDestroy() {
			clearTimeout(this.unreadPushTimer)
			clearTimeout(this.dockTimer)
			clearTimeout(this.publishTimer)
			clearTimeout(this.transitionTimer)
			this.$root.$off('campus-editor-visibility', this.handleEditorVisibility)
			uni.$off(CAMPUS_CHROME_EVENT, this.handleChromeVisibility)
			uni.$off(CAMPUS_UNREAD_EVENT, this.handleUnreadBadge)
			uni.$off('campus:push', this.handleUnreadPush)
		},
		methods: {
			handleUnreadBadge(count) {
				this.unreadCount = Math.max(0, Number(count) || 0)
			},
			handleUnreadPush() {
				if (!this.pageActive || this.current === 2) return
				clearTimeout(this.unreadPushTimer)
				this.unreadPushTimer = setTimeout(() => this.refreshUnread(), 180)
			},
			refreshUnread() {
				if (this.current === 2) return
				const now = Date.now()
				if (now - this.unreadRefreshAt < 800) return
				this.unreadRefreshAt = now
				refreshUnreadBadge(this, localStorage.getItem('token'))
			},
			handleEditorVisibility(visible) {
				this.editorOpen = Boolean(visible)
			},
			handleChromeVisibility(state) {
				const progress = state && typeof state === 'object' ? state.progress : (state ? 1 : 0)
				this.chromeProgress = Math.max(0, Math.min(1, Number(progress) || 0))
				this.scrollHidden = this.chromeProgress >= 0.999
			},
			activate() {
				this.pageActive = true
				this.refreshUnread()
				clearTimeout(this.dockTimer)
				clearTimeout(this.publishTimer)
				clearTimeout(this.transitionTimer)
				const previousTab = lastCampusTab
				lastCampusTab = this.current
				if (previousTab !== null && previousTab !== this.current) {
					this.transitionOrigin = previousTab < 2 ? 'publish' : 'plain'
					this.$nextTick(() => {
						this.transitionTimer = setTimeout(() => {
							this.transitionOrigin = ''
							this.transitionTimer = null
						}, 32)
					})
				} else {
					this.transitionOrigin = ''
				}
				this.dockReady = true
				this.$nextTick(() => {
					this.publishTimer = setTimeout(() => {
						if (this.$refs.publishPanel) this.$refs.publishPanel.activatePage()
					}, 0)
				})
			},
			deactivate() {
				this.pageActive = false
				clearTimeout(this.unreadPushTimer)
				clearTimeout(this.dockTimer)
				clearTimeout(this.publishTimer)
				// Keep the dock mounted and ready across tab switches so the next page
				// can animate its position instead of flashing from an invisible state.
				this.dockReady = true
				this.editorOpen = false
				this.scrollHidden = false
				this.chromeProgress = 0
				if (this.$refs.publishPanel && this.$refs.publishPanel.deactivatePage) {
					this.$refs.publishPanel.deactivatePage()
				}
			},
			tabbarChange(path) {
				uni.switchTab({
					url: path
				})
		}
		},
		computed: {
			dockChromeStyle() {
				if (!this.chromeProgress) return {}
				return {
					opacity: String(1 - this.chromeProgress),
					transform: `translate3d(0, ${132 * this.chromeProgress}rpx, 0)`
				}
			}
		}
	};
</script>

<style>
	.tabbarActive {
		color: #237c74 !important;
	}

	.tabbar-item {
		position: relative;
		display: flex;
		flex: 1 1 0;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		height: 88rpx;
		background: transparent;
		text-align: center;
		overflow: visible;
		border: none;
		margin: 0;
		padding: 0;
		border-radius: 14rpx;
		box-sizing: border-box;
		transition: background-color 0.2s ease, transform 0.2s ease;
	}

	.tabbar-item.is-active {
		background-color: #e7f6f3;
	}

	.tabbar-item .item-img {
		width: 46rpx;
		height: 46rpx;
		display: block;
		margin: 0 auto;
		text-align: center;
		transition: transform 0.2s ease;
	}

	.item-icon-wrap {
		position: relative;
		flex: 0 0 46rpx;
		width: 46rpx;
		height: 46rpx;
	}

	.tabbar-unread-dot {
		position: absolute;
		top: -5rpx;
		right: -7rpx;
		width: 15rpx;
		height: 15rpx;
		border: 3rpx solid rgba(247, 250, 250, 0.98);
		border-radius: 50%;
		background: #ef4444;
		box-shadow: 0 2rpx 7rpx rgba(174, 35, 35, 0.28);
		box-sizing: content-box;
		pointer-events: none;
		animation: unreadDotIn 220ms cubic-bezier(0.22, 1, 0.36, 1) both;
	}

	@keyframes unreadDotIn {
		from { opacity: 0; transform: scale(0.55); }
		to { opacity: 1; transform: scale(1); }
	}

	.tabbar-item.is-active .item-img {
		transform: translateY(-2rpx) scale(1.05);
	}

	.tabbar-item .item-name {
		font-size: 23rpx;
		line-height: 30rpx;
		margin-top: 2rpx;
		color: #71817f;
	}

	/* App uses its own dock; H5 keeps the native uni-app tabbar. */
	.tabbar-dock {
		position: fixed;
		z-index: 990;
		bottom: calc(14rpx + env(safe-area-inset-bottom));
		left: calc(16px + env(safe-area-inset-left));
		right: calc(16px + env(safe-area-inset-right));
		width: auto;
		display: flex;
		align-items: center;
		height: 116rpx;
		padding: 8rpx 14rpx;
		border: 1rpx solid rgba(255, 255, 255, 0.82);
		border-radius: 58rpx;
		background: rgba(247, 250, 250, 0.96);
		box-shadow: 0 16rpx 46rpx rgba(39, 59, 66, 0.15), inset 0 1rpx 0 rgba(255, 255, 255, 0.7);
		box-sizing: border-box;
		opacity: 0;
		transform: translate3d(0, 14rpx, 0);
		transition: left 420ms cubic-bezier(0.22, 1, 0.36, 1), right 420ms cubic-bezier(0.22, 1, 0.36, 1), opacity 220ms ease, transform 420ms cubic-bezier(0.22, 1, 0.36, 1), height 320ms ease, border-radius 320ms ease;
		will-change: left, right, opacity, transform;
	}

	/* Fill the available row after the publish trigger instead of shrinking by viewport width. */
	.tabbar-system.has-publish .tabbar-dock {
		left: calc(28px + 92rpx + env(safe-area-inset-left));
	}

	.tabbar-system.is-ready .tabbar-dock {
		opacity: 1;
		transform: translate3d(0, 0, 0);
	}

	/* Reserve a left-side slot for the publish trigger without changing the four-item order. */
	.tabbar-system.has-publish:not(.is-ready) .tabbar-dock {
		transform: translate3d(0, 0, 0);
	}

	.tabbar-system.has-publish.is-ready .tabbar-dock {
		transform: translate3d(0, 0, 0);
	}

	.tabbar-system:not(.has-publish):not(.is-ready) .tabbar-dock {
		transform: translate3d(0, 0, 0);
	}

	.tabbar-system:not(.has-publish).is-ready .tabbar-dock {
		transform: translate3d(0, 0, 0);
	}

	.tabbar-system.has-publish .global-publish-trigger {
		left: calc(16px + env(safe-area-inset-left));
	}

	/* Keep the previous tab shape for one frame while this page's dock mounts. */
	.tabbar-system.transition-from-publish:not(.has-publish) .tabbar-dock {
		left: calc(28px + 92rpx + env(safe-area-inset-left));
	}

	.tabbar-system.transition-from-plain.has-publish .tabbar-dock {
		left: calc(16px + env(safe-area-inset-left));
	}

	.tabbar-dock .tabbar-item {
		min-width: 0;
		height: 98rpx;
		border-radius: 28rpx;
		transition: transform 180ms ease, background-color 220ms ease;
	}

	.tabbar-dock .tabbar-item.is-active {
		background: rgba(220, 243, 240, 0.72);
	}

	.tabbar-current-0 .tabbar-dock .tabbar-item.is-active { background: rgba(230, 243, 241, 0.86); }
	.tabbar-current-1 .tabbar-dock .tabbar-item.is-active { background: rgba(230, 243, 241, 0.86); }
	.tabbar-current-2 .tabbar-dock .tabbar-item.is-active { background: rgba(230, 243, 241, 0.86); }
	.tabbar-current-3 .tabbar-dock .tabbar-item.is-active { background: rgba(230, 243, 241, 0.86); }
	.tabbar-current-0 .tabbarActive,
	.tabbar-current-1 .tabbarActive,
	.tabbar-current-2 .tabbarActive,
	.tabbar-current-3 .tabbarActive { color: #237c74 !important; }

	.tabbar-dock .tabbar-item:active {
		transform: scale(0.91);
	}


	.tabbar-system.is-night .tabbar-dock,
	.campus-night .tabbar-dock {
		border-color: rgba(226, 232, 230, 0.1);
		background: #1c2123;
		box-shadow: 0 14rpx 36rpx rgba(0, 0, 0, 0.24);
	}

	.tabbar-system.is-night .tabbar-unread-dot,
	.campus-night .tabbar-unread-dot {
		border-color: #1c2123;
	}

	.tabbar-system.is-night .tabbar-dock .tabbar-item .item-name,
	.campus-night .tabbar-dock .tabbar-item .item-name {
		color: #929c99;
	}

	.tabbar-system.is-night .tabbar-dock .tabbar-item.is-active,
	.campus-night .tabbar-dock .tabbar-item.is-active {
		background: #293032;
	}

	.tabbar-system.is-night.tabbar-current-0 .tabbar-dock .tabbar-item.is-active,
	.tabbar-system.is-night.tabbar-current-1 .tabbar-dock .tabbar-item.is-active,
	.tabbar-system.is-night.tabbar-current-2 .tabbar-dock .tabbar-item.is-active,
	.tabbar-system.is-night.tabbar-current-3 .tabbar-dock .tabbar-item.is-active,
	.campus-night .tabbar-current-0 .tabbar-dock .tabbar-item.is-active,
	.campus-night .tabbar-current-1 .tabbar-dock .tabbar-item.is-active,
	.campus-night .tabbar-current-2 .tabbar-dock .tabbar-item.is-active,
	.campus-night .tabbar-current-3 .tabbar-dock .tabbar-item.is-active {
		background: #293032;
	}

	.tabbar-system.is-night.tabbar-current-0 .tabbarActive,
	.tabbar-system.is-night.tabbar-current-1 .tabbarActive,
	.tabbar-system.is-night.tabbar-current-2 .tabbarActive,
	.tabbar-system.is-night.tabbar-current-3 .tabbarActive,
	.campus-night .tabbar-current-0 .tabbarActive,
	.campus-night .tabbar-current-1 .tabbarActive,
	.campus-night .tabbar-current-2 .tabbarActive,
	.campus-night .tabbar-current-3 .tabbarActive {
		color: #5aae83 !important;
	}

	.tabbar-system.is-night.tabbar-current-0 .tabbar-dock .tabbar-item.is-active { background: rgba(65, 123, 174, 0.2); }
	.tabbar-system.is-night.tabbar-current-1 .tabbar-dock .tabbar-item.is-active { background: rgba(181, 126, 54, 0.2); }
	.tabbar-system.is-night.tabbar-current-2 .tabbar-dock .tabbar-item.is-active { background: rgba(69, 150, 126, 0.2); }
	.tabbar-system.is-night.tabbar-current-3 .tabbar-dock .tabbar-item.is-active { background: rgba(174, 83, 109, 0.2); }
	.tabbar-system.is-night.tabbar-current-0 .tabbarActive { color: #6da7d6 !important; }
	.tabbar-system.is-night.tabbar-current-1 .tabbarActive { color: #c89759 !important; }
	.tabbar-system.is-night.tabbar-current-2 .tabbarActive { color: #63ad93 !important; }
	.tabbar-system.is-night.tabbar-current-3 .tabbarActive { color: #c97b92 !important; }

	.tabbar-system.is-night .tabbar-dock .item-img {
		opacity: 0.78;
	}

	.tabbar-system.is-night .tabbar-dock .tabbar-item.is-active .item-img {
		opacity: 1;
		filter: brightness(0.96) saturate(0.82);
	}

	@media (prefers-reduced-motion: reduce) {
		.tabbar-dock,
		.tabbar-dock .tabbar-item {
			transition: none;
		}
		.tabbar-dock { opacity: 1; transform: translate3d(0, 0, 0); }
	}

	.tabbar-system.is-editor-hidden {
		display: none !important;
	}

	.tabbar-system.is-scroll-hidden.has-publish .tabbar-dock {
		opacity: 0;
		transform: translate3d(0, 132rpx, 0) !important;
		pointer-events: none;
	}

	.tabbar-system.is-scroll-hidden:not(.has-publish) .tabbar-dock {
		opacity: 0;
		transform: translate3d(0, 132rpx, 0) !important;
		pointer-events: none;
	}
</style>
