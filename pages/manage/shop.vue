<template>
	<view class="manage-help-page" :class="AppStyle">
		<view class="header" :style="[{height: CustomBar + 'px'}]">
			<view class="cu-bar bg-white" :style="{'height': CustomBar + 'px', 'padding-top': StatusBar + 'px'}">
				<view class="action" @tap="back"><text class="cuIcon-back"></text></view>
				<view class="content text-bold" :style="[{top: StatusBar + 'px'}]">校园互助管理</view>
				<view class="action"></view>
			</view>
		</view>
		<view :style="[{paddingTop: NavBar + 'px'}]"></view>

		<view class="manage-tabs">
			<view :class="tab === 'review' ? 'active' : ''" @tap="tab = 'review'">审核队列</view>
			<view :class="tab === 'settings' ? 'active' : ''" @tap="openSettings">功能设置</view>
		</view>

		<view v-if="tab === 'review'" class="review-panel">
			<view class="review-filter">
				<view v-for="option in statusOptions" :key="option.value" :class="reviewStatus === option.value ? 'active' : ''" @tap="setReviewStatus(option.value)">{{ option.label }}</view>
			</view>
			<view v-if="loaded && items.length === 0" class="empty-state"><text class="cuIcon-roundcheck empty-icon"></text><text>当前没有待处理信息</text></view>
			<view v-for="(item, index) in items" :key="item.id" class="review-item" :style="{animationDelay: Math.min(index, 5) * 45 + 'ms'}">
				<view class="review-main" @tap="openItem(item.id)">
					<image v-if="item.imageUrl" class="review-image" :src="item.imageUrl" mode="aspectFill"></image>
					<view v-else class="review-image image-placeholder"><text class="cuIcon-form"></text></view>
					<view class="review-copy">
						<view class="review-labels"><text :class="item.kind === 1 ? 'request-label' : 'offer-label'">{{ item.kind === 1 ? '求助' : '可帮助' }}</text><text>{{ categoryName(item.category) }}</text></view>
						<text class="review-title">{{ item.title }}</text>
						<text class="review-location"><text class="cuIcon-location"></text>{{ item.location }}</text>
					</view>
				</view>
				<view v-if="item.status === 0" class="review-actions"><button class="cu-btn line-red" @tap="openReject(item)"><text class="cuIcon-close"></text>拒绝</button><button class="cu-btn bg-green" @tap="audit(item, 'approve', '')"><text class="cuIcon-check"></text>通过</button></view>
			</view>
		</view>

		<view v-else class="settings-panel">
			<view class="setting-row"><view><text class="setting-title">开放校园互助</text><text class="setting-description">关闭后停止新的发布、评论和联系方式授权</text></view><switch color="#168cf0" :checked="settings.enabled === 1" @change="settings.enabled = $event.detail.value ? 1 : 0" /></view>
			<view class="setting-row"><view><text class="setting-title">最低参与等级</text><text class="setting-description">浏览不受限制，参与操作需要达到该等级</text></view><picker :range="levelLabels" :value="settings.minimumLevel" @change="settings.minimumLevel = Number($event.detail.value)"><view class="setting-picker">Lv{{ settings.minimumLevel }}<text class="cuIcon-right"></text></view></picker></view>
			<view class="setting-row"><view><text class="setting-title">发布后审核</text><text class="setting-description">普通用户提交后先进入审核队列</text></view><switch color="#168cf0" :checked="settings.auditRequired === 1" @change="settings.auditRequired = $event.detail.value ? 1 : 0" /></view>
			<view class="setting-row"><view><text class="setting-title">定向发送 QQ</text><text class="setting-description">联系方式仍只对指定接收者可见</text></view><switch color="#168cf0" :checked="settings.contactEnabled === 1" @change="settings.contactEnabled = $event.detail.value ? 1 : 0" /></view>
			<view class="setting-row"><view><text class="setting-title">每日发送上限</text><text class="setting-description">每个账号每天最多发送联系方式的次数</text></view><input class="number-input" type="number" v-model.number="settings.dailyContactLimit" /></view>
			<view class="setting-row"><view><text class="setting-title">信息有效期</text><text class="setting-description">到期后停止交流，并从进行中列表隐藏</text></view><view class="number-with-unit"><input class="number-input" type="number" v-model.number="settings.itemExpiryDays" /><text>天</text></view></view>
			<view v-if="currentGroup !== 'administrator'" class="admin-note">只有管理员可以修改校园互助设置</view>
			<button class="cu-btn bg-blue save-settings" :disabled="saving || currentGroup !== 'administrator'" @tap="saveSettings"><text class="cuIcon-save"></text>{{ saving ? '保存中...' : '保存设置' }}</button>
		</view>

		<view v-if="rejectItem" class="modal-mask" @touchmove.stop.prevent>
			<view class="reject-modal">
				<view class="reject-title">填写拒绝理由</view>
				<textarea v-model="rejectReason" maxlength="500" placeholder="说明需要修改的内容"></textarea>
				<view class="reject-actions"><view @tap="rejectItem = null">取消</view><view class="reject-confirm" @tap="submitReject">确认拒绝</view></view>
			</view>
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
				tab: 'review', reviewStatus: 0, items: [], loaded: false, loading: true, saving: false,
				currentGroup: '', rejectItem: null, rejectReason: '',
				statusOptions: [{ value: 0, label: '待审核' }, { value: 1, label: '进行中' }, { value: 2, label: '已解决' }, { value: 3, label: '未通过' }],
				levelLabels: ['Lv0', 'Lv1', 'Lv2', 'Lv3', 'Lv4', 'Lv5', 'Lv6', 'Lv7', 'Lv8', 'Lv9'],
				settings: { enabled: 1, minimumLevel: 2, auditRequired: 1, contactEnabled: 1, dailyContactLimit: 5, itemExpiryDays: 30 },
				categories: ['', '失物招领', '物品借用', '学习互助', '校园生活', '其他帮助']
			}
		},
		onLoad() {
			// #ifdef APP-PLUS || MP
			this.NavBar = this.CustomBar
			// #endif
			this.readUser()
		},
		onShow() { this.loadItems() },
		methods: {
			back() { uni.navigateBack({ delta: 1 }) },
			readUser() { if (!localStorage.getItem('userinfo')) return; try { this.currentGroup = JSON.parse(localStorage.getItem('userinfo')).group || '' } catch (error) { this.currentGroup = '' } },
			setReviewStatus(value) { this.reviewStatus = value; this.loadItems() },
			loadItems() { var that = this; that.loading = true; that.$Net.request({ url: that.$API.lostFoundManage(), data: { token: that.token(), status: that.reviewStatus, page: 1, limit: 30 }, method: 'get', dataType: 'json', success: function(res) { if (res.data.code === 1) that.items = res.data.data || []; else uni.showToast({ title: res.data.msg, icon: 'none' }) }, complete: function() { that.loaded = true; that.loading = false } }) },
			openSettings() { this.tab = 'settings'; this.loadSettings() },
			loadSettings() { var that = this; that.loading = true; that.$Net.request({ url: that.$API.lostFoundConfigManage(), data: { token: that.token() }, method: 'get', dataType: 'json', success: function(res) { if (res.data.code === 1) that.settings = Object.assign({}, that.settings, res.data.data); else uni.showToast({ title: res.data.msg, icon: 'none' }) }, complete: function() { that.loading = false } }) },
			saveSettings() { if (this.currentGroup !== 'administrator' || this.saving) return; var that = this; that.saving = true; that.$Net.request({ url: that.$API.lostFoundConfigSave(), data: { token: that.token(), params: JSON.stringify(that.settings) }, method: 'post', dataType: 'json', success: function(res) { uni.showToast({ title: res.data.msg, icon: 'none' }); if (res.data.code === 1) that.settings = Object.assign({}, that.settings, res.data.data) }, complete: function() { that.saving = false } }) },
			openItem(id) { uni.navigateTo({ url: '/pages/contents/shopinfo?id=' + id }) },
			openReject(item) { this.rejectItem = item; this.rejectReason = '' },
			submitReject() { if (!this.rejectReason.trim()) { uni.showToast({ title: '请填写拒绝理由', icon: 'none' }); return } this.audit(this.rejectItem, 'reject', this.rejectReason); this.rejectItem = null },
			audit(item, action, reason) { var that = this; that.$Net.request({ url: that.$API.lostFoundAudit(), data: { token: that.token(), id: item.id, action: action, reason: reason }, method: 'post', dataType: 'json', success: function(res) { uni.showToast({ title: res.data.msg, icon: 'none' }); if (res.data.code === 1) that.loadItems() } }) },
			categoryName(value) { return this.categories[Number(value)] || '其他帮助' },
			token() { if (localStorage.getItem('token')) return localStorage.getItem('token'); if (!localStorage.getItem('userinfo')) return ''; try { return JSON.parse(localStorage.getItem('userinfo')).token || '' } catch (error) { return '' } }
		}
	}
