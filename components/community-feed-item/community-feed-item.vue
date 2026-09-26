<template>
	<view class="community-feed-wrapper">
	<space-item v-if="item.feedType === 'space' && supportsSpaceItem" :space-list="[item]" :night="night" @before-navigate="$emit('before-navigate', $event)"></space-item>
	<view v-else class="community-feed-item" :class="['feed-' + item.feedType, { 'feed-night': night }]" @tap="$emit('open', item)">
		<view class="feed-head">
			<view class="feed-type"><text :class="typeIcon"></text><text>{{typeLabel}}</text></view>
			<text class="feed-time">{{displayTime(item.lastActivity || item.modified || item.created)}}</text>
		</view>
		<view class="feed-main">
			<view class="feed-copy">
				<text v-if="displayTitle" class="feed-title">{{displayTitle}}</text>
				<text v-if="summary" class="feed-summary">{{summary}}</text>
			</view>
			<image v-if="previewImage" class="feed-image" :src="previewImage" mode="aspectFill"></image>
		</view>
		<view v-if="item.feedType === 'question' && item.latestAnswer && item.latestAnswer.text" class="feed-answer">
			<view class="feed-answer-label"><text class="cuIcon-comment"></text><text>最新回答</text></view>
			<text class="feed-answer-text">{{item.latestAnswer.text}}</text>
		</view>
		<view v-if="item.feedType === 'task'" class="feed-task-meta">
			<text class="feed-task-kind">{{Number(item.kind) === 1 ? '寻求帮助' : '提供帮助'}}</text>
			<text class="feed-task-state">进行中</text>
		</view>
		<view class="feed-foot">
			<view class="feed-author" v-if="item.userJson">
				<image v-if="item.userJson.avatar" :src="item.userJson.avatar" mode="aspectFill"></image>
				<view v-else class="feed-avatar-fallback"><text class="cuIcon-people"></text></view>
				<text>{{item.userJson.name || '社区用户'}}</text>
			</view>
			<view class="feed-stat" v-if="item.feedType === 'question'"><text class="cuIcon-question"></text><text>{{item.answerCount || 0}} 个回答</text></view>
			<view class="feed-stat" v-else-if="item.feedType === 'space'"><text class="cuIcon-appreciate"></text><text>{{item.likes || 0}}</text></view>
			<view class="feed-open"><text>{{item.feedType === 'task' ? '查看任务' : item.feedType === 'question' ? '查看问题' : '查看动态'}}</text><text class="cuIcon-right"></text></view>
		</view>
	</view>
	</view>
</template>
<script>
import SpaceItem from '@/pages/components/spaceItem.vue'

