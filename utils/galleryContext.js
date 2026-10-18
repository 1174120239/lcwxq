// 图集列表上下文：详情页左右滑跨图集时共享列表数据，避免重复请求。
const context = {
	tag: '',
	items: [],
	total: 0,
	page: 1
}

export function galleryContextSet(items, tag, total, page) {
	context.items = items || []
	context.tag = tag || ''
	context.total = Number(total) || 0
	context.page = Number(page) || 1
}

export function galleryContextAppend(items, total, page) {
	context.items = context.items.concat(items || [])
	if (total != null) context.total = Number(total) || 0
	if (page != null) context.page = Number(page) || 1
}

export function galleryContextGet() {
	return context
}

export function galleryContextIndexOf(id) {
	return context.items.findIndex(x => Number(x.id) === Number(id))
}
