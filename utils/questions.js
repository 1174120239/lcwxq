export function shuffleQuestions(items) {
	const result = Array.isArray(items) ? items.slice() : []
	for (let index = result.length - 1; index > 0; index--) {
		const target = Math.floor(Math.random() * (index + 1))
		const current = result[index]
		result[index] = result[target]
		result[target] = current
	}
	return result
}
