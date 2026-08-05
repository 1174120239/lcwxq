<template>
	<view class="reply-history-item" :class="{'is-clickable': item.originalState === 'visible'}" @tap="openOriginal">
		<view class="reply-history-head">
			<text>评论于 {{formatDate(item.created)}}</text>
			<text class="cuIcon-right" v-if="item.originalState === 'visible'"></text>
		</view>
		<text class="reply-history-text">{{item.text}}</text>
		<view class="reply-history-original" v-if="item.originalState === 'visible' && item.original">
			<text class="reply-history-author">@{{originalAuthor}}</text>
			<text class="reply-history-summary">{{item.original.text || '图片动态'}}</text>
		</view>
		<view class="reply-history-state" v-else-if="item.originalState === 'forbidden'">原动态不可见</view>
		<view class="reply-history-state" v-else>原动态已删除</view>
	</view>
</template>

<script>
	export default {
		props: { item: { type: Object, required: true } },
		computed: {
			originalAuthor() {
				return this.item.original && this.item.original.userJson
					? this.item.original.userJson.name : '用户'
			}
		},
		methods: {
			openOriginal() {
				if (this.item.originalState !== 'visible' || !this.item.original) return
				uni.navigateTo({ url: '/pages/space/info?id=' + encodeURIComponent(String(this.item.original.id)) })
			},
			formatDate(timestamp) {
				const date = new Date(Number(timestamp || 0) * 1000)
				const pad = value => String(value).padStart(2, '0')
				return date.getFullYear() + '-' + pad(date.getMonth() + 1) + '-' + pad(date.getDate())
					+ ' ' + pad(date.getHours()) + ':' + pad(date.getMinutes())
			}
		}
	}
</script>

<style scoped>
	.reply-history-item { margin: 16rpx 20rpx; padding: 24rpx; background: #fff; border: 1px solid #e5eae8; border-radius: 8rpx; }
	.reply-history-item.is-clickable:active { background: #f4f7f6; }
	.reply-history-head { display: flex; justify-content: space-between; color: #7b8682; font-size: 23rpx; }
	.reply-history-text { display: block; margin-top: 14rpx; color: #24332f; line-height: 1.65; white-space: pre-wrap; word-break: break-word; }
	.reply-history-original { margin-top: 18rpx; padding: 18rpx; background: #f1f4f3; border-left: 5rpx solid #168c80; }
	.reply-history-author { display: block; color: #168c80; font-size: 24rpx; }
	.reply-history-summary { display: block; margin-top: 6rpx; color: #596662; line-height: 1.55; white-space: pre-wrap; }
	.reply-history-state { margin-top: 18rpx; padding: 20rpx; background: #f1f3f2; color: #8a9491; text-align: center; }
	.campus-night .reply-history-item, .night .reply-history-item { background: #1f2526; border-color: #333b3c; }
	.campus-night .reply-history-text, .night .reply-history-text { color: #e1e6e4; }
	.campus-night .reply-history-original, .campus-night .reply-history-state, .night .reply-history-original, .night .reply-history-state { background: #293031; }
	.campus-night .reply-history-summary, .night .reply-history-summary { color: #b9c2bf; }
</style>
