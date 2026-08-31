const WGT_MANIFEST_URL = 'https://frp.lcxqy.cn/app-updates/update.json'
const APP_ID = '__UNI__850911F'

function isAndroidApp() {
	// #ifdef APP-PLUS
	return plus.os && plus.os.name && plus.os.name.toLowerCase() === 'android'
	// #endif
	return false
}

function getRuntimeInfo() {
	return new Promise((resolve, reject) => {
		plus.runtime.getProperty(plus.runtime.appid, (info) => {
			resolve({
				appid: plus.runtime.appid,
				isAndroid: isAndroidApp(),
				version: info.version || '',
				versionCode: Number(info.versionCode) || 0
			})
		})
	})
}

function requestManifest() {
	return new Promise((resolve, reject) => {
		uni.request({
			url: WGT_MANIFEST_URL + '?t=' + Date.now(),
			method: 'GET',
			dataType: 'json',
			timeout: 10000,
			success: (res) => {
				if (res.statusCode !== 200 || !res.data || typeof res.data !== 'object') {
					reject(new Error('WGT 更新清单不可用'))
					return
				}
				resolve(res.data)
			},
			fail: reject
		})
	})
}

function normalizeManifest(raw) {
	const data = raw || {}
	const versionCode = Number(data.versionCode)
	const wgtUrl = typeof data.wgtUrl === 'string' ? data.wgtUrl.trim() : ''
	const appid = data.appid || data.appId || APP_ID
	const platform = String(data.platform || 'android').toLowerCase()
	return {
		appid,
		platform,
		version: data.version || data.versionName || '',
		versionCode,
		wgtUrl,
		description: data.description || data.versionIntro || '',
		force: data.force === true || data.force === 'true' || Number(data.force) === 1 ||
			data.qzgx === true || data.qzgx === 'true' || Number(data.qzgx) === 1
	}
}

export function checkAndroidWgtUpdate() {
	return getRuntimeInfo().then((runtime) => {
		if (!runtime.isAndroid) return { runtime, available: false, update: null }
		return requestManifest().then((raw) => {
			const update = normalizeManifest(raw)
			const available = update.appid === runtime.appid &&
				(update.platform === 'android' || update.platform === 'all') &&
				Number.isFinite(update.versionCode) && update.versionCode > runtime.versionCode &&
				/^https:\/\//i.test(update.wgtUrl)
			return { runtime, available, update }
		}, () => ({ runtime, available: false, update: null }))
	})
}

export function installAndroidWgt(update, onProgress, onStage) {
	return new Promise((resolve, reject) => {
		if (!isAndroidApp() || !update || !/^https:\/\//i.test(update.wgtUrl || '')) {
			reject(new Error('没有可安装的安卓 WGT 更新包'))
			return
		}
		let lastProgress = -1
		let lastProgressAt = 0
		const reportProgress = (value, force) => {
			if (typeof onProgress !== 'function') return
			const progress = Math.max(0, Math.min(100, Number(value) || 0))
			const now = Date.now()
			if (!force && progress < 100 && now - lastProgressAt < 120) return
			if (!force && progress === lastProgress) return
			lastProgress = progress
			lastProgressAt = now
			onProgress(progress)
		}
		const reportStage = (stage) => {
			if (typeof onStage === 'function') onStage(stage)
		}
		reportStage('downloading')
		const task = uni.downloadFile({
			url: update.wgtUrl,
			timeout: 120000,
			success: (res) => {
				if (res.statusCode !== 200 || !res.tempFilePath) {
					reject(new Error('WGT 下载失败'))
					return
				}
				reportProgress(100, true)
				reportStage('installing')
				plus.runtime.install(res.tempFilePath, { force: false }, () => {
					resolve()
					setTimeout(() => plus.runtime.restart(), 420)
				}, (error) => {
					reject(new Error('WGT 安装失败：' + (error && error.message ? error.message : '文件可能损坏')))
				})
			},
			fail: () => reject(new Error('WGT 下载失败，请检查网络'))
		})
		if (task && typeof task.onProgressUpdate === 'function' && typeof onProgress === 'function') {
			task.onProgressUpdate((event) => reportProgress(event.progress, false))
		}
	})
}

export const APP_WGT_MANIFEST_URL = WGT_MANIFEST_URL
