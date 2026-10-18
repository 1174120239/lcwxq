<template>
	<view v-if="items.length" class="cf-stage" :class="{ night: night }" :style="stageStyle">
		<view class="cf-head">
			<text class="cf-eyebrow" :style="titleYieldStyle">CAMPUS PHOTO JOURNAL</text>
			<text class="cf-title" :style="titleYieldStyle">看见校园</text>
		</view>
		<view class="cf-viewport" :style="{ height: cfCardH + 'px' }"
			@touchstart="cfStart" @touchmove="cfMove" @touchend="cfEnd" @touchcancel="cfEnd"
			@mousedown="cfMouseStart" @mousemove="cfMouseMove" @mouseup="cfMouseEnd" @mouseleave="cfMouseEnd">
			<view class="cf-deck">
				<view v-for="(item, index) in items" :key="item.id" class="cf-card" :style="cfCardStyle(index)" @tap="cfTap(index, item)">
					<image v-if="item.imageUrls.length > 1" class="cf-stack-img" :src="item.imageUrls[1]" mode="aspectFill" />
					<image class="cf-img" :src="item.imageUrls[0]" mode="aspectFill" />
					<view class="cf-dim" :style="{ opacity: cfDim(index) }"></view>
					<view class="cf-copy" :class="{ show: index === focusIndex && !cfDragging }">
						<text class="cf-copy-meta">{{ item.imageUrls.length }} 张 · {{ formatViews(item.views) }} 浏览</text>
						<text class="cf-copy-title">{{ item.title }}</text>
						<text v-if="item.tags.length" class="cf-copy-tags">#{{ item.tags.slice(0, 2).join(' #') }}</text>
					</view>
				</view>
			</view>
		</view>
		<view class="cf-dots">
			<view v-for="(item, index) in items" :key="'dot-' + item.id" class="cf-dot" :class="{ active: index === focusIndex }" @tap="focusIndex = index"></view>
		</view>
		<view class="cf-spill"></view>
	</view>
</template>

