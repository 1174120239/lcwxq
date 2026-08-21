<template>
	<view class="rich-composer" :class="{'is-night': night}">
		<editor id="rich-editor" class="rich-composer-input" :placeholder="placeholder" :read-only="false"
			:show-img-size="false" :show-img-toolbar="false" :show-img-resize="false"
			@ready="onEditorReady" @input="onEditorInput" @statuschange="onStatusChange"></editor>
		<view v-if="showStatus" class="rich-composer-status"><text>{{textLength}}/{{maxlength}}</text><text>{{status}}</text></view>
		<view class="rich-composer-toolbar">
			<text class="cuIcon-emoji toolbar-button" :class="{'is-active': formatOpen === false && emojiActive}" @tap="$emit('emoji')"></text>
			<text v-if="showMedia" class="cuIcon-pic toolbar-button" @tap="$emit('media')"></text>
			<text class="toolbar-button toolbar-text" :class="{'is-active': formatOpen}" @tap="toggleFormatPanel">T</text>
			<text v-if="showComponent" class="cuIcon-add toolbar-button" @tap="$emit('component')"></text>
			<text class="cuIcon-back toolbar-button toolbar-history" :class="{'is-disabled': !canUndo}" @tap="undo"></text>
			<text class="cuIcon-forward toolbar-button toolbar-history" :class="{'is-disabled': !canRedo}" @tap="redo"></text>
		</view>
		<transition name="format-panel">
		<view v-if="formatOpen" class="rich-format-panel">
			<view class="format-row format-headings">
				<text :class="{'is-selected': activeBlock==='h1'}" @tap="setBlock(1)">H1</text>
				<text :class="{'is-selected': activeBlock==='h2'}" @tap="setBlock(2)">H2</text>
				<text :class="{'is-selected': activeBlock==='h3'}" @tap="setBlock(3)">H3</text>
				<text :class="{'is-selected': activeBlock==='paragraph'}" @tap="setBlock(false)">正文</text>
			</view>
			<view class="format-row">
				<text class="format-list" :class="{'is-selected': formats.list==='bullet'}" @tap="setFormat('list','bullet')">• 列表</text>
				<text class="format-list" :class="{'is-selected': formats.list==='ordered'}" @tap="setFormat('list','ordered')">1. 列表</text>
				<view class="format-align format-align-left" :class="{'is-selected': !formats.align || formats.align==='left'}" aria-label="左对齐" @tap="setFormat('align','left')"><text></text><text></text><text></text></view>
				<view class="format-align format-align-center" :class="{'is-selected': formats.align==='center'}" aria-label="居中对齐" @tap="setFormat('align','center')"><text></text><text></text><text></text></view>
				<view class="format-align format-align-right" :class="{'is-selected': formats.align==='right'}" aria-label="右对齐" @tap="setFormat('align','right')"><text></text><text></text><text></text></view>
			</view>
			<view class="format-row">
				<text class="format-color" :class="{'is-selected': colorPickerOpen || formats.color}" :style="formats.color ? {color: formats.color} : {}" @tap="toggleColorPicker">A 颜色</text>
				<text :class="{'is-selected': formats.bold}" @tap="toggleFormat('bold')">B</text>
				<text class="format-italic" :class="{'is-selected': formats.italic}" @tap="toggleFormat('italic')">I</text>
				<text class="format-underline" :class="{'is-selected': formats.underline}" @tap="toggleFormat('underline')">U</text>
				<text class="format-strike" :class="{'is-selected': formats.strike}" @tap="toggleFormat('strike')">S</text>
			</view>
			<view v-if="colorPickerOpen" class="format-color-picker">
				<text v-for="color in colors" :key="color" class="format-color-swatch" :style="{backgroundColor: color}" @tap="applyColor(color)"></text>
			</view>
		</view>
		</transition>
	</view>
</template>

