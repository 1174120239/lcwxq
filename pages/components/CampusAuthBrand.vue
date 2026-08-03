<template>
	<view class="campus-auth-brand" :class="{'is-compact': compact}">
		<view class="auth-emblem-shell">
			<image class="auth-emblem" src="/static/branding/liaocheng-no1-emblem.png" mode="aspectFill"></image>
			<view class="auth-emblem-glint"></view>
		</view>
		<view class="auth-brand-copy">
			<text class="auth-school-name">聊城一中论坛</text>
			<text class="auth-school-meta">{{caption}}</text>
		</view>
	</view>
</template>

<script>
	export default {
		name: 'CampusAuthBrand',
		props: {
			caption: {
				type: String,
				default: '1942 · 记录校园，连接同学'
			},
			compact: {
				type: Boolean,
				default: false
			}
		}
	}
</script>

<style scoped>
	.campus-auth-brand {
		display: flex;
		align-items: center;
		gap: 20rpx;
		min-width: 0;
		color: var(--auth-brand-title, #153b33);
	}

	.auth-emblem-shell {
		position: relative;
		flex: 0 0 88rpx;
		width: 88rpx;
		height: 88rpx;
		padding: 5rpx;
		border: 2rpx solid var(--auth-brand-ring, rgba(0, 132, 61, 0.28));
		border-radius: 50%;
		background: var(--auth-brand-surface, rgba(255, 255, 255, 0.86));
		box-shadow: 0 12rpx 28rpx var(--auth-brand-shadow, rgba(0, 104, 64, 0.14)), inset 0 1rpx 0 rgba(255, 255, 255, 0.72);
		box-sizing: border-box;
		overflow: hidden;
	}

	.auth-emblem {
		display: block;
		width: 100%;
		height: 100%;
		border-radius: 50%;
	}

	.auth-emblem-glint {
		position: absolute;
		top: 7rpx;
		right: 10rpx;
		width: 12rpx;
		height: 12rpx;
		border-radius: 50%;
		background: #f4c95b;
		box-shadow: 0 0 0 4rpx rgba(244, 201, 91, 0.16);
	}

	.auth-brand-copy {
		display: flex;
		min-width: 0;
		flex-direction: column;
		gap: 6rpx;
	}

	.auth-school-name {
		font-size: 30rpx;
		font-weight: 800;
		line-height: 1.25;
		letter-spacing: 0;
		color: var(--auth-brand-title, #153b33);
	}

	.auth-school-meta {
		font-size: 20rpx;
		line-height: 1.4;
		letter-spacing: 0;
		color: var(--auth-brand-muted, #6d817b);
	}

	.campus-auth-brand.is-compact {
		gap: 14rpx;
	}

	.is-compact .auth-emblem-shell {
		flex-basis: 68rpx;
		width: 68rpx;
		height: 68rpx;
		padding: 4rpx;
	}

	.is-compact .auth-school-name {
		font-size: 26rpx;
	}

	.is-compact .auth-school-meta {
		font-size: 18rpx;
	}
</style>
