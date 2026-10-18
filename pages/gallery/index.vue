<template>
	<view class="gallery-page" :class="{ night: campusNight }">
		<view class="g-ambient" :style="{ backgroundColor: ambientColor }"></view>
		<view class="g-nav" :class="{ focused: navFocused, solid: navSolid }">
			<view class="g-nav-inner">
				<text class="cuIcon-back g-nav-icon" @tap="back"></text>
				<view class="g-nav-brand">
					<text class="g-nav-title">校园图集</text>
					<text class="g-nav-eyebrow">CAMPUS GALLERY</text>
				</view>
				<view class="g-nav-publish" @tap="openPost"><text class="cuIcon-add"></text><text>发布</text></view>
			</view>
		</view>

		<gallery-focus :items="focusItems" :scroll-top="pageScrollTop" :night="campusNight" @open="openInfo" @focus-change="ambientIndex = $event"></gallery-focus>

		<scroll-view class="g-tags" :class="{ stuck: navSolid }" scroll-x :show-scrollbar="false">
			<view class="g-tags-track">
				<view class="g-tag" :class="{ active: !activeTag }" @tap="selectTag('')"><text class="g-tag-name">全部</text></view>
				<view v-for="tag in tags" :key="tag.name" class="g-tag" :class="{ active: activeTag === tag.name }" @tap="selectTag(tag.name)">
					<text class="g-tag-name"># {{ tag.name }}</text>
					<text class="g-tag-count">{{ tag.count }}</text>
				</view>
			</view>
		</scroll-view>

		<view v-if="firstLoading" class="g-skeletons">
			<view class="g-skeleton-col" v-for="col in 2" :key="col">
				<view class="g-skeleton-card" v-for="i in 2" :key="i">
					<view class="g-skeleton-cover" :class="{ tall: (col + i) % 2 === 0 }"></view>
					<view class="g-skeleton-line wide"></view>
					<view class="g-skeleton-line"></view>
					<view class="g-skeleton-line short"></view>
				</view>
			</view>
		</view>

		<view v-else class="g-waterfall">
			<view class="g-column" v-for="(column, index) in columns" :key="index">
				<view class="g-card" :id="'g-card-' + item.id" :class="{ revealed: item._revealed }" :style="cardTiltStyle(item)" v-for="item in column" :key="item.id" @tap="cardTap(item)">
					<view class="g-cover" :class="{ 'g-cover-fan': item.imageUrls.length > 1 }" :style="{ height: coverHeight(item) }"
						@touchstart="cardCoverStart(item, $event)" @touchmove="cardCoverMove(item, $event)" @touchend="cardCoverEnd(item, $event)" @touchcancel="cardCoverEnd(item, $event)"
						@mousedown="cardCoverMouseStart(item, $event)" @mousemove="cardCoverMouseMove(item, $event)" @mouseup="cardCoverMouseEnd" @mouseleave="cardCoverMouseEnd">
						<template v-if="item.imageUrls.length > 1">
							<view class="g-fan-glow"></view>
							<view v-for="sheet in fanSlotsOf(item)" :key="'fs-' + sheet.off" class="g-fan-sheet" :class="'g-fan-' + sheet.angle" :style="fanSheetStyle(item, sheet)">
								<image class="g-fan-img" :src="sheet.url" mode="aspectFill" lazy-load />
							</view>
							<view class="g-count"><text class="cuIcon-picfill"></text><text>{{ (item.fanStart || 0) + 1 }}/{{ item.imageUrls.length }}</text></view>
						</template>
						<image v-else class="g-cover-img g-cover-main" :src="item.imageUrls[0]" mode="aspectFill" lazy-load />
					</view>
					<view class="g-card-body">
						<text class="g-card-title">{{ item.title }}</text>
						<text v-if="item.description" class="g-card-desc">{{ item.description }}</text>
						<view v-if="item.tags.length" class="g-card-tags"><text v-for="tag in item.tags.slice(0, 3)" :key="tag">#{{ tag }}</text></view>
						<view class="g-card-foot">
							<view class="g-author">
								<image v-if="item.authorAvatar" class="g-author-avatar" :src="item.authorAvatar" mode="aspectFill" />
								<view v-else class="g-author-ph"><text class="cuIcon-picfill"></text></view>
								<text class="g-author-name">{{ item.authorName || '校园用户' }}</text>
							</view>
							<view class="g-views"><text class="cuIcon-attention"></text><text>{{ formatViews(item.views) }}</text></view>
						</view>
					</view>
				</view>
			</view>
		</view>

		<view v-if="!firstLoading && !items.length" class="g-empty">
			<view class="g-empty-mark"><text class="cuIcon-picfill"></text></view>
			<text class="g-empty-title">{{ activeTag ? '这个标签下还没有图集' : '图库还是空的' }}</text>
			<text class="g-empty-sub">{{ activeTag ? '换一个标签看看，或者发布第一组' : '发布第一组照片，成为图库的开端' }}</text>
			<view class="g-empty-cta" @tap="openPost">发布图集</view>
		</view>
		<view v-else-if="!firstLoading" class="g-more" @tap="loadMore">
			<template v-if="hasMore">加载更多</template>
			<template v-else>已经到底了</template>
		</view>

		<view class="g-fab" @tap="openPost">
			<text class="cuIcon-add"></text>
			<text>发布图集</text>
		</view>
	</view>
