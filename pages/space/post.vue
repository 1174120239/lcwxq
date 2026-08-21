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
						<button v-if="type==0||type==4" class="cu-btn round post-submit-button" :disabled="!canPublish" @tap="publishSpace">{{isUploading ? '上传中' : isSubmitting ? '提交中' : '发布'}}</button>
					</block>
					<block v-else>
						<button class="cu-btn round post-submit-button" :disabled="!canPublish" @tap="editSpace()">{{isUploading ? '上传中' : isSubmitting ? '保存中' : '保存'}}</button>
					</block>
					<!--  #endif -->
				</view>
			</view>
		</view>
		<form>
			<view class="post-compose">
				<rich-composer v-model="text" :maxlength="maxTextLength" placeholder="分享校园里的新鲜事…"
					:night="campusNight" :show-component="postType=='add' && !anonymousMode && type==0"
					:status="publishReason" @emoji="OwO" @media="chooseMedia" @component="openComponentPicker"></rich-composer>

				<!--  #ifdef H5 || APP-PLUS -->
				<view class="comments-owo space-owo" v-if="isOwO">
					<view class="owo">
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
					<view class="anonymous-tip-icon"><text class="cuIcon-lock"></text></view>
					<view class="anonymous-tip-copy">
						<text class="anonymous-tip-title">匿名发布已开启</text>
						<text class="anonymous-tip-desc">仅显示匿名身份</text>
					</view>
					<text class="anonymous-tip-state">已保护</text>
				</view>

				<view class="media-section" v-if="type==0||type==4">
					<view class="media-section-heading">
						<view class="media-section-heading-main">
							<text class="media-section-title">图片和视频</text>
						</view>
						<text class="media-section-count" v-if="type==0">{{picList.length + pendingUploads.length}}/9</text>
					</view>
					<view v-if="mediaOrderMode" class="media-order-banner">
						<view class="media-order-banner-copy"><text class="cuIcon-sort"></text><text>正在调整图片顺序</text><text class="media-order-banner-hint">使用左右按钮移动</text></view>
						<text class="media-order-done" @tap="mediaOrderMode=false">完成</text>
					</view>
					<view class="media-grid">
						<view class="media-image" :class="{'is-failed': item.failed, 'is-uploading': item.uploading}" :style="'background-image:url('+item.path+');'" v-for="(item, mediaIndex) in mediaItems" :key="(item.uploading ? 'uploading-' : item.failed ? 'failed-' : 'uploaded-') + item.order + '-' + item.path" @tap="previewMedia(item)" @longpress="enableMediaOrder(item)">
							<text v-if="mediaOrderMode && !item.uploading && !item.failed" class="media-order-index">{{mediaIndex + 1}}</text>
							<view class="media-uploading-mask" v-if="item.uploading">
								<view class="media-uploading-spinner"></view>
								<text>{{item.progress}}%</text>
							</view>
							<view class="media-failed-mask" v-if="item.failed" @tap.stop="retryFailedUpload(item.source)">
								<text class="cuIcon-refresh"></text><text>上传失败，重试</text>
							</view>
							<text class="cuIcon-close media-remove" v-if="!item.uploading" @tap.stop="item.failed ? removeFailedUpload(item.source) : picClose(item.path)"></text>
							<view v-if="mediaOrderMode && !item.uploading && !item.failed" class="media-order-controls"><text class="cuIcon-back" @tap.stop="movePic(item.path, -1)"></text><text class="cuIcon-right" @tap.stop="movePic(item.path, 1)"></text></view>
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
				<view class="upload-progress-card" v-if="isUploading">
					<view class="upload-progress-head">
						<view class="upload-progress-title-wrap">
							<view class="upload-progress-icon"><view class="upload-progress-spinner"></view></view>
							<view class="upload-progress-title">{{uploadCurrentLabel || '正在准备上传'}}</view>
						</view>
						<text class="upload-progress-value">{{uploadProgress}}%</text>
					</view>
					<view class="upload-progress-track"><view class="upload-progress-fill" :style="{width: uploadProgress + '%'}"></view></view>
					<view class="upload-progress-foot">
						<text>{{uploadTotal ? (uploadCompleted+'/'+uploadTotal) : '准备中'}}</text>
						<text>完成后可发布</text>
					</view>
				</view>
				<view class="component-editor" v-if="postType=='add' && !anonymousMode && type==0">
					<view class="component-add" v-if="!poll" @tap="openComponentPicker">
						<view class="component-add-icon"><text class="cuIcon-add"></text></view>
						<view class="component-add-copy"><text>添加组件</text><text>投票等互动内容</text></view>
						<text class="cuIcon-right component-add-arrow"></text>
					</view>
					<view class="poll-summary" v-else>
						<view class="poll-summary-main" @tap="openPollEditor">
							<view class="poll-summary-icon"><text class="cuIcon-rank"></text></view>
							<view class="poll-summary-copy">
								<view class="poll-summary-label">投票组件</view>
								<view class="poll-summary-title">{{poll.title}}</view>
								<view class="poll-summary-meta">{{poll.multiple ? '多选，最多 '+poll.maxChoices+' 项' : '单选'}} · {{poll.options.length}} 个选项</view>
							</view>
							<text class="cuIcon-right poll-summary-arrow"></text>
						</view>
						<view class="poll-remove" @tap="removePoll"><text class="cuIcon-delete"></text></view>
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
						<button v-if="type==0||type==4" class="cu-btn post-submit-button post-submit-button-block margin-tb-sm lg" :disabled="!canPublish" @tap="publishSpace">{{isUploading ? '上传中' : isSubmitting ? '提交中' : '发布'}}</button>
					</block>
					<block v-else>
						<button class="cu-btn post-submit-button post-submit-button-block margin-tb-sm lg" :disabled="!canPublish" @tap="editSpace()">{{isUploading ? '上传中' : isSubmitting ? '保存中' : '保存'}}</button>
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
		<view class="component-sheet-layer" v-if="componentPickerVisible" @tap="closeComponentPicker">
			<view class="component-sheet" @tap.stop>
				<view class="sheet-handle"></view>
				<view class="sheet-head">
					<view><view class="sheet-title">添加组件</view><view class="sheet-subtitle">为动态增加互动内容</view></view>
					<view class="sheet-close cuIcon-close" @tap="closeComponentPicker"></view>
				</view>
				<view class="component-option" @tap="selectPollComponent">
					<view class="component-option-icon"><text class="cuIcon-rank"></text></view>
					<view class="component-option-copy"><view>投票</view><text>发起单选或多选投票，发布后展示结果条</text></view>
					<text class="cuIcon-right component-option-arrow"></text>
				</view>
			</view>
		</view>
		<view class="poll-modal-mask" v-if="pollEditorVisible" @tap="closePollEditor">
			<view class="poll-modal" @tap.stop>
				<view class="sheet-handle"></view>
				<view class="poll-modal-head">
					<text class="poll-cancel" @tap="closePollEditor">取消</text>
					<view class="poll-modal-heading"><view>{{poll ? '编辑投票' : '创建投票'}}</view><text>发布后不能修改选项</text></view>
					<text class="poll-save" :class="{'is-disabled': !canSavePoll}" @tap="savePoll">完成</text>
				</view>
				<scroll-view scroll-y class="poll-modal-body">
					<view class="poll-section">
						<view class="poll-section-head"><text>投票内容</text><text>{{pollDraft.title.length}}/80</text></view>
						<input class="poll-input" v-model="pollDraft.title" maxlength="80" placeholder="你想问什么？" />
						<textarea class="poll-intro" v-model="pollDraft.description" maxlength="240" placeholder="补充说明（选填）"></textarea>
					</view>
					<view class="poll-section">
						<view class="poll-section-head"><text>选项</text><text>{{pollDraft.options.length}}/6</text></view>
						<view class="poll-option-row" v-for="(option,index) in pollDraft.options" :key="index">
							<view class="poll-option-index">{{index+1}}</view>
							<input v-model="pollDraft.options[index]" maxlength="80" :placeholder="'选项 '+(index+1)" />
							<view class="poll-option-remove" v-if="pollDraft.options.length>2" @tap="removePollOption(index)"><text class="cuIcon-close"></text></view>
						</view>
						<view class="poll-add-option" :class="{'is-disabled':pollDraft.options.length>=6}" @tap="addPollOption">
							<text class="cuIcon-add"></text><text>{{pollDraft.options.length<6 ? '增加选项' : '最多添加 6 个选项'}}</text>
						</view>
					</view>
					<view class="poll-section poll-rule-section">
						<view class="poll-section-head"><text>选择规则</text></view>
						<view class="poll-mode-segment">
							<view :class="{'is-active':!pollDraft.multiple}" @tap="setPollMode(false)"><text>单选</text><text class="poll-mode-help">每人选 1 项</text></view>
							<view :class="{'is-active':pollDraft.multiple}" @tap="setPollMode(true)"><text>多选</text><text class="poll-mode-help">可选择多项</text></view>
						</view>
						<view class="poll-setting-row" v-if="pollDraft.multiple">
							<view><text>最多选择</text><text class="poll-setting-help">每位参与者的选择上限</text></view>
							<picker :range="pollChoiceRanges" :value="pollChoiceIndex" @change="changePollMax">
								<view class="poll-choice-picker">{{pollDraft.maxChoices}} 项 <text class="cuIcon-right"></text></view>
							</picker>
						</view>
					</view>
					<view class="poll-privacy-note"><text class="cuIcon-lock"></text><text>投票匿名展示，其他用户看不到具体选择人。</text></view>
				</scroll-view>
			</view>
		</view>
	</view>
