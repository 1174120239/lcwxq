<template>
	<view class="detail-page" :class="{ night: campusNight }">
		<view v-if="item" class="stage" :class="{ crossing: crossFade }"
			@touchstart="stageTouchStart" @touchmove="stageTouchMove" @touchend="stageTouchEnd">
			<swiper class="stage-swiper" :style="{ height: screenHeight + 'px' }" :current="currentIndex" :duration="300"
				@change="changeSlide">
				<swiper-item v-for="(url, index) in item.imageUrls" :key="url">
					<view class="stage-slide">
						<image :class="{ loaded: slideLoaded[index] }" :src="url" mode="aspectFit" @tap="stageTap" @load="onSlideLoad(index)" @error="onSlideLoad(index)" />
					</view>
				</swiper-item>
			</swiper>

			<view class="stage-top" :class="{ hidden: !chromeVisible }">
				<view class="stage-pill stage-back" @tap="back"><text class="cuIcon-back"></text></view>
				<text v-if="ctxIndex >= 0" class="stage-pos">{{ ctxIndex + 1 }}/{{ ctxTotal || ctxItems.length }}</text>
				<view class="stage-pill stage-counter">{{ currentIndex + 1 }} / {{ item.imageUrls.length }}</view>
			</view>

			<view class="stage-bottom" :class="{ hidden: !chromeVisible }">
				<scroll-view v-if="item.imageUrls.length > 1" class="stage-thumbs" scroll-x :scroll-into-view="thumbAnchor" :show-scrollbar="false">
					<view class="stage-thumbs-track">
						<view v-for="(url, index) in item.imageUrls" :id="'gallery-thumb-' + index" :key="url" class="stage-thumb" :class="{ active: currentIndex === index }" @tap.stop="selectSlide(index)">
							<image :src="url" mode="aspectFill" />
						</view>
					</view>
				</scroll-view>
				<view class="stage-bar">
					<image v-if="item.authorAvatar" class="stage-bar-avatar" :src="item.authorAvatar" mode="aspectFill" />
					<view v-else class="stage-bar-avatar stage-bar-avatar-ph"><text class="cuIcon-picfill"></text></view>
					<text class="stage-bar-title">{{ item.title }}</text>
					<text class="stage-bar-views">{{ item.views || 0 }} 浏览</text>
				</view>
			</view>
		</view>

		<view v-if="item" class="sheet" :class="{ open: sheetOpen }"
			@touchstart="sheetTouchStart" @touchmove="sheetTouchMove" @touchend="sheetTouchEnd">
			<view class="sheet-handle"></view>
			<scroll-view scroll-y class="sheet-scroll" @scroll="sheetScrollTop = $event.detail.scrollTop">
				<view class="sheet-author">
					<image v-if="item.authorAvatar" class="sheet-avatar" :src="item.authorAvatar" mode="aspectFill" />
					<view v-else class="sheet-avatar sheet-avatar-ph"><text class="cuIcon-picfill"></text></view>
					<view class="sheet-author-copy">
						<text class="sheet-author-name">{{ item.authorName || '校园用户' }}</text>
						<text class="sheet-author-meta">{{ date(item.created) }} · {{ item.views || 0 }} 次浏览</text>
					</view>
				</view>
				<text class="sheet-title">{{ item.title }}</text>
				<text v-if="item.description" class="sheet-desc">{{ item.description }}</text>
				<view v-if="item.tags.length" class="sheet-tags">
					<view v-for="tag in item.tags" :key="tag" class="sheet-tag" @tap="backToTag(tag)"># {{ tag }}</view>
				</view>
				<view class="sheet-foot">
					<text class="sheet-foot-note">共 {{ item.imageUrls.length }} 张 · 双击图片全屏查看</text>
					<view class="sheet-fullscreen" @tap="preview(0)"><text class="cuIcon-unfold"></text><text>全屏查看</text></view>
				</view>
			</scroll-view>
		</view>

		<view v-else class="detail-state">
			<view class="state-mark"><text class="cuIcon-picfill"></text></view>
			<text class="state-title">{{ loading ? '正在加载图集' : '图集不存在或尚未公开' }}</text>
			<text v-if="loading" class="state-sub">照片正在路上</text>
			<view v-else class="state-back" @tap="back"><text class="cuIcon-back"></text><text>返回图库</text></view>
		</view>
	</view>
</template>

