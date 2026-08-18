<template>
	<view class="publish-system" :class="{'is-night': night, 'is-editor-hidden': editorOpen, 'is-page-hidden': !pageActive}">
		<view class="global-publish-trigger"
			:class="{'is-ready': isReady, 'is-hidden': isOpen || isLeaving, 'is-disabled': !visible}"
			:style="triggerChromeStyle"
			@tap.stop="openPanel">
			<text class="cuIcon-add"></text>
		</view>

		<view class="publish-curtain" :class="{'is-open': isOpen, 'is-leaving': isLeaving}" @tap="closePanel">
			<view class="publish-curtain-mask" @tap="closePanel"></view>
			<view class="publish-sheet" @tap="closePanel">
				<view class="publish-sheet-handle" aria-hidden="true"></view>
				<scroll-view scroll-y class="publish-curtain-scroll" @tap.stop="closePanel">
					<view class="publish-curtain-content">
					<!-- Protected UI baseline: keep in sync with AI_PROJECT_BRIEF.md section 22. -->
					<view class="publish-announcement">
						<text class="announcement-heading">近期公告</text>
						<rich-text class="announcement-copy" :nodes="announcement || fallbackAnnouncement"></rich-text>
					</view>

					<view class="publish-actions">
						<view class="publish-option publish-option-primary" @tap.stop="goPublish('/pages/space/post')">
							<view class="publish-option-icon"><text class="cuIcon-camera"></text></view>
							<view class="publish-option-copy">
								<text class="publish-option-label">发布动态</text>
								<text class="publish-option-desc">分享此刻的校园生活</text>
							</view>
							<text class="publish-option-arrow cuIcon-right"></text>
						</view>
						<view class="publish-option-grid">
							<view class="publish-option publish-option-secondary" @tap.stop="goPublish('/pages/qa/post')">
								<view class="publish-option-icon question-icon"><text class="cuIcon-question"></text></view>
								<text class="publish-option-label">提出问题</text>
								<text class="publish-option-arrow cuIcon-right"></text>
							</view>
							<view class="publish-option publish-option-secondary" @tap.stop="goPublish('/pages/space/post?anonymous=1')">
								<view class="publish-option-icon anonymous-icon"><text class="cuIcon-notice"></text></view>
								<text class="publish-option-label">匿名动态</text>
								<text class="publish-option-arrow cuIcon-right"></text>
							</view>
						</view>
						<view class="publish-option publish-option-mutual-aid" @tap.stop="goPublish('/pages/user/addshop')">
							<view class="publish-option-icon mutual-aid-icon"><text class="cuIcon-friendadd"></text></view>
							<view class="publish-option-copy">
								<text class="publish-option-label">发布求助信息</text>
								<text class="publish-option-desc">寻找帮助，也可以主动提供帮助</text>
							</view>
							<text class="publish-option-arrow cuIcon-right"></text>
						</view>
						<text class="publish-option-hint">匿名动态不展示真实身份，请遵守社区规范</text>
					</view>
					</view>
				</scroll-view>
			</view>
		</view>
	</view>
</template>

