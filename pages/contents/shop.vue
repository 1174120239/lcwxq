<template>
	<view class="lost-page" :class="AppStyle">
		<view class="header" :style="[{height: CustomBar + 'px'}]">
			<view class="cu-bar bg-white" :style="{'height': CustomBar + 'px', 'padding-top': StatusBar + 'px'}">
				<view class="action" @tap="back"><text class="cuIcon-back"></text></view>
				<view class="content text-bold" :style="[{top: StatusBar + 'px'}]">校园互助</view>
				<view class="action" @tap="publish"><text class="cuIcon-add lost-header-icon"></text></view>
			</view>
		</view>
		<view :style="[{paddingTop: NavBar + 'px'}]"></view>

		<view class="lost-controls" :style="{top: NavBar + 'px'}">
			<view v-if="configLoaded && !featureConfig.enabled" class="access-banner">校园互助暂未开放</view>
			<view v-else-if="configLoaded && token() && !featureConfig.eligible" class="access-banner">达到 Lv{{ featureConfig.minimumLevel }} 后可参与互助</view>
			<view class="lost-search">
				<text class="cuIcon-search"></text>
				<input v-model="keyword" confirm-type="search" placeholder="搜索标题、内容或地点" @confirm="reload" />
				<text v-if="keyword" class="cuIcon-close" @tap="clearKeyword"></text>
			</view>
			<view class="lost-segments">
				<view :class="['lost-segment', kind === 0 ? 'active-all' : '']" @tap="setKind(0)">全部</view>
				<view :class="['lost-segment', kind === 1 ? 'active-lost' : '']" @tap="setKind(1)">寻求帮助</view>
				<view :class="['lost-segment', kind === 2 ? 'active-found' : '']" @tap="setKind(2)">提供帮助</view>
			</view>
			<view class="lost-filter-row">
				<picker :range="categoryLabels" :value="category" @change="changeCategory">
					<view class="lost-filter"><text class="cuIcon-filter"></text>{{ categoryLabels[category] }}<text class="cuIcon-unfold"></text></view>
				</picker>
				<view class="lost-state-toggle">
					<text :class="state === 0 ? 'selected' : ''" @tap="setState(0)">进行中</text>
					<text :class="state === 2 ? 'selected' : ''" @tap="setState(2)">已解决</text>
				</view>
			</view>
		</view>

		<view class="lost-list">
			<view class="lost-empty" v-if="loaded && items.length === 0">
				<text class="cuIcon-searchlist lost-empty-icon"></text>
				<text>暂时没有相关信息</text>
				<button class="cu-btn bg-blue lost-empty-action" @tap="publish">发布信息</button>
			</view>
			<view class="lost-item" v-for="(item, index) in items" :key="item.id" :style="{animationDelay: Math.min(index, 5) * 45 + 'ms'}" @tap="openItem(item.id)">
				<image v-if="item.imageUrl" class="lost-thumb" :src="item.imageUrl" mode="aspectFill"></image>
				<view v-else class="lost-thumb lost-thumb-placeholder"><text class="cuIcon-pic"></text></view>
				<view class="lost-item-body">
					<view class="lost-item-top">
						<text :class="['lost-kind', item.kind === 1 ? 'kind-lost' : 'kind-found']">{{ item.kind === 1 ? '求助' : '可帮助' }}</text>
						<text class="lost-category">{{ categoryName(item.category) }}</text>
						<text class="lost-free">免费</text>
					</view>
					<view class="lost-title">{{ item.title }}</view>
					<view class="lost-meta"><text class="cuIcon-location"></text>{{ item.location }}</view>
					<view class="lost-foot">
						<text>{{ item.userJson && item.userJson.name ? item.userJson.name : '校园用户' }}</text>
						<text>{{ formatDate(item.occurredAt || item.created) }}</text>
					</view>
				</view>
			</view>
			<view class="lost-more" v-if="items.length > 0" @tap="loadMore">{{ moreText }}</view>
		</view>

		<view class="loading" v-if="loading">
			<view class="loading-main"><view class="campus-loader"></view></view>
		</view>
	</view>
</template>

