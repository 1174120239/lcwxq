<template>
	<view class="qa-thread" :class="{'is-night': night, 'is-flat': flatMode}">
		<view class="qa-thread-item" v-for="item in items" :key="item.id" :id="'qa-comment-' + item.id" :class="{'is-highlighted': isHighlighted(item)}">
			<view class="qa-thread-node">
				<campus-avatar class="qa-thread-avatar round" :src="item.userJson && item.userJson.avatar" :name="item.userJson && item.userJson.name" @tap.stop="$emit('user', item.userJson)"></campus-avatar>
				<view class="qa-thread-body">
					<view class="qa-thread-author" @tap.stop="$emit('user', item.userJson)">
						<text>{{item.userJson && item.userJson.name}}</text>
						<text v-if="item.userJson && item.userJson.campus" class="qa-thread-campus">{{item.userJson.campus}}</text>
					</view>
					<view class="qa-thread-text" user-select @longpress.stop="copyComment(item.text)"><text v-if="item.replyToUser && item.replyToUser.name" class="qa-thread-mention">回复 @{{item.replyToUser.name}} </text><text>{{item.text}}</text></view>
					<view class="qa-thread-actions">
						<text>{{displayTime(item.created)}}</text>
						<text class="qa-thread-action" @tap="$emit('reply', item)">回复</text>
						<text v-if="canDelete(item)" class="qa-thread-delete" @tap="$emit('delete', item)">删除</text>
					</view>
					<qa-comment-thread v-if="!flatMode && depth < maxDepth && item.children && item.children.length" :items="item.children" :depth="depth + 1" :max-depth="maxDepth" :night="night" :current-uid="currentUid" :group="group" :highlight-id="highlightId" @reply="$emit('reply', $event)" @delete="$emit('delete', $event)" @user="$emit('user', $event)"></qa-comment-thread>
					<view v-if="!flatMode && depth >= maxDepth && flatItems(item).length" class="qa-thread-tail">
						<view class="qa-thread-tail-title">后续回复</view>
						<qa-comment-thread :items="flatItems(item)" :depth="maxDepth" :max-depth="maxDepth" :flat-mode="true" :night="night" :current-uid="currentUid" :group="group" :highlight-id="highlightId" @reply="$emit('reply', $event)" @delete="$emit('delete', $event)" @user="$emit('user', $event)"></qa-comment-thread>
					</view>
				</view>
			</view>
		</view>
	</view>
</template>

<script>
	import { copyText } from '@/utils/clipboard.js'

	export default {
		name: 'qaCommentThread',
		props: {
			items: { type: Array, default: () => [] },
			depth: { type: Number, default: 1 },
			maxDepth: { type: Number, default: 3 },
			flatMode: { type: Boolean, default: false },
			highlightId: { type: [Number, String], default: 0 },
			night: { type: Boolean, default: false },
			currentUid: { type: [Number, String], default: 0 },
			group: { type: String, default: '' }
		},
		methods: {
			isHighlighted(item) {
				return item && this.highlightId && String(item.id) === String(this.highlightId);
			},
			flatItems(item) {
				var result = [];
				var visit = function(children) {
					(children || []).forEach(function(child) {
						result.push(child);
						visit(child.children);
					});
				};
				visit(item && item.children);
				return result.sort(function(a, b) {
					var createdDiff = Number(a.created || 0) - Number(b.created || 0);
					return createdDiff || Number(a.id || 0) - Number(b.id || 0);
				});
			},
			copyComment(text) {
				copyText(text, '评论已复制');
			},
			canDelete(item) {
				return (item && item.uid == this.currentUid) || this.group === 'administrator' || this.group === 'editor';
			},
			displayTime(timestamp) {
				var value = Number(timestamp || 0) * 1000;
				if (!value) return '';
				var diff = Math.max(0, Date.now() - value);
				if (diff < 60000) return '刚刚';
				if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前';
				if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前';
				var date = new Date(value);
				return (date.getMonth() + 1) + '-' + date.getDate();
			}
		}
	}
</script>

<style scoped>
	.qa-thread {
		position: relative;
		margin-top: 16rpx;
		padding-left: 28rpx;
	}

	.qa-thread::before {
		position: absolute;
		top: 0;
		bottom: 26rpx;
		left: 9rpx;
		width: 2rpx;
		background: #d9e0dd;
		content: '';
	}

	.qa-thread-item {
		position: relative;
		padding: 4rpx 0 20rpx;
	}

	.qa-thread-item.is-highlighted {
		padding-left: 12rpx;
		margin-left: -12rpx;
		border-radius: 12rpx;
		background: rgba(73, 183, 164, .14);
		box-shadow: 0 0 0 2rpx rgba(73, 183, 164, .24);
	}

	.qa-thread-item::before {
		position: absolute;
		top: 12rpx;
		left: -19rpx;
		width: 19rpx;
		height: 18rpx;
		border-bottom: 2rpx solid #d9e0dd;
		border-left: 2rpx solid #d9e0dd;
		border-bottom-left-radius: 12rpx;
		content: '';
	}

	.qa-thread-node {
		display: flex;
		align-items: flex-start;
	}

	.qa-thread-avatar {
		flex: 0 0 52rpx;
		width: 52rpx;
		height: 52rpx;
		margin-right: 14rpx;
	}

	.qa-thread-body {
		min-width: 0;
		flex: 1;
	}

	.qa-thread-author {
		display: flex;
		align-items: center;
		gap: 10rpx;
		color: #42514d;
		font-size: 25rpx;
		font-weight: 600;
	}

	.qa-thread-campus {
		color: #8b9692;
		font-size: 20rpx;
		font-weight: 400;
	}

	.qa-thread-text {
		display: block;
		margin-top: 5rpx;
		color: #273632;
		font-size: 27rpx;
		line-height: 1.62;
		white-space: pre-wrap;
		word-break: break-word;
	}

	.qa-thread-actions {
		display: flex;
		gap: 24rpx;
		margin-top: 8rpx;
		color: #8b9692;
		font-size: 22rpx;
	}

	.qa-thread-action { color: #61716c; }
	.qa-thread-mention { color: #168c80; }
	.qa-thread-delete { color: #b26b6b; }
	.qa-thread-tail { margin-top: 4rpx; }
	.qa-thread-tail-title { margin: 4rpx 0 4rpx; color: #71807a; font-size: 22rpx; }
	.qa-thread.is-flat { margin-top: 0; padding-left: 0; }
	.qa-thread.is-flat::before,
	.qa-thread.is-flat .qa-thread-item::before { display: none; }
	.qa-thread.is-flat .qa-thread-item { padding: 8rpx 0 14rpx; }
	.qa-thread.is-night::before { background: #3a4542; }
	.qa-thread.is-night .qa-thread-item::before { border-color: #3a4542; }
	.qa-thread.is-night .qa-thread-author { color: #d7e0dd; }
	.qa-thread.is-night .qa-thread-text { color: #ebf0ee; }
	.qa-thread.is-night .qa-thread-actions,
	.qa-thread.is-night .qa-thread-campus { color: #8f9d98; }
	.qa-thread.is-night .qa-thread-action { color: #b0bcb8; }
	.qa-thread.is-night .qa-thread-mention { color: #8dd0c2; }
	.qa-thread.is-night .qa-thread-item.is-highlighted { background: rgba(111, 210, 192, .16); box-shadow: 0 0 0 2rpx rgba(111, 210, 192, .26); }
	.qa-thread.is-night .qa-thread-tail-title { color: #9aa9a3; }
</style>
