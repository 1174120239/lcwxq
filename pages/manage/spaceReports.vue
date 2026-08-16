<template>
	<view class="report-manage" :class="AppStyle">
		<view class="header" :style="[{height:CustomBar + 'px'}]">
			<view class="cu-bar bg-white" :style="{'height':CustomBar + 'px','padding-top':StatusBar + 'px'}">
				<view class="action" @tap="back"><text class="cuIcon-back"></text></view>
				<view class="content text-bold" :style="[{top:StatusBar + 'px'}]">动态风险审核</view>
				<view class="action" @tap="reload"><text class="cuIcon-refresh"></text></view>
			</view>
		</view>
		<view :style="[{padding:NavBar + 'px 0 0'}]"></view>

		<view class="review-tabs">
			<text :class="{'is-active':mode==='ai'}" @tap="setMode('ai')">AI 审核</text>
			<text :class="{'is-active':mode==='report'}" @tap="setMode('report')">用户举报</text>
		</view>
		<view class="ai-filters" v-if="mode==='ai'">
			<text :class="{'is-active':decision===''}" @tap="setDecision('')">全部</text>
			<text :class="{'is-active':decision==='rejected'}" @tap="setDecision('rejected')">风险</text>
			<text :class="{'is-active':decision==='approved'}" @tap="setDecision('approved')">通过</text>
			<text :class="{'is-active':decision==='error'}" @tap="setDecision('error')">异常</text>
		</view>

		<view class="report-summary">
			<text class="report-summary-title">{{mode==='ai' ? 'AI 审核记录' : '待处理举报'}}</text>
			<text class="report-summary-count">{{total}}</text>
		</view>

		<view class="report-list">
			<view class="report-item" v-for="item in list" :key="reviewKey(item)">
				<view class="report-head">
					<campus-avatar class="report-avatar round" :src="item.reporterJson && item.reporterJson.avatar" :name="item.reporterJson && item.reporterJson.name" @tap.stop="openUser(item.reporterJson)"></campus-avatar>
					<view class="report-head-main" @tap.stop="openUser(item.reporterJson)">
						<text class="report-user">{{item.reporterJson && item.reporterJson.name}}</text>
						<text class="report-time">{{formatDate(item.created)}}</text>
					</view>
					<text class="report-reason" :class="{'is-ai':item.source==='ai'}">{{item.source==='ai' ? aiDecisionText(item) + ' · ' : ''}}{{item.reason}}</text>
				</view>
				<text class="report-detail" v-if="item.detail">{{item.detail}}</text>
				<view class="ai-final-state" v-if="item.source==='ai'">
					<text>当前：{{spaceStatusText(item)}}</text>
					<text v-if="item.humanDecision">人工改判：{{humanDecisionText(item.humanDecision)}}</text>
					<text v-if="item.reviewNote">说明：{{item.reviewNote}}</text>
				</view>

				<view class="reported-space" v-if="item.spaceState==='visible' && item.spaceInfo" @tap="openSpace(item.spaceInfo.id)">
					<view class="reported-space-author">
						<campus-avatar class="reported-avatar round" :src="item.spaceInfo.userJson && item.spaceInfo.userJson.avatar" :name="item.spaceInfo.userJson && item.spaceInfo.userJson.name"></campus-avatar>
						<text>{{item.spaceInfo.userJson && item.spaceInfo.userJson.name}}</text>
					</view>
					<text class="reported-space-text">{{spacePreview(item.spaceInfo.text)}}</text>
					<text class="reported-space-link">查看动态 <text class="cuIcon-right"></text></text>
				</view>
				<view class="reported-space is-deleted" v-else>原动态已删除</view>

				<view class="report-actions">
					<button v-if="item.source==='ai' && item.status!=1" class="cu-btn line-green sm" @tap="approve(item)"><text class="cuIcon-check margin-right-xs"></text>公开动态</button>
					<button v-if="item.source==='ai' && (item.status==1 || item.status==2)" class="cu-btn line-red sm" @tap="hideSpace(item)"><text class="cuIcon-close margin-right-xs"></text>隐藏动态</button>
					<button v-if="item.source!=='ai'" class="cu-btn line-gray sm" @tap="dismiss(item)"><text class="cuIcon-close margin-right-xs"></text>驳回举报</button>
					<button v-if="item.source!=='ai'" class="cu-btn bg-red sm" @tap="deleteSpace(item)"><text class="cuIcon-delete margin-right-xs"></text>删除动态</button>
				</view>
			</view>
		</view>

		<view class="report-empty" v-if="!loading && list.length===0"><text class="cuIcon-check"></text><text>{{mode==='ai' ? '没有符合条件的 AI 审核记录' : '没有待处理举报'}}</text></view>
		<view class="report-more" v-if="list.length" @tap="loadMore">{{moreText}}</view>
	</view>
</template>

