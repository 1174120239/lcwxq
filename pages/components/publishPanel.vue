<template>
	<view class="publish-system" :class="{'is-night': night}">
		<view class="global-publish-trigger"
			:class="{'is-ready': isReady, 'is-hidden': isOpen, 'is-disabled': !visible}"
			@tap.stop="openPanel">
			<text class="cuIcon-add"></text>
		</view>

		<view class="publish-curtain" :class="{'is-open': isOpen}" @tap="closePanel">
			<scroll-view scroll-y class="publish-curtain-scroll" @tap="closePanel">
				<view class="publish-curtain-content">
					<!-- Protected UI baseline: keep in sync with AI_PROJECT_BRIEF.md section 22. -->
					<view class="publish-announcement" @tap.stop>
						<text class="announcement-heading">近期公告</text>
						<rich-text class="announcement-copy" :nodes="announcement || fallbackAnnouncement"></rich-text>
					</view>

					<view class="publish-actions" @tap.stop>
						<view class="publish-option" @tap="goPublish('/pages/space/post')">
							<view class="publish-option-icon"><text class="cuIcon-camera"></text></view>
							<text class="publish-option-label">发布动态</text>
							<text class="publish-option-arrow cuIcon-right"></text>
						</view>
						<text class="publish-option-hint">更多发布方式，敬请期待</text>
					</view>
				</view>
			</scroll-view>
		</view>
	</view>
</template>

<script>
	export default {
		name: 'PublishPanel',
		props: {
		visible: {
			type: Boolean,
			default: true
		},
		night: {
			type: Boolean,
			default: false
		},
		autoIntro: {
				type: Boolean,
				default: true
			}
		},
		data() {
			return {
				isOpen: false,
				isLeaving: false,
				isReady: false,
				introTimer: null,
				closeTimer: null,
				announcement: '',
				fallbackAnnouncement: '欢迎来到校园社区。分享日常、记录成长，也请尊重每一位认真表达的人。'
			}
		},
		created() {
			if (this.visible) this.loadAnnouncement()
		},
		mounted() {
			this.syncTabbarState()
			if (this.autoIntro) this.activatePage()
		},
		watch: {
			visible(value) {
				this.syncTabbarState()
				if (value) {
					if (!this.announcement) this.loadAnnouncement()
					if (this.autoIntro) this.playIntro()
				} else {
					this.resetPanel()
				}
			}
		},
		beforeDestroy() {
			clearTimeout(this.introTimer)
			clearTimeout(this.closeTimer)
			this.removeTabbarState()
			this.unlockPageScroll()
		},
		methods: {
			activatePage() {
				this.syncTabbarState()
				if (!this.visible) {
					this.resetPanel()
					return
				}
				this.playIntro()
			},
			playIntro() {
				if (!this.visible) return
				clearTimeout(this.introTimer)
				this.isReady = false
				this.$nextTick(() => {
					this.introTimer = setTimeout(() => {
						this.isReady = true
					}, 70)
				})
			},
			resetPanel() {
				clearTimeout(this.introTimer)
				clearTimeout(this.closeTimer)
				this.isReady = false
				this.isOpen = false
				this.isLeaving = false
				this.unlockPageScroll()
			},
			openPanel() {
				if (!this.visible || !this.isReady || this.isOpen || this.isLeaving) return
				this.isOpen = true
				this.lockPageScroll()
			},
			closePanel() {
				if (!this.isOpen || this.isLeaving) return
				this.isLeaving = true
				this.isOpen = false
				clearTimeout(this.closeTimer)
				this.closeTimer = setTimeout(() => {
					this.isLeaving = false
					this.unlockPageScroll()
				}, 700)
			},
			loadAnnouncement() {
				const that = this
				const cacheKey = 'publishAnnouncementCache'
				try {
					const cached = uni.getStorageSync(cacheKey)
					if (cached && cached.content && Date.now() - cached.time < 300000) {
						that.announcement = cached.content
						return
					}
				} catch (error) {}
				that.$Net.request({
					url: that.$API.GetUpdateUrl(),
					method: 'get',
					header: {'content-type': 'application/json'},
					success(res) {
						const content = res.data && res.data.announcement
						that.announcement = typeof content === 'string' ? content : ''
						if (that.announcement) {
							try {
								uni.setStorageSync(cacheKey, {
									content: that.announcement,
									time: Date.now()
								})
							} catch (error) {}
						}
					}
				})
			},
			goPublish(url) {
				if (this.isLeaving) return
				this.isLeaving = true
				this.isOpen = false
				setTimeout(() => {
					this.unlockPageScroll()
					this.isLeaving = false
					uni.navigateTo({url: url})
				}, 420)
			},
			lockPageScroll() {
				// #ifdef H5
				if (typeof document !== 'undefined') {
					document.documentElement.classList.add('publish-panel-open')
					document.body.classList.add('publish-panel-open')
				}
				// #endif
			},
			unlockPageScroll() {
				// #ifdef H5
				if (typeof document !== 'undefined') {
					document.documentElement.classList.remove('publish-panel-open')
					document.body.classList.remove('publish-panel-open')
				}
				// #endif
			},
			syncTabbarState() {
				// #ifdef H5
				if (typeof document !== 'undefined') {
					const method = this.visible ? 'add' : 'remove'
					document.documentElement.classList[method]('publish-tabbar-enabled')
					document.body.classList[method]('publish-tabbar-enabled')
				}
				// #endif
			},
			removeTabbarState() {
				// #ifdef H5
				if (typeof document !== 'undefined') {
					document.documentElement.classList.remove('publish-tabbar-enabled')
					document.body.classList.remove('publish-tabbar-enabled')
				}
				// #endif
			}
		}
	}
