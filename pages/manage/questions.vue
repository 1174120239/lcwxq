<template>
	<view class="question-manage" :class="{'is-night': campusNight}">
		<view class="manage-nav" :style="{paddingTop: StatusBar + 'px'}">
			<view class="manage-nav-bar">
				<view class="manage-nav-button" @tap="back"><text class="cuIcon-back"></text></view>
				<text class="manage-nav-title">问答管理</text>
				<view class="manage-nav-button is-add" @tap="createQuestion"><text class="cuIcon-add"></text></view>
			</view>
		</view>

		<view class="manage-tools">
			<view class="manage-search"><text class="cuIcon-search"></text><input v-model="keyword" placeholder="搜索问题或话题" confirm-type="search" @confirm="reload"></input><text v-if="keyword" class="cuIcon-close" @tap="clearSearch"></text></view>
			<view class="manage-filter">
				<text :class="{'is-active': status===''}" @tap="setStatus('')">全部</text>
				<text :class="{'is-active': status==='1'}" @tap="setStatus('1')">已发布</text>
				<text :class="{'is-active': status==='0'}" @tap="setStatus('0')">已停用</text>
			</view>
		</view>

		<view class="question-list">
			<view class="question-row" v-for="item in list" :key="item.id">
				<view class="question-row-head">
					<view class="question-state" :class="{'is-disabled': item.status!=1}">{{item.status==1 ? '已发布' : '已停用'}}</view>
					<text v-if="item.recommended==1" class="question-recommended">推荐</text>
					<text class="question-sort">排序 {{item.sortOrder || 0}}</text>
				</view>
				<text class="question-title">{{item.title}}</text>
				<text v-if="item.description" class="question-description">{{item.description}}</text>
				<view class="question-meta"><text v-if="item.topic">{{item.topic}}</text><text>{{item.answerCount || 0}} 个回答</text></view>
				<view class="question-actions">
					<text @tap="preview(item)">查看</text>
					<text @tap="editQuestion(item)">编辑</text>
					<text :class="{'is-danger': item.status==1}" @tap="toggleStatus(item)">{{item.status==1 ? '停用' : '启用'}}</text>
				</view>
			</view>
		</view>
		<view class="manage-empty" v-if="!loading && list.length===0">暂无符合条件的问题</view>
		<view class="manage-more" v-if="list.length" @tap="loadMore">{{moreText}}</view>

		<view class="editor-mask" v-if="editorVisible" @tap="closeEditor">
			<view class="question-editor" @tap.stop>
				<view class="editor-head"><text @tap="closeEditor">取消</text><text class="editor-title">{{form.id ? '编辑问题' : '新增问题'}}</text><text class="editor-save" :class="{'is-disabled': !canSave}" @tap="saveQuestion">保存</text></view>
				<scroll-view class="editor-body" scroll-y>
					<view class="form-item"><text class="form-label">问题标题</text><textarea v-model="form.title" maxlength="160" placeholder="用一句话把问题说清楚"></textarea><text class="form-count">{{form.title.length}}/160</text></view>
					<view class="form-item"><text class="form-label">问题说明</text><textarea class="is-tall" v-model="form.description" maxlength="5000" placeholder="补充背景、范围或需要回答的重点"></textarea><text class="form-count">{{form.description.length}}/5000</text></view>
					<view class="form-item"><text class="form-label">话题</text><input v-model="form.topic" maxlength="80" placeholder="例如：校园生活"></input></view>
					<view class="form-item"><text class="form-label">封面地址（可选）</text><input v-model="form.coverUrl" maxlength="500" placeholder="https://..."></input></view>
					<view class="form-item form-inline"><text class="form-label">排序</text><input class="sort-input" v-model="form.sortOrder" type="number"></input></view>
					<view class="form-item form-inline"><view><text class="form-label">首页推荐</text><text class="form-hint">推荐问题优先出现在主页</text></view><switch :checked="form.recommended==1" color="#168c80" @change="form.recommended=$event.detail.value?1:0"></switch></view>
					<view class="form-item form-inline"><view><text class="form-label">发布状态</text><text class="form-hint">停用后普通用户不可查看</text></view><switch :checked="form.status==1" color="#168c80" @change="form.status=$event.detail.value?1:0"></switch></view>
				</scroll-view>
			</view>
		</view>
	</view>
</template>

