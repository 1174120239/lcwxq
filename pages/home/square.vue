<template>
	<view class="campus-page campus-square" :class="{'campus-night': campusNight}">
		
		<view class="header square-header" :style="{paddingTop: StatusBar + 'px'}">
			<view class="square-mainbar">
				<view class="square-tool-button" :class="{'is-placeholder': contentMode==='qa'}" @tap="toggleSquareMenu"><text class="cuIcon-sort"></text></view>
				<text class="square-page-title">{{squarePageTitle}}</text>
				<view class="square-tool-button" :class="{'is-placeholder': contentMode==='qa'}" @tap="toSearch"><text class="cuIcon-search"></text></view>
			</view>
			<view class="square-section-tabs" v-if="squareid==0">
				<view class="square-section-track">
					<view class="square-section-item" :class="{'is-active':contentMode==='space'}" @tap="switchContentMode('space')">普通动态</view>
					<view class="square-section-item" :class="{'is-active':contentMode==='qa'}" @tap="switchContentMode('qa')">提问区</view>
				</view>
			</view>
			<view class="square-filter-row" v-if="contentMode==='space'" @tap="toggleSquareMenu">
				<view class="square-filter-label">
					<text class="cuIcon-filter"></text>
					<text>{{squareFilterLabel}}</text>
				</view>
				<text class="cuIcon-unfold square-filter-arrow" :class="{'is-open':showSquareMenu}"></text>
			</view>
			<view class="square-filter-menu" v-if="contentMode==='space'" :class="{'is-open':showSquareMenu}" @tap.stop>
				<view class="filter-menu-title">动态筛选</view>
				<view class="filter-menu-options">
					<view :class="{'is-active':follow==1&&squareid==0&&selectedTopics.length===0}" @tap="setFollow(1);showSquareMenu=false">全部</view>
					<view :class="{'is-active':follow==0&&squareid==0}" @tap="setFollow(0);showSquareMenu=false">关注</view>
					<view :class="{'is-active':follow==2&&squareid==0}" @tap="setFollow(2);showSquareMenu=false">视频</view>
					<view :class="{'is-active':follow==3&&squareid==0}" @tap="setFollow(3);showSquareMenu=false">图集</view>
				</view>
				<view class="filter-menu-title service-title">
					<text>话题筛选</text>
					<text class="filter-topic-count" v-if="selectedTopics.length">已选 {{selectedTopics.length}}/3</text>
				</view>
				<view class="filter-menu-topics" v-if="officialTopicPreview.length>0">
					<scroll-view scroll-x class="filter-topic-scroll" :show-scrollbar="false">
						<view class="filter-topic-track">
							<view class="filter-topic-chip" :class="{'is-active': isTopicSelected(topic.mid)}" v-for="topic in officialTopicPreview" :key="'menu-topic-'+topic.mid"
								@tap="selectTopic(topic)">
								<text :class="isTopicSelected(topic.mid) ? 'cuIcon-check' : 'cuIcon-tag'"></text><text>#{{topic.name}}</text>
							</view>
							<view class="filter-topic-more" @tap="showAllTopics">
								<text>显示更多</text><text class="cuIcon-right"></text>
							</view>
						</view>
					</scroll-view>
				</view>
				<view class="filter-topic-empty" v-else @tap="showAllTopics">
					<text class="cuIcon-tag"></text>
					<text>{{topicCenterLoading ? '话题加载中' : '话题'}}</text>
					<text class="filter-topic-empty-more">显示更多</text>
				</view>
				<view class="filter-menu-services filter-menu-extra-services" v-if="groupChatEnabled || sy_appbox">
					<view v-if="groupChatEnabled" @tap="handleClick1();showSquareMenu=false"><text class="cuIcon-message"></text><text>群聊</text></view>
					<view v-if="sy_appbox" @tap="handleClick3();showSquareMenu=false"><text class="cuIcon-apps"></text><text>应用</text></view>
				</view>
			</view>
		</view>
			
		<block v-if="squareid==0&&contentMode==='space'">
			<view class="square-header-spacer" :style="squareHeaderSpacer"></view>
			<scroll-view v-if="topContents.length>0" scroll-x class="square-pinned-strip" :show-scrollbar="false">
				<view class="pinned-track">
					<view class="pinned-item" v-for="(item,index) in topContents.slice(0,2)" :key="'pinned'+index" @tap="toInfo(item)">
						<text class="pinned-badge">置顶</text><text class="pinned-title">{{item.title}}</text>
					</view>
				</view>
			</scroll-view>
			<view class="appcontent margin-top-xl" @tap="collapseSquareMenu" @touchmove="collapseSquareMenu">
			
			<block v-if="follow==0">
				<view class="no-data" v-if="token==''">
					<text class="cuIcon-text"></text>
					请先登录哦！
					<view class="text-center margin-top-sm">
						<text class="cu-btn bg-shojo radius" @tap="goLogin()">登录</text>
						<text class="cu-btn line-blue margin-left-sm radius" @tap="goRegister()">注册</text>
					</view>
				
				</view>
				<view v-else>
				<view class="no-data square-empty" v-if="spaceList.length==0">
				<text class="cuIcon-text"></text>
				暂时还没有关注的人哦~
			</view>
			<followItem :spaceList="spaceList" :followList="spaceList.isFollow" @before-navigate="rememberSpaceReturn"></followItem>
			<view class="load-more" @tap="loadMore" v-if="dataLoad&&chatList.length>0">
				<text>{{moreText}}</text>
			</view>
			</view>
			</block>
			<block v-if="follow==1">
			<view class="no-data square-empty" v-if="spaceList.length==0">
				<text class="cuIcon-text"></text>
				{{selectedTopics.length > 1 ? '暂无同时包含这些话题的动态' : selectedTopics.length === 1 ? '该话题下暂无动态' : '什么都没有'}}
			</view>
			
			<spaceItem :spaceList="spaceList" :night="campusNight" @before-navigate="rememberSpaceReturn"></spaceItem>
			<view class="load-more" @tap="loadMore" v-if="dataLoad&&chatList.length>0">
				<text>{{moreText}}</text>
			</view>
			</block>
			<block v-if="follow==2">
			<view class="no-data square-empty" v-if="spaceList.length==0">
				<text class="cuIcon-text"></text>
				什么都没有
			</view>
			
			<spaceItem :spaceList="spaceList" :night="campusNight" @before-navigate="rememberSpaceReturn"></spaceItem>
			<view class="load-more" @tap="loadMore" v-if="dataLoad&&chatList.length>0">
				<text>{{moreText}}</text>
			</view>
			</block>
			<block v-if="follow==3">
			<view class="no-data square-empty" v-if="spaceList.length==0">
				<text class="cuIcon-text"></text>
				什么都没有
			</view>
			
			<spaceItem :spaceList="spaceList" :night="campusNight" @before-navigate="rememberSpaceReturn"></spaceItem>
			<view class="load-more" @tap="loadMore" v-if="dataLoad&&chatList.length>0">
				<text>{{moreText}}</text>
			</view>
			</block>
		</view>
		</block>
		<block v-if="squareid==0&&contentMode==='qa'">
			<view class="square-header-spacer" :style="squareHeaderSpacer"></view>
			<view class="square-qa-list" @touchmove="collapseSquareMenu">
				<view class="square-qa-loading" v-if="questionLoading&&questionList.length===0">
					<view class="campus-loader"></view>
				</view>
				<view class="no-data square-empty" v-else-if="!questionLoading&&questionList.length===0">
					<text class="cuIcon-question"></text>
					暂时还没有已发布的问题
				</view>
				<qa-question-card v-for="item in questionList" :key="'square-question-'+item.id"
					:question="item" :night="campusNight" @open="openQuestion"></qa-question-card>
				<view class="square-qa-more" v-if="questionList.length>0">{{questionMoreText}}</view>
			</view>
		</block>
		<block v-if="squareid==1&&groupChatEnabled">
			<view class="square-header-spacer" :style="squareHeaderSpacer"></view>
			<view class="no-data" v-if="token==''">
				<text class="cuIcon-text"></text>
				请先登录哦！
				<view class="text-center margin-top-sm">
					<text class="cu-btn bg-shojo radius" @tap="goLogin()">登录</text>
					<text class="cu-btn line-blue margin-left-sm radius" @tap="goRegister()">注册</text>
				</view>

			</view>
			<view class="cu-list menu-avatar" v-if="token!=''">
				<view class="cu-bar bg-white search">
					<view class="search-form round">
						<text class="cuIcon-search"></text>
						<input type="text" placeholder="搜索群聊" v-model="searchText"></input>
						<view class="search-close" v-if="searchText!=''" @tap="searchClose()"><text
								class="cuIcon-close"></text></view>
					</view>
				</view>
				<view class="no-data" v-if="chatList.length==0">
					<text class="cuIcon-text"></text>
					
					暂时没有数据
				</view>
				<block v-for="(item,index) in chatList" :key="index">
					<view class="cu-item" @tap="goChat(item)" v-if="item.name.indexOf(searchText)!=-1">
						<block v-if="item.type==1">
							<view class="cu-avatar round lg" :style="'background-image:url('+item.pic+');'"></view>
						</block>
						<block v-else>
							<view class="cu-avatar round lg" :style="'background-image:url('+item.userJson.avatar+');'">
							</view>
						</block>
						<view class="content">
							<view>
								<view class="text-cut">{{item.name}}</view>
							</view>
							<view class="text-gray text-sm flex">
								<view class="text-cut">
									<block v-if="item.lastMsg!=null">

										<block v-if="item.lastMsg.type!=4">
											<block v-if="item.lastMsg.uid==uid">
												我:
											</block>
											<block v-if="item.lastMsg.uid!=uid">
												{{item.lastMsg.name}}:
											</block>
											<block v-if="item.lastMsg.type==0">
												{{item.lastMsg.text}}
											</block>
											<block v-if="item.lastMsg.type==1">
												[图片]
											</block>
										</block>
										<block v-else>
											<block v-if="item.lastMsg.text=='ban'">
												<text class="text-red">[已开启全体禁言]</text>
											</block>
											<block v-else>
												<text class="text-blue">[已解除全体禁言]</text>
											</block>
										</block>
									</block>
									<block v-else>暂无消息</block>
								</view>
							</view>
						</view>
						<view class="action">
							<view class="text-grey text-xs">{{chatFormatDate(item.lastTime)}}</view>
							<block v-if="item.lastMsg!=null">
								<block v-if="item.lastMsg.uid==uid">
									<view class="cu-tag sm" style="background: none;">&nbsp</view>
								</block>
								<block v-else>
									<view class="cu-tag sm" style="background: none;" v-if="item.isNew==0">&nbsp</view>
									<view class="cu-tag round bg-red sm" v-else>{{item.unRead}}</view>
								</block>
							</block>
							<block v-else>
								<view class="cu-tag sm" style="background: none;">&nbsp</view>
							</block>
						</view>
					</view>
				</block>
			</view>
		</block>
	<block v-if="squareid==2">
		<view class="square-header-spacer" :style="squareHeaderSpacer"></view>
		<view class="topic-center">
			<view class="topic-center-heading">
				<text class="topic-center-title">热门话题</text>
			</view>
			<view class="topic-center-list" v-if="officialTopics.length>0">
				<view class="topic-center-item" v-for="topic in officialTopics" :key="'official-'+topic.mid">
					<view class="topic-center-main" @tap="selectTopic(topic)">
						<text class="topic-center-name">#{{topic.name}}</text>
						<text class="topic-center-count">{{topic.spaceCount || 0}}条动态</text>
					</view>
					<text class="topic-center-follow" @tap.stop="toggleTopicFollow(topic)">
						{{topic.isFollowed==1 ? '已关注' : '关注'}}
					</text>
				</view>
			</view>
			<view class="topic-center-empty" v-else>暂无热门话题</view>

			<view class="topic-center-heading topic-center-heading-followed">
				<text class="topic-center-title">我关注的话题</text>
				<text class="topic-center-subtitle">关注后可快速查看相关动态</text>
			</view>
			<view class="topic-center-list" v-if="followedTopics.length>0">
				<view class="topic-center-item" v-for="topic in followedTopics" :key="'followed-'+topic.mid">
					<view class="topic-center-main" @tap="selectTopic(topic)">
						<text class="topic-center-name">#{{topic.name}}</text>
						<text class="topic-center-count">{{topic.spaceCount || 0}}条动态</text>
					</view>
					<text class="topic-center-follow is-followed" @tap.stop="toggleTopicFollow(topic)">已关注</text>
				</view>
			</view>
			<view class="topic-center-empty" v-else>还没有关注话题</view>
		</view>
	</block>
	<!-- 应用start -->
	<block v-if="squareid==3">
		<view class="square-header-spacer" :style="squareHeaderSpacer"></view>
			<!-- #ifdef APP-PLUS || H5 -->
			<block v-if="appLoad">
				<!-- 应用列表部分 -->
				<view class="u-wrap" :style="{ height: `calc(100vh - ${NavBar+70}px)` }">
					<view class="u-menu-wrap">
						<!-- 左侧菜单保持不变 -->
						<scroll-view scroll-y scroll-with-animation class="u-tab-view menu-scroll-view"
							:scroll-top="scrollTop">
							<view v-for="(item,index) in left_tabbar" :key="index" class="u-tab-item"
								:class="[current==index ? 'u-tab-item-active' : '']" :data-current="index"
								@tap.stop="swichMenu(index)">
								<text class="u-line-1">{{item.name}}</text>
							</view>
							<view style="width: 100%; height: 120upx;" :style="{'background-color':'#fff'}" ></view>
						</scroll-view>
						<scroll-view scroll-y class="right-box"
							@scrolltolower="handleScrollToLower">
							<view class="page-view">
								<view class="class-item">
									<view class="filter-bar">
										<view class="filter-item" @tap="showOrderPicker">
											<text>{{orderText}}</text>
											<text class="cuIcon-unfold"></text>
										</view>
										<view class="filter-item" @tap="showTypeFilter">
											<text>{{typeText}}</text>
											<text class="cuIcon-unfold"></text>
										</view>
									</view>
									<view class="loading-container" v-if="loading">
										<u-loading mode="circle" size="36"></u-loading>
									</view>

									<view class="item-container" v-else>
										<block v-if="appList.length>0">
											<view class="app-box" style="padding: 10rpx 10rpx 0 10rpx;"
												v-for="(item, index) in appList" :key="index">
												<view class="app-box-body" @tap="toAppInfo(item.id)">
													<view class="app-box-logo">
														<u-image :src="item.logo" width="110rpx" height="110rpx"
															mode="aspectFill" :lazy-load="true" :fade="true"
															duration="450" border-radius="28rpx">
															<u-loading slot="loading"></u-loading>
														</u-image>
													</view>
													<view class="app-box-content">
														<view class="app-box-title text-cut">{{item.name}}</view>
														<view class="app-box-info">
															<text :style="{color: item.tagInfo.color}"
																:class="item.score>=3?'tn-icon-star-fill':'tn-icon-star'"></text>
															<text
																:style="{color: item.tagInfo.color}">{{item.score}}</text>
															<text>{{item.size}}</text>

														</view>
														<view class="app-box-tags">
															<text class="app-tag"
																:style="{backgroundColor: item.tagInfo.color}">{{item.tagInfo.text}}</text>
															<text>v{{item.version}}</text>
															<text
																:class="item.system=='ios'?'tn-icon-iphone':''"></text>
														</view>
													</view>
												</view>
												<view class="app-box-down" @tap="toAppInfo(item.id)">下载</view>
											</view>
										</block>
										<block v-else>
											<view class="margin-top-sm">
												<u-empty text="暂无数据" mode="data" icon-size="100"
													font-size="24"></u-empty>
											</view>

										</block>
										<view class="loading-more" v-if="loadStatus !== 'nomore'">
											<u-loadmore :status="loadStatus" :icon-type="'circle'"
												:load-text="loadMoreText" @loadmore="handleScrollToLower" />
										</view>
									</view>
								</view>
							</view>



							<view style="width: 100%; height: 120upx;" :style="{'background-color':'#fff'}" ></view>
						</scroll-view>
					</view>
				</view>
				
			</block>
			<block v-else>
				<view class="dataLoad" v-if="!dataLoad">
					<view class="campus-loader"></view>
				</view>
			</block>
		
			<u-picker v-model="showOrder" :show="showOrder" :columns="[orderOptions]" @confirm="confirmOrder"
				@cancel="cancelOrder" mode="selector" :range="orderOptions" range-key="text"></u-picker>

			<u-popup v-model="showType" mode="bottom" :mask-close-able="true" :safe-area-inset-bottom="true"
				@close="showType = false">
				<view class="filter-popup">
					<view class="filter-title">选择类型</view>
					<view class="filter-options">
						<view class="filter-option" v-for="(type, index) in typeOptions" :key="index"
							:class="{'active': selectedType === type.value}" @tap="selectType(type.value)">
							{{type.label}}
						</view>
					</view>
					<view class="filter-buttons">
						<view class="btn-reset" @tap="resetType">重置</view>
						<view class="btn-confirm" @tap="confirmType">确定</view>
					</view>
				</view>
			</u-popup>
			<!-- #endif -->
		</block>
		<!-- 应用end -->
		<!--加载遮罩-->
		<view class="loading" v-if="contentMode!=='qa'&&(isLoading==0||changeLoading==0)">
			<view class="loading-main">
				<view class="campus-loader"></view>
			</view>
		</view>
		<view v-if="showBackToTop && squareid === 0" class="square-back-top" title="回到顶部" aria-label="回到顶部" @tap.stop="backToTop">
			<text class="cuIcon-top"></text>
		</view>
		<!--  #ifdef APP-PLUS -->
		<view style="height: 100upx;"></view>
		<Tabbar ref="tabbar" :current="1" :night="campusNight"></Tabbar>
		<!--  #endif -->
		<!--  #ifdef H5 -->
		<PublishPanel ref="publishPanel" :visible="true" :night="campusNight" :auto-intro="false"></PublishPanel>
		<!--  #endif -->

	</view>
