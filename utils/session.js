import API from './api.js'
import Net from './net.js'
import { localStorage } from '@/js_sdk/mp-storage/mp-storage/index.js'

const SESSION_REFRESH_INTERVAL = 6 * 60 * 60 * 1000
let refreshInFlight = false
let lastRefreshAttempt = 0

function clearLocalSession() {
	localStorage.removeItem('userinfo')
	localStorage.removeItem('token')
}

function parseCachedUser() {
	try {
		return JSON.parse(localStorage.getItem('userinfo') || '{}')
	} catch (error) {
		return {}
	}
}

function isInvalidSessionResponse(body) {
	if (!body || body.code != 0) return false
	const message = String(body.msg || '')
	return message.indexOf('未登录') !== -1 || message.indexOf('Token验证失败') !== -1
}

export function refreshSession(options) {
	const token = localStorage.getItem('token')
	if (!token || refreshInFlight) return

	const now = Date.now()
	const force = options && options.force
	if (!force && now - lastRefreshAttempt < SESSION_REFRESH_INTERVAL) return

	refreshInFlight = true
	Net.request({
		url: API.userStatus(),
		data: { token: token },
		header: {
			'Content-Type': 'application/x-www-form-urlencoded'
		},
		method: 'get',
		dataType: 'json',
		success(res) {
			const body = res && res.data
			if (body && body.code == 1 && body.data) {
				lastRefreshAttempt = Date.now()
				const latestUser = Object.assign({}, parseCachedUser(), body.data, { token: token })
				localStorage.setItem('userinfo', JSON.stringify(latestUser))
				localStorage.setItem('token', token)
			} else if (isInvalidSessionResponse(body)) {
				lastRefreshAttempt = Date.now()
				clearLocalSession()
			}
		},
		complete() {
			refreshInFlight = false
		}
	})
}