<script>
import { isDongchangfuNight, resolveCampusNight, getCampusThemeMode } from '@/utils/campusTheme.js'
import { galleryContextGet, galleryContextAppend, galleryContextIndexOf } from '@/utils/galleryContext.js'
export default {
	data() {
		return {
			id: 0,
			item: null,
			loading: true,
			currentIndex: 0,
			screenWidth: 375,
			screenHeight: 667,
			thumbAnchor: '',
			slideLoaded: [],
			chromeVisible: true,
			chromeTimer: null,
			sheetOpen: false,
			lastTapTime: 0,
			tsX: 0,
			tsY: 0,
			tsMoved: false,
			tsZone: 'center',
			ssY: 0,
			ssMoved: false,
			sheetScrollTop: 0,
			crossFade: false,
			ctxItems: [],
			ctxIndex: -1,
			ctxTag: '',
			ctxTotal: 0,
			ctxPage: 1,
			fetchingMore: false,
			campusNight: false
		}
	},
	onLoad(q) {
		const info = uni.getSystemInfoSync()
		this.screenWidth = info.windowWidth || 375
		this.screenHeight = info.windowHeight || 667
		this.id = Number(q && q.id || 0)
		if (q && q.tag) this.ctxTag = q.tag
		this.load()
	},
	onShow() {
		this.campusNight = resolveCampusNight(getCampusThemeMode(), isDongchangfuNight(Date.now()))
		// #ifdef APP-PLUS
		try {
			plus.navigator.setStatusBarStyle('light')
			uni.setBackgroundColor({ backgroundColor: '#050807', backgroundColorTop: '#050807', backgroundColorBottom: '#050807', fail() {} })
		} catch (e) {}
		// #endif
	},
	onUnload() {
		if (this.chromeTimer) clearTimeout(this.chromeTimer)
	},
	methods: {
		back() { uni.navigateBack({ delta: 1 }) },
		normalize(item) {
			item.imageUrls = Array.isArray(item.imageUrls) ? item.imageUrls : []
			item.tags = Array.isArray(item.tags) ? item.tags : []
			return item
		},
		load() {
			this.$Net.request({
				url: this.$API.galleryInfo(),
				data: { id: this.id },
				method: 'get',
				dataType: 'json',
				success: r => {
					this.loading = false
					if (r.data && r.data.code === 1) {
						this.setItem(r.data.data, 0)
						this.resolveContext()
					}
				},
				fail: () => { this.loading = false }
			})
		},
		setItem(raw, index) {
			this.item = this.normalize(raw)
			this.currentIndex = index
			this.slideLoaded = this.item.imageUrls.map(() => false)
			this.updateThumbAnchor()
			this.resetChrome()
		},
		resolveContext() {
			const ctx = galleryContextGet()
			let idx = galleryContextIndexOf(this.id)
			if (idx < 0 && !ctx.items.length) {
				this.$Net.request({
					url: this.$API.galleryList(),
					data: { page: 1, limit: 20, tag: this.ctxTag, _ts: Date.now() },
					method: 'get',
					dataType: 'json',
					success: r => {
						if (r.data && r.data.code === 1 && Array.isArray(r.data.data)) {
							r.data.data.forEach(x => this.normalize(x))
							galleryContextAppend(r.data.data, r.data.total, 1)
							this.syncContext()
						}
					}
				})
			} else {
				this.syncContext()
			}
		},
		syncContext() {
			const ctx = galleryContextGet()
			this.ctxItems = ctx.items
			this.ctxIndex = galleryContextIndexOf(this.id)
			this.ctxTag = ctx.tag
			this.ctxTotal = ctx.total
			this.ctxPage = ctx.page
		},
		changeSlide(event) {
			this.currentIndex = Number(event.detail && event.detail.current || 0)
			this.updateThumbAnchor()
			this.scheduleChromeHide()
		},
		selectSlide(index) {
			this.currentIndex = index
			this.resetChrome()
		},
		updateThumbAnchor() {
			this.thumbAnchor = this.item && this.item.imageUrls.length ? 'gallery-thumb-' + this.currentIndex : ''
		},
		onSlideLoad(index) {
			if (!this.slideLoaded[index]) this.$set(this.slideLoaded, index, true)
		},
		resetChrome() {
			this.chromeVisible = true
			this.scheduleChromeHide()
		},
		scheduleChromeHide() {
			if (this.chromeTimer) clearTimeout(this.chromeTimer)
			this.chromeTimer = setTimeout(() => {
				if (!this.sheetOpen && !this.crossFade) this.chromeVisible = false
			}, 3200)
		},
		stageTap() {
			const now = Date.now()
			if (now - this.lastTapTime < 320) {
				this.preview(this.currentIndex)
				this.lastTapTime = 0
				return
			}
			this.lastTapTime = now
			if (this.sheetOpen) { this.closeSheet(); return }
			this.chromeVisible = !this.chromeVisible
			if (this.chromeVisible) this.scheduleChromeHide()
		},
		preview(index) {
			if (!this.item || !this.item.imageUrls.length) return
			uni.previewImage({ urls: this.item.imageUrls, current: this.item.imageUrls[index] })
		},
		date(value) {
			const d = new Date(Number(value) > 10000000000 ? Number(value) : Number(value) * 1000)
			return isNaN(d.getTime()) ? '' : d.getFullYear() + '.' + String(d.getMonth() + 1).padStart(2, '0') + '.' + String(d.getDate()).padStart(2, '0')
		},
		backToTag(tag) { uni.redirectTo({ url: '/pages/gallery/index?tag=' + encodeURIComponent(tag) }) },
		/* ----- stage gestures: chrome / sheet / cross-album ----- */
		stageTouchStart(e) {
			const t = e.touches && e.touches[0]
			if (!t) return
			this.tsX = t.clientX
			this.tsY = t.clientY
			this.tsMoved = false
			this.tsZone = t.clientY < 90 ? 'top' : t.clientY > this.screenHeight - 150 ? 'bottom' : 'center'
		},
		stageTouchMove(e) {
			const t = e.touches && e.touches[0]
			if (!t) return
			const dx = t.clientX - this.tsX
			const dy = t.clientY - this.tsY
			if (!this.tsMoved && (Math.abs(dx) > 12 || Math.abs(dy) > 12)) this.tsMoved = true
		},
		stageTouchEnd(e) {
			if (!this.tsMoved || this.tsZone !== 'center') return
			const t = e.changedTouches && e.changedTouches[0]
			if (!t) return
			const dx = t.clientX - this.tsX
			const dy = t.clientY - this.tsY
			if (this.sheetOpen) {
				if (dy > 40 && Math.abs(dy) > Math.abs(dx)) this.closeSheet()
				return
			}
			if (dy < -40 && Math.abs(dy) > Math.abs(dx)) { this.openSheet(); return }
			const n = this.item ? this.item.imageUrls.length : 0
			if (dx < -60 && Math.abs(dx) > Math.abs(dy) && this.currentIndex === n - 1) { this.crossAlbum('next'); return }
			if (dx > 60 && Math.abs(dx) > Math.abs(dy) && this.currentIndex === 0) { this.crossAlbum('prev'); return }
		},
		openSheet() {
			this.sheetOpen = true
			this.chromeVisible = false
		},
		closeSheet() { this.sheetOpen = false },
		sheetTouchStart(e) {
			const t = e.touches && e.touches[0]
			if (!t) return
			this.ssY = t.clientY
			this.ssMoved = false
		},
		sheetTouchMove(e) {
			const t = e.touches && e.touches[0]
			if (!t) return
			if (!this.ssMoved && Math.abs(t.clientY - this.ssY) > 10) this.ssMoved = true
		},
		sheetTouchEnd(e) {
			if (!this.ssMoved) return
			if (this.sheetScrollTop > 4) return
			const t = e.changedTouches && e.changedTouches[0]
			if (!t) return
			if (t.clientY - this.ssY > 36) this.closeSheet()
		},
		/* ----- cross-album ----- */
		crossAlbum(dir) {
			if (this.ctxIndex < 0) return
			if (dir === 'next') {
				if (this.ctxIndex + 1 >= this.ctxItems.length) {
					if (this.ctxItems.length >= this.ctxTotal || this.fetchingMore) return
					this.fetchingMore = true
					this.$Net.request({
						url: this.$API.galleryList(),
						data: { page: this.ctxPage + 1, limit: 20, tag: this.ctxTag, _ts: Date.now() },
						method: 'get',
						dataType: 'json',
						success: r => {
							this.fetchingMore = false
							if (r.data && r.data.code === 1 && Array.isArray(r.data.data)) {
								r.data.data.forEach(x => this.normalize(x))
								galleryContextAppend(r.data.data, r.data.total, this.ctxPage + 1)
								this.syncContext()
								this.switchAlbum(this.ctxIndex + 1, dir)
							}
						},
						fail: () => { this.fetchingMore = false }
					})
					return
				}
				this.switchAlbum(this.ctxIndex + 1, dir)
			} else {
				if (this.ctxIndex <= 0) return
				this.switchAlbum(this.ctxIndex - 1, dir)
			}
		},
		switchAlbum(index, dir) {
			const next = this.ctxItems[index]
			if (!next) return
			this.crossFade = true
			try { uni.vibrateShort({ type: 'light' }) } catch (e) {}
			setTimeout(() => {
				this.ctxIndex = index
				const photoIndex = dir === 'prev' ? next.imageUrls.length - 1 : 0
				this.setItem(next, photoIndex)
				setTimeout(() => { this.crossFade = false }, 60)
			}, 190)
		}
	}
}
</script>