</script>

<style>
	/* #ifdef H5 */
	html.publish-panel-open,
	body.publish-panel-open {
		overflow: hidden !important;
	}
	/* #endif */

	.publish-system {
		--publish-origin-left: 74rpx;
		--publish-origin-bottom: calc(70rpx + env(safe-area-inset-bottom));
	}

	/* #ifdef H5 */
	.publish-system {
		--publish-origin-bottom: calc(42px + env(safe-area-inset-bottom));
	}
	/* #endif */

	.global-publish-trigger {
		position: fixed;
		z-index: 1202;
		left: 28rpx;
		bottom: calc(24rpx + env(safe-area-inset-bottom));
		display: flex;
		align-items: center;
		justify-content: center;
		width: 92rpx;
		height: 92rpx;
		border: 2rpx solid rgba(255, 255, 255, 0.86);
		border-radius: 50%;
		background: rgba(247, 251, 252, 0.96);
		box-shadow: 0 16rpx 46rpx rgba(39, 59, 66, 0.16), inset 0 1rpx 0 rgba(255, 255, 255, 0.78);
		color: #168cf0;
		opacity: 0;
		transform: translateY(10rpx) scale(0.58) translateZ(0);
		transition: opacity 260ms ease, transform 440ms cubic-bezier(0.16, 1, 0.3, 1), box-shadow 240ms ease;
		will-change: transform, opacity;
	}

	/* #ifdef H5 */
	.global-publish-trigger {
		bottom: calc(42px + env(safe-area-inset-bottom) - 46rpx);
	}
	/* #endif */

	.global-publish-trigger.is-ready {
		opacity: 1;
		transform: translateY(0) scale(1) translateZ(0);
	}

	.global-publish-trigger text {
		font-size: 44rpx;
		line-height: 1;
	}

	.global-publish-trigger:active {
		transform: scale(0.92);
		background: rgba(232, 247, 255, 0.9);
		box-shadow: 0 8rpx 22rpx rgba(35, 129, 193, 0.16), inset 0 1rpx 0 rgba(255, 255, 255, 0.82);
	}

	.global-publish-trigger.is-hidden,
	.global-publish-trigger.is-disabled {
		opacity: 0;
		pointer-events: none;
		transform: translateY(8rpx) scale(0.58) translateZ(0);
	}

	.publish-curtain {
		position: fixed;
		z-index: 1201;
		inset: 0;
		background: #fcfcfb;
		clip-path: circle(36rpx at var(--publish-origin-left) calc(100% - var(--publish-origin-bottom)));
		-webkit-clip-path: circle(36rpx at var(--publish-origin-left) calc(100% - var(--publish-origin-bottom)));
		opacity: 0;
		visibility: hidden;
		pointer-events: none;
		transform: translateZ(0);
		transition: clip-path 680ms cubic-bezier(0.16, 1, 0.3, 1), -webkit-clip-path 680ms cubic-bezier(0.16, 1, 0.3, 1), opacity 160ms ease, visibility 0s linear 680ms;
		will-change: clip-path, opacity;
	}

	.publish-curtain.is-open {
		clip-path: circle(150vmax at var(--publish-origin-left) calc(100% - var(--publish-origin-bottom)));
		-webkit-clip-path: circle(150vmax at var(--publish-origin-left) calc(100% - var(--publish-origin-bottom)));
		opacity: 1;
		visibility: visible;
		pointer-events: auto;
		transition-delay: 0s;
	}

	.publish-curtain-scroll {
		height: 100vh;
		height: 100dvh;
	}

	.publish-curtain-content {
		display: flex;
		flex-direction: column;
		min-height: 100vh;
		min-height: 100dvh;
		padding: calc(106rpx + env(safe-area-inset-top)) 96rpx calc(190rpx + env(safe-area-inset-bottom));
		box-sizing: border-box;
	}

	.publish-announcement {
		max-width: 570rpx;
		margin: 0 auto;
		opacity: 0;
		transform: translateY(28rpx);
		transition: opacity 360ms ease 210ms, transform 540ms cubic-bezier(0.16, 1, 0.3, 1) 210ms;
	}

	.is-open .publish-announcement {
		opacity: 1;
		transform: translateY(0);
	}

	.announcement-heading {
		display: block;
		margin-bottom: 26rpx;
		font-size: 54rpx;
		font-weight: 300;
		line-height: 1.25;
		color: #303234;
		letter-spacing: 0;
	}

	.announcement-copy {
		display: block;
		font-size: 29rpx;
		font-weight: 300;
		line-height: 1.78;
		color: #74777a;
		letter-spacing: 0;
	}

	.announcement-copy p,
	.announcement-copy div {
		margin: 0;
	}

	.publish-actions {
		width: 100%;
		max-width: 520rpx;
		margin: auto auto 0;
		padding-top: 96rpx;
		opacity: 0;
		transform: translateY(26rpx);
		transition: opacity 360ms ease 320ms, transform 580ms cubic-bezier(0.16, 1, 0.3, 1) 320ms;
	}

	.is-open .publish-actions {
		opacity: 1;
		transform: translateY(0);
	}

	.publish-option {
		/* 520:112 keeps a calm 4.6:1 primary-action proportion and a >=56px touch target. */
		display: grid;
		grid-template-columns: 76rpx minmax(0, 1fr) 32rpx;
		align-items: center;
		gap: 22rpx;
		width: 100%;
		min-height: 112rpx;
		min-width: 0;
		padding: 18rpx 24rpx 18rpx 18rpx;
		border: 1rpx solid rgba(222, 229, 232, 0.94);
		border-radius: 24rpx;
		background: rgba(247, 250, 250, 0.96);
		box-shadow: 0 16rpx 46rpx rgba(39, 59, 66, 0.13), inset 0 1rpx 0 rgba(255, 255, 255, 0.82);
		box-sizing: border-box;
		color: #354146;
		transition: transform 200ms ease, background-color 200ms ease, box-shadow 200ms ease;
	}

	.publish-option:active {
		transform: scale(0.975);
		background: rgba(238, 247, 252, 0.98);
		box-shadow: 0 9rpx 24rpx rgba(39, 59, 66, 0.14), inset 0 1rpx 0 rgba(255, 255, 255, 0.86);
	}

	.publish-option-icon {
		display: flex;
		align-items: center;
		justify-content: center;
		width: 76rpx;
		height: 76rpx;
		border: 1rpx solid rgba(190, 220, 247, 0.72);
		border-radius: 20rpx;
		background: rgba(222, 239, 255, 0.9);
		font-size: 38rpx;
		color: #168cf0;
	}

	.publish-option-label {
		white-space: nowrap;
		font-size: 31rpx;
		font-weight: 600;
		line-height: 1.2;
		text-align: left;
		letter-spacing: 0;
	}

	.publish-option-arrow {
		font-size: 30rpx;
		color: #89969b;
		text-align: right;
	}

	.publish-option-hint {
		display: block;
		margin-top: 22rpx;
		font-size: 23rpx;
		font-weight: 400;
		line-height: 1.4;
		color: #9aa5aa;
		text-align: center;
		letter-spacing: 0;
	}


	.publish-system.is-night .global-publish-trigger {
		border-color: rgba(255, 255, 255, 0.1);
		background: #347f60;
		box-shadow: 0 12rpx 30rpx rgba(0, 0, 0, 0.24);
		color: #ffffff;
	}

	.publish-system.is-night .global-publish-trigger:active {
		background: #2e7256;
		box-shadow: 0 6rpx 18rpx rgba(0, 0, 0, 0.22);
	}

	.publish-system.is-night .publish-curtain {
		background: #15191b;
	}

	.publish-system.is-night .announcement-heading {
		color: #edf0ef;
	}

	.publish-system.is-night .announcement-copy {
		color: #a1aaa7;
	}

	.publish-system.is-night .publish-option {
		border-color: rgba(226, 232, 230, 0.09);
		background: #212628;
		box-shadow: 0 10rpx 28rpx rgba(0, 0, 0, 0.18);
		color: #edf0ef;
	}

	.publish-system.is-night .publish-option:active {
		background: #292f31;
	}

	.publish-system.is-night .publish-option-icon {
		border-color: rgba(195, 164, 93, 0.22);
		background: #2b332f;
		color: #c3a45d;
	}

	.publish-system.is-night .publish-option-arrow,
	.publish-system.is-night .publish-option-hint {
		color: #929c99;
	}

	@media (max-height: 720px) {
		.publish-curtain-content { padding-top: calc(76rpx + env(safe-area-inset-top)); }
	}

	/* Keep the independent trigger attached to the centered H5 dock on wide screens. */
	@media (min-width: 760px) {
		.publish-system {
			--publish-origin-left: calc(50% - 258px);
		}

		.global-publish-trigger {
			left: calc(50% - 281px);
		}
	}

	@media (prefers-reduced-motion: reduce) {
		.publish-curtain,
		.publish-announcement,
		.publish-actions,
		.global-publish-trigger {
			transition-duration: 0.01ms !important;
			transition-delay: 0s !important;
		}
	}
</style>
