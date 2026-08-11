export function copyText(value, message) {
	var text = value == null ? '' : String(value);
	if (!text) {
		return false;
	}
	uni.setClipboardData({
		data: text,
		success: function() {
			uni.showToast({ title: message || '已复制', icon: 'success' });
		},
		fail: function() {
			uni.showToast({ title: '复制失败', icon: 'none' });
		}
	});
	return true;
}
