<template>
	<view class="detail-page" :class="AppStyle">
		<view class="header" :style="[{height: CustomBar + 'px'}]">
			<view class="cu-bar bg-white" :style="{'height': CustomBar + 'px', 'padding-top': StatusBar + 'px'}">
				<view class="action" @tap="back"><text class="cuIcon-back"></text></view>
				<view class="content text-bold" :style="[{top: StatusBar + 'px'}]">互助详情</view>
				<view class="action" v-if="isOwner" @tap="openOwnerActions"><text class="cuIcon-more"></text></view>
			</view>
		</view>
		<view :style="[{paddingTop: NavBar + 'px'}]"></view>

		<view v-if="item" class="detail-content">
			<image v-if="item.imageUrl" class="detail-image" :src="item.imageUrl" mode="widthFix" @tap="previewImage"></image>
			<view class="detail-main">
				<view class="detail-labels">
					<text :class="['kind-label', item.kind === 1 ? 'kind-request' : 'kind-offer']">{{ item.kind === 1 ? '寻求帮助' : '提供帮助' }}</text>
					<text class="category-label">{{ categoryName(item.category) }}</text>
					<text class="free-label">免费互助</text>
					<text v-if="item.status === 2" class="resolved-label">已解决</text>
					<text v-if="item.expired === 1 && item.status === 1" class="expired-label">已过期</text>
				</view>
				<view class="detail-title">{{ item.title }}</view>
				<view v-if="item.status === 0" class="review-banner pending"><text class="cuIcon-time"></text>等待管理员审核</view>
				<view v-if="item.status === 3" class="review-banner rejected"><text class="cuIcon-warn"></text>审核未通过：{{ item.reviewReason || '请修改后重新提交' }}</view>

				<view class="detail-facts">
					<view class="fact-row"><text class="cuIcon-location fact-icon"></text><view><text class="fact-name">地点</text><text class="fact-value">{{ item.location }}</text></view></view>
					<view class="fact-row"><text class="cuIcon-time fact-icon"></text><view><text class="fact-name">时间</text><text class="fact-value">{{ formatDate(item.occurredAt || item.created) }}</text></view></view>
				</view>

				<view class="detail-section">
					<view class="section-title">详细说明</view>
					<text class="description-text" selectable>{{ item.description }}</text>
				</view>

				<view class="publisher" @tap="openPublisher">
					<campus-avatar class="publisher-avatar" :src="publisher.avatar" :name="publisher.name"></campus-avatar>
					<view class="publisher-copy"><text class="publisher-name">{{ publisher.name || '校园用户' }}</text><text class="publisher-campus">{{ publisherCampus }}</text></view>
					<text class="cuIcon-right publisher-arrow"></text>
				</view>
			</view>

			<view class="comment-section">
				<view class="comment-heading"><text>交流区</text><text class="comment-count">{{ flatComments.length }}</text></view>
				<view v-if="commentsLoaded && flatComments.length === 0" class="comment-empty">还没有人回应</view>
				<view v-for="entry in flatComments" :key="entry.comment.id" class="comment-row" :style="{paddingLeft: Math.min(entry.depth, 2) * 48 + 'rpx'}">
					<campus-avatar class="comment-avatar" :src="entry.comment.userJson.avatar" :name="entry.comment.userJson.name"></campus-avatar>
					<view class="comment-body">
						<view class="comment-author-line"><text class="comment-author">{{ entry.comment.userJson.name || '校园用户' }}</text><text v-if="Number(entry.comment.uid) === Number(item.uid)" class="owner-label">发布者</text></view>
						<text class="comment-text" selectable>{{ entry.comment.text }}</text>
						<view class="comment-meta">
							<text>{{ formatDate(entry.comment.created) }}</text>
							<text v-if="canParticipate && item.status === 1 && item.expired !== 1" @tap="replyComment(entry.comment)">回复</text>
							<text v-if="canShareContact(entry.comment)" :class="contactSent(entry.comment.id) ? 'sent-label' : 'contact-action'" @tap="confirmShare(entry.comment)">{{ contactSent(entry.comment.id) ? 'QQ已发送' : '发送我的QQ' }}</text>
							<text v-if="canDeleteComment(entry.comment)" class="delete-action" @tap="deleteComment(entry.comment)">删除</text>
						</view>
						<view v-for="grant in receivedFor(entry.comment.id)" :key="grant.senderUid + '-' + grant.commentId" class="private-contact">
							<view class="private-contact-title"><text class="cuIcon-lock"></text>仅你可见的联系方式</view>
							<view class="private-contact-main"><text>{{ grant.userJson.name || '对方' }}的 QQ</text><text class="qq-number" @tap="copyQq(grant.qq)">{{ grant.qq }} <text class="cuIcon-copy"></text></text></view>
						</view>
					</view>
				</view>
			</view>
		</view>

		<view v-if="item && item.status === 1 && item.expired !== 1" class="comment-composer">
			<view v-if="replyTo" class="reply-context"><text>回复 {{ replyTo.userJson.name }}</text><text class="cuIcon-close" @tap="replyTo = null"></text></view>
			<view class="composer-row">
				<input v-model="commentText" maxlength="1000" :placeholder="composerPlaceholder" confirm-type="send" @confirm="submitComment" />
				<button class="cu-btn composer-send" :disabled="commentSending || !commentText.trim()" @tap="submitComment"><text class="cuIcon-forward"></text></button>
			</view>
		</view>

		<view v-if="shareTarget" class="modal-mask" @touchmove.stop.prevent>
			<view class="contact-modal">
				<view class="contact-modal-icon"><text class="cuIcon-lock"></text></view>
				<text class="contact-modal-title">定向发送联系方式</text>
				<text class="contact-modal-copy">系统会把你绑定 QQ 邮箱对应的 QQ 号，仅展示给 {{ shareTarget.userJson.name || '对方' }}。</text>
				<view class="contact-modal-actions"><view @tap="shareTarget = null">取消</view><view class="contact-confirm" @tap="shareContact">确认发送</view></view>
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
				id: 0, item: null, loading: true, currentUid: 0, currentGroup: '',
				comments: [], commentsLoaded: false, commentText: '', commentSending: false, replyTo: null,
				contactAccess: { received: [], sent: [] }, shareTarget: null,
				featureConfig: { enabled: 1, eligible: false, contactEnabled: 1, minimumLevel: 2 },
				categoryLabels: ['', '失物招领', '物品借用', '学习互助', '校园生活', '其他帮助']
			}
		},
		computed: {
			publisher() { return this.item && this.item.userJson ? this.item.userJson : {} },
			publisherCampus() { var parts = []; if (this.publisher.campus) parts.push(this.publisher.campus); if (this.publisher.grade) parts.push(this.publisher.grade); return parts.length ? parts.join(' · ') : '校园用户' },
			isOwner() { return this.item && Number(this.item.uid) === Number(this.currentUid) },
			isStaff() { return this.currentGroup === 'administrator' || this.currentGroup === 'editor' },
			canParticipate() { return !!this.token() && !!this.featureConfig.eligible },
			composerPlaceholder() { if (!this.token()) return '登录后参与交流'; if (!this.featureConfig.eligible) return '达到Lv' + this.featureConfig.minimumLevel + '后可参与'; return this.replyTo ? '回复 ' + this.replyTo.userJson.name : '公开回复，联系方式请使用定向发送' },
			flatComments() {
				var result = []
				function walk(list, depth) { (list || []).forEach(function(comment) { result.push({ comment: comment, depth: depth }); walk(comment.children, depth + 1) }) }
				walk(this.comments, 0)
				return result
			}
		},
		onLoad(options) {
			// #ifdef APP-PLUS || MP
			this.NavBar = this.CustomBar
			// #endif
			this.id = Number(options.id || 0)
			if (!this.id) {
				uni.showToast({ title: options.sid ? '原商城内容已停用' : '互助信息不存在', icon: 'none' })
				setTimeout(function() { uni.navigateBack({ delta: 1 }) }, 900)
				return
			}
			this.readCurrentUser()
		},
		onShow() {
			// #ifdef APP-PLUS
			plus.navigator.setStatusBarStyle(this.AppStyle === 'campus-night' ? 'light' : 'dark')
			// #endif
			if (this.id) this.loadAll()
		},
		onPullDownRefresh() { this.loadAll() },
		methods: {
			back() { uni.navigateBack({ delta: 1 }) },
			loadAll() { this.getConfig(); this.getInfo(); this.getComments() },
			getConfig() {
				var that = this
				that.$Net.request({ url: that.$API.lostFoundConfig(), data: { token: that.token() }, method: 'get', dataType: 'json', success: function(res) { if (res.data.code === 1) { that.featureConfig = res.data.data; if (that.canParticipate) that.getContactAccess() } } })
			},
			getInfo() {
				var that = this; that.loading = true
				that.$Net.request({
					url: that.$API.lostFoundInfo(), data: { id: that.id, token: that.token() }, header: { 'Content-Type': 'application/x-www-form-urlencoded' }, method: 'get', dataType: 'json',
					success: function(res) { if (res.data.code === 1) that.item = res.data.data; else { uni.showToast({ title: res.data.msg || '信息不存在', icon: 'none' }); setTimeout(function() { that.back() }, 900) } },
					fail: function() { uni.showToast({ title: '网络不太好哦~', icon: 'none' }) }, complete: function() { that.loading = false; uni.stopPullDownRefresh() }
				})
			},
			getComments() {
				var that = this
				that.$Net.request({ url: that.$API.lostFoundCommentList(), data: { itemId: that.id }, method: 'get', dataType: 'json', success: function(res) { if (res.data.code === 1) that.comments = res.data.data || [] }, complete: function() { that.commentsLoaded = true } })
			},
			getContactAccess() {
				var that = this
				that.$Net.request({ url: that.$API.lostFoundContactAccess(), data: { itemId: that.id, token: that.token() }, method: 'get', dataType: 'json', success: function(res) { if (res.data.code === 1) that.contactAccess = res.data.data || { received: [], sent: [] } } })
			},
			previewImage() { uni.previewImage({ urls: [this.item.imageUrl], current: this.item.imageUrl }) },
			categoryName(category) { return this.categoryLabels[Number(category)] || '其他帮助' },
			formatDate(timestamp) { return timestamp ? this.$API.formatDate(timestamp) : '时间待补充' },
			openPublisher() { var user = this.publisher; uni.navigateTo({ url: '/pages/contents/userinfo?title=' + encodeURIComponent((user.name || '校园用户') + '的信息') + '&name=' + encodeURIComponent(user.name || '') + '&uid=' + this.item.uid + '&avatar=' + encodeURIComponent(user.avatar || '') }) },
			openOwnerActions() {
				var that = this; var actions = []
				if (that.item.status !== 2 && that.item.status !== 4) actions.push('编辑互助')
				if (that.item.status === 1) actions.push('标记为已解决')
				if (that.item.status === 2) actions.push('重新发布')
				actions.push('关闭互助')
				uni.showActionSheet({ itemList: actions, success: function(res) { var action = actions[res.tapIndex]; if (action === '编辑互助') uni.navigateTo({ url: '/pages/user/addshop?type=edit&id=' + that.item.id }); else if (action === '标记为已解决') that.changeStatus('resolve'); else if (action === '重新发布') that.changeStatus('reopen'); else if (action === '关闭互助') that.closeItem() } })
			},
			changeStatus(action) {
				var that = this
				that.$Net.request({ url: that.$API.lostFoundStatus(), data: { id: that.item.id, action: action, token: that.token() }, method: 'post', dataType: 'json', success: function(res) { uni.showToast({ title: res.data.msg, icon: 'none' }); if (res.data.code === 1) that.item = res.data.data } })
			},
			closeItem() { var that = this; uni.showModal({ title: '关闭互助', content: '关闭后将停止展示和交流。', success: function(res) { if (res.confirm) that.$Net.request({ url: that.$API.lostFoundDelete(), data: { id: that.item.id, token: that.token() }, method: 'post', dataType: 'json', success: function(r) { uni.showToast({ title: r.data.msg, icon: 'none' }); if (r.data.code === 1) setTimeout(function() { that.back() }, 700) } }) } }) },
			replyComment(comment) { if (!this.ensureParticipation()) return; this.replyTo = comment },
			submitComment() {
				if (!this.ensureParticipation() || !this.commentText.trim() || this.commentSending) return
				var that = this; that.commentSending = true
				that.$Net.request({ url: that.$API.lostFoundCommentAdd(), data: { itemId: that.id, parentId: that.replyTo ? that.replyTo.id : 0, text: that.commentText, token: that.token() }, method: 'post', dataType: 'json', success: function(res) { uni.showToast({ title: res.data.msg, icon: 'none' }); if (res.data.code === 1) { that.commentText = ''; that.replyTo = null; that.getComments() } }, complete: function() { that.commentSending = false } })
			},
			canShareContact(comment) { if (!this.item || !this.canParticipate || !this.featureConfig.contactEnabled || this.item.status !== 1 || this.item.expired === 1) return false; var uid = Number(comment.uid); return (this.isOwner && uid !== Number(this.currentUid)) || (uid === Number(this.currentUid) && !this.isOwner) },
			contactSent(commentId) { return (this.contactAccess.sent || []).some(function(item) { return Number(item.commentId) === Number(commentId) }) },
			receivedFor(commentId) { return (this.contactAccess.received || []).filter(function(item) { return Number(item.commentId) === Number(commentId) }) },
			confirmShare(comment) { if (this.contactSent(comment.id)) return; this.shareTarget = comment },
			shareContact() {
				var that = this; var target = that.shareTarget; if (!target) return
				that.shareTarget = null
				that.$Net.request({ url: that.$API.lostFoundContactShare(), data: { itemId: that.id, commentId: target.id, token: that.token() }, method: 'post', dataType: 'json', success: function(res) { uni.showToast({ title: res.data.msg, icon: 'none' }); if (res.data.code === 1) that.getContactAccess() } })
			},
			canDeleteComment(comment) { return Number(comment.uid) === Number(this.currentUid) || this.isStaff },
			deleteComment(comment) { var that = this; uni.showModal({ title: '删除评论', content: '与这条评论关联的联系方式授权也会失效。', success: function(res) { if (res.confirm) that.$Net.request({ url: that.$API.lostFoundCommentDelete(), data: { commentId: comment.id, token: that.token() }, method: 'post', dataType: 'json', success: function(r) { uni.showToast({ title: r.data.msg, icon: 'none' }); if (r.data.code === 1) { that.getComments(); if (that.canParticipate) that.getContactAccess() } } }) } }) },
			copyQq(qq) { uni.setClipboardData({ data: String(qq), success: function() { uni.showToast({ title: 'QQ号已复制', icon: 'none' }) } }) },
			ensureParticipation() { if (!this.token()) { uni.showToast({ title: '请先登录', icon: 'none' }); return false } if (!this.featureConfig.eligible) { uni.showToast({ title: this.featureConfig.enabled ? '达到Lv' + this.featureConfig.minimumLevel + '后可参与校园互助' : '校园互助暂未开放', icon: 'none' }); return false } return true },
			readCurrentUser() { if (!localStorage.getItem('userinfo')) return; try { var user = JSON.parse(localStorage.getItem('userinfo')); this.currentUid = user.uid || 0; this.currentGroup = user.group || '' } catch (error) { this.currentUid = 0; this.currentGroup = '' } },
			token() { if (localStorage.getItem('token')) return localStorage.getItem('token'); if (!localStorage.getItem('userinfo')) return ''; try { return JSON.parse(localStorage.getItem('userinfo')).token || '' } catch (error) { return '' } }
		}
	}