export default {
	name: 'CommunityFeedItem',
	components: {
		SpaceItem
	},
	props: {
		item: { type: Object, default: () => ({}) },
		night: { type: Boolean, default: false }
	},
	computed: {
		supportsSpaceItem() {
			const type = Number(this.item.type != null ? this.item.type : this.item.spaceType)
			return type === 0 || type === 4
		},
		typeLabel() {
			return this.item.feedType === 'question' ? '问题' : this.item.feedType === 'task' ? '校园互助' : '动态'
		},
		typeIcon() {
			return this.item.feedType === 'question' ? 'cuIcon-question' : this.item.feedType === 'task' ? 'cuIcon-friendadd' : this.item.feedType === 'space' ? 'cuIcon-community' : 'cuIcon-edit'
		},
		displayTitle() {
			const title = String(this.item.title || '').trim()
			return title || (this.item.feedType === 'task' ? '校园互助信息' : '')
		},
		summary() {
			return this.item.feedType === 'question' || this.item.feedType === 'task'
				? (this.item.description || '')
				: (this.item.text || '')
		},
		previewImage() {
			if (this.item.feedType === 'question') return this.item.coverUrl || ''
			if (this.item.feedType === 'task') return this.item.imageUrl || ''
			if (this.item.feedType === 'space') return String(this.item.pic || '').split('||')[0]
			return ''
		}
	},
	methods: {
		displayTime(timestamp) {
			const value = Number(timestamp || 0) * 1000
			if (!value) return ''
			const diff = Math.max(0, Date.now() - value)
			if (diff < 60000) return '刚刚'
			if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前'
			if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前'
			const date = new Date(value)
			return (date.getMonth() + 1) + '-' + date.getDate()
		}
	}
}
</script>
<style scoped>
.community-feed-item { margin: 0 0 16rpx; padding: 26rpx 26rpx 22rpx; border: 1rpx solid #e2e9e6; border-radius: 16rpx; background: #fff; box-shadow: 0 5rpx 16rpx rgba(40, 63, 56, .045); }
.feed-head, .feed-foot, .feed-type, .feed-author, .feed-stat, .feed-open, .feed-task-meta { display: flex; align-items: center; }
.feed-head { justify-content: space-between; gap: 16rpx; }
.feed-type { gap: 9rpx; color: #237c74; font-size: 23rpx; font-weight: 700; }
.feed-time { flex: 0 0 auto; color: #8b9995; font-size: 22rpx; }
.feed-main { display: flex; align-items: flex-start; gap: 18rpx; margin-top: 12rpx; }
.feed-copy { flex: 1; min-width: 0; }
.feed-title { display: block; color: #263832; font-size: 32rpx; font-weight: 700; line-height: 1.42; word-break: break-word; }
.feed-summary { display: -webkit-box; overflow: hidden; margin-top: 7rpx; color: #60716b; font-size: 25rpx; line-height: 1.58; word-break: break-word; -webkit-box-orient: vertical; -webkit-line-clamp: 3; }
.feed-image { flex: 0 0 150rpx; width: 150rpx; height: 116rpx; border-radius: 10rpx; background: #eef2f0; }
.feed-answer { margin-top: 16rpx; padding: 14rpx 16rpx; border-left: 4rpx solid #6ca58e; border-radius: 8rpx; background: #f2f7f4; }
.feed-answer-label { display: flex; align-items: center; gap: 8rpx; color: #43816a; font-size: 22rpx; font-weight: 700; }
.feed-answer-text { display: -webkit-box; overflow: hidden; margin-top: 7rpx; color: #52645d; font-size: 24rpx; line-height: 1.55; word-break: break-word; -webkit-box-orient: vertical; -webkit-line-clamp: 3; }
.feed-task-meta { gap: 12rpx; margin-top: 14rpx; font-size: 22rpx; }
.feed-task-kind { color: #277a59; font-weight: 600; }
.feed-task-state { color: #7b8984; }
.feed-foot { gap: 14rpx; min-height: 46rpx; margin-top: 18rpx; padding-top: 14rpx; border-top: 1rpx solid #edf1ef; color: #7d8d88; font-size: 22rpx; }
.feed-author { flex: 1; min-width: 0; gap: 9rpx; overflow: hidden; }
.feed-author image, .feed-avatar-fallback { flex: 0 0 40rpx; width: 40rpx; height: 40rpx; border-radius: 50%; background: #e8efec; }
.feed-avatar-fallback { display: flex; align-items: center; justify-content: center; color: #778c83; font-size: 22rpx; }
.feed-author text { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.feed-stat, .feed-open { flex: 0 0 auto; gap: 6rpx; white-space: nowrap; }
.feed-open { color: #237c74; font-weight: 700; }
.feed-question .feed-type { color: #367fa5; }
.feed-task .feed-type { color: #287f5d; }
.feed-space .feed-type { color: #a3653f; }
.feed-night { border-color: #303b38; background: #1d2523; box-shadow: 0 5rpx 16rpx rgba(0, 0, 0, .14); }
.feed-night .feed-title { color: #edf3f0; }
.feed-night .feed-summary, .feed-night .feed-time, .feed-night .feed-foot, .feed-night .feed-task-state { color: #9eaca7; }
.feed-night .feed-answer { border-left-color: #5d9f87; background: #26352f; }
.feed-night .feed-answer-text { color: #b8c8c1; }
.feed-night .feed-foot { border-top-color: #303b38; }
.feed-night .feed-avatar-fallback, .feed-night .feed-image { background: #303b38; color: #a9bbb3; }
@media (max-width: 360px) {
	.community-feed-item { padding-right: 20rpx; padding-left: 20rpx; }
	.feed-main { gap: 12rpx; }
	.feed-image { flex-basis: 126rpx; width: 126rpx; height: 104rpx; }
	.feed-title { font-size: 30rpx; }
}
</style>
