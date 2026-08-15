export const CAMPUS_UNREAD_EVENT = 'campus:unread-change'

const MESSAGE_TAB_INDEX = 2
let latestUnreadCount = 0
let requestVersion = 0

export function normalizeUnreadCount(value) {
	const source = value && typeof value === 'object'
		? (value.count != null ? value.count : (value.unread != null ? value.unread : value.total))
		: value
	const count = Number(source)
	return Number.isFinite(count) && count > 0 ? Math.floor(count) : 0
}

function renderUnreadBadge(count) {
	latestUnreadCount = count
	uni.$emit(CAMPUS_UNREAD_EVENT, count)

	// App-plus hides the native tabBar and renders pages/components/tabBar.vue instead.
	// #ifndef APP-PLUS
	const method = count > 0 ? 'showTabBarRedDot' : 'hideTabBarRedDot'
	if (typeof uni[method] === 'function') {
		uni[method]({
			index: MESSAGE_TAB_INDEX,
			fail() {}
		})
	}
	// #endif

	return count
}

export function syncUnreadBadge(value) {
	requestVersion += 1
	return renderUnreadBadge(normalizeUnreadCount(value))
}

export function clearUnreadBadge() {
	return syncUnreadBadge(0)
}

export function getUnreadBadgeCount() {
	return latestUnreadCount
}

export function refreshUnreadBadge(context, token, onSuccess) {
	if (!token || !context || !context.$Net || !context.$API) {
		const count = clearUnreadBadge()
		if (typeof onSuccess === 'function') onSuccess(count)
		return
	}

	const version = ++requestVersion
	context.$Net.request({
		url: context.$API.unreadNum(),
		data: { token },
		header: {
			'Content-Type': 'application/x-www-form-urlencoded'
		},
		method: 'get',
		dataType: 'json',
		timeout: 15000,
		success(res) {
			if (version !== requestVersion) return
			if (res && res.data && res.data.code == 1) {
				const count = renderUnreadBadge(normalizeUnreadCount(res.data.data))
				if (typeof onSuccess === 'function') onSuccess(count)
				return
			}
			if (res && res.data && res.data.code == 0) {
				const count = renderUnreadBadge(0)
				if (typeof onSuccess === 'function') onSuccess(count)
			}
		},
		// A temporary network failure must not erase a known unread state.
		fail() {}
	})
}
