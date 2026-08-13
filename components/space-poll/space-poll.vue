<template>
	<view class="space-poll" :class="{'is-night': night, 'is-compact': compact, 'has-voted': value.voted}" @tap.stop>
		<view class="space-poll-head">
			<view class="space-poll-badge"><text class="cuIcon-rank"></text><text>投票</text></view>
			<text class="space-poll-rule">{{ruleText}}</text>
		</view>
		<view class="space-poll-title">{{value.title}}</view>
		<view class="space-poll-description" v-if="value.description">{{value.description}}</view>

		<view class="poll-results" v-if="value.voted">
			<view class="poll-result" :class="{'is-selected': option.selected}" v-for="option in value.options" :key="option.id">
				<view class="poll-result-track"><view class="poll-result-fill" :style="{width: percent(option.votes) + '%'}"></view></view>
				<view class="poll-result-content">
					<view class="poll-result-name"><text class="cuIcon-check" v-if="option.selected"></text><text>{{option.text}}</text></view>
					<text class="poll-result-percent">{{percent(option.votes)}}%</text>
				</view>
				<view class="poll-result-count">{{Number(option.votes || 0)}} 票</view>
			</view>
		</view>

		<view class="poll-choices" v-else>
			<view class="poll-choice" :class="{'is-selected': isChosen(option.id)}" v-for="option in value.options"
				:key="option.id" @tap="toggle(option.id)">
				<view class="poll-choice-mark" :class="{'is-multiple': isMultiple}"><text v-if="isChosen(option.id)" class="cuIcon-check"></text></view>
				<text class="poll-choice-text">{{option.text}}</text>
			</view>
			<button class="poll-submit" :disabled="submitting || !chosen.length" @tap="submit">
				<text v-if="!submitting">确认投票</text><text v-else>正在提交</text>
			</button>
		</view>

		<view class="space-poll-footer">
			<text>{{Number(value.totalVotes || 0)}} 人参与</text>
			<text>匿名投票</text>
		</view>
	</view>
</template>

<script>
	import { localStorage } from '../../js_sdk/mp-storage/mp-storage/index.js'
	export default {
		name: 'space-poll',
		props: {
			poll: { type: Object, required: true },
			night: { type: Boolean, default: false },
			compact: { type: Boolean, default: false }
		},
		data() { return { value: this.poll, chosen: [], submitting: false } },
		computed: {
			isMultiple() { return Number(this.value.multiple) === 1 },
			ruleText() { return this.isMultiple ? '多选 · 最多 ' + this.value.maxChoices + ' 项' : '单选' }
		},
		watch: { poll: { deep: true, handler(value) { this.value = value } } },
		methods: {
			percent(votes) {
				const total = Number(this.value.totalVotes) || 0
				return total ? Math.round(Number(votes || 0) * 100 / total) : 0
			},
			isChosen(id) { return this.chosen.indexOf(String(id)) !== -1 },
			toggle(id) {
				const key = String(id)
				const index = this.chosen.indexOf(key)
				if (index !== -1) { this.chosen.splice(index, 1); return }
				if (!this.isMultiple) { this.chosen = [key]; return }
				if (this.chosen.length >= Number(this.value.maxChoices)) {
					uni.showToast({ title: '最多选择 ' + this.value.maxChoices + ' 项', icon: 'none' })
					return
				}
				this.chosen.push(key)
			},
			submit() {
				let token = ''
				try { token = localStorage.getItem('token') || '' } catch (e) {}
				if (!token) { uni.showToast({ title: '请先登录', icon: 'none' }); return }
				if (!this.chosen.length || this.submitting) return
				this.submitting = true
				this.$Net.request({
					url: this.$API.pollVote(), method: 'post',
					header: { 'Content-Type': 'application/x-www-form-urlencoded' },
					data: { token, pollId: this.value.id, optionIds: this.chosen.join(',') },
					success: (res) => {
						this.submitting = false
						if (res.data.code == 1) {
							this.value = res.data.data
							this.$emit('change', res.data.data)
							uni.showToast({ title: '投票成功', icon: 'success' })
						} else uni.showToast({ title: res.data.msg || '投票失败', icon: 'none' })
					},
					fail: () => { this.submitting = false; uni.showToast({ title: '网络不太好', icon: 'none' }) }
				})
			}
		}
	}
</script>

