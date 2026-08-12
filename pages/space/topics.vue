<template>
	<view class="campus-topic-page" :class="{'campus-night': campusNight}">
		<view class="topic-header" :style="{paddingTop: StatusBar + 'px'}">
			<view class="topic-nav" :style="{height: CustomBar + 'px'}">
				<view class="topic-icon-button" @tap="back"><text class="cuIcon-back"></text></view>
				<text class="topic-title">话题</text>
				<view class="topic-icon-button" @tap="clearAll" :class="{'is-disabled': !searchKey && selectedTopics.length === 0}">
					<text class="cuIcon-refresh"></text>
				</view>
			</view>
		</view>
		<view :style="{height: (StatusBar + CustomBar) + 'px'}"></view>

		<view class="topic-controls">
			<view class="topic-search" v-if="!directTopicView">
				<text class="cuIcon-search"></text>
				<input v-model="searchKey" placeholder="搜索话题名称" confirm-type="search" @input="queueSearch" @confirm="loadTopics"></input>
				<text v-if="searchKey" class="cuIcon-close" @tap="clearSearch"></text>
			</view>
			<view class="topic-tabs" v-if="!directTopicView">
				<view v-for="tab in tabs" :key="tab.value" :class="{'is-active': filter === tab.value}" @tap="setFilter(tab.value)">{{tab.label}}</view>
			</view>
			<view class="topic-selected" v-if="selectedTopics.length">
				<view class="topic-chip" v-for="topic in selectedTopics" :key="'selected-'+topic.mid" @tap="removeTopic(topic.mid)">
					<text>#{{topic.name}}</text><text class="cuIcon-close"></text>
				</view>
				<text class="topic-clear" @tap="clearSelected">清除全部</text>
			</view>
		</view>

		<view class="topic-list" v-if="!directTopicView && visibleTopics.length">
			<view class="topic-row" :class="{'is-selected': isSelected(topic.mid)}" v-for="topic in visibleTopics" :key="'topic-'+topic.mid">
				<view class="topic-row-main" @tap="toggleSelected(topic)">
					<view class="topic-row-heading">
						<text class="topic-name">#{{topic.name}}</text>
						<text class="topic-check" v-if="isSelected(topic.mid)"><text class="cuIcon-check"></text></text>
					</view>
					<text class="topic-meta">{{topic.spaceCount || 0}} 条动态 · {{topic.followCount || 0}} 人关注</text>
				</view>
				<button class="topic-follow" :class="{'is-followed': topic.isFollowed == 1}" @tap.stop="toggleFollow(topic)">
					{{topic.isFollowed == 1 ? '已关注' : '关注'}}
				</button>
			</view>
		</view>
		<view class="topic-empty" v-else-if="!directTopicView && !loadingTopics">{{topicEmptyText}}</view>

		<view class="result-band" v-if="selectedTopics.length">
			<view>
				<text class="result-title">{{directTopicView && selectedTopic ? '#' + selectedTopic.name : '相关动态'}}</text>
				<text class="result-count">{{spaceTotal}} 条</text>
			</view>
			<button v-if="directTopicView && selectedTopic" class="topic-follow result-follow" :class="{'is-followed': selectedTopic.isFollowed == 1}" @tap="toggleFollow(selectedTopic)">
				{{selectedTopic.isFollowed == 1 ? '已关注' : '关注'}}
			</button>
			<text v-else class="result-mode">同时包含全部已选话题</text>
		</view>
		<view class="direct-filter-entry" v-if="directTopicView && selectedTopics.length" @tap="directTopicView=false">
			<text class="cuIcon-filter"></text><text>添加其他话题进行组合筛选</text><text class="cuIcon-right"></text>
		</view>
		<spaceItem v-if="spaceList.length" :spaceList="spaceList" :night="campusNight"></spaceItem>
		<view class="topic-empty result-empty" v-else-if="selectedTopics.length && !loadingSpaces">
			{{selectedTopics.length > 1 ? '暂无同时包含这些话题的动态' : '该话题下暂无动态'}}
		</view>
		<view class="load-more" v-if="spaceList.length && !noMore" @tap="loadSpaces(true)">{{loadingSpaces ? '正在加载...' : '加载更多'}}</view>
	</view>