<script>
	import { CAMPUS_CHROME_EVENT } from '@/utils/campusChrome.js'

	export default {
		name: 'PublishPanel',
		props: {
			visible: {
				type: Boolean,
				default: true
			},
			tabScope: {
				type: String,
				default: ''
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
				pageActive: false,
			editorOpen: false,
			chromeProgress: 0,
				introTimer: null,
				closeTimer: null,
				navigateTimer: null,
				announcement: '',
				fallbackAnnouncement: '欢迎来到校园社区。分享日常、记录成长，也请尊重每一位认真表达的人。'
			}
		},
		created() {
			if (this.visible) this.loadAnnouncement()
		},
		mounted() {
			this.$root.$on('campus-editor-visibility', this.handleEditorVisibility)
			this.$root.$on('campus-tab-activity', this.handleTabActivity)
			uni.$on(CAMPUS_CHROME_EVENT, this.handleChromeVisibility)
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
			this.$root.$off('campus-editor-visibility', this.handleEditorVisibility)
			this.$root.$off('campus-tab-activity', this.handleTabActivity)
			uni.$off(CAMPUS_CHROME_EVENT, this.handleChromeVisibility)
			clearTimeout(this.introTimer)
			clearTimeout(this.closeTimer)
			clearTimeout(this.navigateTimer)
			if (typeof document !== 'undefined' && document.documentElement.__campusTabbarTransitionTimer) {
				clearTimeout(document.documentElement.__campusTabbarTransitionTimer)
				document.documentElement.__campusTabbarTransitionTimer = null
			}
			this.removeTabbarState()
			this.unlockPageScroll()
		},
		computed: {
			triggerChromeStyle() {
				const progress = Math.max(0, Math.min(1, Number(this.chromeProgress) || 0))
				if (!progress) return {}
				return {
					opacity: this.visible && this.isReady && !this.isOpen && !this.isLeaving && !this.editorOpen ? String(1 - progress) : '0',
					transform: `translateY(${110 * progress}px) scale(${1 - (0.42 * progress)}) translateZ(0)`
				}
			}
		},
		methods: {
			handleChromeVisibility(state) {
				const progress = state && typeof state === 'object' ? state.progress : (state ? 1 : 0)
				this.chromeProgress = Math.max(0, Math.min(1, Number(progress) || 0))
			},
			handleTabActivity(scope) {
				if (!this.tabScope) return
				this.pageActive = scope === this.tabScope
				this.syncTabbarState()
				if (!this.pageActive) {
					this.resetPanel()
				} else if (this.visible && !this.editorOpen) {
					this.activatePage()
				}
			},
			handleEditorVisibility(visible) {
				this.editorOpen = Boolean(visible)
				if (this.editorOpen) {
					this.resetPanel()
				} else if (this.visible) {
					this.activatePage()
				}
			},
			activatePage() {
				if (!this.visible) {
					this.pageActive = false
					this.syncTabbarState()
					this.resetPanel()
					return
				}
				this.pageActive = true
				this.syncTabbarState()
				this.playIntro()
			},
			deactivatePage() {
				this.pageActive = false
				this.syncTabbarState()
				this.resetPanel()
			},
			playIntro() {
				if (!this.visible || this.isReady) return
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
				clearTimeout(this.navigateTimer)
				this.isReady = false
				this.isOpen = false
				this.isLeaving = false
				this.unlockPageScroll()
			},
			openPanel() {
				if (!this.visible || !this.pageActive || this.editorOpen || !this.isReady || this.isOpen || this.isLeaving) return
				clearTimeout(this.closeTimer)
				clearTimeout(this.navigateTimer)
				this.isLeaving = false
				this.isOpen = true
				this.lockPageScroll()
			},
			closePanel() {
				if ((!this.isOpen && !this.isLeaving) || this.isLeaving) return
				this.isLeaving = true
				this.isOpen = false
				clearTimeout(this.closeTimer)
				this.closeTimer = setTimeout(() => {
					this.isLeaving = false
					this.unlockPageScroll()
				}, 320)
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
				if (!this.isOpen || this.isLeaving) return
				this.isLeaving = true
				this.isOpen = false
				clearTimeout(this.closeTimer)
				clearTimeout(this.navigateTimer)
				this.navigateTimer = setTimeout(() => {
					this.unlockPageScroll()
					this.isLeaving = false
					uni.navigateTo({url: url})
				}, 320)
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
					const roots = [document.documentElement, document.body]
					const active = this.visible && this.pageActive && !this.editorOpen
					const root = document.documentElement
					const wasActive = root.classList.contains('publish-tabbar-enabled')
					const initialized = root.getAttribute('data-campus-tabbar-ready') === '1'
					if (!initialized) root.setAttribute('data-campus-tabbar-ready', '1')
					if (initialized && wasActive !== active) {
						const originClass = active ? 'campus-tabbar-from-plain' : 'campus-tabbar-from-publish'
						const otherOriginClass = active ? 'campus-tabbar-from-publish' : 'campus-tabbar-from-plain'
						roots.forEach((target) => {
							if (!target) return
							target.classList.remove(otherOriginClass)
							target.classList.add(originClass)
							target.classList.toggle('publish-tabbar-enabled', active)
						})
						clearTimeout(root.__campusTabbarTransitionTimer)
						root.__campusTabbarTransitionTimer = setTimeout(() => {
							roots.forEach((target) => target && target.classList.remove(originClass))
							root.__campusTabbarTransitionTimer = null
						}, 32)
						return
					}
					roots.forEach((target) => {
						if (target) target.classList.toggle('publish-tabbar-enabled', active)
					})
				}
				// #endif
			},
			removeTabbarState() {
				// #ifdef H5
				if (typeof document !== 'undefined') {
					const roots = [document.documentElement, document.body]
					roots.forEach((target) => {
						if (!target) return
						target.classList.remove('publish-tabbar-enabled', 'campus-tabbar-from-plain', 'campus-tabbar-from-publish')
					})
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

	html.campus-editor-open .publish-system,
	body.campus-editor-open .publish-system {
		visibility: hidden !important;
		pointer-events: none !important;
	}
	/* #endif */

	.publish-system {
		--publish-primary: #237c74;
		--publish-background: #f5f8f7;
		--publish-surface: #ffffff;
	}

	.global-publish-trigger {
		position: fixed;
		z-index: 1202;
		left: 28rpx;
		/* Share the navigation bar's center line while staying in its own left slot. */
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
		color: var(--publish-primary, #237c74);
		opacity: 0;
		transform: translateY(10rpx) scale(0.58) translateZ(0);
		transition: opacity 360ms ease, transform 560ms cubic-bezier(0.16, 1, 0.3, 1), box-shadow 280ms ease;
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
		background: #e6f3f1;
		box-shadow: 0 8rpx 22rpx rgba(35, 124, 116, 0.16), inset 0 1rpx 0 rgba(255, 255, 255, 0.82);
	}

	.global-publish-trigger.is-hidden,
	.global-publish-trigger.is-disabled,
	.publish-system.is-editor-hidden .global-publish-trigger {
		opacity: 0;
		pointer-events: none;
		transform: translateY(8rpx) scale(0.58) translateZ(0);
	}

	.publish-curtain {
		position: fixed;
		z-index: 1201;
		inset: 0;
		background: transparent;
		opacity: 0;
		visibility: hidden;
		pointer-events: none;
		transition: opacity 220ms ease, visibility 0s linear 320ms;
		will-change: opacity;
	}

	.publish-curtain.is-open,
	.publish-curtain.is-leaving {
		opacity: 1;
		visibility: visible;
		transition-delay: 0s;
	}

	.publish-curtain.is-open {
		pointer-events: auto;
	}

	.publish-curtain.is-leaving { pointer-events: none; }

	.publish-system.is-editor-hidden {
		visibility: hidden;
		pointer-events: none;
	}

	.publish-curtain-mask {
		position: absolute;
		inset: 0;
		background: rgba(24, 40, 38, 0.22);
	}

	.publish-sheet {
		position: absolute;
		left: 50%;
		right: auto;
		bottom: 0;
		width: 100%;
		max-width: 760px;
		height: 62vh;
		max-height: 1160rpx;
		min-height: 0;
		border-radius: 34rpx 34rpx 0 0;
		background: var(--publish-background, #f5f8f7);
		box-shadow: 0 -18rpx 60rpx rgba(39, 59, 66, 0.18);
		overflow: hidden;
		transform: translate3d(-50%, 100%, 0);
		transition: transform 320ms cubic-bezier(0.22, 1, 0.36, 1);
		will-change: transform;
	}

	.publish-curtain.is-open .publish-sheet {
		transform: translate3d(-50%, 0, 0);
	}

	.publish-sheet-handle {
		position: absolute;
		z-index: 2;
		top: 16rpx;
		left: 50%;
		width: 72rpx;
		height: 8rpx;
		border-radius: 8rpx;
		background: rgba(115, 133, 129, 0.28);
		transform: translateX(-50%);
	}

	.publish-curtain-scroll {
		height: 100%;
	}

	.publish-curtain-content {
		display: flex;
		flex-direction: column;
		min-height: 100%;
		padding: calc(40rpx + env(safe-area-inset-top)) 28rpx calc(24rpx + env(safe-area-inset-bottom));
		box-sizing: border-box;
	}

	.publish-announcement {
		width: 100%;
		max-width: 640rpx;
		margin: 0 auto;
		opacity: 0;
		transform: translateY(28rpx);
		transition: opacity 260ms ease 80ms, transform 320ms cubic-bezier(0.22, 1, 0.36, 1) 80ms;
	}

	.is-open .publish-announcement {
		opacity: 1;
		transform: translateY(0);
	}

	.announcement-heading {
		display: block;
		margin-bottom: 10rpx;
		font-size: 42rpx;
		font-weight: 300;
		line-height: 1.15;
		color: #303234;
		letter-spacing: 0;
	}

	.announcement-copy {
		display: -webkit-box;
		font-size: 25rpx;
		font-weight: 300;
		line-height: 1.35;
		color: #74777a;
		letter-spacing: 0;
		-webkit-box-orient: vertical;
		-webkit-line-clamp: 2;
		overflow: hidden;
	}

	.announcement-copy p,
	.announcement-copy div {
		margin: 0;
	}

	.publish-actions {
		width: 100%;
		max-width: 640rpx;
		margin: auto auto clamp(56rpx, 5.5vh, 104rpx);
		padding-top: clamp(72rpx, 6vh, 112rpx);
		opacity: 0;
		transform: translateY(calc(4% + 26rpx));
		transition: opacity 260ms ease 150ms, transform 360ms cubic-bezier(0.22, 1, 0.36, 1) 150ms;
	}

	.is-open .publish-actions {
		opacity: 1;
		transform: translateY(4%);
	}

	.is-open .publish-option {
		opacity: 1;
		transform: translateY(0);
	}

	.publish-option {
		position: relative;
		box-sizing: border-box;
		border: 1rpx solid rgba(222, 229, 232, 0.94);
		border-radius: 24rpx;
		background: var(--publish-surface, #ffffff);
		box-shadow: 0 16rpx 46rpx rgba(39, 59, 66, 0.13), inset 0 1rpx 0 rgba(255, 255, 255, 0.82);
		color: #354146;
		opacity: 0;
		transform: translateY(18rpx);
		transition: opacity 280ms ease, transform 420ms cubic-bezier(0.22, 1, 0.36, 1), background-color 200ms ease, box-shadow 200ms ease;
	}

	.publish-option-primary {
		display: grid;
		grid-template-columns: 76rpx minmax(0, 1fr) 30rpx;
		align-items: center;
		gap: 20rpx;
		min-height: 136rpx;
		padding: 18rpx 22rpx 18rpx 18rpx;
		border-color: rgba(166, 211, 202, 0.76);
		background: #edf7f5;
		box-shadow: 0 18rpx 44rpx rgba(35, 124, 116, 0.13), inset 0 1rpx 0 rgba(255, 255, 255, 0.9);
		transition-delay: 40ms;
	}

	.publish-option-grid {
		display: grid;
		grid-template-columns: repeat(2, minmax(0, 1fr));
		gap: 16rpx;
		margin-top: 16rpx;
	}

	.publish-option-secondary {
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		min-width: 0;
		min-height: 142rpx;
		padding: 18rpx 12rpx 16rpx;
		text-align: center;
	}

	.publish-option-mutual-aid {
		display: grid;
		grid-template-columns: 64rpx minmax(0, 1fr) 28rpx;
		align-items: center;
		gap: 18rpx;
		min-height: 112rpx;
		margin-top: 16rpx;
		padding: 16rpx 22rpx 16rpx 18rpx;
		border-color: rgba(139, 195, 162, 0.7);
		background: #f1f8f4;
		transition-delay: 220ms;
	}

	.publish-option-mutual-aid .publish-option-icon {
		width: 64rpx;
		height: 64rpx;
		font-size: 30rpx;
	}

	.publish-option-grid .publish-option-secondary:first-child {
		transition-delay: 110ms;
	}

	.publish-option-grid .publish-option-secondary:last-child {
		transition-delay: 170ms;
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
		font-size: 36rpx;
		color: #4c8fc5;
	}

	.publish-option-secondary .publish-option-icon {
		width: 64rpx;
		height: 64rpx;
		margin-bottom: 10rpx;
		font-size: 30rpx;
	}

	.publish-option-icon.question-icon {
		border-color: rgba(116, 194, 164, 0.72);
		background: rgba(221, 244, 235, 0.92);
		color: #2f9188;
	}

	.publish-option-icon.anonymous-icon {
		border-color: rgba(218, 188, 128, 0.76);
		background: rgba(255, 245, 226, 0.96);
		color: #b88b4d;
	}

	.publish-option-icon.mutual-aid-icon {
		border-color: rgba(109, 184, 141, 0.7);
		background: rgba(224, 245, 233, 0.94);
		color: #287f5d;
	}

	.publish-option-copy {
		min-width: 0;
	}

	.publish-option-label {
		display: block;
		white-space: nowrap;
		font-size: 30rpx;
		font-weight: 600;
		line-height: 1.2;
		text-align: left;
		letter-spacing: 0;
	}

	.publish-option-secondary .publish-option-label {
		font-size: 28rpx;
		line-height: 1.3;
		text-align: center;
	}

	.publish-option-desc {
		display: block;
		margin-top: 4rpx;
		font-size: 22rpx;
		font-weight: 400;
		line-height: 1.35;
		color: #6d8984;
		white-space: nowrap;
	}

	.publish-option-arrow {
		font-size: 28rpx;
		color: #89969b;
		text-align: right;
	}

	.publish-option-secondary .publish-option-arrow {
		position: absolute;
		top: 10rpx;
		right: 12rpx;
		font-size: 22rpx;
	}

	.publish-option-hint {
		display: block;
		margin-top: 16rpx;
		font-size: 21rpx;
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
		background: transparent;
	}

	.publish-system.is-night .publish-curtain-mask {
		background: rgba(6, 9, 9, 0.58);
	}

	.publish-system.is-night .publish-sheet {
		background: #202527;
		box-shadow: 0 -18rpx 60rpx rgba(0, 0, 0, 0.3);
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

	.publish-system.is-night .publish-option-primary {
		border-color: rgba(113, 177, 151, 0.28);
		background: #263a35;
		box-shadow: 0 14rpx 32rpx rgba(0, 0, 0, 0.2);
	}

	.publish-system.is-night .publish-option-mutual-aid {
		border-color: rgba(104, 169, 132, 0.28);
		background: #26352f;
	}

	.publish-system.is-night .publish-option-icon {
		border-color: rgba(195, 164, 93, 0.22);
		background: #2b332f;
		color: #c3a45d;
	}

	.publish-system.is-night .publish-option-desc {
		color: #9eb7ae;
	}

	.publish-system.is-night .publish-option-arrow,
	.publish-system.is-night .publish-option-hint {
		color: #929c99;
	}

	@media (max-height: 720px) {
		.publish-sheet { height: 64vh; max-height: 1160rpx; min-height: 0; }
		.publish-curtain-content { padding-top: calc(32rpx + env(safe-area-inset-top)); }
	}

	/* H5 keeps the trigger in the same 42px center line as its 60px native dock. */
	/* #ifdef H5 */
	@media (max-width: 759px) {
		.global-publish-trigger {
			/* Touch the dock's left shoulder so both controls read as one rail. */
			left: 58px;
			width: 48px;
			height: 48px;
			border-width: 3px;
		}
	}

	@media (min-width: 760px) {
		.global-publish-trigger {
			bottom: calc(16px + env(safe-area-inset-bottom));
			width: 52px;
			height: 52px;
			border-width: 3px;
		}

		.global-publish-trigger text {
			font-size: 27px;
		}
	}
	/* #endif */

	/* Keep the original trigger aligned with the bounded wide-screen dock. */
	@media (min-width: 760px) {
		.publish-system {
			--publish-origin-left: calc(50% - 258px);
		}

		.global-publish-trigger {
			left: calc(50% - 186px);
		}
	}

	@media (prefers-reduced-motion: reduce) {
		.publish-curtain,
		.publish-sheet,
		.publish-announcement,
		.publish-actions,
		.publish-option,
		.global-publish-trigger {
			transition-duration: 0.01ms !important;
			transition-delay: 0s !important;
		}
	}
</style>