<script>
	import { localStorage } from '../../js_sdk/mp-storage/mp-storage/index.js'
	export default {
		data() {
			return {
				StatusBar: this.StatusBar,
				CustomBar: this.CustomBar,
				NavBar: this.StatusBar + this.CustomBar,
				AppStyle: this.$store.state.AppStyle,
				items: [],
				keyword: '',
				kind: 0,
				category: 0,
				state: 0,
				page: 1,
				loading: true,
				loaded: false,
				loadingMore: false,
				hasMore: true,
				moreText: '加载更多',
				categoryLabels: ['全部分类', '失物招领', '物品借用', '学习互助', '校园生活', '其他帮助'],
				configLoaded: false,
				featureConfig: { enabled: 1, eligible: false, minimumLevel: 2 }
			}
		},
		onLoad() {
			// #ifdef APP-PLUS || MP
			this.NavBar = this.CustomBar
			// #endif
		},
		onShow() {
			// #ifdef APP-PLUS
			plus.navigator.setStatusBarStyle(this.AppStyle === 'campus-night' ? 'light' : 'dark')
			// #endif
			this.getConfig()
			this.reload()
		},
		onPullDownRefresh() {
			this.reload()
		},
		onReachBottom() {
			this.loadMore()
		},
		methods: {
			back() { uni.navigateBack({ delta: 1 }) },
			publish() {
				if (!this.token()) {
					uni.showToast({ title: '请先登录', icon: 'none' })
					setTimeout(function() { uni.navigateTo({ url: '/pages/user/login' }) }, 700)
					return
				}
				if (this.configLoaded && !this.featureConfig.eligible) {
					uni.showToast({ title: this.featureConfig.enabled ? '达到Lv' + this.featureConfig.minimumLevel + '后可参与校园互助' : '校园互助暂未开放', icon: 'none' })
					return
				}
				uni.navigateTo({ url: '/pages/user/addshop' })
			},
			getConfig() {
				var that = this
				that.$Net.request({
					url: that.$API.lostFoundConfig(), data: { token: that.token() }, method: 'get', dataType: 'json',
					success: function(res) { if (res.data.code === 1) that.featureConfig = res.data.data },
					complete: function() { that.configLoaded = true }
				})
			},
			openItem(id) { uni.navigateTo({ url: '/pages/contents/shopinfo?id=' + id }) },
			setKind(value) { this.kind = value; this.reload() },
			setState(value) { this.state = value; this.reload() },
			changeCategory(event) { this.category = Number(event.detail.value); this.reload() },
			clearKeyword() { this.keyword = ''; this.reload() },
			reload() {
				this.page = 1
				this.hasMore = true
				this.moreText = '加载更多'
				this.fetchItems(false)
			},
			loadMore() {
				if (!this.hasMore || this.loadingMore || this.loading) return
				this.fetchItems(true)
			},
			fetchItems(append) {
				var that = this
				var nextPage = append ? that.page + 1 : 1
				if (append) {
					that.loadingMore = true
					that.moreText = '加载中...'
				} else {
					that.loading = true
				}
				that.$Net.request({
					url: that.$API.lostFoundList(),
					data: { page: nextPage, limit: 10, kind: that.kind, category: that.category, state: that.state, keyword: that.keyword },
					header: { 'Content-Type': 'application/x-www-form-urlencoded' },
					method: 'get',
					dataType: 'json',
					success: function(res) {
						if (res.data.code === 1) {
							var list = res.data.data || []
							that.items = append ? that.items.concat(list) : list
							that.page = nextPage
							that.hasMore = list.length === 10
							that.moreText = that.hasMore ? '加载更多' : '没有更多了'
						} else {
							uni.showToast({ title: res.data.msg || '加载失败', icon: 'none' })
						}
					},
					fail: function() { uni.showToast({ title: '网络不太好哦~', icon: 'none' }) },
					complete: function() {
						that.loading = false
						that.loaded = true
						that.loadingMore = false
						uni.stopPullDownRefresh()
					}
				})
			},
			categoryName(value) { return this.categoryLabels[Number(value)] || '其他帮助' },
			formatDate(timestamp) {
				if (!timestamp) return '时间待补充'
				return this.$API.formatDate(timestamp)
			},
			token() {
				if (localStorage.getItem('token')) return localStorage.getItem('token')
				if (!localStorage.getItem('userinfo')) return ''
				try { return JSON.parse(localStorage.getItem('userinfo')).token || '' } catch (error) { return '' }
			}
		}
	}
</script>

