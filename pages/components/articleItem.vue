<template>

	<view class="article-item-shell" :class="{'is-entering': animationIndex >= 0 && animationIndex < 3, 'is-home-feed': homeFeed}" :style="entryStyle">
		<!--帖子推流广告区域-->
		<view class="cu-card article no-card" :class="isTop?'topContents':''" v-if="item.isAds" @tap="goAds(item)">
			<view class="cu-item shadow">
				<view class="title">
					<view class="text-cut">{{item.name}}</view>

				</view>
				<view class="content article-content" style="position: relative;">
					<image :src="item.img" mode="aspectFill" lazy-load></image>
					<view class="desc">
						<view class="text-content">{{item.intro}}</view>
						<view class="ads-more" @tap="goAds(item)">了解更多<text class="cuIcon-right"></text></view>
					</view>
					<text class="ads-ico">广告</text>
				</view>
			</view>
		</view>
		<view class="cu-card article no-card" v-else @tap="toInfo(item)">

			<view v-if="isTop" class="cu-item" style="border-radius: 20upx;padding-bottom:0px">
				<view class="title">
					<view class="flex">
						<view class="margin-top-xs margin-right-xs" style="width: 43upx;height: 43upx;">
							<image src="../../static/page/br6.png" mode="widthFix"></image>
						</view>
						<text>{{formattedTitle}}</text>
					</view>
				</view>
			</view>

			<view v-if="isTop==false" class="cu-item">
				<block v-if="homeFeed">
					<view class="home-article-card">
						<view class="home-article-head">
							<view class="home-article-pill" v-if="item.category.length>0">{{item.category[0].name}}</view>
							<view class="home-article-pill" v-else>校园热帖</view>
							<view class="home-article-clock">{{formatDate(item.created)}}</view>
						</view>
						<view class="home-article-main" :class="{'has-thumb': item.images.length>0}">
							<view class="home-article-copy">
								<text class="home-article-title">{{formattedTitle}}</text>
								<text class="home-article-desc" v-if="item.text.length > 0">{{infotext}}</text>
							</view>
							<view class="home-article-thumb" v-if="item.images.length>0">
								<image :src="isVipContent ? '../../static/page/vip_img.png' : item.images[0]" mode="aspectFill" lazy-load></image>
								<text v-if="item.images.length > 1" class="home-image-count">+{{item.images.length-1}}</text>
							</view>
						</view>
						<view class="home-article-footer">
							<view class="home-article-meta">
								<text class="cuIcon-people"></text>
								<text>{{item.authorInfo.name}}</text>
								<text class="home-meta-dot">·</text>
								<text class="cuIcon-hotfill"></text>
								<text>{{formatNumber(item.views)}} 热度</text>
							</view>
							<view class="home-read-link">阅读全文<text class="cuIcon-right"></text></view>
						</view>
					</view>
				</block>
				<block v-else>
					<view class="title">
						<view class="article-heading">
							<view class="content-author flex align-center">
								<image :src="item.authorInfo.avatar" mode="aspectFill" lazy-load></image>
								<text v-if="item.authorInfo.isvip>0"
									class="content-author-name text-shojo2">{{item.authorInfo.name}}</text>
								<text class="userlv article-vip-badge" v-if="item.authorInfo.isvip>0">VIP</text>
								<text v-else class="content-author-name tn-text-bold">{{item.authorInfo.name}}</text>
								<text class="article-more cuIcon-moreandroid"></text>
							</view>
							<view class="data-time" style="color: #888;font-size: 9px;font-weight: normal !important;">
								{{formatDate(item.created)}}</view>
							<view class="article-category" v-if="item.category.length>0">{{item.category[0].name}}</view>
							<text class="article-title">{{formattedTitle}}</text>
						</view>
					</view>

					<block v-if="item.images.length == 0&&isTop==false">
						<view class="content article-content">
							<view class="text-content" v-if="item.text.length > 0"> {{infotext}}</view>

						</view>
					</block>
					<block v-if="item.images.length > 0 && isTop==false">
						<view class="content article-content">
							<view class="text-content text-ellipsis" v-if="item.text.length > 0">
								{{infotext}}
							</view>

							<view class="grid flex-sub col-3 grid-square">
								<block v-if="item.images.length == 1">
									<view class="bg-img">
										<image :src="isVipContent ? '../../static/page/vip_img.png' : item.images[0]"
											mode="aspectFill" lazy-load></image>
									</view>
								</block>
								<block v-if="item.images.length == 2">
									<view class="bg-img">
										<image :src="isVipContent ? '../../static/page/vip_img.png' : item.images[0]"
											mode="aspectFill" lazy-load></image>
									</view>
									<view class="bg-img">
										<image :src="isVipContent ? '../../static/page/vip_img.png' : item.images[1]"
											mode="aspectFill" lazy-load></image>
									</view>
								</block>
								<block v-if="item.images.length >= 3">
									<view class="bg-img">
										<image :src="isVipContent ? '../../static/page/vip_img.png' : item.images[0]"
											mode="aspectFill" lazy-load></image>
									</view>
									<view class="bg-img">
										<image :src="isVipContent ? '../../static/page/vip_img.png' : item.images[1]"
											mode="aspectFill" lazy-load></image>
									</view>
									<view class="bg-img">
										<image :src="isVipContent ? '../../static/page/vip_img.png' : item.images[2]"
										mode="aspectFill" lazy-load></image>
										<text v-if="item.images.length > 3" class="extra-count"><view class="cuIcon-add center-add"> {{ item.images.length-3 }}</view></text>
									</view>
									
								</block>
							</view>
						</view>
					</block>

					<view v-if="isTop==false" class="article-content-btn article-list-btn flex justify-between	"
						style="margin-top:30upx;">
						<view class="tn-padding-xs text-shojo"
							style="border-radius: 40upx;color: #262626;font-weight: bold;background: #f1f1f1;">
							<text class="padding-sm radius" v-if="item.category.length>0">{{item.category[0].name}}</text>

						</view>
						<view class="article-stats">
							<view><text class="cuIcon-attention"></text><text>{{formatNumber(item.views)}}</text></view>
							<view><text class="cuIcon-appreciate"></text><text>{{item.likes}}</text></view>
							<view><text class="cuIcon-message"></text><text>{{item.commentsNum}}</text></view>
						</view>

					</view>
				</block>

			</view>
		</view>
		<view class="feed-card-spacer"></view>
	</view>

