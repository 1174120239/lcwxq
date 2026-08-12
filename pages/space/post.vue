<template>
	<view class="user campus-subpage campus-editor-page" :class="{'campus-night': campusNight}">
		<view class="header" :style="[{height:CustomBar + 'px'}]">
			<view class="cu-bar bg-white" :style="{'height': CustomBar + 'px','padding-top':StatusBar + 'px'}">
				<view class="action" @tap="back">
					<text class="cuIcon-close"></text>
				</view>
				<view class="content text-bold" :style="[{top:StatusBar + 'px'}]">
					<block v-if="postType=='add'">
						<block v-if="anonymousMode">匿名动态</block>
						<block v-else>发布动态</block>
					</block>
					<block v-else>
						编辑动态
					</block>
				</view>
				<view class="action">
					<!--  #ifdef H5 || APP-PLUS -->
					<block v-if="postType=='add'">
						<button v-if="type==0||type==4" class="cu-btn round post-submit-button" :disabled="!canPublish" @tap="publishSpace">{{isUploading ? '上传中' : '发布'}}</button>
					</block>
					<block v-else>
						<button class="cu-btn round post-submit-button" :disabled="!canPublish" @tap="editSpace()">{{isUploading ? '上传中' : '保存'}}</button>
					</block>
					<!--  #endif -->
				</view>
			</view>
		</view>
		<form>
			<view class="post-compose">
				<view class="post-editor-surface">
					<textarea class="post-editor-input" :maxlength="maxTextLength" v-model="text" placeholder="分享校园里的新鲜事…" @input="limitTextInput"></textarea>
					<view class="post-editor-status" :class="{'is-error': publishReason && !isUploading}">
						<text>{{textCount}}/{{maxTextLength}}</text>
						<text>{{publishReason}}</text>
					</view>
				</view>

				<!--  #ifdef H5 || APP-PLUS -->
				<view class="comments-owo space-owo">
					<text class="cuIcon-emoji" :class="{'is-active': isOwO}" @tap="OwO"></text>
					<view class="owo" v-if="isOwO">
						<scroll-view class="owo-list" scroll-y>
							<view class="owo-main">
								<view class="owo-lit-box" v-for="(item,index) in owoList" @tap="setOwO(item)" :key="index">
									<image :src="'/'+item.icon" mode="aspectFill"></image>
								</view>
							</view>
						</scroll-view>
						<view class="owo-type">
							<view class="owo-box" @tap="toOwO('paopao')" :class="OwOtype=='paopao'?'cur':''">泡泡</view>
							<view class="owo-box" @tap="toOwO('alu')" :class="OwOtype=='alu'?'cur':''">阿鲁</view>
							<view class="owo-box" @tap="toOwO('quyinniang')" :class="OwOtype=='quyinniang'?'cur':''">蛆音娘</view>
						</view>
					</view>
				</view>
				<!--  #endif -->

				<view class="anonymous-tip" v-if="anonymousMode">
					<text class="cuIcon-notice"></text>
					<text>匿名发布：动态将以匿名账号展示，其他用户无法看到你的身份。</text>
				</view>

				<view class="media-section" v-if="type==0||type==4">
					<view class="media-grid">
						<view class="media-image" :class="{'is-failed': item.failed}" :style="'background-image:url('+item.path+');'" v-for="item in mediaItems" :key="(item.failed ? 'failed-' : 'uploaded-') + item.order + '-' + item.path">
							<view class="media-failed-mask" v-if="item.failed" @tap.stop="retryFailedUpload(item.source)">
								<text class="cuIcon-refresh"></text><text>上传失败，重试</text>
							</view>
							<text class="cuIcon-close media-remove" @tap.stop="item.failed ? removeFailedUpload(item.source) : picClose(item.path)"></text>
						</view>
						<view class="media-video" v-if="type==4 && pic">
							<video class="media-video-preview" :src="videoPreviewPath || pic" :controls="false" :show-center-play-btn="false" object-fit="cover"></video>
							<view class="media-video-badge"><text class="cuIcon-videofill"></text><text>视频</text></view>
							<text class="cuIcon-close media-remove" @tap.stop="removeVideo"></text>
						</view>
						<view class="media-upload" v-if="canAddMedia" aria-label="添加图片或视频" @tap="chooseMedia">
							<text class="cuIcon-add"></text>
						</view>
					</view>
				</view>
				<view class="component-editor" v-if="postType=='add' && !anonymousMode && type==0">
					<view class="component-add" v-if="!poll" @tap="openComponentPicker">
						<text class="cuIcon-add"></text><text>添加组件</text>
					</view>
					<view class="poll-summary" v-else>
						<view class="poll-summary-main" @tap="openPollEditor">
							<text class="cuIcon-rank poll-summary-icon"></text>
							<view><view class="poll-summary-title">{{poll.title}}</view><view class="poll-summary-meta">{{poll.multiple ? '多选，最多'+poll.maxChoices+'项' : '单选'}} · {{poll.options.length}}个选项</view></view>
						</view>
						<text class="cuIcon-close poll-remove" @tap="removePoll"></text>
					</view>
				</view>
				<view class="topic-editor">
					<view class="topic-editor-head">
						<text class="topic-editor-title">话题</text>
						<text class="topic-editor-toggle" @tap="showTopicPicker=!showTopicPicker">
							{{showTopicPicker ? '收起' : '添加话题'}}
						</text>
					</view>
					<view class="topic-chip-list" v-if="selectedTopics.length>0">
						<view class="topic-chip" v-for="topic in selectedTopics" :key="topic.mid">
							<text>#{{topic.name}}</text>
							<text class="cuIcon-close topic-chip-close" @tap="removeTopic(topic)"></text>
						</view>
					</view>
					<view class="topic-recommend">
						<text class="topic-recommend-label">官方推荐</text>
						<view class="topic-recommend-state" v-if="topicLoading">加载中…</view>
						<scroll-view class="topic-recommend-scroll" scroll-x v-else-if="recommendedTopics.length>0">
							<view class="topic-recommend-track">
								<view class="topic-recommend-chip" :class="{'is-selected': isTopicSelected(topic)}"
									v-for="topic in recommendedTopics" :key="'official-'+topic.mid" @tap="toggleTopic(topic)">
									<text>#{{topic.name}}</text>
								</view>
							</view>
						</scroll-view>
						<view class="topic-recommend-state" v-else>暂无推荐话题</view>
					</view>
					<view class="topic-picker" v-if="showTopicPicker">
						<view class="topic-picker-list" v-if="topicOptions.length>0">
							<view class="topic-picker-item" v-for="topic in topicOptions" :key="topic.mid"
								@tap="toggleTopic(topic)">
								<text class="topic-picker-name">#{{topic.name}}</text>
								<text class="topic-picker-count">{{topic.spaceCount || 0}}条动态</text>
								<text class="topic-follow-action" @tap.stop="toggleTopicFollow(topic)">
									{{topic.isFollowed==1 ? '已关注' : '关注'}}
								</text>
							</view>
						</view>
						<view class="topic-empty" v-else>还没有可选话题，可以创建一个</view>
						<view class="topic-create-row">
							<input class="topic-create-input" v-model="topicInput" maxlength="24"
								placeholder="输入话题，例如 寻人" @confirm="createTopic" />
							<text class="topic-create-button" @tap="createTopic">添加</text>
						</view>
					</view>
				</view>
			</view>
			<!--  #ifdef MP -->
			<view class="all-btn">
				<view class="user-btn flex flex-direction">
					<block v-if="postType=='add'">		
						<button v-if="type==0||type==4" class="cu-btn post-submit-button post-submit-button-block margin-tb-sm lg" :disabled="!canPublish" @tap="publishSpace">{{isUploading ? '上传中' : '发布'}}</button>
					</block>
					<block v-else>
						<button class="cu-btn post-submit-button post-submit-button-block margin-tb-sm lg" :disabled="!canPublish" @tap="editSpace()">{{isUploading ? '上传中' : '保存'}}</button>
					</block>
					
					
				</view>
			</view>
			<!--  #endif -->
			
		</form>
		<tn-popup v-model="modelVisible" mode="bottom" :borderRadius="18" :maskCloseable="false" :backgroundColor="campusNight ? '#1d2423' : '#ffffff'">
			<view class="post-policy-popup">
				<view class="post-policy-title">动态规范</view>
				<view class="model-body">分享真实、友善、有价值的校园生活，请勿发布违法违规或伤害他人的内容。</view>
				<tn-button class="post-policy-confirm" :backgroundColor="campusNight ? '#3a9278' : '#168573'" fontColor="#fff" @tap="okBtn">知道了</tn-button>
			</view>
		</tn-popup>
		<view class="poll-modal-mask" v-if="pollEditorVisible" @tap="closePollEditor">
			<view class="poll-modal" @tap.stop>
				<view class="poll-modal-head"><text @tap="closePollEditor">取消</text><text class="poll-modal-title">添加投票</text><text class="poll-save" @tap="savePoll">完成</text></view>
				<scroll-view scroll-y class="poll-modal-body">
					<input class="poll-input" v-model="pollDraft.title" maxlength="80" placeholder="投票标题（必填）" />
					<textarea class="poll-intro" v-model="pollDraft.description" maxlength="240" placeholder="补充说明（选填）"></textarea>
					<view class="poll-option-row" v-for="(option,index) in pollDraft.options" :key="index">
						<text class="poll-option-index">{{index+1}}</text><input v-model="pollDraft.options[index]" maxlength="80" :placeholder="'选项 '+(index+1)" />
						<text class="cuIcon-close" v-if="pollDraft.options.length>2" @tap="removePollOption(index)"></text>
					</view>
					<view class="poll-add-option" v-if="pollDraft.options.length<6" @tap="addPollOption"><text class="cuIcon-add"></text><text>增加选项</text></view>
					<view class="poll-setting-row"><text>允许多选</text><switch color="#168573" :checked="pollDraft.multiple" @change="togglePollMultiple" /></view>
					<view class="poll-setting-row" v-if="pollDraft.multiple"><text>最多选择</text><picker :range="pollChoiceRanges" :value="pollChoiceIndex" @change="changePollMax"><view class="poll-choice-picker">{{pollDraft.maxChoices}} 项 <text class="cuIcon-right"></text></view></picker></view>
					<view class="poll-privacy-note">投票匿名展示，发布后不可修改选项</view>
				</scroll-view>
			</view>
		</view>
	</view>