</template>

<script>
	import { localStorage } from '../../js_sdk/mp-storage/mp-storage/index.js'
	import spaceItem from '@/pages/components/spaceItem.vue'
	import { applyCampusThemeShell, getCampusThemeMode, isDongchangfuNight, resolveCampusNight } from '@/utils/campusTheme.js'

	export default {
		components: { spaceItem },
		data() {
			return {
				StatusBar: this.StatusBar,
				CustomBar: this.CustomBar,
				campusThemeMode: getCampusThemeMode(),
				campusThemeClock: Date.now(),
				tabs: [
					{ value: 'all', label: '全部话题' },
					{ value: 'hot', label: '热门话题' },
					{ value: 'followed', label: '我关注的' }
				],
				filter: 'all',
				searchKey: '',
				allTopics: [],
				hotTopics: [],
				followedTopics: [],
				selectedTopics: [],
				spaceList: [],
				spaceTotal: 0,
				page: 1,
				loadingTopics: false,
				loadingSpaces: false,
				noMore: false,
				searchTimer: null,
				token: '',
				directTopicView: false
			}
		},
		computed: {
			campusNight() {
				return resolveCampusNight(this.campusThemeMode, isDongchangfuNight(this.campusThemeClock))
			},
			visibleTopics() {
				if (this.filter === 'hot') return this.hotTopics
				if (this.filter === 'followed') return this.followedTopics
				return this.allTopics
			},
			selectedTopic() {
				return this.selectedTopics.length === 1 ? this.selectedTopics[0] : null
			},
			topicEmptyText() {
				if (this.searchKey) return '未找到相关话题'
				if (this.filter === 'followed') return this.token ? '还没有关注话题' : '登录后可查看关注的话题'
				return this.filter === 'hot' ? '暂无热门话题' : '暂无话题'
			}
		},
		onLoad(options) {
			this.token = localStorage.getItem('token') || ''
			applyCampusThemeShell(this.campusThemeMode, this.campusThemeClock)
			const mid = Number(options.mid || 0)
			const name = this.safeDecode(options.name || '')
			if (mid > 0) {
				this.selectedTopics = [{ mid, name }]
				this.directTopicView = true
			}
			this.loadTopics().then(() => {
				if (mid > 0) {
					const complete = this.allTopics.find(item => Number(item.mid) === mid)
					if (complete) this.selectedTopics = [complete]
					this.loadSpaces(false)
				}
			})
		},
		onShow() {
			this.campusThemeMode = getCampusThemeMode()
			applyCampusThemeShell(this.campusThemeMode, Date.now())
		},
		onUnload() {
			if (this.searchTimer) clearTimeout(this.searchTimer)
		},
		onReachBottom() {
			if (this.selectedTopics.length && !this.noMore) this.loadSpaces(true)
		},
		methods: {
			safeDecode(value) {
				try { return decodeURIComponent(value) } catch (error) { return value }
			},
			back() {
				const pages = getCurrentPages()
				if (pages.length > 1) uni.navigateBack({ delta: 1 })
				else uni.reLaunch({ url: '/pages/home/home' })
			},
			setFilter(value) { this.filter = value },
			queueSearch() {
				if (this.searchTimer) clearTimeout(this.searchTimer)
				this.searchTimer = setTimeout(() => this.loadTopics(), 250)
			},
			clearSearch() { this.searchKey = ''; this.loadTopics() },
			clearAll() { this.searchKey = ''; this.filter = 'all'; this.clearSelected(); this.loadTopics() },
			clearSelected() { this.selectedTopics = []; this.spaceList = []; this.spaceTotal = 0; this.page = 1; this.noMore = false; this.directTopicView = false },
			removeTopic(mid) {
				this.selectedTopics = this.selectedTopics.filter(item => String(item.mid) !== String(mid))
				if (this.selectedTopics.length) this.loadSpaces(false)
				else this.clearSelected()
			},
			isSelected(mid) { return this.selectedTopics.some(item => String(item.mid) === String(mid)) },
			toggleSelected(topic) {
				if (this.isSelected(topic.mid)) return this.removeTopic(topic.mid)
				if (this.selectedTopics.length >= 3) {
					uni.showToast({ title: '最多同时选择3个话题', icon: 'none' })
					return
				}
				this.selectedTopics = this.selectedTopics.concat([topic])
				this.loadSpaces(false)
			},
			loadTopics() {
				if (this.loadingTopics) return Promise.resolve()
				this.loadingTopics = true
				return new Promise(resolve => {
					this.$Net.request({
						url: this.$API.topicList(),
						data: { token: this.token, searchKey: this.searchKey.trim() },
						method: 'get', dataType: 'json',
						success: res => {
							const data = res.data.code === 1 ? (res.data.data || {}) : {}
							this.allTopics = data.all || data.official || []
							this.hotTopics = data.hot || data.official || []
							this.followedTopics = data.followed || []
							this.syncSelectedStates()
							this.loadingTopics = false
							resolve()
						},
						fail: () => { this.loadingTopics = false; resolve() }
					})
				})
			},
			syncSelectedStates() {
				this.selectedTopics = this.selectedTopics.map(selected =>
					this.allTopics.find(item => String(item.mid) === String(selected.mid)) || selected)
			},
			toggleFollow(topic) {
				if (!this.token) {
					uni.showToast({ title: '请先登录', icon: 'none' })
					return
				}
				const type = Number(topic.isFollowed) === 1 ? 0 : 1
				this.$Net.request({
					url: this.$API.topicFollow(), data: { token: this.token, mid: topic.mid, type },
					method: 'post', dataType: 'json',
					success: res => {
						uni.showToast({ title: res.data.msg, icon: 'none' })
						if (res.data.code === 1) this.loadTopics()
					}
				})
			},
			loadSpaces(append) {
				if (this.loadingSpaces || !this.selectedTopics.length) return
				this.loadingSpaces = true
				const nextPage = append ? this.page + 1 : 1
				this.$Net.request({
					url: this.$API.spaceList(),
					data: {
						token: this.token, page: nextPage, limit: 10, order: 'created',
						searchParams: JSON.stringify({ topicIds: this.selectedTopics.map(item => Number(item.mid)) })
					},
					method: 'get', dataType: 'json',
					success: res => {
						const rows = res.data.code === 1 && Array.isArray(res.data.data) ? res.data.data : []
						rows.forEach(item => {
							item.picList = item.pic ? item.pic.split('||').filter(Boolean) : []
							if (item.forwardJson) item.forwardJson.picList = item.forwardJson.pic ? item.forwardJson.pic.split('||').filter(Boolean) : []
						})
						this.spaceList = append ? this.spaceList.concat(rows) : rows
						this.page = nextPage
						this.spaceTotal = Number(res.data.total || rows.length)
						this.noMore = rows.length < 10
						this.loadingSpaces = false
					},
					fail: () => { this.loadingSpaces = false }
				})
			}
		}
	}
