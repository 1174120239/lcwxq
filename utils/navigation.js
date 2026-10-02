/*
 * Shared navigation rules for the uni-app client.
 *
 * Pages may still provide their own back method for unsaved forms or a
 * business-specific return target. Ordinary navigateBack calls are wrapped
 * here so a cold-start/deep-link page never gets stuck when its stack is empty.
 */

export const CAMPUS_HOME_TAB = '/pages/home/home'
export const CAMPUS_TAB_ROUTES = [

	CAMPUS_HOME_TAB,
	'/pages/home/square',
	'/pages/home/find',
	'/pages/home/user'
]

const FALLBACK_ROUTES = {
	'/pages/contents/info': '/pages/contents/contentlist',
	'/pages/contents/videoInfo': '/pages/contents/recommend',
	'/pages/contents/shopinfo': '/pages/contents/shop',
	'/pages/contents/shoptext': '/pages/contents/shop',
	'/pages/contents/rewardLog': '/pages/contents/shop',
	'/pages/contents/comments': '/pages/contents/contentlist',
	'/pages/contents/commentsadd': '/pages/contents/contentlist',
	'/pages/contents/userinfo': '/pages/home/square',
	'/pages/space/info': '/pages/home/square',
	'/pages/space/topics': '/pages/home/square',
	'/pages/space/post': '/pages/home/square',
	'/pages/space/reply': '/pages/home/square',
	'/pages/qa/info': '/pages/home/square',
	'/pages/qa/post': '/pages/home/square',
	'/pages/journal/index': CAMPUS_HOME_TAB,
	'/pages/journal/info': '/pages/journal/index',
	'/pages/journal/post': '/pages/journal/index',
	'/pages/journal/submissions': '/pages/journal/index',
	'/pages/user/login': CAMPUS_HOME_TAB,
	'/pages/user/register': CAMPUS_HOME_TAB,
	'/pages/user/foget': '/pages/user/login',
	'/pages/user/addshop': CAMPUS_HOME_TAB,
	'/pages/user/addshopinfo': '/pages/user/myshop',
	'/pages/user/myshop': '/pages/home/user',
	'/pages/user/inbox': '/pages/home/find',
	'/pages/user/userpost': '/pages/home/user',
	'/pages/user/usercomments': '/pages/home/user',
	'/pages/user/userlist': '/pages/home/user',
	'/pages/user/fanList': '/pages/home/user',
	'/pages/user/followList': '/pages/home/user',
	'/pages/user/useredit': '/pages/home/user',
	'/pages/user/setup': '/pages/home/user',
	'/pages/user/invitation': '/pages/home/user',
	'/pages/user/agreement': CAMPUS_HOME_TAB,
	'/pages/user/webview': CAMPUS_HOME_TAB,
	'/pages/chat/chat': '/pages/home/square',
	'/pages/home/tool': CAMPUS_HOME_TAB,
	'/pages/home/user': CAMPUS_HOME_TAB,
	'/pages/ads/home': '/pages/home/user',
	'/pages/ads/adsPost': '/pages/ads/home',
	'/pages/ads/myAds': '/pages/ads/home'
}

let navigateBackInstalled = false
let nativeBackInFlight = false
let fallbackInFlight = false

export function normalizeRoute(route) {
	if (!route) return ''
	const value = String(route)
	return value.charAt(0) === '/' ? value : '/' + value
}

export function getCurrentPage() {
	const pages = typeof getCurrentPages === 'function' ? getCurrentPages() : []
	return pages && pages.length ? pages[pages.length - 1] : null
}

export function getCurrentRoute(page) {
	const current = page || getCurrentPage()
	return normalizeRoute(current && current.route)
}

function decodeSafely(value) {
	try {
		return decodeURIComponent(String(value || ''))
	} catch (error) {
		return String(value || '')
	}
}

