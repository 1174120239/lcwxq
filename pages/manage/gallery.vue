<template>
	<view class="gallery-manage" :class="{ night: night }">
		<view class="manage-nav" :style="{ paddingTop: StatusBar + 'px' }">
			<view class="manage-nav-inner">
				<text class="cuIcon-back manage-back" @tap="back"></text>
				<view class="manage-nav-brand">
					<text class="manage-nav-title">图集审核</text>
					<text class="manage-nav-eyebrow">GALLERY REVIEW</text>
				</view>
				<text class="manage-nav-note">ADMIN</text>
			</view>
		</view>

		<view class="manage-content" :style="{ paddingTop: (StatusBar + 58) + 'px' }">
			<view class="intro">
				<view class="intro-copy">
					<text class="eyebrow">IMAGE CHANNEL</text>
					<text class="intro-title">把校园照片，整理成册</text>
					<text class="intro-desc">审核独立图集，不会混入动态、问答或普通帖子。</text>
				</view>
				<view class="intro-mark"><text class="cuIcon-picfill"></text></view>
			</view>

			<scroll-view class="filters" scroll-x :show-scrollbar="false">
				<view class="filters-track">
					<view v-for="item in statuses" :key="item.value" class="filter-chip" :class="{ active: status === item.value }" @tap="setStatus(item.value)">{{ item.label }}</view>
				</view>
			</scroll-view>

			<view class="tag-filter">
				<text class="cuIcon-search"></text>
				<input v-model="tag" placeholder="按标签筛选" confirm-type="search" @confirm="reload" />
				<text v-if="tag" class="cuIcon-close tag-clear" @tap="clearTag"></text>
			</view>

			<view v-if="!loading && !items.length" class="empty">
				<view class="empty-mark"><text class="cuIcon-picfill"></text></view>
				<text class="empty-title">暂无图集</text>
				<text class="empty-sub">新的投稿会出现在这里。</text>
			</view>

			<view v-for="item in items" :key="item.id" class="gallery-row">
				<view class="gallery-images" @tap="preview(item)">
					<image v-if="item.imageUrls && item.imageUrls[0]" :src="item.imageUrls[0]" mode="aspectFill" />
					<view v-else class="image-fallback"><text class="cuIcon-picfill"></text></view>
					<text v-if="item.imageUrls && item.imageUrls.length > 1" class="image-count">{{ item.imageUrls.length }} 张</text>
				</view>
				<view class="gallery-main">
					<view class="gallery-title-line">
						<text class="gallery-title">{{ item.title }}</text>
						<text class="status" :class="'status-' + item.status">{{ statusLabel(item.status) }}</text>
					</view>
					<text v-if="item.description" class="gallery-description">{{ item.description }}</text>
					<text class="gallery-meta">{{ item.authorName || '未知用户' }} · {{ formatDate(item.created) }} · {{ item.views || 0 }} 次浏览</text>
					<view v-if="item.tags && item.tags.length" class="gallery-tags"><text v-for="tagName in item.tags" :key="tagName">#{{ tagName }}</text></view>
					<text v-if="item.reviewReason" class="gallery-reason">审核意见：{{ item.reviewReason }}</text>
					<view class="gallery-actions">
						<text class="act" @tap="preview(item)">查看</text>
						<text v-if="item.status === 'submitted'" class="act act-primary" @tap="review(item, 'published')">发布</text>
						<text v-if="item.status === 'submitted'" class="act act-warn" @tap="review(item, 'rejected')">退回</text>
						<text v-if="item.status === 'published'" class="act act-warn" @tap="review(item, 'hidden')">隐藏</text>
						<text v-if="item.status === 'hidden' || item.status === 'rejected'" class="act act-primary" @tap="review(item, 'published')">恢复发布</text>
						<text v-if="item.status !== 'deleted'" class="act act-danger" @tap="remove(item)">删除</text>
					</view>
				</view>
			</view>

			<view v-if="items.length" class="load-more" @tap="loadMore">{{ moreText }}</view>
		</view>

		<view v-if="previewItem" class="preview-mask" @tap="previewItem = null">
			<view class="preview-sheet" @tap.stop>
				<view class="preview-head">
					<view class="preview-head-copy">
						<text class="preview-title">{{ previewItem.title }}</text>
						<text class="preview-meta">{{ previewItem.authorName || '未知用户' }} · {{ formatDate(previewItem.created) }}</text>
					</view>
					<text class="cuIcon-close preview-close" @tap="previewItem = null"></text>
				</view>
				<swiper v-if="(previewItem.imageUrls || []).length" class="preview-swiper" :style="{ height: previewHeight + 'px' }" :current="previewIndex" @change="onPreviewChange">
					<swiper-item v-for="(url, index) in previewItem.imageUrls || []" :key="url">
						<view class="preview-slide"><image :src="url" mode="aspectFit" @tap="showImage(url)" /></view>
					</swiper-item>
				</swiper>
				<view v-else class="preview-swiper preview-swiper-empty" :style="{ height: '120px' }"><text>这组图集没有可预览的图片</text></view>
				<view v-if="(previewItem.imageUrls || []).length" class="preview-foot">
					<text class="preview-counter">{{ previewIndex + 1 }} / {{ (previewItem.imageUrls || []).length }}</text>
					<text class="preview-tip">点击图片可全屏查看</text>
				</view>
				<scroll-view scroll-y class="preview-copy-scroll">
					<text class="preview-description">{{ previewItem.description || '没有描述' }}</text>
					<view v-if="previewItem.tags && previewItem.tags.length" class="preview-tags"><text v-for="tagName in previewItem.tags" :key="tagName">#{{ tagName }}</text></view>
					<text v-if="previewItem.reviewReason" class="preview-reason">审核意见：{{ previewItem.reviewReason }}</text>
				</scroll-view>
			</view>
		</view>

		<view v-if="reasonEditor" class="reason-mask" @tap="reasonEditor = false">
			<view class="reason-sheet" @tap.stop>
				<text class="reason-title">退回图集</text>
				<textarea v-model="reason" maxlength="500" placeholder="填写退回理由，作者会看到这条意见" />
				<view class="reason-actions">
					<text @tap="reasonEditor = false">取消</text>
					<text class="confirm" @tap="confirmReject">确认退回</text>
				</view>
			</view>
		</view>
	</view>