</template>

<script>
	// #ifdef APP-PLUS
	import owo from '../../static/app-plus/owo/OwO.js'
	// #endif
	// #ifdef H5
	import owo from '../../static/h5/owo/OwO.js'
	// #endif
	// #ifdef MP
	var owo = [];
	// #endif
	export default {
		props: {
			item: {
				type: Object,
				default: () => ({})
			},
			isTop: {
				type: Boolean,
				default: false
			},
			animationIndex: {
				type: Number,
				default: -1
			},
			homeFeed: {
				type: Boolean,
				default: false
			}
		},
		name: "articleItem",
		data() {
			return {
				needRefresh: false
			};
		},
		computed: {
			entryStyle() {
				if (this.animationIndex < 0 || this.animationIndex > 2) return null
				return {
					animationDelay: `${this.animationIndex * 55}ms`
				}
			},
			displayImages() {
				return this.item.images.slice(0, 3)
			},
			isVipContent() {
				return this.item.category?.[0]?.slug === 'vip'
			},
			formattedTitle() {
				return this.replaceSpecialChar(this.item.title)
			},
			infotext(){
				var text = this.item.text;
				const replacements = {
					'vip(.*?)/vip': '(该内容仅会员可见)',
					'audio(.*?)/audio': '(该帖子包含音乐)',
					'\\|\\|rn\\|\\|': '' // 去掉所有||rn||
				}

				Object.entries(replacements).forEach(([pattern, replacement]) => {
					text = text.replace(new RegExp(pattern, 'g'), replacement)
				})
				return text
			}
		},
		methods: {
			replaceAll(string, search, replace) {
				return string.split(search).join(replace);
			},

			replaceSpecialChar(text) {
				text = text.replace(/&quot;/g, '"');
				text = text.replace(/&amp;/g, '&');
				text = text.replace(/&lt;/g, '<');
				text = text.replace(/&gt;/g, '>');
				text = text.replace(/&nbsp;/g, ' ');
				return text;
			},
			formatDate(datetime) {
				const timeUnits = [{
						unit: 'year',
						divisor: 31536000000,
						suffix: '年前'
					},
					{
						unit: 'month',
						divisor: 2592000000,
						suffix: '个月前'
					},
					{
						unit: 'week',
						divisor: 604800000,
						suffix: '周前'
					},
					{
						unit: 'day',
						divisor: 86400000,
						suffix: '天前'
					},
					{
						unit: 'hour',
						divisor: 3600000,
						suffix: '小时前'
					},
					{
						unit: 'minute',
						divisor: 60000,
						suffix: '分钟前'
					},
					{
						unit: 'second',
						divisor: 1000,
						suffix: '秒前'
					}
				]

				const diff = new Date() - new Date(datetime * 1000)

				for (const {
						divisor,
						suffix
					}
					of timeUnits) {
					const value = Math.floor(diff / divisor)
					if (value >= 1) return `${value}${suffix}`
				}

				return '刚刚'
			},
			formatNumber(num) {
				const formats = [{
						threshold: 1e4,
						divisor: 1e4,
						suffix: 'w'
					},
					{
						threshold: 1e3,
						divisor: 1e3,
						suffix: 'k'
					}
				]

				for (const {
						threshold,
						divisor,
						suffix
					}
					of formats) {
					if (num >= threshold) {
						return (num / divisor).toFixed(1) + suffix
					}
				}

				return num
			},
			toInfo(data) {
				var that = this;
				if (data.type == 'post') {
					uni.navigateTo({
						url: '/pages/contents/info?cid=' + data.cid + "&title=" + data.title
					});
				} else {
					uni.navigateTo({
						url: '/pages/contents/videoInfo?cid=' + data.cid + "&title=" + data.title
					});
				}
			},
			toVideoInfo(data) {
				var that = this;

				uni.navigateTo({
					url: '/pages/contents/videoInfo?cid=' + data.cid + "&title=" + data.title
				});
			},
			goAds(data) {
				var that = this;
				var url = data.url;
				var type = data.urltype;
				// #ifdef APP-PLUS
				if (type == 1) {
					plus.runtime.openURL(url);
				}
				if (type == 0) {
					plus.runtime.openWeb(url);
				}
				// #endif
				// #ifdef H5
				window.open(url)
				// #endif
			},
		}
	}
