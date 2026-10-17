<template>
	<view class="gallery-page" :class="{'is-night': campusNight}">
		<view class="gallery-nav" :style="navStyle">
			<text class="cuIcon-back nav-back" @tap="back"></text>
			<view class="nav-title"><text>校园图集</text><small>GALLERY</small></view>
			<text class="cuIcon-add nav-add" @tap="openPost"></text>
		</view>
		<view class="gallery-intro"><view><text class="intro-title">看见校园</text><text class="intro-subtitle">用照片记录值得分享的日常</text></view><text class="intro-publish" @tap="openPost">发布图集 <text class="cuIcon-right"></text></text></view>
		<scroll-view class="tag-scroll" scroll-x :show-scrollbar="false">
			<view class="tag-track"><text class="tag-chip" :class="{'active':!activeTag}" @tap="selectTag('')">全部</text><text v-for="tag in tags" :key="tag" class="tag-chip" :class="{'active':activeTag===tag}" @tap="selectTag(tag)"># {{tag}}</text></view>
		</scroll-view>
		<view class="gallery-waterfall">
			<view class="gallery-column" v-for="(column,index) in columns" :key="index">
				<view class="gallery-card" v-for="item in column" :key="item.id" @tap="openInfo(item)">
					<view class="gallery-cover-stack" :style="{height:imageHeight(item)}">
						<image v-if="item.imageUrls.length>2" class="gallery-cover gallery-cover-back gallery-cover-back-two" :src="item.imageUrls[2]" mode="aspectFill" lazy-load />
						<image v-if="item.imageUrls.length>1" class="gallery-cover gallery-cover-back gallery-cover-back-one" :src="item.imageUrls[1]" mode="aspectFill" lazy-load />
						<image class="gallery-cover gallery-cover-main" :src="item.imageUrls[0]" mode="aspectFill" lazy-load />
						<view v-if="item.imageUrls.length>1" class="gallery-count-badge"><text class="cuIcon-picfill"></text> {{item.imageUrls.length}} 张</view>
					</view>
					<view class="gallery-card-copy"><text class="gallery-card-title">{{item.title}}</text><text v-if="item.description" class="gallery-card-desc">{{item.description}}</text><view class="gallery-card-tags"><text v-for="tag in item.tags.slice(0,3)" :key="tag">#{{tag}}</text></view><view class="gallery-card-foot"><view class="gallery-author"><image v-if="item.authorAvatar" :src="item.authorAvatar" mode="aspectFill"/><text>{{item.authorName||'校园用户'}}</text></view><text class="gallery-views"><text class="cuIcon-attention"></text> {{item.views||0}}</text></view></view>
				</view>
			</view>
		</view>
		<view class="gallery-state" v-if="loading">正在加载图集</view>
		<view class="gallery-state" v-else-if="!items.length">这个标签下还没有图集</view>
		<view class="gallery-state more" v-else @tap="loadMore">{{hasMore?'加载更多':'已经到底了'}}</view>
	</view>
</template>

<script>
import { isDongchangfuNight, resolveCampusNight, getCampusThemeMode } from '@/utils/campusTheme.js'
export default {
	data(){return {items:[],columns:[[],[]],tags:[],activeTag:'',page:1,total:0,loading:false,hasMore:true,screenWidth:375,campusNight:false}},
	onLoad(q){this.screenWidth=uni.getSystemInfoSync().windowWidth||375;this.activeTag=q&&q.tag?q.tag:'';this.loadTags();this.load()},
	onShow(){this.campusNight=resolveCampusNight(getCampusThemeMode(),isDongchangfuNight(Date.now()))},
	onPullDownRefresh(){this.page=1;this.items=[];this.columns=[[],[]];this.hasMore=true;this.load(()=>uni.stopPullDownRefresh())},
	onReachBottom(){this.loadMore()},
	methods:{
		back(){uni.navigateBack({delta:1})},
		openPost(){if(!this.token())return uni.navigateTo({url:'/pages/user/login'});uni.navigateTo({url:'/pages/gallery/post'})},
		openInfo(item){uni.navigateTo({url:'/pages/gallery/info?id='+item.id})},
		loadTags(){this.$Net.request({url:this.$API.galleryTags(),data:{},method:'get',dataType:'json',success:r=>{if(r.data&&r.data.code===1&&Array.isArray(r.data.data))this.tags=r.data.data.map(x=>x.name)}})},
		token(){try{return JSON.parse(uni.getStorageSync('userinfo')||'{}').token||''}catch(e){return ''}},
		selectTag(tag){if(this.activeTag===tag)return;this.activeTag=tag;this.page=1;this.items=[];this.columns=[[],[]];this.hasMore=true;this.load()},
		imageHeight(item){const width=Math.max(150,Math.min(490,(Math.min(this.screenWidth-30,1100)-12)/2));const ratio=Math.max(.35,Math.min(2.5,Number(item.imageRatio)||.8));return Math.round(width/ratio)+'px'},
		load(done){if(this.loading)return;this.loading=true;this.$Net.request({url:this.$API.galleryList(),data:{page:this.page,limit:20,tag:this.activeTag,_ts:Date.now()},method:'get',dataType:'json',success:r=>{const data=r.data&&r.data.code===1&&Array.isArray(r.data.data)?r.data.data:[];this.total=Number(r.data.total||0);this.append(data);this.hasMore=this.items.length<this.total;this.loading=false;if(done)done()},fail:()=>{this.loading=false;if(done)done()}})},
		append(rows){rows.forEach(item=>{item.imageUrls=Array.isArray(item.imageUrls)?item.imageUrls:[];item.tags=Array.isArray(item.tags)?item.tags:[];this.items.push(item);const left=this.columns[0].reduce((sum,x)=>sum+1/(Number(x.imageRatio)||.8),0);const right=this.columns[1].reduce((sum,x)=>sum+1/(Number(x.imageRatio)||.8),0);this.columns[left<=right?0:1].push(item)})},
		loadMore(){if(this.loading||!this.hasMore)return;this.page++;this.load()}
	}
}
</script>