</template>

<script>
import { localStorage } from '../../js_sdk/mp-storage/mp-storage/index.js'

export default {
	data() {
		return {
			StatusBar: this.StatusBar,
			token: '',
			group: '',
			night: false,
			status: 'submitted',
			tag: '',
			items: [],
			loading: false,
			page: 1,
			total: 0,
			moreText: '加载更多',
			previewItem: null,
			previewIndex: 0,
			previewHeight: 300,
			reasonEditor: false,
			reason: '',
			rejecting: null,
			statuses: [
				{ label: '待审核', value: 'submitted' },
				{ label: '已发布', value: 'published' },
				{ label: '已退回', value: 'rejected' },
				{ label: '已隐藏', value: 'hidden' },
				{ label: '已删除', value: 'deleted' },
				{ label: '全部', value: '' }
			]
		}
	},
	onLoad() {
		this.token = localStorage.getItem('token') || ''
		try {
			const info = JSON.parse(localStorage.getItem('userinfo') || '{}')
			this.group = info.group || ''
			this.night = info.isNight === 1
		} catch (e) {}
		if (this.group !== 'administrator' && this.group !== 'editor') {
			uni.showToast({ title: '没有管理权限', icon: 'none' })
			return
		}
		this.reload()
	},
	onShow() {
		// #ifdef APP-PLUS
		try { plus.navigator.setStatusBarStyle(this.night ? 'light' : 'dark') } catch (e) {}
		// #endif
	},
	methods: {
		back() { uni.navigateBack({ delta: 1 }) },
		reload() { this.page = 1; this.loadList(false) },
		setStatus(value) { if (this.status === value) return; this.status = value; this.reload() },
		clearTag() { this.tag = ''; this.reload() },
		loadMore() { if (this.loading || this.items.length >= this.total) return; this.loadList(true) },
		loadList(append) {
			if (this.loading) return
			this.loading = true
			const page = append ? this.page + 1 : 1
			this.$Net.request({
				url: this.$API.galleryManage(),
				data: { token: this.token, status: this.status, tag: this.tag.trim(), page, limit: 20 },
				method: 'get',
				dataType: 'json',
				success: res => {
					if (res.data && res.data.code === 1) {
						const rows = Array.isArray(res.data.data) ? res.data.data : []
						this.items = append ? this.items.concat(rows) : rows
						this.page = page
						this.total = Number(res.data.total || 0)
						this.moreText = this.items.length < this.total ? '加载更多' : '已经到底了'
					} else {
						uni.showToast({ title: res.data && res.data.msg || '列表加载失败', icon: 'none' })
					}
				},
				fail: () => uni.showToast({ title: '图集列表加载失败', icon: 'none' }),
				complete: () => { this.loading = false }
			})
		},
		statusLabel(value) {
			return { submitted: '待审核', published: '已发布', rejected: '已退回', hidden: '已隐藏', deleted: '已删除' }[value] || '未知'
		},
		formatDate(value) {
			const date = new Date(Number(value) > 10000000000 ? Number(value) : Number(value) * 1000)
			return isNaN(date.getTime()) ? '' : date.getFullYear() + '.' + String(date.getMonth() + 1).padStart(2, '0') + '.' + String(date.getDate()).padStart(2, '0')
		},
		preview(item) {
			this.previewItem = item
			this.previewIndex = 0
			this.previewHeight = Math.max(260, Math.min(460, Math.round((uni.getSystemInfoSync().windowWidth || 375) * 0.9)))
		},
		onPreviewChange(event) { this.previewIndex = Number(event.detail && event.detail.current || 0) },
		showImage(url) { uni.previewImage({ urls: this.previewItem.imageUrls || [], current: url }) },
		review(item, next) {
			if (next === 'rejected') {
				this.rejecting = item
				this.reason = ''
				this.reasonEditor = true
				return
			}
			const title = next === 'published' ? (item.status === 'hidden' || item.status === 'rejected' ? '恢复发布这组图？' : '发布这组图？') : '隐藏这组图？'
			uni.showModal({
				title,
				content: next === 'published' ? '公开后普通用户可以在图库中看到它。' : '隐藏后普通用户将无法看到它。',
				success: res => { if (res.confirm) this.submitReview(item, next, '') }
			})
		},
		confirmReject() {
			if (!this.rejecting) return
			const item = this.rejecting
			this.reasonEditor = false
			this.rejecting = null
			this.submitReview(item, 'rejected', this.reason)
		},
		submitReview(item, next, reason) {
			uni.showLoading({ title: '处理中' })
			this.$Net.request({
				url: this.$API.galleryReview(),
				data: { token: this.token, id: item.id, status: next, reason: reason || '', _ts: Date.now() },
				method: 'post',
				dataType: 'json',
				success: res => {
					uni.hideLoading()
					uni.showToast({ title: res.data && res.data.msg || '已更新', icon: 'none' })
					if (res.data && res.data.code === 1) this.reload()
				},
				fail: () => {
					uni.hideLoading()
					uni.showToast({ title: '网络不太好，请稍后重试', icon: 'none' })
				}
			})
		},
		remove(item) {
			uni.showModal({
				title: '删除这组图？',
				content: '删除后会从公开图库移除，并保留审核审计记录。',
				confirmText: '删除',
				confirmColor: '#a04f45',
				success: res => {
					if (!res.confirm) return
					uni.showLoading({ title: '删除中' })
					this.$Net.request({
						url: this.$API.galleryDelete(),
						data: { token: this.token, id: item.id, reason: '管理员删除', _ts: Date.now() },
						method: 'post',
						dataType: 'json',
						success: response => {
							uni.hideLoading()
							uni.showToast({ title: response.data && response.data.msg || '已删除', icon: 'none' })
							if (response.data && response.data.code === 1) this.reload()
						},
						fail: () => {
							uni.hideLoading()
							uni.showToast({ title: '删除失败', icon: 'none' })
						}
					})
				}
			})
		}
	}
}
</script>