</template>

<script>
	import { localStorage } from '../../js_sdk/mp-storage/mp-storage/index.js'
	import { applyCampusThemeShell, getCampusThemeMode, isDongchangfuNight, resolveCampusNight } from '@/utils/campusTheme.js'
	import RichComposer from '@/components/rich-composer/rich-composer'
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
		components: { RichComposer },
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
				isSubmitting:false,
				pendingUploads:[],
				uploadProgress:0,
				uploadTotal:0,
				uploadCompleted:0,
				uploadCurrentLabel:'',
				failedUploads:[],
				imageOrder:{},
				mediaOrderMode:false,
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
				componentPickerVisible:false,
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
			canSavePoll() {
				const title = (this.pollDraft.title || '').trim()
				const options = (this.pollDraft.options || []).map(item => (item || '').trim())
				return Boolean(title) && options.length >= 2 && options.every(Boolean) && new Set(options).size === options.length
			},
			campusNight() {
				return resolveCampusNight(this.campusThemeMode, isDongchangfuNight(this.campusThemeClock))
			},
			canAddMedia() {
				if (this.isUploading) return false
				if (this.type === 4 && this.pic) return false
				return this.picList.length + this.pendingUploads.length + this.failedUploads.length < 9
			},
			mediaItems() {
				const items = this.picList.map((url, index) => ({
					path: url,
					order: Object.prototype.hasOwnProperty.call(this.imageOrder, url)
						? Number(this.imageOrder[url]) : index,
					failed: false,
					uploading: false
				}))
				this.pendingUploads.forEach(item => items.push({
					path: item.path,
					order: Number(item.order),
					failed: false,
					uploading: true,
					progress: Number(item.progress) || 0,
					source: item
				}))
				this.failedUploads.forEach((item, index) => items.push({
					path: item.path,
					order: Number(item.order == null ? this.picList.length + index : item.order),
					failed: true,
					uploading: false,
					source: item
				}))
				return items.sort((left, right) => left.order - right.order)
			},
			textCount() {
				return this.stripImageMarker(this.text).length
			},
			publishReason() {
				if (this.isUploading) return '媒体正在上传，请稍候'
				if (this.isSubmitting) return '正在提交，请稍候'
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
				if (this.isUploading || this.isSubmitting || this.pendingUploads.length || this.failedUploads.length) return false
				if (this.textCount > this.maxTextLength) return false
				const textLength = this.stripImageMarker(this.text).trim().length
				const hasText = textLength > 0
				if (this.type === 4) return hasText && Boolean(this.pic)
				if (this.type === 0) return textLength >= 4 || this.picList.length > 0 || Boolean(this.poll)
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
			openComponentPicker() { this.componentPickerVisible = true },
			closeComponentPicker() { this.componentPickerVisible = false },
			selectPollComponent() { this.componentPickerVisible = false; this.openPollEditor() },
			blankPollDraft() { return { title:'', description:'', options:['',''], multiple:false, maxChoices:1 } },
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
			setPollMode(multiple) {
				this.pollDraft.multiple = Boolean(multiple)
				this.pollDraft.maxChoices = multiple ? Math.min(2, this.pollDraft.options.length) : 1
			},
			changePollMax(event) { this.pollDraft.maxChoices = this.pollChoiceRanges[Number(event.detail.value)] || 2 },
			savePoll() {
				if (!this.canSavePoll) {
					uni.showToast({ title: !(this.pollDraft.title || '').trim() ? '请填写投票标题' : '请检查选项是否完整或重复', icon: 'none' })
					return
				}
				const options = this.pollDraft.options.map(item => item.trim())
				this.poll = { title:this.pollDraft.title.trim(), description:(this.pollDraft.description || '').trim(), options, multiple:!!this.pollDraft.multiple, maxChoices:this.pollDraft.multiple ? Number(this.pollDraft.maxChoices) : 1 }
				this.pollEditorVisible = false
			},
			removePoll() {
				uni.showModal({ title:'移除投票', content:'移除后已填写的投票内容不会保留。', confirmColor:'#d45555', success:({confirm}) => { if (confirm) this.poll = null } })
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
						const removesPoll = nextType === 4 && Boolean(this.poll)
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
								? (changesMediaType ? '视频动态暂不支持投票，继续后将移除投票并替换当前图片。' : '视频动态暂不支持投票，继续后将移除当前投票。')
								: (nextType === 4 ? '一条动态不能同时包含图片和视频，继续后将用视频替换当前图片。' : '一条动态不能同时包含视频和图片，继续后将用图片替换当前视频。'),
							success: ({ confirm }) => {
								if (confirm) selectMedia()
							}
						})
					}
				})
			},
			publishSpace() {
				if (this.isSubmitting) return
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
				if (that.isSubmitting) return false;
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
				that.isSubmitting = true;
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
						} else {
							that.isSubmitting = false
						}
						},
					fail: function(res) {
						that.isSubmitting = false
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
				if (that.isSubmitting) return false;
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
				that.isSubmitting = true;
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
						} else {
							that.isSubmitting = false
						}
					},
					fail: function(res) {
						that.isSubmitting = false
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
				if (that.isSubmitting) return false;
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
				that.isSubmitting = true;
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
						} else {
							that.isSubmitting = false
						}
					},
					fail: function(res) {
						that.isSubmitting = false
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
			uploadMediaFile(filePath, onProgress) {
				return new Promise((resolve, reject) => {
					let settled = false
					const finish = (handler, value) => {
						if (settled) return
						settled = true
						handler(value)
					}
					const task = uni.uploadFile({
						url: this.$API.upload(),
						filePath,
						name: 'file',
						formData: { token: this.token },
						success: (response) => {
							let body = response && response.data
							try {
								if (typeof body === 'string') body = JSON.parse(body)
							} catch (error) {
								finish(reject, { msg: '上传响应格式异常' })
								return
							}
							if (body && body.code == 1 && body.data && body.data.url) {
								finish(resolve, body.data)
								return
							}
							finish(reject, { msg: (body && body.msg) || '上传失败' })
						},
						fail: (error) => finish(reject, { msg: '网络异常，上传失败', error })
					})
					if (task && task.onProgressUpdate) {
						task.onProgressUpdate((event) => {
							const progress = Math.max(0, Math.min(100, Number(event && event.progress) || 0))
							if (onProgress) onProgress(progress)
						})
					}
				})
			},
			updatePendingProgress(order, progress, index, total) {
				const value = Math.max(0, Math.min(100, Number(progress) || 0))
				const item = this.pendingUploads.find(upload => Number(upload.order) === Number(order))
				if (item) this.$set(item, 'progress', value)
				if (total) this.uploadProgress = Math.max(0, Math.min(100, Math.round(((index + value / 100) / total) * 100)))
			},
			resetUploadProgress() {
				this.isUploading = false
				this.uploadProgress = 0
				this.uploadTotal = 0
				this.uploadCompleted = 0
				this.uploadCurrentLabel = ''
			},
			async processImageUploads(filePaths, orderBase) {
				const total = filePaths.length
				const failures = []
				this.isUploading = true
				this.uploadTotal = total
				this.uploadCompleted = 0
				this.uploadProgress = 0
				for (let index = 0; index < total; index++) {
					const order = orderBase + index
					this.uploadCurrentLabel = `正在上传第 ${index + 1} 张图片`
					this.updatePendingProgress(order, 0, index, total)
					try {
						const result = await this.uploadMediaFile(filePaths[index], progress => {
							this.updatePendingProgress(order, progress, index, total)
						})
						this.removePendingUploadByOrder(order)
						if (this.picList.length < 9) {
							this.$set(this.imageOrder, result.url, order)
							this.picList = this.picList.concat([result.url])
						}
					} catch (error) {
						this.removePendingUploadByOrder(order)
						failures.push({ path: filePaths[index], order, message: error && error.msg })
					}
					this.uploadCompleted = index + 1
					this.uploadProgress = Math.round(((index + 1) / total) * 100)
				}
				this.sortPicList()
				this.resetUploadProgress()
				if (failures.length) {
					this.failedUploads = this.failedUploads.concat(failures)
					uni.showToast({ title: '部分图片上传失败，可重试或删除', icon: 'none' })
				}
			},
			upload(){
				if (this.token === "") {
					uni.showToast({ title:"请先登录", icon:'none', duration: 1000 })
					setTimeout(() => uni.navigateTo({ url: '/pages/user/login' }), 1000)
					return false
				}
				const remaining = 9 - this.picList.length - this.pendingUploads.length - this.failedUploads.length
				if (remaining <= 0) return false
				uni.chooseImage({
					count: remaining,
					sizeType:['compressed'],
					sourceType: ['album', 'camera'],
					success: (res) => {
						const tempFilePaths = res.tempFilePaths || []
						if (!tempFilePaths.length) return
						if (this.type === 4) {
							this.pic = ''
							this.videoPreviewPath = ''
							this.type = 0
						}
						const orderBase = this.nextUploadOrder
						this.nextUploadOrder += tempFilePaths.length
						this.pendingUploads = this.pendingUploads.concat(tempFilePaths.map((path, index) => ({
							path,
							order: orderBase + index,
							progress: 0,
							uploading: true
						})))
						this.processImageUploads(tempFilePaths, orderBase)
					}
				})
			},
			uploadVideo(){
				if (this.token === "") {
					uni.showToast({ title:"请先登录", icon:'none', duration: 1000 })
					setTimeout(() => uni.navigateTo({ url: '/pages/user/login' }), 1000)
					return false
				}
				uni.chooseVideo({
					sourceType: ['camera', 'album'],
					compressed:true,
					success: async (response) => {
						const videoFile = response.tempFilePath
						if (response.size > 20 * 1024 * 1024) {
							uni.showToast({ title: '视频不能超过 20MB', icon: 'none' })
							return false
						}
						this.isUploading = true
						this.uploadTotal = 1
						this.uploadCompleted = 0
						this.uploadProgress = 0
						this.uploadCurrentLabel = '正在上传视频'
						try {
							const result = await this.uploadMediaFile(videoFile, progress => {
								this.uploadProgress = progress
							})
							this.type = 4
							this.picList = []
							this.imageOrder = {}
							this.failedUploads = []
							this.pendingUploads = []
							this.pic = result.url
							this.videoPreviewPath = videoFile
							this.uploadCompleted = 1
							this.uploadProgress = 100
						} catch (error) {
							uni.showToast({ title: (error && error.msg) || '视频上传失败，请重试', icon: 'none' })
						} finally {
							this.resetUploadProgress()
						}
					}
				})
			},
			picClose(item){
				this.picList = this.picList.filter(pic => pic !== item)
				this.$delete(this.imageOrder, item)
			},
			enableMediaOrder(item) {
				if (item.uploading || item.failed || this.picList.length < 2) return
				this.mediaOrderMode = true
			},
			previewMedia(item) {
				if (this.mediaOrderMode || !item || item.uploading || item.failed || !item.path) return
				uni.previewImage({ current: item.path, urls: this.picList.slice() })
			},
			movePic(path, offset) {
				const index = this.picList.indexOf(path)
				const target = index + offset
				if (index < 0 || target < 0 || target >= this.picList.length) return
				const next = this.picList.slice()
				next.splice(index, 1)
				next.splice(target, 0, path)
				this.picList = next
				this.initializeImageOrder()
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
				if (this.isUploading || !item) return
				if (!this.token) {
					uni.showToast({ title: '请先登录', icon: 'none' })
					return
				}
				this.removeFailedUpload(item)
				this.pendingUploads = this.pendingUploads.concat([{ path: item.path, order: item.order, progress: 0, uploading: true }])
				this.isUploading = true
				this.uploadTotal = 1
				this.uploadCompleted = 0
				this.uploadProgress = 0
				this.uploadCurrentLabel = '正在重试图片上传'
				this.uploadMediaFile(item.path, progress => {
					this.updatePendingProgress(item.order, progress, 0, 1)
				}).then(result => {
					this.removePendingUploadByOrder(item.order)
					this.$set(this.imageOrder, result.url, item.order)
					this.picList = this.picList.concat([result.url])
					this.sortPicList()
				}).catch(error => {
					this.removePendingUploadByOrder(item.order)
					this.failedUploads = this.failedUploads.concat([{ path: item.path, order: item.order, message: error && error.msg }])
					uni.showToast({ title: (error && error.msg) || '图片上传失败，请重试', icon: 'none' })
				}).finally(() => {
					this.uploadCompleted = 1
					this.uploadProgress = 100
					this.resetUploadProgress()
				})
			},
			removePendingUploadByOrder(order) {
				this.pendingUploads = this.pendingUploads.filter(item => Number(item.order) !== Number(order))
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

	.media-order-controls {
		position: absolute;
		z-index: 3;
		bottom: 8rpx;
		left: 8rpx;
		display: flex;
		gap: 6rpx;
	}

	.media-order-controls text {
		display: flex;
		align-items: center;
		justify-content: center;
		width: 42rpx;
		height: 42rpx;
		border-radius: 50%;
		background: rgba(27, 46, 41, .72);
		color: #fff;
		font-size: 23rpx;
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

	.component-editor { width: calc(100% - 48rpx); margin: 14rpx 24rpx 0; box-sizing: border-box; }
	.component-add { display: flex; align-items: center; min-height: 82rpx; padding: 8rpx 0; border-top: 1rpx solid #e5e9e8; box-sizing: border-box; }
	.component-add-icon { display: flex; align-items: center; justify-content: center; width: 52rpx; height: 52rpx; margin-right: 14rpx; border-radius: 8rpx; background: #edf5f2; color: #287d69; font-size: 26rpx; }
	.component-add-copy { display: flex; flex: 1; min-width: 0; align-items: baseline; gap: 12rpx; }
	.component-add-copy > text:first-child { font-size: 25rpx; font-weight: 600; color: #344043; }
	.component-add-copy > text:last-child { font-size: 21rpx; color: #929a9c; }
	.component-add-arrow { color: #a2aaab; font-size: 24rpx; }
	.poll-summary { display: flex; align-items: stretch; border: 1rpx solid #dfe6e4; border-radius: 10rpx; background: #f8faf9; overflow: hidden; }
	.poll-summary-main { display: flex; align-items: center; flex: 1; min-width: 0; padding: 18rpx; }
	.poll-summary-icon { display: flex; align-items: center; justify-content: center; flex: 0 0 auto; width: 60rpx; height: 60rpx; margin-right: 16rpx; border-radius: 8rpx; background: #e5f2ee; color: #168573; font-size: 30rpx; }
	.poll-summary-copy { flex: 1; min-width: 0; }
	.poll-summary-label { font-size: 20rpx; font-weight: 600; color: #468175; }
	.poll-summary-title { margin-top: 3rpx; color: #2d393c; font-size: 26rpx; font-weight: 650; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
	.poll-summary-meta { margin-top: 5rpx; color: #8b9496; font-size: 21rpx; }
	.poll-summary-arrow { flex: 0 0 auto; margin-left: 12rpx; color: #a1aaab; font-size: 23rpx; }
	.poll-remove { display: flex; align-items: center; justify-content: center; width: 72rpx; border-left: 1rpx solid #e3e9e7; color: #9a6969; font-size: 28rpx; }
	.poll-remove:active { background: #f6eeee; }

	.component-sheet-layer, .poll-modal-mask { position: fixed; z-index: 10010; inset: 0; display: flex; align-items: flex-end; justify-content: center; padding-top: 80rpx; background: rgba(18, 25, 27, .54); box-sizing: border-box; }
	.component-sheet, .poll-modal { width: 100%; max-width: 680px; border-radius: 20rpx 20rpx 0 0; background: #fff; box-shadow: 0 -18rpx 50rpx rgba(20, 33, 35, .14); overflow: hidden; }
	.component-sheet { padding: 0 28rpx calc(30rpx + env(safe-area-inset-bottom)); box-sizing: border-box; }
	.sheet-handle { width: 58rpx; height: 7rpx; margin: 14rpx auto 20rpx; border-radius: 999rpx; background: #d7dddd; }
	.sheet-head { display: flex; align-items: flex-start; justify-content: space-between; padding-bottom: 24rpx; }
	.sheet-title { color: #243033; font-size: 32rpx; font-weight: 700; line-height: 1.3; }
	.sheet-subtitle { margin-top: 6rpx; color: #8a9496; font-size: 23rpx; }
	.sheet-close { display: flex; align-items: center; justify-content: center; width: 64rpx; height: 64rpx; margin: -6rpx -10rpx 0 16rpx; border-radius: 50%; color: #7c8789; font-size: 30rpx; }
	.sheet-close:active { background: #f0f3f2; }
	.component-option { display: flex; align-items: center; min-height: 112rpx; padding: 16rpx; border: 1rpx solid #dfe6e4; border-radius: 10rpx; background: #f8faf9; box-sizing: border-box; }
	.component-option:active { border-color: #a8c8bf; background: #edf6f3; }
	.component-option-icon { display: flex; align-items: center; justify-content: center; width: 66rpx; height: 66rpx; margin-right: 16rpx; border-radius: 8rpx; background: #dfeee9; color: #168573; font-size: 32rpx; }
	.component-option-copy { flex: 1; min-width: 0; }
	.component-option-copy > view { color: #2d393c; font-size: 27rpx; font-weight: 650; }
	.component-option-copy > text { display: block; margin-top: 5rpx; color: #7f898b; font-size: 21rpx; line-height: 1.45; }
	.component-option-arrow { margin-left: 14rpx; color: #9ca5a6; font-size: 24rpx; }

	.poll-modal { max-height: 92vh; }
	.poll-modal > .sheet-handle { margin-bottom: 4rpx; }
	.poll-modal-head { display: grid; grid-template-columns: 90rpx minmax(0, 1fr) 90rpx; align-items: center; min-height: 96rpx; padding: 0 24rpx 10rpx; border-bottom: 1rpx solid #e6eaea; box-sizing: border-box; }
	.poll-cancel, .poll-save { font-size: 24rpx; }
	.poll-cancel { color: #6f797b; }
	.poll-save { justify-self: end; color: #168573; font-weight: 650; }
	.poll-save.is-disabled { color: #aeb6b5; }
	.poll-modal-heading { min-width: 0; text-align: center; }
	.poll-modal-heading > view { color: #283437; font-size: 28rpx; font-weight: 700; }
	.poll-modal-heading > text { display: block; margin-top: 4rpx; color: #99a1a3; font-size: 19rpx; }
	.poll-modal-body { max-height: calc(92vh - 120rpx); padding: 22rpx 24rpx calc(34rpx + env(safe-area-inset-bottom)); box-sizing: border-box; }
	.poll-section { margin-bottom: 20rpx; padding: 18rpx; border: 1rpx solid #e1e7e6; border-radius: 10rpx; background: #fafbfb; box-sizing: border-box; }
	.poll-section-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14rpx; }
	.poll-section-head > text:first-child { color: #344043; font-size: 24rpx; font-weight: 650; }
	.poll-section-head > text:last-child { color: #98a0a2; font-size: 20rpx; }
	.poll-input, .poll-intro, .poll-option-row { width: 100%; border: 1rpx solid #dfe5e4; border-radius: 7rpx; background: #fff; box-sizing: border-box; }
	.poll-input { height: 74rpx; padding: 0 16rpx; color: #2b3639; font-size: 26rpx; }
	.poll-intro { height: 104rpx; margin-top: 12rpx; padding: 14rpx 16rpx; color: #2f3a3d; font-size: 23rpx; line-height: 1.5; }
	.poll-option-row { display: flex; align-items: center; min-height: 70rpx; margin-top: 10rpx; padding: 7rpx 9rpx 7rpx 12rpx; }
	.poll-option-row:first-of-type { margin-top: 0; }
	.poll-option-row input { flex: 1; min-width: 0; height: 54rpx; padding: 0 12rpx; color: #303c3f; font-size: 24rpx; }
	.poll-option-index { display: flex; align-items: center; justify-content: center; width: 36rpx; height: 36rpx; border-radius: 50%; background: #e6f1ed; color: #287d69; font-size: 20rpx; font-weight: 650; }
	.poll-option-remove { display: flex; align-items: center; justify-content: center; width: 48rpx; height: 48rpx; color: #9a7777; }
	.poll-add-option { display: flex; align-items: center; justify-content: center; gap: 8rpx; height: 62rpx; margin-top: 10rpx; border: 1rpx dashed #b9cbc6; border-radius: 7rpx; color: #287d69; font-size: 23rpx; }
	.poll-add-option.is-disabled { border-color: #dfe4e3; color: #a3abaa; }
	.poll-rule-section { margin-bottom: 0; }
	.poll-mode-segment { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10rpx; }
	.poll-mode-segment > view { display: flex; flex-direction: column; justify-content: center; min-height: 72rpx; padding: 8rpx 14rpx; border: 1rpx solid #dce3e1; border-radius: 7rpx; background: #fff; box-sizing: border-box; }
	.poll-mode-segment > view.is-active { border-color: #78ad9f; background: #edf6f3; }
	.poll-mode-segment text { color: #354144; font-size: 24rpx; font-weight: 600; }
	.poll-mode-help, .poll-setting-help { margin-top: 3rpx; color: #8b9597; font-size: 19rpx; }
	.poll-mode-segment > view.is-active text { color: #256f60; }
	.poll-setting-row { display: flex; align-items: center; justify-content: space-between; min-height: 78rpx; margin-top: 14rpx; padding-top: 12rpx; border-top: 1rpx solid #e4e9e8; }
	.poll-setting-row > view:first-child { display: flex; flex-direction: column; }
	.poll-setting-row > view:first-child > text { color: #354144; font-size: 23rpx; font-weight: 600; }
	.poll-choice-picker { min-width: 100rpx; padding: 14rpx 4rpx 14rpx 14rpx; color: #287d69; font-size: 23rpx; text-align: right; }
	.poll-privacy-note { display: flex; align-items: flex-start; gap: 9rpx; margin: 0 4rpx; color: #8d9798; font-size: 20rpx; line-height: 1.5; }
	.poll-privacy-note .cuIcon-lock { margin-top: 2rpx; color: #5c8178; }

	.topic-editor {
		width: calc(100% - 48rpx);
		margin: 18rpx 24rpx 28rpx;
		padding-top: 22rpx;
		border-top: 1rpx solid #e5e8e9;
		box-sizing: border-box;
	}

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

	.campus-editor-page.campus-night .component-add { border-top-color: #2d3335; }
	.campus-editor-page.campus-night .component-add-icon { background: #263531; color: #78b5a4; }
	.campus-editor-page.campus-night .component-add-copy > text:first-child { color: #dfe4e5; }
	.campus-editor-page.campus-night .component-add-copy > text:last-child, .campus-editor-page.campus-night .component-add-arrow { color: #778183; }
	.campus-editor-page.campus-night .poll-summary { border-color: #303738; background: #191e1f; }
	.campus-editor-page.campus-night .poll-summary-icon { background: #263a34; color: #78b5a4; }
	.campus-editor-page.campus-night .poll-summary-label { color: #79aa9d; }
	.campus-editor-page.campus-night .poll-summary-title { color: #e0e5e6; }
	.campus-editor-page.campus-night .poll-summary-meta, .campus-editor-page.campus-night .poll-summary-arrow { color: #7f898b; }
	.campus-editor-page.campus-night .poll-remove { border-left-color: #303738; color: #a98181; }
	.campus-editor-page.campus-night .poll-remove:active { background: #2a2324; }
	.campus-editor-page.campus-night .component-sheet-layer, .campus-editor-page.campus-night .poll-modal-mask { background: rgba(0, 0, 0, .68); }
	.campus-editor-page.campus-night .component-sheet, .campus-editor-page.campus-night .poll-modal { background: #181d1e; box-shadow: 0 -18rpx 50rpx rgba(0, 0, 0, .28); }
	.campus-editor-page.campus-night .sheet-handle { background: #3a4243; }
	.campus-editor-page.campus-night .sheet-title, .campus-editor-page.campus-night .poll-modal-heading > view, .campus-editor-page.campus-night .poll-section-head > text:first-child { color: #e5e9ea; }
	.campus-editor-page.campus-night .sheet-subtitle, .campus-editor-page.campus-night .poll-modal-heading > text { color: #899395; }
	.campus-editor-page.campus-night .sheet-close, .campus-editor-page.campus-night .poll-cancel { color: #9aa3a5; }
	.campus-editor-page.campus-night .sheet-close:active { background: #252b2c; }
	.campus-editor-page.campus-night .component-option, .campus-editor-page.campus-night .poll-section { border-color: #303738; background: #1b2021; }
	.campus-editor-page.campus-night .component-option:active { border-color: #4e8073; background: #23322e; }
	.campus-editor-page.campus-night .component-option-icon { background: #2b403a; color: #78b5a4; }
	.campus-editor-page.campus-night .component-option-copy > view { color: #e0e5e6; }
	.campus-editor-page.campus-night .component-option-copy > text, .campus-editor-page.campus-night .component-option-arrow { color: #899395; }
	.campus-editor-page.campus-night .poll-modal-head { border-bottom-color: #2d3435; }
	.campus-editor-page.campus-night .poll-save.is-disabled { color: #606a6b; }
	.campus-editor-page.campus-night .poll-input, .campus-editor-page.campus-night .poll-intro, .campus-editor-page.campus-night .poll-option-row, .campus-editor-page.campus-night .poll-mode-segment > view { border-color: #343b3c; background: #141819; color: #e1e6e7; }
	.campus-editor-page.campus-night .poll-option-row input, .campus-editor-page.campus-night .poll-mode-segment text, .campus-editor-page.campus-night .poll-setting-row > view:first-child > text { color: #dce2e3; }
	.campus-editor-page.campus-night .poll-option-index { background: #293b36; color: #81b7a7; }
	.campus-editor-page.campus-night .poll-add-option { border-color: #40554f; color: #75ad9d; }
	.campus-editor-page.campus-night .poll-add-option.is-disabled { border-color: #303738; color: #657071; }
	.campus-editor-page.campus-night .poll-mode-segment > view.is-active { border-color: #4e8778; background: #24342f; }
	.campus-editor-page.campus-night .poll-mode-segment > view.is-active text { color: #93c5b6; }
	.campus-editor-page.campus-night .poll-mode-help, .campus-editor-page.campus-night .poll-setting-help, .campus-editor-page.campus-night .poll-privacy-note { color: #879193; }
	.campus-editor-page.campus-night .poll-setting-row { border-top-color: #303738; }

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

	/* Upload feedback stays in the compose surface so the user never loses context. */
	.media-image {
		animation: media-card-in 240ms cubic-bezier(.2,.8,.2,1) both;
	}

	.media-image.is-uploading {
		border-color: #83bcae;
		box-shadow: 0 0 0 2rpx rgba(35, 130, 103, .12);
	}

	.media-uploading-mask {
		position: absolute;
		inset: 0;
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		gap: 10rpx;
		background: rgba(24, 50, 44, .68);
		color: #fff;
		font-size: 24rpx;
		font-weight: 650;
	}

	.media-uploading-spinner,
	.upload-progress-spinner {
		width: 34rpx;
		height: 34rpx;
		border: 4rpx solid rgba(255, 255, 255, .35);
		border-top-color: #fff;
		border-radius: 50%;
		animation: media-upload-spin 800ms linear infinite;
	}

	.upload-progress-card {
		width: calc(100% - 48rpx);
		margin: 18rpx 24rpx 0;
		padding: 18rpx 20rpx 16rpx;
		border: 1rpx solid #d8e8e2;
		border-radius: 14rpx;
		background: #f5faf8;
		box-sizing: border-box;
		animation: upload-card-in 220ms ease both;
	}

	.upload-progress-head,
	.upload-progress-title-wrap,
	.upload-progress-foot {
		display: flex;
		align-items: center;
	}

	.upload-progress-head {
		justify-content: space-between;
		gap: 16rpx;
	}

	.upload-progress-title-wrap {
		min-width: 0;
		gap: 10rpx;
	}

	.upload-progress-icon {
		display: flex;
		align-items: center;
		justify-content: center;
		width: 42rpx;
		height: 42rpx;
		border-radius: 12rpx;
		background: #dcefe8;
	}

	.upload-progress-icon .upload-progress-spinner {
		width: 22rpx;
		height: 22rpx;
		border-width: 3rpx;
		border-color: #a8cfc0;
		border-top-color: #238267;
	}

	.upload-progress-title {
		overflow: hidden;
		color: #31594e;
		font-size: 24rpx;
		font-weight: 650;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.upload-progress-value {
		flex: 0 0 auto;
		color: #238267;
		font-size: 25rpx;
		font-weight: 700;
	}

	.upload-progress-track {
		height: 10rpx;
		margin-top: 16rpx;
		border-radius: 999rpx;
		background: #dce9e5;
		overflow: hidden;
	}

	.upload-progress-fill {
		height: 100%;
		border-radius: inherit;
		background: #238267;
		transition: width 180ms ease;
	}

	.upload-progress-foot {
		justify-content: space-between;
		gap: 14rpx;
		margin-top: 10rpx;
		color: #82928c;
		font-size: 20rpx;
	}

	.anonymous-tip {
		gap: 14rpx;
		border: 1rpx solid #cfe8df;
		background: #f1faf6;
		color: #386b5c;
		animation: upload-card-in 240ms ease both;
	}

	.anonymous-tip-icon {
		display: flex;
		align-items: center;
		justify-content: center;
		flex: 0 0 auto;
		width: 46rpx;
		height: 46rpx;
		border-radius: 14rpx;
		background: #dcefe8;
		color: #238267;
		font-size: 25rpx;
	}

	.anonymous-tip-copy {
		display: flex;
		flex: 1;
		min-width: 0;
		flex-direction: column;
		gap: 3rpx;
	}

	.anonymous-tip-title {
		font-size: 24rpx;
		font-weight: 700;
	}

	.anonymous-tip-desc {
		color: #68847a;
		font-size: 20rpx;
		line-height: 1.45;
	}

	.anonymous-tip-state {
		flex: 0 0 auto;
		padding: 6rpx 10rpx;
		border-radius: 999rpx;
		background: #dcefe8;
		color: #238267;
		font-size: 19rpx;
	}

	.campus-editor-page.campus-night .media-image.is-uploading {
		border-color: #589b87;
		box-shadow: 0 0 0 2rpx rgba(98, 168, 148, .15);
	}

	.campus-editor-page.campus-night .upload-progress-card {
		border-color: #2f4840;
		background: #1d2a26;
	}

	.campus-editor-page.campus-night .upload-progress-icon,
	.campus-editor-page.campus-night .anonymous-tip-icon,
	.campus-editor-page.campus-night .anonymous-tip-state {
		background: #29453b;
		color: #9dd2be;
	}

	.campus-editor-page.campus-night .upload-progress-title,
	.campus-editor-page.campus-night .anonymous-tip-title {
		color: #d7ebe2;
	}

	.campus-editor-page.campus-night .upload-progress-value {
		color: #9dd2be;
	}

	.campus-editor-page.campus-night .upload-progress-track {
		background: #2b4039;
	}

	.campus-editor-page.campus-night .upload-progress-fill {
		background: #63a993;
	}

	.campus-editor-page.campus-night .upload-progress-foot,
	.campus-editor-page.campus-night .anonymous-tip-desc {
		color: #8ea99f;
	}

	.campus-editor-page.campus-night .anonymous-tip {
		border-color: #315347;
		background: #1d2d28;
		color: #b9d9cb;
	}

	@keyframes media-card-in {
		from { opacity: 0; transform: scale(.96) translateY(8rpx); }
		to { opacity: 1; transform: scale(1) translateY(0); }
	}

	@keyframes upload-card-in {
		from { opacity: 0; transform: translateY(8rpx); }
		to { opacity: 1; transform: translateY(0); }
	}

	@keyframes media-upload-spin {
		to { transform: rotate(360deg); }
	}

	/* Final editor rhythm: one primary surface, then compact supporting sections. */
	.campus-editor-page {
		background: #f3f7f5;
		color: #263a35;
		animation: editor-page-in 260ms ease both;
	}

	.campus-editor-page .header {
		border-bottom-color: rgba(37, 83, 74, .1);
		background: rgba(250, 252, 251, .92);
		box-shadow: 0 4rpx 20rpx rgba(36, 75, 67, .05);
	}

	.campus-editor-page .header .cu-bar {
		min-height: 100%;
	}

	.campus-editor-page .header .content {
		font-size: 32rpx;
		font-weight: 700;
		color: #25423b;
	}

	.campus-editor-page .header .action:first-child text {
		font-size: 40rpx;
		color: #59706a;
		transition: transform 180ms ease, color 180ms ease;
	}

	.campus-editor-page .header .action:first-child:active text {
		transform: scale(.9);
		color: #238267;
	}

	.post-submit-button {
		min-width: 124rpx;
		height: 64rpx;
		padding: 0 30rpx;
		border-radius: 999rpx !important;
		background: #238267 !important;
		box-shadow: 0 8rpx 18rpx rgba(35, 130, 103, .2);
		font-size: 25rpx;
		line-height: 64rpx;
		transition: transform 180ms ease, box-shadow 220ms ease, opacity 180ms ease;
	}

	.post-submit-button:active {
		transform: scale(.95);
		box-shadow: 0 4rpx 10rpx rgba(35, 130, 103, .16);
	}

	.post-compose {
		width: calc(100% - 48rpx);
		max-width: 760px;
		padding: 14rpx 24rpx 38rpx;
		box-sizing: border-box;
	}

	.post-editor-surface {
		width: 100%;
		max-width: none;
		margin: 0;
		padding: 24rpx 24rpx 18rpx;
		border: 1rpx solid #dfeae6;
		border-radius: 20rpx;
		background: #ffffff;
		box-shadow: 0 12rpx 30rpx rgba(37, 78, 70, .07);
		box-sizing: border-box;
		animation: editor-section-in 300ms cubic-bezier(.2,.8,.2,1) both;
	}

	.post-editor-input {
		min-height: clamp(280rpx, 34vh, 520rpx) !important;
		padding: 0;
		font-size: 31rpx;
		line-height: 1.68;
		color: #263c36;
	}

	.post-editor-input::placeholder {
		color: #a1afaa;
	}

	.post-editor-status {
		margin-top: 14rpx;
		padding: 14rpx 2rpx 0;
		border-top: 1rpx solid #edf2f0;
		color: #84948d;
		font-size: 22rpx;
		line-height: 1.4;
	}

	.space-owo {
		width: 100%;
		min-height: 64rpx;
		margin: 14rpx 0 0;
		padding: 0 12rpx;
		border: 1rpx solid #dfeae6;
		border-radius: 16rpx;
		background: #fbfdfc;
		box-shadow: 0 8rpx 18rpx rgba(37, 78, 70, .04);
		box-sizing: border-box;
		animation: editor-section-in 300ms 30ms cubic-bezier(.2,.8,.2,1) both;
	}

	.space-owo .cuIcon-emoji {
		width: 64rpx;
		height: 64rpx;
		font-size: 35rpx;
		color: #71837c;
		transition: color 180ms ease, transform 180ms ease;
	}

	.space-owo .cuIcon-emoji.is-active {
		color: #238267;
	}

	.space-owo .cuIcon-emoji:active {
		transform: scale(.88);
	}

	.space-owo .owo {
		border-top-color: #e3ece8;
		border-radius: 12rpx;
		background: #f6faf8;
		animation: editor-section-in 220ms ease both;
	}

	.media-section {
		width: 100%;
		margin: 22rpx 0 0;
		padding: 18rpx;
		border: 1rpx solid #dfeae6;
		border-radius: 20rpx;
		background: #ffffff;
		box-shadow: 0 10rpx 24rpx rgba(37, 78, 70, .05);
		box-sizing: border-box;
		animation: editor-section-in 300ms 70ms cubic-bezier(.2,.8,.2,1) both;
	}

	.media-section-heading {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 16rpx;
		margin: 0 4rpx 16rpx;
	}

	.media-section-heading-main {
		display: flex;
		align-items: baseline;
		min-width: 0;
		gap: 12rpx;
	}

	.media-section-title {
		color: #304b43;
		font-size: 26rpx;
		font-weight: 700;
	}

	.media-section-subtitle {
		overflow: hidden;
		color: #91a09a;
		font-size: 20rpx;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.media-section-count {
		flex: 0 0 auto;
		color: #7d9188;
		font-size: 21rpx;
		font-variant-numeric: tabular-nums;
	}

	.media-grid {
		gap: 12rpx;
	}

	.media-image,
	.media-video,
	.media-upload {
		border-radius: 16rpx;
	}

	.media-image,
	.media-video {
		border-color: #e0eae6;
	}

	.media-upload {
		border: 1rpx dashed #b8d5ca;
		background: #f2f9f6 !important;
		color: #238267;
		transition: transform 180ms ease, background-color 180ms ease, border-color 180ms ease;
	}

	.media-upload:active {
		transform: scale(.96);
		border-color: #238267;
		background: #e8f4ef !important;
	}

	.media-upload > text:first-child {
		font-size: 50rpx !important;
		font-weight: 300;
	}

	.media-remove {
		width: 46rpx;
		height: 46rpx;
		top: 10rpx;
		right: 10rpx;
		background: rgba(27, 46, 41, .72);
		transition: transform 160ms ease, background-color 160ms ease;
	}

	.media-remove:active {
		transform: scale(.88);
		background: rgba(27, 46, 41, .9);
	}

	.component-editor,
	.topic-editor {
		width: 100%;
		margin: 18rpx 0 0;
		box-sizing: border-box;
		animation: editor-section-in 300ms 100ms cubic-bezier(.2,.8,.2,1) both;
	}

	.component-add {
		min-height: 76rpx;
		padding: 10rpx 6rpx;
		border: 1rpx solid #dfeae6;
		border-radius: 16rpx;
		background: #fbfdfc;
		transition: transform 180ms ease, background-color 180ms ease, border-color 180ms ease;
	}

	.component-add:active {
		transform: scale(.985);
		border-color: #acd0c1;
		background: #f1faf6;
	}

	.topic-editor {
		padding: 18rpx 0 0;
		border-top: 1rpx solid #e4ece9;
	}

	.anonymous-tip,
	.upload-progress-card,
	.poll-summary {
		border-radius: 16rpx;
	}

	.component-sheet-layer,
	.poll-modal-mask {
		animation: editor-mask-in 220ms ease both;
	}

	.component-sheet,
	.poll-modal {
		animation: editor-sheet-up 320ms cubic-bezier(.2,.8,.2,1) both;
	}

	.campus-editor-page.campus-night {
		background: #111a17;
		color: #e5eeea;
	}

	.campus-editor-page.campus-night .header {
		border-bottom-color: #293b35;
		background: rgba(20, 32, 28, .94);
		box-shadow: 0 4rpx 20rpx rgba(0, 0, 0, .18);
	}

	.campus-editor-page.campus-night .header .content {
		color: #e3eee9;
	}

	.campus-editor-page.campus-night .post-editor-surface,
	.campus-editor-page.campus-night .media-section {
		border-color: #2d4039;
		background: #18231f;
		box-shadow: 0 10rpx 24rpx rgba(0, 0, 0, .16);
	}

	.campus-editor-page.campus-night .post-editor-input {
		color: #e5eeea;
	}

	.campus-editor-page.campus-night .post-editor-input::placeholder {
		color: #71847b;
	}

	.campus-editor-page.campus-night .post-editor-status,
	.campus-editor-page.campus-night .topic-editor {
		border-top-color: #2b3c36;
	}

	.campus-editor-page.campus-night .space-owo,
	.campus-editor-page.campus-night .component-add {
		border-color: #2d4039;
		background: #1a2823;
	}

	.campus-editor-page.campus-night .media-section-title {
		color: #d1e2da;
	}

	.campus-editor-page.campus-night .media-section-subtitle,
	.campus-editor-page.campus-night .media-section-count {
		color: #879d92;
	}

	.campus-editor-page.campus-night .media-image,
	.campus-editor-page.campus-night .media-video {
		border-color: #30423c;
	}

	.campus-editor-page.campus-night .media-upload {
		border-color: #477967;
		background: #1d322a !important;
		color: #9bd0bb;
	}

	@keyframes editor-page-in {
		from { opacity: 0; }
		to { opacity: 1; }
	}

	@keyframes editor-section-in {
		from { opacity: 0; transform: translateY(10rpx); }
		to { opacity: 1; transform: translateY(0); }
	}

	@keyframes editor-mask-in {
		from { opacity: 0; }
		to { opacity: 1; }
	}

	@keyframes editor-sheet-up {
		from { opacity: .7; transform: translateY(36rpx); }
		to { opacity: 1; transform: translateY(0); }
	}

	@media (prefers-reduced-motion: reduce) {
		.campus-editor-page,
		.campus-editor-page * {
			animation-duration: .01ms !important;
			animation-iteration-count: 1 !important;
			transition-duration: .01ms !important;
		}
	}

	/* Keep the editor open and editorial: dividers and rhythm instead of stacked cards. */
	.campus-editor-page {
		background: #f7f9f8;
	}

	.campus-editor-page .header {
		background: rgba(247, 249, 248, .96);
		box-shadow: none;
	}

	.post-submit-button {
		min-width: 112rpx;
		height: 58rpx;
		padding: 0 24rpx;
		border-radius: 12rpx !important;
		box-shadow: none;
		font-size: 25rpx;
		line-height: 58rpx;
	}

	.post-submit-button:active {
		box-shadow: none;
	}

	.post-compose {
		padding: 0 24rpx 36rpx;
	}

	.post-editor-surface {
		margin: 0;
		padding: 26rpx 0 16rpx;
		border: 0;
		border-radius: 0;
		background: transparent;
		box-shadow: none;
		animation: editor-section-in 260ms ease both;
	}

	.post-editor-input {
		min-height: clamp(300rpx, 36vh, 540rpx) !important;
		font-size: 31rpx;
		line-height: 1.72;
	}

	.post-editor-status {
		margin-top: 16rpx;
		padding: 12rpx 0 0;
		border-top-color: #e5ece9;
	}

	.space-owo {
		min-height: 62rpx;
		margin: 0;
		padding: 0;
		border-right: 0;
		border-left: 0;
		border-radius: 0;
		background: transparent;
		box-shadow: none;
		animation: editor-section-in 240ms 20ms ease both;
	}

	.space-owo .owo {
		border-radius: 0;
		background: #f2f6f4;
		box-shadow: none;
	}

	.media-section {
		margin: 0;
		padding: 18rpx 0 0;
		border: 0;
		border-top: 1rpx solid #e5ece9;
		border-radius: 0;
		background: transparent;
		box-shadow: none;
		animation: editor-section-in 260ms 40ms ease both;
	}

	.media-section-heading {
		margin: 0 0 14rpx;
	}

	.media-section-title {
		color: #3d514b;
		font-size: 24rpx;
		font-weight: 600;
	}

	.media-section-count {
		color: #8b9993;
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

	.media-image,
	.media-video {
		border-color: #e2e9e6;
		box-shadow: none;
		animation: editor-section-in 220ms ease both;
	}

	.media-upload {
		border-color: #c4d9d1;
		background: #f2f6f4 !important;
		box-shadow: none;
	}

	.media-upload > text:first-child {
		font-size: 46rpx !important;
	}

	.media-remove {
		width: 42rpx;
		height: 42rpx;
		background: rgba(29, 43, 39, .66);
	}

	.anonymous-tip {
		margin: 14rpx 0;
		padding: 10rpx 0 10rpx 14rpx;
		border: 0;
		border-left: 4rpx solid #7bb9a5;
		border-radius: 0;
		background: transparent;
		color: #477568;
		box-shadow: none;
	}

	.anonymous-tip-icon {
		width: auto;
		height: auto;
		border-radius: 0;
		background: transparent;
		font-size: 24rpx;
	}

	.anonymous-tip-copy {
		gap: 2rpx;
	}

	.anonymous-tip-title {
		font-size: 23rpx;
		font-weight: 600;
	}

	.anonymous-tip-desc {
		font-size: 20rpx;
	}

	.anonymous-tip-state {
		padding: 0;
		border-radius: 0;
		background: transparent;
		font-size: 19rpx;
	}

	.upload-progress-card {
		width: 100%;
		margin: 14rpx 0 0;
		padding: 14rpx 0;
		border: 0;
		border-top: 1rpx solid #e5ece9;
		border-bottom: 1rpx solid #e5ece9;
		border-radius: 0;
		background: transparent;
		box-shadow: none;
	}

	.upload-progress-icon {
		width: 36rpx;
		height: 36rpx;
		border-radius: 50%;
		background: transparent;
	}

	.upload-progress-title {
		color: #526760;
		font-size: 23rpx;
		font-weight: 500;
	}

	.upload-progress-value {
		color: #36866f;
		font-size: 23rpx;
	}

	.upload-progress-track {
		height: 6rpx;
		margin-top: 12rpx;
		background: #e2ebe7;
	}

	.upload-progress-fill {
		background: #3b967a;
	}

	.upload-progress-foot {
		margin-top: 8rpx;
		font-size: 19rpx;
	}

	.component-editor,
	.topic-editor {
		margin-top: 0;
		animation: editor-section-in 260ms 70ms ease both;
	}

	.component-add {
		min-height: 70rpx;
		padding: 12rpx 0;
		border: 0;
		border-top: 1rpx solid #e5ece9;
		border-bottom: 1rpx solid #e5ece9;
		border-radius: 0;
		background: transparent;
	}

	.component-add:active {
		transform: translateX(2rpx);
		border-color: #c8ddd4;
		background: transparent;
	}

	.poll-summary {
		border: 1rpx solid #dfe8e4;
		border-radius: 10rpx;
		background: transparent;
		box-shadow: none;
	}

	.topic-editor {
		padding-top: 16rpx;
		border-top-color: #e5ece9;
	}

	.component-sheet-layer,
	.poll-modal-mask {
		background: rgba(22, 34, 31, .42);
	}

	.campus-editor-page.campus-night {
		background: #131b18;
	}

	.campus-editor-page.campus-night .header {
		background: rgba(19, 27, 24, .96);
		box-shadow: none;
	}

	.campus-editor-page.campus-night .post-editor-surface,
	.campus-editor-page.campus-night .media-section {
		border-color: #2c3b35;
		background: transparent;
		box-shadow: none;
	}

	.campus-editor-page.campus-night .post-editor-status,
	.campus-editor-page.campus-night .media-section,
	.campus-editor-page.campus-night .upload-progress-card,
	.campus-editor-page.campus-night .component-add,
	.campus-editor-page.campus-night .topic-editor {
		border-color: #2c3b35;
	}

	.campus-editor-page.campus-night .space-owo .owo {
		background: #1a2521;
	}

	.campus-editor-page.campus-night .media-upload {
		background: #192520 !important;
	}

	.campus-editor-page.campus-night .anonymous-tip {
		background: transparent;
		border-left-color: #5d9e88;
	}

	@keyframes editor-section-in {
		from { opacity: 0; transform: translateY(6rpx); }
		to { opacity: 1; transform: translateY(0); }
	}

	/* Publish flow transitions: every optional block enters in the same rhythm. */
	.media-order-banner {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 12rpx;
		margin: 12rpx 0 10rpx;
		padding: 12rpx 14rpx;
		border: 1rpx solid #cce5dc;
		border-radius: 12rpx;
		background: #eef8f4;
		animation: publish-expand-in .22s cubic-bezier(.22,.78,.25,1) both;
	}
	.media-order-banner-copy { display:flex; align-items:center; min-width:0; gap:8rpx; color:#287d69; font-size:22rpx; }
	.media-order-banner-copy > text:first-child { font-size:25rpx; }
	.media-order-banner-hint { overflow:hidden; color:#78968e; font-size:19rpx; text-overflow:ellipsis; white-space:nowrap; }
	.media-order-done { flex:0 0 auto; padding:6rpx 12rpx; border-radius:999rpx; background:#d9eee6; color:#287d69; font-size:21rpx; transition:transform .16s ease; }
	.media-order-done:active { transform:scale(.95); }
	.media-order-index { position:absolute; left:10rpx; top:10rpx; z-index:4; display:flex; align-items:center; justify-content:center; width:42rpx; height:42rpx; border-radius:50%; background:rgba(19,40,35,.78); color:#fff; font-size:20rpx; font-weight:700; }
	.media-image,.media-video,.media-upload { transition:transform .18s ease, box-shadow .18s ease, opacity .18s ease; }
	.media-image:active,.media-upload:active { transform:scale(.97); }
	.media-order-controls { animation: publish-expand-in .18s ease both; }
	.media-image:nth-child(2) { animation-delay: .025s; }
	.media-image:nth-child(3) { animation-delay: .05s; }
	.media-image:nth-child(4) { animation-delay: .075s; }
	.component-sheet-layer, .poll-modal-mask { animation: publish-mask-in .22s ease both; }
	.component-sheet, .poll-modal { animation: publish-sheet-up .3s cubic-bezier(.19,1,.22,1) both; }
	.topic-picker { overflow:hidden; animation: publish-expand-in .24s cubic-bezier(.22,.78,.25,1) both; }
	.upload-progress-card { animation: publish-expand-in .24s cubic-bezier(.22,.78,.25,1) both; }
	.post-submit-button { transition:transform .18s ease, box-shadow .22s ease, filter .22s ease, opacity .2s ease; }
	.post-submit-button[disabled] { opacity:.48; box-shadow:none; filter:grayscale(.2); }

	@keyframes publish-expand-in { from { opacity:0; transform:translateY(-8rpx); } to { opacity:1; transform:translateY(0); } }
	@keyframes publish-mask-in { from { opacity:0; } to { opacity:1; } }
	@keyframes publish-sheet-up { from { opacity:0; transform:translateY(100%); } to { opacity:1; transform:translateY(0); } }
</style>