</template>

<script>
	import { localStorage } from '../../js_sdk/mp-storage/mp-storage/index.js'
	import { applyCampusThemeShell, getCampusThemeMode, isDongchangfuNight, resolveCampusNight } from '@/utils/campusTheme.js'
	// #ifdef APP-PLUS
	import owo from '../../static/app-plus/owo/OwO.js'
	// #endif
	// #ifdef H5
	import owo from '../../static/h5/owo/OwO.js'
	// #endif
	// #ifdef MP
	var owo = [];
	// #endif
	export default {
		data() {
			return {
				StatusBar: this.StatusBar,
				CustomBar: this.CustomBar,
				NavBar:this.StatusBar +  this.CustomBar,
				AppStyle:this.$store.state.AppStyle,
				campusThemeMode: 'auto',
				campusThemeClock: Date.now(),
				campusThemeTimer: null,
				
				id:0,
				postType:"add",
				anonymousMode:false,
				type:0,
				text:"",
				maxTextLength:1500,
				toid:0,
				pic:"",
				picList:[],
				videoPreviewPath:"",
				isUploading:false,
				failedUploads:[],
				imageOrder:{},
				nextUploadOrder:0,
				token:"",
				currencyName:"",
				topicCenter: {
					official: [],
					followed: []
				},
				selectedTopics: [],
				topicInput: "",
				showTopicPicker: false,
				topicLoading: false,
				poll:null,
				pollEditorVisible:false,
				pollDraft:{ title:'', description:'', options:['',''], multiple:false, maxChoices:1 },
				
				modelVisible: true,
				forwardJson:null,
				contentJson:null,
				shopJson:null,
				
				isOwO:false,
				owo:owo,
				owoList:[],
				OwOtype:"paopao",
				
				
				
				
			}
		},
		computed: {
			pollChoiceRanges() {
				return Array.from({length: Math.max(1, this.pollDraft.options.length - 1)}, (_, index) => index + 2)
			},
			pollChoiceIndex() {
				return Math.max(0, this.pollChoiceRanges.indexOf(Number(this.pollDraft.maxChoices)))
			},
			campusNight() {
				return resolveCampusNight(this.campusThemeMode, isDongchangfuNight(this.campusThemeClock))
			},
			canAddMedia() {
				if (this.isUploading) return false
				if (this.type === 4 && this.pic) return false
				return this.picList.length + this.failedUploads.length < 9
			},
			mediaItems() {
				const items = this.picList.map((url, index) => ({
					path: url,
					order: Object.prototype.hasOwnProperty.call(this.imageOrder, url)
						? Number(this.imageOrder[url]) : index,
					failed: false
				}))
				this.failedUploads.forEach((item, index) => items.push({
					path: item.path,
					order: Number(item.order == null ? this.picList.length + index : item.order),
					failed: true,
					source: item
				}))
				return items.sort((left, right) => left.order - right.order)
			},
			textCount() {
				return this.stripImageMarker(this.text).length
			},
			publishReason() {
				if (this.isUploading) return '图片正在上传，请稍候'
				if (this.failedUploads.length) return '有图片上传失败，请重试或删除'
				if (this.textCount > this.maxTextLength) return '动态正文不能超过' + this.maxTextLength + '字'
				if (this.type === 4) {
					if (!this.pic) return '请先上传视频'
					if (!this.stripImageMarker(this.text).trim()) return '视频动态需要填写说明'
					return ''
				}
				if (this.type !== 0 || this.picList.length > 0 || this.poll) return ''
				const remaining = Math.max(0, 4 - this.stripImageMarker(this.text).trim().length)
				return remaining > 0 ? '纯文字动态至少需要4个字，还需输入' + remaining + '个字' : ''
			},
			canPublish() {
				if (this.isUploading || this.failedUploads.length) return false
				if (this.textCount > this.maxTextLength) return false
				const textLength = this.stripImageMarker(this.text).trim().length
				const hasText = textLength > 0
				if (this.type === 4) return hasText && Boolean(this.pic)
				if (this.type === 0) return textLength >= 4 || this.picList.length > 0
				return true
			},
			recommendedTopics() {
				return (this.topicCenter.official || []).filter(topic => topic && topic.mid && topic.name)
			},
			topicOptions() {
				const result = []
				const seen = {}
				const source = (this.topicCenter.official || []).concat(this.topicCenter.followed || [])
				for (let i = 0; i < source.length; i++) {
					const topic = source[i]
					if (!topic || seen[topic.mid]) continue
					seen[topic.mid] = true
					result.push(topic)
				}
				return result
			}
		},
		onPullDownRefresh(){
			var that = this;
			
		},
		onHide() {
			this.setEditorVisibility(false)
			localStorage.removeItem('getuid')
			this.stopCampusThemeClock()
		},
		onUnload() {
			this.setEditorVisibility(false)
			this.stopCampusThemeClock()
		},
			onShow(){
			var that = this;
			that.setEditorVisibility(true)
			that.loadCampusThemeMode();
			that.startCampusThemeClock();
			// #ifdef APP-PLUS
			
			plus.navigator.setStatusBarStyle(that.campusNight ? "light" : "dark")
			// #endif
			if(localStorage.getItem('token')){
				that.token = localStorage.getItem('token');
			}
			that.loadTopics();
			
		},
		onLoad(res) {
			
			var that = this;
			const modelViewTime = Number(uni.getStorageSync('modelView'))
			const policyReminderInterval = 30 * 24 * 60 * 60 * 1000
			this.modelVisible = !modelViewTime || Date.now() - modelViewTime >= policyReminderInterval
			that.currencyName = that.$API.getCurrencyName();
			// #ifdef APP-PLUS || MP
			that.NavBar = this.CustomBar;
			// #endif
			// #ifdef APP-PLUS || H5
			that.owoList = that.owo.data.paopao.container;
			// #endif
			if(res.type){
				that.type = Number(res.type);
			}
			if(res.toid){
				that.toid = res.toid;
				that.getInfo()
			}
			
			
			if(res.postType){
				that.postType = res.postType;
			}
			if(res.anonymous){
				that.anonymousMode = true;
				that.postType = "add";
			}
			if(res.id){
				that.id = res.id;
				if(that.postType=='add'){
					that.getForwardInfo(that.id);
				}else{
					that.getSpaceInfo();
				}
				
			}
			
		},
		methods: {
			openComponentPicker() {
				uni.showActionSheet({ itemList:['投票'], success:() => this.openPollEditor() })
			},
			blankPollDraft() {
				return { title:'', description:'', options:['',''], multiple:false, maxChoices:1 }
			},
			openPollEditor() {
				this.pollDraft = this.poll ? JSON.parse(JSON.stringify(this.poll)) : this.blankPollDraft()
				this.pollEditorVisible = true
			},
			closePollEditor() { this.pollEditorVisible = false },
			addPollOption() { if (this.pollDraft.options.length < 6) this.pollDraft.options.push('') },
			removePollOption(index) {
				if (this.pollDraft.options.length > 2) this.pollDraft.options.splice(index, 1)
				if (this.pollDraft.multiple) this.pollDraft.maxChoices = Math.min(this.pollDraft.maxChoices, this.pollDraft.options.length)
			},
			togglePollMultiple(event) {
				this.pollDraft.multiple = !!event.detail.value
				this.pollDraft.maxChoices = this.pollDraft.multiple ? Math.min(2, this.pollDraft.options.length) : 1
			},
			changePollMax(event) { this.pollDraft.maxChoices = this.pollChoiceRanges[Number(event.detail.value)] || 2 },
			savePoll() {
				const title = (this.pollDraft.title || '').trim()
				const options = this.pollDraft.options.map(item => (item || '').trim())
				if (!title) return uni.showToast({title:'请填写投票标题', icon:'none'})
				if (options.some(item => !item)) return uni.showToast({title:'请填写完整投票选项', icon:'none'})
				if (new Set(options).size !== options.length) return uni.showToast({title:'投票选项不能重复', icon:'none'})
				this.poll = { title, description:(this.pollDraft.description || '').trim(), options, multiple:!!this.pollDraft.multiple, maxChoices:this.pollDraft.multiple ? Number(this.pollDraft.maxChoices) : 1 }
				this.pollEditorVisible = false
			},
			removePoll() {
				uni.showModal({title:'移除投票', content:'确定移除这个投票组件吗？', success:({confirm}) => { if (confirm) this.poll = null }})
			},
			pollPayload() { return this.poll ? JSON.stringify({...this.poll, multiple:this.poll.multiple ? 1 : 0}) : '' },
			loadTopics() {
				if (!this.token) return
				this.topicLoading = true
				this.$Net.request({
					url: this.$API.topicList(),
					data: { token: this.token },
					method: "get",
					dataType: "json",
					success: (res) => {
						if (res.data.code == 1 && res.data.data) {
							this.topicCenter = res.data.data
						}
						this.topicLoading = false
					},
					fail: () => {
						this.topicLoading = false
					}
				})
			},
			topicIdsPayload() {
				const value = this.selectedTopics.map(topic => topic.mid).join(',')
				return value || '0'
			},
			isTopicSelected(topic) {
				return this.selectedTopics.some(item => String(item.mid) === String(topic.mid))
			},
			toggleTopic(topic) {
				if (this.isTopicSelected(topic)) {
					this.removeTopic(topic)
					return
				}
				if (this.selectedTopics.length >= 3) {
					uni.showToast({ title: "一条动态最多选择3个话题", icon: "none" })
					return
				}
				this.selectedTopics = this.selectedTopics.concat([topic])
			},
			removeTopic(topic) {
				this.selectedTopics = this.selectedTopics.filter(
					item => String(item.mid) !== String(topic.mid))
			},
			toggleTopicFollow(topic) {
				if (!this.token) {
					uni.showToast({ title: "请先登录", icon: "none" })
					return
				}
				const nextType = topic.isFollowed == 1 ? 0 : 1
				this.$Net.request({
					url: this.$API.topicFollow(),
					data: { token: this.token, mid: topic.mid, type: nextType },
					method: "get",
					dataType: "json",
					success: (res) => {
						if (res.data.code == 1) {
							this.updateTopicFollowState(topic.mid, nextType)
						}
					}
				})
			},
			updateTopicFollowState(mid, followed) {
				const lists = [
					this.topicCenter.official || [],
					this.topicCenter.followed || [],
					this.selectedTopics || []
				]
				for (let i = 0; i < lists.length; i++) {
					for (let j = 0; j < lists[i].length; j++) {
						const item = lists[i][j]
						if (String(item.mid) !== String(mid)) continue
						this.$set(item, "isFollowed", followed)
						const count = Number(item.followCount) || 0
						this.$set(item, "followCount", Math.max(0, count + (followed ? 1 : -1)))
					}
				}
				if (followed) {
					const exists = (this.topicCenter.followed || []).some(
						item => String(item.mid) === String(mid))
					const source = (this.topicCenter.official || []).find(
						item => String(item.mid) === String(mid))
					if (!exists && source) {
						this.topicCenter.followed = [source].concat(this.topicCenter.followed || [])
					}
				} else {
					this.topicCenter.followed = (this.topicCenter.followed || []).filter(
						item => String(item.mid) !== String(mid))
				}
			},
			createTopic() {
				const name = (this.topicInput || "").trim()
				if (!name) return
				if (!this.token) {
					uni.showToast({ title: "请先登录", icon: "none" })
					return
				}
				this.$Net.request({
					url: this.$API.topicCreate(),
					data: { token: this.token, name: name },
					method: "get",
					dataType: "json",
					success: (res) => {
						if (res.data.code != 1 || !res.data.data) {
							uni.showToast({ title: res.data.msg || "话题添加失败", icon: "none" })
							return
						}
						const topic = res.data.data
						if (!this.isTopicSelected(topic) && this.selectedTopics.length < 3) {
							this.selectedTopics = this.selectedTopics.concat([topic])
						}
						this.topicInput = ""
						this.loadTopics()
					}
				})
			},
			commitPendingTopic(callback) {
				const name = (this.topicInput || "").trim()
				if (!name) {
					callback()
					return
				}
				const normalizedName = name.replace(/^#+|#+$/g, "").trim()
				const alreadySelected = (this.selectedTopics || []).some(
					item => String(item.name || "") === normalizedName)
				if (alreadySelected) {
					this.topicInput = ""
					callback()
					return
				}
				if (!this.token) {
					uni.showToast({ title: "请先登录", icon: "none" })
					return
				}
				if (this.selectedTopics.length >= 3) {
					uni.showToast({ title: "一条动态最多选择3个话题", icon: "none" })
					return
				}
				uni.showLoading({ title: "保存话题" })
				this.$Net.request({
					url: this.$API.topicCreate(),
					data: { token: this.token, name: name },
					method: "get",
					dataType: "json",
					success: (res) => {
						uni.hideLoading()
						if (res.data.code != 1 || !res.data.data) {
							uni.showToast({ title: res.data.msg || "话题添加失败", icon: "none" })
							return
						}
						const topic = res.data.data
						if (!this.isTopicSelected(topic)) {
							this.selectedTopics = this.selectedTopics.concat([topic])
						}
						this.topicInput = ""
						this.loadTopics()
						callback()
					},
					fail: () => {
						uni.hideLoading()
						uni.showToast({ title: "话题添加失败", icon: "none" })
					}
				})
			},
			stripImageMarker(text) {
				return (text || '').replace(/\s*#图集#\s*$/, '')
			},
			limitTextInput(e) {
				const value = e && e.detail ? e.detail.value : this.text
				if ((value || '').length <= this.maxTextLength) return
				this.text = (value || '').slice(0, this.maxTextLength)
			},
			showPublishReason() {
				if (!this.publishReason) return false
				uni.showToast({ title: this.publishReason, icon: 'none' })
				return true
			},
			imagePayloadText(text) {
				return this.stripImageMarker(text)
			},
			setEditorVisibility(visible) {
				if (this.$root && this.$root.$emit) this.$root.$emit('campus-editor-visibility', visible)
				if (typeof document !== 'undefined') {
					document.documentElement.classList.toggle('campus-editor-open', visible)
					document.body.classList.toggle('campus-editor-open', visible)
				}
			},
			loadCampusThemeMode() {
				this.campusThemeMode = getCampusThemeMode()
				applyCampusThemeShell(this.campusThemeMode, this.campusThemeClock)
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
			chooseMedia() {
				if (this.isUploading) return
				uni.showActionSheet({
					itemList: ['选择图片', '选择视频'],
					success: ({ tapIndex }) => {
						const nextType = tapIndex === 1 ? 4 : 0
						const changesMediaType = (nextType === 4 && this.picList.length > 0) || (nextType === 0 && this.type === 4 && this.pic)
						const removesPoll = nextType === 4 && !!this.poll
						const selectMedia = () => {
							if (removesPoll) this.poll = null
							nextType === 4 ? this.uploadVideo() : this.upload()
						}
						if (!changesMediaType && !removesPoll) {
							selectMedia()
							return
						}
						uni.showModal({
							title: removesPoll ? '改为视频动态' : '替换当前媒体',
							content: removesPoll
								? (changesMediaType ? '视频动态不支持投票，继续后将移除投票并替换当前图片。' : '视频动态不支持投票，继续后将移除投票。')
								: (nextType === 4 ? '一条动态不能同时包含图片和视频，继续后将用视频替换当前图片。' : '一条动态不能同时包含视频和图片，继续后将用图片替换当前视频。'),
							success: ({ confirm }) => {
								if (confirm) selectMedia()
							}
						})
					}
				})
			},
			publishSpace() {
				if (!this.canPublish && this.showPublishReason()) return
				this.commitPendingTopic(() => {
					if (this.type === 4) {
						this.addSpace2()
						return
					}
					this.addSpace()
				})
			},
			okBtn() {
							const nowDate = +new Date();
							uni.setStorageSync('modelView', nowDate);
							this.cancleBtn();
						},
			cancleBtn() {
							this.modelVisible = false;
						},
			back(){
				uni.navigateBack({
					delta: 1
				});
			},
			toOwO(text){
				var that = this;
				that.OwOtype = text;
				if(text=="paopao"){
					that.owoList = that.owo.data.paopao.container;
				}
				if(text=="adai"){
					that.owoList = that.owo.data.adai.container;
				}
				if(text=="alu"){
					that.owoList = that.owo.data.alu.container;
				}
				if(text=="quyinniang"){
					that.owoList = that.owo.data.quyinniang.container;
				}
			},
			setOwO(data){
				var that = this;
				var text = data.data;
				that.text = (that.text + text).slice(0, that.maxTextLength);
			},
			OwO(){
				var that = this;
				that.isOwO = !that.isOwO;
			},
			getForwardInfo(toid){
				var that = this;
				var data = {
					"id":toid
				}
				that.$Net.request({
					url: that.$API.spaceInfo(),
					data:data,
					header:{
						'Content-Type':'application/x-www-form-urlencoded'
					},
					method: "get",
					dataType: 'json',
					success: function(res) {
						that.isLoading=1;
						if(res.data.code==1){
							that.forwardJson = res.data.data;
							that.toid = res.data.data.id;
							
						}
					},
					fail: function(res) {
						that.isLoading=1;
					}
				});
				
			},
			replaceAll(string, search, replace) {
			  return string.split(search).join(replace);
			},
			getSpaceInfo(){
				var that = this;
				var data = {
					"id":that.id
				}
				that.$Net.request({
					url: that.$API.spaceInfo(),
					data:data,
					header:{
						'Content-Type':'application/x-www-form-urlencoded'
					},
					method: "get",
					dataType: 'json',
					success: function(res) {
						that.isLoading=1;
						if(res.data.code==1){
							that.id = res.data.data.id;
							that.type = res.data.data.type;
							var text = res.data.data.text;
							text = that.replaceAll(text,"/r/n","\n");
							text = that.replaceAll(text,"||rn||","\n");
							that.text = text;
							that.selectedTopics = Array.isArray(res.data.data.topics)
								? res.data.data.topics : [];
							that.toid = res.data.data.toid;
							if(res.data.data.pic){
								that.pic = res.data.data.pic;
								if(that.type==0){
									if(that.pic.indexOf("||")!=-1){
										that.picList = that.pic.split("||");
									}else{
										that.picList = [that.pic];
									}
								}
							}
							if(that.type==0) that.initializeImageOrder();
							
							if(that.type==1){
								that.contentJson = res.data.data.contentJson;
							}
							if(that.type==5){
								that.shopJson = res.data.data.shopJson;
							}
							if(that.type==2){
								that.getForwardInfo(that.toid);
							}
							
						}
					},
					fail: function(res) {
						that.isLoading=1;
					}
				});
				
			},
			getInfo(){
				var that = this;
				var data = {
					"key":that.toid,
					"isMd":0,
				}
				
				that.$Net.request({
					url: that.$API.getContentsInfo(),
					data:data,
					header:{
						'Content-Type':'application/x-www-form-urlencoded'
					},
					method: "get",
					dataType: 'json',
					success: function(res) {
						uni.stopPullDownRefresh();
						if(res.data.title){
							that.contentJson = res.data;
							

							
						}
					},
					fail: function(res) {
						uni.stopPullDownRefresh();
					}
				})
			},
			addSpace(){
				var that = this;
				if(that.token==""){
					uni.showToast({
					    title:"请先登录",
						icon:'none',
						duration: 1000,
					});
					
					var timer = setTimeout(function() {
						uni.navigateTo({
						    url: '/pages/user/login'
						});
						clearTimeout('timer')
					}, 1000)
					return false
				}
				var text = that.text || "";
				if(that.type==2 && text.trim()==""){
					text = "转发了动态"
				}
				if(that.type==0){
					var picList = that.picList;
					var pic = "";
					for(var i in picList){
						if(i==0){
							pic += picList[i];
						}else{
							pic += "||"+picList[i];
						}
					}
					that.pic = pic;
					text = that.imagePayloadText(text);
					if(text.trim()=="" && that.pic=="" && !that.poll){
						uni.showToast({
						    title:"请输入文字或上传图片",
							icon:'none',
							duration: 1000,
						});
						return false;
					}
				}
				if(that.type==4){
					if(that.pic==""){
						uni.showToast({
						    title:"请上传视频",
							icon:'none',
							duration: 1000,
						});
						return false;
					}
				}
				text = text.replace(/\r\n/g,"||rn||");
				text = text.replace(/\n/g,"||rn||");
				var data = {
					type:that.type,
					text:text,
					toid:that.toid,
					pic:that.pic,
					topicIds:that.topicIdsPayload(),
					poll:that.pollPayload(),
					token:that.token
				}
				uni.showLoading({
					title: "加载中"
				});
				that.$Net.request({
					
					url: that.anonymousMode ? that.$API.anonymousPost() : that.$API.addSpace(),
					data:that.$API.removeObjectEmptyKey(data),
					header:{
						'Content-Type':'application/x-www-form-urlencoded'
					},
					method: "get",
					dataType: 'json',
					success: function(res) {
						uni.hideLoading();
						uni.showToast({
							title:res.data.msg,
						    icon:'none'
						});
						if(res.data.code==1){
							setTimeout(function() {
								that.back();
							}, 900)
						}
					},
					fail: function(res) {
						setTimeout(function () {
							uni.hideLoading();
						}, 1000);
						uni.showToast({
							title: "网络不太好哦~",
							icon: 'none'
						})
						uni.stopPullDownRefresh()
					}
				})
			},
			addSpace2(){
				var that = this;
				if(that.token==""){
					uni.showToast({
					    title:"请先登录",
						icon:'none',
						duration: 1000,
					});
					
					var timer = setTimeout(function() {
						uni.navigateTo({
						    url: '/pages/user/login'
						});
						clearTimeout('timer')
					}, 1000)
					return false
				}
				if(that.type==2){
					if (that.text == "") {
						text = "转发了动态"
					}
				}
				if (that.text == "") {
					uni.showToast({
					    title:"请输入动态内容",
						icon:'none',
						duration: 1000,
						position:'bottom',
					});
					return false
				}
				var text  = that.text;
				if(that.type==0){
					var picList = that.picList;
					var pic = "";
					for(var i in picList){
						if(i==0){
							pic += picList[i];
						}else{
							pic += "||"+picList[i];
						}
					}
					that.pic = pic;
					if(that.pic==""){
						uni.showToast({
						    title:"请上传图片",
							icon:'none',
							duration: 1000,
							position:'bottom',
						});
						return false;
					}
				}
				if(that.type==4){
					if(that.pic==""){
						uni.showToast({
						    title:"请上传视频",
							icon:'none',
							duration: 1000,
							position:'bottom',
						});
						return false;
					}
				}
				text = text.replace(/\r\n/g,"||rn||");
				text = text.replace(/\n/g,"||rn||");
				var data = {
					type:that.type,
					text:text + " #视频#",
					toid:that.toid,
					pic:that.pic,
					topicIds:that.topicIdsPayload(),
					poll:that.pollPayload(),
					token:that.token
				}
				uni.showLoading({
					title: "加载中"
				});
				that.$Net.request({
					
					url: that.anonymousMode ? that.$API.anonymousPost() : that.$API.addSpace(),
					data:that.$API.removeObjectEmptyKey(data),
					header:{
						'Content-Type':'application/x-www-form-urlencoded'
					},
					method: "get",
					dataType: 'json',
					success: function(res) {
						uni.hideLoading();
						uni.showToast({
							title: res.data.msg,
							icon: 'none'
						})
						if(res.data.code==1){
							setTimeout(function() {
								that.back();
							}, 900)
						}
					},
					fail: function(res) {
						setTimeout(function () {
							uni.hideLoading();
						}, 1000);
						uni.showToast({
							title: "网络不太好哦~",
							icon: 'none'
						})
						uni.stopPullDownRefresh()
					}
				})
			},
			editSpace(){
				var that = this;
				if (!that.canPublish && that.showPublishReason()) return false;
				if(that.token==""){
					uni.showToast({
					    title:"请先登录",
						icon:'none',
						duration: 1000,
					});
					
					var timer = setTimeout(function() {
						uni.navigateTo({
						    url: '/pages/user/login'
						});
						clearTimeout('timer')
					}, 1000)
					return false
				}
				if ((that.topicInput || "").trim() !== "") {
					that.commitPendingTopic(() => that.editSpace())
					return false
				}
				var text = that.text || "";
				if(that.type==2 && text.trim()==""){
					text = "转发了动态"
				}
				if(that.type==0){
					var picList = that.picList;
					var pic = "";
					for(var i in picList){
						if(i==0){
							pic += picList[i];
						}else{
							pic += "||"+picList[i];
						}
					}
					that.pic = pic;
					text = that.imagePayloadText(text);
					if(text.trim()=="" && that.pic==""){
						uni.showToast({
						    title:"请输入文字或上传图片",
							icon:'none',
							duration: 1000,
						});
						return false;
					}
				}
				if(that.type==4){
					if(that.pic==""){
						uni.showToast({
						    title:"请上传视频",
							icon:'none',
							duration: 1000,
						});
						return false;
					}
				}
				text = text.replace(/\r\n/g,"||rn||");
				text = text.replace(/\n/g,"||rn||");
				var data = {
					id:that.id,
					type:that.type,
					text:text,
					toid:that.toid,
					pic:that.pic,
					topicIds:that.topicIdsPayload(),
					token:that.token
				}
				uni.showLoading({
					title: "加载中"
				});
				that.$Net.request({
					
					url: that.$API.editSpace(),
					data:that.$API.removeObjectEmptyKey(data),
					header:{
						'Content-Type':'application/x-www-form-urlencoded'
					},
					method: "get",
					dataType: 'json',
					success: function(res) {
						setTimeout(function () {
							uni.hideLoading();
						}, 1000);
						uni.showToast({
							title: res.data.msg,
							icon: 'none'
						})
						if(res.data.code==1){
							var timer = setTimeout(function() {
								that.back();
							}, 1000)
							
						}
					},
					fail: function(res) {
						setTimeout(function () {
							uni.hideLoading();
						}, 1000);
						uni.showToast({
							title: "网络不太好哦~",
							icon: 'none'
						})
						uni.stopPullDownRefresh()
					}
				})
			},
			upload(){
				const that = this
				if(that.token===""){
					uni.showToast({ title:"请先登录", icon:'none', duration: 1000 })
					setTimeout(function() {
						uni.navigateTo({ url: '/pages/user/login' })
					}, 1000)
					return false
				}
				const remaining = that.type === 0 ? 9 - that.picList.length - that.failedUploads.length : 9
				if (remaining <= 0) return false
				uni.chooseImage({
					count: remaining,
					sizeType:['compressed'],
					sourceType: ['album', 'camera'],
						success: function (res) {
							const tempFilePaths = res.tempFilePaths || []
							if (!tempFilePaths.length) return
							const orderBase = that.nextUploadOrder
							that.nextUploadOrder += tempFilePaths.length
							that.isUploading = true
						uni.showLoading({ title: "上传中" })
						let completed = 0
						const results = new Array(tempFilePaths.length)
						const finishUpload = () => {
							completed += 1
							if (completed !== tempFilePaths.length) return
							for (let index = 0; index < results.length; index++) {
								const result = results[index]
									if (result && result.url && that.picList.length < 9) {
										that.$set(that.imageOrder, result.url, result.order)
										that.picList.push(result.url)
									}
									if (!result || !result.url) that.failedUploads.push({ path: tempFilePaths[index], order: orderBase + index })
								}
								that.sortPicList()
							that.isUploading = false
							uni.hideLoading()
							if (that.failedUploads.length) uni.showToast({ title: '部分图片上传失败，可重试或删除', icon: 'none' })
						}
						for(let i = 0;i < tempFilePaths.length; i++) {
							uni.uploadFile({
							  url : that.$API.upload(),
							  filePath: tempFilePaths[i],
							  name: 'file',
							  formData: { 'token': that.token },
							  success: function (uploadFileRes) {
									try {
										const data = JSON.parse(uploadFileRes.data)
										if(data.code==1 && data.data && data.data.url){
											if (that.type === 4) {
												that.pic = ''
												that.videoPreviewPath = ''
												that.picList = []
												that.imageOrder = {}
												that.failedUploads = []
											}
											that.type = 0
											results[i] = { url: data.data.url, order: orderBase + i }
										} else {
											results[i] = null
										}
									} catch (error) {
										results[i] = null
									}
								},
								fail: function(){ results[i] = null },
								complete: finishUpload
							})
						}
					}
				})
			},
			uploadVideo(){
				const that = this
				if(that.token===""){
					uni.showToast({ title:"请先登录", icon:'none', duration: 1000 })
					setTimeout(function() {
						uni.navigateTo({ url: '/pages/user/login' })
					}, 1000)
					return false
				}
				uni.chooseVideo({
					sourceType: ['camera', 'album'],
					compressed:true,
					success: (response) => {
					  const videoFile = response.tempFilePath
					  const fileSize = response.size
					  if (fileSize > 20 * 1024 * 1024) {
					    uni.showToast({
					      title: '视频不能超过 20MB',
					      icon: 'none'
					    })
					    return false
					  }
						that.isUploading = true
						uni.showLoading({ title: "上传中" })
						uni.uploadFile({
						  url : that.$API.upload(),
						  filePath:videoFile,
						  name: 'file',
						  formData: { 'token': that.token },
						  success: function (uploadFileRes) {
								try {
									const data = JSON.parse(uploadFileRes.data)
									if(data.code==1 && data.data && data.data.url){
										that.type = 4
										that.picList = []
										that.pic = data.data.url
										that.videoPreviewPath = videoFile
										return
									}
									uni.showToast({ title: data.msg || '视频上传失败', icon: 'none' })
								} catch (error) {
									uni.showToast({ title: '视频上传失败，请重试', icon: 'none' })
								}
							},fail:function(){
								uni.showToast({ title: "网络异常，视频上传失败", icon: 'none' })
							},
							complete: function() {
								that.isUploading = false
								uni.hideLoading()
							}
						})
					}
				})
			},
			picClose(item){
				this.picList = this.picList.filter(pic => pic !== item)
				this.$delete(this.imageOrder, item)
			},
			sortPicList() {
				this.picList = this.picList.slice().sort((left, right) =>
					Number(this.imageOrder[left] || 0) - Number(this.imageOrder[right] || 0))
			},
			initializeImageOrder() {
				this.imageOrder = {}
				this.picList.forEach((url, index) => this.$set(this.imageOrder, url, index))
				this.nextUploadOrder = this.picList.length
			},
			retryFailedUpload(item) {
				if (this.isUploading) return
				this.isUploading = true
				uni.showLoading({ title: '重新上传' })
				uni.uploadFile({
					url: this.$API.upload(), filePath: item.path, name: 'file',
					formData: { token: this.token },
					success: response => {
						try {
							const data = JSON.parse(response.data)
							if (data.code === 1 && data.data && data.data.url) {
								this.$set(this.imageOrder, data.data.url, item.order)
								this.picList.push(data.data.url)
								this.sortPicList()
								this.removeFailedUpload(item)
								return
							}
						} catch (error) {}
						uni.showToast({ title: '图片上传失败，请重试', icon: 'none' })
					},
					fail: () => uni.showToast({ title: '图片上传失败，请重试', icon: 'none' }),
					complete: () => { this.isUploading = false; uni.hideLoading() }
				})
			},
			removeFailedUpload(item) {
				this.failedUploads = this.failedUploads.filter(upload => upload !== item)
			},
			removeVideo(){
				this.pic = ''
				this.videoPreviewPath = ''
				this.type = 0
			}
		}
	}
</script>

<style>
	.campus-editor-page {
		min-height: 100vh;
		min-height: 100dvh;
		box-sizing: border-box;
		padding-bottom: calc(36rpx + env(safe-area-inset-bottom));
		background: #f7f9f8;
		color: #253936;
		transition: background 240ms ease, color 240ms ease;
	}

	.campus-editor-page .header {
		position: relative;
		z-index: 10;
		border-bottom: 1rpx solid rgba(33, 79, 73, 0.08);
		background: rgba(255, 255, 255, 0.78);
		backdrop-filter: blur(18px);
		-webkit-backdrop-filter: blur(18px);
	}

	.campus-editor-page .header .cu-bar {
		box-sizing: border-box;
		padding-left: 18rpx;
		padding-right: 18rpx;
	}

	.campus-editor-page .header .action {
		min-width: 92rpx;
	}

	.campus-editor-page .header .action:first-child {
		justify-content: flex-start;
	}

	.campus-editor-page .header .action:last-child {
		justify-content: flex-end;
	}

	.campus-editor-page .header .content {
		font-size: 34rpx;
		letter-spacing: 0;
		color: #213b37;
	}

	.post-editor-surface {
		position: relative;
		width: calc(100% - 32rpx);
		max-width: 760rpx;
		margin: 0 auto;
		padding: 28rpx 28rpx 22rpx;
		border: 1rpx solid #e0ebe8;
		border-radius: 22rpx;
		background: rgba(255, 255, 255, 0.94);
		box-shadow: 0 14rpx 36rpx rgba(37, 77, 72, 0.08);
		box-sizing: border-box;
	}

	.post-editor-input {
		width: 100%;
		min-height: 360rpx !important;
		padding: 4rpx 0;
		font-size: 31rpx;
		line-height: 1.65;
		color: #243d39;
		box-sizing: border-box;
	}

	.post-editor-input::placeholder {
		color: #a2afac;
	}
	.post-editor-status {
		display: flex;
		justify-content: space-between;
		gap: 20rpx;
		padding: 10rpx 4rpx 0;
		color: #7c8884;
		font-size: 23rpx;
	}
	.post-editor-status.is-error { color: #c44949; }
	.media-image.is-failed { position: relative; }
	.media-failed-mask {
		position: absolute;
		inset: 0;
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		gap: 6rpx;
		padding: 12rpx;
		background: rgba(30, 34, 33, .72);
		color: #fff;
		font-size: 21rpx;
		text-align: center;
	}
	.campus-editor-page.campus-night .post-editor-status { color: #9ba6a2; }
	.campus-editor-page.campus-night .post-editor-status.is-error { color: #ff9690; }

	.space-owo {
		width: calc(100% - 32rpx);
		max-width: 760rpx;
		height: auto;
		min-height: 76rpx;
		margin: 16rpx auto 0;
		padding: 0 22rpx;
		border: 1rpx solid #e0ebe8;
		border-radius: 18rpx;
		background: rgba(255, 255, 255, 0.9);
		box-shadow: 0 8rpx 22rpx rgba(37, 77, 72, 0.05);
		box-sizing: border-box;
	}

	.space-owo .cuIcon-emoji {
		float: none;
		display: inline-flex;
		align-items: center;
		justify-content: center;
		width: 76rpx;
		height: 76rpx;
		font-size: 40rpx;
		color: #657672;
		transition: color 200ms ease, transform 180ms ease;
	}

	.space-owo .cuIcon-emoji:active {
		transform: scale(0.9);
	}

	.space-owo .owo {
		position: relative;
		left: auto;
		right: auto;
		margin: 0 -22rpx;
		border-top: 1rpx solid #e0ebe8;
		border-radius: 0 0 18rpx 18rpx;
		background: #f8fbfa;
		overflow: hidden;
	}

	.space-owo .owo-list {
		max-height: 330rpx;
	}

	.space-owo .owo-main {
		padding: 16rpx 14rpx;
	}

	.space-owo .owo-type {
		display: flex;
		gap: 10rpx;
		padding: 12rpx 16rpx 16rpx;
		border-top: 1rpx solid #e4eeeb;
	}

	.space-owo .owo-box {
		flex: 1;
		padding: 12rpx 8rpx;
		border-radius: 10rpx;
		color: #6b7b77;
		font-size: 23rpx;
		text-align: center;
		transition: background-color 180ms ease, color 180ms ease;
	}

	.space-owo .owo-box.cur {
		background: #dcefeb;
		color: #147d73;
	}

	.space-pic {
		display: grid !important;
		grid-template-columns: repeat(3, minmax(0, 1fr));
		gap: 16rpx;
		width: calc(100% - 32rpx);
		max-width: 760rpx;
		margin: 18rpx auto 0;
		padding: 0;
		box-sizing: border-box;
	}

	.space-pic > view {
		width: auto !important;
		height: auto !important;
		margin: 0 !important;
		aspect-ratio: 1;
		border-radius: 18rpx;
		overflow: hidden;
	}

	.space-pic .bg-img {
		position: relative;
		width: 100%;
		height: 100%;
		border: 1rpx solid rgba(43, 91, 84, 0.1);
		background-color: #e8f1ef;
		background-size: cover;
		background-position: center;
	}

	.space-pic .bg-img .cuIcon-close {
		top: 10rpx;
		right: 10rpx;
		width: 48rpx;
		height: 48rpx;
		line-height: 48rpx;
		border-radius: 50%;
		background: rgba(30, 45, 46, 0.72) !important;
		color: #ffffff !important;
		font-size: 25rpx !important;
		text-align: center;
	}

	.space-upload {
		display: flex;
		align-items: center;
		justify-content: center;
		border: 1rpx dashed #bbd8d2;
		background: rgba(236, 247, 244, 0.88) !important;
		color: #3b9a8d;
		transition: background-color 200ms ease, border-color 200ms ease, transform 180ms ease;
	}

	.space-upload:active {
		transform: scale(0.96);
		border-color: #2e9d90;
	}

	.space-upload text {
		font-size: 58rpx !important;
		font-weight: 300;
	}

	.space-upload .video-ready-icon {
		font-size: 46rpx !important;
		color: #2d91bd;
	}

	.space-upload .video-ready-label {
		margin-left: 8rpx;
		font-size: 23rpx;
		color: #47645e;
	}

	.space-upload .bg-black {
		position: absolute;
		top: 10rpx;
		right: 10rpx;
		width: 48rpx;
		height: 48rpx;
		line-height: 48rpx;
		border-radius: 50%;
		background: rgba(30, 45, 46, 0.72) !important;
		font-size: 25rpx !important;
		text-align: center;
	}

	.post-submit-button {
		min-width: 118rpx;
		height: 60rpx;
		padding: 0 24rpx;
		border: 0 !important;
		border-radius: 999rpx !important;
		background: #168573 !important;
		box-shadow: 0 8rpx 16rpx rgba(31, 132, 143, 0.24);
		color: #ffffff !important;
		font-size: 25rpx;
		line-height: 60rpx;
		transition: transform 180ms ease, box-shadow 220ms ease, filter 220ms ease;
	}

	.post-submit-button:active {
		transform: translateY(1rpx) scale(0.95);
		box-shadow: 0 4rpx 10rpx rgba(31, 132, 143, 0.18);
		filter: brightness(0.96);
	}

	.post-submit-button-block {
		width: calc(100% - 64rpx);
		max-width: 680rpx;
		height: 88rpx;
		line-height: 88rpx;
		margin: 26rpx auto calc(18rpx + env(safe-area-inset-bottom)) !important;
		font-size: 30rpx;
	}

	.all-btn {
		padding: 0;
		background: transparent;
	}

	.post-policy-popup {
		color: #29423e;
		background: #ffffff;
		border-radius: 28rpx 28rpx 0 0;
	}

	.post-policy-popup .model-body {
		color: #667773;
		font-size: 26rpx;
		line-height: 1.75;
	}

	.campus-editor-page.campus-night {
		background: #121918;
		color: #edf1ef;
	}

	.campus-editor-page.campus-night .header {
		border-bottom-color: rgba(219, 235, 230, 0.09);
		background: rgba(25, 31, 33, 0.88);
	}

	.campus-editor-page.campus-night .header .content,
	.campus-editor-page.campus-night .header .action {
		color: #edf1ef !important;
	}

	.campus-editor-page.campus-night .post-editor-surface,
	.campus-editor-page.campus-night .space-owo {
		border-color: rgba(212, 230, 224, 0.1);
		background: rgba(32, 40, 42, 0.96);
		box-shadow: 0 12rpx 28rpx rgba(0, 0, 0, 0.16);
	}

	.campus-editor-page.campus-night .post-editor-input {
		color: #edf1ef;
	}

	.campus-editor-page.campus-night .post-editor-input::placeholder {
		color: #7d8a87;
	}

	.campus-editor-page.campus-night .space-owo .cuIcon-emoji {
		color: #a8b7b2;
	}

	.campus-editor-page.campus-night .space-owo .owo {
		border-top-color: rgba(212, 230, 224, 0.1);
		background: #252d2f;
	}

	.campus-editor-page.campus-night .space-owo .owo-type {
		border-top-color: rgba(212, 230, 224, 0.1);
	}

	.campus-editor-page.campus-night .space-owo .owo-box {
		color: #9eaba8;
	}

	.campus-editor-page.campus-night .space-owo .owo-box.cur {
		background: #31524d;
		color: #b9eee1;
	}

	.campus-editor-page.campus-night .space-upload {
		border-color: #496763;
		background: rgba(43, 56, 57, 0.96) !important;
		color: #91d4c5;
	}

	.campus-editor-page.campus-night .space-pic .bg-img {
		border-color: rgba(212, 230, 224, 0.1);
		background-color: #2a3536;
	}

	.campus-editor-page.campus-night .space-upload .video-ready-label {
		color: #b1c3be;
	}

	.campus-editor-page.campus-night .post-policy-popup {
		background: #242a2c;
		color: #edf1ef;
	}

	.campus-editor-page.campus-night .post-policy-popup .model-body {
		color: #aab8b4;
	}

	@media (min-width: 760px) {
		.campus-editor-page .post-editor-surface,
		.campus-editor-page .space-owo,
		.campus-editor-page .space-pic {
			max-width: 720px;
		}
	}

	@media (max-width: 360px) {
		.campus-editor-page .header .action {
			min-width: 78rpx;
		}
		.post-editor-surface {
			padding-left: 22rpx;
			padding-right: 22rpx;
		}
		.post-editor-input {
			font-size: 29rpx;
		}
	}

	.video-ready-icon {
		font-size: 42rpx;
		color: #2d91bd;
	}

	.video-ready-label {
		font-size: 24rpx;
		color: #47645e;
	}

	/* Protected editor baseline: quiet writing surface, one media entry point. */
	.campus-editor-page {
		background: #f7f9f8;
		color: #1d2d2a;
	}

	.campus-editor-page .header {
		border-bottom-color: #e5ebe9;
		background: #ffffff;
		backdrop-filter: none;
		-webkit-backdrop-filter: none;
	}

	.campus-editor-page .header .action:first-child text {
		font-size: 42rpx;
		color: #465450;
	}

	.campus-editor-page .header .content {
		font-size: 34rpx;
		font-weight: 600;
		color: #1d2d2a;
	}

	.post-submit-button {
		min-width: 116rpx;
		height: 60rpx;
		padding: 0 28rpx;
		background: #168573 !important;
		box-shadow: none;
		font-size: 26rpx;
		font-weight: 600;
		line-height: 60rpx;
	}

	.post-submit-button[disabled] {
		border: 1rpx solid #d7e1de !important;
		background: transparent !important;
		color: #98a5a1 !important;
		box-shadow: none;
		opacity: 1;
	}

	.post-compose {
		width: 100%;
		max-width: 760px;
		margin: 0 auto;
	}

	.anonymous-tip {
		display: flex;
		align-items: center;
		margin: 16rpx 28rpx 0;
		padding: 16rpx 20rpx;
		border-radius: 16rpx;
		background: #f0f9ff;
		color: #2a7fb8;
		font-size: 24rpx;
		line-height: 1.5;
	}

	.anonymous-tip .cuIcon-notice {
		margin-right: 10rpx;
		font-size: 32rpx;
	}

	.post-editor-surface {
		width: 100%;
		max-width: none;
		min-height: 0;
		margin: 0;
		padding: 24rpx 28rpx 0;
		border: 0;
		border-radius: 0;
		background: transparent;
		box-shadow: none;
		box-sizing: border-box;
	}

	.post-editor-input {
		width: 100%;
		min-height: clamp(360rpx, 42vh, 660rpx) !important;
		padding: 0;
		font-size: 32rpx;
		line-height: 1.65;
		color: #253532;
		box-sizing: border-box;
	}

	.post-editor-input::placeholder {
		color: #a2ada9;
	}

	.space-owo {
		width: calc(100% - 56rpx);
		max-width: none;
		min-height: 68rpx;
		margin: 0 28rpx;
		padding: 0;
		border: 0;
		border-top: 1rpx solid #e3eae7;
		border-bottom: 1rpx solid #e3eae7;
		border-radius: 0;
		background: transparent;
		box-shadow: none;
		box-sizing: border-box;
	}

	.space-owo .cuIcon-emoji {
		width: 68rpx;
		height: 68rpx;
		font-size: 36rpx;
		color: #75837f;
	}

	.space-owo .cuIcon-emoji.is-active {
		color: #168573;
	}

	.space-owo .owo {
		margin: 0;
		border-top: 0;
		border-radius: 0;
		background: #f1f5f3;
	}

	.media-section {
		width: calc(100% - 56rpx);
		max-width: none;
		margin: 28rpx 28rpx 0;
	}

	.media-section-heading {
		display: flex;
		align-items: baseline;
		justify-content: space-between;
		gap: 24rpx;
		margin-bottom: 16rpx;
	}

	.media-section-title {
		font-size: 27rpx;
		font-weight: 600;
		color: #334540;
	}

	.media-section-meta {
		flex: 1;
		font-size: 22rpx;
		color: #87948f;
		text-align: right;
	}

	.media-grid {
		display: grid;
		grid-template-columns: repeat(3, minmax(0, 1fr));
		gap: 14rpx;
	}

	.media-image,
	.media-video,
	.media-upload {
		position: relative;
		width: 100%;
		aspect-ratio: 1;
		border-radius: 12rpx;
		overflow: hidden;
		box-sizing: border-box;
	}

	.media-image {
		border: 1rpx solid #e3eae7;
		background-color: #e9f0ee;
		background-position: center;
		background-size: cover;
	}

	.media-video {
		border: 1rpx solid #dfe9e5;
		background: #dbe6e2;
	}

	.media-video-preview {
		display: block;
		width: 100%;
		height: 100%;
	}

	.media-video-badge {
		position: absolute;
		left: 12rpx;
		bottom: 12rpx;
		display: flex;
		align-items: center;
		gap: 6rpx;
		padding: 6rpx 12rpx;
		border-radius: 999rpx;
		background: rgba(23, 42, 38, 0.72);
		color: #ffffff;
		font-size: 21rpx;
	}

	.media-upload {
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		border: 1rpx dashed #bdd3cc;
		background: #f0f5f3 !important;
		color: #168573;
		transition: background-color 180ms ease, border-color 180ms ease, transform 180ms ease;
	}

	.media-upload:active {
		transform: scale(0.97);
		border-color: #168573;
		background: #e8f2ef !important;
	}

	.media-upload > text:first-child {
		font-size: 54rpx !important;
		font-weight: 300;
		line-height: 1;
	}

	.media-upload-label {
		margin-top: 10rpx;
		font-size: 22rpx;
	}

	.media-remove {
		position: absolute;
		top: 10rpx;
		right: 10rpx;
		display: flex;
		align-items: center;
		justify-content: center;
		width: 44rpx;
		height: 44rpx;
		border-radius: 50%;
		background: rgba(24, 38, 35, 0.72);
		color: #ffffff !important;
		font-size: 23rpx !important;
	}

	.post-policy-popup {
		padding: 38rpx 42rpx calc(34rpx + env(safe-area-inset-bottom));
		border-radius: 28rpx 28rpx 0 0;
		background: #ffffff;
		color: #243a34;
	}

	.post-policy-title {
		font-size: 31rpx;
		font-weight: 600;
		text-align: center;
	}

	.post-policy-popup .model-body {
		margin-top: 20rpx;
		color: #687772;
		font-size: 26rpx;
		line-height: 1.7;
		text-align: center;
	}

	.post-policy-confirm {
		display: block;
		width: 240rpx;
		margin: 26rpx auto 0;
	}

	.campus-editor-page.campus-night {
		background: #121918;
		color: #e8efec;
	}

	.campus-editor-page.campus-night .header {
		border-bottom-color: #293633;
		background: #17201e;
	}

	.campus-editor-page.campus-night .header .content,
	.campus-editor-page.campus-night .header .action,
	.campus-editor-page.campus-night .header .action:first-child text {
		color: #e8efec !important;
	}

	.campus-editor-page.campus-night .post-submit-button {
		background: #3a9278 !important;
	}

	.campus-editor-page.campus-night .post-submit-button[disabled] {
		border-color: #34443f !important;
		background: transparent !important;
		color: #75857f !important;
	}

	.campus-editor-page.campus-night .post-editor-input {
		color: #e8efec;
	}

	.campus-editor-page.campus-night .post-editor-surface {
		border: 0;
		background: transparent;
		box-shadow: none;
	}

	.campus-editor-page.campus-night .post-editor-input::placeholder {
		color: #74847e;
	}

	.campus-editor-page.campus-night .space-owo,
	.campus-editor-page.campus-night .media-section {
		border-color: #293633;
	}

	.campus-editor-page.campus-night .space-owo {
		border-left-color: transparent;
		border-right-color: transparent;
		background: transparent;
		box-shadow: none;
	}

	.campus-editor-page.campus-night .space-owo .cuIcon-emoji {
		color: #8e9e98;
	}

	.campus-editor-page.campus-night .space-owo .cuIcon-emoji.is-active {
		color: #65c3a5;
	}

	.campus-editor-page.campus-night .space-owo .owo {
		background: #1b2623;
	}

	.campus-editor-page.campus-night .media-section-title {
		color: #d6e2dd;
	}

	.campus-editor-page.campus-night .media-section-meta {
		color: #82918b;
	}

	.campus-editor-page.campus-night .media-image {
		border-color: #30413c;
		background-color: #263630;
	}

	.campus-editor-page.campus-night .media-video {
		border-color: #30413c;
		background: #263630;
	}

	.campus-editor-page.campus-night .media-upload {
		border-color: #496d60;
		background: #1c2b27 !important;
		color: #6fc6a8;
	}

	.campus-editor-page.campus-night .media-upload:active {
		background: #253a34 !important;
	}

	.campus-editor-page.campus-night .post-policy-popup {
		background: #1d2423;
		color: #e8efec;
	}

	.campus-editor-page.campus-night .post-policy-popup .model-body {
		color: #a9b8b2;
	}

	@media (min-width: 760px) {
		.post-compose {
			padding: 0 24px;
			box-sizing: border-box;
		}
		.post-editor-surface {
			padding-left: 0;
			padding-right: 0;
		}
		.space-owo,
		.media-section {
			width: 100%;
			margin-left: 0;
			margin-right: 0;
		}
	}

	@media (max-height: 700px) {
		.post-editor-surface {
			padding-top: 14rpx;
		}
		.post-editor-input {
			min-height: 300rpx !important;
		}
		.media-section {
			margin-top: 18rpx;
		}
	}

	/* The editor owns the viewport while open; the parent tabbar must not sit above it. */
	html.campus-editor-open .tabbar-system,
	body.campus-editor-open .tabbar-system {
		display: none !important;
	}

	.campus-editor-page {
		position: relative;
		height: 100vh;
		height: 100dvh;
		overflow-x: hidden;
		overflow-y: auto;
		padding-bottom: calc(24rpx + env(safe-area-inset-bottom));
	}

	.post-editor-input {
		min-height: clamp(280rpx, 32vh, 460rpx) !important;
	}

	.space-owo {
		position: relative;
		z-index: 3;
	}

	.space-owo .owo {
		position: relative !important;
		top: auto !important;
		left: auto !important;
		right: auto !important;
		bottom: auto !important;
		width: 100% !important;
		max-width: none !important;
		margin: 0 !important;
		box-sizing: border-box;
		border: 1rpx solid #dbe6e2;
		border-radius: 0 0 12rpx 12rpx;
		box-shadow: 0 12rpx 24rpx rgba(35, 61, 55, 0.08);
	}

	.space-owo .owo-list {
		height: 236rpx;
		max-height: none;
		overflow-x: hidden;
		overflow-y: auto !important;
	}

	.space-owo .owo-main {
		display: grid;
		grid-template-columns: repeat(7, minmax(0, 1fr));
		gap: 8rpx;
		padding: 12rpx 16rpx;
		box-sizing: border-box;
	}

	.space-owo .owo-lit-box {
		display: flex;
		align-items: center;
		justify-content: center;
		width: 100% !important;
		height: auto !important;
		min-width: 0;
		aspect-ratio: 1;
	}

	.space-owo .owo-lit-box image {
		width: 48rpx;
		height: 48rpx;
	}

	.space-owo .owo-type {
		display: grid;
		grid-template-columns: repeat(3, minmax(0, 1fr));
		gap: 8rpx;
		height: 68rpx;
		margin: 0 !important;
		padding: 8rpx 12rpx;
		box-sizing: border-box;
	}

	.space-owo .owo-box {
		display: flex;
		align-items: center;
		justify-content: center;
		padding: 0;
		border-radius: 8rpx;
		font-size: 23rpx;
	}

	.media-section {
		margin-top: 22rpx;
		padding-bottom: calc(18rpx + env(safe-area-inset-bottom));
	}

	.media-section-heading {
		margin-bottom: 12rpx;
	}

	.media-section-title {
		font-size: 25rpx;
	}

	.media-section-meta {
		font-size: 21rpx;
	}

	.media-grid {
		gap: 10rpx;
	}

	.media-image,
	.media-video,
	.media-upload {
		border-radius: 10rpx;
	}

	.media-upload-label {
		font-size: 21rpx;
	}

	.campus-editor-page.campus-night .space-owo .owo {
		border-color: #30413c;
		background: #18221f;
		box-shadow: 0 12rpx 24rpx rgba(0, 0, 0, 0.22);
	}

	.campus-editor-page.campus-night .space-owo .owo-type {
		border-top-color: #30413c;
		background: #18221f;
	}

	.campus-editor-page.campus-night .media-upload {
		background: #1c2b27 !important;
	}

	/* Native composer treatment: neutral surfaces, minimal labels and decoration. */
	.campus-editor-page {
		background: #ffffff;
		color: #202426;
	}

	.campus-editor-page .header {
		border-bottom-color: #eceff0;
		background: #ffffff;
	}

	.campus-editor-page .header .content {
		color: #202426;
		font-weight: 600;
	}

	.campus-editor-page .header .action:first-child text {
		color: #33383a;
	}

	.post-compose {
		max-width: 680px;
	}

	.post-editor-surface {
		padding: 26rpx 24rpx 0;
	}

	.post-editor-input {
		min-height: clamp(260rpx, 30vh, 420rpx) !important;
		font-size: 30rpx;
		line-height: 1.6;
		color: #24292b;
	}

	.post-editor-input::placeholder {
		color: #a4aaad;
	}

	.space-owo {
		width: calc(100% - 48rpx);
		min-height: 58rpx;
		margin: 0 24rpx;
		border: 0;
	}

	.space-owo .cuIcon-emoji {
		width: 58rpx;
		height: 58rpx;
		font-size: 34rpx;
		color: #767e81;
	}

	.space-owo .cuIcon-emoji.is-active {
		color: #287d69;
	}

	.space-owo .owo {
		border: 1rpx solid #e2e6e7;
		border-radius: 4rpx;
		background: #f7f8f8;
		box-shadow: none;
	}

	.space-owo .owo-type {
		border-top-color: #e2e6e7;
		background: #f7f8f8;
	}

	.space-owo .owo-box {
		border-radius: 4rpx;
		color: #71797c;
	}

	.space-owo .owo-box.cur {
		background: #e8eeec;
		color: #246b5b;
	}

	.media-section {
		width: calc(100% - 48rpx);
		margin: 14rpx 24rpx 0;
		padding-bottom: calc(16rpx + env(safe-area-inset-bottom));
	}

	.media-grid {
		gap: 10rpx;
	}

	.media-image,
	.media-video,
	.media-upload {
		border-radius: 5rpx;
	}

	.media-upload {
		border: 1rpx solid #e2e5e6;
		background: #f2f3f3 !important;
		color: #7e8789;
	}

	.media-upload:active {
		border-color: #cbd4d1;
		background: #eaedec !important;
	}

	.media-upload > text:first-child {
		font-size: 50rpx !important;
		color: inherit;
	}

	.media-video-badge {
		border-radius: 4rpx;
		background: rgba(24, 28, 30, 0.72);
	}

	.topic-editor {
		width: calc(100% - 48rpx);
		margin: 18rpx 24rpx 28rpx;
		padding-top: 22rpx;
		border-top: 1rpx solid #e5e8e9;
		box-sizing: border-box;
	}

	.component-editor { width:calc(100% - 48rpx); margin:18rpx 24rpx 0; box-sizing:border-box; }
	.component-add { display:flex; align-items:center; gap:10rpx; min-height:72rpx; color:#287d69; font-size:25rpx; border-top:1rpx solid #e5e8e9; }
	.poll-summary { display:flex; align-items:center; min-height:100rpx; padding:16rpx; border:1rpx solid #dfe6e4; border-radius:8rpx; background:#f7f9f8; box-sizing:border-box; }
	.poll-summary-main { display:flex; align-items:center; gap:16rpx; flex:1; min-width:0; }
	.poll-summary-icon { color:#168573; font-size:36rpx; }
	.poll-summary-title { color:#2d393c; font-size:26rpx; font-weight:600; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
	.poll-summary-meta { margin-top:6rpx; color:#8a9395; font-size:22rpx; }
	.poll-remove { padding:18rpx 0 18rpx 18rpx; color:#8a9395; }
	.poll-modal-mask { position:fixed; z-index:9999; inset:0; display:flex; align-items:flex-end; background:rgba(0,0,0,.48); }
	.poll-modal { width:100%; max-height:86vh; border-radius:12rpx 12rpx 0 0; background:#fff; overflow:hidden; }
	.poll-modal-head { display:grid; grid-template-columns:1fr auto 1fr; align-items:center; height:92rpx; padding:0 24rpx; border-bottom:1rpx solid #e8ebec; color:#667174; font-size:25rpx; }
	.poll-modal-title { color:#273235; font-size:29rpx; font-weight:600; }
	.poll-save { justify-self:end; color:#168573; font-weight:600; }
	.poll-modal-body { max-height:calc(86vh - 92rpx); padding:22rpx 24rpx calc(34rpx + env(safe-area-inset-bottom)); box-sizing:border-box; }
	.poll-input,.poll-intro,.poll-option-row { width:100%; border:1rpx solid #dfe4e5; border-radius:6rpx; background:#f8f9f9; box-sizing:border-box; }
	.poll-input { height:76rpx; padding:0 18rpx; font-size:26rpx; }
	.poll-intro { height:116rpx; margin-top:16rpx; padding:16rpx 18rpx; font-size:25rpx; }
	.poll-option-row { display:flex; align-items:center; height:72rpx; margin-top:14rpx; padding:0 16rpx; }
	.poll-option-row input { flex:1; min-width:0; padding:0 14rpx; font-size:25rpx; }
	.poll-option-index { color:#168573; font-weight:600; }
	.poll-option-row .cuIcon-close { color:#939b9e; padding:15rpx 0 15rpx 15rpx; }
	.poll-add-option { display:flex; align-items:center; gap:8rpx; height:68rpx; color:#287d69; font-size:24rpx; }
	.poll-setting-row { display:flex; align-items:center; justify-content:space-between; min-height:82rpx; border-top:1rpx solid #e8ebec; color:#303a3d; font-size:25rpx; }
	.poll-setting-row switch { transform:scale(.78); transform-origin:right center; }
	.poll-choice-picker { color:#687275; }
	.poll-privacy-note { padding:18rpx 0 4rpx; color:#90989a; font-size:22rpx; }
	.campus-editor-page.campus-night .component-add { border-top-color:#2d3335; color:#62a894; }
	.campus-editor-page.campus-night .poll-summary { border-color:#303738; background:#191d1e; }
	.campus-editor-page.campus-night .poll-summary-title,.campus-editor-page.campus-night .poll-modal-title,.campus-editor-page.campus-night .poll-setting-row { color:#e0e5e6; }
	.campus-editor-page.campus-night .poll-modal { background:#181c1d; }
	.campus-editor-page.campus-night .poll-modal-head,.campus-editor-page.campus-night .poll-setting-row { border-color:#2d3335; }
	.campus-editor-page.campus-night .poll-input,.campus-editor-page.campus-night .poll-intro,.campus-editor-page.campus-night .poll-option-row { border-color:#303739; background:#141819; color:#e1e5e6; }

	.topic-editor-head,
	.topic-picker-item,
	.topic-create-row {
		display: flex;
		align-items: center;
	}

	.topic-editor-head {
		justify-content: space-between;
	}

	.topic-editor-title {
		font-size: 27rpx;
		font-weight: 600;
		color: #31383b;
	}

	.topic-editor-toggle,
	.topic-follow-action,
	.topic-create-button {
		font-size: 24rpx;
		color: #287d69;
	}

	.topic-chip-list {
		display: flex;
		flex-wrap: wrap;
		gap: 12rpx;
		margin-top: 16rpx;
	}

	.topic-chip {
		display: inline-flex;
		align-items: center;
		gap: 8rpx;
		height: 52rpx;
		padding: 0 16rpx;
		border-radius: 8rpx;
		background: #e9f3f0;
		font-size: 24rpx;
		color: #246b5b;
	}

	.topic-chip-close {
		font-size: 20rpx;
	}

	.topic-recommend {
		display: flex;
		align-items: center;
		gap: 14rpx;
		margin-top: 16rpx;
		min-width: 0;
	}

	.topic-recommend-label {
		flex: 0 0 auto;
		font-size: 22rpx;
		color: #8b9497;
	}

	.topic-recommend-scroll {
		flex: 1;
		min-width: 0;
		white-space: nowrap;
	}

	.topic-recommend-track {
		display: inline-flex;
		align-items: center;
		gap: 10rpx;
		padding-right: 8rpx;
	}

	.topic-recommend-chip {
		display: inline-flex;
		align-items: center;
		height: 48rpx;
		padding: 0 15rpx;
		border: 1rpx solid #dde3e2;
		border-radius: 8rpx;
		background: #f7f9f8;
		font-size: 23rpx;
		color: #687274;
		transition: border-color 0.16s ease, background-color 0.16s ease, color 0.16s ease;
	}

	.topic-recommend-chip.is-selected {
		border-color: #9fc8bc;
		background: #e9f3f0;
		color: #246b5b;
	}

	.topic-recommend-state {
		font-size: 22rpx;
		color: #9aa1a3;
	}

	.topic-picker {
		margin-top: 18rpx;
		border: 1rpx solid #e1e6e7;
		border-radius: 8rpx;
		background: #f7f9f9;
		overflow: hidden;
	}

	.topic-picker-list {
		max-height: 360rpx;
		overflow-y: auto;
	}

	.topic-picker-item {
		min-height: 72rpx;
		padding: 0 18rpx;
		border-bottom: 1rpx solid #e8ebec;
	}

	.topic-picker-name {
		flex: 1;
		min-width: 0;
		font-size: 25rpx;
		color: #2d393c;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.topic-picker-count {
		margin-right: 18rpx;
		font-size: 22rpx;
		color: #929a9d;
	}

	.topic-follow-action {
		flex: 0 0 auto;
	}

	.topic-empty {
		padding: 28rpx 18rpx 12rpx;
		font-size: 23rpx;
		color: #929a9d;
	}

	.topic-create-row {
		gap: 14rpx;
		padding: 16rpx 18rpx;
	}

	.topic-create-input {
		flex: 1;
		min-width: 0;
		height: 64rpx;
		padding: 0 16rpx;
		border: 1rpx solid #dce2e3;
		border-radius: 6rpx;
		background: #fff;
		font-size: 24rpx;
		box-sizing: border-box;
	}

	.topic-create-button {
		flex: 0 0 auto;
		padding: 16rpx 8rpx;
		font-weight: 600;
	}

	.campus-editor-page.campus-night {
		background: #121516;
		color: #e5e8e9;
	}

	.campus-editor-page.campus-night .header {
		border-bottom-color: #292e30;
		background: #161a1b;
	}

	.campus-editor-page.campus-night .header .content,
	.campus-editor-page.campus-night .header .action,
	.campus-editor-page.campus-night .header .action:first-child text {
		color: #e5e8e9 !important;
	}

	.campus-editor-page.campus-night .post-editor-input {
		color: #e5e8e9;
	}

	.campus-editor-page.campus-night .post-editor-input::placeholder {
		color: #727a7d;
	}

	.campus-editor-page.campus-night .space-owo .cuIcon-emoji {
		color: #838c8f;
	}

	.campus-editor-page.campus-night .space-owo .cuIcon-emoji.is-active {
		color: #62a894;
	}

	.campus-editor-page.campus-night .space-owo .owo,
	.campus-editor-page.campus-night .space-owo .owo-type {
		border-color: #2d3335;
		background: #191d1e;
		box-shadow: none;
	}

	.campus-editor-page.campus-night .space-owo .owo-box {
		color: #939b9e;
	}

	.campus-editor-page.campus-night .space-owo .owo-box.cur {
		background: #293331;
		color: #b8d2ca;
	}

	.campus-editor-page.campus-night .media-upload {
		border-color: #2e3436;
		background: #1b1f20 !important;
		color: #7d8688;
	}

	.campus-editor-page.campus-night .media-upload:active {
		background: #222728 !important;
	}

	.campus-editor-page.campus-night .topic-editor {
		border-top-color: #2d3335;
	}

	.campus-editor-page.campus-night .topic-editor-title,
	.campus-editor-page.campus-night .topic-picker-name {
		color: #e0e5e6;
	}

	.campus-editor-page.campus-night .topic-editor-toggle,
	.campus-editor-page.campus-night .topic-follow-action,
	.campus-editor-page.campus-night .topic-create-button {
		color: #62a894;
	}

	.campus-editor-page.campus-night .topic-chip {
		background: #263531;
		color: #9dc9bc;
	}

	.campus-editor-page.campus-night .topic-recommend-label,
	.campus-editor-page.campus-night .topic-recommend-state {
		color: #7f898b;
	}

	.campus-editor-page.campus-night .topic-recommend-chip {
		border-color: #303738;
		background: #1a1f20;
		color: #9aa3a5;
	}

	.campus-editor-page.campus-night .topic-recommend-chip.is-selected {
		border-color: #477d6f;
		background: #263531;
		color: #9dc9bc;
	}

	.campus-editor-page.campus-night .topic-picker {
		border-color: #2d3436;
		background: #191d1e;
	}

	.campus-editor-page.campus-night .topic-picker-item {
		border-bottom-color: #2a3032;
	}

	.campus-editor-page.campus-night .topic-create-input {
		border-color: #303739;
		background: #141819;
		color: #e1e5e6;
	}
</style>