<script>
export default {
	name: 'gallery-focus',
	props: {
		items: { type: Array, default: () => [] },
		scrollTop: { type: Number, default: 0 },
		night: { type: Boolean, default: false }
	},
	data() {
		return {
			focusIndex: 0,
			cfCardW: 0,
			cfCardH: 0,
			cfStep: 0,
			stageH: 0,
			cfDragging: false,
			cfDragPx: 0,
			cfDragStartX: 0,
			cfDragStartY: 0,
			cfDragMoved: false,
			cfDragTime: 0,
			cfLastTouch: 0,
			autoTimer: null
		}
	},
	computed: {
		collapseP() {
			if (!this.stageH) return 0
			return Math.max(0, Math.min(1, this.scrollTop * 1.15 / (this.stageH + 24)))
		},
		stageStyle() {
			const p = this.collapseP
			if (p <= 0) return {}
			return {
				transform: 'translateY(' + (-p * (this.stageH + 24)) + 'px) scale(' + (1 - 0.08 * p) + ')',
				opacity: 1 - 0.92 * p,
				transformOrigin: 'top center',
				pointerEvents: p >= 0.95 ? 'none' : 'auto'
			}
		},
		titleYieldStyle() {
			const p = this.collapseP
			if (p <= 0) return {}
			return {
				transform: 'translateY(' + (-p * 14) + 'px) scale(' + (1 - 0.3 * p) + ')',
				opacity: 1 - p,
				transformOrigin: 'center bottom'
			}
		}
	},
	mounted() {
		this.cfLayout()
		this.measure()
		this.startAuto()
	},
	beforeDestroy() {
		if (this.autoTimer) clearInterval(this.autoTimer)
	},
	watch: {
		items() {
			if (this.focusIndex >= this.items.length) this.focusIndex = 0
			this.$nextTick(() => this.measure())
			this.startAuto()
		},
		collapseP(p) {
			if (p >= 0.2) this.stopAuto()
			else this.startAuto()
		}
	},
	methods: {
		formatViews(num) {
			const n = Number(num) || 0
			if (n >= 10000) return (n / 10000).toFixed(1) + 'w'
			if (n >= 1000) return (n / 1000).toFixed(1) + 'k'
			return String(n)
		},
		startAuto() {
			if (this.autoTimer || this.items.length < 2) return
			this.autoTimer = setInterval(() => {
				if (this.cfDragging || this.collapseP >= 0.2) return
				const n = this.items.length
				this.focusIndex = (this.focusIndex + 1) % n
				this.$emit('focus-change', this.focusIndex)
			}, 3600)
		},
		stopAuto() {
			if (this.autoTimer) { clearInterval(this.autoTimer); this.autoTimer = null }
		},
		cfLayout() {
			const w = uni.getSystemInfoSync().windowWidth || 375
			this.cfCardW = Math.min(w * 0.52, 300)
			this.cfCardH = Math.round(this.cfCardW * 4 / 3)
			this.cfStep = Math.round(this.cfCardW * 0.5)
		},
		measure() {
			uni.createSelectorQuery().in(this).select('.cf-stage').boundingClientRect(rect => {
				if (rect && rect.height) this.stageH = Math.round(rect.height)
			}).exec()
		},
		cfOffset(index) {
			const n = this.items.length || 1
			let d = index - this.focusIndex
			if (d > n / 2) d -= n
			if (d < -n / 2) d += n
			return d
		},
		cfCardStyle(index) {
			const d = this.cfOffset(index)
			const p = this.cfDragging ? Math.max(-1.3, Math.min(1.3, this.cfDragPx / this.cfStep)) : 0
			const off = d + p
			const ad = Math.abs(off)
			const x = off * this.cfStep
			const y = ad * 10
			const rotY = -off * 30
			const z = -ad * 70
			const scale = Math.max(0.62, 1 - ad * 0.13)
			return {
				width: this.cfCardW + 'px',
				height: this.cfCardH + 'px',
				marginLeft: -(this.cfCardW / 2) + 'px',
				marginTop: -(this.cfCardH / 2) + 'px',
				zIndex: 100 - Math.round(ad * 10),
				transform: 'translate3d(' + x + 'px,' + y + 'px,' + z + 'px) rotateY(' + rotY + 'deg) scale(' + scale + ')',
				transition: this.cfDragging ? 'none' : 'transform 0.55s cubic-bezier(0.22, 0.9, 0.3, 1)'
			}
		},
		cfDim(index) {
			const d = this.cfOffset(index)
			const p = this.cfDragging ? Math.max(-1.3, Math.min(1.3, this.cfDragPx / this.cfStep)) : 0
			return Math.min(0.55, Math.abs(d + p) * 0.3)
		},
		cfStart(e) {
			this.cfLastTouch = Date.now()
			this.stopAuto()
			const t = e.touches && e.touches[0]
			if (!t) return
			this.cfDragging = true
			this.cfDragPx = 0
			this.cfDragStartX = t.clientX
			this.cfDragStartY = t.clientY
			this.cfDragMoved = false
			this.cfDragTime = Date.now()
		},
		cfMove(e) {
			if (!this.cfDragging) return
			const t = e.touches && e.touches[0]
			if (!t) return
			const dx = t.clientX - this.cfDragStartX
			const dy = t.clientY - this.cfDragStartY
			if (!this.cfDragMoved) {
				if (Math.abs(dx) < 8 || Math.abs(dx) <= Math.abs(dy)) return
				this.cfDragMoved = true
				try { e.preventDefault() } catch (err) {}
			}
			this.cfDragPx = Math.max(-this.cfStep * 1.3, Math.min(this.cfStep * 1.3, dx))
		},
		cfEnd() {
			if (!this.cfDragging) return
			this.cfDragging = false
			const px = this.cfDragPx
			this.cfDragPx = 0
			this.cfFinish(px)
			this.startAuto()
		},
		cfMouseStart(e) {
			if (Date.now() - this.cfLastTouch < 700) return
			this.stopAuto()
			this.cfDragging = true
			this.cfDragPx = 0
			this.cfDragStartX = e.clientX
			this.cfDragStartY = e.clientY
			this.cfDragMoved = false
			this.cfDragTime = Date.now()
		},
		cfMouseMove(e) {
			if (!this.cfDragging) return
			const dx = e.clientX - this.cfDragStartX
			const dy = e.clientY - this.cfDragStartY
			if (!this.cfDragMoved) {
				if (Math.abs(dx) < 6 || Math.abs(dx) <= Math.abs(dy)) return
				this.cfDragMoved = true
			}
			this.cfDragPx = Math.max(-this.cfStep * 1.3, Math.min(this.cfStep * 1.3, dx))
		},
		cfMouseEnd() { this.cfEnd() },
		cfFinish(px) {
			const dt = Date.now() - this.cfDragTime
			const velocity = dt > 0 ? px / dt : 0
			let move = 0
			if (px > this.cfStep * 0.22 || velocity > 0.4) move = 1
			else if (px < -this.cfStep * 0.22 || velocity < -0.4) move = -1
			if (move) {
				const n = this.items.length
				this.focusIndex = (this.focusIndex + move + n) % n
				this.$emit('focus-change', this.focusIndex)
				try { uni.vibrateShort({ type: 'light' }) } catch (e) {}
			}
		},
		cfTap(index, item) {
			if (this.cfDragMoved) return
			if (index === this.focusIndex) this.$emit('open', item)
			else {
				this.focusIndex = index
				this.$emit('focus-change', index)
			}
		}
	}
}
</script>

