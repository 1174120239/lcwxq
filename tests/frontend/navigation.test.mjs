import test from 'node:test'
import assert from 'node:assert/strict'

import {
	CAMPUS_HOME_TAB,
	getExplicitReturnTarget,
	getFallbackUrl,
	handleCampusBackPress,
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
	assert.equal(getFallbackUrl(page('pages/contents/shop')), CAMPUS_HOME_TAB)
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

	handleCampusBackPress(undefined, { from: 'backbutton' })
	assert.deepEqual(calls, ['handleBack'])
})

test('programmatic navigateBack is left to uni-app', () => {
	const vm = { back() { throw new Error('physical handler must not run') } }
	globalThis.getCurrentPages = () => [page('pages/contents/shopinfo', {}, vm)]
	assert.equal(handleCampusBackPress(vm, { from: 'navigateBack' }), false)
})

test('mutual-aid detail keeps an explicit list return on physical back', () => {
	const calls = []
	const vm = { back() { calls.push('detailBack') } }
	globalThis.getCurrentPages = () => [page('pages/contents/shopinfo', { returnTo: 'mutualAidList' }, vm)]
	globalThis.plus = undefined
	assert.equal(handleCampusBackPress(vm, { from: 'backbutton' }), true)
	assert.deepEqual(calls, ['detailBack'])
})

test('only the home root shows the exit prompt', () => {
	const calls = []
	globalThis.getCurrentPages = () => [page('pages/home/home', {}, {})]
	globalThis.uni = {
		showToast(options) { calls.push(options.title) },
		navigateBack() { calls.push('navigateBack') },
		switchTab() { calls.push('switchTab') },
		redirectTo() { calls.push('redirectTo') },
		reLaunch() { calls.push('reLaunch') }
	}
	globalThis.plus = undefined
	assert.equal(handleCampusBackPress(undefined, { from: 'backbutton' }), true)
	assert.deepEqual(calls, ['再按一次退出应用'])
	let quitCount = 0
	globalThis.plus = { runtime: { quit() { quitCount++ } } }
	assert.equal(handleCampusBackPress(undefined, { from: 'backbutton' }), true)
	assert.equal(quitCount, 1)

	globalThis.getCurrentPages = () => [page('pages/contents/shop', {}, { back() { calls.push('listBack') } })]
	assert.equal(handleCampusBackPress(undefined, { from: 'backbutton' }), true)
	assert.deepEqual(calls, ['再按一次退出应用', 'listBack'])
})
