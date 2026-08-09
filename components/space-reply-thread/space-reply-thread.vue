<template>
	<view class="reply-thread" :class="{'is-night': night}">
		<view class="reply-thread-item" v-for="item in items" :key="item.id">
			<view class="reply-thread-node">
				<campus-avatar class="reply-thread-avatar round" :src="item.userJson.avatar" :name="item.userJson.name" @tap="$emit('user', item.userJson)"></campus-avatar>
				<view class="reply-thread-body">
					<view class="reply-thread-author">{{item.userJson.name}}</view>
					<view class="reply-thread-content">
						<text class="reply-thread-mention" v-if="item.parentJson && item.parentJson.username">回复 @{{item.parentJson.username}} </text>
						<rich-text :nodes="item.renderedText || item.text"></rich-text>
					</view>
					<view class="reply-thread-actions">
						<text>{{item.displayTime}}</text>
						<text class="reply-thread-action" @tap="$emit('reply', item)">回复</text>
						<text class="reply-thread-action" :class="{'is-liked': item.isLikes==1}" @tap="$emit('like', item)">
							<text class="cuIcon-appreciate"></text>{{item.likes > 0 ? item.likes : ''}}
						</text>
						<text class="reply-thread-delete" v-if="canDelete(item)" @tap="$emit('delete', item)">删除</text>
					</view>

					<view class="reply-thread-toggle" v-if="item.reply>0 && !item._expanded" @tap="$emit('toggle', item)">
						<text v-if="item._loading">正在加载…</text>
						<block v-else><text>显示{{item.reply}}条回复</text><text class="cuIcon-unfold"></text></block>
					</view>
					<view v-if="item._expanded" class="reply-thread-children">
						<space-reply-thread
							:items="item._children"
							:night="night"
							:current-uid="currentUid"
							:group="group"
							@reply="$emit('reply', $event)"
							@like="$emit('like', $event)"
							@delete="$emit('delete', $event)"
							@toggle="$emit('toggle', $event)"
							@more="$emit('more', $event)"
							@user="$emit('user', $event)"
						></space-reply-thread>
						<view class="reply-thread-toggle" v-if="item._childMore" @tap="$emit('more', item)">
							<text v-if="item._loading">正在加载…</text>
							<block v-else><text>显示更多回复</text><text class="cuIcon-unfold"></text></block>
						</view>
						<view class="reply-thread-toggle is-collapse" @tap="$emit('toggle', item)">
							<text>收起回复</text><text class="cuIcon-fold"></text>
						</view>
					</view>
				</view>
			</view>
		</view>
	</view>
</template>

<script>
	export default {
		name: 'spaceReplyThread',
		props: {
			items: {
				type: Array,
				default: () => []
			},
			night: {
				type: Boolean,
				default: false
			},
			currentUid: {
				type: [Number, String],
				default: 0
			},
			group: {
				type: String,
				default: ''
			}
		},
		methods: {
			canDelete(item) {
				var authorUid = item && item.userJson ? item.userJson.uid : 0;
				return (authorUid != 0 && authorUid == this.currentUid) || this.group === 'administrator';
			}
		}
	}
</script>

<style scoped>
	.reply-thread {
		position: relative;
		margin: 14rpx 0 0 6rpx;
		padding-left: 34rpx;
	}

	.reply-thread::before {
		position: absolute;
		top: 0;
		bottom: 28rpx;
		left: 12rpx;
		width: 2rpx;
		background: #d8e0de;
		content: '';
	}

	.reply-thread-item {
		position: relative;
		padding: 8rpx 0 20rpx;
	}

	.reply-thread-item::before {
		position: absolute;
		top: 14rpx;
		left: -22rpx;
		width: 22rpx;
		height: 21rpx;
		border-bottom: 2rpx solid #d8e0de;
		border-left: 2rpx solid #d8e0de;
		border-bottom-left-radius: 14rpx;
		content: '';
	}

	.reply-thread-node {
		display: flex;
		align-items: flex-start;
	}

	.reply-thread-avatar {
		flex: 0 0 54rpx;
		width: 54rpx;
		height: 54rpx;
		margin-right: 16rpx;
	}

	.reply-thread-body {
		min-width: 0;
		flex: 1;
	}

	.reply-thread-author {
		color: #40504d;
		font-size: 25rpx;
		font-weight: 600;
		line-height: 1.4;
	}

	.reply-thread-content {
		margin-top: 5rpx;
		color: #263a37;
		font-size: 28rpx;
		line-height: 1.6;
		word-break: break-all;
	}

	.reply-thread-content rich-text {
		display: inline;
	}

	.reply-thread-mention {
		color: #168c80;
	}

	.reply-thread-actions {
		display: flex;
		align-items: center;
		gap: 24rpx;
		margin-top: 10rpx;
		color: #899691;
		font-size: 23rpx;
	}

	.reply-thread-action {
		color: #65746f;
	}

	.reply-thread-action .cuIcon-appreciate {
		margin-right: 6rpx;
	}

	.reply-thread-action.is-liked {
		color: #168c80;
	}

	.reply-thread-delete {
		color: #b26b6b;
	}

	.reply-thread-toggle {
		display: inline-flex;
		align-items: center;
		gap: 8rpx;
		min-height: 54rpx;
		margin-top: 8rpx;
		color: #526762;
		font-size: 25rpx;
		font-weight: 600;
	}

	.reply-thread-toggle.is-collapse {
		color: #7c8b86;
	}

	.reply-thread-children {
		margin-top: 2rpx;
	}

	.reply-thread.is-night::before {
		background: #3b4745;
	}

	.reply-thread.is-night .reply-thread-item::before {
		border-color: #3b4745;
	}

	.reply-thread.is-night .reply-thread-author {
		color: #d8e2de;
	}

	.reply-thread.is-night .reply-thread-content {
		color: #edf3f0;
	}

	.reply-thread.is-night .reply-thread-actions {
		color: #91a09b;
	}

	.reply-thread.is-night .reply-thread-action,
	.reply-thread.is-night .reply-thread-toggle {
		color: #b2c0bb;
	}

	.reply-thread.is-night .reply-thread-mention,
	.reply-thread.is-night .reply-thread-action.is-liked {
		color: #8dd2c4;
	}
</style>
