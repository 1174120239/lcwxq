<template>
	<view class="space-poll" :class="{'is-night':night}" @tap.stop>
		<view class="space-poll-title">{{value.title}}</view>
		<view class="space-poll-description" v-if="value.description">{{value.description}}</view>
		<view class="space-poll-hint">{{value.multiple==1 ? '多选，最多选择 '+value.maxChoices+' 项' : '单选'}} · 匿名投票</view>
		<view v-if="value.voted">
			<view class="poll-result" v-for="option in value.options" :key="option.id">
				<view class="poll-result-track"><view class="poll-result-fill" :class="{'is-selected':option.selected}" :style="{width:percent(option.votes)+'%'}"></view></view>
				<view class="poll-result-label"><text>{{option.text}}<text v-if="option.selected"> · 已选</text></text><text>{{percent(option.votes)}}%</text></view>
			</view>
		</view>
		<view v-else>
			<view class="poll-choice" :class="{'is-selected':chosen.indexOf(String(option.id))!==-1}" v-for="option in value.options" :key="option.id" @tap="toggle(option.id)">
				<text class="poll-choice-mark">{{chosen.indexOf(String(option.id))!==-1 ? '✓' : ''}}</text><text>{{option.text}}</text>
			</view>
			<button class="poll-submit" :disabled="submitting || !chosen.length" @tap="submit">{{submitting ? '提交中' : '提交投票'}}</button>
		</view>
		<view class="space-poll-total">{{value.totalVotes || 0}} 人参与</view>
	</view>
</template>
<script>
	import { localStorage } from '../../js_sdk/mp-storage/mp-storage/index.js'
	export default {
		name:'space-poll', props:{ poll:{type:Object,required:true}, night:{type:Boolean,default:false} },
		data(){ return { value:this.poll, chosen:[], submitting:false } },
		watch:{ poll:{deep:true,handler(value){this.value=value}} },
		methods:{
			percent(votes){ const total=Number(this.value.totalVotes)||0; return total ? Math.round(Number(votes||0)*100/total) : 0 },
			toggle(id){
				const key=String(id), index=this.chosen.indexOf(key)
				if(index!==-1){ this.chosen.splice(index,1); return }
				if(Number(this.value.multiple)!==1){ this.chosen=[key]; return }
				if(this.chosen.length>=Number(this.value.maxChoices)){ uni.showToast({title:'最多选择 '+this.value.maxChoices+' 项',icon:'none'}); return }
				this.chosen.push(key)
			},
			submit(){
				let token=''; try{ token=localStorage.getItem('token') || '' }catch(e){}
				if(!token){ uni.showToast({title:'请先登录',icon:'none'}); return }
				if(!this.chosen.length || this.submitting) return
				this.submitting=true
				this.$Net.request({url:this.$API.pollVote(),method:'post',header:{'Content-Type':'application/x-www-form-urlencoded'},data:{token,pollId:this.value.id,optionIds:this.chosen.join(',')},success:(res)=>{
					this.submitting=false
					if(res.data.code==1){ this.value=res.data.data; this.$emit('change',res.data.data); uni.showToast({title:'投票成功',icon:'none'}) }
					else uni.showToast({title:res.data.msg||'投票失败',icon:'none'})
				},fail:()=>{this.submitting=false;uni.showToast({title:'网络不太好',icon:'none'})}})
			}
		}
	}
</script>
<style scoped>
	.space-poll{margin:18rpx 0;padding:22rpx;border:1rpx solid #dfe6e4;border-radius:8rpx;background:#f7f9f8;color:#293437}.space-poll-title{font-size:28rpx;font-weight:650}.space-poll-description{margin-top:8rpx;color:#687275;font-size:24rpx;line-height:1.5}.space-poll-hint,.space-poll-total{margin-top:8rpx;color:#8a9395;font-size:21rpx}.poll-choice{display:flex;align-items:center;gap:14rpx;min-height:68rpx;margin-top:12rpx;padding:0 16rpx;border:1rpx solid #d9e0df;border-radius:6rpx;background:#fff;font-size:24rpx}.poll-choice.is-selected{border-color:#68a895;background:#edf5f2}.poll-choice-mark{display:inline-flex;align-items:center;justify-content:center;width:28rpx;height:28rpx;border:1rpx solid #aeb8b6;border-radius:50%;color:#168573;font-size:20rpx}.poll-submit{height:68rpx;margin:18rpx 0 0;border-radius:6rpx;background:#168573;color:#fff;font-size:24rpx;line-height:68rpx}.poll-submit[disabled]{opacity:.48}.poll-result{position:relative;margin-top:15rpx}.poll-result-track{height:60rpx;border-radius:5rpx;background:#e6ebea;overflow:hidden}.poll-result-fill{height:100%;background:#b9cfca}.poll-result-fill.is-selected{background:#69a995}.poll-result-label{position:absolute;inset:0;display:flex;align-items:center;justify-content:space-between;padding:0 14rpx;color:#263235;font-size:23rpx}.space-poll.is-night{border-color:#303738;background:#191d1e;color:#e2e7e8}.space-poll.is-night .space-poll-description,.space-poll.is-night .space-poll-hint,.space-poll.is-night .space-poll-total{color:#8d9799}.space-poll.is-night .poll-choice{border-color:#343c3d;background:#15191a;color:#dce2e3}.space-poll.is-night .poll-choice.is-selected{border-color:#4e8475;background:#24332f}.space-poll.is-night .poll-result-track{background:#293031}.space-poll.is-night .poll-result-fill{background:#49635c}.space-poll.is-night .poll-result-fill.is-selected{background:#4d8b79}.space-poll.is-night .poll-result-label{color:#e1e6e7}
</style>
