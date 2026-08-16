<template>
	<view class="my-help-page" :class="AppStyle">
		<view class="header" :style="[{height: CustomBar + 'px'}]">
			<view class="cu-bar bg-white" :style="{'height': CustomBar + 'px', 'padding-top': StatusBar + 'px'}">
				<view class="action" @tap="back"><text class="cuIcon-back"></text></view>
				<view class="content text-bold" :style="[{top: StatusBar + 'px'}]">我的互助</view>
				<view class="action" @tap="publish"><text class="cuIcon-add header-add"></text></view>
			</view>
		</view>
		<view :style="[{paddingTop: NavBar + 'px'}]"></view>

		<scroll-view scroll-x class="status-tabs" :style="{top: NavBar + 'px'}" :show-scrollbar="false">
			<view class="status-tabs-inner">
				<view v-for="tab in tabs" :key="tab.value" :class="['status-tab', status === tab.value ? 'active' : '']" @tap="setStatus(tab.value)">{{ tab.label }}</view>
			</view>
		</scroll-view>

		<view class="my-help-list">
			<view v-if="loaded && items.length === 0" class="empty-state"><text class="cuIcon-form empty-icon"></text><text>暂无相关互助</text><button class="cu-btn bg-blue" @tap="publish">发布互助</button></view>
			<view v-for="(item, index) in items" :key="item.id" class="my-help-item" :style="{animationDelay: Math.min(index, 5) * 45 + 'ms'}">
				<view class="item-main" @tap="openItem(item.id)">
					<image v-if="item.imageUrl" class="item-image" :src="item.imageUrl" mode="aspectFill"></image>
					<view v-else class="item-image image-placeholder"><text class="cuIcon-form"></text></view>
					<view class="item-copy">
						<view class="item-labels"><text :class="item.kind === 1 ? 'request-label' : 'offer-label'">{{ item.kind === 1 ? '求助' : '可帮助' }}</text><text class="category-label">{{ categoryName(item.category) }}</text></view>
						<text class="item-title">{{ item.title }}</text>
						<text :class="['status-label', 'status-' + item.status]">{{ statusName(item.status) }}</text>
					</view>
				</view>
				<view v-if="item.reviewReason && item.status === 3" class="reject-reason">审核意见：{{ item.reviewReason }}</view>
				<view class="item-actions">
					<button v-if="item.status === 0 || item.status === 1 || item.status === 3" class="cu-btn line-gray" @tap="editItem(item)"><text class="cuIcon-edit"></text>编辑</button>
					<button v-if="item.status === 1" class="cu-btn line-green" @tap="changeStatus(item, 'resolve')"><text class="cuIcon-roundcheck"></text>已解决</button>
					<button v-if="item.status === 2" class="cu-btn line-blue" @tap="changeStatus(item, 'reopen')"><text class="cuIcon-refresh"></text>重新发布</button>
					<button v-if="item.status !== 4" class="cu-btn line-red" @tap="closeItem(item)"><text class="cuIcon-close"></text>关闭</button>
				</view>
			</view>
			<view v-if="items.length" class="load-more" @tap="loadMore">{{ moreText }}</view>
		</view>

		<view class="loading" v-if="loading"><view class="loading-main"><view class="campus-loader"></view></view></view>
	</view>
</template>

<script>
	import { localStorage } from '../../js_sdk/mp-storage/mp-storage/index.js'
	export default {
		data() {
			return {
				StatusBar: this.StatusBar, CustomBar: this.CustomBar, NavBar: this.StatusBar + this.CustomBar,
				AppStyle: this.$store.state.AppStyle,
				status: -1, page: 1, items: [], loading: true, loadingMore: false, loaded: false, hasMore: true, moreText: '加载更多',
				tabs: [{ value: -1, label: '全部' }, { value: 0, label: '待审核' }, { value: 1, label: '进行中' }, { value: 2, label: '已解决' }, { value: 3, label: '未通过' }, { value: 4, label: '已关闭' }],
				categories: ['', '失物招领', '物品借用', '学习互助', '校园生活', '其他帮助']
			}
		},
		onLoad() {
			// #ifdef APP-PLUS || MP
			this.NavBar = this.CustomBar
			// #endif
			if (!this.token()) { uni.showToast({ title: '请先登录', icon: 'none' }); setTimeout(function() { uni.redirectTo({ url: '/pages/user/login' }) }, 700) }
		},
		onShow() { if (this.token()) this.reload() },
		onReachBottom() { this.loadMore() },
		methods: {
			back() { uni.navigateBack({ delta: 1 }) },
			publish() { uni.navigateTo({ url: '/pages/user/addshop' }) },
			setStatus(value) { this.status = value; this.reload() },
			reload() { this.page = 1; this.hasMore = true; this.fetch(false) },
			loadMore() { if (this.hasMore && !this.loading && !this.loadingMore) this.fetch(true) },
			fetch(append) {
				var that = this; var next = append ? that.page + 1 : 1; that.loading = !append; that.loadingMore = append; if (append) that.moreText = '加载中...'
				that.$Net.request({ url: that.$API.lostFoundManage(), data: { token: that.token(), status: that.status, page: next, limit: 10 }, method: 'get', dataType: 'json', success: function(res) { if (res.data.code === 1) { var list = res.data.data || []; that.items = append ? that.items.concat(list) : list; that.page = next; that.hasMore = list.length === 10; that.moreText = that.hasMore ? '加载更多' : '没有更多了' } else uni.showToast({ title: res.data.msg, icon: 'none' }) }, fail: function() { uni.showToast({ title: '网络不太好哦~', icon: 'none' }) }, complete: function() { that.loading = false; that.loadingMore = false; that.loaded = true } })
			},
			openItem(id) { uni.navigateTo({ url: '/pages/contents/shopinfo?id=' + id }) },
			editItem(item) { uni.navigateTo({ url: '/pages/user/addshop?type=edit&id=' + item.id }) },
			changeStatus(item, action) { var that = this; that.$Net.request({ url: that.$API.lostFoundStatus(), data: { token: that.token(), id: item.id, action: action }, method: 'post', dataType: 'json', success: function(res) { uni.showToast({ title: res.data.msg, icon: 'none' }); if (res.data.code === 1) that.reload() } }) },
			closeItem(item) { var that = this; uni.showModal({ title: '关闭互助', content: '关闭后将停止展示和交流。', success: function(res) { if (res.confirm) that.$Net.request({ url: that.$API.lostFoundDelete(), data: { token: that.token(), id: item.id }, method: 'post', dataType: 'json', success: function(r) { uni.showToast({ title: r.data.msg, icon: 'none' }); if (r.data.code === 1) that.reload() } }) } }) },
			categoryName(value) { return this.categories[Number(value)] || '其他帮助' },
			statusName(value) { return ['等待审核', '进行中', '已解决', '审核未通过', '已关闭'][Number(value)] || '未知状态' },
			token() { if (localStorage.getItem('token')) return localStorage.getItem('token'); if (!localStorage.getItem('userinfo')) return ''; try { return JSON.parse(localStorage.getItem('userinfo')).token || '' } catch (error) { return '' } }
		}
	}