</script>

<style scoped>
	.article-item-shell {
		padding: 0 16rpx;
	}

	.article-item-shell.is-entering {
		animation: feedCardIn 310ms cubic-bezier(0.22, 1, 0.36, 1) both;
	}

	.feed-card-spacer {
		height: 16rpx;
	}

	.cu-card.article.no-card {
		margin: 0;
	}

	.cu-card.article.no-card > .cu-item {
		border: 1rpx solid rgba(255, 255, 255, 0.82);
		border-radius: 28rpx;
		background: rgba(255, 255, 255, 0.78);
		box-shadow: 0 14rpx 38rpx rgba(31, 64, 61, 0.09);
		overflow: hidden;
		transition: transform 180ms ease, box-shadow 180ms ease;
	}

	.cu-card.article.no-card:active > .cu-item {
		transform: scale(0.986);
		box-shadow: 0 8rpx 24rpx rgba(31, 64, 61, 0.08);
	}

	.cu-card.article .cu-item > .title {
		padding: 28rpx 30rpx 12rpx;
	}

	.article-heading {
		min-width: 0;
	}

	.content-author {
		position: relative;
		display: flex;
		min-height: 72rpx;
		padding-right: 42rpx;
		min-width: 0;
	}

	.content-author image {
		width: 72rpx !important;
		height: 72rpx !important;
		margin-right: 16rpx;
		border: 2rpx solid rgba(255, 255, 255, 0.9);
		border-radius: 50%;
		box-shadow: 0 5rpx 16rpx rgba(34, 67, 65, 0.1);
		flex: 0 0 72rpx;
	}

	.content-author-name {
		max-width: min(46vw, 360rpx);
		min-width: 0;
		font-size: 29rpx;
		font-weight: 700;
		color: #2b3c40 !important;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.article-vip-badge {
		flex: 0 0 auto;
		margin-left: 10rpx;
		padding: 3rpx 10rpx !important;
		border-radius: 12rpx !important;
		background: linear-gradient(135deg, #f2ad5c, #e6216d 62%, #901ccb);
		color: #fff !important;
		font-size: 18rpx;
		line-height: 28rpx;
	}

	.article-more {
		position: absolute;
		right: 0;
		top: 16rpx;
		font-size: 34rpx;
		color: #89969a;
	}

	.data-time {
		margin-top: -18rpx;
		margin-left: 88rpx;
		font-size: 22rpx !important;
		color: #8c9ba2 !important;
	}

	.article-category {
		display: inline-flex;
		align-items: center;
		min-height: 46rpx;
		margin-top: 24rpx;
		padding: 0 18rpx;
		border: 1rpx solid rgba(255, 135, 112, 0.46);
		border-radius: 24rpx;
		background: rgba(255, 239, 235, 0.78);
		font-size: 23rpx;
		color: #8f6f69;
	}

	.article-title {
		display: block;
		margin-top: 20rpx;
		font-size: 31rpx;
		font-weight: 700;
		line-height: 1.45;
		color: #26393d;
		white-space: normal;
	}

	.text-content {
		overflow: hidden;
		display: -webkit-box;
		-webkit-line-clamp: 3;
		-webkit-box-orient: vertical;
		word-break: break-all;
		font-size: 28rpx;
		line-height: 1.75;
		color: #52666b;
	}

	.article-content {
		padding-right: 30rpx !important;
		padding-left: 30rpx !important;
	}

	.grid.grid-square {
		display: flex;
		flex-wrap: wrap;
		gap: 12rpx;
		margin-top: 18rpx;
	}

	.grid.grid-square > view {
		flex: 1 1 calc(33.333% - 8rpx);
		width: auto !important;
		min-width: 0;
		margin: 0 !important;
		padding-bottom: 31.5% !important;
		border-radius: 20rpx !important;
		box-shadow: inset 0 0 0 1rpx rgba(255, 255, 255, 0.38);
		overflow: hidden;
	}

	.grid.grid-square > view:first-child:nth-last-child(1) {
		flex-basis: 100%;
		max-width: 100%;
		padding-bottom: 61.8% !important;
	}

	.grid.grid-square > view:first-child:nth-last-child(2),
	.grid.grid-square > view:first-child:nth-last-child(2) ~ view {
		flex-basis: calc(50% - 6rpx);
		padding-bottom: 48% !important;
	}

	.article-content-btn {
		min-height: 82rpx;
		margin: 20rpx 30rpx 0 !important;
		padding: 0 !important;
		border-top: 1rpx solid rgba(117, 139, 143, 0.14);
		color: #75878c !important;
	}

	.article-content-btn > .tn-padding-xs {
		display: none;
	}

	.article-content-btn .flex.align-center {
		width: 100%;
		justify-content: space-around;
		color: #75878c !important;
	}

	.article-content-btn image {
		opacity: 0.68;
	}

	.article-stats {
		display: grid !important;
		grid-template-columns: repeat(3, minmax(0, 1fr));
		align-items: center;
		column-gap: 14rpx;
		width: 100%;
		min-width: 0;
		color: #75878c !important;
	}

	.article-stats > view {
		display: flex;
		align-items: center;
		justify-content: flex-start;
		gap: 8rpx;
		min-width: 0;
		font-size: 23rpx;
	}

	.article-stats > view text {
		display: inline-block;
		min-width: 0;
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}

	.article-stats > view text:first-child {
		font-size: 28rpx;
		color: #83949a;
	}


	.extra-count {
		position: absolute;
		inset: 0;
		background-color: rgba(0, 0, 0, 0.47);
		color: white;
		font-size: 20px;
		border-radius: 20rpx;
		font-weight: bold;
		display: flex;
		align-items: center;
		justify-content: center;
	}

	.grid.grid-square>uni-view {
		border-radius: 20rpx;
		overflow: hidden;
	}

	.text-ellipsis {
		overflow: hidden;
		text-overflow: ellipsis;
		display: -webkit-box;
		-webkit-line-clamp: 2;
		-webkit-box-orient: vertical;
		word-break: break-all;
		line-height: 1.5;
		font-size: 28rpx;
		color: #647572;
	}

	.article-item-shell.is-home-feed {
		padding-right: 10rpx;
		padding-left: 10rpx;
	}

	.article-item-shell.is-home-feed .cu-card.article.no-card > .cu-item {
		border: 2rpx solid rgba(255, 255, 255, 0.86);
		border-radius: 30rpx;
		background:
			linear-gradient(135deg, rgba(255, 236, 246, 0.72) 0%, rgba(213, 246, 250, 0.88) 46%, rgba(197, 232, 218, 0.78) 100%);
		box-shadow: 0 18rpx 42rpx rgba(37, 87, 91, 0.12);
		backdrop-filter: blur(12px) saturate(1.18);
		-webkit-backdrop-filter: blur(12px) saturate(1.18);
	}

	.article-item-shell.is-home-feed .cu-card.article.no-card:active > .cu-item {
		transform: scale(0.988) translateY(2rpx);
		box-shadow: 0 10rpx 26rpx rgba(37, 87, 91, 0.1);
	}

	.home-article-card {
		padding: 30rpx 32rpx 28rpx;
		color: #1f2f33;
		box-sizing: border-box;
	}

	.home-article-head,
	.home-article-footer,
	.home-article-meta,
	.home-read-link {
		display: flex;
		align-items: center;
	}

	.home-article-head {
		justify-content: space-between;
		gap: 18rpx;
		margin-bottom: 20rpx;
	}

	.home-article-pill {
		display: inline-flex;
		align-items: center;
		max-width: 44vw;
		height: 40rpx;
		padding: 0 16rpx;
		border: 1rpx solid rgba(255, 118, 92, 0.2);
		border-radius: 20rpx;
		background: linear-gradient(135deg, rgba(255, 95, 72, 0.92), rgba(255, 142, 112, 0.9));
		color: #fff;
		font-size: 22rpx;
		font-weight: 700;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
		box-shadow: inset 0 1rpx 0 rgba(255, 255, 255, 0.44);
	}

	.home-article-clock {
		flex: 0 0 auto;
		font-size: 22rpx;
		color: rgba(44, 63, 68, 0.58);
	}

	.home-article-main {
		display: flex;
		align-items: flex-start;
		gap: 22rpx;
		min-width: 0;
	}

	.home-article-copy {
		display: flex;
		flex: 1;
		flex-direction: column;
		min-width: 0;
	}

	.home-article-title {
		display: -webkit-box;
		-webkit-line-clamp: 2;
		-webkit-box-orient: vertical;
		overflow: hidden;
		font-size: 34rpx;
		font-weight: 800;
		line-height: 1.34;
		color: #1e2c31;
		letter-spacing: 0;
	}

	.home-article-desc {
		display: -webkit-box;
		-webkit-line-clamp: 2;
		-webkit-box-orient: vertical;
		overflow: hidden;
		margin-top: 14rpx;
		font-size: 27rpx;
		line-height: 1.55;
		color: rgba(44, 61, 66, 0.62);
		word-break: break-all;
	}

	.home-article-thumb {
		position: relative;
		flex: 0 0 140rpx;
		width: 140rpx;
		height: 112rpx;
		margin-top: 8rpx;
		border-radius: 16rpx;
		background: rgba(137, 196, 191, 0.22);
		overflow: hidden;
		box-shadow: inset 0 0 0 1rpx rgba(255, 255, 255, 0.32);
	}

	.home-article-thumb image {
		width: 100%;
		height: 100%;
		border-radius: 16rpx;
	}

	.home-image-count {
		position: absolute;
		right: 8rpx;
		bottom: 8rpx;
		min-width: 34rpx;
		height: 30rpx;
		padding: 0 8rpx;
		border-radius: 15rpx;
		background: rgba(31, 47, 51, 0.62);
		color: #fff;
		font-size: 20rpx;
		line-height: 30rpx;
		text-align: center;
	}

	.home-article-footer {
		justify-content: space-between;
		gap: 18rpx;
		margin-top: 26rpx;
		padding-top: 20rpx;
		border-top: 1rpx solid rgba(85, 126, 128, 0.16);
	}

	.home-article-meta {
		flex: 1;
		gap: 8rpx;
		min-width: 0;
		font-size: 23rpx;
		color: rgba(43, 61, 66, 0.52);
		overflow: hidden;
		white-space: nowrap;
	}

	.home-article-meta text {
		flex: 0 0 auto;
	}

	.home-article-meta .cuIcon-hotfill {
		color: #ff7d57;
	}

	.home-article-meta text:nth-child(2) {
		min-width: 0;
		max-width: 150rpx;
		overflow: hidden;
		text-overflow: ellipsis;
	}

	.home-meta-dot {
		color: rgba(43, 61, 66, 0.32);
	}

	.home-read-link {
		flex: 0 0 auto;
		gap: 4rpx;
		font-size: 25rpx;
		font-weight: 700;
		color: #168cf0;
	}

	@keyframes feedCardIn {
		from {
			opacity: 0;
			transform: translate3d(0, 14rpx, 0) scale(0.992);
		}
		to {
			opacity: 1;
			transform: translate3d(0, 0, 0) scale(1);
		}
	}

	@media (prefers-reduced-motion: reduce) {
		.article-item-shell.is-entering {
			animation: none;
		}
	}

	@media (max-width: 360px) {
		.article-item-shell { padding: 0 12rpx; }
		.cu-card.article .cu-item > .title { padding: 24rpx 24rpx 10rpx; }
		.article-content { padding-right: 24rpx !important; padding-left: 24rpx !important; }
		.article-content-btn { margin-right: 24rpx !important; margin-left: 24rpx !important; }
		.content-author-name { max-width: 42vw; }
		.article-stats { column-gap: 4rpx; }
	}
</style>
