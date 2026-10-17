<template>
	<view class="detail-page">
		<view class="detail-nav"><text class="cuIcon-back" @tap="back"></text><text>图集</text><text class="nav-space"></text></view>
		<view v-if="item" class="detail-content">
			<view class="author-row"><image v-if="item.authorAvatar" :src="item.authorAvatar" mode="aspectFill"/><view><text class="author-name">{{item.authorName||'校园用户'}}</text><text class="author-date">{{date(item.created)}}</text></view><text class="view-count"><text class="cuIcon-attention"></text> {{item.views||0}}</text></view>
			<text class="detail-title">{{item.title}}</text><text class="detail-description" v-if="item.description">{{item.description}}</text>
			<view class="detail-tags"><text v-for="tag in item.tags" :key="tag" @tap="backToTag(tag)"># {{tag}}</text></view>
			<view class="detail-images"><image v-for="(url,index) in item.imageUrls" :key="url" :src="url" mode="widthFix" @tap="preview(index)"/></view>
		</view>
		<view v-else class="detail-state">{{loading?'正在加载图集':'图集不存在或尚未公开'}}</view>
	</view>
</template>

<script>
export default {data(){return {id:0,item:null,loading:true}},onLoad(q){this.id=Number(q&&q.id||0);this.load()},methods:{back(){uni.navigateBack({delta:1})},token(){try{return JSON.parse(uni.getStorageSync('userinfo')||'{}').token||''}catch(e){return ''}},load(){this.$Net.request({url:this.$API.galleryInfo(),data:{id:this.id,token:this.token()},method:'get',dataType:'json',success:r=>{this.loading=false;if(r.data&&r.data.code===1){this.item=r.data.data;this.item.imageUrls=Array.isArray(this.item.imageUrls)?this.item.imageUrls:[];this.item.tags=Array.isArray(this.item.tags)?this.item.tags:[]}} ,fail:()=>this.loading=false})},preview(index){uni.previewImage({urls:this.item.imageUrls,current:this.item.imageUrls[index]})},date(value){const d=new Date(Number(value)>10000000000?Number(value):Number(value)*1000);return isNaN(d.getTime())?'':`${d.getFullYear()}.${String(d.getMonth()+1).padStart(2,'0')}.${String(d.getDate()).padStart(2,'0')}`},backToTag(tag){uni.redirectTo({url:'/pages/gallery/index?tag='+encodeURIComponent(tag)})}}}
</script>

<style scoped>
.detail-page{min-height:100vh;background:#fff;color:#202923}.detail-nav{height:calc(56px + env(safe-area-inset-top));padding:env(safe-area-inset-top) 18px 0;display:flex;align-items:center;justify-content:space-between;border-bottom:1px solid #edf0ed;font-size:15px}.detail-nav>.cuIcon-back{width:42px;font-size:20px}.nav-space{width:42px}.detail-content{max-width:860px;margin:0 auto;padding:18px 18px 50px}.author-row{display:flex;align-items:center;gap:10px}.author-row image{width:38px;height:38px;border-radius:50%;background:#e7ece8}.author-row>view{display:flex;flex-direction:column;gap:4px}.author-name{font-size:13px;font-weight:600}.author-date,.view-count{font-size:11px;color:#89958d}.view-count{margin-left:auto}.detail-title{display:block;margin-top:22px;font-size:23px;line-height:1.4;font-weight:700}.detail-description{display:block;margin-top:10px;font-size:14px;line-height:1.8;color:#59675f;white-space:pre-wrap}.detail-tags{display:flex;flex-wrap:wrap;gap:8px;margin:15px 0 18px}.detail-tags text{color:#4e8067;font-size:12px}.detail-images image{display:block;width:100%;height:auto;margin-top:8px;border-radius:3px}.detail-state{padding:100px 20px;text-align:center;color:#8a968f;font-size:13px}@media(min-width:700px){.detail-content{padding-top:28px}.detail-title{font-size:28px}.detail-images image{margin-top:12px}}
</style>