</script>

<style scoped>
	.my-help-page { min-height: 100vh; background: #f2f5f6; color: #17212b; }
	.header-add { color: #168cf0; font-size: 38rpx; }
	.status-tabs { position: sticky; top: 0; z-index: 8; background: #fff; border-bottom: 1rpx solid #e4eaec; white-space: nowrap; }
	.status-tabs-inner { display: inline-flex; padding: 0 18rpx; }
	.status-tab { position: relative; height: 82rpx; line-height: 82rpx; padding: 0 22rpx; color: #738188; font-size: 26rpx; }
	.status-tab.active { color: #17212b; font-weight: 600; }
	.status-tab.active:after { content: ''; position: absolute; left: 25%; right: 25%; bottom: 0; height: 5rpx; background: #168cf0; border-radius: 3rpx; }
	.my-help-list { padding: 20rpx 24rpx 50rpx; }
	.my-help-item { margin-bottom: 18rpx; padding: 18rpx; background: #fff; border: 1rpx solid #e2e9eb; border-radius: 8rpx; animation: itemIn .3s ease both; }
	.item-main { display: flex; }
	.item-image { width: 150rpx; height: 150rpx; flex: 0 0 150rpx; border-radius: 6rpx; background: #eaf0f1; }
	.image-placeholder { display: flex; align-items: center; justify-content: center; color: #98a5aa; font-size: 44rpx; }
	.item-copy { flex: 1; min-width: 0; margin-left: 18rpx; display: flex; flex-direction: column; }
	.item-labels { display: flex; gap: 10rpx; }
	.item-labels text { padding: 3rpx 9rpx; border-radius: 5rpx; font-size: 20rpx; }
	.request-label { color: #b84a3d; background: #fff0ed; }
	.offer-label { color: #087c6c; background: #e9f8f4; }
	.category-label { color: #637078; background: #eef2f3; }
	.item-title { margin-top: 12rpx; font-size: 29rpx; line-height: 40rpx; font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
	.status-label { margin-top: auto; font-size: 23rpx; }
	.status-0 { color: #9a730f; } .status-1 { color: #087c6c; } .status-2 { color: #53636b; } .status-3 { color: #b84a3d; } .status-4 { color: #8b979d; }
	.reject-reason { margin-top: 16rpx; padding: 14rpx 16rpx; color: #a6463c; background: #fff2ef; font-size: 23rpx; line-height: 34rpx; }
	.item-actions { display: flex; justify-content: flex-end; flex-wrap: wrap; gap: 12rpx; margin-top: 18rpx; padding-top: 16rpx; border-top: 1rpx solid #edf1f2; }
	.item-actions button { height: 58rpx; padding: 0 20rpx; border-radius: 8rpx; font-size: 23rpx; }
	.item-actions text { margin-right: 6rpx; }
	.empty-state { min-height: 55vh; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 22rpx; color: #849197; }
	.empty-icon { color: #a6b1b6; font-size: 68rpx; }
	.load-more { padding: 26rpx; text-align: center; color: #87949a; font-size: 24rpx; }
	.campus-night.my-help-page { background: #15191b; color: #edf3f0; }
	.campus-night .status-tabs { border-bottom-color: #333b3c; background: #202527; }
	.campus-night .status-tab { color: #a9b5b0; }
	.campus-night .status-tab.active { color: #edf3f0; }
	.campus-night .my-help-item { border-color: #333b3c; background: #202527; }
	.campus-night .item-image,
	.campus-night .image-placeholder,
	.campus-night .category-label { background: #293032; color: #aeb9b5; }
	.campus-night .reject-reason { background: #392927; color: #ef9b91; }
	.campus-night .item-actions { border-color: #333b3c; }
	.campus-night .empty-state,
	.campus-night .load-more { color: #a9b5b0; }
	/* #ifdef H5 */
	@media screen and (min-width: 820px) {
		.status-tabs,
		.my-help-list { width: 760px; margin-right: auto; margin-left: auto; }
	}
	/* #endif */
	@keyframes itemIn { from { opacity: 0; transform: translateY(12rpx); } to { opacity: 1; transform: translateY(0); } }
</style>
