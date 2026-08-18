import test from 'node:test'
import assert from 'node:assert/strict'

import { QIXI_EASTER_EGG_DATE, isQixiEasterEggDate } from '../../utils/qixiEasterEgg.js'

test('enables the easter egg for local Qixi day', () => {
	assert.deepEqual(QIXI_EASTER_EGG_DATE, { year: 2026, month: 8, day: 19 })
	assert.equal(isQixiEasterEggDate(new Date(2026, 7, 19, 0, 0, 0)), true)
	assert.equal(isQixiEasterEggDate(new Date(2026, 7, 19, 23, 59, 59)), true)
})

test('keeps the easter egg hidden outside the configured day', () => {
	assert.equal(isQixiEasterEggDate(new Date(2026, 7, 18, 23, 59, 59)), false)
	assert.equal(isQixiEasterEggDate(new Date(2026, 7, 20, 0, 0, 0)), false)
	assert.equal(isQixiEasterEggDate(new Date(2027, 7, 19, 12, 0, 0)), false)
})

test('rejects invalid date values', () => {
	assert.equal(isQixiEasterEggDate('not-a-date'), false)
})
