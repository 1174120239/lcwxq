function toVersionCode(value) {
	var normalized = String(value == null ? '' : value).trim()
	if (!/^\d+$/.test(normalized)) return 0
	return parseInt(normalized, 10)
}

function normalizeUpdate(payload, installedVersionCode) {
	payload = payload && typeof payload === 'object' ? payload : {}
	var versionCode = toVersionCode(payload.versionCode)
	return {
		needUpdate: versionCode > toVersionCode(installedVersionCode),
		force: Number(payload.qzgx) === 1 || Number(payload.force) === 1,
		versionCode: versionCode,
		version: String(payload.version || '').trim(),
		intro: String(payload.versionIntro || '').trim(),
		url: String(payload.versionUrl || '').trim()
	}
}

module.exports = {
	normalizeUpdate: normalizeUpdate,
	toVersionCode: toVersionCode
}