function isSafeInternalUrl(value) {
	return /^\/pages\/[A-Za-z0-9_/-]+(?:\?[^#]*)?$/.test(value)
}

export function getPageOptions(page) {
	const current = page || getCurrentPage()
	return current && current.options ? current.options : {}
}

/* Only business flows that explicitly opt in may replace the current page. */
export function getExplicitReturnTarget(page) {
	const current = page || getCurrentPage()
	const route = getCurrentRoute(current)
	const options = getPageOptions(current)
	const raw = options && options.returnTo
	if (!raw) return ''
	if (raw === 'mutualAidList' && route === '/pages/contents/shopinfo') return '/pages/contents/shop'
	if (route !== '/pages/journal/article') return ''
	const decoded = decodeSafely(raw)
	return isSafeInternalUrl(decoded) ? decoded : ''
}

function targetForRoute(route) {
	if (route === '/pages/journal/article') {
		try {
			const journalId = Number(uni.getStorageSync('journalArticleReturnJournalId') || 0)
			return journalId > 0 ? '/pages/journal/info?id=' + journalId : '/pages/journal/index'
		} catch (error) {
			return '/pages/journal/index'
		}
	}
	if (FALLBACK_ROUTES[route]) return FALLBACK_ROUTES[route]
	if (route.indexOf('/pages/manage/') === 0) return '/pages/home/user'
	if (route.indexOf('/pages/user/') === 0) return '/pages/home/user'
	if (route.indexOf('/pages/plugins/sy_appbox/') === 0) return '/pages/plugins/sy_appbox/home'
	if (route.indexOf('/pages/plugins/') === 0) return CAMPUS_HOME_TAB
	if (route.indexOf('/pages/home/') === 0) return CAMPUS_HOME_TAB
	return CAMPUS_HOME_TAB
}

export function getFallbackUrl(page) {
	const current = page || getCurrentPage()
	const route = getCurrentRoute(current)
	const explicit = getExplicitReturnTarget(current)
	if (explicit) return explicit
	if (CAMPUS_TAB_ROUTES.indexOf(route) >= 0) {
		return route === CAMPUS_HOME_TAB ? '' : CAMPUS_HOME_TAB
	}
	return targetForRoute(route)
}

function isTabUrl(url) {
	return CAMPUS_TAB_ROUTES.indexOf(normalizeRoute(String(url || '').split('?')[0])) >= 0
}

function navigateToFallback(url) {
	if (!url || fallbackInFlight) return false
	fallbackInFlight = true
	const finish = () => {
		setTimeout(() => { fallbackInFlight = false }, 450)
	}
	const fail = () => {
		try {
			uni.reLaunch({ url })
		} finally {
			finish()
		}
	}
	try {
		const callback = {
			success: finish,
			fail
		}
		if (isTabUrl(url)) uni.switchTab(Object.assign({ url }, callback))
		else uni.redirectTo(Object.assign({ url }, callback))
	} catch (error) {
		fail()
	}
	return true
}

export function navigateBack(options) {
	const input = typeof options === 'number' ? { delta: options } : (options || {})
	const config = Object.assign({}, input)
	if (typeof config.fail !== 'function') {
		config.fail = function() {
			navigateToFallback(getFallbackUrl())
		}
	}
	return uni.navigateBack(config)
}

function closeOpenPublishPanel(vm) {
	const refs = vm && vm.$refs ? vm.$refs : {}
	const panels = [refs.publishPanel, refs.tabbar && refs.tabbar.$refs && refs.tabbar.$refs.publishPanel]
	const panel = panels.find(item => item && (item.isOpen || item.isLeaving))
	if (!panel || typeof panel.closePanel !== 'function') return false
	panel.closePanel()
	return true
}

export function handleCampusBackButton() {
	if (nativeBackInFlight) return true
	nativeBackInFlight = true
	setTimeout(() => { nativeBackInFlight = false }, 450)

	const pages = typeof getCurrentPages === 'function' ? getCurrentPages() : []
	const page = pages && pages.length ? pages[pages.length - 1] : null
	const route = getCurrentRoute(page)
	const vm = page && page.$vm

	if (closeOpenPublishPanel(vm)) return true

	if (vm && typeof vm.handleBack === 'function') {
		vm.handleBack()
		return true
	}
	if (vm && typeof vm.back === 'function' && CAMPUS_TAB_ROUTES.indexOf(route) < 0) {
		vm.back()
		return true
	}

	if (CAMPUS_TAB_ROUTES.indexOf(route) >= 0 && pages.length <= 1) {
		if (route !== CAMPUS_HOME_TAB) navigateToFallback(CAMPUS_HOME_TAB)
		return true
	}

	if (pages.length > 1) {
		navigateBack({ delta: 1 })
		return true
	}

	const fallback = getFallbackUrl(page)
	if (fallback) navigateToFallback(fallback)
	return true
}

/* Add failure recovery to legacy page-level back buttons without rewriting all pages. */
export function installCampusNavigation() {
	if (navigateBackInstalled || typeof uni === 'undefined' || typeof uni.navigateBack !== 'function') return
	const original = uni.navigateBack
	try {
		uni.navigateBack = function(options) {
			const input = typeof options === 'number' ? { delta: options } : (options || {})
			const config = Object.assign({}, input)
			if (typeof config.fail !== 'function') {
				config.fail = function() {
					navigateToFallback(getFallbackUrl())
				}
			}
			return original.call(uni, config)
		}
		navigateBackInstalled = true
	} catch (error) {
		// Some runtimes expose a non-writable API object. Page methods still
		// work there; the App-plus physical-back dispatcher remains available.
	}
}