<style scoped>
.gallery-page{min-height:100vh;background:#f5f6f4;color:#202925;padding-bottom:30px}.gallery-page.is-night{background:#17201d;color:#e4ebe7}.gallery-nav{height:56px;padding:calc(env(safe-area-inset-top) + 8px) 18px 0;display:flex;align-items:center;justify-content:space-between;position:sticky;top:0;z-index:5;background:rgba(245,246,244,.96);border-bottom:1px solid #e5e9e5}.is-night .gallery-nav{background:rgba(23,32,29,.96);border-color:#303d37}.nav-back,.nav-add{width:38px;height:38px;display:flex;align-items:center;justify-content:center;font-size:20px}.nav-title{display:flex;align-items:center;gap:7px;font-size:17px;font-weight:650}.nav-title small{color:#739184;font-size:9px;letter-spacing:1px}.gallery-intro{display:flex;align-items:end;justify-content:space-between;padding:24px 18px 16px}.intro-title,.intro-subtitle{display:block}.intro-title{font-size:23px;font-weight:700}.intro-subtitle{margin-top:5px;font-size:12px;color:#87918b}.is-night .intro-subtitle{color:#9daaa3}.intro-publish{padding:8px 0 5px;color:#27806b;font-size:12px}.tag-scroll{height:45px;white-space:nowrap}.tag-track{display:flex;gap:8px;padding:0 18px}.tag-chip{height:31px;line-height:31px;padding:0 13px;background:#e9eeea;color:#64736b;font-size:12px;border-radius:16px}.tag-chip.active{background:#263d34;color:#fff}.is-night .tag-chip{background:#27342e;color:#b7c5bc}.is-night .tag-chip.active{background:#4b806c;color:#fff}.gallery-waterfall{display:flex;align-items:flex-start;gap:10px;padding:4px 12px 0;max-width:1124px;margin:0 auto}.gallery-column{flex:1;min-width:0}.gallery-card{overflow:hidden;margin-bottom:10px;background:#fff;border-radius:8px;box-shadow:0 1px 5px rgba(29,48,38,.06)}.is-night .gallery-card{background:#202c27;box-shadow:none}.gallery-cover-stack{position:relative;width:100%;overflow:hidden;background:#e2e7e3}.gallery-cover{display:block;width:100%;height:100%}.gallery-cover-main{position:absolute;inset:0;z-index:3}.gallery-cover-back{position:absolute;inset:0;transform-origin:bottom center;border-radius:4px}.gallery-cover-back-one{z-index:2;transform:translate(5px,5px) scale(.97);opacity:.92}.gallery-cover-back-two{z-index:1;transform:translate(10px,10px) scale(.94);opacity:.72}.gallery-count-badge{position:absolute;right:8px;bottom:8px;z-index:4;padding:5px 8px;border-radius:13px;background:rgba(19,31,25,.78);color:#fff;font-size:10px;line-height:1;box-shadow:0 1px 4px rgba(0,0,0,.12)}.gallery-count-badge .cuIcon-picfill{margin-right:2px;font-size:10px}.gallery-card-copy{padding:10px 10px 9px}.gallery-card-title{display:-webkit-box;overflow:hidden;-webkit-line-clamp:2;-webkit-box-orient:vertical;font-size:14px;line-height:1.45;font-weight:650}.gallery-card-desc{display:-webkit-box;overflow:hidden;-webkit-line-clamp:2;-webkit-box-orient:vertical;margin-top:5px;color:#7d8982;font-size:11px;line-height:1.45}.gallery-card-tags{display:flex;gap:7px;overflow:hidden;margin-top:8px;color:#58806e;font-size:10px;white-space:nowrap}.gallery-card-foot{display:flex;justify-content:space-between;align-items:center;margin-top:10px;color:#929c96;font-size:10px}.gallery-author{display:flex;align-items:center;gap:6px;min-width:0}.gallery-author image{width:20px;height:20px;border-radius:50%;background:#e5ebe6}.gallery-author text{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;max-width:100px}.gallery-views{white-space:nowrap}.gallery-state{padding:24px 12px;text-align:center;color:#87928c;font-size:12px}.gallery-state.more{padding:18px}.is-night .gallery-state{color:#94a29a}@media(min-width:700px){.gallery-intro,.tag-track{padding-left:max(22px,calc((100vw - 1080px)/2));padding-right:max(22px,calc((100vw - 1080px)/2))}.gallery-waterfall{padding-left:20px;padding-right:20px;gap:14px}.gallery-card-copy{padding:13px}.gallery-card-title{font-size:15px}}
</style>