<script>
	import { localStorage } from '../../js_sdk/mp-storage/mp-storage/index.js'
	import { applyCampusThemeShell, getCampusThemeMode, isDongchangfuNight, resolveCampusNight } from '@/utils/campusTheme.js'

	const emptyForm = () => ({ id: 0, title: '', description: '', topic: '', coverUrl: '', sortOrder: 0, recommended: 0, status: 1 })

	export default {
		data() {
			return {
				StatusBar: this.StatusBar,
				token: '',
				campusThemeMode: 'auto',
				themeClock: Date.now(),
				keyword: '',
				status: '',
				list: [],
				page: 1,
				pageSize: 20,
				total: 0,
				loading: false,
				moreText: '加载更多',
				editorVisible: false,
				saving: false,
				form: emptyForm()
			}
		},
		computed: {
			campusNight() { return resolveCampusNight(this.campusThemeMode, isDongchangfuNight(this.themeClock)); },
			canSave() { return !this.saving && this.form.title.trim().length >= 4; }
		},
		onLoad() {
			this.token = localStorage.getItem('token') || '';
			this.loadTheme();
			this.reload();
		},
		onShow() { this.loadTheme(); },
		methods: {
			loadTheme() { this.campusThemeMode = getCampusThemeMode(); this.themeClock = Date.now(); applyCampusThemeShell(this.campusThemeMode, this.themeClock); },
			back() { uni.navigateBack({ delta: 1 }); },
			reload() { this.page = 1; this.loadList(false); },
			loadMore() { if (this.list.length >= this.total || this.loading) return; this.loadList(true); },
			loadList(append) {
				if (this.loading) return;
				this.loading = true;
				var targetPage = append ? this.page + 1 : 1;
				this.$Net.request({
					url: this.$API.qaQuestionManage(),
					data: { token: this.token, keyword: this.keyword.trim(), status: this.status, page: targetPage, limit: this.pageSize },
					method: 'get', dataType: 'json',
					success: (res) => {
						if (res.data && res.data.code == 1) {
							var rows = Array.isArray(res.data.data) ? res.data.data : [];
							this.list = append ? this.list.concat(rows) : rows;
							this.total = Number(res.data.total || 0);
							if (append && rows.length) this.page = targetPage;
							this.moreText = this.list.length < this.total ? '加载更多' : '已经到底了';
						} else uni.showToast({ title: res.data.msg || '没有管理权限', icon: 'none' });
					},
					fail: () => uni.showToast({ title: '问题列表加载失败', icon: 'none' }),
					complete: () => { this.loading = false; }
				})
			},
			setStatus(status) { if (this.status === status) return; this.status = status; this.reload(); },
			clearSearch() { this.keyword = ''; this.reload(); },
			createQuestion() { this.form = emptyForm(); this.editorVisible = true; },
			editQuestion(item) { this.form = Object.assign(emptyForm(), item); this.editorVisible = true; },
			closeEditor() { if (!this.saving) this.editorVisible = false; },
			saveQuestion() {
				if (!this.canSave) return;
				this.saving = true;
				var body = Object.assign({}, this.form, { sortOrder: Number(this.form.sortOrder || 0) });
				this.$Net.request({
					url: this.$API.qaQuestionSave(), data: { token: this.token, params: JSON.stringify(body) },
					header: { 'Content-Type': 'application/x-www-form-urlencoded' }, method: 'post', dataType: 'json',
					success: (res) => {
						if (res.data && res.data.code == 1) { this.editorVisible = false; uni.showToast({ title: '保存成功', icon: 'success' }); this.reload(); }
						else uni.showToast({ title: res.data.msg || '保存失败', icon: 'none' });
					},
					complete: () => { this.saving = false; }
				})
			},
			toggleStatus(item) {
				var next = item.status == 1 ? 0 : 1;
				uni.showModal({
					title: next ? '启用问题' : '停用问题', content: next ? '启用后用户可以查看和回答。' : '停用后已有回答会保留，但普通用户不可查看。',
					success: (choice) => {
						if (!choice.confirm) return;
						this.$Net.request({
							url: this.$API.qaQuestionStatus(), data: { token: this.token, id: item.id, status: next }, method: 'post', dataType: 'json',
							success: (res) => { if (res.data && res.data.code == 1) this.reload(); else uni.showToast({ title: res.data.msg || '操作失败', icon: 'none' }); }
						})
					}
				})
			},
			preview(item) { uni.navigateTo({ url: '/pages/qa/info?id=' + item.id }); }
		}
	}
</script>

