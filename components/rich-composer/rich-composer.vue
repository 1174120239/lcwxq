<template>
	<view class="rich-composer" :class="{'is-night': night}">
		<textarea class="rich-composer-input" :value="value" :maxlength="maxlength" :placeholder="placeholder"
			:cursor="cursor" :focus="focus" :auto-height="autoHeight" :adjust-position="true" :cursor-spacing="24"
			@input="onInput" @cursor="onCursor" @selectionchange="onSelectionChange"></textarea>
		<view v-if="showStatus" class="rich-composer-status"><text>{{textLength}}/{{maxlength}}</text><text>{{status}}</text></view>
		<view class="rich-composer-toolbar">
			<text class="cuIcon-emoji toolbar-button" @tap="$emit('emoji')"></text>
			<text class="cuIcon-pic toolbar-button" @tap="$emit('media')"></text>
			<text class="toolbar-button toolbar-text" :class="{'is-active': formatOpen}" @tap="formatOpen=!formatOpen">T</text>
			<text v-if="showComponent" class="cuIcon-add toolbar-button" @tap="$emit('component')"></text>
			<text class="cuIcon-back toolbar-button toolbar-history" :class="{'is-disabled': historyIndex<=0}" @tap="undo"></text>
			<text class="cuIcon-forward toolbar-button toolbar-history" :class="{'is-disabled': historyIndex>=history.length-1}" @tap="redo"></text>
		</view>
		<view v-if="formatOpen" class="rich-format-panel">
			<view class="format-row format-headings">
				<text @tap="linePrefix('# ')">H1</text><text @tap="linePrefix('## ')">H2</text><text @tap="linePrefix('### ')">H3</text><text class="is-selected" @tap="linePrefix('')">正文</text>
			</view>
			<view class="format-row">
				<text class="cuIcon-list" @tap="linePrefix('- ')"></text><text class="cuIcon-list" @tap="linePrefix('1. ')"></text>
				<text class="cuIcon-sort" @tap="wrap('[align=left]','[/align]')"></text><text class="cuIcon-sort" @tap="wrap('[align=center]','[/align]')"></text><text class="cuIcon-sort" @tap="wrap('[align=right]','[/align]')"></text>
			</view>
			<view class="format-row">
				<text class="format-color" @tap="wrap('[color=#e05650]','[/color]')">A 颜色</text><text @tap="wrap('**','**')">B</text><text class="format-italic" @tap="wrap('*','*')">I</text><text class="format-underline" @tap="wrap('[u]','[/u]')">U</text><text class="format-strike" @tap="wrap('~~','~~')">S</text>
			</view>
		</view>
	</view>
</template>

