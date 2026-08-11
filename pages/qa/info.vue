<template>
	<view class="qa-page" :class="{'qa-night': campusNight}">
		<view class="qa-nav" :style="{paddingTop: StatusBar + 'px'}">
			<view class="qa-nav-bar">
				<view class="qa-nav-button" @tap="back"><text class="cuIcon-back"></text></view>
				<text class="qa-nav-title">校园问答</text>
				<view class="qa-nav-placeholder"></view>
			</view>
		</view>

		<view class="qa-question" v-if="question.id">
			<view class="qa-question-meta">
				<text class="qa-label">问答</text>
				<text v-if="question.topic" class="qa-topic">{{question.topic}}</text>
			</view>
			<text class="qa-question-title" user-select>{{question.title}}</text>
			<text v-if="question.description" class="qa-question-description" user-select>{{question.description}}</text>
			<image v-if="question.coverUrl" class="qa-question-cover" :src="question.coverUrl" mode="widthFix"></image>
		</view>

		<view class="qa-answer-toolbar" v-if="question.id">
			<text class="qa-answer-count">{{question.answerCount || answerTotal || 0}} 个回答</text>
			<view class="qa-sort">
				<text :class="{'is-active': sort==='hot'}" @tap="changeSort('hot')">热门</text>
				<text :class="{'is-active': sort==='latest'}" @tap="changeSort('latest')">最新</text>
			</view>
		</view>

		<view class="qa-answer-list">
			<view class="qa-answer" v-for="answer in answers" :key="answer.id">
				<view class="qa-author-row" @tap.stop>
					<campus-avatar class="qa-author-avatar round" :src="answer.userJson && answer.userJson.avatar" :name="answer.userJson && answer.userJson.name" @tap="openUser(answer.userJson)"></campus-avatar>
					<view class="qa-author-main" @tap="openUser(answer.userJson)">
						<text class="qa-author-name">{{answer.userJson && answer.userJson.name}}</text>
						<view class="qa-author-meta">
							<text v-if="answer.userJson && answer.userJson.campus">{{answer.userJson.campus}}</text>
							<text>{{displayTime(answer.created)}}</text>
						</view>
					</view>
				</view>
				<text class="qa-answer-text" :class="{'is-collapsed': isLongAnswer(answer) && !answer._expanded}" user-select @tap.stop="toggleAnswerExpanded(answer)">{{answer.text}}</text>
				<view class="qa-answer-expand" v-if="isLongAnswer(answer)" @tap.stop="toggleAnswerExpanded(answer)">
					<text>{{answer._expanded ? '收起' : '展开全文'}}</text><text :class="answer._expanded ? 'cuIcon-fold' : 'cuIcon-unfold'"></text>
				</view>
				<view class="qa-answer-actions" @tap.stop>
					<view class="qa-action" :class="{'is-liked': answer.isLiked==1}" @tap="toggleLike(answer)"><text class="cuIcon-appreciate"></text><text>{{answer.likes || '赞同'}}</text></view>
					<view class="qa-action" @tap="toggleComments(answer)"><text class="cuIcon-comment"></text><text>{{answer.commentCount || 0}} 条评论</text></view>
					<view class="qa-action" @tap="openComment(answer)"><text class="cuIcon-write"></text><text>评论</text></view>
					<view class="qa-action" v-if="canManageAnswer(answer)" @tap="editAnswer(answer)"><text>编辑</text></view>
					<view class="qa-action is-delete" v-if="canManageAnswer(answer)" @tap="deleteAnswer(answer)"><text>删除</text></view>
				</view>

				<view class="qa-comments" v-if="answer._commentsExpanded" @tap.stop>
					<view class="qa-comments-loading" v-if="answer._commentsLoading"><view class="campus-loader"></view></view>
					<view class="qa-comments-empty" v-else-if="answer._commentsLoaded && answer._comments.length===0">还没有评论</view>
					<qa-comment-thread v-else :items="answer._comments" :night="campusNight" :current-uid="uid" :group="group" @reply="replyComment(answer, $event)" @delete="deleteComment(answer, $event)" @user="openUser"></qa-comment-thread>
				</view>
			</view>
		</view>

		<view class="qa-empty" v-if="!loading && question.id && answers.length===0">
			<text class="cuIcon-edit"></text>
			<text>还没有回答，来写第一个回答吧</text>
		</view>
		<view class="qa-page-loading" v-if="loading"><view class="campus-loader"></view></view>
		<view class="qa-more" v-if="answers.length>0">{{moreText}}</view>

		<view class="qa-write-bar" v-if="question.id">
			<view class="qa-write-button" @tap="openAnswer"><text class="cuIcon-write"></text><text>写回答</text></view>
		</view>

		<view class="qa-composer-mask" v-if="composerVisible" @tap="closeComposer">
			<view class="qa-composer" @tap.stop>
				<view class="qa-composer-head">
					<text class="qa-composer-cancel" @tap="closeComposer">取消</text>
					<text class="qa-composer-title">{{composerTitle}}</text>
					<text class="qa-composer-send" :class="{'is-disabled': !canSubmit}" @tap="submitComposer">发送</text>
				</view>
				<textarea class="qa-composer-input" v-model="composerText" :focus="composerFocus" :maxlength="composerMode==='answer' || composerMode==='edit' ? 5000 : 1000" :placeholder="composerPlaceholder" :adjust-position="true" :cursor-spacing="24"></textarea>
				<view class="qa-composer-meta">
					<text v-if="composerMode==='answer' || composerMode==='edit'">{{composerText.trim().length < 4 ? '还需输入 ' + (4-composerText.trim().length) + ' 个字' : '内容会完整展示'}}</text>
					<text>{{composerText.length}}/{{composerMode==='answer' || composerMode==='edit' ? 5000 : 1000}}</text>
				</view>
			</view>
		</view>
	</view>
