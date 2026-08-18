export const QIXI_EASTER_EGG_DATE = Object.freeze({
	year: 2026,
	month: 8,
	day: 19
})

export function isQixiEasterEggDate(value = new Date()) {
	const date = value instanceof Date ? value : new Date(value)
	if (Number.isNaN(date.getTime())) return false

	return date.getFullYear() === QIXI_EASTER_EGG_DATE.year &&
		date.getMonth() + 1 === QIXI_EASTER_EGG_DATE.month &&
		date.getDate() === QIXI_EASTER_EGG_DATE.day
}
