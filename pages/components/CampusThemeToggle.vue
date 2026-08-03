<template>
	<view
		class="campus-theme-toggle"
		:class="[
			'mode-' + currentMode,
			{'is-night': resolvedNight, 'is-manual': currentMode !== 'auto', 'is-compact': compact}
		]"
		:aria-label="buttonLabel"
		@tap.stop="toggleTheme"
		@longpress.stop="restoreAutomatic"
	>
		<view class="theme-toggle-glow"></view>
		<view class="theme-toggle-sheen"></view>
		<view class="theme-toggle-orb">
			<text class="theme-toggle-symbol">{{resolvedNight ? '☾' : '☀'}}</text>
		</view>
		<text class="theme-toggle-label">{{modeLabel}}</text>
		<view class="theme-toggle-status"></view>
	</view>
</template>

<script>
	import {
		CAMPUS_THEME_EVENT,
		applyCampusThemeShell,
		getCampusThemeMode,
		normalizeCampusThemeMode,
		resolveCampusNight,
		setCampusThemeMode
	} from '@/utils/campusTheme.js'

	export default {
		name: 'CampusThemeToggle',
		props: {
			night: {
				type: Boolean,
				default: false
			},
			value: {
				type: String,
				default: ''
			},
			compact: {
				type: Boolean,
				default: false
			}
		},
		data() {
			return {
				currentMode: 'auto',
				ignoreNextTap: false,
				longPressTimer: null
			}
		},
		computed: {
			resolvedNight() {
				return resolveCampusNight(this.currentMode, this.night)
			},
			modeLabel() {
				if (this.currentMode === 'auto') return '自动'
				return this.resolvedNight ? '夜' : '昼'
			},
			buttonLabel() {
				const state = this.currentMode === 'auto' ? '自动主题' : (this.resolvedNight ? '夜间主题' : '日间主题')
				return state + '，点击切换，长按恢复自动'
			}
		},
		watch: {
			value: {
				immediate: true,
				handler(mode) {
					if (mode) this.currentMode = normalizeCampusThemeMode(mode)
				}
			}
		},
		created() {
			if (!this.value) this.currentMode = getCampusThemeMode()
			uni.$on(CAMPUS_THEME_EVENT, this.receiveThemeMode)
		},
		beforeDestroy() {
			uni.$off(CAMPUS_THEME_EVENT, this.receiveThemeMode)
			clearTimeout(this.longPressTimer)
			this.longPressTimer = null
		},
		methods: {
			receiveThemeMode(mode) {
				this.currentMode = normalizeCampusThemeMode(mode)
			},
			applyThemeMode(mode) {
				const nextMode = setCampusThemeMode(mode)
				this.currentMode = nextMode
				applyCampusThemeShell(nextMode)
				uni.$emit(CAMPUS_THEME_EVENT, nextMode)
				this.$emit('input', nextMode)
				this.$emit('change', nextMode)
			},
			toggleTheme() {
				if (this.ignoreNextTap) return
				this.applyThemeMode(this.resolvedNight ? 'day' : 'night')
			},
			restoreAutomatic() {
				this.ignoreNextTap = true
				clearTimeout(this.longPressTimer)
				this.longPressTimer = setTimeout(() => {
					this.ignoreNextTap = false
				}, 420)
				this.applyThemeMode('auto')
				uni.showToast({
					title: '已恢复自动主题',
					icon: 'none'
				})
			}
		}
	}
</script>