</template>

<script>
import { isDongchangfuNight, resolveCampusNight, getCampusThemeMode } from '@/utils/campusTheme.js'
import { galleryContextSet, galleryContextAppend } from '@/utils/galleryContext.js'
export default {
	data() {
		return {
			items: [],
			columns: [[], []],
			tags: [],
			activeTag: '',
			page: 1,
			total: 0,
			loading: false,
			firstLoading: true,
			hasMore: true,
			screenWidth: 375,
			screenHeight: 667,
			campusNight: false,
			pageScrollTop: 0,
			navFocused: false,
			navSolid: false,
			ambientIndex: 0,
			cardObserver: null,
			cardDrag: { item: null, x: 0, y: 0, px: 0, moved: false, used: false, time: 0, tilt: 0 },
			cardTouchTime: 0
		}
	},
	computed: {
		focusItems() { return this.items.slice(0, 10) },
		ambientColor() {
			const palette = [
				'110, 186, 174',
				'208, 161, 124',
				'151, 132, 196',
				'122, 166, 130',
				'120, 156, 186',
				'186, 130, 150'
			]
			const alpha = this.campusNight ? 0.12 : 0.2
			return 'rgba(' + palette[Math.abs(this.ambientIndex) % palette.length] + ', ' + alpha + ')'
		}
	},
	onLoad(q) {
		this.screenWidth = uni.getSystemInfoSync().windowWidth || 375
		this.screenHeight = uni.getSystemInfoSync().windowHeight || 667
		this.activeTag = q && q.tag ? q.tag : ''
		this.loadTags()
		this.load()
	},
	onPageScroll(e) {
		this.pageScrollTop = e.scrollTop || 0
		this.navFocused = this.pageScrollTop > 150
		this.navSolid = this.pageScrollTop > 40
	},
	onUnload() {
		if (this.cardObserver) { try { this.cardObserver.disconnect() } catch (e) {} this.cardObserver = null }
	},
	onShow() {
		this.campusNight = resolveCampusNight(getCampusThemeMode(), isDongchangfuNight(Date.now()))
		// #ifdef APP-PLUS
		try {
			plus.navigator.setStatusBarStyle(this.campusNight ? 'light' : 'dark')
			const shell = this.campusNight ? '#0a0e0c' : '#f5f8f7'
			uni.setBackgroundColor({ backgroundColor: shell, backgroundColorTop: shell, backgroundColorBottom: shell, fail() {} })
		} catch (e) {}
		// #endif
	},
	onPullDownRefresh() {
		this.page = 1
		this.items = []
		this.columns = [[], []]
		this.hasMore = true
		this.firstLoading = true
		galleryContextSet([], this.activeTag, 0, 1)
		this.load(() => uni.stopPullDownRefresh())
	},
	onReachBottom() { this.loadMore() },
	methods: {
		back() { uni.navigateBack({ delta: 1 }) },
		openPost() {
			if (!this.token()) return uni.navigateTo({ url: '/pages/user/login' })
			uni.navigateTo({ url: '/pages/gallery/post' })
		},
		openInfo(item) { uni.navigateTo({ url: '/pages/gallery/info?id=' + item.id }) },
		loadTags() {
			this.$Net.request({
				url: this.$API.galleryTags(),
				data: {},
				method: 'get',
				dataType: 'json',
				success: r => {
					if (r.data && r.data.code === 1 && Array.isArray(r.data.data)) {
						this.tags = r.data.data.map(x => ({ name: x.name, count: Number(x.count) || 0 }))
					}
				}
			})
		},
		token() {
			try { return JSON.parse(uni.getStorageSync('userinfo') || '{}').token || '' } catch (e) { return '' }
		},
		selectTag(tag) {
			if (this.activeTag === tag) return
			this.activeTag = tag
			this.page = 1
			this.items = []
			this.columns = [[], []]
			this.hasMore = true
			this.firstLoading = true
			galleryContextSet([], this.activeTag, 0, 1)
			this.load()
		},
		imageHeight(item) {
			const wide = this.screenWidth >= 700
			const side = wide ? 40 : 28
			const gap = wide ? 16 : 12
			const width = Math.max(150, Math.min(490, (Math.min(this.screenWidth - side, 1124 - side) - gap) / 2))
			const ratio = Math.max(0.4, Math.min(2.4, Number(item.imageRatio) || 0.8))
			return Math.round(width / ratio) + 'px'
		},
		coverHeight(item) {
			if (item.imageUrls.length <= 1) return this.imageHeight(item)
			const wide = this.screenWidth >= 700
			const side = wide ? 40 : 28
			const gap = wide ? 16 : 12
			const width = Math.max(150, Math.min(490, (Math.min(this.screenWidth - side, 1124 - side) - gap) / 2))
			return Math.round(Math.min(width * 1.15, 430)) + 'px'
		},
		fanSlotsOf(item) {
			const n = item.imageUrls.length
			const start = item.fanStart || 0
			const offsets = [0, 1, -1, 2, -2]
			const slots = []
			offsets.forEach(off => {
				const idx = ((start + off) % n + n) % n
				if (slots.some(s => s.idx === idx)) return
				const angle = off === 0 ? 0 : Math.abs(off) === 1 ? 13 : 26
				slots.push({ off: off, idx: idx, url: item.imageUrls[idx], angle: off > 0 ? angle : off < 0 ? -angle : 0 })
			})
			return slots
		},
		fanSheetStyle(item, sheet) {
			const dragging = this.cardDrag.item === item && this.cardDrag.moved
			if (!dragging) return {}
			const delta = Math.max(-30, Math.min(30, this.cardDrag.px * 0.325))
			return {
				transform: 'translateX(-50%) rotate(' + (sheet.angle + delta) + 'deg)',
				transition: 'none'
			}
		},
		cardCoverStart(item, e) {
			if (item.imageUrls.length <= 1) return
			this.cardTouchTime = Date.now()
			const t = e.touches && e.touches[0]
			if (!t) return
			this.cardDrag.item = item
			this.cardDrag.x = t.clientX
			this.cardDrag.y = t.clientY
			this.cardDrag.px = 0
			this.cardDrag.moved = false
			this.cardDrag.used = false
			this.cardDrag.time = Date.now()
			this.cardDrag.tilt = Math.max(-1, Math.min(1, (t.clientY - this.screenHeight / 2) / (this.screenHeight / 2)))
		},
		cardCoverMove(item, e) {
			if (this.cardDrag.item !== item) return
			const t = e.touches && e.touches[0]
			if (!t) return
			const dx = t.clientX - this.cardDrag.x
			const dy = t.clientY - this.cardDrag.y
			if (!this.cardDrag.moved) {
				if (Math.abs(dx) < 10 || Math.abs(dx) <= Math.abs(dy)) return
				this.cardDrag.moved = true
				try { e.preventDefault() } catch (err) {}
			}
			this.cardDrag.px = Math.max(-80, Math.min(80, dx))
		},
		cardCoverEnd(item) {
			if (this.cardDrag.item !== item) return
			const n = item.imageUrls.length
			const dt = Date.now() - this.cardDrag.time
			const velocity = dt > 0 ? this.cardDrag.px / dt : 0
			let move = 0
			if (this.cardDrag.px > 24 || velocity > 0.35) move = -1
			else if (this.cardDrag.px < -24 || velocity < -0.35) move = 1
			if (this.cardDrag.moved) this.cardDrag.used = true
			this.cardDrag.item = null
			this.cardDrag.moved = false
			this.cardDrag.px = 0
			if (move) {
				item.fanStart = ((item.fanStart || 0) + move + n) % n
				try { uni.vibrateShort({ type: 'light' }) } catch (e) {}
			}
		},
		cardCoverMouseStart(item, e) {
			if (item.imageUrls.length <= 1) return
			if (Date.now() - this.cardTouchTime < 700) return
			this.cardDrag.item = item
			this.cardDrag.x = e.clientX
			this.cardDrag.y = e.clientY
			this.cardDrag.px = 0
			this.cardDrag.moved = false
			this.cardDrag.used = false
			this.cardDrag.time = Date.now()
		},
		cardCoverMouseMove(item, e) {
			if (this.cardDrag.item !== item) return
			const dx = e.clientX - this.cardDrag.x
			const dy = e.clientY - this.cardDrag.y
			if (!this.cardDrag.moved) {
				if (Math.abs(dx) < 8 || Math.abs(dx) <= Math.abs(dy)) return
				this.cardDrag.moved = true
			}
			this.cardDrag.px = Math.max(-80, Math.min(80, dx))
		},
		cardCoverMouseEnd() {
			if (!this.cardDrag.item) return
			this.cardCoverEnd(this.cardDrag.item)
		},
		cardTap(item) {
			if (this.cardDrag.used) { this.cardDrag.used = false; return }
			this.openInfo(item)
		},
		cardTiltStyle(item) {
			const style = {}
			if (item._revealed && item._animDelay) style.transitionDelay = item._animDelay + 'ms'
			const pressing = this.cardDrag.item === item && !this.cardDrag.moved
			if (pressing) {
				const tilt = this.cardDrag.tilt || 0
				style.transform = 'perspective(700px) rotateX(' + (-2.5 * tilt) + 'deg) scale(0.98)'
				style.transition = 'none'
			}
			return style
		},
		observeCards() {
			if (!this.items.length) return
			if (!this.cardObserver) {
				try { this.cardObserver = uni.createIntersectionObserver(this) } catch (e) {
					this.items.forEach(x => { this.$set(x, '_revealed', true) })
					return
				}
			}
			this.items.forEach((item, index) => {
				if (item._observed) return
				this.$set(item, '_observed', true)
				item._order = index
				this.cardObserver.observe('#g-card-' + item.id, res => {
					if (res && res.intersectionRatio > 0 && !item._revealed) {
						this.$set(item, '_revealed', true)
						const delay = (index % 20) * 45
						this.$set(item, '_animDelay', delay)
						setTimeout(() => { if (item._animDelay) this.$set(item, '_animDelay', 0) }, delay + 700)
					}
				})
			})
			if (!this.cardRevealFallback) {
				this.cardRevealFallback = setTimeout(() => {
					const anyRevealed = this.items.some(x => x._revealed)
					if (!anyRevealed) this.items.forEach(x => { this.$set(x, '_revealed', true) })
				}, 2000)
			}
		},
		weightOf(item) {
			return item.imageUrls.length > 1 ? 1 / 1.15 : 1 / (Number(item.imageRatio) || 0.8)
		},
		formatViews(num) {
			const n = Number(num) || 0
			if (n >= 10000) return (n / 10000).toFixed(1) + 'w'
			if (n >= 1000) return (n / 1000).toFixed(1) + 'k'
			return String(n)
		},
		/* ---------- list ---------- */
		load(done) {
			if (this.loading) return
			this.loading = true
			this.$Net.request({
				url: this.$API.galleryList(),
				data: { page: this.page, limit: 20, tag: this.activeTag, _ts: Date.now() },
				method: 'get',
				dataType: 'json',
				success: r => {
					const data = r.data && r.data.code === 1 && Array.isArray(r.data.data) ? r.data.data : []
					this.total = Number(r.data && r.data.total || 0)
					this.append(data)
					this.hasMore = this.items.length < this.total
					this.loading = false
					this.firstLoading = false
					if (done) done()
				},
				fail: () => {
					this.loading = false
					this.firstLoading = false
					if (done) done()
				}
			})
		},
		append(rows) {
			rows.forEach(item => {
				item.imageUrls = Array.isArray(item.imageUrls) ? item.imageUrls : []
				item.tags = Array.isArray(item.tags) ? item.tags : []
				item.fanStart = 0
				this.items.push(item)
				const left = this.columns[0].reduce((sum, x) => sum + this.weightOf(x), 0)
				const right = this.columns[1].reduce((sum, x) => sum + this.weightOf(x), 0)
				this.columns[left <= right ? 0 : 1].push(item)
			})
			galleryContextAppend(rows, this.total, this.page)
			this.$nextTick(() => this.observeCards())
		},
		loadMore() {
			if (this.loading || !this.hasMore) return
			this.page++
			this.load()
		}
	}
}
</script>