<script>
	import { localStorage } from '../../js_sdk/mp-storage/mp-storage/index.js'

	export default {
		data(){
			return {
				StatusBar: this.StatusBar,
				CustomBar: this.CustomBar,
				NavBar: this.StatusBar + this.CustomBar,
				AppStyle: this.$store.state.AppStyle,
				token: '',
				mode: 'ai',
				decision: '',
				list: [],
				page: 1,
				pageSize: 20,
				total: 0,
				loading: false,
				moreText: '加载更多'
			}
		},
		onLoad(){
			// #ifdef APP-PLUS || MP
			this.NavBar = this.CustomBar;
			// #endif
			this.token = localStorage.getItem('token') || '';
			this.reload();
		},
		onPullDownRefresh(){
			this.reload(() => uni.stopPullDownRefresh());
		},
		onReachBottom(){ this.loadMore(); },
		methods: {
			reviewKey(item){ return (item && item.source ? item.source : 'report') + '-' + String(item && item.id || 0); },
			back(){ uni.navigateBack({ delta: 1 }); },
			setMode(mode){ if(this.mode===mode) return; this.mode=mode; this.reload(); },
			setDecision(decision){ if(this.decision===decision) return; this.decision=decision; this.reload(); },
			reload(complete){ this.page = 1; this.loadList(false, complete); },
			loadMore(){
				if(this.loading || this.list.length >= this.total) return;
				this.loadList(true);
			},
			loadList(append,complete){
				if(this.loading) return;
				this.loading = true;
				var targetPage = append ? this.page + 1 : 1;
				var requestData = this.mode === 'ai'
					? { token: this.token, source: 'ai', decision: this.decision, page: targetPage, limit: this.pageSize }
					: { token: this.token, status: 0, page: targetPage, limit: this.pageSize };
				this.$Net.request({
					url: this.$API.spaceReportList(),
					data: requestData,
					method: 'get',
					dataType: 'json',
					success: (res) => {
						if(res.data && res.data.code == 1){
							var rows = Array.isArray(res.data.data) ? res.data.data : [];
							this.list = append ? this.list.concat(rows) : rows;
							this.total = Number(res.data.total || 0);
							if(append && rows.length) this.page = targetPage;
							this.moreText = this.list.length < this.total ? '加载更多' : '已经到底了';
						} else {
							uni.showToast({ title: res.data && res.data.msg ? res.data.msg : '没有审核权限', icon: 'none' });
						}
					},
					fail: () => uni.showToast({ title: '举报列表加载失败', icon: 'none' }),
					complete: () => {
						this.loading = false;
						if(complete) complete();
					}
				});
			},
			openSpace(id){ uni.navigateTo({ url: '/pages/space/info?id=' + id }); },
			openUser(user){
				if(!user || !user.uid){
					uni.showToast({ title: '用户不存在或已注销', icon: 'none' });
					return false;
				}
				var name = user.name || '用户';
				uni.navigateTo({
					url: '/pages/contents/userinfo?title=' + encodeURIComponent(name + '的信息')
						+ '&name=' + encodeURIComponent(name) + '&uid=' + user.uid
						+ '&avatar=' + encodeURIComponent(user.avatar || '')
				});
			},
			dismiss(item){
				this.confirmReview(item, 'dismiss', '驳回举报', '确认该举报不成立并结束审核吗？');
			},
			approve(item){ this.confirmReview(item, 'approve', '通过审核', '确认内容符合规范并公开发布吗？'); },
			hideSpace(item){ this.confirmReview(item, 'hide', '隐藏动态', '确认隐藏这条动态吗？内容和 AI 审核记录都会保留，可再次公开。'); },
			deleteSpace(item){
				var content = item.spaceState === 'visible'
					? '将删除原动态，并把同一动态的待处理举报全部标记为已处理。'
					: '原动态已删除，将把相关举报标记为已处理。';
				this.confirmReview(item, 'delete', '删除动态', content);
			},
			confirmReview(item,action,title,content){
				uni.showModal({
					title: title,
					content: content,
					success: (choice) => {
						if(!choice.confirm) return;
						this.review(item, action);
					}
				});
			},
			review(item,action){
				uni.showLoading({ title: '处理中' });
				this.$Net.request({
					url: this.$API.spaceReportReview(),
					data: { token: this.token, id: item.id, action: action, source: item.source || 'report' },
					header: { 'Content-Type':'application/x-www-form-urlencoded' },
					method: 'post',
					dataType: 'json',
					success: (res) => {
						if(res.data && res.data.code == 1){
							uni.showToast({ title: item.source==='ai' ? '动态状态已更新' : '举报已处理', icon: 'success' });
							this.reload();
						} else {
							uni.showToast({ title: res.data && res.data.msg ? res.data.msg : '处理失败', icon: 'none' });
						}
					},
					fail: () => uni.showToast({ title: '网络不太好哦', icon: 'none' }),
					complete: () => uni.hideLoading()
				});
			},
			spacePreview(text){
				var value = String(text || '').trim();
				if(!value) return '图片或视频动态';
				return value.length > 140 ? value.substring(0,140) + '…' : value;
			},
			aiDecisionText(item){
				if(item.aiDecision==='approved') return '通过';
				if(item.aiDecision==='error') return '异常';
				return '风险';
			},
			spaceStatusText(item){
				if(Number(item.status)===1) return '公开';
				if(Number(item.status)===2) return '锁定';
				return '隐藏';
			},
			humanDecisionText(value){ return value==='approved' ? '公开' : (value==='locked' ? '锁定' : '隐藏'); },
			formatDate(timestamp){
				var date = new Date(Number(timestamp || 0) * 1000);
				var pad = function(value){ return String(value).padStart(2,'0'); };
				return date.getFullYear() + '-' + pad(date.getMonth()+1) + '-' + pad(date.getDate())
					+ ' ' + pad(date.getHours()) + ':' + pad(date.getMinutes());
			}
		}
	}