</script>

<style scoped>
	.campus-topic-page { min-height: 100vh; background: #f4f7f6; color: #18201e; }
	.topic-header { position: fixed; z-index: 20; left: 0; right: 0; top: 0; background: #fff; border-bottom: 1px solid #e7ecea; }
	.topic-nav { display: grid; grid-template-columns: 88rpx 1fr 88rpx; align-items: center; padding: 0 20rpx; }
	.topic-title { text-align: center; font-size: 32rpx; font-weight: 600; }
	.topic-icon-button { width: 72rpx; height: 72rpx; display: flex; align-items: center; justify-content: center; font-size: 36rpx; }
	.topic-icon-button.is-disabled { opacity: .32; }
	.topic-controls { padding: 24rpx 24rpx 10rpx; background: #fff; }
	.topic-search { height: 76rpx; padding: 0 24rpx; display: flex; align-items: center; gap: 16rpx; background: #eef3f1; border-radius: 8rpx; }
	.topic-search input { flex: 1; min-width: 0; }
	.topic-tabs { display: grid; grid-template-columns: repeat(3, 1fr); margin-top: 20rpx; border-bottom: 1px solid #e8edeb; }
	.topic-tabs view { height: 72rpx; display: flex; align-items: center; justify-content: center; color: #65716e; border-bottom: 4rpx solid transparent; }
	.topic-tabs .is-active { color: #168c67; border-bottom-color: #168c67; font-weight: 600; }
	.topic-selected { display: flex; align-items: center; flex-wrap: wrap; gap: 12rpx; padding-top: 18rpx; }
	.topic-chip { display: flex; align-items: center; gap: 8rpx; padding: 10rpx 14rpx; color: #146c54; background: #e4f4ee; border-radius: 6rpx; }
	.topic-clear { color: #78827f; margin-left: auto; padding: 10rpx; }
	.topic-list { background: #fff; }
	.topic-row { min-height: 126rpx; padding: 22rpx 24rpx; display: flex; align-items: center; border-bottom: 1px solid #edf0ef; }
	.topic-row.is-selected { background: #edf8f4; box-shadow: inset 6rpx 0 0 #168c67; }
	.topic-row-main { flex: 1; min-width: 0; }
	.topic-row-heading { display: flex; align-items: center; gap: 10rpx; }
	.topic-name { font-size: 30rpx; font-weight: 600; }
	.topic-check { color: #168c67; }
	.topic-meta { display: block; margin-top: 8rpx; color: #7a8582; font-size: 24rpx; }
	.topic-follow { flex: 0 0 126rpx; height: 60rpx; line-height: 58rpx; padding: 0; margin: 0 0 0 18rpx; border: 1px solid #168c67; background: #168c67; color: #fff; border-radius: 6rpx; font-size: 26rpx; }
	.topic-follow.is-followed { color: #4f5d59; background: transparent; border-color: #b8c2bf; }
	.topic-empty { padding: 80rpx 24rpx; text-align: center; color: #7d8784; background: #fff; }
	.result-band { margin-top: 18rpx; padding: 22rpx 24rpx; display: flex; justify-content: space-between; align-items: center; background: #fff; border-bottom: 1px solid #e7ecea; }
	.result-title { font-size: 30rpx; font-weight: 600; }
	.result-count { color: #168c67; margin-left: 12rpx; }
	.result-mode { color: #7a8582; font-size: 23rpx; }
	.result-follow { margin-left: 18rpx; }
	.direct-filter-entry { min-height: 72rpx; padding: 0 24rpx; display: flex; align-items: center; gap: 12rpx; color: #52625d; background: #fff; border-bottom: 1px solid #e7ecea; }
	.direct-filter-entry .cuIcon-right { margin-left: auto; }
	.result-empty { margin-top: 0; }
	.load-more { padding: 30rpx; text-align: center; color: #6f7a77; }
	.campus-night { background: #15191b; color: #e7ecea; }
	.campus-night .topic-header, .campus-night .topic-controls, .campus-night .topic-list, .campus-night .topic-empty, .campus-night .result-band { background: #1c2223; border-color: #303839; }
	.campus-night .topic-search { background: #273031; }
	.campus-night .topic-row, .campus-night .topic-tabs { border-color: #303839; }
	.campus-night .topic-row.is-selected { background: #20342f; }
	.campus-night .topic-name, .campus-night .topic-title { color: #f1f4f3; }
	.campus-night .topic-meta, .campus-night .result-mode, .campus-night .topic-clear { color: #9ba7a3; }
	.campus-night .direct-filter-entry { color: #b9c3c0; background: #1c2223; border-color: #303839; }
	.campus-night .topic-follow.is-followed { color: #d0d7d4; border-color: #596461; }
</style>