</template>

<script>
	import { localStorage } from '../../js_sdk/mp-storage/mp-storage/index.js'
	import { applyCampusThemeShell, getCampusThemeMode, isDongchangfuNight, resolveCampusNight } from '@/utils/campusTheme.js'

	export default {
		data() {
			return {
				StatusBar: this.StatusBar,
				id: 0,
				token: '',
				uid: 0,
				group: '',
				campusThemeMode: 'auto',
				themeClock: Date.now(),
				question: {},
				answers: [],
				answerTotal: 0,
				page: 1,
				pageSize: 10,
				sort: 'hot',
				loading: true,
				loadingMore: false,
				moreText: '',
				composerVisible: false,
				composerFocus: false,
				composerMode: 'answer',
				composerText: '',
				composerAnswer: null,
				composerParent: null,
				composerEditingId: 0,
				composerSubmitting: false
			}
		},
		computed: {
			campusNight() {
				return resolveCampusNight(this.campusThemeMode, isDongchangfuNight(this.themeClock));
			},
			composerTitle() {
				if (this.composerMode === 'edit') return '编辑回答';
				if (this.composerMode === 'comment') return '评论回答';
				if (this.composerMode === 'reply') return '回复 ' + (this.composerParent && this.composerParent.userJson ? this.composerParent.userJson.name : '评论');
				return '写回答';
			},
			composerPlaceholder() {
				return this.composerMode === 'answer' || this.composerMode === 'edit' ? '写下清楚、具体的回答' : '友善交流，说说你的看法';
			},
			canSubmit() {
				var minimum = this.composerMode === 'answer' || this.composerMode === 'edit' ? 4 : 1;
				return !this.composerSubmitting && this.composerText.trim().length >= minimum;
			}
		},
		onLoad(options) {
			this.id = Number(options.id || 0);
			this.loadIdentity();
			this.loadTheme();
			this.loadQuestion();
			this.loadAnswers(false);
		},
		onShow() {
			this.loadIdentity();
			this.loadTheme();
		},
		onPullDownRefresh() {
			this.loadQuestion();
			this.loadAnswers(false, () => uni.stopPullDownRefresh());
		},
		onReachBottom() {
			this.loadAnswers(true);
		},
		methods: {
			loadTheme() {
				this.campusThemeMode = getCampusThemeMode();
				this.themeClock = Date.now();
				applyCampusThemeShell(this.campusThemeMode, this.themeClock);
			},
			loadIdentity() {
				this.token = localStorage.getItem('token') || '';
				var user = {};
				try { user = JSON.parse(localStorage.getItem('userinfo') || '{}'); } catch (error) {}
				this.uid = Number(user.uid || 0);
				this.group = user.group || '';
			},
			back() {
				uni.navigateBack({ delta: 1 });
			},
			loadQuestion() {
				if (!this.id) return;
				this.$Net.request({
					url: this.$API.qaQuestionInfo(),
					data: { id: this.id, token: this.token },
					method: 'get',
					dataType: 'json',
					success: (res) => {
						if (res.data && res.data.code == 1) this.question = res.data.data || {};
						else this.showError(res.data && res.data.msg ? res.data.msg : '问题不存在');
					},
					fail: () => this.showError('问题加载失败')
				})
			},
			loadAnswers(append, complete) {
				if (!this.id || this.loadingMore) return;
				this.loadingMore = true;
				if (!append) {
					this.page = 1;
					this.loading = true;
				}
				var targetPage = append ? this.page + 1 : 1;
				this.$Net.request({
					url: this.$API.qaAnswerList(),
					data: { questionId: this.id, token: this.token, sort: this.sort, page: targetPage, limit: this.pageSize },
					method: 'get',
					dataType: 'json',
					success: (res) => {
						if (res.data && res.data.code == 1) {
							var list = (Array.isArray(res.data.data) ? res.data.data : []).map(this.prepareAnswer);
							this.answers = append ? this.answers.concat(list) : list;
							this.answerTotal = Number(res.data.total || 0);
							if (append && list.length) this.page = targetPage;
							this.moreText = this.answers.length < this.answerTotal ? '继续上滑加载' : (this.answers.length ? '已经到底了' : '');
						}
					},
					complete: () => {
						this.loading = false;
						this.loadingMore = false;
						if (complete) complete();
					}
				})
			},
			prepareAnswer(answer) {
				answer._comments = [];
				answer._commentsLoaded = false;
				answer._commentsLoading = false;
				answer._commentsExpanded = false;
				answer._expanded = false;
				return answer;
			},
			changeSort(sort) {
				if (this.sort === sort) return;
				this.sort = sort;
				this.loadAnswers(false);
			},
			requireLogin() {
				if (this.token) return true;
				uni.showToast({ title: '请先登录', icon: 'none' });
				setTimeout(() => uni.navigateTo({ url: '/pages/user/login' }), 500);
				return false;
			},
			openAnswer() {
				if (!this.requireLogin()) return;
				this.openComposer('answer', '', null, null, 0);
			},
			isLongAnswer(answer) {
				return !!(answer && String(answer.text || '').length > 220);
			},
			toggleAnswerExpanded(answer) {
				if (!this.isLongAnswer(answer)) return;
				this.$set(answer, '_expanded', !answer._expanded);
			},
			editAnswer(answer) {
				this.openComposer('edit', answer.text, answer, null, answer.id);
			},
			openComment(answer) {
				if (!this.requireLogin()) return;
				this.openComposer('comment', '', answer, null, 0);
			},
			replyComment(answer, comment) {
				if (!this.requireLogin()) return;
				this.openComposer('reply', '', answer, comment, 0);
			},
			openComposer(mode, text, answer, parent, editingId) {
				this.composerMode = mode;
				this.composerText = text || '';
				this.composerAnswer = answer;
				this.composerParent = parent;
				this.composerEditingId = editingId || 0;
				this.composerVisible = true;
				this.$nextTick(() => { this.composerFocus = true; });
			},
			closeComposer() {
				if (this.composerSubmitting) return;
				this.composerVisible = false;
				this.composerFocus = false;
			},
			submitComposer() {
				if (!this.canSubmit) return;
				this.composerSubmitting = true;
				var isAnswer = this.composerMode === 'answer' || this.composerMode === 'edit';
				var url = this.composerMode === 'answer' ? this.$API.qaAnswerAdd() : (this.composerMode === 'edit' ? this.$API.qaAnswerEdit() : this.$API.qaCommentAdd());
				var body = { text: this.composerText.trim() };
				if (this.composerMode === 'answer') body.questionId = this.id;
				if (this.composerMode === 'edit') body.id = this.composerEditingId;
				if (!isAnswer) {
					body.answerId = this.composerAnswer.id;
					body.parentId = this.composerParent ? this.composerParent.id : 0;
				}
				this.$Net.request({
					url: url,
					data: { token: this.token, params: JSON.stringify(body) },
					header: { 'Content-Type': 'application/x-www-form-urlencoded' },
					method: 'post',
					dataType: 'json',
					success: (res) => {
						if (!res.data || res.data.code != 1) {
							uni.showToast({ title: res.data && res.data.msg ? res.data.msg : '发送失败', icon: 'none' });
							return;
						}
						this.composerVisible = false;
						this.composerFocus = false;
						uni.showToast({ title: res.data.msg || '发送成功', icon: 'success' });
						if (isAnswer) {
							this.loadAnswers(false);
							this.loadQuestion();
						} else {
							this.$set(this.composerAnswer, 'commentCount', Number(this.composerAnswer.commentCount || 0) + 1);
							this.$set(this.composerAnswer, '_commentsExpanded', true);
							this.loadComments(this.composerAnswer, true);
						}
					},
					complete: () => { this.composerSubmitting = false; }
				})
			},
			toggleLike(answer) {
				if (!this.requireLogin() || answer._likeLoading) return;
				this.$set(answer, '_likeLoading', true);
				this.$Net.request({
					url: this.$API.qaAnswerLike(),
					data: { token: this.token, answerId: answer.id },
					method: 'post',
					dataType: 'json',
					success: (res) => {
						if (res.data && res.data.code == 1) {
							this.$set(answer, 'isLiked', res.data.data.isLiked);
							this.$set(answer, 'likes', res.data.data.likes);
						} else uni.showToast({ title: res.data.msg || '操作失败', icon: 'none' });
					},
					complete: () => this.$set(answer, '_likeLoading', false)
				})
			},
			toggleComments(answer) {
				this.$set(answer, '_commentsExpanded', !answer._commentsExpanded);
				if (answer._commentsExpanded && !answer._commentsLoaded) this.loadComments(answer, false);
			},
			loadComments(answer, force) {
				if (answer._commentsLoading) return;
				this.$set(answer, '_commentsLoading', true);
				this.$Net.request({
					url: this.$API.qaCommentList(),
					data: { answerId: answer.id, page: 1, limit: 30 },
					method: 'get',
					dataType: 'json',
					success: (res) => {
						if (res.data && res.data.code == 1) this.$set(answer, '_comments', Array.isArray(res.data.data) ? res.data.data : []);
					},
					complete: () => {
						this.$set(answer, '_commentsLoaded', true);
						this.$set(answer, '_commentsLoading', false);
					}
				})
			},
			deleteAnswer(answer) {
				uni.showModal({
					title: '删除回答',
					content: '删除后回答和讨论将不再展示。',
					success: (choice) => {
						if (!choice.confirm) return;
						this.$Net.request({
							url: this.$API.qaAnswerDelete(), data: { token: this.token, id: answer.id }, method: 'post', dataType: 'json',
							success: (res) => {
								if (res.data && res.data.code == 1) { this.loadAnswers(false); this.loadQuestion(); }
								else uni.showToast({ title: res.data.msg || '删除失败', icon: 'none' });
							}
						})
					}
				})
			},
			deleteComment(answer, comment) {
				uni.showModal({
					title: '删除评论', content: '确认删除这条评论吗？',
					success: (choice) => {
						if (!choice.confirm) return;
						this.$Net.request({
							url: this.$API.qaCommentDelete(), data: { token: this.token, id: comment.id }, method: 'post', dataType: 'json',
								success: (res) => {
									if (res.data && res.data.code == 1) {
										this.$set(answer, 'commentCount', Math.max(0, Number(answer.commentCount || 0) - Number(res.data.data || 1)));
									this.loadComments(answer, true);
								} else uni.showToast({ title: res.data.msg || '删除失败', icon: 'none' });
							}
						})
					}
				})
			},
			canManageAnswer(answer) {
				return answer.uid == this.uid || this.group === 'administrator' || this.group === 'editor';
			},
			openUser(user) {
				if (!user || !user.uid) {
					uni.showToast({ title: '用户不存在或已注销', icon: 'none' });
					return false;
				}
				var name = user.name || '用户';
				uni.navigateTo({
					url: '/pages/contents/userinfo?title=' + encodeURIComponent(name + '的信息')
						+ '&name=' + encodeURIComponent(name) + '&uid=' + user.uid
						+ '&avatar=' + encodeURIComponent(user.avatar || ''),
					fail: function(){
						uni.showToast({ title: '无法打开用户主页', icon: 'none' });
					}
				})
			},
			displayTime(timestamp) {
				var value = Number(timestamp || 0) * 1000;
				if (!value) return '';
				var diff = Math.max(0, Date.now() - value);
				if (diff < 60000) return '刚刚';
				if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前';
				if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前';
				var date = new Date(value);
				return date.getFullYear() + '-' + (date.getMonth() + 1) + '-' + date.getDate();
			},
			showError(message) {
				uni.showToast({ title: message, icon: 'none' });
			}
		}
	}
