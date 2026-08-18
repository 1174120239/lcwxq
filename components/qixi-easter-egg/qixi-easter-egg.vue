<template>
	<view
		v-if="visible"
		class="qixi-overlay"
		:class="{'is-night': night}"
		role="dialog"
		aria-modal="true"
		aria-label="七夕限定彩蛋"
		@tap="close"
		@touchmove.stop.prevent
	>
		<view class="qixi-sky" aria-hidden="true">
			<view
				v-for="star in stars"
				:key="star.id"
				class="qixi-star"
				:style="star.style"
			></view>
			<view class="qixi-moon"></view>
			<view class="qixi-river"></view>
			<view class="qixi-bridge">
				<text
					v-for="index in 11"
					:key="index"
					class="qixi-bridge-light cuIcon-favorfill"
					:style="bridgeLightStyle(index)"
				></text>
			</view>
			<view class="qixi-figure qixi-figure-left"><view class="qixi-figure-head"></view><view class="qixi-figure-body"></view></view>
			<view class="qixi-figure qixi-figure-right"><view class="qixi-figure-head"></view><view class="qixi-figure-body"></view></view>
		</view>

		<view class="qixi-message" @tap.stop>
			<button class="qixi-close cuIcon-close" aria-label="关闭七夕彩蛋" @tap="close"></button>
			<text class="qixi-kicker">七夕限定 · 聊一论坛</text>
			<text class="qixi-title">今夕何夕，见此良人</text>
			<view class="qixi-divider"><view></view><text class="cuIcon-favorfill"></text><view></view></view>
			<text class="qixi-copy">愿你想见的人，恰好也在想你。\n愿每一份真心，都有温柔回应。</text>
			<text class="qixi-date">2026.08.19</text>
		</view>
	</view>
</template>

<script>
	export default {
		name: 'QixiEasterEgg',
		props: {
			visible: {
				type: Boolean,
				default: false
			},
			night: {
				type: Boolean,
				default: false
			}
		},
		data() {
			return {
				stars: [
					{id: 1, style: 'left: 8%; top: 12%; animation-delay: 0ms;'},
					{id: 2, style: 'left: 18%; top: 27%; animation-delay: 480ms;'},
					{id: 3, style: 'left: 31%; top: 9%; animation-delay: 920ms;'},
					{id: 4, style: 'left: 43%; top: 22%; animation-delay: 260ms;'},
					{id: 5, style: 'left: 57%; top: 12%; animation-delay: 760ms;'},
					{id: 6, style: 'left: 69%; top: 29%; animation-delay: 120ms;'},
					{id: 7, style: 'left: 82%; top: 16%; animation-delay: 1080ms;'},
					{id: 8, style: 'left: 92%; top: 35%; animation-delay: 620ms;'},
					{id: 9, style: 'left: 13%; top: 48%; animation-delay: 840ms;'},
					{id: 10, style: 'left: 88%; top: 52%; animation-delay: 340ms;'}
				]
			}
		},
		methods: {
			close() {
				this.$emit('close')
			},
			bridgeLightStyle(index) {
				const distance = Math.abs(6 - index)
				return {
					left: (index - 1) * 10 + '%',
					top: (distance * distance * 1.35) + 'rpx',
					animationDelay: (index * 90) + 'ms'
				}
			}
		}
	}
</script>

