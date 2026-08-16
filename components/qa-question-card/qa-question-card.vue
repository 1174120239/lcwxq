<template>
	<view class="qa-card" :class="[{'qa-card-night': night}, variant === 'feed' ? 'qa-card-feed' : '']" @tap="$emit('open', question)">
		<view class="qa-card-main">
			<view class="qa-card-meta">
				<text class="qa-card-kind">问答</text>
				<text v-if="question.topic" class="qa-card-topic">{{question.topic}}</text>
			</view>
			<view class="qa-card-title">{{question.title}}</view>
			<view v-if="question.description" class="qa-card-description">{{question.description}}</view>
			<view class="qa-card-foot">
				<view class="qa-card-answer" :class="{'is-empty': answerCount===0}"><text class="cuIcon-comment"></text><text>{{answerCountText}}</text></view>
				<text class="qa-card-time">{{displayTime(question.modified || question.created)}}</text>
				<view class="qa-card-open"><text>{{answerCount > 0 ? '查看讨论' : '去回答'}}</text><text class="cuIcon-right"></text></view>
			</view>
		</view>
		<image v-if="question.coverUrl" class="qa-card-cover" :src="question.coverUrl" mode="aspectFill"></image>
	</view>
</template>

<script>
	export default {
		name: 'qaQuestionCard',
		props: {
			question: {
				type: Object,
				default: () => ({})
			},
			night: {
				type: Boolean,
				default: false
			},
			variant: {
				type: String,
				default: 'compact'
			}
		},
		computed: {
			answerCount() {
				return Math.max(0, Number(this.question && this.question.answerCount) || 0)
			},
			answerCountText() {
				return this.answerCount > 0 ? this.answerCount + ' 个回答' : '等待回答'
			}
		},
		methods: {
			displayTime(timestamp) {
				var value = Number(timestamp || 0) * 1000;
				if (!value) return '';
				var diff = Math.max(0, Date.now() - value);
				if (diff < 60 * 1000) return '刚刚';
				if (diff < 60 * 60 * 1000) return Math.floor(diff / 60000) + '分钟前';
				if (diff < 24 * 60 * 60 * 1000) return Math.floor(diff / 3600000) + '小时前';
				var date = new Date(value);
				return (date.getMonth() + 1) + '-' + date.getDate();
			}
		}
	}
</script>

<style scoped>
	.qa-card {
		display: flex;
		align-items: center;
		min-height: 176rpx;
		padding: 26rpx 28rpx;
		border-bottom: 1rpx solid #edf0ef;
		background: #ffffff;
		transition: background-color .18s ease, transform .18s ease;
	}

	.qa-card-feed {
		align-items: flex-start;
		min-height: 0;
		margin-bottom: 14rpx;
		padding: 28rpx 28rpx 24rpx;
		border: 1rpx solid #e2e9e6;
		border-radius: 16rpx;
		box-shadow: 0 8rpx 24rpx rgba(38, 65, 61, .055);
	}

	.qa-card:active {
		background: #f7faf9;
		transform: scale(.995);
	}

	.qa-card-main {
		min-width: 0;
		flex: 1;
	}

	.qa-card-meta {
		display: flex;
		align-items: center;
		flex-wrap: wrap;
		gap: 10rpx;
		color: #71807c;
		font-size: 22rpx;
	}

	.qa-card-kind {
		color: #168c80;
		font-weight: 700;
	}

	.qa-card-topic {
		padding: 2rpx 10rpx;
		border-radius: 8rpx;
		background: #eef5f3;
		color: #60726d;
		white-space: normal;
		word-break: break-word;
	}

	.qa-card-title {
		display: block;
		overflow: visible;
		margin-top: 10rpx;
		color: #182522;
		font-size: 31rpx;
		font-weight: 600;
		line-height: 1.45;
		white-space: pre-wrap;
		word-break: break-word;
	}

	.qa-card-feed .qa-card-title {
		margin-top: 14rpx;
		font-size: 34rpx;
		font-weight: 700;
		line-height: 1.42;
	}

	.qa-card-description {
		display: -webkit-box;
		overflow: hidden;
		margin-top: 8rpx;
		color: #63706c;
		font-size: 25rpx;
		line-height: 1.5;
		white-space: normal;
		word-break: break-word;
		-webkit-box-orient: vertical;
		-webkit-line-clamp: 2;
	}

	.qa-card-foot {
		display: flex;
		align-items: center;
		gap: 22rpx;
		margin-top: 12rpx;
		color: #89938f;
		font-size: 22rpx;
	}

	.qa-card-feed .qa-card-foot {
		margin-top: 20rpx;
		padding-top: 16rpx;
		border-top: 1rpx solid #edf1ef;
	}

	.qa-card-answer,
	.qa-card-open {
		display: inline-flex;
		align-items: center;
		gap: 7rpx;
	}

	.qa-card-answer {
		font-weight: 600;
		color: #237c74;
	}

	.qa-card-answer.is-empty {
		color: #b17b32;
	}

	.qa-card-time {
		white-space: nowrap;
	}

	.qa-card-open {
		margin-left: auto;
		color: #5e706b;
		white-space: nowrap;
	}

	.qa-card-cover {
		flex: 0 0 152rpx;
		width: 152rpx;
		height: 112rpx;
		margin-left: 24rpx;
		border-radius: 12rpx;
		background: #eef2f1;
	}

	.qa-card-night {
		border-bottom-color: #303b38;
		background: #1d2523;
	}

	.qa-card-night:active {
		background: #242e2b;
	}

	.qa-card-night .qa-card-title {
		color: #edf3f0;
	}

	.qa-card-night .qa-card-description {
		color: #aebbb6;
	}

	.qa-card-night .qa-card-meta,
	.qa-card-night .qa-card-foot {
		color: #899993;
	}

	.qa-card-night .qa-card-kind {
		color: #82cbbb;
	}

	.qa-card-night .qa-card-topic {
		background: #293532;
		color: #9baca6;
	}

	.qa-card-night .qa-card-answer {
		color: #82cbbb;
	}

	.qa-card-night .qa-card-answer.is-empty {
		color: #d0a265;
	}

	.qa-card-night .qa-card-open {
		color: #aab8b3;
	}

	.qa-card-night.qa-card-feed {
		border-color: #303b38;
		box-shadow: 0 8rpx 22rpx rgba(0, 0, 0, .14);
	}

	.qa-card-night.qa-card-feed .qa-card-foot {
		border-top-color: #303b38;
	}
</style>