</script>

<style scoped>
	.manage-help-page { min-height: 100vh; background: #f2f5f6; color: #17212b; }
	.manage-tabs { display: grid; grid-template-columns: 1fr 1fr; background: #fff; border-bottom: 1rpx solid #e2e8ea; }
	.manage-tabs view { position: relative; height: 82rpx; line-height: 82rpx; text-align: center; color: #748188; font-size: 27rpx; }
	.manage-tabs .active { color: #17212b; font-weight: 600; }
	.manage-tabs .active:after { content: ''; position: absolute; left: 34%; right: 34%; bottom: 0; height: 5rpx; background: #168cf0; }
	.review-panel { padding: 18rpx 24rpx 50rpx; }
	.review-filter { display: flex; gap: 12rpx; overflow-x: auto; padding: 4rpx 0 18rpx; }
	.review-filter view { flex: 0 0 auto; padding: 11rpx 20rpx; border-radius: 8rpx; color: #68767d; background: #e8edef; font-size: 23rpx; }
	.review-filter .active { color: #146fca; background: #e6f2ff; }
	.review-item { margin-bottom: 16rpx; padding: 18rpx; border: 1rpx solid #e1e8ea; border-radius: 8rpx; background: #fff; animation: itemIn .28s ease both; }
	.review-main { display: flex; }
	.review-image { width: 145rpx; height: 145rpx; flex: 0 0 145rpx; border-radius: 6rpx; background: #e9eff0; }
	.image-placeholder { display: flex; align-items: center; justify-content: center; color: #9aa6ab; font-size: 42rpx; }
	.review-copy { flex: 1; min-width: 0; margin-left: 18rpx; display: flex; flex-direction: column; }
	.review-labels { display: flex; gap: 9rpx; }
	.review-labels > text { padding: 3rpx 9rpx; border-radius: 5rpx; color: #65737a; background: #eef2f3; font-size: 20rpx; }
	.review-labels .request-label { color: #b84a3d; background: #fff0ed; }
	.review-labels .offer-label { color: #087c6c; background: #e9f8f4; }
	.review-title { margin-top: 12rpx; font-size: 29rpx; font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
	.review-location { margin-top: auto; color: #7b898f; font-size: 22rpx; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
	.review-location text { margin-right: 6rpx; }
	.review-actions { display: flex; justify-content: flex-end; gap: 14rpx; margin-top: 18rpx; padding-top: 16rpx; border-top: 1rpx solid #edf1f2; }
	.review-actions button { min-width: 142rpx; height: 60rpx; border-radius: 8rpx; font-size: 24rpx; }
	.review-actions text { margin-right: 7rpx; }
	.settings-panel { margin-top: 14rpx; padding-bottom: 50rpx; background: #fff; }
	.setting-row { min-height: 112rpx; padding: 20rpx 28rpx; display: flex; align-items: center; justify-content: space-between; gap: 24rpx; border-bottom: 1rpx solid #e8edef; }
	.setting-row > view:first-child { flex: 1; display: flex; flex-direction: column; }
	.setting-title { font-size: 27rpx; font-weight: 600; }
	.setting-description { margin-top: 6rpx; color: #87949a; font-size: 21rpx; line-height: 31rpx; }
	.setting-picker { min-width: 108rpx; color: #168cf0; text-align: right; font-size: 27rpx; }
	.setting-picker text { margin-left: 6rpx; color: #a0aaaf; }
	.number-input { width: 112rpx; height: 58rpx; padding: 0 12rpx; border: 1rpx solid #d7e0e3; border-radius: 8rpx; text-align: center; font-size: 26rpx; }
	.number-with-unit { display: flex; align-items: center; gap: 9rpx; color: #6f7c83; font-size: 24rpx; }
	.admin-note { margin: 22rpx 28rpx 0; padding: 16rpx 18rpx; color: #765816; background: #fff8e4; border-left: 6rpx solid #e5a51b; font-size: 23rpx; }
	.save-settings { width: calc(100% - 56rpx); height: 82rpx; margin: 26rpx 28rpx 0; border-radius: 8rpx; font-size: 28rpx; }
	.save-settings text { margin-right: 8rpx; }
	.empty-state { min-height: 50vh; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 18rpx; color: #87949a; }
	.empty-icon { color: #18a47f; font-size: 68rpx; }
	.modal-mask { position: fixed; z-index: 1000; inset: 0; display: flex; align-items: center; justify-content: center; background: rgba(18,27,32,.44); }
	.reject-modal { width: 590rpx; overflow: hidden; border-radius: 16rpx; background: #fff; animation: modalIn .2s ease; }
	.reject-title { padding: 28rpx 28rpx 10rpx; text-align: center; font-size: 30rpx; font-weight: 600; }
	.reject-modal textarea { width: calc(100% - 56rpx); height: 190rpx; margin: 18rpx 28rpx 28rpx; padding: 18rpx; border-radius: 8rpx; background: #f1f4f5; font-size: 26rpx; }
	.reject-actions { display: grid; grid-template-columns: 1fr 1fr; border-top: 1rpx solid #e3e9eb; }
	.reject-actions view { height: 86rpx; line-height: 86rpx; text-align: center; color: #67757c; font-size: 27rpx; }
	.reject-confirm { border-left: 1rpx solid #e3e9eb; color: #c94e42 !important; font-weight: 600; }
	.campus-night.manage-help-page { background: #15191b; color: #edf3f0; }
	.campus-night .manage-tabs,
	.campus-night .settings-panel { border-color: #333b3c; background: #202527; }
	.campus-night .manage-tabs view { color: #a9b5b0; }
	.campus-night .manage-tabs .active { color: #edf3f0; }
	.campus-night .review-filter view,
	.campus-night .review-labels > text { background: #293032; color: #aeb9b5; }
	.campus-night .review-filter .active { background: #223748; color: #78bafa; }
	.campus-night .review-item { border-color: #333b3c; background: #202527; }
	.campus-night .review-image,
	.campus-night .image-placeholder { background: #293032; color: #9facb0; }
	.campus-night .review-location,
	.campus-night .setting-description,
	.campus-night .number-with-unit,
	.campus-night .empty-state { color: #a9b5b0; }
	.campus-night .review-actions,
	.campus-night .setting-row { border-color: #333b3c; }
	.campus-night .number-input,
	.campus-night .reject-modal textarea { border-color: #3b4446; background: #293032; color: #edf3f0; }
	.campus-night .admin-note { border-left-color: #c9932c; background: #332e21; color: #e1c97f; }
	.campus-night .reject-modal { background: #202527; }
	.campus-night .reject-actions,
	.campus-night .reject-confirm { border-color: #333b3c; }
	.campus-night .reject-actions view { color: #b7c1bd; }
	/* #ifdef H5 */
	@media screen and (min-width: 820px) {
		.manage-tabs,
		.review-panel,
		.settings-panel { width: 760px; margin-right: auto; margin-left: auto; }
	}
	/* #endif */
	@keyframes itemIn { from { opacity: 0; transform: translateY(10rpx); } to { opacity: 1; transform: translateY(0); } }
	@keyframes modalIn { from { opacity: 0; transform: scale(1.04); } to { opacity: 1; transform: scale(1); } }
</style>
