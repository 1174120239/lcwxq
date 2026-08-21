import marked from '@/components/mp-html/markdown/marked.min.js'

const COLOR_TOKEN = /\[color=(#[0-9a-fA-F]{6})\]([\s\S]*?)\[\/color\]/g
const ALIGN_TOKEN = /\[align=(left|center|right)\]([\s\S]*?)\[\/align\]/g
const UNDERLINE_TOKEN = /\[u\]([\s\S]*?)\[\/u\]/g

export function plainText(value) {
	return String(value || '')
		.replace(/\[color=#[0-9a-fA-F]{6}\]|\[\/color\]|\[align=(?:left|center|right)\]|\[\/align\]|\[u\]|\[\/u\]/g, '')
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
	source = source.replace(COLOR_TOKEN, (all, color, text) => token('<span style="color:' + color + '">' + escape(text) + '</span>'))
	source = source.replace(ALIGN_TOKEN, (all, align, text) => token('<span style="display:block;text-align:' + align + '">' + escape(text) + '</span>'))
	source = source.replace(UNDERLINE_TOKEN, (all, text) => token('<span style="text-decoration:underline">' + escape(text) + '</span>'))
	source = source.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
	let html = marked(source, { breaks: true })
	tokens.forEach((item) => {
		const escaped = item.id.replace(/_/g, '_')
		html = html.split(escaped).join(item.html)
	})
	return html.replace(/href="javascript:[^"]*"/gi, 'href=""')
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
