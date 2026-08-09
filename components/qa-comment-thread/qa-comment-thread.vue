<template>
	<view class="qa-thread" :class="{'is-night': night}">
		<view class="qa-thread-item" v-for="item in items" :key="item.id">
			<view class="qa-thread-node">
				<campus-avatar class="qa-thread-avatar round" :src="item.userJson && item.userJson.avatar" :name="item.userJson && item.userJson.name" @tap="$emit('user', item.userJson)"></campus-avatar>
				<view class="qa-thread-body">
					<view class="qa-thread-author">
						<text>{{item.userJson && item.userJson.name}}</text>
						<text v-if="item.userJson && item.userJson.campus" class="qa-thread-campus">{{item.userJson.campus}}</text>
					</view>
					<view class="qa-thread-text" user-select><text v-if="item.replyToUser && item.replyToUser.name" class="qa-thread-mention">回复 @{{item.replyToUser.name}} </text><text>{{item.text}}</text></view>
					<view class="qa-thread-actions">
						<text>{{displayTime(item.created)}}</text>
						<text class="qa-thread-action" @tap="$emit('reply', item)">回复</text>
						<text v-if="canDelete(item)" class="qa-thread-delete" @tap="$emit('delete', item)">删除</text>
					</view>
					<qa-comment-thread v-if="item.children && item.children.length" :items="item.children" :night="night" :current-uid="currentUid" :group="group" @reply="$emit('reply', $event)" @delete="$emit('delete', $event)" @user="$emit('user', $event)"></qa-comment-thread>
				</view>
			</view>
		</view>
	</view>
</template>

<script>
	export default {
		name: 'qaCommentThread',
		props: {
			items: { type: Array, default: () => [] },
			night: { type: Boolean, default: false },
			currentUid: { type: [Number, String], default: 0 },
			group: { type: String, default: '' }
		},
		methods: {
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
	.qa-thread.is-night::before { background: #3a4542; }
	.qa-thread.is-night .qa-thread-item::before { border-color: #3a4542; }
	.qa-thread.is-night .qa-thread-author { color: #d7e0dd; }
	.qa-thread.is-night .qa-thread-text { color: #ebf0ee; }
	.qa-thread.is-night .qa-thread-actions,
	.qa-thread.is-night .qa-thread-campus { color: #8f9d98; }
	.qa-thread.is-night .qa-thread-action { color: #b0bcb8; }
	.qa-thread.is-night .qa-thread-mention { color: #8dd0c2; }
</style>
