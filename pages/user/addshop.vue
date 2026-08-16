<template>
	<view class="editor-page" :class="AppStyle">
		<view class="header" :style="[{height: CustomBar + 'px'}]">
			<view class="cu-bar bg-white" :style="{'height': CustomBar + 'px', 'padding-top': StatusBar + 'px'}">
				<view class="action" @tap="handleBack"><text class="cuIcon-back"></text></view>
				<view class="content text-bold" :style="[{top: StatusBar + 'px'}]">{{ editing ? '修改互助' : '发布互助' }}</view>
				<view class="action"></view>
			</view>
		</view>
		<view :style="[{paddingTop: NavBar + 'px'}]"></view>

		<view class="editor-form">
			<view class="form-section">
				<view class="field-label">信息类型</view>
				<view class="kind-picker">
					<view :class="['kind-option', form.kind === 1 ? 'kind-lost-active' : '']" @tap="form.kind = 1"><text class="cuIcon-question"></text><text>我需要帮助</text></view>
					<view :class="['kind-option', form.kind === 2 ? 'kind-found-active' : '']" @tap="form.kind = 2"><text class="cuIcon-friendadd"></text><text>我可以提供帮助</text></view>
				</view>
			</view>

			<view class="form-section image-section">
				<view v-if="form.imageUrl" class="cover-preview" @tap="chooseImage">
					<image :src="form.imageUrl" mode="aspectFill"></image>
					<view class="cover-replace"><text class="cuIcon-camera"></text>更换图片</view>
					<view class="image-remove" @tap.stop="form.imageUrl = ''"><text class="cuIcon-close"></text></view>
				</view>
				<view v-else class="cover-add" @tap="chooseImage">
					<view class="cover-icon"><text class="cuIcon-cameraadd"></text></view>
					<text class="cover-title">添加相关图片</text>
					<text class="cover-subtitle">选填，可帮助同学快速了解情况</text>
				</view>
			</view>

			<view class="form-section form-fields">
				<view class="field-row">
					<text class="field-label inline-label">互助分类</text>
					<view class="field-control picker-value" @tap="showCategorySheet = true">{{ categoryLabels[form.category - 1] }}<text class="cuIcon-right"></text></view>
				</view>
				<view class="field-row vertical-row">
					<text class="field-label">标题</text>
					<input v-model="form.title" maxlength="120" placeholder="用一句话说明需要或能提供的帮助" />
					<text class="field-count">{{ form.title.length }}/120</text>
				</view>
				<view class="field-row vertical-row">
					<text class="field-label">地点</text>
					<input v-model="form.location" maxlength="120" placeholder="填写互助发生的校内地点" />
				</view>
				<view class="field-row">
					<text class="field-label inline-label">发生时间</text>
					<view class="date-controls">
						<picker mode="date" :value="date" :end="today" @change="date = $event.detail.value"><text>{{ date }}</text></picker>
						<picker mode="time" :value="time" @change="time = $event.detail.value"><text>{{ time }}</text></picker>
					</view>
				</view>
			</view>

			<view class="form-section">
				<view class="field-label">详细说明</view>
				<textarea v-model="form.description" maxlength="5000" placeholder="说明具体情况、希望获得的帮助或你能提供的帮助。请勿公开QQ号、证件号码等个人信息。" />
				<view class="description-count">{{ form.description.length }}/5000</view>
			</view>

		</view>

		<view v-if="showCategorySheet" class="sheet-mask" @tap="showCategorySheet = false">
			<view class="category-sheet" @tap.stop>
				<view class="sheet-handle"></view>
				<view class="sheet-title">选择互助分类</view>
				<view v-for="(label, index) in categoryLabels" :key="label" :class="['sheet-option', form.category === index + 1 ? 'sheet-option-active' : '']" @tap="selectCategory(index + 1)">
					<text>{{ label }}</text><text v-if="form.category === index + 1" class="cuIcon-check"></text>
				</view>
				<view class="sheet-cancel" @tap="showCategorySheet = false">取消</view>
			</view>
		</view>

		<view class="editor-bottom-bar">
			<button class="cu-btn bg-blue editor-submit-bottom" :disabled="submitting || !canSubmit" @tap="submit">
				<view v-if="submitting" class="button-spinner"></view>
				<text v-else class="cuIcon-upload"></text>{{ submitting ? '提交中...' : (editing ? '保存修改' : '提交审核') }}
			</button>
		</view>

		<view v-if="showExitModal" class="modal-mask" @touchmove.stop.prevent>
			<view class="exit-modal">
				<view class="exit-copy"><text class="exit-title">退出发布？</text><text class="exit-description">当前填写的内容不会被保存。</text></view>
				<view class="exit-actions"><view @tap="showExitModal = false">继续编辑</view><view class="exit-confirm" @tap="confirmExit">退出</view></view>
			</view>
		</view>

		<view v-if="hud.visible" class="hud-layer">
			<view class="hud-box">
				<view v-if="hud.type === 'loading'" class="hud-spinner"></view>
				<text v-else-if="hud.type === 'success'" class="cuIcon-check hud-mark"></text>
				<text v-else class="cuIcon-close hud-mark"></text>
				<text class="hud-text">{{ hud.text }}</text>
			</view>
		</view>

		<view class="loading" v-if="loading"><view class="loading-main"><view class="campus-loader"></view></view></view>
	</view>