<script>
	import { insertRichToken, plainText } from '@/utils/richContent.js'
	export default {
		name: 'richComposer',
		props: {
			value: { type: String, default: '' }, placeholder: { type: String, default: '' }, maxlength: { type: Number, default: 5000 },
			focus: { type: Boolean, default: false }, night: { type: Boolean, default: false }, showStatus: { type: Boolean, default: true },
			showComponent: { type: Boolean, default: false }, autoHeight: { type: Boolean, default: false }, status: { type: String, default: '' }
		},
		data() { return { cursor: 0, selectionStart: 0, selectionEnd: 0, formatOpen: false, history: [this.value || ''], historyIndex: 0 } },
		computed: { textLength() { return plainText(this.value).length } },
		watch: {
			value(next) {
				if (next === this.history[this.historyIndex]) return
				this.history = this.history.slice(0, this.historyIndex + 1).concat([next])
				if (this.history.length > 40) this.history.shift()
				this.historyIndex = this.history.length - 1
			}
		},
		methods: {
			onInput(event) { this.$emit('input', event.detail.value) },
			onCursor(event) {
				this.cursor = Number(event.detail.cursor) || 0
				if (this.selectionStart === this.selectionEnd) this.selectionStart = this.selectionEnd = this.cursor
			},
			onSelectionChange(event) {
				const detail = event && event.detail ? event.detail : {}
				this.selectionStart = Math.max(0, Number(detail.selectionStart == null ? detail.start : detail.selectionStart) || 0)
				this.selectionEnd = Math.max(this.selectionStart, Number(detail.selectionEnd == null ? detail.end : detail.selectionEnd) || this.selectionStart)
				this.cursor = this.selectionStart
			},
			apply(result) { this.cursor = result.cursor; this.$emit('input', result.value) },
			wrap(before, after) {
				const result = insertRichToken(this.value, this.selectionStart, before, after, this.selectionEnd)
				this.selectionStart = result.selectionStart
				this.selectionEnd = result.selectionEnd
				this.apply(result)
			},
			linePrefix(prefix) {
				const source = String(this.value || ''), cursor = Math.max(0, Math.min(this.selectionStart, source.length)), end = Math.max(cursor, Math.min(this.selectionEnd, source.length))
				const start = source.lastIndexOf('\n', cursor - 1) + 1
				const selectedEnd = source.indexOf('\n', end) < 0 ? source.length : source.indexOf('\n', end)
				const lines = source.slice(start, selectedEnd).split('\n').map(line => prefix + line.replace(/^(# |## |### |- |\d+\. )/, ''))
				const next = source.slice(0, start) + lines.join('\n') + source.slice(selectedEnd)
				this.selectionStart = start
				this.selectionEnd = start + lines.join('\n').length
				this.cursor = this.selectionEnd
				this.$emit('input', next)
			},
			undo() { if (this.historyIndex <= 0) return; this.historyIndex--; this.cursor = this.history[this.historyIndex].length; this.$emit('input', this.history[this.historyIndex]) },
			redo() { if (this.historyIndex >= this.history.length - 1) return; this.historyIndex++; this.cursor = this.history[this.historyIndex].length; this.$emit('input', this.history[this.historyIndex]) }
		}
	}
</script>

<style scoped>
	.rich-composer { border-bottom: 1rpx solid #e5ece9; background: #fff; }
	.rich-composer-input { display:block; width:100%; min-height:300rpx; padding:28rpx 0 16rpx; box-sizing:border-box; color:#263934; font-size:31rpx; line-height:1.72; }
	.rich-composer-status { display:flex; justify-content:space-between; padding:10rpx 0 14rpx; color:#7e9089; font-size:21rpx; }
	.rich-composer-toolbar { display:flex; align-items:center; gap:42rpx; min-height:76rpx; border-top:1rpx solid #e5ece9; }
	.toolbar-button { min-width:34rpx; color:#24332f; font-size:32rpx; text-align:center; }
	.toolbar-text { font-family:serif; font-size:38rpx; }
	.toolbar-button.is-active { color:#237c74; }
	.toolbar-history { color:#99a6a1; }
	.toolbar-history.is-disabled { opacity:.36; }
	.rich-format-panel { margin:0 -24rpx; padding:16rpx 24rpx 22rpx; border-top:1rpx solid #e5ece9; background:#f6f8f7; }
	.format-row { display:grid; grid-template-columns:repeat(5,1fr); gap:8rpx; margin-top:12rpx; }
	.format-row:first-child { margin-top:0; }
	.format-row text { display:flex; align-items:center; justify-content:center; height:64rpx; border-radius:8rpx; background:#fff; color:#293733; font-size:27rpx; }
	.format-headings { grid-template-columns:repeat(4,1fr); }
	.format-row .is-selected { color:#287d70; background:#e7f3ee; }
	.format-color { font-size:22rpx !important; color:#c94d4a !important; }
	.format-italic { font-family:serif; font-style:italic; font-size:34rpx !important; }
	.format-underline { text-decoration:underline; }
	.format-strike { text-decoration:line-through; }
	.is-night { border-color:#2d4039; background:#18231f; }
	.is-night .rich-composer-input,.is-night .toolbar-button { color:#e5eeea; }
	.is-night .rich-composer-toolbar,.is-night .rich-format-panel { border-color:#2d4039; background:#151f1b; }
	.is-night .format-row text { background:#26342f; color:#dce8e2; }
	.is-night .format-row .is-selected { background:#25483d; color:#9bd0bb; }
</style>