<style scoped>
	.campus-theme-toggle {
		position: relative;
		display: inline-flex;
		align-items: center;
		width: 122rpx;
		height: 64rpx;
		padding: 6rpx;
		border: 1rpx solid rgba(255, 255, 255, 0.62);
		border-radius: 999rpx;
		background: linear-gradient(135deg, #00af68 0%, #30d6c2 46%, #ffd05e 100%);
		box-shadow: 0 12rpx 28rpx rgba(0, 132, 61, 0.2), inset 0 1rpx 0 rgba(255, 255, 255, 0.65);
		box-sizing: border-box;
		overflow: hidden;
		isolation: isolate;
		transition: transform 220ms cubic-bezier(0.2, 0.8, 0.2, 1), box-shadow 260ms ease, background-color 260ms ease;
		will-change: transform;
	}

	.campus-theme-toggle.is-compact {
		width: 108rpx;
		height: 58rpx;
		padding: 5rpx;
	}

	.campus-theme-toggle.is-night {
		border-color: rgba(226, 232, 230, 0.18);
		background: #303638;
		box-shadow: 0 10rpx 24rpx rgba(0, 0, 0, 0.2);
	}

	.campus-theme-toggle.is-night .theme-toggle-glow,
	.campus-theme-toggle.is-night .theme-toggle-sheen {
		display: none;
	}

	.campus-theme-toggle:active {
		transform: scale(0.93);
		box-shadow: 0 5rpx 16rpx rgba(0, 88, 61, 0.18), inset 0 1rpx 0 rgba(255, 255, 255, 0.5);
	}

	.theme-toggle-glow {
		position: absolute;
		z-index: -1;
		inset: 5rpx;
		border-radius: inherit;
		background: rgba(255, 255, 255, 0.24);
		animation: themeToggleBreath 3.8s ease-in-out infinite;
		will-change: transform, opacity;
	}

	.theme-toggle-sheen {
		position: absolute;
		z-index: 0;
		top: -26rpx;
		left: -34rpx;
		width: 34rpx;
		height: 116rpx;
		background: rgba(255, 255, 255, 0.5);
		transform: rotate(18deg) translateX(-48rpx);
		opacity: 0;
		transition: transform 520ms ease, opacity 180ms ease;
		pointer-events: none;
	}

	.campus-theme-toggle:active .theme-toggle-sheen {
		transform: rotate(18deg) translateX(170rpx);
		opacity: 0.86;
	}

	.theme-toggle-orb {
		position: absolute;
		z-index: 2;
		top: 7rpx;
		left: 7rpx;
		display: flex;
		align-items: center;
		justify-content: center;
		width: 48rpx;
		height: 48rpx;
		border-radius: 50%;
		background: rgba(255, 255, 255, 0.94);
		box-shadow: 0 6rpx 14rpx rgba(17, 70, 59, 0.22);
		color: #e99a00;
		transform: translateX(0);
		transition: transform 420ms cubic-bezier(0.2, 0.9, 0.25, 1.15), color 260ms ease, background-color 260ms ease;
		will-change: transform;
	}

	.is-compact .theme-toggle-orb {
		top: 6rpx;
		left: 6rpx;
		width: 44rpx;
		height: 44rpx;
	}

	.is-night .theme-toggle-orb {
		transform: translateX(60rpx) rotate(16deg);
		background: #e5e9e7;
		color: #606c78;
	}

	.is-compact.is-night .theme-toggle-orb {
		transform: translateX(52rpx) rotate(16deg);
	}

	.theme-toggle-symbol {
		font-size: 30rpx;
		font-weight: 700;
		line-height: 1;
		letter-spacing: 0;
	}

	.theme-toggle-label {
		position: absolute;
		z-index: 2;
		right: 14rpx;
		max-width: 54rpx;
		font-size: 20rpx;
		font-weight: 700;
		line-height: 1;
		letter-spacing: 0;
		color: rgba(255, 255, 255, 0.96);
		text-align: center;
		transition: left 360ms ease, right 360ms ease, transform 360ms ease, opacity 220ms ease;
	}

	.is-night .theme-toggle-label {
		left: 14rpx;
		right: auto;
	}

	.is-compact .theme-toggle-label {
		font-size: 18rpx;
	}

	.theme-toggle-status {
		position: absolute;
		z-index: 3;
		right: 7rpx;
		top: 7rpx;
		width: 8rpx;
		height: 8rpx;
		border: 2rpx solid rgba(255, 255, 255, 0.9);
		border-radius: 50%;
		background: #e60012;
		box-sizing: border-box;
		transition: transform 320ms ease, background-color 260ms ease, opacity 260ms ease;
	}

	.mode-auto .theme-toggle-status {
		background: #ffd05e;
	}

	.is-night .theme-toggle-status {
		right: auto;
		left: 7rpx;
		background: #6fa487;
	}

	@keyframes themeToggleBreath {
		0%, 100% { transform: scale(0.94); opacity: 0.34; }
		50% { transform: scale(1.05); opacity: 0.68; }
	}

	@media (prefers-reduced-motion: reduce) {
		.campus-theme-toggle,
		.theme-toggle-orb,
		.theme-toggle-sheen,
		.theme-toggle-status {
			transition-duration: 1ms;
		}

		.theme-toggle-glow {
			animation: none;
		}
	}
</style>