</template>

<script>
	import waves from '@/components/xxley-waves/waves.vue';
	import metas from '@/pages/contents/metas.vue'
	import { applyCampusThemeShell, getCampusThemeMode, isDongchangfuNight, resolveCampusNight } from '@/utils/campusTheme.js'
	import featureFlags from '@/utils/featureFlags.js'
	// #ifdef APP-PLUS
	import Tabbar from '@/pages/components/tabBar.vue'
	// #endif
	
	import {
		localStorage
	} from '../../js_sdk/mp-storage/mp-storage/index.js'
	export default {
		data() {
			return {
				StatusBar: this.StatusBar,
				CustomBar: this.CustomBar,
				NavBar: this.StatusBar + this.CustomBar,
				AppStyle: this.$store.state.AppStyle,
				campusThemeClock: Date.now(),
				campusThemeTimer: null,
				campusThemeMode: 'auto',

					 changeLoading:1,
				userInfo: null,
				token: "",
				isLoading: 0,
				left_tabbar: [],
				scrollTop: 0, 
				pageScrollTop: 0,
				showBackToTop: false,
				spaceReturnScrollTop: 0,
				spaceReturnPending: false,
				current: 0,
				menuHeight: 0,
				menuItemHeight: 0, 
				sectionList: [],
				toolid: 0,
				userList:[],
				noticeSum: 0,
				squareid: 0,
				groupChatEnabled: featureFlags.groupChat,
				privateChatEnabled: featureFlags.privateChat,
				follow: 1,
				searchText: "",
				sousuok: "",
				chatList: [],
				oldChatList: [],
				metaList: [],
				spaceList: [],
				topContents: [],
				showSquareMenu: false,
				contentMode: 'space',
				questionList: [],
				questionPage: 1,
				questionTotal: 0,
				questionPageSize: 12,
				questionLoading: false,
				questionLoadingMore: false,
				questionMoreText: '',
				latestUserAvatar: [],
				curIMG:"",
				isGetChat: null,
				uid: 0,
				dataLoad: false,
				appModOrder:2,
				page: 1,
				moreText: "加载更多",
				isMetasLoading: 0,
				metaPage: 1,
				metaCircleList: [],
				metaCircleMoreTxt: "加载更多",
				officialTopics: [],
				followedTopics: [],
				topicCenterLoaded: false,
				topicCenterLoading: false,
				selectedTopics: [],
				appList: [], 
				limit: 15,
				loading: false,
				finished: false,
				appLoad: false,
				tagMap: {
					1: {
						text: '搬运',
						color: '#7c72ff'
					},
					2: {
						text: '原创',
						color: '#19be6b'
					},
					3: {
						text: '金标',
						color: '#ff6600'
					},
					4: {
						text: '官方',
						color: '#2979ff'
					}
				},

				// 筛选相关
				showOrder: false,
				orderOptions: [{
						text: '最新投稿',
						value: 'created'
					},
					{
						text: '好评如潮',
						value: 'score'
					},
					{
						text: '讨论火热',
						value: 'commentsNum'
					},
					{
						text: '随便看看',
						value: 'random'
					}
				],
				selectedOrder: 'created',
				orderText: '最新投稿',

				// 系统筛选
				showSystem: false,
				systemOptions: [
					{
						label: 'Android',
						value: 'android'
					}
				],
				selectedSystem: '',
				systemText: '系统',
				showType: false,
				typeOptions: [{
						label: '全部',
						value: ''
					},
					{
						label: '搬运',
						value: '1'
					},
					{
						label: '原创',
						value: '2'
					},
					{
						label: '金标',
						value: '3'
					},
					{
						label: '官方',
						value: '4'
					}
				],
				selectedType: '',
				typeText: '应用类型',
				sy_appbox:false,

				// 修改加载状态的处理
				loadStatus: 'loadmore', // loadmore, loading, nomore
				loadMoreText: {
					loadmore: '上拉加载更多',
					loading: '正在加载...',
					nomore: '没有更多了'
				},

			}
		},
		computed: {
			campusNight() {
				return resolveCampusNight(this.campusThemeMode, isDongchangfuNight(this.campusThemeClock))
			},
			squareFilterLabel() {
				if (this.squareid == 0) {
					if (this.selectedTopics.length === 1) return '#' + this.selectedTopics[0].name
					if (this.selectedTopics.length > 1) return '已选' + this.selectedTopics.length + '个话题'
					return this.follow == 0 ? '关注' : this.follow == 1 ? '全部' : this.follow == 2 ? '视频' : '图集'
				}
				if (this.squareid == 1 && this.groupChatEnabled) return '群聊'
				return this.squareid == 2 ? '话题' : '校园应用'
			},
			squarePageTitle() {
				if (this.squareid == 0 && this.contentMode === 'qa') return '提问区'
				return '动态'
			},
			squareHeaderSpacer() {
				const systemInfo = uni.getSystemInfoSync()
				const viewportWidth = systemInfo.windowWidth || 375
				let toolbarRpx = 184
				if (this.squareid == 0) toolbarRpx = this.contentMode === 'qa' ? 166 : 254
				const toolbarHeight = Math.round(toolbarRpx * viewportWidth / 750)
				return { height: (this.StatusBar + toolbarHeight) + 'px' }
			},
			officialTopicPreview() {
				const topics = this.officialTopics || []
				const recommended = topics.filter(topic => Number(topic.isrecommend) === 1)
				return (recommended.length > 0 ? recommended : topics).slice(0, 12)
			}
		},
		onPullDownRefresh() {
			var that = this;
			var stopRefresh = function() {
				uni.stopPullDownRefresh();
			};
				if (that.squareid == 0 && that.contentMode === 'qa') {
					that.loadQuestionList(false, stopRefresh);
					return;
				}
				if (that.follow == 2 && that.squareid == 0) {
					that.changeLoading = 0;
					that.getSpaceList2();
					
					}
				if (that.follow == 3 && that.squareid == 0) {
					that.changeLoading = 0;
					that.getSpaceList3();
					
				}
				if (that.squareid == 3) {
					if(that.sy_appbox)
					{
						that.getSortList();
					}
					
				}
				if (that.squareid == 0 && (that.follow == 0 || that.follow == 1)) {
					
					that.getSpaceList(false);
				}
			
			setTimeout(stopRefresh, 1200)
		},
		onReachBottom() {
			//触底后执行的方法，比如无限加载之类的
			var that = this;
			if (that.squareid == 0 && that.contentMode === 'qa') {
				that.loadQuestionList(true);
				return;
			}
			if (that.follow == 2 && that.squareid == 0) {
				that.changeLoading = 0;
				that.getSpaceList2();
				
				}
			if (that.follow == 3 && that.squareid == 0) {
				that.changeLoading = 0;
				that.getSpaceList3();
				
			}
			if (that.squareid == 0 && that.contentMode === 'space' && (that.follow == 0 || that.follow == 1)) {
				
				that.getSpaceList(true);
				
			}
				
			
			
		},
		onPageScroll(event) {
			this.pageScrollTop = event && Number(event.scrollTop) >= 0 ? Number(event.scrollTop) : this.pageScrollTop
			this.showBackToTop = this.pageScrollTop > 520
			this.collapseSquareMenu()
		},
		onHide() {
			this.stopChatPolling();
			this.stopCampusThemeClock();
		},
		onUnload() {
			this.stopChatPolling();
			this.stopCampusThemeClock();
		},
		onShow() {
			var that = this;
			var restoreSpacePosition = that.spaceReturnPending;
			that.spaceReturnPending = false;
			if (!that.groupChatEnabled && that.squareid == 1) {
				that.squareid = 0;
				that.stopChatPolling();
			}
			that.loadCampusThemeMode();
			that.startCampusThemeClock();
			that.$nextTick(function() {
				// #ifdef APP-PLUS
				if (that.$refs.tabbar) that.$refs.tabbar.activate()
				// #endif
				// #ifdef H5
				if (that.$refs.publishPanel) that.$refs.publishPanel.activatePage()
				// #endif
			})
			if (!restoreSpacePosition) that.page = 1;
			// #ifdef APP-PLUS
			uni.hideTabBar({
				animation: false
			})


			plus.navigator.setStatusBarStyle(that.campusNight ? "light" : "dark")
			// #endif
			if (localStorage.getItem('userinfo')) {
				try {
					that.userInfo = JSON.parse(localStorage.getItem('userinfo'));
					that.userInfo.style = "background-image:url(" + that.userInfo.avatar + ");";
					that.uid = that.userInfo.uid;
				} catch (error) {
					localStorage.removeItem('userinfo');
					that.userInfo = null;
					that.uid = 0;
				}
			}
			if (localStorage.getItem('token')) {

				that.token = localStorage.getItem('token');
			} else {
				that.token = "";
			}
			if (!restoreSpacePosition) that.getTopicCenter();
			if (localStorage.getItem('chatList')) {
				try {
					var cachedChatList = JSON.parse(localStorage.getItem('chatList'));
					that.oldChatList = Array.isArray(cachedChatList) ? cachedChatList : [];
				} catch (error) {
					localStorage.removeItem('chatList');
					that.oldChatList = [];
				}
				// that.chatList = JSON.parse(localStorage.getItem('chatList'));
			}
			that.userStatus();
			that.unreadNum();
			if (restoreSpacePosition) {
				var restoreTop = Number(that.spaceReturnScrollTop) || 0;
				that.$nextTick(function() {
					setTimeout(function() {
						uni.pageScrollTo({ scrollTop: restoreTop, duration: 0 });
					}, 30);
				});
				if (that.token != "" && that.squareid == 1) that.startChatPolling();
				return;
			}
			if (that.squareid == 0 && that.contentMode === 'space' && (that.follow == 0 || that.follow == 1)) {
				that.getSpaceList(false);
			}
			if (that.squareid == 0 && that.contentMode === 'space' && that.follow == 2) {
				that.changeLoading = 0;
				that.getSpaceList2();
				// #ifdef H5
				 window.scrollTo(0, 0); // 将页面滚动到顶部
				 // #endif
				 // #ifdef APP-PLUS
				uni.pageScrollTo({
				  scrollTop: 0,
				  duration: 300
				});
				 // #endif
				}
			if (that.squareid == 0 && that.contentMode === 'space' && that.follow == 3) {
				that.changeLoading = 0;
				that.getSpaceList3();
				// #ifdef H5
				 window.scrollTo(0, 0); // 将页面滚动到顶部
				 // #endif
				 // #ifdef APP-PLUS
				uni.pageScrollTo({
				  scrollTop: 0,
				  duration: 300
				});
				 // #endif
				
			}
			if (that.squareid == 0 && that.contentMode === 'qa' && that.questionList.length === 0) {
				that.loadQuestionList(false);
			}
			if (that.token != "" && that.squareid == 1) that.startChatPolling();

		},
		onLoad() {
			var that = this;
			// #ifdef APP-PLUS || MP
			that.NavBar = this.CustomBar;
			// #endif
			if (localStorage.getItem('getPlugins')) {
				var cachedPlugins = localStorage.getItem('getPlugins');
				if (cachedPlugins) {
					const pluginList = JSON.parse(cachedPlugins);
					// #ifdef APP-PLUS || H5
					that.sy_appbox = pluginList.includes('sy_appbox');
					// #endif
				}
				if(that.sy_appbox){
					that.getAppBoxInfo()
				}
			}

		},
		
		
		mounted() {
			var that = this;
			that.getgg();
			
		},
		methods: {
			backToTop() {
				if (this.squareid !== 0) return
				this.showBackToTop = false
				uni.pageScrollTo({
					scrollTop: 0,
					duration: 260
				})
				setTimeout(() => {
					this.pageScrollTop = 0
					if (this.contentMode === 'qa') {
						this.loadQuestionList(false)
						return
					}
					this.page = 1
					if (this.follow === 2) {
						this.changeLoading = 0
						this.getSpaceList2(false)
					} else if (this.follow === 3) {
						this.changeLoading = 0
						this.getSpaceList3(false)
					} else {
						this.getSpaceList(false)
					}
				}, 280)
			},
			rememberSpaceReturn() {
				this.spaceReturnScrollTop = Number(this.pageScrollTop) || 0;
				this.spaceReturnPending = true;
			},
			collapseSquareMenu() {
				if (this.showSquareMenu) this.showSquareMenu = false
			},
			toggleSquareMenu() {
				if (this.contentMode !== 'space') return;
				this.showSquareMenu = !this.showSquareMenu;
				if (this.showSquareMenu && !this.topicCenterLoading) {
					this.getTopicCenter();
				}
			},
			switchContentMode(mode) {
				if (mode !== 'space' && mode !== 'qa') return;
				this.showSquareMenu = false;
				this.squareid = 0;
				if (this.contentMode === mode) return;
				this.contentMode = mode;
				this.stopChatPolling();
				uni.pageScrollTo({ scrollTop: 0, duration: 0 });
				if (mode === 'qa' && this.questionList.length === 0) {
					this.loadQuestionList(false);
				} else if (mode === 'space' && this.spaceList.length === 0) {
					this.page = 1;
					this.getSpaceList(false);
				}
			},
			loadQuestionList(append, complete) {
				if (this.questionLoadingMore || (append && (this.questionList.length === 0 || this.questionList.length >= this.questionTotal))) {
					if (complete) complete();
					return;
				}
				const targetPage = append ? this.questionPage + 1 : 1;
				this.questionLoadingMore = true;
				if (!append) this.questionLoading = true;
				this.questionMoreText = append ? '加载中...' : '';
				this.$Net.request({
					url: this.$API.qaQuestionList(),
					data: { page: targetPage, limit: this.questionPageSize },
					method: 'get',
					dataType: 'json',
					success: (res) => {
						if (!res.data || res.data.code != 1) {
							if (!append) uni.showToast({ title: res.data && res.data.msg ? res.data.msg : '问题加载失败', icon: 'none' });
							return;
						}
						const list = Array.isArray(res.data.data) ? res.data.data : [];
						this.questionList = append ? this.questionList.concat(list) : list;
						this.questionPage = targetPage;
						this.questionTotal = Number(res.data.total || 0);
						this.questionMoreText = this.questionList.length < this.questionTotal ? '继续上滑加载' : '已经到底了';
					},
					fail: () => {
						if (!append) uni.showToast({ title: '问题加载失败', icon: 'none' });
						this.questionMoreText = this.questionList.length ? '加载失败，稍后重试' : '';
					},
					complete: () => {
						this.questionLoading = false;
						this.questionLoadingMore = false;
						if (complete) complete();
					}
				});
			},
			openQuestion(question) {
				if (!question || !question.id) return;
				this.rememberSpaceReturn();
				uni.navigateTo({ url: '/pages/qa/info?id=' + question.id });
			},
			showAllTopics() {
				this.showSquareMenu = false;
				uni.navigateTo({ url: '/pages/space/topics' });
			},
			loadCampusThemeMode() {
				this.campusThemeMode = getCampusThemeMode()
				applyCampusThemeShell(this.campusThemeMode, this.campusThemeClock)
			},
			handleCampusThemeMode(mode) {
				this.campusThemeMode = mode
				// #ifdef APP-PLUS
				this.$nextTick(() => plus.navigator.setStatusBarStyle(this.campusNight ? 'light' : 'dark'))
				// #endif
			},
			startCampusThemeClock() {
				this.stopCampusThemeClock()
				this.campusThemeClock = Date.now()
				applyCampusThemeShell(this.campusThemeMode, this.campusThemeClock)
				const nextHour = (Math.floor(this.campusThemeClock / (60 * 60 * 1000)) + 1) * 60 * 60 * 1000
				this.campusThemeTimer = setTimeout(() => this.startCampusThemeClock(), nextHour - this.campusThemeClock + 120)
			},
			stopCampusThemeClock() {
				if (!this.campusThemeTimer) return
				clearTimeout(this.campusThemeTimer)
				this.campusThemeTimer = null
			},
			stopChatPolling() {
				if (this.isGetChat) clearInterval(this.isGetChat);
				this.isGetChat = null;
			},
			startChatPolling() {
				this.stopChatPolling();
				if (!this.groupChatEnabled || !this.token || this.squareid != 1) return;
				this.getMyChat(false);
				this.isGetChat = setInterval(() => {
					this.getMyChat(false);
				}, 12000);
			},
			getSortList() {
				const that = this;
				that.$Net.request({
					url: that.$API.PluginLoad('sy_appbox'),
					data: {
						"action": "getSortList",
						"getsort_page": 1,
						"getsort_limit": 50,
						"getsort_order": "sort"
					},
					method: "GET",
					dataType: 'json',
					success: function(res) {
						if (res.data.code == 200) {
							// 添加"小编推荐"作为第一个分区
							that.left_tabbar = [{
								name: '小编推荐',
								slug: 'recommend',
								id: 0
							}, ...res.data.data];
						}
						that.getAppList();
					}
				});
			},
			getAppList(isPage = false) {

				const that = this;
				if (that.loading) return;

				if (!isPage) that.loading = true;
				that.loadStatus = 'loading';
				const page = isPage ? that.page + 1 : 1;

				// 构建筛选条件
				let conditions = {};
				if (that.selectedSystem != '') {
					conditions.system = that.selectedSystem
				}
				if (that.selectedType != '') {
					conditions.type = that.selectedType
				}
				if (that.current === 0) {
					conditions.istop = '1';
				} else if (that.left_tabbar[that.current]) {
					conditions.sort = String(that.left_tabbar[that.current].id);
				}

				that.$Net.request({
					url: that.$API.PluginLoad('sy_appbox'),
					data: {
						"action": "getAppList",
						"getapp_page": page,
						"getapp_limit": that.limit,
						"getapp_order": that.selectedOrder,
						"getapp_if": JSON.stringify(conditions)
					},
					method: "GET",
					dataType: 'json',
					success: function(res) {
						if (res.data.code == 200) {
							const data = res.data.data || [];
							// 确保data是数组类型
							const dataArray = Array.isArray(data) ? data : [];
							const list = dataArray.map(item => ({
								...item,
								tagInfo: that.tagMap[item.type] || {
									text: '未知',
									color: '#999'
								},
								size: that.formatSize(item.size)
							}));

							if (isPage) {
								// 加载更多时，使用数组拓展运算符合并数组
								that.appList = [...that.appList, ...list];
								that.page = page;
							} else {
								// 只有在初加载或刷新时才重置列表
								that.appList = list;
								that.page = 1;
							}

							that.finished = list.length < that.limit;
							that.loadStatus = that.finished ? 'nomore' : 'loadmore';
						}
						that.appLoad = true;
						that.loading = false;
					},
					fail: function() {
						that.appLoad = true;
						that.loading = false;
						that.loadStatus = 'loadmore';
					}
				});
			},
			// 点击左边的栏目切换
			async swichMenu(index) {
				if (index == this.current) return; // 如果点击当前项则不处理

				this.current = index;
				this.page = 1;
				this.finished = false;
				this.loading = false;
				// 重置应用列表，避免显示上一个分类的数据
				this.appList = [];
				this.loadStatus = 'loadmore';
				this.getAppList();
				if (this.menuHeight == 0 || this.menuItemHeight == 0) {
					await this.getElRect('menu-scroll-view', 'menuHeight');
					await this.getElRect('u-tab-item', 'menuItemHeight');
				}
				// 将菜单菜单活动item垂直居中
				this.scrollTop = index * this.menuItemHeight + this.menuItemHeight / 2 - this.menuHeight / 2;

			},
			// 获取一个目标元素的高度
			getElRect(elClass, dataVal) {
				new Promise((resolve, reject) => {
					const query = uni.createSelectorQuery().in(this);
					query.select('.' + elClass).fields({
						size: true
					}, res => {
						// 如果节点尚未生成，res值为null，循环调用执行
						if (!res) {
							setTimeout(() => {
								this.getElRect(elClass);
							}, 10);
							return;
						}
						this[dataVal] = res.height;
					}).exec();
				})
			},
			// 触底加载
			handleScrollToLower() {
				console.log('触发触底加载');
				if (this.loadStatus === 'loadmore' && !this.loading&&this.sy_appbox) {
					this.getAppList(true);
				}
			},

			// 筛选相关方法
			showOrderPicker() {
				this.showOrder = true;
			},

			confirmOrder(e) {
				const selectedOption = this.orderOptions[e[0]];
				if (selectedOption) {
					this.selectedOrder = selectedOption.value;
					this.orderText = selectedOption.text;
					this.showOrder = false;
					this.page = 1; // 重置页码
					if(this.sy_appbox){
						this.appList = []; // 重置应用列表
						this.getAppList(false); // 使用false参数重新加载
					}
					
				}
			},

			showSystemFilter() {
				this.showSystem = true;
			},

			confirmSystem() {
				const option = this.systemOptions.find(item => item.value === this.selectedSystem);
				this.systemText = option ? option.label : '系统';
				this.showSystem = false;
				this.page = 1; // 重置页码
				if(this.sy_appbox){
					this.appList = []; // 重置应用列表
					this.getAppList(false); // 使用false参数重新加载
				}
			},

			showTypeFilter() {
				this.showType = true;
			},

			confirmType() {
				const option = this.typeOptions.find(item => item.value === this.selectedType);
				this.typeText = option ? option.label : '类型';
				this.showType = false;
				this.page = 1; // 重置页码
				if(this.sy_appbox){
					this.appList = []; // 重置应用列表
					this.getAppList(false); // 使用false参数重新加载
				}
			},
			// 获取标签信息
			getTagInfo(item) {
				return this.tagInfoMap[item.type] || {
					text: '未知',
					color: '#999999'
				};
			},

			// 文件大小
			formatSize(size) {
				if (!size) return '未知大小';

				if (size >= 1024 * 1024) {
					return (size / (1024 * 1024)).toFixed(1) + 'GB';
				} else if (size >= 1024) {
					return (size / 1024).toFixed(1) + 'MB';
				} else {
					return size + 'KB';
				}
			},
			selectSystem(value) {
				this.selectedSystem = value;
			},
			resetSystem() {
				this.selectedSystem = '';
				this.systemText = '系统';
				this.showSystem = false;
				this.appList = []; // 重置应用列表
				this.getAppList();
			},
			selectType(value) {
				this.selectedType = value;
			},
			resetType() {
				this.selectedType = '';
				this.typeText = '应用类型';
				this.showType = false;
				this.appList = []; // 重置应用列表
				this.getAppList(false);
			},
			toAppInfo(id) {
				uni.navigateTo({
					url: '/pages/plugins/sy_appbox/info?id=' + id
				});
			},
			// 添加取消排序方法
			cancelOrder() {
				this.showOrder = false;
			},
			goAppInfo(item) {
				var that = this;
				uni.navigateTo({
					url: '/pages/plugins/sy_appbox/info'
				});
			},
			loadMore() {
				var that = this;
				that.moreText = "加载中...";
				that.isLoad = 1;
				
				if (that.follow == 2) {
					that.getSpaceList2(true);
					
					}
				if (that.follow == 3) {
					that.getSpaceList3(true);
					
					
				}
				if (that.follow == 0||that.follow == 1) {
					
					this.getSpaceList(true);
					
				}
			},
			//公共缓存
			allCache() {
				var that = this;
				var meta = that.TabCur;
				if (localStorage.getItem('swiperList')) {
					that.swiperList = JSON.parse(localStorage.getItem('swiperList'));
					var timer = setTimeout(function() {
						that.isLoading = 1;
						clearTimeout('timer')
					}, 300)
				}

				if (localStorage.getItem('metaList')) {
					that.metaList = JSON.parse(localStorage.getItem('metaList'));
				}
				if (localStorage.getItem('contentsList_' + meta)) {
					that.contentsList = JSON.parse(localStorage.getItem('contentsList_' + meta));
				}
				if (localStorage.getItem('topContents')) {
					that.topContents = JSON.parse(localStorage.getItem('topContents'));
				}

				if (localStorage.getItem('Topic')) {
					that.Topic = JSON.parse(localStorage.getItem('Topic'));
				}
			},
			getTopPic() {
							var that = this;
							var data = {
								"isrecommend": "1"
							}
							that.$Net.request({
								url: that.$API.getMetasList(),
								data: {
									"searchParams": JSON.stringify(that.$API.removeObjectEmptyKey(data)),
									"limit": 4,
									"page": 1,
								},
								header: {
									'Content-Type': 'application/x-www-form-urlencoded'
								},
								method: "get",
								dataType: 'json',
								success: function(res) {
									if (res.data.code == 1) {
										var list = res.data.data;
										if (list.length > 0) {
											that.Topic = list;
			
										} else {
											that.Topic = [];
										}
										localStorage.setItem('Topic', JSON.stringify(that.Topic));
									}
									var timer = setTimeout(function() {
										that.isLoading = 1;
										clearTimeout('timer')
									}, 300)
								},
								fail: function(res) {
									var timer = setTimeout(function() {
										that.isLoading = 1;
										clearTimeout('timer')
									}, 300)
								}
							})
						},
			getMetaContents(isPage, meta) {
							var that = this;
							var data = {
								"mid": meta,
								"type": "post"
							}
							var page = that.page;
							if (isPage) {
								page++;
							}
							that.$Net.request({
								url: that.$API.getMetaContents(),
								data: {
									"searchParams": JSON.stringify(that.$API.removeObjectEmptyKey(data)),
									"limit": 5,
									"page": page,
									"order": "created"
								},
								header: {
									'Content-Type': 'application/x-www-form-urlencoded'
								},
								method: "get",
								dataType: 'json',
								success: function(res) {
									if (!isPage) {
										that.dataLoad = true;
									}
									that.isLoad = 0;
			
									that.moreText = "加载更多";
									if (res.data.code == 1) {
										var list = res.data.data;
										if (list.length > 0) {
											var contentsList = [];
											//将自定义字段获取并添加到数据
											var curFields = that.$API.GetFields();
											for (var i in list) {
												var fields = list[i].fields;
												if (fields.length > 0) {
													for (var j in fields) {
														if (curFields.indexOf(fields[j].name) != -1) {
															list[i][fields[j].name] = fields[j].strValue;
														}
													}
												}
												contentsList.push(list[i]);
											}
											if (isPage) {
												that.page++;
												that.contentsList = that.contentsList.concat(contentsList);
											} else {
												that.contentsList = contentsList;
											}
			
			
											localStorage.setItem('contentsList_' + meta, JSON.stringify(that
												.contentsList));
										} else {
											that.moreText = "没有更多啦~";
										}
									}
								},
								fail: function(res) {
			
									that.moreText = "加载更多";
									that.isLoad = 0;
								}
							})
			},
			getgg() {
			  var that = this;
			      uni.request({
			        url:that.$API.SMgonggao(),
			        method:'GET',
			        dataType:"json",
			        success(res) {
					  that.sousuok = res.data.sousuok;
					  
			        },
			        fail(error) {
			          console.log(error);
			        }
			      })
			},
			
			toCategoryContents(title, id) {
							var that = this;
							var type = "meta";
							uni.navigateTo({
								url: '/pages/contents/contentlist?title=' + title + "&type=" + type + "&id=" + id
							});
						},
			
			
			metaLoadMore() {
				if (this.squareid==2){
					console.log('metaLoadMore');
					this.metaCircleMoreTxt = "加载中...";
				}

			},
			
			getUserList(isPage){
				var that = this;
				var page = that.page;
				if(isPage){
					page++;
				}
				that.$Net.request({
					url: that.$API.followList(),
					data:{
						"uid":this.uid,
						"limit":10,
						"page":page,
					},
					header:{
						'Content-Type':'application/x-www-form-urlencoded'
					},
					method: "get",
					dataType: 'json',
					success: function(res) {
						that.isLoad=0;
						if(res.data.code==1){
							var list = res.data.data;
							if(list.length>0){
								
								var userList = [];
								for(var i in list){
									var arr = list[i];
									arr.style = "background-image:url("+list[i].userJson.avatar+");"
									userList.push(arr);
								}
								if(isPage){
									that.page++;
									that.userList = that.userList.concat(userList);
								}else{
									that.userList = userList;
								}
							}else{
								//that.moreText="没有更多数据了";
							}
						}
						var timer = setTimeout(function() {
							that.isLoading=1;
							clearTimeout('timer')
						}, 300)
					},
					fail: function(res) {
						that.isLoad=0;
						that.moreText="加载更多";
						var timer = setTimeout(function() {
							that.isLoading=1;
							clearTimeout('timer')
						}, 300)
					}
				})
			},
			ChooseCheckbox(j) {
				let items = this.checkbox;
				for (let i = 0, lenI = items.length; i < lenI; ++i) {
					this.checkbox[i].checked = false;
				}
				this.checkbox[j].checked = !this.checkbox[j].checked;
			},
			handleClick() {
				this.squareid=0;
			    this.setSquare(0);
			      },
			handleClick2() {
				this.squareid=2;
			    this.setSquare(2);
			      },
			handleClick3() {
				this.squareid=3;
			    this.setSquare(3);
			      },
			handleClick1() {
				if (!this.groupChatEnabled) {
					this.squareid = 0;
					this.stopChatPolling();
					return false;
				}
				this.squareid=1;
		    this.setSquare(1);
		      },
			setSquare(type) {
				var that = this;
				if (type == 1 && !that.groupChatEnabled) {
					that.squareid = 0;
					that.stopChatPolling();
					return false;
				}
				that.page = 1;
				that.contentMode = 'space';
				that.squareid = type;
				that.stopChatPolling();
				if (type == 0) {
					if (that.follow == 2) {
						that.changeLoading = 0;
						that.getSpaceList2();
					} else if (that.follow == 3) {
						that.changeLoading = 0;
						that.getSpaceList3();
					} else {
						that.getSpaceList(false);
					}
				}
				if (type == 1) {
					that.startChatPolling();

				}
				if (type == 2){
					that.moreText = "加载中...";
					that.isLoad = 1;
					this.getTopicCenter();
				}
				if (type == 3) {
					if(that.sy_appbox){
						that.getSortList();
					}
					
				}
			},
			
			getLatestUsers() {
				const that = this;
				that.$Net.request({
					url: that.$API.getUserList(),
					data: {
						"searchParams": "",
						"limit": 4,
						"page": 1,
						"searchKey": "",
						"order": "created"
					},
					method: "post",
					dataType: 'json',
					success: function(res) {
						if(res.data.code == 1) {
							const users = res.data.data || [];
							// 确保users是数组类型
							const usersArray = Array.isArray(users) ? users : [];
							that.latestUserAvatar = usersArray.map(user => ({
								userJson: {
									avatar: user.avatar
								}
							}));
						}
					}
				})
			},
			setFollow(type) {
				var that = this;
				that.page = 1;
				that.squareid = 0;
				that.contentMode = 'space';
				that.selectedTopics = [];
				that.follow = type;
				that.stopChatPolling();
				if (type == 2) {
					that.changeLoading = 0;
					that.getSpaceList2();
					}
				if (type == 3) {
					that.changeLoading = 0;
					that.getSpaceList3();
					
				}
				if (type == 1||type == 0) {
					that.getSpaceList(false);
				}
			},
			searchClose() {
				var that = this;
				that.searchText = "";
				that.page = 1;
				that.getUserList(false);
			},
			formatDate(datetime) {
			  var now = new Date();
			  var diff = now - new Date(datetime * 1000);
			  var minuteDiff = Math.floor(diff / 60000);
			  var hourDiff = Math.floor(diff / 3600000);
			  var dayDiff = Math.floor(diff / 86400000);
			  var weekDiff = Math.floor(dayDiff / 7);
			  var monthDiff = Math.floor(diff / 2592000000);
			  var yearDiff = Math.floor(diff / 31536000000);
			
			  if (diff < 60000) {
			    return Math.floor(diff / 1000) + "秒前";
			  } else if (diff < 3600000) {
			    return minuteDiff + "分钟前";
			  } else if (hourDiff < 24) {
			    return hourDiff + "小时前";
			  } else if (dayDiff < 7 && dayDiff > 0) {
			    return dayDiff + "天前";
			  } else if (weekDiff > 0 && monthDiff <= 1) {
			    return weekDiff + "周前";
			  } else if (monthDiff > 1 && monthDiff < 12) {
			    return monthDiff + "个月前";
			  } else if (yearDiff >= 1) {
			    return yearDiff + "年前";
			  } else {
			    return "刚刚";
			  }
			},
			toInfo(data) {
				var that = this;
				clearInterval(that.chatLoading);
				that.chatLoading = null
				uni.navigateTo({
					url: '/pages/contents/info?cid=' + data.cid + "&title=" + data.title
				});
			},
			getMetaList() {
				var that = this;
				var data = {
					"type": "category"
				}
				that.$Net.request({
					url: that.$API.getMetasList(),
					data: {
						"searchParams": JSON.stringify(that.$API.removeObjectEmptyKey(data)),
						"limit": 15,
						"page": 1,
					},
					header: {
						'Content-Type': 'application/x-www-form-urlencoded'
					},
					method: "get",
					dataType: 'json',
					success: function(res) {
						if (res.data.code == 1) {
							var list = res.data.data;
							if (list.length > 0) {
								var meta = [{
									mid: 0,
									name: "推荐",
									parent: 0
								}];
								that.metaList = meta.concat(list);
			
							} else {
								that.metaList = [];
							}
							localStorage.setItem('metaList', JSON.stringify(that.metaList));
						}
						var timer = setTimeout(function() {
							that.isLoading = 1;
							clearTimeout('timer')
						}, 300)
					},
					fail: function(res) {
						var timer = setTimeout(function() {
							that.isLoading = 1;
							clearTimeout('timer')
						}, 300)
					}
				})
			},
			toPage(title, cid) {
				var that = this;
				clearInterval(that.chatLoading);
				that.chatLoading = null
				uni.navigateTo({
					url: '/pages/contents/info?cid=' + cid + "&title=" + title
				});
			},
			toSearch() {
				var that = this;
				clearInterval(that.chatLoading);
				that.chatLoading = null
				uni.navigateTo({
					url: '/pages/contents/search'
				});
			},
			getTopicCenter() {
				if (this.topicCenterLoading) return;
				this.topicCenterLoading = true;
				this.$Net.request({
					url: this.$API.topicList(),
					data: { token: this.token },
					method: "get",
					dataType: "json",
					success: (res) => {
						if (res.data.code == 1 && res.data.data) {
							this.officialTopics = res.data.data.official || [];
							this.followedTopics = res.data.data.followed || [];
						}
						this.topicCenterLoaded = true;
						this.topicCenterLoading = false;
						this.isLoading = 1;
					},
					fail: () => {
						this.topicCenterLoading = false;
						this.isLoading = 1;
					}
				})
			},
			selectTopic(topic) {
				if (!topic || !topic.mid) return;
				const selected = this.isTopicSelected(topic.mid);
				if (!selected && this.selectedTopics.length >= 3) {
					uni.showToast({ title: '最多同时选择3个话题', icon: 'none' });
					return;
				}
				this.squareid = 0;
				this.contentMode = 'space';
				this.follow = 1;
				this.selectedTopics = selected
					? this.selectedTopics.filter(item => String(item.mid) !== String(topic.mid))
					: this.selectedTopics.concat([{ mid: Number(topic.mid), name: topic.name || '' }]);
				this.page = 1;
				this.spaceList = [];
				this.getSpaceList(false);
			},
			isTopicSelected(mid) {
				return this.selectedTopics.some(item => String(item.mid) === String(mid));
			},
			toggleTopicFollow(topic) {
				if (!this.token) {
					uni.showToast({ title: "请先登录", icon: "none" });
					return;
				}
				const nextType = topic.isFollowed == 1 ? 0 : 1;
				this.$Net.request({
					url: this.$API.topicFollow(),
					data: { token: this.token, mid: topic.mid, type: nextType },
					method: "get",
					dataType: "json",
					success: (res) => {
						if (res.data.code != 1) return;
						const update = list => (list || []).map(item => {
							if (String(item.mid) === String(topic.mid)) {
								this.$set(item, "isFollowed", nextType);
							}
							return item;
						});
						this.officialTopics = update(this.officialTopics);
						if (nextType == 1) {
							const exists = (this.followedTopics || []).some(
								item => String(item.mid) === String(topic.mid));
							if (!exists) this.followedTopics = [topic].concat(this.followedTopics || []);
						} else {
							this.followedTopics = (this.followedTopics || []).filter(
								item => String(item.mid) !== String(topic.mid));
						}
					}
				})
			},
			goPage(url) {
				var that = this;
				clearInterval(that.chatLoading);
				that.chatLoading = null
				uni.navigateTo({
					url: url
				});
			},
			toCategoryContents(title, id) {
				var that = this;
				clearInterval(that.chatLoading);
				that.chatLoading = null
				var type = "meta";
				uni.navigateTo({
					url: '/pages/contents/contentlist?title=' + title + "&type=" + type + "&id=" + id
				});
			},
			userStatus() {
				var that = this;
				that.$Net.request({

					url: that.$API.userStatus(),
					data: {
						"token": that.token
					},
					header: {
						'Content-Type': 'application/x-www-form-urlencoded'
					},
					method: "get",
					dataType: 'json',
					success: function(res) {
						that.isLoading = 1;
						if (res.data.code == 0) {
							localStorage.removeItem('userinfo');
							localStorage.removeItem('token');
							that.token = "";
							that.userinfo = null;
						}
					},
					fail: function(res) {
						uni.showToast({
							title: "网络不太好哦~",
							icon: 'none'
						})
					}
				})
			},
			toLink(text) {
				var that = this;

				if (!localStorage.getItem('token') || localStorage.getItem('token') == "") {
					uni.showToast({
						title: "请先登录哦",
						icon: 'none'
					})
					return false;
				}
				uni.navigateTo({
					url: text
				});
			},
			toGroup() {
				var url = that.$API.GetGroupUrl();
				// #ifdef APP-PLUS
				plus.runtime.openURL(url)
				// #endif
				// #ifdef H5
				window.open(url)
				// #endif
			},
			getTopPic2(){
							var that = this;
							var data = {
								"type":'category',
							}
							var page = that.metaPage;
							// if(isPage){
							// 	page++;
							// }
							
							that.$Net.request({
								url: that.$API.getMetasList(),
								data:{
									"searchParams":JSON.stringify(that.$API.removeObjectEmptyKey(data)),
									"limit": 20,
									"order":"order",
									"page":page,
								},
								header:{
									'Content-Type':'application/x-www-form-urlencoded'
								},
								method: "get",
								dataType: 'json',
								success: function(res) {
									that.isMetasLoading=1;
									that.isLoad=0;
									if(res.data.code==1){
										var list = res.data.data;
										if(list.length>0){
											var Topic = list;
											// if(isPage){
												that.metaPage++;
												that.metaCircleList = that.metaCircleList.concat(Topic);
											// }else{
											// 	that.metaCircleList = Topic;
											// }
										}else{
											//that.metaCircleMoreTxt="没有更多数据了";
										}
										
									}
								},
								fail: function(res) {
									that.isMetasLoading=1;
									that.isLoad=0;
									that.metaCircleMoreTxt="加载更多";
								}
							})
						},
			unreadNum() {
				var that = this;
				that.$Net.request({

					url: that.$API.unreadNum(),
					data: {
						"token": that.token
					},
					header: {
						'Content-Type': 'application/x-www-form-urlencoded'
					},
					method: "get",
					dataType: 'json',
					success: function(res) {
						if (res.data.code == 1) {
							that.noticeSum = res.data.data;
						}
					},
					fail: function(res) {
						uni.showToast({
							title: "网络不太好哦~",
							icon: 'none'
						})
					}
				})
			},
			getAppBoxInfo(){
				var that = this;
				uni.request({
					url: that.$API.PluginLoad('sy_appbox'),
					data: {
						"action": "getConfig"
					},
					method: 'GET',
					dataType: "json",
					success(res) {
						if(res.data.code == 200) {
							that.appModOrder = res.data.data.appModOrder;
							
						} else {
							console.log(res.data.msg)
						}
					},
					fail(error) {
						that.apploading = false;
						console.log(error);
						uni.showToast({
							title: "网络开小差了",
							icon: 'none'
						});
					}
				});
			},
			//群聊(性能考虑，只加载前30条)
			getMyChat(isPage) {
				var that = this;
				var page = that.page;
				if (isPage) {
					page++;
				}
				if (that.token == "") {
					uni.showToast({
						title: "请先登录",
						icon: 'none',
						duration: 1000,
						position: 'bottom',
					});
					return false
				}
				that.$Net.request({
					url: that.$API.allChat(),
					data: {
						"token": that.token,
						"limit": 30,
						"page": page,
						"type": that.type,
						"order": "lastTime"
					},
					header: {
						'Content-Type': 'application/x-www-form-urlencoded'
					},
					method: "get",
					dataType: 'json',
					success: function(res) {
						that.isLoading = 1;
						that.isLoad = 0;
						if (res.data.code == 1) {
							var list = res.data.data;
							if (list.length > 0) {
								var chatList = [];
								for (var i in list) {
									var arr = list[i];
									arr.isNew = 0;
									arr.unRead = 0;
									chatList.push(arr);
								}
								if (isPage) {
									that.page++;
									that.chatList = that.chatList.concat(chatList);
								} else {
									var oldChatList = [];
									if (that.oldChatList != null) {
										oldChatList = that.oldChatList;
									}
									if (oldChatList.length > 0) {

										if (!that.arraysEqual(oldChatList, chatList)) {
											console.log("开始对比")
											for (var c in chatList) {
												for (var d in oldChatList) {
													if (oldChatList[d].id == chatList[c].id) {
														if (oldChatList[d].lastTime < chatList[c].lastTime) {
															console.log("赋值完成")
															chatList[c].isNew = 1;

															var unRead = chatList[c].msgNum - oldChatList[d]
																.msgNum;
															if (unRead <= 0) {
																unRead = 0;
															}
															chatList[c].unRead = unRead;
														}
													}

												}
											}
											that.oldChatList = chatList;
											that.chatList = chatList;
											localStorage.setItem('AllchatList', JSON.stringify(chatList));
										}


									} else {
										that.oldChatList = chatList;
										that.chatList = chatList;
										localStorage.setItem('AllchatList', JSON.stringify(chatList));
									}
								}
							} else {
								// that.moreText="没有更多消息了";
							}

						}
					},
					fail: function(res) {
						that.isLoading = 1;
						that.isLoad = 0;
						// that.moreText="加载更多";
					}
				})
			},
			arraysEqual(a, b) {
				if (a === b) return true;
				if (a == null || b == null) return false;
				if (a.length != b.length) return false;
				for (var c in a) {
					var match = false;
					for (var d in b) {
						if (String(b[d].id) === String(a[c].id)) {
							match = b[d].lastTime == a[c].lastTime;
							break;
						}
					}
					if (!match) return false;
				}
				return true;
			},
			chatFormatDate(datetime) {
				var datetime = new Date(parseInt(datetime * 1000));
				// 获取年月日时分秒值  slice(-2)过滤掉大于10日期前面的0
				var year = datetime.getFullYear();
				var month = ("0" + (datetime.getMonth() + 1)).slice(-2);
				var date = ("0" + datetime.getDate()).slice(-2);
				var hour = ("0" + datetime.getHours()).slice(-2);
				var minute = ("0" + datetime.getMinutes()).slice(-2);
				var time = year + "" + month + "" + date;

				var result = hour + ":" + minute;
				var curDate = new Date();
				var curYear = curDate.getFullYear(); //获取完整的年份(4位)
				var curMonth = ("0" + (curDate.getMonth() + 1)).slice(-2);
				var curDay = ("0" + curDate.getDate()).slice(-2); //获取当前日(1-31)
				var curTime = curYear + "" + curMonth + "" + curDay;
				if (year == curYear) {
					if (year == curYear) {
						if (date == curDay) {
							result = hour + ":" + minute;
						} else {
							result = month + "-" + date;
						}
					} else {
						result = month + "-" + date;
					}
				} else {
					result = month + "-" + date;
				}
				return result;
			},
			goChat(data) {
				var that = this;
				if ((data.type == 1 && !that.groupChatEnabled) || (data.type == 0 && !that.privateChatEnabled)) {
					uni.showToast({
						title: '该功能暂未开放',
						icon: 'none',
						duration: 1200,
						position: 'bottom'
					});
					return false;
				}
				var chatid = data.id;
				clearInterval(that.chatLoading);
				that.chatLoading = null
				//去除未读标志
				var chatlist = that.chatList;
				for (var i in chatlist) {
					if (chatlist[i].id == chatid) {
						chatlist[i].isNew = 0;
						chatlist[i].unRead = 0;
					}
				}
				that.chatList = chatlist;
				that.oldChatList = that.chatList;
				localStorage.setItem('AllchatList', JSON.stringify(that.chatList));
				//结束
				if (data.type == 0) {
					var name = data.userJson.name;
					var uid = data.userJson.uid;

					uni.navigateTo({
						url: '/pages/chat/chat?uid=' + uid + "&name=" + name + "&chatid=" + chatid + "&type=0"
					});
				}
				if (data.type == 1) {
					var name = data.name;

					uni.navigateTo({
						url: '/pages/chat/chat?&name=' + name + '&chatid=' + chatid + '&type=1'
					});
				}

			},
			getSpaceList2(isPage){
				var that = this;
				var page = that.page;
				var token = "";
				if(localStorage.getItem('userinfo')){
					try {
						var userInfo = JSON.parse(localStorage.getItem('userinfo'));
						token=userInfo && userInfo.token ? userInfo.token : that.token;
					} catch (error) {
						localStorage.removeItem('userinfo');
					}
				}
				
				if(isPage){
					page++;
				}
					that.$Net.request({
						url: that.$API.spaceList(),
						data:{
							"limit":10,
							"page":page,
							"order":"created",
							"searchKey":"#视频#",
							"token":token
						},
						method: "get",
						dataType: 'json',
						success: function(res) {
							that.changeLoading = 1;
							that.isLoad=0;
							that.moreText="加载更多";
							if(!isPage){
								that.dataLoad = true;
							}
							if(res.data.code==1){
								var list = res.data.data;
								var spaceList = [];
								for(var i in list){
									if(list[i].type==0){
										if(list[i].pic){
											var pic = list[i].pic;
											list[i].picList = pic.split("||");
										}else{
											list[i].picList = [];
										}
										
									}
									if(list[i].type==2){
										if(list[i].forwardJson.pic){
											var pic = list[i].forwardJson.pic;
											list[i].forwardJson.picList = pic.split("||");
										}else{
											list[i].forwardJson.picList = [];
										}
										
									}
								}
								spaceList = list;
								if(list.length>0){
									if(isPage){
										that.page++;
										that.spaceList = that.spaceList.concat(spaceList);
									}else{
										that.spaceList = spaceList;
									}
									
								}else{
									that.moreText="没有更多动态了";
								}
							}
						},
						fail: function(res) {
							
							that.changeLoading = 1;
							that.isLoad=0;
							that.moreText="加载更多";
							var timer = setTimeout(function() {
								that.isLoading=1;
								clearTimeout('timer')
							}, 300)
						}
					})
				
			},
			getSpaceList3(isPage){
				var that = this;
				var page = that.page;
				var token = "";
				
				if(localStorage.getItem('userinfo')){
					var userInfo = JSON.parse(localStorage.getItem('userinfo'));
					token=userInfo && userInfo.token ? userInfo.token : that.token;
				}
				if(isPage){
					page++;
				}
					that.$Net.request({
						url: that.$API.spaceList(),
						data:{
							"limit":10,
							"page":page,
							"order":"created",
							"searchKey":"#图集#",
							"token":token
						},
						method: "get",
						dataType: 'json',
						success: function(res) {
							that.changeLoading = 1;
							that.isLoad=0;
							that.moreText="加载更多";
							if(!isPage){
								that.dataLoad = true;
							}
							if(res.data.code==1){
								var list = res.data.data;
								var spaceList = [];
								for(var i in list){
									if(list[i].type==0){
										if(list[i].pic){
											var pic = list[i].pic;
											list[i].picList = pic.split("||");
										}else{
											list[i].picList = [];
										}
										
									}
									if(list[i].type==2){
										if(list[i].forwardJson.pic){
											var pic = list[i].forwardJson.pic;
											list[i].forwardJson.picList = pic.split("||");
										}else{
											list[i].forwardJson.picList = [];
										}
										
									}
								}
								spaceList = list;
								if(list.length>0){
									if(isPage){
										that.page++;
										that.spaceList = that.spaceList.concat(spaceList);
									}else{
										that.spaceList = spaceList;
									}
									
								}else{
									that.moreText="没有更多动态了";
								}
							}
						},
						fail: function(res) {
							
							that.changeLoading = 1;
							that.isLoad=0;
							that.moreText="加载更多";
							var timer = setTimeout(function() {
								that.isLoading=1;
								clearTimeout('timer')
							}, 300)
						}
					})
				
			},
			getSpaceList(isPage) {
				var that = this;
				var page = that.page;
				var topicIds = that.selectedTopics.map(item => Number(item.mid));
				var topicFilterKey = topicIds.join(',');
				if (isPage) {
					page++;
				}
				that.$Net.request({
					url: that.$API.spaceList(),
					data: {
						"limit": 10,
						"page": page,
						"order": "created",
						"token": that.token,
						"searchParams": topicIds.length > 0
							? JSON.stringify({ topicIds: topicIds }) : ""
					},
					method: "get",
					dataType: 'json',
					success: function(res) {
						if (that.selectedTopics.map(item => Number(item.mid)).join(',') !== topicFilterKey) return;
						that.isLoading = 1;
						that.isLoad = 0;
						that.moreText = "加载更多";
						if (!isPage) {
							that.dataLoad = true;
						}
						if (res.data.code == 1) {
							var list = res.data.data;
							var spaceList = [];
							for (var i in list) {
								if (list[i].type == 0) {
									if (list[i].pic) {
										var pic = list[i].pic;
										list[i].picList = pic.split("||");
									} else {
										list[i].picList = [];
									}

								}
								if (list[i].type == 2) {
									if (list[i].forwardJson.pic) {
										var pic = list[i].forwardJson.pic;
										list[i].forwardJson.picList = pic.split("||");
									} else {
										list[i].forwardJson.picList = [];
									}

								}
							}
							spaceList = list;
							if (list.length > 0) {
								if (isPage) {
									that.page++;
									that.spaceList = that.spaceList.concat(spaceList);
								} else {
									that.spaceList = spaceList;
								}

							} else {
								that.moreText = "没有更多动态了";
							}
						}
					},
					fail: function(res) {
						if (that.selectedTopics.map(item => Number(item.mid)).join(',') !== topicFilterKey) return;
						that.isLoading = 1;
						that.moreText = "加载更多";
						that.isLoad = 0;
					}
				})
			},

			goUserInfo() {

				var that = this;
				if (!localStorage.getItem('token') || localStorage.getItem('token') == "") {
					uni.navigateTo({
						url: '/pages/user/login'
					});
					return false;
				}
				uni.switchTab({
					url: '/pages/home/user'
				});
			},
			goLogin() {
				uni.navigateTo({
					url: '/pages/user/login'
				});
			},
			goRegister() {
				uni.navigateTo({
					url: '/pages/user/register'
				});
			},
			
			},
		// #ifdef APP-PLUS
		components: {
			waves,
			Tabbar,
			metas
		},
		// #endif

		// #ifdef MP
		components: {
			waves,metas
		},
		// #endif

	}