<style scoped>
	.question-manage { min-height: 100vh; background: #f4f6f5; color: #24332f; }
	.manage-nav { background: rgba(255,255,255,.96); border-bottom: 1rpx solid #e6ebe8; }
	.manage-nav-bar { display: flex; align-items: center; justify-content: space-between; height: 88rpx; padding: 0 22rpx; }
	.manage-nav-button { display: flex; align-items: center; width: 64rpx; height: 64rpx; font-size: 34rpx; }
	.manage-nav-button.is-add { justify-content: flex-end; color: #168c80; }
	.manage-nav-title { font-size: 31rpx; font-weight: 600; }
	.manage-tools { padding: 24rpx; background: #fff; }
	.manage-search { display: flex; align-items: center; gap: 13rpx; height: 72rpx; padding: 0 22rpx; border-radius: 14rpx; background: #f1f4f3; color: #82908b; }
	.manage-search input { min-width: 0; flex: 1; font-size: 26rpx; }
	.manage-filter { display: flex; gap: 10rpx; margin-top: 18rpx; }
	.manage-filter text { padding: 10rpx 20rpx; border-radius: 10rpx; color: #65736f; font-size: 24rpx; }
	.manage-filter .is-active { background: #e5f3ef; color: #168c80; font-weight: 600; }
	.question-row { padding: 28rpx 28rpx 24rpx; border-bottom: 12rpx solid #f4f6f5; background: #fff; }
	.question-row-head { display: flex; align-items: center; gap: 12rpx; color: #86928e; font-size: 21rpx; }
	.question-state { color: #168c80; }
	.question-state.is-disabled { color: #a16c6c; }
	.question-recommended { color: #b1842a; }
	.question-sort { margin-left: auto; }
	.question-title { display: block; margin-top: 13rpx; font-size: 31rpx; font-weight: 600; line-height: 1.5; }
	.question-description { display: -webkit-box; overflow: hidden; margin-top: 9rpx; color: #61706b; font-size: 25rpx; line-height: 1.55; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
	.question-meta { display: flex; gap: 18rpx; margin-top: 14rpx; color: #88948f; font-size: 22rpx; }
	.question-actions { display: flex; gap: 34rpx; margin-top: 22rpx; color: #536761; font-size: 25rpx; }
	.question-actions .is-danger { color: #a46666; }
	.manage-empty,.manage-more { padding: 60rpx 0; color: #87938f; font-size: 24rpx; text-align: center; }
	.editor-mask { position: fixed; inset: 0; z-index: 100; display: flex; align-items: flex-end; background: rgba(15,22,20,.42); }
	.question-editor { width: 100%; height: 88vh; border-radius: 24rpx 24rpx 0 0; background: #fff; animation: editorUp .22s ease-out both; }
	.editor-head { display: flex; align-items: center; justify-content: space-between; height: 94rpx; padding: 0 28rpx; border-bottom: 1rpx solid #e7ece9; color: #6e7b77; font-size: 26rpx; }
	.editor-title { color: #283733; font-size: 29rpx; font-weight: 600; }
	.editor-save { color: #168c80; font-weight: 600; }
	.editor-save.is-disabled { color: #a7b0ad; }
	.editor-body { height: calc(88vh - 94rpx); padding: 0 28rpx calc(30rpx + env(safe-area-inset-bottom)); box-sizing: border-box; }
	.form-item { position: relative; padding: 26rpx 0; border-bottom: 1rpx solid #e8ecea; }
	.form-label { display: block; margin-bottom: 14rpx; font-size: 26rpx; font-weight: 600; }
	.form-item textarea { width: 100%; height: 120rpx; font-size: 28rpx; line-height: 1.55; }
	.form-item textarea.is-tall { height: 210rpx; }
	.form-item input { height: 58rpx; font-size: 27rpx; }
	.form-count { position: absolute; right: 0; bottom: 16rpx; color: #929d99; font-size: 20rpx; }
	.form-inline { display: flex; align-items: center; justify-content: space-between; }
	.form-inline .form-label { margin-bottom: 0; }
	.form-hint { display: block; margin-top: 7rpx; color: #89948f; font-size: 22rpx; }
	.sort-input { width: 180rpx; text-align: right; }
	.is-night { background: #151b19; color: #edf2ef; }
	.is-night .manage-nav,.is-night .manage-tools,.is-night .question-row,.is-night .question-editor { border-color: #303a37; background: #1d2523; }
	.is-night .manage-search { background: #29322f; }
	.is-night .question-row { border-bottom-color: #151b19; }
	.is-night .question-title,.is-night .editor-title { color: #edf2ef; }
	.is-night .question-description { color: #b2bfba; }
	.is-night .manage-filter .is-active { background: #2d403a; color: #8dd0c2; }
	.is-night .editor-head,.is-night .form-item { border-color: #303a37; }
	@keyframes editorUp { from { transform: translateY(100%); } to { transform: translateY(0); } }
</style>