</script>

<style scoped>
	.detail-page { min-height: 100vh; padding-bottom: 132rpx; background: #f2f5f6; color: #17212b; }
	.detail-content { background: #fff; }
	.detail-image { display: block; width: 100%; max-height: 760rpx; background: #e9eef0; animation: imageIn .3s ease; }
	.detail-main { padding: 30rpx 28rpx 34rpx; }
	.detail-labels { display: flex; align-items: center; gap: 12rpx; flex-wrap: wrap; }
	.kind-label, .category-label, .free-label, .resolved-label, .expired-label { padding: 5rpx 12rpx; border-radius: 6rpx; font-size: 23rpx; }
	.kind-request { color: #b84a3d; background: #fff0ed; }
	.kind-offer { color: #087c6c; background: #e9f8f4; }
	.category-label { color: #596871; background: #eef2f3; }
	.free-label { color: #53636b; background: #f1f4f5; }
	.resolved-label, .expired-label { color: #53636b; background: #e5eaec; }
	.detail-title { margin-top: 22rpx; font-size: 40rpx; line-height: 54rpx; font-weight: 700; }
	.review-banner { margin-top: 22rpx; padding: 18rpx 20rpx; border-radius: 6rpx; font-size: 25rpx; line-height: 36rpx; }
	.review-banner text { margin-right: 10rpx; }
	.pending { color: #84630b; background: #fff7db; }
	.rejected { color: #a43e34; background: #fff0ed; }
	.detail-facts { margin-top: 30rpx; padding: 22rpx 0; border-top: 1rpx solid #e6ecee; border-bottom: 1rpx solid #e6ecee; }
	.fact-row { display: flex; align-items: flex-start; min-height: 64rpx; padding: 10rpx 0; }
	.fact-icon { width: 50rpx; color: #168cf0; font-size: 34rpx; }
	.fact-row view { flex: 1; display: flex; flex-direction: column; }
	.fact-name { color: #87949b; font-size: 22rpx; }
	.fact-value { margin-top: 5rpx; color: #26343c; font-size: 28rpx; line-height: 38rpx; }
	.detail-section { padding: 34rpx 0; border-bottom: 1rpx solid #e6ecee; }
	.section-title { font-size: 30rpx; font-weight: 600; }
	.description-text { display: block; margin-top: 18rpx; color: #3b4951; font-size: 28rpx; line-height: 48rpx; white-space: pre-wrap; word-break: break-word; }
	.publisher { display: flex; align-items: center; padding-top: 30rpx; }
	.publisher-avatar, .comment-avatar { flex-shrink: 0; width: 76rpx; height: 76rpx; }
	.publisher-copy { flex: 1; min-width: 0; display: flex; flex-direction: column; margin-left: 18rpx; }
	.publisher-name { font-size: 28rpx; font-weight: 600; }
	.publisher-campus { margin-top: 6rpx; color: #87949b; font-size: 23rpx; }
	.publisher-arrow { color: #a1adb2; font-size: 30rpx; }
	.comment-section { margin-top: 14rpx; padding: 28rpx; background: #fff; }
	.comment-heading { display: flex; align-items: center; gap: 10rpx; padding-bottom: 20rpx; font-size: 30rpx; font-weight: 600; }
	.comment-count { color: #89969c; font-size: 23rpx; font-weight: 400; }
	.comment-empty { padding: 70rpx 0; text-align: center; color: #939fa5; font-size: 25rpx; }
	.comment-row { display: flex; padding-top: 22rpx; padding-bottom: 22rpx; border-top: 1rpx solid #edf1f2; animation: commentIn .24s ease both; }
	.comment-avatar { width: 64rpx; height: 64rpx; }
	.comment-body { flex: 1; min-width: 0; margin-left: 16rpx; }
	.comment-author-line { display: flex; align-items: center; gap: 10rpx; }
	.comment-author { color: #34434b; font-size: 25rpx; font-weight: 600; }
	.owner-label { padding: 2rpx 8rpx; border-radius: 4rpx; color: #146fca; background: #eaf4ff; font-size: 19rpx; }
	.comment-text { display: block; margin-top: 9rpx; color: #35434b; font-size: 27rpx; line-height: 42rpx; word-break: break-word; }
	.comment-meta { display: flex; flex-wrap: wrap; gap: 22rpx; margin-top: 12rpx; color: #919da3; font-size: 21rpx; }
	.comment-meta .contact-action { color: #168cf0; }
	.comment-meta .sent-label { color: #168a76; }
	.comment-meta .delete-action { color: #bc554a; }
	.private-contact { margin-top: 18rpx; padding: 18rpx 20rpx; border-left: 6rpx solid #168cf0; background: #eef7ff; animation: contactIn .3s ease; }
	.private-contact-title { color: #3978ad; font-size: 21rpx; }
	.private-contact-title text { margin-right: 7rpx; }
	.private-contact-main { display: flex; justify-content: space-between; gap: 16rpx; margin-top: 10rpx; color: #34434b; font-size: 25rpx; }
	.qq-number { color: #146fca; font-weight: 600; }
	.comment-composer { position: fixed; z-index: 80; left: 0; right: 0; bottom: 0; padding: 14rpx 20rpx calc(14rpx + env(safe-area-inset-bottom)); background: rgba(255,255,255,.97); border-top: 1rpx solid #dfe7e9; }
	.reply-context { display: flex; justify-content: space-between; padding: 0 10rpx 10rpx; color: #6f7e85; font-size: 22rpx; }
	.composer-row { display: flex; align-items: center; gap: 12rpx; }
	.composer-row input { flex: 1; height: 72rpx; padding: 0 20rpx; border-radius: 12rpx; background: #f0f4f5; font-size: 26rpx; }
	.composer-send { width: 72rpx; height: 72rpx; padding: 0; display: flex; align-items: center; justify-content: center; border-radius: 12rpx; color: #fff; background: #168cf0; }
	.composer-send[disabled] { color: #9ba6ab; background: #dfe5e7; }
	.modal-mask { position: fixed; z-index: 1000; inset: 0; display: flex; align-items: center; justify-content: center; background: rgba(18,27,32,.44); animation: fadeIn .18s ease; }
	.contact-modal { width: 570rpx; padding: 34rpx 30rpx 0; overflow: hidden; border-radius: 16rpx; background: #fff; text-align: center; animation: modalIn .2s ease; }
	.contact-modal-icon { width: 76rpx; height: 76rpx; line-height: 76rpx; margin: 0 auto 18rpx; border-radius: 50%; color: #168cf0; background: #eaf4ff; font-size: 38rpx; }
	.contact-modal-title, .contact-modal-copy { display: block; }
	.contact-modal-title { font-size: 31rpx; font-weight: 600; }
	.contact-modal-copy { margin: 16rpx 8rpx 30rpx; color: #66757d; font-size: 25rpx; line-height: 40rpx; }
	.contact-modal-actions { display: grid; grid-template-columns: 1fr 1fr; margin: 0 -30rpx; border-top: 1rpx solid #e4eaec; }
	.contact-modal-actions view { height: 88rpx; line-height: 88rpx; color: #66757d; font-size: 27rpx; }
	.contact-confirm { border-left: 1rpx solid #e4eaec; color: #168cf0 !important; font-weight: 600; }
	.campus-night.detail-page { background: #15191b; color: #edf3f0; }
	.campus-night .detail-content,
	.campus-night .comment-section { background: #202527; }
	.campus-night .detail-image { background: #293032; }
	.campus-night .category-label,
	.campus-night .free-label,
	.campus-night .resolved-label,
	.campus-night .expired-label { background: #293032; color: #b7c1bd; }
	.campus-night .detail-facts,
	.campus-night .detail-section,
	.campus-night .comment-row { border-color: #333b3c; }
	.campus-night .fact-name,
	.campus-night .publisher-campus,
	.campus-night .comment-count,
	.campus-night .comment-empty,
	.campus-night .comment-meta { color: #a9b5b0; }
	.campus-night .fact-value,
	.campus-night .description-text,
	.campus-night .comment-author,
	.campus-night .comment-text { color: #e1e7e4; }
	.campus-night .private-contact { border-left-color: #559bd5; background: #223748; }
	.campus-night .private-contact-title,
	.campus-night .qq-number { color: #78bafa; }
	.campus-night .private-contact-main { color: #d8e1dd; }
	.campus-night .comment-composer { border-top-color: #333b3c; background: rgba(27, 33, 35, .97); }
	.campus-night .composer-row input { background: #293032; color: #edf3f0; }
	.campus-night .contact-modal { background: #202527; }
	.campus-night .contact-modal-copy,
	.campus-night .contact-modal-actions view { color: #b7c1bd; }
	.campus-night .contact-modal-actions,
	.campus-night .contact-confirm { border-color: #333b3c; }
	/* #ifdef H5 */
	@media screen and (min-width: 820px) {
		.detail-content,
		.comment-section { width: 760px; margin-right: auto; margin-left: auto; }
		.comment-composer { right: auto; left: 50%; width: 760px; transform: translateX(-50%); }
	}
	/* #endif */
	@keyframes imageIn { from { opacity: .5; transform: scale(1.01); } to { opacity: 1; transform: scale(1); } }
	@keyframes commentIn { from { opacity: 0; transform: translateY(8rpx); } to { opacity: 1; transform: translateY(0); } }
	@keyframes contactIn { from { opacity: 0; transform: translateY(8rpx); } to { opacity: 1; transform: translateY(0); } }
	@keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }
	@keyframes modalIn { from { opacity: 0; transform: scale(1.04); } to { opacity: 1; transform: scale(1); } }
</style>