<style scoped>
	.space-poll { margin: 20rpx 30rpx 24rpx; padding: 22rpx; border: 1rpx solid #dfe7e5; border-radius: 10rpx; background: #f8faf9; color: #283437; box-sizing: border-box; }
	.space-poll-head, .space-poll-footer, .poll-result-content { display: flex; align-items: center; justify-content: space-between; }
	.space-poll-badge { display: inline-flex; align-items: center; gap: 7rpx; color: #287d69; font-size: 22rpx; font-weight: 600; }
	.space-poll-badge .cuIcon-rank { font-size: 25rpx; }
	.space-poll-rule { color: #879193; font-size: 21rpx; }
	.space-poll-title { margin-top: 15rpx; font-size: 29rpx; font-weight: 700; line-height: 1.45; color: #283437; word-break: break-word; }
	.space-poll-description { margin-top: 7rpx; color: #697477; font-size: 23rpx; line-height: 1.55; word-break: break-word; }
	.poll-choices, .poll-results { margin-top: 18rpx; }
	.poll-choice { display: flex; align-items: center; min-height: 72rpx; margin-top: 12rpx; padding: 10rpx 16rpx; border: 1rpx solid #dce3e1; border-radius: 8rpx; background: #fff; box-sizing: border-box; transition: border-color .16s ease, background-color .16s ease; }
	.poll-choice:first-child { margin-top: 0; }
	.poll-choice:active { background: #f1f6f4; }
	.poll-choice.is-selected { border-color: #76ad9e; background: #edf6f3; }
	.poll-choice-mark { display: flex; align-items: center; justify-content: center; flex: 0 0 auto; width: 32rpx; height: 32rpx; margin-right: 14rpx; border: 2rpx solid #aeb9b7; border-radius: 50%; color: #fff; font-size: 20rpx; box-sizing: border-box; }
	.poll-choice-mark.is-multiple { border-radius: 6rpx; }
	.poll-choice.is-selected .poll-choice-mark { border-color: #168573; background: #168573; }
	.poll-choice-text { min-width: 0; font-size: 25rpx; line-height: 1.45; color: #334043; word-break: break-word; }
	.poll-submit { height: 72rpx; margin: 18rpx 0 0; border: 0; border-radius: 8rpx; background: #168573; color: #fff; font-size: 25rpx; font-weight: 600; line-height: 72rpx; }
	.poll-submit::after { border: 0; }
	.poll-submit[disabled] { background: #dce4e2; color: #95a09e; opacity: 1; }
	.poll-result { position: relative; min-height: 82rpx; margin-top: 12rpx; padding: 13rpx 14rpx 10rpx; border: 1rpx solid #e0e6e5; border-radius: 8rpx; overflow: hidden; box-sizing: border-box; }
	.poll-result:first-child { margin-top: 0; }
	.poll-result-track { position: absolute; z-index: 0; inset: 0; background: #f0f3f2; }
	.poll-result-fill { height: 100%; min-width: 0; background: #dce9e5; transition: width .3s ease; }
	.poll-result.is-selected { border-color: #8bb7aa; }
	.poll-result.is-selected .poll-result-fill { background: #cbe2db; }
	.poll-result-content, .poll-result-count { position: relative; z-index: 1; }
	.poll-result-name { display: flex; align-items: center; min-width: 0; gap: 7rpx; font-size: 24rpx; font-weight: 600; color: #344144; }
	.poll-result-name .cuIcon-check { color: #168573; }
	.poll-result-percent { flex: 0 0 auto; margin-left: 16rpx; font-size: 24rpx; font-weight: 700; color: #256f60; }
	.poll-result-count { margin-top: 5rpx; font-size: 20rpx; color: #7d888a; }
	.space-poll-footer { margin-top: 16rpx; padding-top: 14rpx; border-top: 1rpx solid #e4e9e8; color: #889294; font-size: 21rpx; }
	.space-poll.is-compact { margin: 12rpx 16rpx 16rpx; padding: 14rpx; }
	.space-poll.is-compact .space-poll-head { align-items: flex-start; gap: 6rpx; }
	.space-poll.is-compact .space-poll-rule { text-align: right; }
	.space-poll.is-compact .space-poll-title { margin-top: 10rpx; font-size: 23rpx; }
	.space-poll.is-compact .space-poll-description { display: none; }
	.space-poll.is-compact .poll-choices, .space-poll.is-compact .poll-results { margin-top: 12rpx; }
	.space-poll.is-compact .poll-choice { min-height: 58rpx; margin-top: 8rpx; padding: 7rpx 10rpx; }
	.space-poll.is-compact .poll-choice-mark { width: 27rpx; height: 27rpx; margin-right: 9rpx; }
	.space-poll.is-compact .poll-choice-text { font-size: 21rpx; }
	.space-poll.is-compact .poll-submit { height: 60rpx; margin-top: 12rpx; font-size: 22rpx; line-height: 60rpx; }
	.space-poll.is-compact .poll-result { min-height: 70rpx; padding: 10rpx; }
	.space-poll.is-compact .poll-result-name, .space-poll.is-compact .poll-result-percent { font-size: 20rpx; }
	.space-poll.is-compact .space-poll-footer { margin-top: 12rpx; padding-top: 10rpx; font-size: 18rpx; }
	.space-poll.is-night { border-color: #303839; background: #1a1f20; color: #e2e7e8; }
	.is-night .space-poll-badge { color: #75b29f; }
	.is-night .space-poll-rule, .is-night .space-poll-footer, .is-night .poll-result-count { color: #899395; }
	.is-night .space-poll-title { color: #e1e6e7; }
	.is-night .space-poll-description { color: #9aa3a5; }
	.is-night .poll-choice { border-color: #343c3d; background: #15191a; }
	.is-night .poll-choice:active { background: #202627; }
	.is-night .poll-choice.is-selected { border-color: #4f8c7b; background: #24342f; }
	.is-night .poll-choice-mark { border-color: #586364; }
	.is-night .poll-choice-text, .is-night .poll-result-name { color: #dce2e3; }
	.is-night .poll-submit[disabled] { background: #2a3131; color: #697374; }
	.is-night .poll-result { border-color: #343b3c; }
	.is-night .poll-result-track { background: #242a2b; }
	.is-night .poll-result-fill { background: #34443f; }
	.is-night .poll-result.is-selected { border-color: #4e8778; }
	.is-night .poll-result.is-selected .poll-result-fill { background: #355a50; }
	.is-night .poll-result-percent { color: #8ac0b0; }
	.is-night .space-poll-footer { border-top-color: #303637; }
</style>
