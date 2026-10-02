import test from 'node:test'
import assert from 'node:assert/strict'

import {
	CAMPUS_HOME_TAB,
	getExplicitReturnTarget,
	getFallbackUrl,
	handleCampusBackButton,
	normalizeRoute
} from '../../utils/navigation.js'

function page(route, options = {}, vm = {}) {
	return { route, options, $vm: vm }
}

test('normalizes routes and keeps explicit mutual-aid return scoped', () => {
	assert.equal(normalizeRoute('pages/home/home'), CAMPUS_HOME_TAB)
	assert.equal(getExplicitReturnTarget(page('pages/contents/shopinfo', { returnTo: 'mutualAidList' })), '/pages/contents/shop')
	assert.equal(getExplicitReturnTarget(page('pages/contents/shopinfo')), '')
	assert.equal(getExplicitReturnTarget(page('pages/contents/shopinfo', { returnTo: 'https://example.com' })), '')
})

test('uses module fallbacks when a page has no history', () => {
	assert.equal(getFallbackUrl(page('pages/contents/info')), '/pages/contents/contentlist')
	assert.equal(getFallbackUrl(page('pages/qa/info')), '/pages/home/square')
	assert.equal(getFallbackUrl(page('pages/user/assets')), '/pages/home/user')
	assert.equal(getFallbackUrl(page('pages/home/square')), CAMPUS_HOME_TAB)
	assert.equal(getFallbackUrl(page('pages/home/home')), '')
})

test('physical back delegates to the page business back handler once', () => {
	const calls = []
	globalThis.getCurrentPages = () => [page('pages/user/addshop', {}, {
		handleBack() {
			calls.push('handleBack')
		}
	})]
	globalThis.uni = {
		navigateBack() { calls.push('navigateBack') },
		switchTab() { calls.push('switchTab') },
		redirectTo() { calls.push('redirectTo') },
		reLaunch() { calls.push('reLaunch') }
	}

	handleCampusBackButton()
	assert.deepEqual(calls, ['handleBack'])
})