<style scoped>
.gallery-page {
	--g-bg: #faf7f2;
	--g-surface: #ffffff;
	--g-ink: #24322c;
	--g-title: #1d2a24;
	--g-muted: #7d8c84;
	--g-faint: #a3aea8;
	--g-line: #eae4da;
	--g-primary: #237c74;
	--g-primary-soft: #e9f1ee;
	--g-accent: #b07952;
	--g-chip: #f1ece4;
	--g-chip-ink: #6b746c;
	--g-shadow: rgba(28, 42, 34, 0.1);
	--g-skeleton: #efe9e0;
	--g-skeleton-glow: #f9f6f0;
	--g-on-primary: #ffffff;
	--g-serif: Georgia, "Times New Roman", "Songti SC", STSong, serif;
	min-height: 100vh;
	background: radial-gradient(130% 55% at 50% 0%, #fffdf8 0%, #faf7f2 72%);
	color: var(--g-ink);
	padding-bottom: calc(96px + env(safe-area-inset-bottom));
}
.gallery-page.night {
	background: #0a0e0c;
}
.gallery-page.night {
	--g-bg: #0a0e0c;
	--g-surface: #161f1c;
	--g-ink: #e6efe9;
	--g-title: #eef5f0;
	--g-muted: #8fa198;
	--g-faint: #63736b;
	--g-line: rgba(233, 241, 236, 0.08);
	--g-primary: #7bc4b7;
	--g-primary-soft: #1f302b;
	--g-accent: #d8a97f;
	--g-chip: #1d2622;
	--g-chip-ink: #9dada4;
	--g-shadow: rgba(0, 0, 0, 0.55);
	--g-skeleton: #1d2622;
	--g-skeleton-glow: #26322d;
	--g-on-primary: #0e1412;
}

/* ambient glow */
.g-ambient { position: fixed; inset: 0; z-index: -1; pointer-events: none; background-color: rgba(110, 186, 174, 0.12); -webkit-mask-image: radial-gradient(120% 62% at 50% 0%, #000 0%, transparent 72%); mask-image: radial-gradient(120% 62% at 50% 0%, #000 0%, transparent 72%); transition: background-color 1.2s ease; }
.gallery-page { position: relative; z-index: 0; }

/* nav */
.g-nav { position: sticky; top: 0; z-index: 30; background: transparent; border-bottom: 1px solid transparent; transition: background 0.35s ease, border-color 0.35s ease; }
.g-nav.solid { background: rgba(245, 248, 247, 0.92); -webkit-backdrop-filter: blur(14px); backdrop-filter: blur(14px); border-bottom-color: var(--g-line); }
.night .g-nav.solid { background: rgba(10, 14, 12, 0.92); }
.g-nav-inner { position: relative; display: flex; align-items: center; box-sizing: border-box; padding: calc(env(safe-area-inset-top) + 4px) 16px 10px; }
.g-nav-icon { width: 40px; height: 40px; display: flex; align-items: center; justify-content: flex-start; font-size: 20px; color: var(--g-ink); }
.g-nav-brand { position: absolute; left: 50%; transform: translateX(-50%); display: flex; flex-direction: column; align-items: center; line-height: 1; }
.g-nav-title { font-family: var(--g-serif); font-size: 16px; font-weight: 700; color: var(--g-title); letter-spacing: 1px; }
.g-nav-eyebrow { margin-top: 4px; font-size: 8px; letter-spacing: 3px; color: var(--g-accent); }
.g-nav-publish { margin-left: auto; display: flex; align-items: center; gap: 4px; height: 32px; padding: 0 14px; border-radius: 16px; background: var(--g-primary); color: var(--g-on-primary); font-size: 12px; font-weight: 600; }
.g-nav-publish .cuIcon-add { font-size: 14px; }
.g-nav-publish:active { opacity: 0.85; }
.g-nav.focused .g-nav-title { color: var(--g-primary); }

/* tags */
.g-tags { position: sticky; top: calc(env(safe-area-inset-top) + 54px); z-index: 20; height: 46px; white-space: nowrap; background: transparent; border-bottom: 1px solid transparent; transition: background 0.35s ease, border-color 0.35s ease; }
.g-tags.stuck { background: rgba(250, 247, 242, 0.92); -webkit-backdrop-filter: blur(12px); backdrop-filter: blur(12px); border-bottom-color: var(--g-line); }
.night .g-tags.stuck { background: rgba(10, 14, 12, 0.92); }
.g-tags-track { display: flex; gap: 8px; padding: 0 18px; }
.g-tag { display: flex; align-items: center; gap: 5px; height: 32px; padding: 0 14px; background: var(--g-chip); border-radius: 16px; color: var(--g-chip-ink); font-size: 12px; }
.g-tag-name { white-space: nowrap; }
.g-tag-count { font-size: 10px; opacity: 0.6; }
.g-tag.active { background: var(--g-primary); color: var(--g-on-primary); font-weight: 600; }

/* waterfall */
.g-waterfall { display: flex; align-items: flex-start; gap: 12px; padding: 8px 14px 0; max-width: 1124px; margin: 0 auto; }
.g-column { flex: 1; min-width: 0; }
.g-card { overflow: hidden; margin-bottom: 12px; background: var(--g-surface); border-radius: 14px; border: 1px solid var(--g-line); box-shadow: 0 1px 2px rgba(28, 42, 34, 0.05), 0 14px 34px rgba(28, 42, 34, 0.1); opacity: 0; transform: translateY(16px); transition: box-shadow 0.25s ease; }
.night .g-card { box-shadow: 0 6px 24px rgba(0, 0, 0, 0.45); }
.g-card.revealed { opacity: 1; transform: translateY(0); transition: opacity 0.55s ease, transform 0.55s cubic-bezier(0.22, 0.9, 0.3, 1), box-shadow 0.25s ease; }
.g-card:active { transition: transform 0.12s ease, box-shadow 0.25s ease; transform: scale(0.985) translateY(-2px); box-shadow: 0 2px 4px rgba(28, 42, 34, 0.06), 0 20px 44px rgba(28, 42, 34, 0.16); }
.night .g-card:active { box-shadow: 0 8px 30px rgba(0, 0, 0, 0.6); }
.g-cover { position: relative; width: 100%; overflow: hidden; background: var(--g-chip); }
.g-cover-img { display: block; width: 100%; height: 100%; }
.g-cover-main { position: absolute; inset: 0; z-index: 3; }
.g-cover-fan { background: radial-gradient(130% 95% at 50% 110%, rgba(35, 124, 116, 0.14), rgba(35, 124, 116, 0) 62%), var(--g-chip); }
.night .g-cover-fan { background: radial-gradient(130% 95% at 50% 110%, rgba(110, 186, 174, 0.16), rgba(110, 186, 174, 0) 62%), var(--g-chip); }
.g-fan-glow { position: absolute; left: 50%; bottom: -8px; z-index: 0; width: 160%; height: 72px; transform: translateX(-50%); background: radial-gradient(50% 70% at 50% 50%, rgba(255, 247, 230, 0.9), rgba(255, 247, 230, 0) 72%); }
.night .g-fan-glow { background: radial-gradient(50% 70% at 50% 50%, rgba(255, 255, 255, 0.16), rgba(255, 255, 255, 0) 72%); }
.g-fan-sheet { position: absolute; left: 50%; bottom: 0; height: 88%; aspect-ratio: 3 / 4; transform-origin: 50% 100%; overflow: hidden; border-radius: 6px; background: #dfe6e0; box-shadow: 0 8px 18px rgba(24, 38, 31, 0.18); transition: transform 0.3s cubic-bezier(0.22, 0.9, 0.3, 1), filter 0.3s ease; }
.night .g-fan-sheet { background: #2a342f; box-shadow: 0 8px 18px rgba(0, 0, 0, 0.5); }
.g-fan-img { display: block; width: 100%; height: 100%; }
.g-fan-0 { z-index: 5; transform: translateX(-50%) rotate(0deg); filter: brightness(1); }
.g-fan-13 { z-index: 4; transform: translateX(-50%) rotate(13deg); filter: brightness(0.9); }
.g-fan--13 { z-index: 4; transform: translateX(-50%) rotate(-13deg); filter: brightness(0.9); }
.g-fan-26 { z-index: 3; transform: translateX(-50%) rotate(26deg); filter: brightness(0.78); }
.g-fan--26 { z-index: 3; transform: translateX(-50%) rotate(-26deg); filter: brightness(0.78); }
.night .g-fan-13, .night .g-fan--13 { filter: brightness(0.94); }
.night .g-fan-26, .night .g-fan--26 { filter: brightness(0.86); }
.g-card:active .g-fan-13 { transform: translateX(-50%) rotate(17deg); }
.g-card:active .g-fan--13 { transform: translateX(-50%) rotate(-17deg); }
.g-card:active .g-fan-26 { transform: translateX(-50%) rotate(32deg); }
.g-card:active .g-fan--26 { transform: translateX(-50%) rotate(-32deg); }
.g-card:active .g-fan-0 { transform: translateX(-50%) rotate(0deg) scale(1.05); }
.g-count { position: absolute; right: 8px; top: 8px; z-index: 6; display: flex; align-items: center; gap: 3px; padding: 5px 9px; border-radius: 12px; background: rgba(15, 22, 19, 0.72); color: #fff; font-size: 10px; line-height: 1; box-shadow: 0 1px 4px rgba(0, 0, 0, 0.18); }
.g-count .cuIcon-picfill { font-size: 10px; }
.g-card-body { padding: 12px 12px 11px; }
.g-card-title { display: -webkit-box; overflow: hidden; -webkit-line-clamp: 2; -webkit-box-orient: vertical; font-family: var(--g-serif); font-size: 15px; line-height: 1.45; font-weight: 700; color: var(--g-title); }
.g-card-desc { display: -webkit-box; overflow: hidden; -webkit-line-clamp: 2; -webkit-box-orient: vertical; margin-top: 6px; color: var(--g-muted); font-size: 11px; line-height: 1.5; }
.g-card-tags { display: flex; gap: 7px; overflow: hidden; margin-top: 8px; color: var(--g-primary); font-size: 10px; white-space: nowrap; }
.night .g-card-tags { color: var(--g-accent); }
.g-card-foot { display: flex; justify-content: space-between; align-items: center; margin-top: 10px; }
.g-author { display: flex; align-items: center; gap: 6px; min-width: 0; }
.g-author-avatar, .g-author-ph { width: 22px; height: 22px; border-radius: 50%; background: var(--g-chip); flex: none; }
.g-author-ph { display: flex; align-items: center; justify-content: center; color: var(--g-faint); font-size: 12px; }
.g-author-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 96px; color: var(--g-muted); font-size: 11px; }
.g-views { display: flex; align-items: center; gap: 3px; color: var(--g-faint); font-size: 10px; white-space: nowrap; }
.g-views .cuIcon-attention { font-size: 11px; }

/* skeleton */
.g-skeletons { display: flex; align-items: flex-start; gap: 12px; padding: 8px 14px 0; max-width: 1124px; margin: 0 auto; }
.g-skeleton-col { flex: 1; min-width: 0; }
.g-skeleton-card { overflow: hidden; margin-bottom: 12px; background: var(--g-surface); border-radius: 12px; }
.g-skeleton-cover { height: 190px; background: linear-gradient(100deg, var(--g-skeleton) 40%, var(--g-skeleton-glow) 50%, var(--g-skeleton) 60%); background-size: 200% 100%; animation: g-shimmer 1.4s ease infinite; }
.g-skeleton-cover.tall { height: 240px; }
.g-skeleton-line { height: 10px; margin: 12px 12px 0; border-radius: 5px; background: linear-gradient(100deg, var(--g-skeleton) 40%, var(--g-skeleton-glow) 50%, var(--g-skeleton) 60%); background-size: 200% 100%; animation: g-shimmer 1.4s ease infinite; }
.g-skeleton-line.wide { width: 85%; }
.g-skeleton-line:not(.wide):not(.short) { width: 60%; }
.g-skeleton-line.short { width: 34%; margin-bottom: 14px; }
@keyframes g-shimmer { 0% { background-position: 180% 0; } 100% { background-position: -20% 0; } }

/* states */
.g-empty { padding: 66px 24px 40px; text-align: center; }
.g-empty-mark { display: flex; align-items: center; justify-content: center; width: 68px; height: 68px; margin: 0 auto; border-radius: 50%; background: var(--g-chip); color: var(--g-faint); font-size: 30px; }
.g-empty-title { display: block; margin-top: 18px; font-family: var(--g-serif); font-size: 16px; font-weight: 700; color: var(--g-title); }
.g-empty-sub { display: block; margin-top: 8px; font-size: 12px; color: var(--g-muted); }
.g-empty-cta { display: inline-block; margin-top: 20px; height: 34px; line-height: 34px; padding: 0 20px; border-radius: 17px; background: var(--g-primary); color: #fff; font-size: 12px; font-weight: 600; }
.g-empty-cta:active { opacity: 0.85; }
.g-more { display: table; margin: 10px auto 0; height: 34px; line-height: 34px; padding: 0 22px; border: 1px solid var(--g-line); border-radius: 17px; color: var(--g-muted); font-size: 12px; }
.g-more:active { background: var(--g-chip); }

/* floating publish */
.g-fab {
	position: fixed;
	right: 20px;
	bottom: calc(24px + env(safe-area-inset-bottom));
	z-index: 40;
	display: flex;
	align-items: center;
	gap: 7px;
	height: 50px;
	padding: 0 20px 0 18px;
	border-radius: 25px;
	background: var(--g-primary);
	color: var(--g-on-primary);
	font-size: 14px;
	font-weight: 700;
	letter-spacing: 0.5px;
	box-shadow: 0 10px 26px rgba(35, 124, 116, 0.38);
	transition: transform 0.15s ease, opacity 0.15s ease;
}
.g-fab .cuIcon-add { font-size: 18px; }
.g-fab:active { transform: scale(0.96); opacity: 0.9; }
.night .g-fab { box-shadow: 0 10px 26px rgba(0, 0, 0, 0.55); }

@media (min-width: 700px) {
	.g-tags-track { padding-left: max(22px, calc((100vw - 1080px) / 2)); padding-right: max(22px, calc((100vw - 1080px) / 2)); }
	.g-waterfall, .g-skeletons { gap: 16px; padding-left: 20px; padding-right: 20px; }
	.g-card-body { padding: 14px 14px 12px; }
	.g-card-title { font-size: 16px; }
}
</style>
