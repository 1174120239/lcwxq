<template>
	<view class="detail-page">
		<view class="detail-nav"><text class="cuIcon-back" @tap="back"></text><text>图集</text><text class="nav-space"></text></view>
		<view v-if="item" class="detail-content">
			<view class="author-row"><image v-if="item.authorAvatar" :src="item.authorAvatar" mode="aspectFill"/><view><text class="author-name">{{item.authorName||'校园用户'}}</text><text class="author-date">{{date(item.created)}}</text></view><text class="view-count"><text class="cuIcon-attention"></text> {{item.views||0}}</text></view>
			<text class="detail-title">{{item.title}}</text><text class="detail-description" v-if="item.description">{{item.description}}</text>
			<view class="detail-tags"><text v-for="tag in item.tags" :key="tag" @tap="backToTag(tag)"># {{tag}}</text></view>
			<view class="detail-gallery">
				<swiper class="detail-swiper" :style="{height:detailHeight+'px'}" :current="currentIndex" :duration="260" @change="changeSlide">
					<swiper-item v-for="(url,index) in item.imageUrls" :key="url">
						<view class="detail-slide"><image :src="url" mode="aspectFit" @tap="preview(index)"/></view>
					</swiper-item>
				</swiper>
				<view class="detail-counter">{{currentIndex+1}} / {{item.imageUrls.length}}</view>
				<scroll-view v-if="item.imageUrls.length>1" class="detail-thumbs" scroll-x :scroll-into-view="thumbAnchor" :show-scrollbar="false">
					<view class="detail-thumb-track"><view v-for="(url,index) in item.imageUrls" :id="'gallery-thumb-'+index" :key="url" class="detail-thumb" :class="{'active':currentIndex===index}" @tap="selectSlide(index)"><image :src="url" mode="aspectFill"/></view></view>
				</scroll-view>
			</view>
		</view>
		<view v-else class="detail-state">{{loading?'正在加载图集':'图集不存在或尚未公开'}}</view>
	</view>
</template>

<script>
export default {data(){return {id:0,item:null,loading:true,currentIndex:0,screenWidth:375,thumbAnchor:''}},computed:{detailHeight(){const ratio=Math.max(.45,Math.min(2.2,Number(this.item&&this.item.imageRatio)||.8));const width=Math.max(280,this.screenWidth-36);return Math.max(260,Math.min(620,Math.round(width/ratio)))}},onLoad(q){this.screenWidth=uni.getSystemInfoSync().windowWidth||375;this.id=Number(q&&q.id||0);this.load()},methods:{back(){uni.navigateBack({delta:1})},token(){try{return JSON.parse(uni.getStorageSync('userinfo')||'{}').token||''}catch(e){return ''}},load(){this.$Net.request({url:this.$API.galleryInfo(),data:{id:this.id,token:this.token()},method:'get',dataType:'json',success:r=>{this.loading=false;if(r.data&&r.data.code===1){this.item=r.data.data;this.item.imageUrls=Array.isArray(this.item.imageUrls)?this.item.imageUrls:[];this.item.tags=Array.isArray(this.item.tags)?this.item.tags:[];this.currentIndex=0;this.updateThumbAnchor()}} ,fail:()=>this.loading=false})},changeSlide(event){this.currentIndex=Number(event.detail&&event.detail.current||0);this.updateThumbAnchor()},selectSlide(index){this.currentIndex=index},updateThumbAnchor(){this.thumbAnchor=this.item&&this.item.imageUrls.length?'gallery-thumb-'+this.currentIndex:''},preview(index){uni.previewImage({urls:this.item.imageUrls,current:this.item.imageUrls[index]})},date(value){const d=new Date(Number(value)>10000000000?Number(value):Number(value)*1000);return isNaN(d.getTime())?'':`${d.getFullYear()}.${String(d.getMonth()+1).padStart(2,'0')}.${String(d.getDate()).padStart(2,'0')}`},backToTag(tag){uni.redirectTo({url:'/pages/gallery/index?tag='+encodeURIComponent(tag)})}}}
</script>

<style scoped>
.detail-page{min-height:100vh;background:#fff;color:#202923}.detail-nav{height:calc(56px + env(safe-area-inset-top));padding:env(safe-area-inset-top) 18px 0;display:flex;align-items:center;justify-content:space-between;border-bottom:1px solid #edf0ed;font-size:15px}.detail-nav>.cuIcon-back{width:42px;font-size:20px}.nav-space{width:42px}.detail-content{max-width:860px;margin:0 auto;padding:18px 18px 50px}.author-row{display:flex;align-items:center;gap:10px}.author-row image{width:38px;height:38px;border-radius:50%;background:#e7ece8}.author-row>view{display:flex;flex-direction:column;gap:4px}.author-name{font-size:13px;font-weight:600}.author-date,.view-count{font-size:11px;color:#89958d}.view-count{margin-left:auto}.detail-title{display:block;margin-top:22px;font-size:23px;line-height:1.4;font-weight:700}.detail-description{display:block;margin-top:10px;font-size:14px;line-height:1.8;color:#59675f;white-space:pre-wrap}.detail-tags{display:flex;flex-wrap:wrap;gap:8px;margin:15px 0 18px}.detail-tags text{color:#4e8067;font-size:12px}.detail-gallery{position:relative;overflow:hidden;border-radius:6px;background:#0d120f}.detail-swiper{width:100%;background:#0d120f}.detail-slide{display:flex;align-items:center;justify-content:center;width:100%;height:100%;background:#0d120f}.detail-slide image{display:block;width:100%;height:100%}.detail-counter{position:absolute;right:12px;top:12px;z-index:2;padding:5px 9px;border-radius:12px;background:rgba(0,0,0,.58);color:#fff;font-size:11px}.detail-thumbs{height:60px;padding:10px 0;box-sizing:border-box;background:#121a15;white-space:nowrap}.detail-thumb-track{display:flex;gap:8px;padding:0 10px}.detail-thumb{width:44px;height:40px;flex:none;overflow:hidden;border:2px solid transparent;border-radius:3px;opacity:.58;box-sizing:border-box}.detail-thumb.active{border-color:#b7e4ca;opacity:1}.detail-thumb image{display:block;width:100%;height:100%}.detail-state{padding:100px 20px;text-align:center;color:#8a968f;font-size:13px}@media(min-width:700px){.detail-content{padding-top:28px}.detail-title{font-size:28px}.detail-thumb{width:52px;height:44px}}
</style>