<style scoped>
.gallery-manage {
	--m-bg: #f4f6f5;
	--m-surface: #ffffff;
	--m-ink: #263630;
	--m-title: #1f332b;
	--m-muted: #7c8d84;
	--m-faint: #9aa8a1;
	--m-line: #e3eae6;
	--m-primary: #2e6657;
	--m-primary-soft: #e3efe9;
	--m-warn: #a4664a;
	--m-warn-soft: #f6ece5;
	--m-danger: #a04f45;
	--m-chip: #eaf0ec;
	--m-serif: Georgia, "Times New Roman", "Songti SC", STSong, serif;
	min-height: 100vh;
	background: var(--m-bg);
	color: var(--m-ink);
}
.gallery-manage.night {
	--m-bg: #161b19;
	--m-surface: #1e2523;
	--m-ink: #e6eee8;
	--m-title: #e9f2ed;
	--m-muted: #9eafa5;
	--m-faint: #74847c;
	--m-line: #39463f;
	--m-primary: #6ebaae;
	--m-primary-soft: #22332e;
	--m-warn: #d0a17c;
	--m-warn-soft: #322a22;
	--m-danger: #d08374;
	--m-chip: #29322f;
}

/* nav */
.manage-nav { position: fixed; left: 0; right: 0; top: 0; z-index: 5; background: rgba(255, 255, 255, 0.96); border-bottom: 1px solid var(--m-line); -webkit-backdrop-filter: blur(12px); backdrop-filter: blur(12px); }
.night .manage-nav { background: rgba(22, 27, 25, 0.96); }
.manage-nav-inner { position: relative; display: flex; align-items: center; height: 58px; padding: 0 22px; }
.manage-back { width: 40px; height: 40px; display: flex; align-items: center; justify-content: flex-start; font-size: 20px; color: var(--m-primary); }
.manage-nav-brand { position: absolute; left: 50%; transform: translateX(-50%); display: flex; flex-direction: column; align-items: center; line-height: 1; }
.manage-nav-title { font-family: var(--m-serif); font-size: 17px; font-weight: 700; color: var(--m-title); letter-spacing: 1px; }
.manage-nav-eyebrow { margin-top: 4px; font-size: 8px; letter-spacing: 2.5px; color: var(--m-primary); }
.manage-nav-note { margin-left: auto; color: var(--m-faint); font-size: 10px; letter-spacing: 2px; }