<style scoped>
.detail-page {
	--d-bg: #0a0e0c;
	--d-surface: #fffdf8;
	--d-ink: #24322c;
	--d-title: #1d2a24;
	--d-muted: #7d8c84;
	--d-faint: #a3aea8;
	--d-line: #eae4da;
	--d-primary: #237c74;
	--d-primary-soft: #e9f1ee;
	--d-accent: #b07952;
	--d-serif: Georgia, "Times New Roman", "Songti SC", STSong, serif;
	min-height: 100vh;
	background: var(--d-bg);
	color: var(--d-ink);
	overflow: hidden;
}
.detail-page.night {
	--d-surface: #171f1c;
	--d-ink: #e8efeb;
	--d-title: #f2f7f4;
	--d-muted: #97a69e;
	--d-faint: #6e7e76;
	--d-line: rgba(233, 241, 236, 0.1);
	--d-primary: #6ebaae;
	--d-primary-soft: #22332e;
	--d-accent: #d0a17c;
}

/* stage */
.stage { position: fixed; inset: 0; z-index: 1; background: #050807; animation: stage-in 0.5s cubic-bezier(0.22, 0.9, 0.3, 1) both; transition: opacity 0.22s ease, transform 0.22s ease; }
.stage.crossing { opacity: 0; transform: scale(1.03); }
@keyframes stage-in { from { opacity: 0; transform: scale(1.08); } to { opacity: 1; transform: scale(1); } }
.stage-swiper { width: 100%; background: #050807; }
.stage-slide { display: flex; align-items: center; justify-content: center; width: 100%; height: 100%; }
.stage-slide image { display: block; width: 100%; height: 100%; opacity: 0; transition: opacity 0.4s ease; }
.stage-slide image.loaded { opacity: 1; }

/* top chrome */
.stage-top { position: absolute; left: 0; right: 0; top: 0; z-index: 3; display: flex; align-items: center; justify-content: space-between; padding: calc(env(safe-area-inset-top) + 10px) 14px 0; transition: opacity 0.3s ease, transform 0.3s ease; }
.stage-top.hidden { opacity: 0; transform: translateY(-8px); pointer-events: none; }
.stage-pill { display: flex; align-items: center; justify-content: center; height: 32px; min-width: 32px; padding: 0 11px; border-radius: 16px; background: rgba(10, 14, 12, 0.55); color: #fff; font-size: 11px; }
.stage-back { width: 32px; padding: 0; font-size: 16px; }
.stage-pos { color: rgba(255, 255, 255, 0.55); font-size: 11px; letter-spacing: 1px; }
.stage-counter { letter-spacing: 1px; }

/* bottom chrome */
.stage-bottom { position: absolute; left: 0; right: 0; bottom: 0; z-index: 3; padding-bottom: calc(env(safe-area-inset-bottom) + 6px); background: linear-gradient(180deg, rgba(5, 8, 7, 0) 0%, rgba(5, 8, 7, 0.82) 100%); transition: opacity 0.3s ease, transform 0.3s ease; }
.stage-bottom.hidden { opacity: 0; transform: translateY(8px); pointer-events: none; }
.stage-thumbs { padding: 30px 0 10px; white-space: nowrap; }
.stage-thumbs-track { display: flex; gap: 8px; padding: 0 12px; }
.stage-thumb { width: 46px; height: 42px; flex: none; overflow: hidden; border: 2px solid transparent; border-radius: 6px; opacity: 0.55; box-sizing: border-box; transition: opacity 0.2s ease; }
.stage-thumb.active { border-color: #9fd8c2; opacity: 1; }
.stage-thumb image { display: block; width: 100%; height: 100%; }
.stage-bar { display: flex; align-items: center; gap: 8px; padding: 2px 14px 4px; }
.stage-bar-avatar { width: 26px; height: 26px; border-radius: 50%; background: #22332e; flex: none; }
.stage-bar-avatar-ph { display: flex; align-items: center; justify-content: center; color: #6e7e76; font-size: 13px; }
.stage-bar-title { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-family: var(--d-serif); font-size: 14px; font-weight: 700; color: #fff; }
.stage-bar-views { flex: none; color: rgba(255, 255, 255, 0.55); font-size: 10px; }

/* sheet */
.sheet { position: fixed; left: 0; right: 0; bottom: 0; z-index: 10; max-height: 72vh; border-radius: 22px 22px 0 0; background: var(--d-surface); transform: translateY(105%); transition: transform 0.38s cubic-bezier(0.22, 0.9, 0.3, 1); display: flex; flex-direction: column; }
.sheet.open { transform: translateY(0); }
.sheet-handle { width: 36px; height: 4px; margin: 10px auto 6px; border-radius: 2px; background: var(--d-line); flex: none; }
.night .sheet { border-top: 1px solid rgba(255, 255, 255, 0.08); }
.sheet-scroll { max-height: calc(72vh - 20px); padding: 0 22px calc(env(safe-area-inset-bottom) + 26px); box-sizing: border-box; }
.sheet-author { display: flex; align-items: center; gap: 10px; }
.sheet-avatar { width: 42px; height: 42px; border-radius: 50%; background: var(--d-primary-soft); flex: none; }
.sheet-avatar-ph { display: flex; align-items: center; justify-content: center; color: var(--d-faint); font-size: 20px; }
.sheet-author-copy { display: flex; flex-direction: column; gap: 3px; min-width: 0; }
.sheet-author-name { font-size: 13px; font-weight: 600; color: var(--d-ink); }
.sheet-author-meta { font-size: 11px; color: var(--d-faint); }
.sheet-title { display: block; margin-top: 18px; font-family: var(--d-serif); font-size: 23px; line-height: 1.4; font-weight: 700; color: var(--d-title); letter-spacing: 0.5px; }
.sheet-desc { display: block; margin-top: 10px; font-size: 14px; line-height: 1.8; color: var(--d-muted); white-space: pre-wrap; }
.sheet-tags { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 16px; }
.sheet-tag { height: 26px; line-height: 26px; padding: 0 12px; border-radius: 13px; background: var(--d-primary-soft); color: var(--d-primary); font-size: 12px; }
.sheet-tag:active { opacity: 0.7; }
.sheet-foot { display: flex; align-items: center; justify-content: space-between; margin-top: 22px; padding-top: 16px; border-top: 1px solid var(--d-line); }
.sheet-foot-note { font-size: 11px; color: var(--d-faint); }
.sheet-fullscreen { display: flex; align-items: center; gap: 5px; color: var(--d-primary); font-size: 12px; font-weight: 600; }
.sheet-fullscreen .cuIcon-unfold { font-size: 13px; }
.sheet-fullscreen:active { opacity: 0.7; }

/* states */
.detail-state { min-height: 100vh; display: flex; flex-direction: column; align-items: center; justify-content: center; background: var(--d-bg); color: rgba(255, 255, 255, 0.55); text-align: center; }
.state-mark { display: flex; align-items: center; justify-content: center; width: 72px; height: 72px; border-radius: 50%; background: rgba(255, 255, 255, 0.06); color: rgba(255, 255, 255, 0.4); font-size: 32px; }
.state-title { margin-top: 20px; font-size: 15px; }
.state-sub { margin-top: 8px; font-size: 12px; color: rgba(255, 255, 255, 0.35); }
.state-back { display: flex; align-items: center; gap: 6px; margin-top: 22px; height: 34px; padding: 0 18px; border: 1px solid rgba(255, 255, 255, 0.25); border-radius: 17px; font-size: 12px; }
.state-back:active { background: rgba(255, 255, 255, 0.08); }

@media (min-width: 700px) {
	.sheet { max-width: 560px; margin: 0 auto; }
	.stage-thumb { width: 56px; height: 50px; }
}
</style>
