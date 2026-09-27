import { copyText } from '@/utils/clipboard.js'

function encodeQuery(value) {
	return encodeURIComponent(value == null ? '' : String(value))
}

export function buildContentShareUrl(baseUrl, path, params) {
	var base = String(baseUrl || '').replace(/\/?$/, '/')
	var query = Object.keys(params || {}).filter(function(key) {
		return params[key] !== undefined && params[key] !== null && params[key] !== ''
	}).map(function(key) {
		return encodeQuery(key) + '=' + encodeQuery(params[key])
	}).join('&')
	return base + '#' + path + (query ? '?' + query : '')
}

export function shareSummary(value, limit) {
	var text = String(value || '')
		.replace(/!\[[^\]]*\]\([^)]*\)/g, '')
		.replace(/<[^>]+>/g, ' ')
		.replace(/[\[\]#>*_`~]/g, ' ')
		.replace(/\s+/g, ' ')
		.trim()
	return text.slice(0, limit || 120)
}

export function shareContent(options) {
	var data = options || {}
	var url = String(data.url || '')
	var title = String(data.title || '聊城一中论坛')
	var text = String(data.text || title)
	if (!url) return false
	var copyFallback = function() {
		if (typeof uni === 'undefined' || typeof uni.showActionSheet !== 'function') return copyText(url, '链接已复制')
		uni.showActionSheet({
			itemList: ['复制分享链接'],
			success: function(result) {
				if (result.tapIndex === 0) copyText(url, '已复制，可粘贴到微信或 QQ 分享')
			}
		})
		return true
	}

	// #ifdef APP-PLUS
	if (typeof plus !== 'undefined' && plus.share && typeof plus.share.sendWithSystem === 'function') {
		plus.share.sendWithSystem({
			type: 'text',
			content: title + '\n' + text,
			href: url
		}, function() {
			uni.showToast({ title: '已打开系统分享', icon: 'none' })
		}, function() {
			uni.showToast({ title: '分享已取消', icon: 'none' })
		})
		return true
	}
	if (typeof uni !== 'undefined' && typeof uni.shareWithSystem === 'function') {
		try {
			uni.shareWithSystem({
				href: url,
				title: title,
				summary: text,
				success: function() { uni.showToast({ title: '已打开系统分享', icon: 'none' }) },
				fail: function() { uni.showToast({ title: '分享已取消', icon: 'none' }) }
			})
			return true
		} catch (error) {
			return copyFallback()
		}
	}
	// #endif

	// #ifdef H5
	if (typeof navigator !== 'undefined' && typeof navigator.share === 'function') {
		try {
			var sharing = navigator.share({ title: title, text: text, url: url })
			if (sharing && typeof sharing.catch === 'function') {
				sharing.catch(function(error) {
					if (!error || error.name !== 'AbortError') copyFallback()
				})
			}
			return true
		} catch (error) {
			return copyFallback()
		}
	}
	// #endif

	return copyFallback()
}
