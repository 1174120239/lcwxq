<template>
	<view v-if="visible" class="wgt-progress-layer">
		<view class="wgt-progress-backdrop"></view>
		<view class="wgt-progress-card">
			<view class="wgt-progress-icon" :class="stage === 'installing' ? 'is-installing' : ''">
				<text class="cuIcon-refresh"></text>
			</view>
			<view class="wgt-progress-title">{{ stageTitle }}</view>
			<view class="wgt-progress-version">版本 {{ version || '新版本' }}</view>
			<view class="wgt-progress-track">
				<view class="wgt-progress-fill" :style="{ width: safeProgress + '%' }"></view>
			</view>
			<view class="wgt-progress-meta">
				<text>{{ safeProgress }}%</text>
				<text>{{ stageHint }}</text>
			</view>
			<view class="wgt-progress-footnote">请保持 App 在前台，完成后会自动重启</view>
		</view>
	</view>
</template>

<script>
	export default {
		name: 'WgtDownloadOverlay',
		props: {
			visible: { type: Boolean, default: false },
			progress: { type: Number, default: 0 },
			stage: { type: String, default: 'downloading' },
			version: { type: String, default: '' }
		},
		computed: {
			safeProgress() {
				return Math.max(0, Math.min(100, Number(this.progress) || 0))
			},
			stageTitle() {
				return this.stage === 'installing' ? '正在应用更新' : '正在下载更新'
			},
			stageHint() {
				return this.stage === 'installing' ? '正在准备重启' : '资源下载中'
			}
		}
	}
</script>

<style>
	.wgt-progress-layer {
		position: fixed;
		inset: 0;
		z-index: 3200;
		display: flex;
		align-items: center;
		justify-content: center;
		padding: 32rpx;
		box-sizing: border-box;
	}

	.wgt-progress-backdrop {
		position: absolute;
		inset: 0;
		background: rgba(13, 26, 28, .66);
	}

	.wgt-progress-card {
		position: relative;
		z-index: 1;
		width: min(620rpx, 100%);
		min-height: 350rpx;
		padding: 42rpx 38rpx 34rpx;
		display: flex;
		flex-direction: column;
		align-items: center;
		border: 1rpx solid rgba(255, 255, 255, .8);
		border-radius: 22rpx;
		background: #fff;
		box-shadow: 0 26rpx 72rpx rgba(10, 35, 37, .3);
		box-sizing: border-box;
		animation: wgt-progress-card-in 220ms ease-out both;
	}

	.wgt-progress-icon {
		width: 82rpx;
		height: 82rpx;
		display: flex;
		align-items: center;
		justify-content: center;
		border-radius: 50%;
		background: #e2f4f0;
		color: #197f76;
		font-size: 40rpx;
		animation: wgt-progress-spin 1.2s linear infinite;
	}

	.wgt-progress-icon.is-installing {
		animation-duration: 1.8s;
		background: #eaf0ff;
		color: #3e6eb8;
	}

	.wgt-progress-title {
		margin-top: 24rpx;
		font-size: 34rpx;
		font-weight: 700;
		line-height: 1.3;
		color: #1d3537;
	}

	.wgt-progress-version {
		margin-top: 10rpx;
		min-height: 36rpx;
		font-size: 24rpx;
		color: #16827e;
	}

	.wgt-progress-track {
		width: 100%;
		height: 14rpx;
		margin-top: 30rpx;
		overflow: hidden;
		border-radius: 99rpx;
		background: #e8efee;
	}

	.wgt-progress-fill {
		height: 100%;
		border-radius: inherit;
		background: #218d83;
		transition: width 220ms cubic-bezier(.2, .8, .2, 1);
	}

	.wgt-progress-meta {
		width: 100%;
		min-height: 34rpx;
		margin-top: 14rpx;
		display: flex;
		align-items: center;
		justify-content: space-between;
		font-size: 23rpx;
		color: #6f817e;
	}

	.wgt-progress-meta text:first-child {
		font-size: 26rpx;
		font-weight: 700;
		color: #197f76;
	}

	.wgt-progress-footnote {
		min-height: 34rpx;
		margin-top: 20rpx;
		font-size: 22rpx;
		line-height: 1.55;
		text-align: center;
		color: #889592;
	}

	@keyframes wgt-progress-card-in {
		from { opacity: 0; transform: translateY(12rpx) scale(.985); }
		to { opacity: 1; transform: none; }
	}

	@keyframes wgt-progress-spin {
		from { transform: rotate(0deg); }
		to { transform: rotate(360deg); }
	}

	@media (prefers-reduced-motion: reduce) {
		.wgt-progress-card, .wgt-progress-icon { animation-duration: 1ms !important; }
		.wgt-progress-fill { transition-duration: 1ms !important; }
	}
</style>
