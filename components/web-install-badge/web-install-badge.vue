<template>
	<!-- #ifdef H5 -->
	<view class="web-install-badge" @tap="openWebsite">
		<text class="badge-line">回到官网</text>
		<text class="badge-line">安装app</text>
	</view>
	<!-- #endif -->
</template>

<script>
	export default {
		name: 'WebInstallBadge',
		methods: {
			openWebsite() {
				// #ifdef H5
				if (typeof window !== 'undefined') window.open('https://lcxqy.cn/', '_blank', 'noopener')
				// #endif
			}
		}
	}
</script>

<style scoped>
.web-install-badge {
	position: fixed;
	left: 20px;
	bottom: calc(64px + env(safe-area-inset-bottom));
	z-index: 120;
	padding: 0;
	background: transparent;
	color: var(--campus-text, #243633);
	font-size: 18px;
	font-weight: 600;
	letter-spacing: 2px;
	line-height: 1.35;
	text-shadow: 0 0 2px currentColor, 0 0 9px currentColor, 0 1px 5px rgba(255, 255, 255, .72);
	filter: blur(.18px);
	opacity: .55;
}

.badge-line { display: block; }

.web-install-badge:active { opacity: .68; }
</style>