</script>

<style lang="scss" scoped>
	.tab-wrap-index {
		color:#454545;
	  position: relative;
	  z-index: 1;
	}
	.tab-wrap-index::after {
	  position: absolute;
	  border-radius: 50px;
	  color:#797979;
	  right: 5%;
	  bottom: 6rpx;
	  z-index: -1;
	  display: block;
	  content: "";
	  width: 100%;
	  height:13rpx;
	  background-color: #3cc9a4;
	}
	.square-box{
		    font-weight: bold;
		    font-size: 17px;
	}
	.square-box2{
		font-weight: bold;
		    font-size: 14px;
	}
	.square-box, .square-box2 {
	  transition: color 220ms ease, transform 220ms cubic-bezier(0.22, 1, 0.36, 1);
	}
	.position-fixed2 {
	  position: fixed;
	  bottom: 160px;
	  right: 30px;
	}
	.position-fixed {
	  position: fixed;
	  bottom: 90px;
	  right: 30px;
	}
	
	.round-button {
	  width: 50px;
	  height: 50px;
	  border-radius: 50%;
	  background: linear-gradient(to bottom right, #acffcc, #47bf7f);
	  display: flex;
	  color: #fff;
	  justify-content: center;
	  align-items: center;
	  box-shadow: #33a26059 0px 3px 5px 0px;
	}
	.round-button2 {
	  width: 50px;
	  height: 50px;
	  border-radius: 50%;
	  background: linear-gradient(to bottom right, #aefffd, #2182bf);
	  display: flex;
	  color: #fff;
	  justify-content: center;
	  align-items: center;
	  box-shadow: #33a26059 0px 3px 5px 0px;
	}
	.appcontent {
		margin-left: 12upx;
		margin-right: 12upx;
	}
	.font-size-small {
		font-size: small;
	}
.dropdown {
  position: relative;
  cursor: pointer;
}

.dropdown i.arrow {
  position: absolute;
  right: 10px;
  top: 50%;
  transform: translateY(-50%);
  width: 0;
  height: 0;
  border-style: solid;
  border-width: 0 6px 6px 6px;
  border-color: transparent transparent #c3c3c3 transparent;
}

.dropdown-menu {
  opacity: 0;
  transition: opacity 180ms ease, transform 240ms cubic-bezier(0.22, 1, 0.36, 1);
  position: absolute;
  bottom: 100%;
  right: 0px;
  z-index: 9999;
  display: none;
  min-width: 80px;
  padding: 10px;
  text-align: center;
  margin: 0;
  list-style: none;
  background-color: #ffffff;
  border-radius: 20px;
  box-shadow: 0 0px 8px rgba(0, 0, 0, 0.2);
}

.dropdown-menu li {
  font-weight: normal;
  transition: color 180ms ease, transform 180ms ease;
  color: #666;
  font-size: 14px;
  display: block;
  padding: 5px 10px;
  cursor: pointer;
}
 
.dropdown-menu li.active {
  font-weight: bold;
  transition: color 180ms ease, transform 180ms ease;
  color: #232323;
  font-size: 16px;
}

.dropdown:hover .dropdown-menu {
  display: block;
  opacity: 1 !important;
}
.header {
    z-index: 995;
	}

	.uni-swiper-dot.uni-swiper-dot-active {
		background-color: #00bcd4 !important;
		opacity: 0.8;
	}

	.uni-swiper-dot {
		border-radius: 50upx !important;
	}

	.uni-swiper-slides swiper-item {
		padding: 0 0;
		box-sizing: border-box;
	}

	.screen-swiper {
		min-height: 300upx;
	}

	.campus-square {
		min-height: 100vh;
		padding-bottom: 164rpx;
		padding-bottom: calc(164rpx + env(safe-area-inset-bottom));
		background: #f5f7f9 !important;
		color: #304150;
	}

	.square-header {
		z-index: 995;
		background: rgba(249, 250, 251, 0.98) !important;
		border-bottom: 1rpx solid rgba(219, 226, 230, 0.9) !important;
		box-shadow: 0 8rpx 28rpx rgba(42, 57, 68, 0.05) !important;
		overflow: visible !important;
	}

	.square-back-top {
		position: fixed;
		right: 24rpx;
		bottom: calc(172rpx + env(safe-area-inset-bottom));
		z-index: 980;
		display: flex;
		align-items: center;
		justify-content: center;
		width: 76rpx;
		height: 76rpx;
		border: 1rpx solid rgba(35, 124, 116, 0.16);
		border-radius: 50%;
		background: rgba(255, 255, 255, 0.96);
		color: #237c74;
		font-size: 34rpx;
		box-shadow: 0 10rpx 26rpx rgba(41, 67, 73, 0.16);
		transition: transform 180ms ease, background-color 180ms ease, color 180ms ease;
	}

	.square-back-top:active {
		transform: scale(0.9);
		background: #e6f3f1;
	}

	.square-mainbar {
		display: grid;
		grid-template-columns: 78rpx 1fr 78rpx;
		align-items: center;
		height: 96rpx;
		padding: 0 22rpx;
		box-sizing: border-box;
	}

	.square-page-title {
		font-size: 34rpx;
		font-weight: 700;
		text-align: center;
		color: #293b48;
	}

	.square-tool-button {
		display: flex;
		align-items: center;
		justify-content: center;
		width: 78rpx;
		height: 78rpx;
		border-radius: 50%;
		font-size: 38rpx;
		color: #29363d;
		transition: transform 180ms ease, background-color 180ms ease;
	}

	.square-tool-button:active {
		transform: scale(0.9);
		background: #edf2f3;
	}

	.square-tool-button.is-placeholder {
		opacity: 0;
		pointer-events: none;
	}

	.square-section-tabs {
		width: 100%;
		height: 70rpx;
		border-top: 1rpx solid #edf1f3;
		background: rgba(255, 255, 255, 0.72);
		white-space: nowrap;
		box-sizing: border-box;
	}

	.square-section-track {
		display: inline-flex;
		align-items: center;
		justify-content: center;
		gap: 12rpx;
		min-width: 100%;
		height: 70rpx;
		padding: 0 22rpx;
		box-sizing: border-box;
	}

	.square-section-item {
		display: inline-flex;
		align-items: center;
		justify-content: center;
		min-width: 106rpx;
		height: 50rpx;
		padding: 0 20rpx;
		border-radius: 8rpx;
		font-size: 25rpx;
		font-weight: 500;
		color: #87949e;
		box-sizing: border-box;
		transition: color 220ms ease, background-color 220ms ease, transform 220ms ease;
	}

	.square-section-item.is-active {
		background: #e6f4f2;
		font-weight: 700;
		color: #237c74;
	}

	.square-section-item:active {
		transform: scale(0.95);
	}

	.square-filter-row {
		display: flex;
		align-items: center;
		justify-content: space-between;
		min-height: 88rpx;
		height: auto;
		padding: 0 24rpx;
		border-top: 1rpx solid #edf1f3;
		background: rgba(255, 255, 255, 0.58);
		box-sizing: border-box;
		transition: background-color 180ms ease;
	}

	.square-filter-row:active { background: rgba(230, 243, 241, 0.86); }

	.square-qa-list {
		width: calc(100% - 24rpx);
		max-width: 760px;
		margin: 12rpx auto 0;
		border: 1rpx solid #e2e8e6;
		border-radius: 8rpx;
		background: #ffffff;
		overflow: hidden;
		box-sizing: border-box;
	}

	.square-qa-loading {
		display: flex;
		align-items: center;
		justify-content: center;
		min-height: 260rpx;
	}

	.square-qa-more {
		padding: 28rpx 20rpx;
		border-top: 1rpx solid #edf0ef;
		font-size: 23rpx;
		color: #909a96;
		text-align: center;
	}

	.square-filter-arrow {
		font-size: 25rpx;
		color: #87939c;
		transition: transform 240ms ease;
	}

	.square-filter-arrow.is-open {
		transform: rotate(180deg);
	}

	.square-filter-menu {
		position: absolute;
		top: 100%;
		left: 24rpx;
		right: 24rpx;
		padding: 28rpx;
		border: 1rpx solid #e5eaed;
		border-radius: 24rpx;
		background: rgba(255, 255, 255, 0.97);
		box-shadow: 0 22rpx 58rpx rgba(35, 51, 61, 0.16);
		opacity: 0;
		visibility: hidden;
		transform: translateY(-12rpx) scale(0.98);
		transform-origin: top center;
		transition: opacity 180ms ease, transform 280ms cubic-bezier(0.22, 1, 0.36, 1), visibility 0s linear 280ms;
	}

	.square-filter-menu.is-open {
		opacity: 1;
		visibility: visible;
		transform: translateY(10rpx) scale(1);
		transition-delay: 0s;
	}

	.filter-menu-title {
		font-size: 22rpx;
		font-weight: 600;
		color: #8a969e;
	}

	.filter-menu-options {
		display: grid;
		grid-template-columns: repeat(4, minmax(0, 1fr));
		gap: 12rpx;
		margin-top: 16rpx;
	}

	.filter-menu-options view {
		display: flex;
		align-items: center;
		justify-content: center;
		height: 64rpx;
		border-radius: 18rpx;
		background: #f3f6f7;
		font-size: 24rpx;
		color: #65747d;
		transition: transform 180ms ease, color 180ms ease, background-color 180ms ease;
	}

	.filter-menu-options view.is-active {
		background: #e6f3f1;
		font-weight: 600;
		color: #237c74;
	}

	.service-title {
		display: flex;
		align-items: center;
		justify-content: space-between;
		margin-top: 26rpx;
	}

	.filter-topic-count {
		font-weight: 600;
		color: #237c74;
	}

	.filter-menu-services {
		display: flex;
		gap: 14rpx;
		margin-top: 16rpx;
	}

	.filter-menu-services > view {
		display: flex;
		flex: 1;
		align-items: center;
		justify-content: center;
		gap: 10rpx;
		height: 72rpx;
		border: 1rpx solid #e7ecee;
		border-radius: 18rpx;
		font-size: 24rpx;
		color: #4f6069;
	}

	.filter-menu-topics {
		margin-top: 16rpx;
		border: 1rpx solid #e7ecee;
		border-radius: 18rpx;
		background: #f9fbfb;
		overflow: hidden;
	}

	.filter-topic-scroll {
		width: 100%;
		white-space: nowrap;
	}

	.filter-topic-track {
		display: inline-flex;
		align-items: center;
		gap: 12rpx;
		min-width: max-content;
		padding: 14rpx 16rpx;
		box-sizing: border-box;
	}

	.filter-topic-chip,
	.filter-topic-more,
	.filter-topic-empty {
		display: flex;
		align-items: center;
		justify-content: center;
		gap: 8rpx;
		height: 58rpx;
		box-sizing: border-box;
		font-size: 24rpx;
		white-space: nowrap;
	}

	.filter-topic-chip {
		flex: 0 0 auto;
		max-width: 230rpx;
		padding: 0 18rpx;
		border-radius: 999rpx;
		background: #e7f4ef;
		color: #207966;
	}

	.filter-topic-chip.is-active {
		border: 1rpx solid #16847c;
		background: #16847c;
		font-weight: 600;
		color: #fff;
	}

	.filter-topic-chip text:last-child {
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.filter-topic-more {
		flex: 0 0 auto;
		padding: 0 12rpx 0 18rpx;
		color: #65747d;
	}

	.filter-topic-empty {
		justify-content: space-between;
		margin-top: 16rpx;
		padding: 0 22rpx;
		border: 1rpx solid #e7ecee;
		border-radius: 18rpx;
		color: #4f6069;
	}

	.filter-topic-empty > text:first-child {
		margin-right: 8rpx;
	}

	.filter-topic-empty-more {
		margin-left: auto;
		font-size: 22rpx;
		color: #8a969e;
	}

	.filter-menu-extra-services {
		margin-top: 14rpx;
	}

	.square-pinned-strip {
		width: 100%;
		height: 80rpx;
		border-bottom: 1rpx solid rgba(230, 207, 170, 0.7);
		background: #fff7e7;
		white-space: nowrap;
	}

	.pinned-track {
		display: inline-flex;
		align-items: center;
		gap: 30rpx;
		height: 80rpx;
		padding: 0 30rpx;
	}

	.pinned-item {
		display: inline-flex;
		align-items: center;
		gap: 14rpx;
		max-width: 61.8vw;
	}

	.pinned-badge {
		flex: 0 0 auto;
		padding: 5rpx 12rpx;
		border-radius: 9rpx;
		background: #258ddf;
		box-shadow: 0 5rpx 13rpx rgba(37, 141, 223, 0.2);
		font-size: 21rpx;
		font-weight: 700;
		color: #fff;
	}

	.pinned-title {
		font-size: 25rpx;
		font-weight: 600;
		color: #374956;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.square-filter-label {
		display: flex;
		align-items: center;
		gap: 10rpx;
		font-size: 25rpx;
		font-weight: 600;
		color: #77858f;
	}

	.square-filter-options {
		display: flex;
		align-items: center;
		gap: 10rpx;
	}

	.square-filter-options text {
		padding: 8rpx 14rpx;
		border-radius: 18rpx;
		font-size: 22rpx;
		color: #89969f;
		transition: color 180ms ease, background-color 180ms ease;
	}

	.square-filter-options text.is-active {
		background: #dff2ef;
		color: #167f77;
	}

	.campus-square .appcontent {
		margin: 0 12rpx !important;
		padding-top: 0;
	}

	.campus-square .appcontent.margin-top-xl {
		margin-top: 0 !important;
	}

	/* Desktop keeps the feed at a readable width and centered under the header. */
	@media (min-width: 760px) {
		.campus-square .appcontent {
			width: 560px;
			max-width: calc(100vw - 48px);
			margin-right: auto !important;
			margin-left: auto !important;
		}
	}

	.campus-square .no-data {
		margin: 0;
		border: 0;
		border-radius: 0;
		background: transparent;
		box-shadow: none;
	}

	.campus-square .square-empty {
		display: flex;
		min-height: 38.2vh;
		padding: 40rpx 0 80rpx;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		font-size: 27rpx;
		color: #87949c;
	}

	.campus-square .square-empty .cuIcon-text {
		margin-bottom: 22rpx;
		font-size: 88rpx;
		color: #d8dddf;
	}

	.campus-square .square-empty .cu-btn {
		min-width: 150rpx;
		height: 62rpx;
		margin-top: 24rpx;
		border-radius: 18rpx !important;
		background: #168cf0 !important;
		font-size: 25rpx;
		line-height: 62rpx;
		color: #fff !important;
	}

	.campus-square ::-webkit-scrollbar {
		display: none;
		width: 0;
		height: 0;
	}

	.campus-square .cu-list.menu-avatar {
		background: transparent;
	}

	.campus-square .cu-list.menu-avatar > .cu-item {
		border-radius: 24rpx;
		background: #fff;
		box-shadow: 0 10rpx 30rpx rgba(49, 65, 76, 0.05);
	}

	@media (prefers-reduced-motion: reduce) {
		.square-section-item,
		.square-tool-button {
			transition: none;
		}
	}

	@media (max-width: 360px) {
		.square-mainbar { grid-template-columns: 72rpx 1fr 72rpx; padding: 0 18rpx; }
		.square-filter-menu { right: 18rpx; left: 18rpx; padding: 22rpx; }
		.campus-square .appcontent { margin-right: 10rpx !important; margin-left: 10rpx !important; }
	}

	.u-wrap {
		display: flex;
		flex-direction: column;
		overflow: hidden;
	}

	.u-search-box {
		padding: 18rpx 30rpx;
	}

	.u-menu-wrap {
		flex: 1;
		display: flex;
		overflow: hidden;
	}

	.u-search-inner {
		background-color: rgb(234, 234, 234);
		border-radius: 100rpx;
		display: flex;
		align-items: center;
		padding: 10rpx 16rpx;
	}

	.u-search-text {
		font-size: 26rpx;
		color: $u-tips-color;
		margin-left: 10rpx;
	}

	.u-tab-view {
		width: 200rpx;
		height: 100%;
	}

	.u-tab-item {
		height: 110rpx; // 保持固定高度
		background: #f6f6f6;
		box-sizing: border-box;
		display: flex;
		align-items: center;
		justify-content: center;
		font-size: 26rpx;
		color: #444;
		font-weight: 400;
		line-height: 1;
	}

	.u-tab-item-active {
		position: relative;
		color: #000;
		font-size: 30rpx;
		font-weight: 600;
		background: #fff;
	}

	.u-tab-item-active::before {
		content: "";
		position: absolute;
		border-left: 4px solid #3cc9a4;
		height: 32rpx;
		left: 0;
		top: 39rpx;
		border-radius: 50rpx;
	}

	.u-tab-view {
		height: 100%;
		overflow: auto;
	}

	.right-box {
		background-color: rgb(250, 250, 250);
		overflow: auto;
	}
	.icon-grid-container {
	  width: 100%;
	  display: flex;
	  justify-content: center;
	  padding: 10rpx 0;
	}

	.icon-grid {
	  display: flex;
	  flex-wrap: wrap;
	  justify-content: flex-start;
	  width: 100%;
	  max-width: 750rpx;
	}

	.icon-item {
	  width: calc(100% / 5);
	  box-sizing: border-box;
	  padding: 10rpx;
	}

	.page-view {
		padding: 16rpx;
	}

	.class-item {
		margin-bottom: 30rpx;
		background-color: #fff;
		padding: 16rpx;
		border-radius: 16rpx;
	}

	.item-title {
		font-size: 26rpx;
		color: $u-main-color;
		font-weight: bold;
	}

	.item-menu-name {
		font-weight: normal;
		font-size: 24rpx;
		color: $u-main-color;
	}

	.thumb-box {
		width: 33.333333%;
		display: flex;
		align-items: center;
		justify-content: center;
		flex-direction: column;
		margin-top: 20rpx;
	}

	.item-menu-image {
		width: 120rpx;
		height: 120rpx;
	}
	.icon5__item--icon {
		width: 62rpx;
		height: 62rpx;
		margin-bottom: 10rpx;
	}
	.icon5__item--icon-2 {
		width: 90rpx;
		height: 90rpx;
		margin-bottom: 10rpx;
	}
	.app-box {
		display: flex;
		justify-content: space-between;
		align-items: center;
		margin-bottom: 10rpx;
	}

	.app-box-body {
		flex: 1;
		display: flex;
		margin-right: 20rpx;
		min-width: 0; // 防止flex子元素溢出
		align-items: center;
	}

	.app-box-logo {
		width: 110rpx;
		height: 110rpx;
		flex-shrink: 0; // 防止图片缩小
	}

	.app-box-content {
		flex: 1;
		margin-left: 20rpx;
		min-width: 0; // 防止flex子元素溢出
	}

	.app-box-title {
		font-size: 30rpx;
		font-weight: bold;
		margin-bottom: 6rpx;
		width: 250rpx;
	}

	.text-cut {
		text-overflow: ellipsis;
		white-space: nowrap;
		overflow: hidden;
	}

	.app-box-info {
		font-size: 26rpx;
		color: #666;
		margin-bottom: 6rpx;

		text {
			margin-right: 10rpx;
		}
	}

	.app-box-tags {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: 8rpx;
	}

	.app-tag {
		padding: 4rpx 12rpx;
		border-radius: 8rpx;
		color: #ffffff;
		font-size: 24rpx;
	}

	.app-category-tag {
		padding: 4rpx 12rpx;
		border-radius: 8rpx;
		background-color: #f5f5f5;
		color: #666666;
		font-size: 24rpx;
	}

	.app-box-down {
		background-color: #3cc9a4;
		color: #fff;
		padding: 10rpx 30rpx;
		border-radius: 100rpx;
		white-space: nowrap; // 防止文字换行
		flex-shrink: 0; // 防止按钮缩小
	}

	.nav {
		white-space: nowrap;
		padding: 0 30rpx;
		height: 90rpx;
		border-bottom: 1px solid #f1f1f1;

		.cu-item {
			height: 90rpx;
			display: inline-block;
			line-height: 90rpx;
			margin: 0 30rpx;
			padding: 0 20rpx;

			&.cur {
				border-bottom: 4rpx solid #3cc9a4;
			}
		}
	}

	.app-box {
		margin-bottom: 20rpx;

		.app-box-tags {
			display: flex;
			flex-wrap: wrap;
			align-items: center;
			color: #666;
			font-size: 26rpx;

			.app-tag {
				padding: 4rpx 8rpx;
				border-radius: 8rpx;
				color: #ffffff;
				font-size: 20rpx;
			}

			.app-category-tag {
				padding: 4rpx 12rpx;
				border-radius: 8rpx;
				background-color: #f5f5f5;
				color: #666666;
				margin-right: 12rpx;
				font-size: 24rpx;
			}
		}
	}
	.des-info{
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
		max-width: 180rpx;
	}
	/* 筛选栏样式 */
	.filter-bar {
		display: flex;
		padding: 20rpx;
		border-bottom: 1px solid #f5f5f5;
	}

	.filter-item {
		flex: 1;
		text-align: center;
		font-size: 28rpx;
		color: #333;
		justify-content: center;
	}

	.filter-item .cuIcon-unfold {
		margin-left: 4rpx;
		font-size: 24rpx;
		color: #999;
	}

	/* 筛选弹窗样式 */
	.filter-popup {
		background: #fff;
		padding: 30rpx;
		border-radius: 20rpx 20rpx 0 0;
	}

	.filter-title {
		font-size: 32rpx;
		font-weight: bold;
		margin-bottom: 30rpx;
	}

	.filter-options {
		display: flex;
		flex-wrap: wrap;
	}

	.filter-option {
		width: 160rpx;
		height: 60rpx;
		line-height: 60rpx;
		text-align: center;
		border: 1px solid #eee;
		border-radius: 30rpx;
		margin: 0 20rpx 20rpx 0;
		font-size: 28rpx;
	}

	.filter-option.active {
		background: #3cc9a4;
		color: #fff;
		border-color: #3cc9a4;
	}

	.filter-buttons {
		display: flex;
		margin-top: 40rpx;
	}

	.btn-reset,
	.btn-confirm {
		flex: 1;
		height: 80rpx;
		line-height: 80rpx;
		text-align: center;
		border-radius: 40rpx;
		margin: 0 20rpx;
		font-size: 30rpx;
	}

	.btn-reset {
		background: #f5f5f5;
		color: #666;
	}

	.btn-confirm {
		background: #3cc9a4;
		color: #fff;
	}

	.text-title-1 {
		overflow: hidden;
		display: -webkit-box;
		-webkit-line-clamp: 1;
		-webkit-box-orient: vertical;
	}

	/* 添加加载动画相关样式 */
	.loading-container {
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		padding: 40rpx 0;

		.loading-text {
			font-size: 26rpx;
			color: #909399;
			margin-top: 20rpx;
		}
	}

	.loading-more {
		display: flex;
		align-items: center;
		justify-content: center;
		padding: 30rpx 0;

		.loading-more-text {
			font-size: 24rpx;
			color: #909399;
		}
	}

	.menu-scroll-view {
		height: 100%;
		overflow: auto;
	}

	.square-data-type-picker {
		display: flex;
		justify-content: flex-end;
		align-items: center;
		padding: 20rpx 30rpx 20rpx 0;
	}
	.filter-item {
		display: flex;
		align-items: center;
		font-size: 28rpx;
		color: #333;
		cursor: pointer;
	}
	.filter-item .cuIcon-unfold {
		margin-left: 8rpx;
		font-size: 24rpx;
		color: #999;
	}

	/* 商城筛选相关样式 */
	.search-bar {
		display: flex;
		align-items: center;
		padding: 20rpx 30rpx 30rpx 0;
	}

	.search-form {
		flex: 1;
		margin-right: 20rpx;
	}

	.filter-btn {
		display: flex;
		align-items: center;
		justify-content: center;
		min-width: 120rpx;
		height: 60rpx;
		background: #f8f8f8;
		border-radius: 30rpx;
		padding: 0 20rpx;
	}

	.filter-text {
		font-size: 26rpx;
		color: #666;
		margin-left: 8rpx;
	}

	.shop-filter-popup {
		background: #fff;
		border-radius: 20rpx 20rpx 0 0;
		padding: 30rpx;
		max-height: 80vh;
		overflow-y: auto;
	}

	.filter-header {
		display: flex;
		justify-content: space-between;
		align-items: center;
		padding-bottom: 30rpx;
		border-bottom: 1px solid #f0f0f0;
		margin-bottom: 30rpx;
	}

	.filter-title {
		font-size: 32rpx;
		font-weight: bold;
		color: #333;
	}

	.filter-reset {
		font-size: 28rpx;
		color: #999;
	}

	.filter-section {
		margin-bottom: 40rpx;
	}

	.filter-section-title {
		font-size: 28rpx;
		font-weight: bold;
		color: #333;
		margin-bottom: 20rpx;
	}

	.filter-tip {
		display: flex;
		align-items: center;
		justify-content: center;
		padding: 30rpx 20rpx;
		background: #f8f9fa;
		border-radius: 16rpx;
		border: 1px dashed #ddd;
	}

	.filter-tip-text {
		font-size: 26rpx;
		color: #999;
		margin-left: 10rpx;
	}

	.filter-options {
		display: flex;
		flex-wrap: wrap;
		gap: 20rpx;
		justify-content: flex-start;
		align-items: flex-start;
	}

	.filter-option {
		padding: 0rpx 24rpx;
		background: #f8f8f8;
		border-radius: 30rpx;
		font-size: 26rpx;
		color: #666;
		border: 1px solid transparent;
		transition: color 180ms ease, background-color 180ms ease, border-color 180ms ease, transform 180ms ease;
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
		min-height: 48rpx;
		display: flex;
		align-items: center;
		justify-content: center;
		text-align: center;
		max-width: calc((100vw - 140rpx) / 3);
		min-width: 80rpx;
		box-sizing: border-box;
		flex-shrink: 0;
	}
	
	/* 针对较小屏幕的优化 */
	@media screen and (max-width: 750rpx) {
		.filter-option {
			max-width: calc((100vw - 120rpx) / 2);
			font-size: 24rpx;
		}
	}

	.filter-option.active {
		background: #e8fff4;
		color: #3cc9a4;
		border-color: #3cc9a4;
	}

	.filter-footer {
		display: flex;
		gap: 20rpx;
		margin-top: 40rpx;
		padding-top: 30rpx;
		border-top: 1px solid #f0f0f0;
	}

	.filter-btn-cancel,
	.filter-btn-confirm {
		flex: 1;
		height: 80rpx;
		line-height: 80rpx;
		text-align: center;
		border-radius: 40rpx;
		font-size: 30rpx;
	}

	.filter-btn-cancel {
		background: #f8f8f8;
		color: #666;
	}

	.filter-btn-confirm {
		background: #3cc9a4;
		color: #fff;
	}

	/* 活跃用户卡片样式 */
	.active-users-container {
		padding: 0 20rpx 30rpx 20rpx;
	}

	.active-users-scroll {
		white-space: nowrap;
	}

	.active-users-list {
		display: flex;
		gap: 24rpx;
		padding: 20rpx 10rpx;
	}

	.active-user-card {
		display: flex;
		flex-direction: column;
		align-items: flex-start;
		width: 260rpx;
		min-width: 260rpx;
		padding: 50rpx 40rpx 50rpx 40rpx;
		border-radius: 24rpx;
		background-color: #f5f5f5;
		transition: transform 240ms cubic-bezier(0.22, 1, 0.36, 1), box-shadow 220ms ease, background-color 220ms ease;
		position: relative;
		overflow: hidden;
	}

	.active-user-card::before {
		content: '';
		position: absolute;
		top: 0;
		left: 0;
		right: 0;
		height: 4rpx;
		opacity: 0;
		transition: opacity 0.3s ease;
	}

	.active-user-card:active {
		transform: translateY(4rpx) scale(0.96);
		box-shadow: 0 4rpx 16rpx rgba(60, 201, 164, 0.16), 0 1rpx 4rpx rgba(0, 0, 0, 0.12);
	}

	.active-user-card:active::before {
		opacity: 1;
	}

	.user-avatar-container {
		position: relative;
		margin-bottom: 20rpx;
	}

	.user-avatar {
		width: 120rpx;
		height: 120rpx;
		border-radius: 64rpx;
		background-size: cover;
		background-position: center;
		transition: transform 0.3s ease;
	}

	.active-user-card:active .user-avatar {
		transform: scale(0.95);
	}

	.user-rz-icon-active {
		position: absolute;
		right: -6rpx;
		bottom: -6rpx;
		width: 44rpx;
		height: 44rpx;
		border-radius: 22rpx;
		box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.2);
	}

	.user-name {
		font-size: 30rpx;
		font-weight: 600;
		color: #333;
		margin-bottom: 10rpx;
		text-align: center;
		max-width: 200rpx;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
		line-height: 1.2;
	}

	.vip-name {
		color: #ff6c3e;
		font-weight: 700;
		text-shadow: 0 1rpx 2rpx rgba(255, 108, 62, 0.2);
	}

	.user-intro {
		font-size: 22rpx;
		color: #666;
		text-align: center;
		line-height: 1.5;
		max-width: 200rpx;
		overflow: hidden;
		display: -webkit-box;
		-webkit-line-clamp: 1;
		text-overflow: ellipsis;
		-webkit-box-orient: vertical;
		word-break: break-all;
		opacity: 0.9;
	}

	.no-active-users {
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		padding: 60rpx 20rpx;
		color: #999;
	}

	.no-active-users .cuIcon-people {
		font-size: 80rpx;
		margin-bottom: 20rpx;
		opacity: 0.5;
	}

	.no-users-text {
		font-size: 28rpx;
		color: #999;
		opacity: 0.7;
	}

	.topic-center {
		width: calc(100% - 24rpx);
		margin: 0 12rpx;
		padding: 26rpx 0 180rpx;
		box-sizing: border-box;
	}

	.topic-center-heading {
		display: flex;
		align-items: baseline;
		justify-content: space-between;
		padding: 0 18rpx 14rpx;
	}

	.topic-center-heading-followed {
		margin-top: 34rpx;
	}

	.topic-center-title {
		font-size: 29rpx;
		font-weight: 700;
		color: #334149;
	}

	.topic-center-subtitle {
		font-size: 22rpx;
		color: #8c989e;
	}

	.topic-center-list {
		border: 1rpx solid #e1e7e9;
		border-radius: 12rpx;
		background: #ffffff;
		overflow: hidden;
	}

	.topic-center-item {
		display: flex;
		align-items: center;
		min-height: 86rpx;
		padding: 0 22rpx;
		border-bottom: 1rpx solid #edf0f1;
	}

	.topic-center-item:last-child {
		border-bottom: 0;
	}

	.topic-center-main {
		display: flex;
		flex: 1;
		min-width: 0;
		align-items: center;
		gap: 16rpx;
	}

	.topic-center-name {
		font-size: 27rpx;
		font-weight: 600;
		color: #287d69;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.topic-center-count {
		font-size: 22rpx;
		color: #909a9e;
	}

	.topic-center-follow {
		flex: 0 0 auto;
		padding: 12rpx 4rpx 12rpx 18rpx;
		font-size: 24rpx;
		color: #287d69;
	}

	.topic-center-follow.is-followed {
		color: #8c989e;
	}

	.topic-center-empty {
		padding: 34rpx 20rpx;
		border: 1rpx solid #e1e7e9;
		border-radius: 12rpx;
		background: #ffffff;
		text-align: center;
		font-size: 24rpx;
		color: #909a9e;
	}

	@media (min-width: 760px) {
		.topic-center {
			width: 560px;
			max-width: calc(100vw - 48px);
			margin-right: auto;
			margin-left: auto;
		}
	}

	/* 推荐话题卡片样式 */
	.topic-cards-container {
		padding: 20rpx;
		background: #ffffff;
	}

	.topic-row {
		display: flex;
		gap: 20rpx;
	}

	.topic-card {
		flex: 1;
		display: flex;
		align-items: center;
		background: #ffffff;
		padding: 20rpx;
		transition: transform 200ms cubic-bezier(0.22, 1, 0.36, 1), opacity 180ms ease;
	}

	.topic-card:active {
		transform: scale(0.98);
		opacity: 0.8;
	}

	.topic-image-container {
		flex-shrink: 0;
		width: 100rpx;
		height: 100rpx;
		border-radius: 20rpx;
		overflow: hidden;
		margin-right: 20rpx;
		background: #f8f9fa;
	}

	.topic-image {
		width: 100%;
		height: 100%;
		object-fit: cover;
	}

	.topic-content {
		flex: 1;
		display: flex;
		flex-direction: column;
		justify-content: center;
		min-width: 0;
	}

	.topic-name {
		font-size: 28rpx;
		font-weight: 600;
		color: #333;
		margin-bottom: 8rpx;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
		line-height: 1.2;
	}

	.topic-count {
		display: flex;
		align-items: baseline;
		gap: 4rpx;
	}


	.topic-count-text {
		font-size: 22rpx;
		color: #999;
	}


	.campus-square.campus-night {
		min-height: 100vh;
		background: #15191b !important;
		color: #edf0ef;
	}

	.campus-square.campus-night .square-back-top {
		border-color: rgba(110, 186, 174, 0.24);
		background: rgba(32, 37, 39, 0.96);
		color: #6ebaae;
		box-shadow: 0 10rpx 26rpx rgba(0, 0, 0, 0.28);
	}

	.campus-square.campus-night .square-header {
		border-bottom-color: rgba(226, 232, 230, 0.09) !important;
		background: #191e20 !important;
		box-shadow: 0 8rpx 24rpx rgba(0, 0, 0, 0.18);
	}

	.campus-square.campus-night .square-page-title,
	.campus-square.campus-night .square-filter-label,
	.campus-square.campus-night .square-tool-button {
		color: #edf0ef;
	}

	.campus-square.campus-night .square-section-tabs {
		border-color: rgba(226, 232, 230, 0.09);
		background: #1b2223;
	}

	.campus-square.campus-night .square-section-item {
		color: #94a19d;
	}

	.campus-square.campus-night .square-section-item.is-active {
		background: #2d5146;
		color: #e8f2ee;
	}

	.campus-square.campus-night .square-qa-list {
		border-color: #303b38;
		background: #1d2523;
	}

	.campus-square.campus-night .square-qa-more {
		border-color: #303b38;
		color: #899993;
	}

	.campus-square.campus-night .square-tool-button,
	.campus-square.campus-night .square-filter-row,
	.campus-square.campus-night .square-filter-menu,
	.campus-square.campus-night .pinned-item,
	.campus-square.campus-night .cu-list.menu-avatar > .cu-item,
	.campus-square.campus-night .topic-card,
	.campus-square.campus-night .topic-cards-container {
		border-color: rgba(226, 232, 230, 0.09) !important;
		background: #212628 !important;
		color: #edf0ef !important;
		box-shadow: 0 10rpx 28rpx rgba(0, 0, 0, 0.18);
	}

	.campus-square.campus-night .filter-menu-options > view.is-active,
	.campus-square.campus-night .app-box-down,
	.campus-square.campus-night .btn-confirm {
		background: #328661 !important;
		color: #fff !important;
	}

	.campus-square.campus-night .filter-menu-topics,
	.campus-square.campus-night .filter-topic-empty {
		border-color: rgba(226, 232, 230, 0.16) !important;
		background: #1f2728 !important;
		color: #aebbb8 !important;
	}

	.campus-square.campus-night .filter-topic-chip {
		background: rgba(62, 146, 112, 0.18) !important;
		color: #79d0ae !important;
	}

	.campus-square.campus-night .filter-topic-chip.is-active {
		border-color: #79d0ae !important;
		background: #328661 !important;
		color: #fff !important;
	}

	.campus-square.campus-night .filter-topic-more,
	.campus-square.campus-night .filter-topic-empty-more {
		color: #9cabaa !important;
	}

	.campus-square.campus-night .no-data,
	.campus-square.campus-night .square-empty,
	.campus-square.campus-night .text-gray,
	.campus-square.campus-night .topic-count-text {
		color: #b8c4c0 !important;
	}

	/* Keep the header utilitarian: icons are controls, not decorative tiles. */
	.square-tool-button {
		width: 64rpx;
		height: 64rpx;
		border-radius: 10rpx;
	}

	.square-filter-menu {
		border-radius: 14rpx;
		box-shadow: 0 12rpx 28rpx rgba(35, 51, 61, 0.12);
	}

	.filter-menu-options view,
	.filter-menu-services > view {
		border-radius: 10rpx;
	}

	.campus-square.campus-night .square-header {
		background: #1b2223 !important;
		box-shadow: none;
	}

	.campus-square.campus-night .square-tool-button {
		border: 0 !important;
		background: transparent !important;
		box-shadow: none !important;
	}

	.campus-square.campus-night .square-tool-button:active {
		background: #293232 !important;
	}

	.campus-square.campus-night .square-filter-row {
		background: #1f2728 !important;
		box-shadow: none !important;
	}

	.campus-square.campus-night .square-filter-menu {
		background: #242d2e !important;
		box-shadow: 0 12rpx 28rpx rgba(0, 0, 0, 0.22) !important;
	}

	.campus-square.campus-night {
		background: #191f20 !important;
	}

	.campus-square.campus-night .topic-center-title {
		color: #e0e5e6;
	}

	.campus-square.campus-night .topic-center-subtitle,
	.campus-square.campus-night .topic-center-count,
	.campus-square.campus-night .topic-center-empty {
		color: #8c989e;
	}

	.campus-square.campus-night .topic-center-list,
	.campus-square.campus-night .topic-center-empty {
		border-color: #344043;
		background: #1a2122;
	}

	.campus-square.campus-night .topic-center-item {
		border-bottom-color: #303a3c;
	}

	.campus-square.campus-night .topic-center-name,
	.campus-square.campus-night .topic-center-follow {
		color: #73b9a2;
	}

	.campus-square.campus-night .topic-center-follow.is-followed {
		color: #8c989e;
	}

</style>