<style scoped>
	.qixi-overlay {
		position: fixed;
		z-index: 1200;
		top: 0;
		right: 0;
		bottom: 0;
		left: 0;
		display: flex;
		align-items: center;
		justify-content: center;
		padding: calc(48rpx + env(safe-area-inset-top)) 42rpx calc(48rpx + env(safe-area-inset-bottom));
		background: linear-gradient(155deg, #111b2c 0%, #24213a 42%, #123d41 100%);
		box-sizing: border-box;
		overflow: hidden;
		animation: qixiFadeIn 360ms ease both;
	}

	.qixi-overlay.is-night {
		background: linear-gradient(155deg, #080d16 0%, #181524 44%, #0b292c 100%);
	}

	.qixi-sky {
		position: absolute;
		inset: 0;
		pointer-events: none;
	}

	.qixi-star {
		position: absolute;
		width: 5rpx;
		height: 5rpx;
		border-radius: 50%;
		background: #fff8d9;
		box-shadow: 0 0 16rpx rgba(255, 238, 176, 0.9);
		animation: qixiTwinkle 1.8s ease-in-out infinite alternate;
	}

	.qixi-moon {
		position: absolute;
		top: calc(80rpx + env(safe-area-inset-top));
		right: 11%;
		width: 96rpx;
		height: 96rpx;
		border-radius: 50%;
		background: #f9e8b9;
		box-shadow: 0 0 52rpx rgba(249, 232, 185, 0.42);
	}

	.qixi-moon::after {
		content: '';
		position: absolute;
		top: -10rpx;
		left: -22rpx;
		width: 96rpx;
		height: 96rpx;
		border-radius: 50%;
		background: #192038;
	}

	.is-night .qixi-moon::after {
		background: #101421;
	}

	.qixi-river {
		position: absolute;
		top: 44%;
		left: -12%;
		width: 124%;
		height: 118rpx;
		background: linear-gradient(90deg, rgba(102, 212, 210, 0.04), rgba(121, 194, 230, 0.24), rgba(239, 166, 192, 0.2), rgba(102, 212, 210, 0.04));
		transform: rotate(-7deg);
		filter: blur(8rpx);
	}

	.qixi-bridge {
		position: absolute;
		top: 34%;
		left: 20%;
		width: 60%;
		height: 120rpx;
	}

	.qixi-bridge-light {
		position: absolute;
		color: #f3a7bd;
		font-size: 25rpx;
		text-shadow: 0 0 18rpx rgba(243, 167, 189, 0.65);
		transform: translateX(-50%);
		animation: qixiBridgeGlow 1.7s ease-in-out infinite alternate;
	}

	.qixi-figure {
		position: absolute;
		top: calc(34% + 78rpx);
		width: 46rpx;
		height: 82rpx;
	}

	.qixi-figure-left { left: 17%; }
	.qixi-figure-right { right: 17%; transform: scaleX(-1); }

	.qixi-figure-head {
		width: 24rpx;
		height: 24rpx;
		margin-left: 11rpx;
		border-radius: 50%;
		background: #10141e;
	}

	.qixi-figure-body {
		width: 40rpx;
		height: 58rpx;
		margin: -1rpx 0 0 6rpx;
		border-radius: 18rpx 18rpx 5rpx 5rpx;
		background: #10141e;
		transform: skewX(-8deg);
	}

	.qixi-message {
		position: relative;
		z-index: 2;
		display: flex;
		width: min(610rpx, 100%);
		min-height: 500rpx;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		margin-top: 300rpx;
		padding: 74rpx 46rpx 60rpx;
		border: 1rpx solid rgba(255, 255, 255, 0.2);
		border-radius: 16rpx;
		background: rgba(12, 18, 29, 0.82);
		box-shadow: 0 28rpx 80rpx rgba(0, 0, 0, 0.3);
		box-sizing: border-box;
		text-align: center;
		animation: qixiMessageIn 520ms cubic-bezier(0.22, 1, 0.36, 1) both;
	}

	.qixi-close {
		position: absolute;
		top: 18rpx;
		right: 18rpx;
		display: flex;
		width: 64rpx;
		height: 64rpx;
		align-items: center;
		justify-content: center;
		margin: 0;
		padding: 0;
		border: 0;
		border-radius: 50%;
		background: rgba(255, 255, 255, 0.1);
		color: #f7f1eb;
		font-size: 28rpx;
		line-height: 1;
	}

	.qixi-close::after { border: 0; }

	.qixi-kicker {
		color: #efc87f;
		font-size: 24rpx;
		line-height: 36rpx;
	}

	.qixi-title {
		margin-top: 22rpx;
		color: #fffaf3;
		font-family: serif;
		font-size: 46rpx;
		font-weight: 600;
		line-height: 1.45;
	}

	.qixi-divider {
		display: flex;
		width: 250rpx;
		align-items: center;
		gap: 18rpx;
		margin: 30rpx 0 26rpx;
		color: #f3a7bd;
		font-size: 25rpx;
	}

	.qixi-divider view {
		flex: 1;
		height: 1rpx;
		background: rgba(239, 200, 127, 0.48);
	}

	.qixi-copy {
		color: rgba(255, 250, 243, 0.86);
		font-size: 28rpx;
		line-height: 1.9;
	}

	.qixi-date {
		margin-top: 28rpx;
		color: rgba(255, 250, 243, 0.48);
		font-size: 21rpx;
		line-height: 30rpx;
	}

	@keyframes qixiFadeIn {
		from { opacity: 0; }
		to { opacity: 1; }
	}

	@keyframes qixiMessageIn {
		from { opacity: 0; transform: translateY(32rpx) scale(0.96); }
		to { opacity: 1; transform: translateY(0) scale(1); }
	}

	@keyframes qixiTwinkle {
		from { opacity: 0.35; transform: scale(0.7); }
		to { opacity: 1; transform: scale(1.3); }
	}

	@keyframes qixiBridgeGlow {
		from { opacity: 0.45; transform: translateX(-50%) translateY(0); }
		to { opacity: 1; transform: translateX(-50%) translateY(-5rpx); }
	}

	@media (prefers-reduced-motion: reduce) {
		.qixi-overlay,
		.qixi-message,
		.qixi-star,
		.qixi-bridge-light {
			animation: none;
		}
	}
</style>