<style scoped>
	.lost-page { min-height: 100vh; background: #f4f7f8; color: #17212b; }
	.lost-header-icon { font-size: 38rpx; color: #168cf0; }
	.lost-controls { position: sticky; top: 0; z-index: 8; padding: 20rpx 24rpx 16rpx; background: #fff; border-bottom: 1rpx solid #e7edef; }
	.access-banner { margin-bottom: 16rpx; padding: 16rpx 18rpx; border-left: 6rpx solid #e5a51b; background: #fff8e4; color: #765816; font-size: 24rpx; }
	.lost-search { height: 72rpx; display: flex; align-items: center; gap: 14rpx; padding: 0 22rpx; background: #f2f5f6; border-radius: 8rpx; color: #71817f; }
	.lost-search input { flex: 1; height: 72rpx; font-size: 28rpx; }
	.lost-segments { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12rpx; margin-top: 18rpx; }
	.lost-segment { height: 64rpx; line-height: 64rpx; text-align: center; background: #f4f6f7; border: 1rpx solid transparent; border-radius: 8rpx; font-size: 27rpx; color: #52616b; }
	.active-all { color: #146fca; background: #eaf4ff; border-color: #a8d2fb; }
	.active-lost { color: #b84a3d; background: #fff0ed; border-color: #efb1a9; }
	.active-found { color: #087c6c; background: #e9f8f4; border-color: #92d5c8; }
	.lost-filter-row { display: flex; justify-content: space-between; align-items: center; margin-top: 18rpx; }
	.lost-filter { color: #52616b; font-size: 25rpx; display: flex; align-items: center; gap: 8rpx; }
	.lost-state-toggle { display: flex; gap: 26rpx; color: #87939b; font-size: 25rpx; }
	.lost-state-toggle .selected { color: #17212b; font-weight: 600; }
	.lost-list { padding: 20rpx 24rpx 40rpx; }
	.lost-item { display: flex; min-height: 218rpx; margin-bottom: 18rpx; padding: 18rpx; background: #fff; border: 1rpx solid #e4eaed; border-radius: 8rpx; animation: itemIn .32s ease both; transition: transform .16s ease, background-color .16s ease; }
	.lost-item:active { transform: scale(.985); background: #f9fbfb; }
	.lost-thumb { flex: 0 0 180rpx; width: 180rpx; height: 180rpx; border-radius: 6rpx; background: #eaf0f1; }
	.lost-thumb-placeholder { display: flex; align-items: center; justify-content: center; color: #9aa8ad; font-size: 52rpx; }
	.lost-item-body { min-width: 0; flex: 1; margin-left: 20rpx; display: flex; flex-direction: column; }
	.lost-item-top { display: flex; align-items: center; gap: 12rpx; }
	.lost-kind, .lost-category, .lost-free { padding: 4rpx 10rpx; border-radius: 6rpx; font-size: 21rpx; line-height: 30rpx; }
	.kind-lost { color: #b84a3d; background: #fff0ed; }
	.kind-found { color: #087c6c; background: #e9f8f4; }
	.lost-category { color: #66747d; background: #eef2f3; }
	.lost-free { margin-left: auto; color: #53636b; background: #f1f4f5; }
	.lost-title { margin-top: 12rpx; font-size: 31rpx; line-height: 42rpx; font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
	.lost-meta { margin-top: 12rpx; color: #52616b; font-size: 24rpx; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
	.lost-meta text { margin-right: 7rpx; }
	.lost-foot { margin-top: auto; display: flex; justify-content: space-between; gap: 12rpx; color: #8a979e; font-size: 22rpx; }
	.lost-empty { min-height: 55vh; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 22rpx; color: #7d8a91; }
	.lost-empty-icon { font-size: 72rpx; color: #aab5ba; }
	.lost-empty-action { margin-top: 8rpx; }
	.lost-more { padding: 28rpx 0; text-align: center; color: #7a888f; font-size: 25rpx; }
	.campus-night.lost-page { background: #15191b; color: #edf3f0; }
	.campus-night .lost-controls { border-bottom-color: #333b3c; background: #202527; }
	.campus-night .access-banner { border-left-color: #c9932c; background: #332e21; color: #e1c97f; }
	.campus-night .lost-search,
	.campus-night .lost-segment { background: #293032; color: #bdc7c3; }
	.campus-night .lost-search input { color: #edf3f0; }
	.campus-night .active-all { border-color: #3a77a7; background: #223748; color: #78bafa; }
	.campus-night .active-lost { border-color: #76504a; background: #392927; color: #ef9b91; }
	.campus-night .active-found { border-color: #386b62; background: #203a35; color: #70cdbb; }
	.campus-night .lost-filter,
	.campus-night .lost-state-toggle,
	.campus-night .lost-more { color: #a9b5b0; }
	.campus-night .lost-state-toggle .selected { color: #edf3f0; }
	.campus-night .lost-item { border-color: #333b3c; background: #202527; }
	.campus-night .lost-item:active { background: #252b2d; }
	.campus-night .lost-thumb,
	.campus-night .lost-thumb-placeholder,
	.campus-night .lost-category,
	.campus-night .lost-free { background: #293032; color: #aeb9b5; }
	.campus-night .lost-meta,
	.campus-night .lost-foot,
	.campus-night .lost-empty { color: #a9b5b0; }
	/* #ifdef H5 */
	@media screen and (min-width: 820px) {
		.lost-controls,
		.lost-list { width: 760px; margin-right: auto; margin-left: auto; }
	}
	/* #endif */
	@keyframes itemIn { from { opacity: 0; transform: translateY(14rpx); } to { opacity: 1; transform: translateY(0); } }
</style>
