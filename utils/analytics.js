var API = require('./api')
var VISITOR_KEY = 'lcxqy.analytics.visitorId'

function visitorId() {
	var value = ''
	try { value = uni.getStorageSync(VISITOR_KEY) || '' } catch (e) {}
	if (!value) {
		value = Date.now().toString(36) + Math.random().toString(36).slice(2, 12)
		try { uni.setStorageSync(VISITOR_KEY, value) } catch (e) {}
	}
	return value
}

function platform() {
	// #ifdef APP-PLUS
	return plus && plus.os && plus.os.name ? String(plus.os.name).toLowerCase() : 'app'
	// #endif
	// #ifdef H5
	return 'h5'
	// #endif
	return 'app'
}

function appVersion() {
	// #ifdef APP-PLUS
	try { return plus.runtime.version || '' } catch (e) {}
	// #endif
	return ''
}

function token() {
	try {
		var raw = uni.getStorageSync('userinfo')
		if (raw && typeof raw === 'string') raw = JSON.parse(raw)
		return raw && raw.token ? raw.token : ''
	} catch (e) { return '' }
}

export function track(eventType, source, extra) {
	if (platform() === 'h5' || platform() === 'web') return
	var data = Object.assign({
		eventType: eventType,
		platform: platform(),
		appVersion: appVersion(),
		source: source || '',
		visitorId: visitorId(),
		token: token()
	}, extra || {})
	try {
		uni.request({ url: API.analyticsEvent(), method: 'POST', data: data, timeout: 2500 })
	} catch (e) {}
}

export function trackDownload(action, source, extra) {
	if (action === 'download') track('app_download_click', source || 'download', extra)
}

export function trackFirstAppLaunch(source) {
	if (platform() === 'h5' || platform() === 'web') return
	var key = 'lcxqy.analytics.firstAppLaunch'
	try {
		if (uni.getStorageSync(key)) return
		uni.setStorageSync(key, '1')
	} catch (e) {}
	track('app_first_launch', source || 'app')
}
