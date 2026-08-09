const EMPTY_AVATAR_VALUES = {
	'': true,
	'null': true,
	'undefined': true,
	'none': true
};

export function normalizeAvatarUrl(value) {
	if (typeof value !== 'string') {
		return '';
	}

	var url = value.trim();
	if (EMPTY_AVATAR_VALUES[url.toLowerCase()]) {
		return '';
	}

	return url;
}

export function normalizeUser(user, fallbackName) {
	var normalized = user && typeof user === 'object'
		? Object.assign({}, user)
		: {};
	var name = typeof normalized.name === 'string' ? normalized.name.trim() : '';

	normalized.name = name || fallbackName || '已注销用户';
	normalized.avatar = normalizeAvatarUrl(normalized.avatar);
	normalized.isvip = Number(normalized.isvip) > 0 ? 1 : 0;
	normalized.uid = normalized.uid == null ? 0 : normalized.uid;

	return normalized;
}

export function avatarInitial(name) {
	var text = typeof name === 'string' ? name.trim() : '';
	if (!text) {
		return '';
	}

	return text.charAt(0).toUpperCase();
}
