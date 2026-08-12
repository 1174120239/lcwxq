<template>

	<view class="tabbar-system"
		:class="['tabbar-current-' + current, {'has-publish': current < 2, 'is-ready': dockReady, 'is-night': night, 'is-editor-hidden': editorOpen}]">
		<PublishPanel ref="publishPanel" :visible="current < 2" :night="night" :auto-intro="false"></PublishPanel>
		<view class="tabbar-dock">
			<block v-for="(item, index) in list" :key="item.path">
				<view class="tabbar-item animation-reverse" :class="{'is-active': current == index}" @tap="tabbarChange(item.path)">
					<view class="tabbar-unread-dot" v-if="index===2 && unreadCount>0"></view>
					<image class="item-img" :src="item.icon_a" v-if="current == index"></image>
					<image class="item-img" :src="item.icon" v-else></image>
					<view class="item-name" :class="{'tabbarActive': current == index}" v-if="item.text">{{item.text}}</view>
				</view>
			</block>
		</view>
	</view>
</template>

<script>
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
				dockReady: false,
				dockTimer: null,
				publishTimer: null,
				editorOpen: false,
				unreadCount: 0,
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
			this.activate()
			this.refreshUnread()
			this.$root.$on('campus-editor-visibility', this.handleEditorVisibility)
			uni.$on('campus:unread-changed', this.handleUnreadChanged)
		},
		beforeDestroy() {
			clearTimeout(this.dockTimer)
			clearTimeout(this.publishTimer)
			this.$root.$off('campus-editor-visibility', this.handleEditorVisibility)
			uni.$off('campus:unread-changed', this.handleUnreadChanged)
		},
		methods: {
			handleUnreadChanged(count) {
				this.unreadCount = Math.max(0, Number(count) || 0)
				this.syncNativeBadge()
			},
			refreshUnread() {
				var that = this
				var token = uni.getStorageSync('token') || ''
				if(!token) return that.handleUnreadChanged(0)
				that.$Net.request({
					url:that.$API.unreadNum(), data:{token:token}, method:'get',
					header:{'Content-Type':'application/x-www-form-urlencoded'},
					success:function(res){ if(res.data.code==1) that.handleUnreadChanged(res.data.data) }
				})
			},
			syncNativeBadge() {
				if(this.unreadCount>0){
					uni.showTabBarRedDot({index:2, fail:function(){}})
				}else{
					uni.hideTabBarRedDot({index:2, fail:function(){}})
				}
			},
			handleEditorVisibility(visible) {
				this.editorOpen = Boolean(visible)
			},
			activate() {
				clearTimeout(this.dockTimer)
				clearTimeout(this.publishTimer)
				this.dockReady = false
				this.$nextTick(() => {
					this.dockTimer = setTimeout(() => {
						this.dockReady = true
					}, 30)
					this.publishTimer = setTimeout(() => {
						if (this.$refs.publishPanel) this.$refs.publishPanel.activatePage()
					}, this.current < 2 ? 110 : 30)
				})
			},
			tabbarChange(path) {
				uni.switchTab({
					url: path
				})
			}
		}
	};
</script>

<style>
	.tabbarActive {
		color: #167f77 !important;
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

	.tabbar-unread-dot {
		position: absolute; z-index: 3; top: 7rpx; left: calc(50% + 16rpx);
		width: 15rpx; height: 15rpx; border: 3rpx solid #fff; border-radius: 50%;
		background: #e5484d; box-sizing: content-box;
	}

	.tabbar-system.is-night .tabbar-unread-dot { border-color: #1c2123; }

	.tabbar-item.is-active {
		background-color: #e7f6f3;
	}

	.tabbar-item .item-img {
		width: 42rpx;
		height: 42rpx;
		display: block;
		margin: 0 auto;
		text-align: center;
		transition: transform 0.2s ease;
	}

	.tabbar-item.is-active .item-img {
		transform: translateY(-2rpx) scale(1.05);
	}

	.tabbar-item .item-name {
		font-size: 22rpx;
		line-height: 30rpx;
		margin-top: 2rpx;
		color: #71817f;
	}

	/* App uses its own dock; H5 keeps the native uni-app tabbar. */
	.tabbar-dock {
		position: fixed;
		z-index: 990;
		bottom: calc(14rpx + env(safe-area-inset-bottom));
		left: 50%;
		width: calc(100% - 156rpx);
		display: flex;
		align-items: center;
		height: 112rpx;
		padding: 8rpx 12rpx;
		border: 1rpx solid rgba(255, 255, 255, 0.82);
		border-radius: 56rpx;
		background: rgba(247, 250, 250, 0.94);
		box-shadow: 0 16rpx 46rpx rgba(39, 59, 66, 0.15), inset 0 1rpx 0 rgba(255, 255, 255, 0.7);
		box-sizing: border-box;
		transform: translate3d(-50%, 0, 0);
		transition: transform 400ms cubic-bezier(0.22, 1, 0.36, 1);
		will-change: transform;
	}

	.tabbar-system.has-publish:not(.is-ready) .tabbar-dock {
		transform: translate3d(-50%, 0, 0);
	}

	.tabbar-system.has-publish.is-ready .tabbar-dock {
		transform: translate3d(-50%, 0, 0) translate3d(60rpx, 0, 0);
	}

	.tabbar-system:not(.has-publish):not(.is-ready) .tabbar-dock {
		transform: translate3d(-50%, 0, 0) translate3d(60rpx, 0, 0);
	}

	.tabbar-system:not(.has-publish).is-ready .tabbar-dock {
		transform: translate3d(-50%, 0, 0);
	}

	.tabbar-dock .tabbar-item {
		min-width: 0;
		height: 92rpx;
		border-radius: 28rpx;
		transition: transform 180ms ease, background-color 220ms ease;
	}

	.tabbar-dock .tabbar-item.is-active {
		background: rgba(220, 243, 240, 0.72);
	}

	.tabbar-current-0 .tabbar-dock .tabbar-item.is-active { background: rgba(222, 239, 255, 0.78); }
	.tabbar-current-1 .tabbar-dock .tabbar-item.is-active { background: rgba(255, 238, 213, 0.8); }
	.tabbar-current-2 .tabbar-dock .tabbar-item.is-active { background: rgba(220, 244, 240, 0.8); }
	.tabbar-current-3 .tabbar-dock .tabbar-item.is-active { background: rgba(253, 228, 236, 0.82); }
	.tabbar-current-0 .tabbarActive { color: #168cf0 !important; }
	.tabbar-current-1 .tabbarActive { color: #e98200 !important; }
	.tabbar-current-2 .tabbarActive { color: #168f84 !important; }
	.tabbar-current-3 .tabbarActive { color: #d95879 !important; }

	.tabbar-dock .tabbar-item:active {
		transform: scale(0.91);
	}


	.tabbar-system.is-night .tabbar-dock,
	.campus-night .tabbar-dock {
		border-color: rgba(226, 232, 230, 0.1);
		background: #1c2123;
		box-shadow: 0 14rpx 36rpx rgba(0, 0, 0, 0.24);
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
	}

	.tabbar-system.is-editor-hidden {
		display: none !important;
	}
</style>
