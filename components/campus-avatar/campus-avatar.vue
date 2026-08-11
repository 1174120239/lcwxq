<template>
	<view class="campus-avatar" @tap="$emit('tap', $event)">
		<text v-if="!showImage" :key="'fallback-' + avatarRenderKey" class="campus-avatar__fallback" :class="fallbackIconClass">
			{{ fallbackText }}
		</text>
		<image
			v-if="showImage"
			:key="'image-' + avatarRenderKey"
			class="campus-avatar__image"
			:src="avatarUrl"
			mode="aspectFill"
			@error="handleImageError"
		></image>
	</view>
</template>

<script>
	import { avatarInitial, normalizeAvatarUrl } from '@/utils/avatar.js'

	export default {
		name: 'campusAvatar',
		props: {
			src: {
				type: String,
				default: ''
			},
			name: {
				type: String,
				default: ''
			},
			fallbackIcon: {
				type: String,
				default: 'people'
			}
		},
		data() {
			return {
				loadFailed: false
			};
		},
		computed: {
			avatarUrl() {
				return normalizeAvatarUrl(this.src);
			},
			avatarRenderKey() {
				return [this.avatarUrl, this.name, this.loadFailed ? 'failed' : 'ready'].join('|');
			},
			showImage() {
				return this.avatarUrl !== '' && !this.loadFailed;
			},
			fallbackText() {
				return avatarInitial(this.name);
			},
			fallbackIconClass() {
				return this.fallbackText ? '' : 'cuIcon-' + this.fallbackIcon;
			}
		},
		watch: {
			src() {
				this.loadFailed = false;
			}
		},
		methods: {
			handleImageError() {
				this.loadFailed = true;
				this.$emit('error', this.src);
			}
		}
	}
</script>

<style scoped>
	.campus-avatar {
		position: relative;
		display: inline-flex;
		align-items: center;
		justify-content: center;
		overflow: hidden;
		background-color: #eef1f4;
		color: #7d8792;
		font-weight: 600;
	}

	.campus-avatar__image {
		position: absolute;
		top: 0;
		right: 0;
		bottom: 0;
		left: 0;
		display: block;
		width: 100%;
		height: 100%;
	}

	.campus-avatar__fallback {
		font-size: 32upx;
		line-height: 1;
	}

	.campus-avatar__fallback[class*="cuIcon-"] {
		font-size: 34upx;
		font-weight: 400;
	}
</style>
