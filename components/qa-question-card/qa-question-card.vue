<template>
	<view class="qa-card" :class="{'qa-card-night': night}" @tap="$emit('open', question)">
		<view class="qa-card-main">
			<view class="qa-card-meta">
				<text class="qa-card-kind">问答</text>
				<text v-if="question.topic" class="qa-card-topic">{{question.topic}}</text>
			</view>
			<view class="qa-card-title">{{question.title}}</view>
			<view v-if="question.description" class="qa-card-description">{{question.description}}</view>
			<view class="qa-card-foot">
				<text>{{question.answerCount || 0}} 个回答</text>
				<text>{{displayTime(question.modified || question.created)}}</text>
			</view>
		</view>
		<image v-if="question.coverUrl" class="qa-card-cover" :src="question.coverUrl" mode="aspectFill"></image>
		<text v-else class="cuIcon-right qa-card-arrow"></text>
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
		gap: 14rpx;
		color: #71807c;
		font-size: 22rpx;
	}

	.qa-card-kind {
		color: #168c80;
		font-weight: 600;
	}

	.qa-card-topic {
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.qa-card-title {
		display: -webkit-box;
		overflow: hidden;
		margin-top: 10rpx;
		color: #182522;
		font-size: 31rpx;
		font-weight: 600;
		line-height: 1.45;
		-webkit-box-orient: vertical;
		-webkit-line-clamp: 2;
	}

	.qa-card-description {
		display: -webkit-box;
		overflow: hidden;
		margin-top: 8rpx;
		color: #63706c;
		font-size: 25rpx;
		line-height: 1.5;
		-webkit-box-orient: vertical;
		-webkit-line-clamp: 2;
	}

	.qa-card-foot {
		display: flex;
		gap: 22rpx;
		margin-top: 12rpx;
		color: #89938f;
		font-size: 22rpx;
	}

	.qa-card-cover {
		flex: 0 0 152rpx;
		width: 152rpx;
		height: 112rpx;
		margin-left: 24rpx;
		border-radius: 12rpx;
		background: #eef2f1;
	}

	.qa-card-arrow {
		margin-left: 18rpx;
		color: #a8b1ae;
		font-size: 28rpx;
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
	.qa-card-night .qa-card-foot,
	.qa-card-night .qa-card-arrow {
		color: #899993;
	}

	.qa-card-night .qa-card-kind {
		color: #82cbbb;
	}
</style>