<script>
	import { containsMarkdownFormatting, deltaHasFormatting, deltaToRichContent, plainText, renderRichContent } from '@/utils/richContent.js'
	export default {
		name: 'richComposer',
		props: {
			value: { type: String, default: '' }, placeholder: { type: String, default: '' }, maxlength: { type: Number, default: 5000 },
			focus: { type: Boolean, default: false }, night: { type: Boolean, default: false }, showStatus: { type: Boolean, default: true },
			showComponent: { type: Boolean, default: false }, showMedia: { type: Boolean, default: true }, autoHeight: { type: Boolean, default: false }, status: { type: String, default: '' }
		},
		data() { return { editorContext: null, editorReady: false, applyingContents: false, internalValue: this.value || '', formatOpen: false, colorPickerOpen: false, emojiActive: false, activeBlock: 'paragraph', formats: {}, canUndo: false, canRedo: false, colors: ['#d94841','#dc7d22','#b68b10','#20845f','#167a9e','#3158b8','#7046b5','#b13f75','#263934','#66756f'] } },
		computed: {
			textLength() { return plainText(this.internalValue).length }
		},
		watch: {
			value(next) {
				if (next === this.internalValue) return
				this.internalValue = String(next || '')
				this.setEditorContents(this.internalValue)
			}
		},
		methods: {
			toggleFormatPanel() {
				this.formatOpen = !this.formatOpen
				if (!this.formatOpen) this.colorPickerOpen = false
				this.emojiActive = false
				this.$emit('format-toggle', this.formatOpen)
			},
			closeFormatPanel() {
				this.formatOpen = false
				this.colorPickerOpen = false
				this.emojiActive = false
			},
			setEmojiActive(active) { this.emojiActive = Boolean(active) },
			insertText(text) {
				const value = String(text || '')
				if (!value || !this.editorContext) return false
				this.editorContext.insertText({
					text: value,
					complete: () => { this.$nextTick(() => { this.editorContext && this.editorContext.focus() }) }
				})
				return true
			},
			onEditorReady() {
				uni.createSelectorQuery().in(this).select('#rich-editor').context(result => {
					this.editorContext = result && result.context
					this.editorReady = Boolean(this.editorContext)
					this.setEditorContents(this.internalValue)
					if (this.focus && this.editorContext) this.editorContext.focus()
				}).exec()
			},
			setEditorContents(value) {
				if (!this.editorReady || !this.editorContext) return
				this.applyingContents = true
				this.editorContext.setContents({
					html: renderRichContent(value),
					complete: () => { this.$nextTick(() => { this.applyingContents = false }) }
				})
			},
			onEditorInput(event) {
				if (this.applyingContents) return
				const detail = event && event.detail ? event.detail : {}
				const rawText = String(detail.text || '').replace(/\n$/, '')
				if (!deltaHasFormatting(detail.delta) && containsMarkdownFormatting(rawText)) {
					this.internalValue = rawText
					this.$emit('input', rawText)
					this.setEditorContents(rawText)
					return
				}
				const next = deltaToRichContent(detail.delta)
				if (plainText(next).length > this.maxlength) {
					this.setEditorContents(this.internalValue)
					uni.showToast({ title: '最多输入' + this.maxlength + '个字', icon: 'none' })
					return
				}
				this.internalValue = next
				this.canUndo = true
				this.canRedo = false
				this.$emit('input', next)
			},
			onStatusChange(event) {
				const detail = event && event.detail ? event.detail : {}
				this.formats = Object.assign({}, detail.formats || detail)
				const header = this.formats.header
				this.activeBlock = header === 1 || header === '1' ? 'h1' : header === 2 || header === '2' ? 'h2' : header === 3 || header === '3' ? 'h3' : 'paragraph'
			},
			setFormat(name, value) {
				if (!this.editorContext) return
				this.editorContext.format(name, value)
			},
			toggleFormat(name) {
				this.setFormat(name, this.formats[name] ? false : true)
			},
			setBlock(level) {
				this.setFormat('header', level)
				this.activeBlock = level ? 'h' + level : 'paragraph'
			},
			toggleColorPicker() {
				this.colorPickerOpen = !this.colorPickerOpen
			},
			applyColor(color) {
				this.colorPickerOpen = false
				this.setFormat('color', color)
			},
			undo() {
				if (!this.editorContext || !this.canUndo) return
				this.editorContext.undo()
				this.canUndo = false
				this.canRedo = true
			},
			redo() {
				if (!this.editorContext || !this.canRedo) return
				this.editorContext.redo()
				this.canUndo = true
				this.canRedo = false
			}
		}
	}
</script>

