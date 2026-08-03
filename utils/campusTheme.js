const CAMPUS_THEME_STORAGE_KEY = 'campusThemeMode'
const CAMPUS_THEME_EVENT = 'campusThemeModeChange'
const CAMPUS_THEME_RESOLVED_EVENT = 'campusThemeResolved'
const VALID_MODES = ['auto', 'day', 'night']

export function normalizeCampusThemeMode(mode) {
	return VALID_MODES.indexOf(mode) !== -1 ? mode : 'auto'
}

export function getCampusThemeMode() {
	try {
		return normalizeCampusThemeMode(uni.getStorageSync(CAMPUS_THEME_STORAGE_KEY))
	} catch (error) {
		return 'auto'
	}
}

export function setCampusThemeMode(mode) {
	const nextMode = normalizeCampusThemeMode(mode)
	try {
		uni.setStorageSync(CAMPUS_THEME_STORAGE_KEY, nextMode)
	} catch (error) {
		// The current page still receives the mode even when storage is unavailable.
	}
	return nextMode
}

export function resolveCampusNight(mode, automaticNight) {
	const normalizedMode = normalizeCampusThemeMode(mode)
	if (normalizedMode === 'night') return true
	if (normalizedMode === 'day') return false
	return Boolean(automaticNight)
}

export function getDongchangfuHour(time) {
	return (new Date(time || Date.now()).getUTCHours() + 8) % 24
}

export function isDongchangfuNight(time) {
	const hour = getDongchangfuHour(time)
	return hour >= 20 || hour < 5
}

export function applyCampusThemeShell(mode, time) {
	const nextMode = normalizeCampusThemeMode(mode)
	const night = resolveCampusNight(nextMode, isDongchangfuNight(time))
	const backgroundColor = night ? '#15191b' : '#f4f8f8'

	if (typeof document !== 'undefined') {
		const targets = [document.documentElement, document.body]
		targets.forEach((target) => {
			if (!target) return
			target.classList.toggle('campus-system-night', night)
			target.style.backgroundColor = backgroundColor
		})
	}

	try {
		uni.setBackgroundColor({
			backgroundColor: backgroundColor,
			backgroundColorTop: backgroundColor,
			backgroundColorBottom: backgroundColor,
			fail() {}
		})
	} catch (error) {
		// Some H5 and mini-program runtimes do not expose this API.
	}

	try {
		uni.setTabBarStyle({
			color: night ? '#929c99' : '#71817f',
			selectedColor: night ? '#45aa7c' : '#168cf0',
			backgroundColor: night ? '#1c2123' : '#fcfefe',
			borderStyle: night ? 'black' : 'white',
			fail() {}
		})
	} catch (error) {
		// App uses the custom dock and some subpages do not expose a native tab bar.
	}

	if (typeof plus !== 'undefined' && plus.webview) {
		const webview = plus.webview.currentWebview()
		if (webview) webview.setStyle({ background: backgroundColor })
	}

	try {
		uni.$emit(CAMPUS_THEME_RESOLVED_EVENT, night)
	} catch (error) {
		// The shell can be applied before the global event bus is ready.
	}

	return night
}

export { CAMPUS_THEME_EVENT, CAMPUS_THEME_RESOLVED_EVENT }