/* content */
.manage-content { max-width: 980px; margin: auto; padding: 0 22px 70px; box-sizing: border-box; }
.intro { display: flex; justify-content: space-between; align-items: flex-end; padding: 34px 0 28px; border-bottom: 1px solid var(--m-line); }
.eyebrow { display: block; color: var(--m-warn); font-size: 10px; letter-spacing: 2.5px; }
.intro-title { display: block; margin-top: 12px; font-family: var(--m-serif); font-size: 30px; font-weight: 700; color: var(--m-title); letter-spacing: 1px; }
.intro-desc { display: block; margin-top: 10px; color: var(--m-muted); font-size: 12px; }
.intro-mark { display: flex; align-items: center; justify-content: center; width: 58px; height: 58px; flex: none; border-radius: 18px; background: var(--m-primary); color: #fff; font-size: 27px; }

/* filters */
.filters { margin-top: 18px; white-space: nowrap; }
.filters-track { display: flex; gap: 8px; }
.filter-chip { height: 32px; line-height: 32px; padding: 0 15px; border-radius: 16px; background: var(--m-chip); color: var(--m-muted); font-size: 12px; }
.filter-chip.active { background: var(--m-primary); color: #fff; font-weight: 650; }
.night .filter-chip.active { color: #101614; }

/* search */
.tag-filter { display: flex; align-items: center; gap: 8px; height: 38px; margin: 12px 0 4px; padding: 0 12px; border-radius: 19px; background: var(--m-chip); color: var(--m-faint); box-sizing: border-box; }
.tag-filter input { min-width: 0; flex: 1; font-size: 12px; color: var(--m-ink); }
.tag-clear { font-size: 14px; }

/* rows */
.gallery-row { display: flex; align-items: flex-start; gap: 14px; padding: 16px 0; border-bottom: 1px solid var(--m-line); }
.gallery-images { position: relative; width: 86px; height: 86px; flex: none; overflow: hidden; border-radius: 10px; background: var(--m-chip); }
.gallery-images image { width: 100%; height: 100%; }
.image-fallback { display: flex; align-items: center; justify-content: center; width: 100%; height: 100%; color: var(--m-faint); font-size: 27px; }
.image-count { position: absolute; right: 5px; bottom: 5px; padding: 3px 7px; border-radius: 9px; background: rgba(16, 24, 20, 0.78); color: #fff; font-size: 10px; line-height: 1.3; }
.gallery-main { min-width: 0; flex: 1; }
.gallery-title-line { display: flex; align-items: center; gap: 8px; }
.gallery-title { overflow: hidden; font-family: var(--m-serif); color: var(--m-title); font-size: 16px; font-weight: 700; text-overflow: ellipsis; white-space: nowrap; }
.status { padding: 3px 8px; border-radius: 10px; flex: none; font-size: 10px; line-height: 1.4; }
.status-submitted { background: var(--m-warn-soft); color: var(--m-warn); }
.status-published { background: var(--m-primary-soft); color: var(--m-primary); }
.status-rejected, .status-hidden, .status-deleted { background: var(--m-chip); color: var(--m-faint); }
.gallery-description { display: -webkit-box; overflow: hidden; margin-top: 7px; color: var(--m-muted); font-size: 12px; line-height: 1.45; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.gallery-meta { display: block; margin-top: 7px; color: var(--m-faint); font-size: 10px; }
.gallery-tags { display: flex; gap: 7px; overflow: hidden; margin-top: 7px; color: var(--m-primary); font-size: 10px; white-space: nowrap; }
.gallery-reason { display: block; margin-top: 6px; color: var(--m-warn); font-size: 10px; }
.gallery-actions { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 10px; }
.act { height: 26px; line-height: 26px; padding: 0 11px; border-radius: 13px; background: var(--m-chip); color: var(--m-muted); font-size: 11px; }
.act:active { opacity: 0.7; }
.act-primary { background: var(--m-primary-soft); color: var(--m-primary); font-weight: 600; }
.act-warn { background: var(--m-warn-soft); color: var(--m-warn); }
.act-danger { background: transparent; border: 1px solid var(--m-line); color: var(--m-danger); }

/* empty & more */
.empty { text-align: center; padding: 80px 20px; }
.empty-mark { display: flex; align-items: center; justify-content: center; width: 68px; height: 68px; margin: 0 auto; border-radius: 50%; background: var(--m-chip); color: var(--m-faint); font-size: 30px; }
.empty-title { display: block; margin-top: 18px; font-family: var(--m-serif); font-size: 16px; font-weight: 700; color: var(--m-title); }
.empty-sub { display: block; margin-top: 8px; color: var(--m-faint); font-size: 12px; }
.load-more { display: table; margin: 20px auto 0; height: 34px; line-height: 34px; padding: 0 22px; border: 1px solid var(--m-line); border-radius: 17px; color: var(--m-primary); font-size: 12px; }
.load-more:active { background: var(--m-chip); }

/* preview sheet */
.preview-mask, .reason-mask { position: fixed; inset: 0; z-index: 20; background: rgba(10, 16, 13, 0.55); display: flex; align-items: flex-end; }
.preview-sheet { width: 100%; max-height: 92vh; overflow: hidden; border-radius: 22px 22px 0 0; background: var(--m-surface); display: flex; flex-direction: column; }
.preview-head { display: flex; align-items: flex-start; justify-content: space-between; padding: 20px 22px 14px; }
.preview-head-copy { min-width: 0; }
.preview-title { display: block; font-family: var(--m-serif); color: var(--m-title); font-size: 19px; font-weight: 700; }
.preview-meta { display: block; margin-top: 5px; color: var(--m-faint); font-size: 11px; }
.preview-close { flex: none; display: flex; align-items: center; justify-content: center; width: 30px; height: 30px; border-radius: 50%; background: var(--m-chip); color: var(--m-muted); font-size: 14px; }
.preview-swiper { width: 100%; background: #0b0f0d; }
.preview-swiper-empty { display: flex; align-items: center; justify-content: center; color: var(--m-faint); font-size: 12px; }
.preview-slide { display: flex; align-items: center; justify-content: center; width: 100%; height: 100%; }
.preview-slide image { width: 100%; height: 100%; }
.preview-foot { display: flex; align-items: center; justify-content: space-between; padding: 10px 22px 2px; }
.preview-counter { font-size: 11px; color: var(--m-muted); }
.preview-tip { font-size: 10px; color: var(--m-faint); }
.preview-copy-scroll { max-height: 26vh; padding: 0 22px 26px; box-sizing: border-box; }
.preview-description { display: block; padding: 12px 0 0; color: var(--m-muted); font-size: 12px; line-height: 1.6; white-space: pre-wrap; }
.preview-tags { display: flex; flex-wrap: wrap; gap: 7px; margin-top: 10px; color: var(--m-primary); font-size: 11px; }
.preview-reason { display: block; margin-top: 10px; color: var(--m-warn); font-size: 11px; }

/* reason sheet */
.reason-sheet { width: 100%; padding: 24px 22px 30px; border-radius: 22px 22px 0 0; background: var(--m-surface); box-sizing: border-box; }
.reason-title { font-family: var(--m-serif); color: var(--m-title); font-size: 19px; font-weight: 700; }
.reason-sheet textarea { width: 100%; height: 110px; margin-top: 18px; padding: 11px; border: 1px solid var(--m-line); border-radius: 8px; background: var(--m-bg); color: var(--m-ink); font-size: 13px; box-sizing: border-box; }
.reason-actions { display: flex; justify-content: flex-end; gap: 18px; margin-top: 16px; color: var(--m-muted); font-size: 13px; }
.reason-actions .confirm { color: var(--m-primary); font-weight: 650; }

@media (max-width: 560px) {
	.manage-content { padding-left: 16px; padding-right: 16px; }
	.intro-title { font-size: 25px; }
	.intro-mark { width: 50px; height: 50px; }
	.gallery-row { gap: 10px; }
	.gallery-images { width: 72px; height: 72px; }
	.gallery-title { font-size: 14px; }
	.manage-nav-inner { padding: 0 16px; }
}
</style>