</script>

<style scoped>
	.report-manage { min-height: 100vh; background: #f4f6f5; color: #24332f; }
	.review-tabs { display: flex; height: 84rpx; padding: 0 28rpx; border-bottom: 1rpx solid #e6ebe8; background: #fff; }
	.review-tabs text { display: flex; align-items: center; margin-right: 42rpx; color: #6f7d78; font-size: 27rpx; }
	.review-tabs .is-active { border-bottom: 4rpx solid #168c80; color: #168c80; font-weight: 600; }
	.ai-filters { display: flex; gap: 12rpx; padding: 18rpx 28rpx; background: #fff; }
	.ai-filters text { padding: 8rpx 18rpx; border-radius: 8rpx; color: #687771; font-size: 23rpx; }
	.ai-filters .is-active { background: #e7f2ef; color: #168c80; font-weight: 600; }
	.report-summary { display: flex; align-items: center; justify-content: space-between; padding: 26rpx 28rpx; border-bottom: 1rpx solid #e6ebe8; background: #fff; }
	.report-summary-title { font-size: 29rpx; font-weight: 600; }
	.report-summary-count { color: #b15f5f; font-size: 28rpx; font-weight: 700; }
	.report-item { margin-bottom: 12rpx; padding: 28rpx; background: #fff; }
	.report-head { display: flex; align-items: center; }
	.report-avatar { flex: 0 0 68rpx; width: 68rpx; height: 68rpx; margin-right: 16rpx; }
	.report-head-main { min-width: 0; flex: 1; }
	.report-user,.report-time { display: block; }
	.report-user { font-size: 27rpx; font-weight: 600; }
	.report-time { margin-top: 4rpx; color: #89948f; font-size: 21rpx; }
	.report-reason { padding: 7rpx 14rpx; border-radius: 8rpx; background: #f7eaea; color: #a65d5d; font-size: 22rpx; }
	.report-reason.is-ai { background: #e8f2ef; color: #257361; }
	.report-detail { display: block; margin-top: 18rpx; color: #596762; font-size: 25rpx; line-height: 1.6; white-space: pre-wrap; }
	.ai-final-state { display: flex; flex-wrap: wrap; gap: 10rpx 24rpx; margin-top: 16rpx; color: #6b7874; font-size: 22rpx; }
	.reported-space { margin-top: 20rpx; padding: 20rpx; border: 1rpx solid #e0e6e3; border-radius: 8rpx; background: #f7f9f8; }
	.reported-space-author { display: flex; align-items: center; gap: 12rpx; color: #53635e; font-size: 24rpx; font-weight: 600; }
	.reported-avatar { width: 44rpx; height: 44rpx; }
	.reported-space-text { display: block; margin-top: 12rpx; color: #2d3b37; font-size: 26rpx; line-height: 1.6; white-space: pre-wrap; word-break: break-word; }
	.reported-space-link { display: block; margin-top: 12rpx; color: #168c80; font-size: 23rpx; }
	.reported-space.is-deleted { color: #8b9692; text-align: center; }
	.report-actions { display: flex; justify-content: flex-end; gap: 18rpx; margin-top: 22rpx; }
	.report-actions .cu-btn { min-width: 174rpx; border-radius: 8rpx; }
	.report-empty { display: flex; flex-direction: column; align-items: center; gap: 16rpx; padding: 150rpx 0; color: #87938f; }
	.report-empty .cuIcon-check { font-size: 58rpx; color: #168c80; }
	.report-more { padding: 40rpx 0 70rpx; color: #85908c; font-size: 24rpx; text-align: center; }
	.campus-night.report-manage { background: #151b19; color: #edf2ef; }
	.campus-night .review-tabs,.campus-night .ai-filters,.campus-night .report-summary,.campus-night .report-item { border-color: #303a37; background: #1d2523; }
	.campus-night .reported-space { border-color: #36413e; background: #252e2b; }
	.campus-night .reported-space-text { color: #e5ece9; }
</style>
