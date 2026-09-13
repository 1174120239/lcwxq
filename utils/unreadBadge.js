export const CAMPUS_UNREAD_EVENT = 'campus:unread-change'

const MESSAGE_TAB_INDEX = 2
let latestUnreadCount = 0
let requestVersion = 0
let lastRefreshAt = 0
let refreshInFlight = false
let refreshCallbacks = []

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
	refreshInFlight = false
	refreshCallbacks = []
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
		refreshInFlight = false
		refreshCallbacks = []
		lastRefreshAt = 0
		const count = clearUnreadBadge()
		if (typeof onSuccess === 'function') onSuccess(count)
		return
	}
	const now = Date.now()
	if (refreshInFlight) {
		if (typeof onSuccess === 'function') refreshCallbacks.push(onSuccess)
		return
	}
	if (now - lastRefreshAt < 1200) {
		if (typeof onSuccess === 'function') onSuccess(latestUnreadCount)
		return
	}
	lastRefreshAt = now
	refreshInFlight = true
	const callbacks = () => {
		const pending = refreshCallbacks.splice(0)
		pending.forEach((callback) => callback(latestUnreadCount))
		if (typeof onSuccess === 'function') onSuccess(latestUnreadCount)
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
			if (version !== requestVersion) {
				refreshInFlight = false
				refreshCallbacks = []
				return
			}
			if (res && res.data && res.data.code == 1) {
				const count = renderUnreadBadge(normalizeUnreadCount(res.data.data))
				refreshInFlight = false
				callbacks()
				return
			}
			if (res && res.data && res.data.code == 0) {
				const count = renderUnreadBadge(0)
				refreshInFlight = false
				callbacks()
			}
		},
		// A temporary network failure must not erase a known unread state.
		fail() {
			refreshInFlight = false
			refreshCallbacks = []
		},
		complete() {
			// Release the shared lock for malformed responses as well as network
			// failures; a later page activation must still be able to refresh.
			if (refreshInFlight) {
				refreshInFlight = false
				refreshCallbacks = []
			}
		}
	})
}