</script>

<style scoped>
	.qa-page { min-height: 100vh; padding-bottom: calc(138rpx + env(safe-area-inset-bottom)); background: #f4f6f5; color: #20302d; }
	.qa-nav { background: rgba(255,255,255,.96); border-bottom: 1rpx solid #e8ecea; }
	.qa-nav-bar { display: flex; align-items: center; justify-content: space-between; height: 88rpx; padding: 0 24rpx; }
	.qa-nav-button,.qa-nav-placeholder { display: flex; align-items: center; width: 64rpx; height: 64rpx; font-size: 35rpx; }
	.qa-nav-title { font-size: 31rpx; font-weight: 600; }
	.qa-question { padding: 38rpx 32rpx 34rpx; background: #fff; border-bottom: 1rpx solid #e8ecea; }
	.qa-question-meta { display: flex; align-items: center; gap: 14rpx; margin-bottom: 17rpx; color: #77837f; font-size: 23rpx; }
	.qa-label { color: #168c80; font-weight: 600; }
	.qa-topic { padding: 4rpx 12rpx; border-radius: 8rpx; background: #eff5f3; color: #5f706b; }
	.qa-question-title { display: block; font-size: 42rpx; font-weight: 700; line-height: 1.35; white-space: pre-wrap; word-break: break-word; }
	.qa-question-description { display: block; margin-top: 22rpx; color: #56645f; font-size: 29rpx; line-height: 1.72; white-space: pre-wrap; word-break: break-word; }
	.qa-question-cover { width: 100%; margin-top: 26rpx; border-radius: 14rpx; background: #edf1ef; }
	.qa-answer-toolbar { display: flex; align-items: center; justify-content: space-between; padding: 28rpx 30rpx 18rpx; }
	.qa-answer-count { font-size: 29rpx; font-weight: 600; }
	.qa-sort { display: flex; align-items: center; gap: 8rpx; padding: 5rpx; border-radius: 12rpx; background: #e9eeec; }
	.qa-sort text { min-width: 68rpx; padding: 8rpx 12rpx; border-radius: 8rpx; color: #6d7a76; font-size: 23rpx; text-align: center; }
	.qa-sort .is-active { background: #fff; color: #168c80; font-weight: 600; }
	.qa-answer { padding: 30rpx 30rpx 28rpx; border-bottom: 12rpx solid #f4f6f5; background: #fff; transition: background-color .18s ease; }
	.qa-answer:active { background: #f7faf8; }
	.qa-author-row { display: flex; align-items: center; }
	.qa-author-avatar { flex: 0 0 72rpx; width: 72rpx; height: 72rpx; margin-right: 18rpx; }
	.qa-author-main { min-width: 0; flex: 1; }
	.qa-author-name { display: block; color: #30423d; font-size: 27rpx; font-weight: 600; }
	.qa-author-meta { display: flex; gap: 16rpx; margin-top: 5rpx; color: #8a9692; font-size: 22rpx; }
	.qa-answer-text { display: block; height: auto; margin-top: 24rpx; color: #1f2d29; font-size: 31rpx; line-height: 1.78; white-space: pre-wrap; word-break: break-word; }
	.qa-answer-text.is-collapsed { display: -webkit-box; overflow: hidden; text-overflow: ellipsis; -webkit-box-orient: vertical; -webkit-line-clamp: 5; }
	.qa-answer-expand { display: flex; align-items: center; gap: 6rpx; margin-top: 12rpx; color: #168c80; font-size: 24rpx; }
	.qa-answer-actions { display: flex; flex-wrap: wrap; align-items: center; gap: 28rpx; margin-top: 24rpx; color: #6f7c78; font-size: 24rpx; }
	.qa-action { display: flex; align-items: center; gap: 7rpx; min-height: 48rpx; }
	.qa-action.is-liked { color: #168c80; }
	.qa-action.is-delete { color: #aa6666; }
	.qa-comments { margin-top: 20rpx; padding: 18rpx 20rpx; border-radius: 14rpx; background: #f6f8f7; }
	.qa-comments-empty,.qa-comments-loading { padding: 24rpx 0; color: #87928e; font-size: 24rpx; text-align: center; }
	.qa-empty { display: flex; flex-direction: column; align-items: center; gap: 18rpx; padding: 100rpx 30rpx; color: #87928e; font-size: 26rpx; }
	.qa-empty .cuIcon-edit { font-size: 50rpx; }
	.qa-page-loading { display: flex; justify-content: center; padding: 90rpx 0; }
	.qa-more { padding: 28rpx 0; color: #8b9692; font-size: 23rpx; text-align: center; }
	.qa-write-bar { position: fixed; right: 0; bottom: 0; left: 0; z-index: 40; padding: 16rpx 28rpx calc(16rpx + env(safe-area-inset-bottom)); border-top: 1rpx solid #e4e9e7; background: rgba(255,255,255,.96); }
	.qa-write-button { display: flex; align-items: center; justify-content: center; gap: 12rpx; height: 78rpx; border-radius: 14rpx; background: #168c80; color: #fff; font-size: 28rpx; font-weight: 600; }
	.qa-composer-mask { position: fixed; inset: 0; z-index: 100; display: flex; align-items: flex-end; background: rgba(15,22,20,.42); }
	.qa-composer { width: 100%; padding: 0 26rpx calc(24rpx + env(safe-area-inset-bottom)); border-radius: 24rpx 24rpx 0 0; background: #fff; animation: composerUp .22s ease-out both; }
	.qa-composer-head { display: flex; align-items: center; justify-content: space-between; height: 92rpx; }
	.qa-composer-title { color: #273632; font-size: 29rpx; font-weight: 600; }
	.qa-composer-cancel { color: #76827e; font-size: 26rpx; }
	.qa-composer-send { color: #168c80; font-size: 26rpx; font-weight: 600; }
	.qa-composer-send.is-disabled { color: #a8b1ae; }
	.qa-composer-input { width: 100%; height: 310rpx; padding: 22rpx; border: 1rpx solid #dde4e1; border-radius: 14rpx; box-sizing: border-box; background: #f8faf9; color: #20302d; font-size: 29rpx; line-height: 1.65; }
	.qa-composer-meta { display: flex; justify-content: space-between; padding-top: 14rpx; color: #8b9692; font-size: 22rpx; }
	.qa-night { background: #151b19; color: #edf2ef; }
	.qa-night .qa-nav,.qa-night .qa-question,.qa-night .qa-answer,.qa-night .qa-write-bar { border-color: #303a37; background: #1d2523; }
	.qa-night .qa-question-description,.qa-night .qa-author-name { color: #b9c4c0; }
	.qa-night .qa-question-title,.qa-night .qa-answer-text,.qa-night .qa-nav-title { color: #edf2ef; }
	.qa-night .qa-topic { background: #2a3431; color: #a9b7b2; }
	.qa-night .qa-answer { border-bottom-color: #151b19; }
	.qa-night .qa-answer:active { background: #25302c; }
	.qa-night .qa-sort { background: #29322f; }
	.qa-night .qa-sort .is-active { background: #3a4541; color: #8ed2c4; }
	.qa-night .qa-comments { background: #252e2b; }
	.qa-night .qa-composer { background: #1d2523; }
	.qa-night .qa-composer-title,.qa-night .qa-composer-input { color: #edf2ef; }
	.qa-night .qa-composer-input { border-color: #3a4541; background: #252e2b; }
	.qa-night .qa-answer-expand { color: #8ed2c4; }
	@keyframes composerUp { from { transform: translateY(100%); } to { transform: translateY(0); } }
</style>