<style scoped>
	.rich-composer { border-bottom: 1rpx solid #e5ece9; background: #fff; }
	.rich-composer-input { display:block; width:100%; min-height:300rpx; padding:28rpx 0 16rpx; box-sizing:border-box; color:#263934; font-size:31rpx; line-height:1.72; }
	.rich-composer-input /deep/ .ql-container { min-height:300rpx; font-size:31rpx; line-height:1.72; }
	.rich-composer-input /deep/ .ql-editor { min-height:300rpx; padding:0; color:#263934; line-height:1.72; }
	.rich-composer-input /deep/ .ql-editor.ql-blank::before { left:0; color:#89958f; font-style:normal; }
	.rich-composer-status { display:flex; justify-content:space-between; padding:10rpx 0 14rpx; color:#7e9089; font-size:21rpx; }
	.rich-composer-toolbar { display:flex; align-items:center; gap:42rpx; min-height:76rpx; border-top:1rpx solid #e5ece9; }
	.toolbar-button { min-width:34rpx; color:#24332f; font-size:32rpx; text-align:center; }
	.toolbar-text { font-family:serif; font-size:38rpx; }
	.toolbar-button.is-active { color:#237c74; }
	.toolbar-history { color:#99a6a1; }
	.toolbar-history.is-disabled { opacity:.36; }
	.rich-format-panel { margin:0 -24rpx; padding:16rpx 24rpx 22rpx; border-top:1rpx solid #e5ece9; background:#f6f8f7; }
	.format-panel-enter-active,.format-panel-leave-active { overflow:hidden; transform-origin:top center; transition:max-height .24s cubic-bezier(.22,.78,.25,1), opacity .18s ease, transform .24s cubic-bezier(.22,.78,.25,1); }
	.format-panel-enter,.format-panel-leave-to { max-height:0; opacity:0; transform:translateY(-10rpx); }
	.format-panel-enter-to,.format-panel-leave { max-height:420rpx; opacity:1; transform:translateY(0); }
	.format-row { display:grid; grid-template-columns:repeat(5,1fr); gap:8rpx; margin-top:12rpx; }
	.format-row:first-child { margin-top:0; }
	.format-row text { display:flex; align-items:center; justify-content:center; height:64rpx; border-radius:8rpx; background:#fff; color:#293733; font-size:27rpx; }
	.format-row text,.format-row .format-align { transition:background-color .16s ease, color .16s ease, transform .16s ease, box-shadow .16s ease; }
	.format-row text:active,.format-row .format-align:active { transform:scale(.96); }
	.format-row .format-align { display:flex; flex-direction:column; align-items:flex-start; justify-content:center; gap:5rpx; height:64rpx; padding:0 30rpx; box-sizing:border-box; border-radius:8rpx; background:#fff; }
	.format-row .format-align text { display:block; height:4rpx; padding:0; border-radius:3rpx; background:#1d3b55; }
	.format-align text:nth-child(1) { width:34rpx; }
	.format-align text:nth-child(2) { width:48rpx; }
	.format-align text:nth-child(3) { width:40rpx; }
	.format-align-center { align-items:center !important; }
	.format-align-right { align-items:flex-end !important; }
	.format-headings { grid-template-columns:repeat(4,1fr); }
	.format-row .is-selected { color:#287d70; background:#e7f3ee; }
	.format-row .is-selected { box-shadow:inset 0 0 0 2rpx rgba(40,125,112,.18); }
	.format-color { font-size:22rpx !important; color:#c94d4a !important; }
	.format-list { font-size:21rpx !important; }
	.format-color-picker { display:flex; flex-wrap:wrap; gap:18rpx; padding:18rpx 4rpx 0; }
	.format-color-swatch { width:42rpx; height:42rpx; border:4rpx solid #fff; border-radius:50%; box-shadow:0 0 0 1rpx #d2ddd8; }
	.format-italic { font-family:serif; font-style:italic; font-size:34rpx !important; }
	.format-underline { text-decoration:underline; }
	.format-strike { text-decoration:line-through; }
	.is-night { border-color:#2d4039; background:#18231f; }
	.is-night .rich-composer-input,.is-night .toolbar-button { color:#e5eeea; }
	.is-night .rich-composer-input /deep/ .ql-editor { color:#e5eeea; }
	.is-night .rich-composer-input /deep/ .ql-editor.ql-blank::before { color:#84928c; }
	.is-night .rich-composer-toolbar,.is-night .rich-format-panel { border-color:#2d4039; background:#151f1b; }
	.is-night .format-row text { background:#26342f; color:#dce8e2; }
	.is-night .format-row .format-align { background:#26342f; }
	.is-night .format-row .format-align text { background:#dce8e2; }
	.is-night .format-row .is-selected { background:#25483d; color:#9bd0bb; }
</style>