</template>

<script>
	import { localStorage } from '../../js_sdk/mp-storage/mp-storage/index.js'
	export default {
		data() {
			var now = new Date()
			return {
				StatusBar: this.StatusBar,
				CustomBar: this.CustomBar,
				NavBar: this.StatusBar + this.CustomBar,
				AppStyle: this.$store.state.AppStyle,
				id: 0,
				editing: false,
				loading: false,
				submitting: false,
				date: this.dateString(now),
				time: this.timeString(now),
				today: this.dateString(now),
				categoryLabels: ['失物招领', '物品借用', '学习互助', '校园生活', '其他帮助'],
				showCategorySheet: false,
				showExitModal: false,
				submitSuccess: false,
				hud: { visible: false, type: 'loading', text: '' },
				form: { kind: 1, category: 1, title: '', location: '', imageUrl: '', description: '' }
			}
		},
		onLoad(options) {
			// #ifdef APP-PLUS || MP
			this.NavBar = this.CustomBar
			// #endif
			if (!this.token()) {
				uni.showToast({ title: '请先登录', icon: 'none' })
				setTimeout(function() { uni.redirectTo({ url: '/pages/user/login' }) }, 700)
				return
			}
			this.editing = options.type === 'edit'
			this.id = Number(options.id || 0)
			if (this.editing && !this.id) {
				uni.showToast({ title: '原商城内容已停用', icon: 'none' })
				this.submitSuccess = true
				setTimeout(function() { uni.navigateBack({ delta: 1 }) }, 900)
				return
			}
			this.checkEligibility()
			if (this.editing && this.id) this.loadItem()
		},
		onShow() {
			// #ifdef APP-PLUS
			plus.navigator.setStatusBarStyle(this.AppStyle === 'campus-night' ? 'light' : 'dark')
			// #endif
		},
		computed: {
			canSubmit() { return this.form.title.trim().length >= 4 && this.form.location.trim().length >= 2 && this.form.description.trim().length >= 5 }
		},
		onBackPress() {
			if (this.hasUnsavedChanges()) { this.showExitModal = true; return true }
			return false
		},
		methods: {
			dateString(date) {
				var month = ('0' + (date.getMonth() + 1)).slice(-2)
				var day = ('0' + date.getDate()).slice(-2)
				return date.getFullYear() + '-' + month + '-' + day
			},
			timeString(date) { return ('0' + date.getHours()).slice(-2) + ':' + ('0' + date.getMinutes()).slice(-2) },
			handleBack() { if (this.hasUnsavedChanges()) this.showExitModal = true; else uni.navigateBack({ delta: 1 }) },
			confirmExit() { this.showExitModal = false; uni.navigateBack({ delta: 1 }) },
			hasUnsavedChanges() { return !this.submitSuccess && !!(this.form.title || this.form.location || this.form.description || this.form.imageUrl) },
			selectCategory(value) { this.form.category = value; this.showCategorySheet = false },
			checkEligibility() {
				var that = this
				that.$Net.request({
					url: that.$API.lostFoundConfig(), data: { token: that.token() }, method: 'get', dataType: 'json',
					success: function(res) {
						if (res.data.code === 1 && !res.data.data.eligible) {
							uni.showToast({ title: res.data.data.enabled ? '达到Lv' + res.data.data.minimumLevel + '后可参与校园互助' : '校园互助暂未开放', icon: 'none' })
							that.submitSuccess = true
							setTimeout(function() { uni.navigateBack({ delta: 1 }) }, 900)
						}
					}
				})
			},
			showHud(text, type) {
				this.hud = { visible: true, text: text, type: type || 'loading' }
				var that = this
				if (type && type !== 'loading') setTimeout(function() { that.hud.visible = false }, 1200)
			},
			hideHud() { this.hud.visible = false },
			loadItem() {
				var that = this
				that.loading = true
				that.$Net.request({
					url: that.$API.lostFoundInfo(),
					data: { id: that.id, token: that.token() },
					header: { 'Content-Type': 'application/x-www-form-urlencoded' },
					method: 'get', dataType: 'json',
					success: function(res) {
						if (res.data.code !== 1) { uni.showToast({ title: res.data.msg, icon: 'none' }); return }
						var item = res.data.data
						that.form = { kind: Number(item.kind), category: Number(item.category), title: item.title || '', location: item.location || '', imageUrl: item.imageUrl || '', description: item.description || '' }
						if (item.occurredAt) {
							var occurred = new Date(Number(item.occurredAt) * 1000)
							that.date = that.dateString(occurred)
							that.time = that.timeString(occurred)
						}
					},
					fail: function() { uni.showToast({ title: '网络不太好哦~', icon: 'none' }) },
					complete: function() { that.loading = false }
				})
			},
			chooseImage() {
				var that = this
				uni.chooseImage({ count: 1, sizeType: ['compressed'], sourceType: ['album', 'camera'], success: function(res) { that.uploadImage(res.tempFilePaths[0]) } })
			},
			uploadImage(filePath) {
				var that = this
				that.showHud('上传中...', 'loading')
				uni.uploadFile({
					url: that.$API.upload(), filePath: filePath, name: 'file', formData: { token: that.token() },
					success: function(response) {
						try {
							var data = JSON.parse(response.data)
							if (data.code === 1 && data.data && data.data.url) {
								that.form.imageUrl = data.data.url
								that.showHud('上传完成', 'success')
							} else that.showHud(data.msg || '上传失败', 'error')
						} catch (error) { that.showHud('上传响应异常', 'error') }
					},
					fail: function() { that.showHud('图片上传失败', 'error') }
				})
			},
			validate() {
				if (this.form.title.trim().length < 4) return '标题至少需要4个字'
				if (this.form.location.trim().length < 2) return '请填写具体地点'
				if (this.form.description.trim().length < 5) return '请补充互助的详细说明'
				return ''
			},
			submit() {
				if (this.submitting) return
				var validation = this.validate()
				if (validation) { uni.showToast({ title: validation, icon: 'none' }); return }
				var occurredAt = Math.floor(new Date(this.date.replace(/-/g, '/') + ' ' + this.time + ':00').getTime() / 1000)
				var params = Object.assign({}, this.form, { occurredAt: occurredAt })
				if (this.editing) params.id = this.id
				var that = this
				that.submitting = true
				that.$Net.request({
					url: that.editing ? that.$API.lostFoundEdit() : that.$API.lostFoundAdd(),
					data: { token: that.token(), params: JSON.stringify(params) },
					header: { 'Content-Type': 'application/x-www-form-urlencoded' },
					method: 'post', dataType: 'json',
					success: function(res) {
						if (res.data.code === 1) {
							that.submitSuccess = true
							that.showHud(res.data.msg || '提交成功', 'success')
							var itemId = res.data.data && res.data.data.id ? res.data.data.id : that.id
							setTimeout(function() { uni.redirectTo({ url: '/pages/contents/shopinfo?id=' + itemId }) }, 1100)
						} else that.showHud(res.data.msg || '提交失败', 'error')
					},
					fail: function() { that.showHud('网络不太好哦~', 'error') },
					complete: function() { that.submitting = false }
				})
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
	.editor-page { min-height: 100vh; padding-bottom: 138rpx; background: #f2f5f6; color: #17212b; }
	.editor-form { padding: 14rpx 0 0; }
	.form-section { margin-bottom: 14rpx; padding: 28rpx; background: #fff; border-top: 1rpx solid #e5ebed; border-bottom: 1rpx solid #e5ebed; animation: sectionIn .28s ease both; }
	.field-label { display: block; color: #26343c; font-size: 27rpx; font-weight: 600; }
	.kind-picker { display: grid; grid-template-columns: 1fr 1fr; gap: 16rpx; margin-top: 20rpx; }
	.kind-option { min-height: 90rpx; padding: 12rpx; display: flex; align-items: center; justify-content: center; gap: 12rpx; border: 1rpx solid #d8e0e3; border-radius: 16rpx; color: #63717a; font-size: 26rpx; transition: transform .16s ease, background-color .18s ease, border-color .18s ease; }
	.kind-option:active { transform: scale(.98); }
	.kind-option text:first-child { font-size: 34rpx; }
	.kind-lost-active { color: #b84a3d; background: #fff0ed; border-color: #efaaa1; }
	.kind-found-active { color: #087c6c; background: #e9f8f4; border-color: #86d1c2; }
	.image-section { padding: 0; }
	.cover-add, .cover-preview { height: 360rpx; position: relative; overflow: hidden; }
	.cover-add { display: flex; flex-direction: column; align-items: center; justify-content: center; background: #fafcfc; color: #718087; }
	.cover-icon { width: 82rpx; height: 82rpx; display: flex; align-items: center; justify-content: center; margin-bottom: 18rpx; border-radius: 50%; background: #eef3f4; font-size: 44rpx; }
	.cover-title { color: #34434b; font-size: 28rpx; font-weight: 600; }
	.cover-subtitle { margin-top: 8rpx; color: #8b989e; font-size: 22rpx; }
	.cover-preview image { width: 100%; height: 100%; }
	.cover-replace { position: absolute; left: 0; right: 0; bottom: 0; padding: 44rpx 0 18rpx; text-align: center; color: #fff; font-size: 23rpx; background: linear-gradient(to top, rgba(18, 28, 33, .72), transparent); }
	.cover-replace text { margin-right: 8rpx; }
	.image-remove { position: absolute; top: 16rpx; right: 16rpx; width: 56rpx; height: 56rpx; display: flex; align-items: center; justify-content: center; border-radius: 50%; color: #fff; background: rgba(20, 28, 32, .72); }
	.form-fields { padding-top: 0; padding-bottom: 0; }
	.field-row { min-height: 102rpx; display: flex; align-items: center; border-bottom: 1rpx solid #e7edef; }
	.field-row:last-child { border-bottom: 0; }
	.inline-label { flex: 0 0 170rpx; }
	.field-control { flex: 1; }
	.picker-value { display: flex; justify-content: flex-end; align-items: center; gap: 10rpx; color: #53616a; font-size: 27rpx; }
	.vertical-row { position: relative; min-height: 132rpx; flex-direction: column; align-items: stretch; justify-content: center; gap: 12rpx; }
	.vertical-row input { width: 100%; height: 52rpx; padding-right: 74rpx; color: #26343c; font-size: 28rpx; }
	.field-count { position: absolute; right: 0; bottom: 20rpx; color: #9aa5aa; font-size: 20rpx; }
	.date-controls { flex: 1; display: flex; justify-content: flex-end; gap: 24rpx; color: #53616a; font-size: 27rpx; }
	.form-section textarea { width: 100%; height: 310rpx; margin-top: 18rpx; color: #34434b; font-size: 28rpx; line-height: 44rpx; }
	.description-count { text-align: right; color: #9aa5aa; font-size: 21rpx; }
	.editor-bottom-bar { position: fixed; z-index: 80; left: 0; right: 0; bottom: 0; padding: 18rpx 28rpx calc(18rpx + env(safe-area-inset-bottom)); background: rgba(255, 255, 255, .96); border-top: 1rpx solid #dfe7e9; }
	.editor-submit-bottom { width: 100%; height: 86rpx; border-radius: 16rpx; font-size: 29rpx; transition: transform .16s ease, opacity .16s ease; }
	.editor-submit-bottom:active { transform: scale(.985); }
	.editor-submit-bottom[disabled] { color: #89959b !important; background: #dfe5e7 !important; }
	.editor-submit-bottom text { margin-right: 10rpx; }
	.button-spinner, .hud-spinner { border-radius: 50%; animation: spin .8s linear infinite; }
	.button-spinner { width: 30rpx; height: 30rpx; margin-right: 12rpx; border: 4rpx solid rgba(255,255,255,.35); border-top-color: #fff; }
	.sheet-mask { position: fixed; z-index: 1000; inset: 0; display: flex; align-items: flex-end; background: rgba(18, 27, 32, .42); animation: fadeIn .2s ease; }
	.category-sheet { width: 100%; padding: 14rpx 24rpx calc(20rpx + env(safe-area-inset-bottom)); background: #fff; border-radius: 16rpx 16rpx 0 0; animation: sheetUp .28s cubic-bezier(.22,.78,.25,1); }
	.sheet-handle { width: 64rpx; height: 8rpx; margin: 0 auto 18rpx; border-radius: 4rpx; background: #dce3e5; }
	.sheet-title { padding: 10rpx 8rpx 22rpx; text-align: center; font-size: 30rpx; font-weight: 600; }
	.sheet-option { min-height: 88rpx; padding: 0 22rpx; display: flex; align-items: center; justify-content: space-between; border-top: 1rpx solid #edf1f2; color: #45545c; font-size: 28rpx; }
	.sheet-option-active { color: #168cf0; font-weight: 600; }
	.sheet-cancel { height: 82rpx; line-height: 82rpx; margin-top: 14rpx; text-align: center; border-radius: 12rpx; background: #f1f4f5; color: #59676e; font-size: 28rpx; }
	.modal-mask { position: fixed; z-index: 1100; inset: 0; display: flex; align-items: center; justify-content: center; background: rgba(18, 27, 32, .42); animation: fadeIn .18s ease; }
	.exit-modal { width: 560rpx; overflow: hidden; border-radius: 16rpx; background: rgba(255,255,255,.98); animation: modalIn .2s ease; }
	.exit-copy { padding: 40rpx 32rpx 34rpx; text-align: center; }
	.exit-title, .exit-description { display: block; }
	.exit-title { font-size: 32rpx; font-weight: 600; }
	.exit-description { margin-top: 12rpx; color: #718087; font-size: 25rpx; }
	.exit-actions { display: grid; grid-template-columns: 1fr 1fr; border-top: 1rpx solid #e2e8ea; }
	.exit-actions view { height: 88rpx; line-height: 88rpx; text-align: center; color: #168cf0; font-size: 28rpx; }
	.exit-confirm { border-left: 1rpx solid #e2e8ea; color: #d84b3e !important; font-weight: 600; }
	.hud-layer { position: fixed; z-index: 1200; inset: 0; display: flex; align-items: center; justify-content: center; pointer-events: none; }
	.hud-box { min-width: 210rpx; padding: 30rpx 38rpx; display: flex; flex-direction: column; align-items: center; border-radius: 16rpx; background: rgba(21, 29, 34, .88); animation: hudIn .18s ease; }
	.hud-spinner { width: 50rpx; height: 50rpx; border: 5rpx solid rgba(255,255,255,.25); border-top-color: #fff; }
	.hud-mark { color: #fff; font-size: 52rpx; }
	.hud-text { margin-top: 14rpx; color: #fff; font-size: 25rpx; }
	.campus-night.editor-page { background: #15191b; color: #edf3f0; }
	.campus-night .form-section { border-color: #333b3c; background: #202527; }
	.campus-night .field-label,
	.campus-night .cover-title,
	.campus-night .vertical-row input,
	.campus-night .form-section textarea { color: #edf3f0; }
	.campus-night .kind-option { border-color: #3b4446; color: #bdc7c3; }
	.campus-night .kind-lost-active { border-color: #76504a; background: #392927; color: #ef9b91; }
	.campus-night .kind-found-active { border-color: #386b62; background: #203a35; color: #70cdbb; }
	.campus-night .cover-add,
	.campus-night .cover-icon { background: #293032; }
	.campus-night .cover-add,
	.campus-night .cover-subtitle,
	.campus-night .field-count,
	.campus-night .description-count { color: #a9b5b0; }
	.campus-night .field-row { border-color: #333b3c; }
	.campus-night .picker-value,
	.campus-night .date-controls { color: #c6cfcb; }
	.campus-night .editor-bottom-bar { border-top-color: #333b3c; background: rgba(27, 33, 35, .97); }
	.campus-night .category-sheet,
	.campus-night .exit-modal { background: #202527; }
	.campus-night .sheet-handle { background: #4a5557; }
	.campus-night .sheet-option,
	.campus-night .exit-actions { border-color: #333b3c; }
	.campus-night .sheet-option { color: #d7dfdc; }
	.campus-night .sheet-cancel { background: #293032; color: #bdc7c3; }
	.campus-night .exit-description { color: #a9b5b0; }
	.campus-night .exit-confirm { border-color: #333b3c; }
	/* #ifdef H5 */
	@media screen and (min-width: 820px) {
		.editor-form { width: 760px; margin-right: auto; margin-left: auto; }
		.editor-bottom-bar { right: auto; left: 50%; width: 760px; transform: translateX(-50%); }
		.category-sheet { width: 760px; margin-right: auto; margin-left: auto; }
	}
	/* #endif */
	@keyframes sectionIn { from { opacity: 0; transform: translateY(12rpx); } to { opacity: 1; transform: translateY(0); } }
	@keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }
	@keyframes sheetUp { from { transform: translateY(100%); } to { transform: translateY(0); } }
	@keyframes modalIn { from { opacity: 0; transform: scale(1.04); } to { opacity: 1; transform: scale(1); } }
	@keyframes hudIn { from { opacity: 0; transform: scale(.94); } to { opacity: 1; transform: scale(1); } }
	@keyframes spin { to { transform: rotate(360deg); } }
</style>
