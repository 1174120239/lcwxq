<template>
	<view class="editor-page" :class="{ night: campusNight }">
		<view class="editor-nav">
			<view class="editor-nav-inner">
				<text class="cuIcon-back editor-back" @tap="back"></text>
				<view class="editor-nav-brand">
					<text class="editor-nav-title">发布图集</text>
					<text class="editor-nav-eyebrow">NEW ALBUM</text>
				</view>
				<view class="editor-submit" :class="{ disabled: !canSubmit }" @tap="submit">{{ submitting ? '提交中' : '发布' }}</view>
			</view>
		</view>

		<scroll-view scroll-y class="editor-scroll">
			<view class="editor-form">
				<input v-model="title" maxlength="120" class="editor-title" placeholder="给这组照片起个标题" placeholder-class="editor-ph" />
				<view class="editor-desc-wrap">
					<textarea v-model="description" maxlength="2000" class="editor-desc" placeholder="分享照片背后的故事（选填）" placeholder-class="editor-ph" />
					<text class="editor-desc-count">{{ description.length }}/2000</text>
				</view>

				<view class="editor-section">
					<view class="editor-section-head">
						<text class="editor-section-title">照片</text>
						<text class="editor-section-note">{{ images.length }}/9 · 第一张为封面</text>
					</view>
					<view class="editor-grid">
						<view class="editor-cell" v-for="(item, index) in images" :key="item.url">
							<image :src="item.url" mode="aspectFill" @tap="preview(index)" />
							<view v-if="index === 0" class="editor-cover-mark">封面</view>
							<view class="editor-remove" @tap.stop="removeImage(index)"><text class="cuIcon-close"></text></view>
						</view>
						<view v-if="images.length < 9" class="editor-add" @tap="chooseImages">
							<text class="cuIcon-cameraadd"></text>
							<text class="editor-add-text">{{ uploading ? '上传中 ' + uploadDone + '/' + uploadTotal : '添加照片' }}</text>
						</view>
					</view>
				</view>

				<view class="editor-section">
					<view class="editor-section-head">
						<text class="editor-section-title">标签</text>
						<text class="editor-section-note">{{ tags.length }}/10 · 至少 1 个</text>
					</view>
					<view class="editor-tags">
						<view v-for="(tag, index) in tags" :key="tag" class="editor-tag">
							<text>#{{ tag }}</text>
							<text class="cuIcon-close editor-tag-close" @tap="removeTag(index)"></text>
						</view>
						<input v-if="tags.length < 10" v-model="tagInput" maxlength="24" class="editor-tag-input" placeholder="输入后回车添加" confirm-type="done" @confirm="addTag" @blur="addTag" />
					</view>
					<scroll-view v-if="hotTags.length" scroll-x class="editor-hot" :show-scrollbar="false">
						<view class="editor-hot-track">
							<view v-for="tag in hotTags" :key="tag.name" class="editor-hot-chip" @tap="addHotTag(tag.name)">
								<text># {{ tag.name }}</text>
								<text class="cuIcon-add"></text>
							</view>
						</view>
					</scroll-view>
				</view>

				<view class="editor-note">
					<text class="cuIcon-info"></text>
					<text>提交后由管理员审核，通过后会展示在图库中。</text>
				</view>
			</view>
		</scroll-view>
	</view>
</template>

