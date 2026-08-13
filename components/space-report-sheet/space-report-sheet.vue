<template>
	<view v-if="visible" class="report-sheet-layer" :class="{'is-night': night}" @tap="close">
		<view class="report-sheet" @tap.stop>
			<view class="report-sheet-handle"></view>
			<view class="report-sheet-head">
				<view>
					<view class="report-sheet-title">举报动态</view>
					<view class="report-sheet-subtitle">请选择最符合的原因</view>
				</view>
				<view class="report-sheet-close cuIcon-close" @tap="close"></view>
			</view>
			<view class="report-reasons">
				<view class="report-reason" :class="{'is-selected': selectedReason === item.value}"
					v-for="item in reasons" :key="item.value" @tap="selectedReason = item.value">
					<view class="report-reason-icon" :class="item.icon"></view>
					<view class="report-reason-copy">
						<view class="report-reason-title">{{item.value}}</view>
						<view class="report-reason-desc">{{item.description}}</view>
					</view>
					<view class="report-reason-check"><text v-if="selectedReason === item.value" class="cuIcon-check"></text></view>
				</view>
			</view>
			<view class="report-sheet-note"><text class="cuIcon-info"></text><text>平台会保护举报人的身份，并依据社区规范核实处理。</text></view>
			<button class="report-submit" :disabled="!selectedReason || submitting" @tap="submit">
				{{submitting ? '正在提交' : '提交举报'}}
			</button>
		</view>
	</view>
</template>

<script>
	export default {
		name: 'space-report-sheet',
		props: {
			visible: { type: Boolean, default: false },
			night: { type: Boolean, default: false },
			submitting: { type: Boolean, default: false }
		},
		data() {
			return {
				selectedReason: '',
				reasons: [
					{ value: '广告营销', description: '引流、刷屏或未经允许的商业推广', icon: 'cuIcon-shop' },
					{ value: '人身攻击', description: '辱骂、骚扰或煽动群体对立', icon: 'cuIcon-comment' },
					{ value: '色情低俗', description: '色情、低俗或令人不适的内容', icon: 'cuIcon-attentionforbid' },
					{ value: '违法违规', description: '违法信息、危险行为或暴露学生隐私', icon: 'cuIcon-warn' },
					{ value: '其他', description: '其他违反社区规范的情况', icon: 'cuIcon-moreandroid' }
				]
			}
		},
		watch: {
			visible(value) {
				if (value) this.selectedReason = ''
			}
		},
		methods: {
			close() {
				if (!this.submitting) this.$emit('close')
			},
			submit() {
				if (this.selectedReason && !this.submitting) this.$emit('submit', this.selectedReason)
			}
		}
	}
</script>

<style scoped>
	.report-sheet-layer { position: fixed; z-index: 10020; inset: 0; display: flex; align-items: flex-end; justify-content: center; padding-top: 80rpx; background: rgba(18, 25, 27, .54); box-sizing: border-box; }
	.report-sheet { width: 100%; max-width: 680px; max-height: 90vh; padding: 0 28rpx calc(28rpx + env(safe-area-inset-bottom)); border-radius: 20rpx 20rpx 0 0; background: #fff; box-shadow: 0 -18rpx 50rpx rgba(20, 33, 35, .14); overflow-y: auto; box-sizing: border-box; }
	.report-sheet-handle { width: 58rpx; height: 7rpx; margin: 14rpx auto 20rpx; border-radius: 999rpx; background: #d7dddd; }
	.report-sheet-head { display: flex; align-items: flex-start; justify-content: space-between; padding-bottom: 22rpx; }
	.report-sheet-title { font-size: 32rpx; font-weight: 700; line-height: 1.3; color: #243033; }
	.report-sheet-subtitle { margin-top: 6rpx; font-size: 23rpx; color: #8a9496; }
	.report-sheet-close { display: flex; align-items: center; justify-content: center; width: 64rpx; height: 64rpx; margin: -6rpx -10rpx 0 16rpx; border-radius: 50%; color: #7c8789; font-size: 30rpx; }
	.report-sheet-close:active { background: #f0f3f2; }
	.report-reasons { border: 1rpx solid #e1e7e6; border-radius: 12rpx; overflow: hidden; }
	.report-reason { display: flex; align-items: center; min-height: 96rpx; padding: 14rpx 16rpx; border-bottom: 1rpx solid #e9edec; background: #fff; box-sizing: border-box; transition: background-color .16s ease; }
	.report-reason:last-child { border-bottom: 0; }
	.report-reason.is-selected { background: #edf6f3; }
	.report-reason-icon { display: flex; align-items: center; justify-content: center; flex: 0 0 auto; width: 56rpx; height: 56rpx; margin-right: 16rpx; border-radius: 8rpx; background: #f0f4f3; color: #52716a; font-size: 28rpx; }
	.report-reason.is-selected .report-reason-icon { background: #dceee8; color: #168573; }
	.report-reason-copy { flex: 1; min-width: 0; }
	.report-reason-title { font-size: 26rpx; font-weight: 600; color: #2c383b; }
	.report-reason-desc { margin-top: 5rpx; font-size: 21rpx; line-height: 1.45; color: #8b9597; }
	.report-reason-check { display: flex; align-items: center; justify-content: center; flex: 0 0 auto; width: 36rpx; height: 36rpx; margin-left: 12rpx; border: 2rpx solid #cad2d1; border-radius: 50%; color: #fff; font-size: 22rpx; box-sizing: border-box; }
	.report-reason.is-selected .report-reason-check { border-color: #168573; background: #168573; }
	.report-sheet-note { display: flex; align-items: flex-start; gap: 10rpx; margin-top: 18rpx; font-size: 21rpx; line-height: 1.5; color: #8c9597; }
	.report-sheet-note .cuIcon-info { margin-top: 2rpx; color: #5d8279; }
	.report-submit { height: 78rpx; margin: 24rpx 0 0; border: 0; border-radius: 8rpx; background: #168573; color: #fff; font-size: 27rpx; font-weight: 600; line-height: 78rpx; }
	.report-submit::after { border: 0; }
	.report-submit[disabled] { background: #dce4e2; color: #96a09e; opacity: 1; }
	.report-sheet-layer.is-night { background: rgba(0, 0, 0, .68); }
	.is-night .report-sheet { background: #181d1e; box-shadow: 0 -18rpx 50rpx rgba(0, 0, 0, .28); }
	.is-night .report-sheet-handle { background: #3a4243; }
	.is-night .report-sheet-title { color: #e6eaeb; }
	.is-night .report-sheet-subtitle, .is-night .report-reason-desc, .is-night .report-sheet-note { color: #899395; }
	.is-night .report-sheet-close { color: #9aa3a5; }
	.is-night .report-sheet-close:active { background: #252b2c; }
	.is-night .report-reasons { border-color: #303738; }
	.is-night .report-reason { border-bottom-color: #2b3233; background: #1b2021; }
	.is-night .report-reason.is-selected { background: #23322e; }
	.is-night .report-reason-icon { background: #252c2c; color: #93a09d; }
	.is-night .report-reason.is-selected .report-reason-icon { background: #2c433c; color: #7ab5a4; }
	.is-night .report-reason-title { color: #dfe4e5; }
	.is-night .report-reason-check { border-color: #4b5555; }
	.is-night .report-reason.is-selected .report-reason-check { border-color: #4d9a85; background: #4d9a85; }
	.is-night .report-submit[disabled] { background: #2a3131; color: #697374; }
</style>