<style scoped>
.cf-stage { position: relative; background: #0c110f; border-bottom: 1px solid rgba(255, 255, 255, 0.05); will-change: transform, opacity; }
.cf-stage.night { background: #090d0b; border-bottom-color: rgba(255, 255, 255, 0.09); }
.cf-stage::after { content: ''; position: absolute; left: 0; right: 0; bottom: 0; height: 42%; pointer-events: none; background: radial-gradient(52% 68% at 50% 100%, rgba(255, 255, 255, 0.07), rgba(255, 255, 255, 0) 72%); }
.cf-spill { position: absolute; left: 0; right: 0; top: 100%; height: 84px; pointer-events: none; background: radial-gradient(58% 100% at 50% 0%, rgba(255, 250, 236, 0.95) 0%, rgba(255, 250, 236, 0.55) 38%, rgba(255, 250, 236, 0) 74%); }
.night .cf-spill { background: radial-gradient(58% 100% at 50% 0%, rgba(255, 255, 255, 0.14) 0%, rgba(255, 255, 255, 0.05) 40%, rgba(255, 255, 255, 0) 72%); }
.cf-head { position: relative; z-index: 2; text-align: center; padding: 14px 18px 8px; }
.cf-eyebrow { display: block; color: rgba(255, 255, 255, 0.42); font-size: 9px; letter-spacing: 4px; }
.cf-title { display: block; margin-top: 6px; font-family: Georgia, "Times New Roman", "Songti SC", STSong, serif; font-size: 24px; font-weight: 700; color: #fff; letter-spacing: 3px; }
.cf-viewport { position: relative; z-index: 1; width: 100%; margin-top: 8px; overflow: hidden; }
.cf-deck { position: absolute; left: 0; right: 0; top: 0; bottom: 0; perspective: 1100px; }
.cf-card { position: absolute; left: 50%; top: 50%; border-radius: 14px; overflow: hidden; background: #18211d; box-shadow: 0 26px 60px rgba(0, 0, 0, 0.55); will-change: transform; }
.cf-img { position: absolute; inset: 0; z-index: 1; display: block; width: 100%; height: 100%; }
.cf-stack-img { position: absolute; inset: 0; z-index: 0; display: block; width: 100%; height: 100%; border-radius: 10px; transform: translate(-7px, 9px) rotate(-3.2deg) scale(0.95); opacity: 0.9; }
.cf-dim { position: absolute; inset: 0; z-index: 2; background: #0a0e0c; }
.cf-copy { position: absolute; left: 0; right: 0; bottom: 0; z-index: 3; padding: 34px 12px 12px; background: linear-gradient(180deg, rgba(6, 10, 8, 0) 0%, rgba(6, 10, 8, 0.82) 100%); opacity: 0; transition: opacity 0.3s ease 0.08s; }
.cf-copy.show { opacity: 1; }
.cf-copy-meta { display: block; color: rgba(255, 255, 255, 0.6); font-size: 10px; letter-spacing: 0.5px; }
.cf-copy-title { display: -webkit-box; overflow: hidden; -webkit-line-clamp: 2; -webkit-box-orient: vertical; margin-top: 5px; font-family: Georgia, "Times New Roman", "Songti SC", STSong, serif; font-size: 15px; line-height: 1.4; font-weight: 700; color: #fff; }
.cf-copy-tags { display: block; overflow: hidden; margin-top: 5px; color: rgba(255, 255, 255, 0.55); font-size: 10px; white-space: nowrap; text-overflow: ellipsis; }
.cf-dots { position: relative; z-index: 2; display: flex; justify-content: center; align-items: center; gap: 6px; padding: 10px 0 14px; }
.cf-dot { width: 6px; height: 6px; border-radius: 3px; background: rgba(255, 255, 255, 0.25); transition: all 0.3s ease; }
.cf-dot.active { width: 18px; background: #fff; }
</style>