<script>
import { isDongchangfuNight, resolveCampusNight, getCampusThemeMode } from '@/utils/campusTheme.js'
export default {
	data() {
		return {
			title: '',
			description: '',
			images: [],
			tags: [],
			tagInput: '',
			hotTags: [],
			submitting: false,
			uploading: false,
			uploadDone: 0,
			uploadTotal: 0,
			campusNight: false
		}
	},
	computed: {
		canSubmit() {
			return this.title.trim().length >= 2 && this.images.length > 0 && this.tags.length > 0 && !this.submitting && !this.uploading
		}
	},
	onLoad() { this.loadHotTags() },
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
	methods: {
		token() {
			try { return JSON.parse(uni.getStorageSync('userinfo') || '{}').token || '' } catch (e) { return '' }
		},
		back() { uni.navigateBack({ delta: 1 }) },
		loadHotTags() {
			this.$Net.request({
				url: this.$API.galleryTags(),
				data: {},
				method: 'get',
				dataType: 'json',
				success: r => {
					if (r.data && r.data.code === 1 && Array.isArray(r.data.data)) {
						this.hotTags = r.data.data.slice(0, 10).map(x => ({ name: x.name, count: Number(x.count) || 0 }))
					}
				}
			})
		},
		chooseImages() {
			if (this.uploading) return
			const remaining = 9 - this.images.length
			if (remaining <= 0) return
			uni.chooseImage({
				count: remaining,
				sizeType: ['compressed'],
				sourceType: ['album', 'camera'],
				success: async r => {
					const paths = r.tempFilePaths || []
					this.uploading = true
					this.uploadTotal = paths.length
					this.uploadDone = 0
					uni.showLoading({ title: '上传 0/' + paths.length, mask: true })
					for (let i = 0; i < paths.length; i++) {
						const path = paths[i]
						const info = await new Promise(resolve => uni.getImageInfo({ src: path, success: resolve, fail: () => resolve({ width: 4, height: 5 }) }))
						uni.showLoading({ title: '上传 ' + (i + 1) + '/' + paths.length, mask: true })
						const uploaded = await this.upload(path)
						if (uploaded) {
							this.images.push({ url: uploaded, ratio: Number(info.width || 4) / Number(info.height || 5) })
							this.uploadDone = i + 1
						} else break
					}
					this.uploading = false
					uni.hideLoading()
				}
			})
		},
		upload(path) {
			return new Promise(resolve => {
				uni.uploadFile({
					url: this.$API.upload(),
					filePath: path,
					name: 'file',
					formData: { token: this.token() },
					success: r => {
						try {
							const d = JSON.parse(r.data)
							const ok = d.code === 1 && d.data && d.data.url
							resolve(ok ? d.data.url : '')
							if (!ok) uni.showToast({ title: d.msg || '图片上传失败', icon: 'none' })
						} catch (e) {
							resolve('')
							uni.showToast({ title: '上传响应无效', icon: 'none' })
						}
					},
					fail: () => {
						resolve('')
						uni.showToast({ title: '图片上传失败', icon: 'none' })
					}
				})
			})
		},
		preview(index) {
			uni.previewImage({ urls: this.images.map(x => x.url), current: this.images[index].url })
		},
		removeImage(index) { this.images.splice(index, 1) },
		addTag() {
			const value = this.tagInput.trim().replace(/^[#＃]+/, '').replace(/[,，\s]/g, '')
			if (value && !this.tags.includes(value) && this.tags.length < 10) this.tags.push(value)
			this.tagInput = ''
		},
		addHotTag(name) {
			if (this.uploading) return
			if (!this.tags.includes(name) && this.tags.length < 10) this.tags.push(name)
		},
		removeTag(index) { this.tags.splice(index, 1) },
		submit() {
			this.addTag()
			if (!this.canSubmit) return
			if (!this.token()) return uni.navigateTo({ url: '/pages/user/login' })
			this.submitting = true
			this.$Net.request({
				url: this.$API.gallerySubmit(),
				data: {
					token: this.token(),
					params: JSON.stringify({
						title: this.title.trim(),
						description: this.description.trim(),
						imageUrls: this.images.map(x => x.url),
						imageRatio: this.images[0] && this.images[0].ratio || 0.8,
						tags: this.tags
					})
				},
				method: 'post',
				dataType: 'json',
				success: r => {
					this.submitting = false
					if (r.data && r.data.code === 1) {
						uni.showModal({
							title: '图集已提交',
							content: '审核通过后会在图库公开展示。',
							showCancel: false,
							success: () => this.back()
						})
					} else {
						uni.showToast({ title: r.data && r.data.msg || '提交失败', icon: 'none' })
					}
				},
				fail: () => {
					this.submitting = false
					uni.showToast({ title: '网络异常，请稍后重试', icon: 'none' })
				}
			})
		}
	}
}
</script>

<style scoped>
.editor-page {
	--e-bg: #faf7f2;
	--e-surface: #ffffff;
	--e-ink: #24322c;
	--e-title: #1d2a24;
	--e-muted: #7d8c84;
	--e-faint: #a3aea8;
	--e-line: #eae4da;
	--e-primary: #237c74;
	--e-primary-soft: #e9f1ee;
	--e-chip: #f1ece4;
	--e-danger: #b4584a;
	--e-on-primary: #ffffff;
	--e-serif: Georgia, "Times New Roman", "Songti SC", STSong, serif;
	min-height: 100vh;
	background: var(--e-bg);
	color: var(--e-ink);
}
.editor-page.night {
	--e-bg: #0a0e0c;
	--e-surface: #161f1c;
	--e-ink: #e6efe9;
	--e-title: #eef5f0;
	--e-muted: #8fa198;
	--e-faint: #63736b;
	--e-line: rgba(233, 241, 236, 0.08);
	--e-primary: #7bc4b7;
	--e-primary-soft: #1f302b;
	--e-chip: #1d2622;
	--e-danger: #d8947f;
	--e-on-primary: #0e1412;
}

/* nav */
.editor-nav { background: var(--e-surface); border-bottom: 1px solid var(--e-line); }
.editor-nav-inner { position: relative; display: flex; align-items: center; box-sizing: border-box; padding: calc(env(safe-area-inset-top) + 4px) 16px 10px; }
.editor-back { width: 40px; height: 40px; display: flex; align-items: center; justify-content: flex-start; font-size: 20px; color: var(--e-ink); }
.editor-nav-brand { position: absolute; left: 50%; transform: translateX(-50%); display: flex; flex-direction: column; align-items: center; line-height: 1; }
.editor-nav-title { font-family: var(--e-serif); font-size: 16px; font-weight: 700; color: var(--e-title); letter-spacing: 1px; }
.editor-nav-eyebrow { margin-top: 4px; font-size: 8px; letter-spacing: 3px; color: var(--e-primary); }
.editor-submit { margin-left: auto; display: flex; align-items: center; justify-content: center; height: 32px; padding: 0 16px; border-radius: 16px; background: var(--e-primary); color: var(--e-on-primary); font-size: 12px; font-weight: 600; }
.editor-submit:active { opacity: 0.85; }
.editor-submit.disabled { opacity: 0.35; }

/* form */
.editor-scroll { height: calc(100vh - env(safe-area-inset-top) - 54px); }
.editor-form { max-width: 760px; margin: 0 auto; padding: 28px 18px 60px; box-sizing: border-box; }
.editor-title { width: 100%; height: 56px; border-bottom: 1px solid var(--e-line); font-family: var(--e-serif); font-size: 22px; font-weight: 700; color: var(--e-title); }
.editor-ph { color: var(--e-faint); }
.editor-desc-wrap { position: relative; }
.editor-desc { width: 100%; min-height: 96px; padding: 14px 0 26px; box-sizing: border-box; font-size: 14px; line-height: 1.65; color: var(--e-ink); }
.editor-desc-count { position: absolute; right: 0; bottom: 8px; font-size: 10px; color: var(--e-faint); }

/* sections */
.editor-section { margin-top: 26px; }
.editor-section-head { display: flex; align-items: baseline; justify-content: space-between; }
.editor-section-title { font-size: 14px; font-weight: 700; color: var(--e-title); }
.editor-section-note { font-size: 11px; color: var(--e-faint); }

/* image grid */
.editor-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; margin-top: 14px; }
.editor-cell, .editor-add { position: relative; aspect-ratio: 1; border-radius: 8px; overflow: hidden; background: var(--e-chip); }
.editor-cell image { width: 100%; height: 100%; }
.editor-cover-mark { position: absolute; left: 6px; bottom: 6px; padding: 3px 8px; border-radius: 4px; background: rgba(16, 24, 20, 0.72); color: #fff; font-size: 10px; line-height: 1.3; }
.editor-remove { position: absolute; right: 6px; top: 6px; display: flex; align-items: center; justify-content: center; width: 22px; height: 22px; border-radius: 50%; background: rgba(16, 24, 20, 0.72); color: #fff; font-size: 11px; }
.editor-add { display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 7px; border: 1px dashed var(--e-line); color: var(--e-muted); background: transparent; }
.editor-add .cuIcon-cameraadd { font-size: 24px; }
.editor-add-text { font-size: 11px; }
.editor-add:active { background: var(--e-chip); }

/* tags */
.editor-tags { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; margin-top: 14px; }
.editor-tag { display: flex; align-items: center; gap: 4px; height: 30px; padding: 0 12px; border-radius: 15px; background: var(--e-primary-soft); color: var(--e-primary); font-size: 12px; }
.editor-tag-close { font-size: 10px; opacity: 0.75; }
.editor-tag-input { min-width: 130px; height: 30px; font-size: 12px; color: var(--e-ink); }
.editor-hot { margin-top: 12px; white-space: nowrap; }
.editor-hot-track { display: flex; gap: 8px; }
.editor-hot-chip { display: flex; align-items: center; gap: 4px; height: 28px; padding: 0 12px; border: 1px solid var(--e-line); border-radius: 14px; color: var(--e-muted); font-size: 11px; }
.editor-hot-chip .cuIcon-add { font-size: 11px; }
.editor-hot-chip:active { background: var(--e-chip); color: var(--e-primary); }

/* note */
.editor-note { display: flex; gap: 8px; margin-top: 32px; padding: 13px 14px; background: var(--e-primary-soft); color: var(--e-muted); border-radius: 8px; font-size: 11px; line-height: 1.55; }
.editor-note .cuIcon-info { flex: none; margin-top: 2px; color: var(--e-primary); font-size: 13px; }

@media (min-width: 700px) {
	.editor-form { padding-top: 40px; }
	.editor-grid { grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; }
	.editor-title { font-size: 26px; }
}
</style>
