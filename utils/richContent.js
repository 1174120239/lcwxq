import marked from '@/components/mp-html/markdown/marked.min.js'

const COLOR_OPEN_TOKEN = /\[color=(#[0-9a-fA-F]{6})\]/g
const ALIGN_OPEN_TOKEN = /\[align=(left|center|right)\]/g

function normalizeColor(value) {
	const color = String(value || '').trim()
	if (/^#[0-9a-fA-F]{6}$/.test(color)) return color.toLowerCase()
	if (/^#[0-9a-fA-F]{3}$/.test(color)) {
		return ('#' + color.slice(1).split('').map(item => item + item).join('')).toLowerCase()
	}
	const rgb = color.match(/^rgba?\(\s*(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*(\d{1,3})/i)
	if (!rgb) return ''
	const values = rgb.slice(1).map(item => Math.max(0, Math.min(255, Number(item))))
	return '#' + values.map(item => item.toString(16).padStart(2, '0')).join('')
}

function escapeMarkdownText(value) {
	return String(value || '').replace(/([\\`*_~#])/g, '\\$1')
}

function renderInline(text, attributes) {
	const attrs = attributes || {}
	let value = escapeMarkdownText(text)
	if (!value) return value
	if (attrs.bold) value = '**' + value + '**'
	if (attrs.italic) value = '*' + value + '*'
	if (attrs.underline) value = '[u]' + value + '[/u]'
	if (attrs.strike) value = '~~' + value + '~~'
	const color = normalizeColor(attrs.color)
	if (color) value = '[color=' + color + ']' + value + '[/color]'
	return value
}

function renderDeltaLine(parts, attributes) {
	const attrs = attributes || {}
	let value = parts.join('')
	if (attrs.header === 1 || attrs.header === '1') value = '# ' + value
	else if (attrs.header === 2 || attrs.header === '2') value = '## ' + value
	else if (attrs.header === 3 || attrs.header === '3') value = '### ' + value
	else if (attrs.list === 'ordered') value = '1. ' + value
	else if (attrs.list === 'bullet') value = '- ' + value
	if (attrs.align === 'center' || attrs.align === 'right') {
		value = '[align=' + attrs.align + ']' + value + '[/align]'
	}
	return value
}

export function plainText(value) {
	return String(value || '')
		.replace(/\[color=#[0-9a-fA-F]{6}\]|\[\/color\]|\[align=(?:left|center|right)\]|\[\/align\]|\[u\]|\[\/u\]/g, '')
		.replace(/\\([\\`*_~#])/g, '$1')
		.replace(/[#>*_~`\[\]]/g, '')
		.replace(/\s+/g, ' ')
		.trim()
}

// Only Markdown typed by the user is parsed. Raw HTML is escaped before parsing so a
// legacy client cannot turn a formatted post into script or event-handler markup.
export function renderRichContent(value, options) {
	let source = String(value || '')
	const escape = (text) => String(text || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
	const tokens = []
	const token = (html) => {
		const id = 'RICH_TOKEN_' + tokens.length + '_END'
		tokens.push({ id, html })
		return id
	}
	const emojiList = options && Array.isArray(options.emojiList) ? options.emojiList : []
	emojiList.forEach((emoji) => {
		if (emoji && emoji.data && emoji.icon) {
			source = source.split(emoji.data).join(token('<img src="/' + String(emoji.icon).replace(/^\/+/, '') + '" class="tImg" />'))
		}
	})
	source = source.replace(COLOR_OPEN_TOKEN, (all, color) => token('<span style="color:' + color + '">'))
	source = source.replace(/\[\/color\]/g, () => token('</span>'))
	source = source.replace(ALIGN_OPEN_TOKEN, (all, align) => token('<span style="display:block;text-align:' + align + '">'))
	source = source.replace(/\[\/align\]/g, () => token('</span>'))
	source = source.replace(/\[u\]/g, () => token('<span style="text-decoration:underline">'))
	source = source.replace(/\[\/u\]/g, () => token('</span>'))
	source = source.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
	let html = marked(source, { breaks: true })
	tokens.forEach((item) => {
		const escaped = item.id.replace(/_/g, '_')
		html = html.split(escaped).join(item.html)
	})
	return html.replace(/href="javascript:[^"]*"/gi, 'href=""')
}

// The uni-app editor exposes a Quill-compatible Delta. Keeping the conversion
// here lets the editor remain visual while the API payload stays Markdown.
export function deltaToRichContent(delta) {
	const operations = delta && Array.isArray(delta.ops) ? delta.ops : []
	const lines = []
	let parts = []
	let endedWithNewline = false
	operations.forEach((operation) => {
		if (typeof operation.insert !== 'string') return
		const chunks = operation.insert.split('\n')
		chunks.forEach((chunk, index) => {
			if (chunk) parts.push(renderInline(chunk, operation.attributes))
			if (index < chunks.length - 1) {
				lines.push(renderDeltaLine(parts, operation.attributes))
				parts = []
				endedWithNewline = true
			} else if (chunk) {
				endedWithNewline = false
			}
		})
	})
	if (parts.length || !endedWithNewline) lines.push(renderDeltaLine(parts, {}))
	if (endedWithNewline && lines.length && lines[lines.length - 1] === '') lines.pop()
	return lines.join('\n')
}

export function containsMarkdownFormatting(value) {
	const source = String(value || '').replace(/\r\n?/g, '\n')
	return /(^|\n)\s{0,3}(?:#{1,3}\s+|[-*+]\s+|\d+\.\s+)/.test(source) ||
		/(\*\*|__)[^\n]+\1|~~[^\n]+~~|\[u\][\s\S]+?\[\/u\]|\[color=#[0-9a-fA-F]{6}\][\s\S]+?\[\/color\]/.test(source)
}

export function deltaHasFormatting(delta) {
	const operations = delta && Array.isArray(delta.ops) ? delta.ops : []
	return operations.some(operation => operation.attributes && Object.keys(operation.attributes).length > 0)
}

export function insertRichToken(value, cursor, before, after, selectionEnd) {
	const source = String(value || '')
	const index = Math.max(0, Math.min(Number(cursor) || 0, source.length))
	const end = Math.max(index, Math.min(Number(selectionEnd == null ? index : selectionEnd) || index, source.length))
	const selected = source.slice(index, end)
	return {
		value: source.slice(0, index) + before + selected + after + source.slice(end),
		cursor: index + before.length + selected.length + after.length,
		selectionStart: index + before.length,
		selectionEnd: index + before.length + selected.length
	}
}
