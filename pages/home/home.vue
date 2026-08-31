<template>
	<view class="campus-page campus-home" :class="[AppStyle, {'campus-night': weatherTheme.isDark, 'is-qixi-open': qixiVisible}]" :style="weatherThemeStyle">
		<view class="home-ambient" :class="{'is-transitioning': themeTransitioning}" aria-hidden="true">
			<view class="home-ambient-layer" :class="{'is-visible': activeAmbientLayer === 0}" :style="ambientLayerStyles[0]"></view>
			<view class="home-ambient-layer" :class="{'is-visible': activeAmbientLayer === 1}" :style="ambientLayerStyles[1]"></view>
			<view class="home-ambient-sheen"></view>
		</view>

		<view class="home-hero" :style="homeHeroStyle">
			<view class="hero-main">
				<view class="hero-copy" :class="{'has-qixi-secret': qixiAvailable}" @tap="handleQixiTap">
					<text class="hero-greeting">{{greetingText}}</text>
					<view class="hero-subtitle-line">
						<text class="hero-subtitle">{{greetingSubtitle}}</text>
						<text v-if="qixiAvailable" class="qixi-secret-hint cuIcon-magic" aria-hidden="true"></text>
					</view>
				</view>
				<!--  #ifdef H5 || APP-PLUS -->
				<view class="hero-actions">
					<view class="weather-pill" aria-label="山东聊城东昌府区天气，点击刷新" @tap="loadWeather(true)"><text class="weather-symbol">{{weatherInfo.symbol}}</text><text>{{weatherInfo.text}} {{weatherInfo.temperature}}℃</text></view>
					<view class="hero-icon-button" @tap="toSearch()"><text class="cuIcon-search"></text></view>
					<view class="hero-icon-button hero-user-button" @tap="goUserInfo()">
						<view class="cu-avatar round" :style="userInfo && userInfo.style" v-if="token!=''"></view>
						<text v-else class="cuIcon-my"></text>
					</view>
				</view>
				<!--  #endif -->
			</view>
		</view>

		<view class="home-stage">
			<view class="home-mode-switch">
				<view class="home-mode-item" :class="{'is-active': flag==0}" @tap="flag=0"><text class="cuIcon-hot"></text><text>此刻</text></view>
				<view class="home-mode-item" :class="{'is-active': flag==1}" @tap="flag=1"><text class="cuIcon-discover"></text><text>发现</text></view>
			</view>

			<view v-if="flag==0" class="home-panel panel-enter">
				<swiper v-if="lunbo_of==1" class="screen-swiper swiper-container"
					:class="dotStyle?'square-dot':'round-dot'" :indicator-dots="true" :circular="true"
					:autoplay="true" interval="5000" duration="520">
					<swiper-item v-for="(item,index) in swiperList" :key="index" @tap="toInfo(item)">
						<view class="swiper-box">
							<image :src="item.url" mode="aspectFill" v-if="item.type=='image'"></image>
							<video :src="item.url" autoplay loop muted :show-play-btn="false" :controls="false"
								objectFit="cover" v-if="item.type=='video'"></video>
						</view>
					</swiper-item>
				</swiper>

				<view v-if="top_of==1" class="index-sort home-shortcuts grid col-4">
					<view class="index-sort-box" v-if="!sy_appbox"><waves itemClass="butclass"><view class="index-sort-main" @tap="goPage('/pages/contents/blackhouse')"><view class="index-sort-i shortcut-green"><text class="cuIcon-apps"></text></view><view class="index-sort-text">小黑屋</view></view></waves></view>
					<view class="index-sort-box" v-if="sy_appbox"><waves itemClass="butclass"><view class="index-sort-main" @tap="goPage('/pages/plugins/sy_appbox/home',true)"><view class="index-sort-i shortcut-green"><text class="cuIcon-apps"></text></view><view class="index-sort-text">应用</view></view></waves></view>
					<view class="index-sort-box"><waves itemClass="butclass"><view class="index-sort-main" @tap="toShop"><view class="index-sort-i shortcut-blue"><text class="cuIcon-friend"></text></view><view class="index-sort-text">校园互助</view></view></waves></view>
					<view class="index-sort-box"><waves itemClass="butclass"><view class="index-sort-main" @tap="toLink('/pages/user/invitation')"><view class="index-sort-i shortcut-violet"><text class="cuIcon-share"></text></view><view class="index-sort-text">分享</view></view></waves></view>
					<view class="index-sort-box"><waves itemClass="butclass"><view class="index-sort-main" @tap="toUsers"><view class="index-sort-i shortcut-coral"><text class="cuIcon-calendar"></text></view><view class="index-sort-text">签到</view></view></waves></view>
				</view>

				<view v-if="gonggao_of==1" class="tn-notice-bar-class tn-notice-bar home-notice">
					<view class="notice-badge"><text class="cuIcon-notification"></text>校园快讯</view>
					<marquee v-if="noticeList.length > 0">{{ noticeList[0] }}</marquee>
				</view>
				<view class="ads-banner" v-if="bannerAdsInfo!=null"><image :src="bannerAdsInfo.img" mode="widthFix" @tap="goAds(bannerAdsInfo)"></image></view>

				<view class="all-box home-feed" :style="TabCur!=0?'margin-top:0;':''">
					<view v-if="hometop==1"><block v-for="(item,index) in topContents" :key="'top-new'+index"><articleItem :item="item" :isTop="true" :owoList="owoList" :home-feed="true"></articleItem></block></view>
					<view v-if="act_of==1">
						<block v-for="(item,index) in contentsList" :key="item.cid || ('feed'+index)"><articleItem :item="item" :owoList="owoList" :animation-index="index" :home-feed="true"></articleItem></block>
						<view class="qa-home-section" v-if="questionList.length>0">
							<view class="qa-home-heading"><text>校园问答</text><text class="qa-home-subtitle">一起把问题说清楚</text></view>
							<view class="qa-home-list">
								<qa-question-card v-for="item in questionList" :key="item.id" :question="item" :night="weatherTheme.isDark" @open="openQuestion"></qa-question-card>
							</view>
						</view>
						<view class="load-more" @tap="loadMore" v-if="dataLoad"><text>{{moreText}}</text></view>
						<view class="dataLoad" v-if="!dataLoad"><view class="campus-loader"></view></view>
					</view>
				</view>
			</view>

			<view v-if="flag==1" class="home-panel discovery-panel panel-enter">
				<swiper class="screen-swiper swiper-container" :class="dotStyle?'square-dot':'round-dot'"
					:indicator-dots="true" :circular="true" :autoplay="true" interval="5000" duration="520" v-if="bannerswitch==1">
					<swiper-item v-for="(item,index) in swiperList2" :key="item.url || index" v-if="index<adimage_sl" @tap="swiperclick(index)"><view class="swiper-box"><image mode="aspectFill" :src="item.url" lazy-load/></view></swiper-item>
				</swiper>
				<view class="all-box home-feed" :style="TabCur!=0?'margin-top:0;':''"><view v-if="findtop==1"><block v-for="(item,index) in topContents" :key="'discover-top'+index"><articleItem :item="item" :isTop="true" :owoList="owoList" :home-feed="true"></articleItem></block></view></view>
				<view class="data-box discovery-card">
					<view class="cu-bar bg-white"><view class="action data-box-title">话题推荐</view><view class="action more" @tap="toAlltag"><text>全部话题</text><text class="cuIcon-right"></text></view></view>
					<view class="tags1"><view class="tags"><text class="tags-box" v-for="(item,index) in tagList" :key="index" @tap="toSpaceTopic(item)">{{item.name}}</text></view></view>
				</view>
				<view class="data-box discovery-card">
					<view class="cu-bar bg-white"><view class="action data-box-title"><text class="hot-dot"></text>上升热点</view><view class="action more" @tap='toTopContents("更多热帖","commentsNum")'><text>更多热帖</text><text class="cuIcon-right"></text></view></view>
					<view class="top"><view class="top-box" v-for="(item,index) in topList" :key="index" @tap="toInfo(item)"><text>{{index+1}}</text>{{item.title}}</view></view>
				</view>
				<view class="qa-discovery-entry" v-if="questionList.length>0">
					<view class="qa-discovery-heading">
						<view><text class="qa-discovery-mark">问答</text><text class="qa-discovery-title">校园问答</text></view>
						<text class="qa-discovery-more">点击查看</text>
					</view>
					<qa-question-card :question="questionList[0]" :night="weatherTheme.isDark" @open="openQuestion"></qa-question-card>
				</view>
				<view class="section-heading"><text>推荐帖子</text><view class="more" @tap="toRecommend"><text>查看更多</text><text class="cuIcon-right"></text></view></view>
				<block v-for="(item,index) in recommendList" :key="item.cid || ('recommend'+index)"><articleItem :item="item" :owoList="owoList" :animation-index="index" :home-feed="true"></articleItem></block>
				<view class="dataLoad" v-if="!dataLoad"><view class="campus-loader"></view></view>
			</view>
		</view>

		<!--  #ifdef H5 -->
		<PublishPanel ref="publishPanel" :visible="true" :night="weatherTheme.isDark" :auto-intro="false"></PublishPanel>
		<!--  #endif -->
		<view v-if="false" class="cu-modal cu-modal2 bottom-modal show2 publish-modal" :class="modalName=='bottomModal'?'show':''" @tap="hideModal">
			<view class="cu-dialog"><view class="publish-sheet" @tap.stop>
				<view class="publish-sheet-title">发布到校园</view>
				<view class="publish-sheet-actions"><view class="publish-action" @tap="postSpace(1)"><image src="../../static/page/square/photo.png" mode="aspectFit"></image><text>发动态</text></view><view class="publish-action" @tap="postSpace(5)"><image src="../../static/page/square/shop.png" mode="aspectFit"></image><text>校园互助</text></view></view>
			</view></view>
		</view>

		<QixiEasterEgg :visible="qixiVisible" :night="weatherTheme.isDark" @close="closeQixiEasterEgg"></QixiEasterEgg>

		<view v-if="false" class="header" :style="{overflow: 'hidden', paddingTop: StatusBar + 'px', backgroundColor: 'rgba(244, 248, 248, 0.96)'}">

			<view class="cu-bar bg-white" :style="{'height': 50 + 'px'}">

				<view @click="flag=0" :class="flag==0?'tab-wrap-index square-box':'square-box2'"
					style="margin: 0upx 30upx 0upx 30upx;">首页</view>
				<view @click="flag=1" :class="flag==1?'tab-wrap-index square-box':'square-box2'"
					style="margin: 0px 100upx 0px 20upx;">发现</view>
				<!--  #ifdef H5 || APP-PLUS -->
				<view class="search-form radius" style="border-radius: 50%;" @tap="toSearch()">
					<text class="cuIcon-search"></text>
					<input type="text" :placeholder="sousuok" confirm-type="search"></input>
				</view>
				<view class="cu-avatar round" style="color: #323232;margin: 0px 10px 0px 0upx;" @tap="goUserInfo()"
					:style="userInfo.style" v-if="token!=''"></view>
				<view class="cu-avatar round" style="color: #323232;margin: 0px 10px 0px 0upx;" @tap="goUserInfo()"
					v-else>
					<text class="home-noLogin">登录</text>
				</view>
				<!--  #endif-->
			</view>
			<!--  #ifdef H5 -->

			<view class="position-fixed">
				<view class="round-button" @tap="showModal" data-target="bottomModal">
					<text class="cuIcon-edit" style="font-size: 18px;"></text>
				</view>
			</view>
			<!--  #endif-->
			<view class="data-box">

				<view class="cu-modal cu-modal2 bottom-modal show2" :class="modalName=='bottomModal'?'show':''"
					@tap="hideModal">

					<view class="cu-dialog" style="background-color: rgb(0, 0, 0,0);">

						<view class="position-fixed">
							<view class="round-button">
								<text class="cuIcon-close" style="font-size: 18px;"></text>
							</view>
						</view>
						<view class="tabbar-operate-main padding-xl flex justify-center"
							style="width: 100%;display: flex; justify-content: center; align-items: flex-end;padding: 170rpx;">
							<view class="text-white tn-flex-1" @tap="postSpace(1)"
								style="width: 110rpx; height: 110rpx;">
								<text
									style="display: inline-block; border-radius: 20rpx;  width: 100rpx;height: 100rpx;">
									<image src="../../static/page/square/photo.png" mode="widthFix"></image>
								</text>
								<view>发帖</view>
							</view>


							<view class="text-white tn-flex-1" @tap="postSpace(5)"
								style="border-radius: 20rpx; width: 110rpx; height: 110rpx;">
								<text
									style="display: inline-block; border-radius: 20rpx;  width: 100rpx;height: 100rpx;">
									<image src="../../static/page/square/shop.png" mode="widthFix"></image>

								</text>
								<view>校园互助</view>


							</view>
						</view>
					</view>
				</view>

			</view>
		</view>
		<view v-if="false && flag==0" class="">

			<view :style="[{padding:NavBar + 25 + 'px 10px 0px 10px'}]"></view>
			<block>
				<view class="margin-left-sm margin-right-sm">
					<swiper v-if="lunbo_of==1" class="screen-swiper swiper-container" style="border-radius: 20upx; "
						:class="dotStyle?'square-dot':'round-dot'" :indicator-dots="true" :circular="true"
						:autoplay="true" interval="5000" duration="500">
						<swiper-item v-for="(item,index) in swiperList" :key="index" @tap="toInfo(item)">
							<view class="swiper-box" style="border-radius: 20upx; ">
								<image :src="item.url" mode="aspectFill" v-if="item.type=='image'"></image>
								<video :src="item.url" autoplay loop muted :show-play-btn="false" :controls="false"
									objectFit="cover" v-if="item.type=='video'"></video>
							</view>
						</swiper-item>
					</swiper>
					<view v-if="top_of==1" class="index-sort grid col-4" style="border-radius: 20upx;">
						<view class="index-sort-box" v-if="!sy_appbox">
							<waves itemClass="butclass">
								<view class="index-sort-main" @tap="goPage('/pages/contents/blackhouse')">
									<view class="index-sort-i"
										style="border-radius: 20upx;background: linear-gradient(to bottom right, #aaff7f, #00ae4b);box-shadow: #55ff0059 0px 3px 5px 0px;">
										<text class="cuIcon-apps" style="color:  #ffffff;"></text>
									</view>
									<view class="index-sort-text">
										小黑屋
									</view>
								</view>
							</waves>
						</view>
						<view class="index-sort-box" v-if="sy_appbox">
							<waves itemClass="butclass">
								<view class="index-sort-main" @tap="goPage('/pages/plugins/sy_appbox/home',true)">
									<view class="index-sort-i"
										style="border-radius: 20upx;background: linear-gradient(to bottom right, #aaff7f, #00ae4b);box-shadow: #55ff0059 0px 3px 5px 0px;">
										<text class="cuIcon-apps" style="color:  #ffffff;"></text>
									</view>
									<view class="index-sort-text">
										应用
									</view>
								</view>
							</waves>
						</view>
						<view class="index-sort-box">
							<waves itemClass="butclass">
								<view class="index-sort-main" @tap="toShop">
									<view class="index-sort-i"
										style="border-radius: 20upx;background: linear-gradient(to bottom right, #aaffff, #89adff);box-shadow: #00aaff59 0px 3px 5px 0px;">
										<text class="cuIcon-goods" style="color:  #ffffff;"></text>
									</view>
									<view class="index-sort-text">
										校园互助
									</view>
								</view>
							</waves>
						</view>

						<view class="index-sort-box">
							<waves itemClass="butclass">
								<view class="index-sort-main" @tap="toLink('/pages/user/invitation')">
									<view class="index-sort-i"
										style="border-radius: 20upx;background: linear-gradient(to bottom right, #aaaaff, #811aff);box-shadow: #aa55ff59 0px 3px 5px 0px;">
										<text class="cuIcon-share" style="color:  #ffffff;"></text>
									</view>
									<view class="index-sort-text">
										分享
									</view>
								</view>
							</waves>
						</view>
						<view class="index-sort-box">
							<waves itemClass="butclass">
								<view class="index-sort-main" @tap="toUsers">
									<view class="index-sort-i"
										style="border-radius: 20upx;background: linear-gradient(to bottom right, #ffd198, #ff5c10);box-shadow: #ffaa0059 0px 3px 5px 0px;">
										<text class="cuIcon-calendar" style="color:  #ffffff;"></text>
									</view>
									<view class="index-sort-text">
										签到
									</view>
								</view>
							</waves>
						</view>

					</view>
					<!-- 滚动通知开头 -->
					<view v-if="gonggao_of==1" class="tn-notice-bar-class tn-notice-bar"
						style="display:flex; align-items:center;border-radius: 10px;backgroundColor:#fff;padding: 0px 5px 0px 10px;margin-top: 10px;">
						<view class="cuIcon-notification"></view>
						<marquee style="margin: 20rpx 10rpx 20rpx 20rpx;backgroundColor:#fff;"
							v-if="noticeList.length > 0">{{ noticeList[0] }}</marquee>
					</view>

					<view class="ads-banner" v-if="bannerAdsInfo!=null">
						<image :src="bannerAdsInfo.img" mode="widthFix" @tap="goAds(bannerAdsInfo)"></image>
					</view>

					<view class="all-box" :style="TabCur!=0?'margin-top:0;':''"
						style="background-color: rgb(0, 0, 0,0);">

						<view v-if="hometop==1">
							<block v-for="(item,index) in topContents" :key="'top'+index">


								<articleItem :item="item" :isTop="true" :owoList="owoList" :home-feed="true"></articleItem>


							</block>
						</view>

						<view v-if="act_of==1">

							<block v-for="(item,index) in contentsList" :key="item.cid || index" v-if="dataLoad">

								<articleItem :item="item" :owoList="owoList" :animation-index="index" :home-feed="true"></articleItem>

							</block>

							<view class="load-more" @tap="loadMore" v-if="dataLoad">

								<text>{{moreText}}</text>

							</view>
							<view class="dataLoad" v-if="!dataLoad">

								<view class="campus-loader"></view>

							</view>
						</view>

					</view>




					<!-- 滚动通知结束 -->
				</view>
			</block>

			
			<!--底下改成滑动形式-->



		</view>

		<view v-if="false && flag==1" class="">
			<view :style="[{padding:NavBar + 25 + 'px 10px 0px 10px'}]"></view>
			<swiper class="screen-swiper swiper-container" style="border-radius: 20upx;margin: 0px 10px 10px 10px;"
				:class="dotStyle?'square-dot':'round-dot'" :indicator-dots="true" :circular="true" :autoplay="true"
				interval="5000" duration="500" v-if="bannerswitch==1">
				<swiper-item v-for="(item,index) in swiperList2" :key="index" v-if="index<adimage_sl" @click="swiperclick(index)">
					<view class="swiper-box" style="border-radius: 20upx;">
						<image style="width: 100%; height: 100%;" mode="aspectFill" :src="item.url"/>
					</view>
				</swiper-item>
			</swiper>
			<view class="margin-left-sm margin-right-sm">

				<view class="all-box" :style="TabCur!=0?'margin-top:0;':''" style="background-color: rgb(0, 0, 0,0);">

					<view v-if="findtop==1">

						<block v-for="(item,index) in topContents" :key="'top'+index">


							<articleItem :item="item" :isTop="true" :owoList="owoList" :home-feed="true"></articleItem>


						</block>

					</view>
				</view>

				<view class="data-box" style="border-radius: 10px;margin-top: 0px;">
					<view class="cu-bar bg-white" style="border-radius: 10px;padding: 15px 5px 0px 5px;">
						<view class="action data-box-title">

							话题推荐 <text class="cuIcon-titles text-rule"></text>


						</view>
						<view class="action more" @tap="toAlltag">
							<text>全部话题</text><text class="cuIcon-right"></text>
						</view>
					</view>
					<view class="tags1">
						<view class="tags">

							<text class="tags-box" v-for="(item,index) in tagList"
								@tap="toSpaceTopic(item)">
								{{item.name}}
							</text>

						</view>
					</view>
				</view>
				<view class="data-box" style="border-radius: 20upx;margin-top:10px">

					<view class="cu-bar bg-white" style="border-radius: 10px;padding: 15px 5px 0px 5px;">

						<view class="action data-box-title">



							<text class="cuIcon-titles text-rule"></text>上升热点 <image src="/static/rd.png"
								style="width: 26px; height: 26px;"></image>

						</view>

						<view class="action more" @tap='toTopContents("更多热帖","commentsNum")'>

							<text>更多热帖</text><text class="cuIcon-right"></text>

						</view>



					</view>

					<view class="top">

						<view class="top-box" v-for="(item,index) in topList" :key="index" @tap="toInfo(item)">

							<text>{{index+1}}</text>{{item.title}}

						</view>

					</view>

				</view>

				<view class="data-box" style="padding: 10px;border-radius: 20upx">
					<view class="cu-bar bg-white" style="border-radius: 20upx;">
						<view class="action data-box-title">
							推荐帖子
						</view>
						<view class="action more" @tap="toRecommend">
							<text>更多推荐</text><text class="cuIcon-right"></text>
						</view>
					</view>
				</view>
				<view class="padding-xs" style="background-color: #f6f6f6;"></view>
				<block v-for="(item,index) in recommendList" :key="item.cid || index" v-if="dataLoad">

					<articleItem :item="item" :owoList="owoList" :animation-index="index" :home-feed="true"></articleItem>

				</block>

				<view class="dataLoad" v-if="!dataLoad">

					<view class="campus-loader"></view>

				</view>
				<view class="padding-xs" style="background-color: #f6f6f6;"></view>


			</view>
		</view>

		<!--加载遮罩-->
		<view class="loading" v-if="isLoading==0 && contentsList.length===0 && topContents.length===0 && recommendList.length===0 && swiperList.length===0">
			<view class="loading-main">
				<view class="campus-loader"></view>
			</view>
		</view>
		<!--加载遮罩结束-->
		<!--弹窗公告-->
		<view class="announcement" v-if="isAnnouncement&&Update!=1">
			<view class="announcement-bg" @tap="readAnnouncement">

			</view>
			<view class="announcement-main">
				<view class="announcement-title">
					公告
					<text class="cuIcon-close text-red" @tap.stop="readAnnouncement"></text>
				</view>
				<view class="announcement-concent" style="background-color: white;">
					<rich-text :nodes="announcement"></rich-text>
				</view>
				<view class="announcement-btn">
					<button class="cu-btn bg-blue lg" @tap="readAnnouncement">我知道了</button>
				</view>
			</view>
		</view>

		<!-- 强制更新不能通过遮罩或关闭按钮跳过。 -->
		<view class="app-update" v-if="Update===1">
			<view class="app-update-bg" @tap="dismissUpdate"></view>
			<view class="app-update-main">
				<view class="app-update-title">{{qzgx===1 ? '必须更新' : '发现新版本'}}</view>
				<view class="app-update-version">版本 {{versionTitle || '新版本'}}</view>
				<view class="app-update-content">
					<rich-text :nodes="versionIntro || '请更新到最新版本后继续使用。'"></rich-text>
				</view>
				<view class="app-update-btn">
					<button class="cu-btn bg-blue lg" @tap="openUpdate">立即更新</button>
					<button v-if="qzgx!==1" class="cu-btn app-update-later" @tap="dismissUpdate">稍后再说</button>
				</view>
			</view>
		</view>

		<!--  #ifdef APP-PLUS -->
		<view class="Startupmap" v-if="!isStart">
			<view class="Startupmap-close" @tap="toStart">
				<text>跳过</text>
			</view>
			<view class="Startupmap-close2">
				<text>广告</text>
			</view>
			<view class="Startupmap-pic" @tap="toStartUrl">
				<image :src="startImg.localUrl"></image>
			</view>
		</view>
		<!--  #endif -->
		<!--  #ifdef APP-PLUS -->
		<view style="height: 100upx;"></view>
		<Tabbar ref="tabbar" :current="0" :night="weatherTheme.isDark"></Tabbar>
		<!--  #endif -->

	</view>
</template>

<script>
	import waves from '@/components/xxley-waves/waves.vue';
	import metas from '@/pages/contents/metas.vue'
	import { applyCampusThemeShell, getCampusThemeMode } from '@/utils/campusTheme.js'
	import { bindCampusChromeScroll, handleCampusChromeScroll, resetCampusChromeScroll, unbindCampusChromeScroll, CAMPUS_CHROME_EVENT } from '@/utils/campusChrome.js'
	import { shuffleQuestions } from '@/utils/questions.js'
	import { refreshUnreadBadge } from '@/utils/unreadBadge.js'
	import { isQixiEasterEggDate } from '@/utils/qixiEasterEgg.js'
	import { checkAndroidWgtUpdate, installAndroidWgt } from '@/utils/appUpdate.js'
	import QixiEasterEgg from '@/components/qixi-easter-egg/qixi-easter-egg.vue'
	// #ifdef APP-PLUS
	import owo from '@/static/app-plus/owo/OwO.js'
	// #endif
	// #ifdef H5
	import owo from '@/static/h5/owo/OwO.js'
	// #endif
	// #ifdef MP
	var owo = [];
	// #endif
	// #ifdef APP-PLUS
	import Tabbar from '@/pages/components/tabBar.vue'
	// #endif
	import {
		localStorage
	} from '../../js_sdk/mp-storage/mp-storage/index.js'

	const WEATHER_COLORS = {
		// 融入聊城一中校徽的青绿、金色和少量暖红，整体比原版更鲜亮。
		clear: ['#21f0db', '#8fdcf6', '#ffd3e7', '#00b86b'],
		cloudy: ['#34ddd4', '#9ccfe8', '#ecc6df', '#16a96a'],
		overcast: ['#35c7c2', '#8fbcd4', '#d7bdd6', '#168c56'],
		rain: ['#20bccb', '#75b4d4', '#c5acd3', '#0f814f'],
		snow: ['#6bf2ec', '#c5ebf5', '#fae6f4', '#42c98a'],
		fog: ['#50d5d0', '#b3dae5', '#e7d7e5', '#49b67e'],
		storm: ['#139aa7', '#5a9bc0', '#a38abf', '#087347']
	}

	const TIME_COLORS = {
		dawn: { colors: ['#f5a899', '#ffd08f', '#f3aac8', '#5fca87'], amount: 0.18 },
		day: { colors: ['#ffffff', '#ffffff', '#ffffff', '#ffffff'], amount: 0 },
		dusk: { colors: ['#f08879', '#d9a064', '#ba85c4', '#218b65'], amount: 0.23 },
		night: { colors: ['#15191b', '#1a2022', '#202527', '#24292b'], amount: 0.82 }
	}

	function mixHex(base, tint, amount) {
		const parse = (hex) => [1, 3, 5].map((start) => parseInt(hex.slice(start, start + 2), 16))
		const baseRgb = parse(base)
		const tintRgb = parse(tint)
		const mixed = baseRgb.map((value, index) => Math.round(value + (tintRgb[index] - value) * amount))
		return '#' + mixed.map((value) => value.toString(16).padStart(2, '0')).join('')
	}

	export default {
		data() {
			return {

				noticeList: [],
				StatusBar: this.StatusBar,
				CustomBar: 80,
				NavBar: this.StatusBar + this.CustomBar,
				AppStyle: this.$store.state.AppStyle,
				weatherInfo: {
					symbol: '⛅',
					text: '天气',
					temperature: '--',
					code: 2,
					isDay: true,
					observedAt: ''
				},
				weatherRequesting: false,
				campusThemeMode: 'auto',
				themeClock: Date.now(),
				themeClockTimer: null,
				themeSwapTimer: null,
				themeTransitionTimer: null,
				themeTransitioning: false,
				activeAmbientLayer: 0,
				ambientLayerStyles: [{}, {}],
				ambientThemeKey: '',
				cardCur: 0,
				weburl: "",
				lunbo_of: 0,
				gonggao_of: 0,
				top_of: 0,
				act_of: 0,
				findtop: 0,
				hometop: 0,
				modalName: null,
				swiperList: [],
				swiperList2: [{
					url: '',
					zt: ''
				}],
				searchText: "",
				submitStatus1: false,
				submitStatus2: false,
				submitStatus3: false,
				submitStatus4: false,
				submitStatus5: false,
				submitStatus6: false,
				owoList: owo,
				contentsList: [],
				topContents: [],
				metaList: [],
				Topic: [],
				dotStyle: false,
				towerStart: 0,
				direction: '100000',
				TabCur: 0,
				scrollLeft: 0,
				countDown: 3,
				flag: 0,
				page: 1,
				moreText: "加载更多",
				isLoad: 0,
				token: "",
				sousuok: '',
				isLoading: 0,
				qzgx: 0,
				versionCode: 0,
				wgtVer: '',
				Update: 0,
				versionUrl: "",
				versionTitle: "",
				versionIntro: "",
				updateSource: "",
				updatePackage: null,
				updateInstalling: false,
				startImg: {
					localUrl: ""
				},
				isStart: false,
				dataLoad: false,
				pushAds: [],
				pushAdsInfo: null,
				bannerAds: [],
				bannerAdsInfo: null,
				announcement: "",
				isAnnouncement: false,
				noticeSum: 0,
				userInfo: null,
				owo: owo,
				owoList: [],
				gonggaotime: 86400000,
				adimage_sl: 0,
				bannerswitch:0,
				topList: [],
				tagList: [],
				recommendList: [],
				questionList: [],
				ads: "",
				noLogin: false,
				latestUserAvatar: [],

				//分类数据
				isMetasLoading: 0,
				metaPage: 1,
				metaCircleList: [],
				metaCircleMoreTxt: "加载更多",
				sy_appbox: false,
				lastHomeRefresh: 0,
				deferredHomeTimer: null,
				chromeProgress: 0,
				qixiTapCount: 0,
				qixiTapTimer: null,
				qixiVisible: false,

			}
		},
		computed: {
			qixiAvailable() {
				return isQixiEasterEggDate(new Date(this.themeClock))
			},
			homeHeroStyle() {
				const progress = Math.max(0, Math.min(1, Number(this.chromeProgress) || 0))
				return {
					paddingTop: this.StatusBar + 20 + 'px',
					opacity: String(1 - progress),
					transform: `translate3d(0, ${-110 * progress}%, 0)`
				}
			},
			dongchangfuHour() {
				return (new Date(this.themeClock).getUTCHours() + 8) % 24
			},
			weatherPeriod() {
				const hour = this.dongchangfuHour
				if (hour >= 5 && hour < 8) return 'dawn'
				if (hour >= 8 && hour < 17) return 'day'
				if (hour >= 17 && hour < 20) return 'dusk'
				return 'night'
			},
			weatherGroup() {
				const code = Number(this.weatherInfo.code)
				if (code === 0 || code === 1) return 'clear'
				if (code === 2) return 'cloudy'
				if (code === 3) return 'overcast'
				if (code === 45 || code === 48) return 'fog'
				if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82)) return 'rain'
				if ((code >= 71 && code <= 77) || code === 85 || code === 86) return 'snow'
				if (code >= 95) return 'storm'
				return 'cloudy'
			},
			weatherTheme() {
				const period = this.weatherPeriod
				const group = this.weatherGroup
				const shellNight = this.$store && this.$store.state.AppStyle === 'campus-night'
				const isDark = this.campusThemeMode === 'night' || (this.campusThemeMode === 'auto' && (shellNight || period === 'night' || group === 'storm'))
				const visualPeriod = isDark ? 'night' : (this.campusThemeMode === 'day' ? 'day' : period)
				const timePalette = TIME_COLORS[visualPeriod]
				const colors = WEATHER_COLORS[group].map((color, index) => mixHex(color, timePalette.colors[index], timePalette.amount))
				return {
					key: group + '-' + visualPeriod + '-' + this.campusThemeMode,
					background: `linear-gradient(145deg, ${colors[0]} 0%, ${colors[1]} 28%, ${colors[2]} 58%, ${colors[3]} 100%)`,
					base: colors[1],
					isDark: isDark,
					foreground: isDark ? '#f5fbfc' : '#17272a',
					muted: isDark ? 'rgba(245, 251, 252, 0.82)' : 'rgba(23, 39, 42, 0.82)',
					chrome: isDark ? 'rgba(255, 255, 255, 0.14)' : 'rgba(255, 255, 255, 0.2)',
					chromeActive: isDark ? 'rgba(255, 255, 255, 0.24)' : 'rgba(255, 255, 255, 0.36)',
					chromeBorder: isDark ? 'rgba(255, 255, 255, 0.34)' : 'rgba(255, 255, 255, 0.8)'
				}
			},
			weatherThemeStyle() {
				return {
					'--weather-foreground': this.weatherTheme.foreground,
					'--weather-muted': this.weatherTheme.muted,
					'--weather-chrome': this.weatherTheme.chrome,
					'--weather-chrome-active': this.weatherTheme.chromeActive,
					'--weather-chrome-border': this.weatherTheme.chromeBorder,
					'--school-green': this.weatherTheme.isDark ? '#45a878' : '#00843d',
					'--school-green-bright': this.weatherTheme.isDark ? '#58b486' : '#00a85a',
					'--school-gold': this.weatherTheme.isDark ? '#c3a45d' : '#d8af3f',
					'--school-red': this.weatherTheme.isDark ? '#d86d72' : '#e60012',
					'--campus-stage': this.weatherTheme.isDark ? '#171b1d' : 'rgba(250, 254, 254, 0.74)',
					'--campus-card': this.weatherTheme.isDark ? '#212628' : 'rgba(255, 255, 255, 0.88)',
					'--campus-card-border': this.weatherTheme.isDark ? 'rgba(226, 232, 230, 0.09)' : 'rgba(255, 255, 255, 0.76)',
					'--campus-card-text': this.weatherTheme.isDark ? '#edf0ef' : '#213437',
					'--campus-card-muted': this.weatherTheme.isDark ? '#a1aaa7' : '#26383b'
				}
			},
			greetingText() {
				if (this.qixiAvailable) return '七夕快乐。';
				const hour = this.dongchangfuHour;
				if (hour < 6) return '夜深了。';
				if (hour < 11) return '上午好。';
				if (hour < 14) return '中午好。';
				if (hour < 18) return '下午好。';
				return '晚上好。';
			},
			greetingSubtitle() {
				if (this.qixiAvailable) return '爱自己是终身浪漫的开始';
				const hour = this.dongchangfuHour;
				if (hour < 6) return '夜色温柔，注意休息。';
				if (hour < 11) return '元气满满，开启校园新一天。';
				if (hour < 14) return '记得按时吃饭，也给自己充充电。';
				if (hour < 18) return '保持专注，也别忘了看看远处。';
				return '夜色温柔，注意休息。';
			}
		},
		watch: {
			weatherTheme: {
				immediate: true,
				deep: true,
				handler(theme) {
					this.applyWeatherTheme(theme)
				}
			}
		},
		onPullDownRefresh() {
			var that = this;
			that.loading(true);
			// #ifdef H5 || APP-PLUS
			that.loadWeather(true);
			// #endif
			var timer = setTimeout(function() {
				uni.stopPullDownRefresh();
			}, 1000)
		},


		onPageScroll(event) {
			handleCampusChromeScroll(this, event && event.scrollTop)
		},
		onShow() {
			var that = this;
			resetCampusChromeScroll(that);
			bindCampusChromeScroll(that);
			that.loadCampusThemeMode();
			that.startThemeClock();
			// #ifdef H5 || APP-PLUS
			that.loadWeather(false);
			// #endif
			that.$nextTick(function() {
				// #ifdef APP-PLUS
				if (that.$refs.tabbar) that.$refs.tabbar.activate()
				// #endif
				// #ifdef H5
				if (that.$refs.publishPanel) that.$refs.publishPanel.activatePage()
				// #endif
			})
			var cachedUser = localStorage.getItem('userinfo');
			if (cachedUser) {
				try {
					that.userInfo = JSON.parse(cachedUser);
					that.userInfo.style = "background-image:url(" + that.userInfo.avatar + ");"
					that.group = that.userInfo.group;
				} catch (error) {
					localStorage.removeItem('userinfo');
					that.userInfo = null;
				}
			} else {
				that.userInfo = null;
			}
			if (localStorage.getItem('token')) {

				that.token = localStorage.getItem('token');
			} else {
				that.token = "";
			}
			// #ifdef APP-PLUS || H5

			that.getAdsCache();

			that.getAds();
			// #endif
			// #ifdef APP-PLUS

			uni.hideTabBar({
				animation: false
			})
			//如果启动图还没有缓存过，第一次进来就不显示启动图了
			if (!localStorage.getItem('appStart')) {
				that.isStart = true;
			}

			plus.navigator.setStatusBarStyle(that.weatherTheme.isDark ? 'light' : 'dark')
			// #endif
			//获取缓存
			that.allCache();
			// 首页问答推荐独立刷新，不受其他首页数据的短时节流影响。
			that.getQuestionList();
			// 每次重新显示首页都向服务端校准，后台删除后不再长期停留在旧缓存。
			that.loading(false);
			if (localStorage.getItem('token')) {

				that.token = localStorage.getItem('token');
			}
			that.unreadNum();
			that.userStatus();
			// #ifdef APP-PLUS
			// 已登录用户每次回到首页都向服务端校准推送 clientId，换设备或重装后也能继续收到通知。
			if (that.token) {
				that.getCID();
			}
			// #endif
			// #ifdef APP-PLUS

			//外部启动APP处理
			var args = plus.runtime.arguments;
			plus.runtime.arguments = null;
			plus.runtime.arguments = "";
			if (args) {

				//跳转到帖子
				if (args.indexOf("?info=") != -1) {
					var arr = args.split("?info=");
					uni.navigateTo({
						url: '/pages/contents/info?cid=' + arr[1]
					});
				}
				//判断是否是扫码登录
				if (args.indexOf("?scan=") != -1) {
					var arr = args.split("?scan=");
					that.scanLogin(arr[1]);
				}
				
			}

			// #endif
			
			that.getadimg();


		},
		onLoad() {
			var that = this;
			// #ifdef APP-PLUS
			that.NavBar = this.CustomBar;

			that.isUpdate(false);
			// #endif
			var owo = that.owo.data;
			var owoList = [];
			for (var i in owo) {
				owoList = owoList.concat(owo[i].container);
			}
			that.owoList = owoList;



			setTimeout(function() {
				that.isStart = true;
			}, 5000);
			// #ifdef APP-PLUS
			that.appStartImg();
			//#endif
			//插件检测
			var cachedPlugins = localStorage.getItem('getPlugins');
			if (cachedPlugins) {
				let pluginList = [];
				try { pluginList = JSON.parse(cachedPlugins); } catch (error) { localStorage.removeItem('getPlugins'); }
				// 检查插件是否存在于插件列表中
				that.sy_appbox = pluginList.includes('sy_appbox'); 
			}

		},
		onReachBottom() {
			//触底后执行的方法，比如无限加载之类的
			var that = this;
			if (that.isLoad == 0) {
				that.loadMore();
			}
		},
		mounted() {
			uni.$on(CAMPUS_CHROME_EVENT, this.handleChromeVisibility);
			this.getgg();
			this.getAnnouncement();
		},
		onHide() {
			resetCampusChromeScroll(this);
			unbindCampusChromeScroll(this);
			// Stop fixed navigation/publish layers while this tab is kept alive in the page stack.
			if (this.$refs.tabbar && this.$refs.tabbar.deactivate) this.$refs.tabbar.deactivate();
			if (this.$refs.publishPanel && this.$refs.publishPanel.resetPanel) this.$refs.publishPanel.resetPanel();
			this.stopThemeClock();
			this.resetQixiEasterEgg();
		},
		onUnload() {
			uni.$off(CAMPUS_CHROME_EVENT, this.handleChromeVisibility);
			clearTimeout(this.deferredHomeTimer);
			this.deferredHomeTimer = null;
			this.stopThemeClock();
			clearTimeout(this.themeSwapTimer);
			this.themeSwapTimer = null;
			clearTimeout(this.themeTransitionTimer);
			this.themeTransitionTimer = null;
			this.resetQixiEasterEgg();
		},
		methods: {
			handleQixiTap() {
				if (!this.qixiAvailable || this.qixiVisible) return
				clearTimeout(this.qixiTapTimer)
				this.qixiTapCount += 1
				if (this.qixiTapCount >= 7) {
					this.qixiTapCount = 0
					this.qixiVisible = true
					return
				}
				this.qixiTapTimer = setTimeout(() => {
					this.qixiTapCount = 0
					this.qixiTapTimer = null
				}, 3000)
			},
			closeQixiEasterEgg() {
				this.qixiVisible = false
			},
			resetQixiEasterEgg() {
				clearTimeout(this.qixiTapTimer)
				this.qixiTapTimer = null
				this.qixiTapCount = 0
				this.qixiVisible = false
			},
			unreadNum() {
				refreshUnreadBadge(this, this.token, (count) => {
					this.noticeSum = count
				})
			},
			handleChromeVisibility(state) {
				const progress = state && typeof state === 'object' ? state.progress : (state ? 1 : 0)
				this.chromeProgress = Math.max(0, Math.min(1, Number(progress) || 0))
			},
			loadCampusThemeMode() {
				this.campusThemeMode = getCampusThemeMode()
				applyCampusThemeShell(this.campusThemeMode, this.themeClock)
			},
			handleCampusThemeMode(mode) {
				this.campusThemeMode = mode
				// #ifdef APP-PLUS
				this.$nextTick(() => plus.navigator.setStatusBarStyle(this.weatherTheme.isDark ? 'light' : 'dark'))
				// #endif
			},
			startThemeClock() {
				this.stopThemeClock()
				this.themeClock = Date.now()
				applyCampusThemeShell(this.campusThemeMode, this.themeClock)
				const nextHour = (Math.floor(this.themeClock / (60 * 60 * 1000)) + 1) * 60 * 60 * 1000
				this.themeClockTimer = setTimeout(() => this.startThemeClock(), nextHour - this.themeClock + 120)
			},
			stopThemeClock() {
				if (!this.themeClockTimer) return
				clearTimeout(this.themeClockTimer)
				this.themeClockTimer = null
			},
			applyWeatherTheme(theme) {
				if (!theme || !theme.key || theme.key === this.ambientThemeKey) return
				const style = {
					backgroundColor: theme.base,
					backgroundImage: theme.background
				}
				if (!this.ambientThemeKey) {
					this.$set(this.ambientLayerStyles, 0, style)
					this.$set(this.ambientLayerStyles, 1, style)
					this.ambientThemeKey = theme.key
					return
				}
				const nextLayer = this.activeAmbientLayer === 0 ? 1 : 0
				this.$set(this.ambientLayerStyles, nextLayer, style)
				this.ambientThemeKey = theme.key
				clearTimeout(this.themeSwapTimer)
				clearTimeout(this.themeTransitionTimer)
				this.themeTransitioning = true
				this.$nextTick(() => {
					this.themeSwapTimer = setTimeout(() => {
						this.activeAmbientLayer = nextLayer
						this.themeTransitionTimer = setTimeout(() => {
							this.themeTransitioning = false
						}, 760)
					}, 24)
				})
				// #ifdef APP-PLUS
				plus.navigator.setStatusBarStyle(theme.isDark ? 'light' : 'dark')
				// #endif
			},
			getWeatherMeta(code, isDay) {
				const weatherCode = Number(code)
				if (weatherCode === 0) return { text: '晴', symbol: isDay ? '☀️' : '🌙' }
				if (weatherCode === 1) return { text: '少云', symbol: isDay ? '🌤️' : '🌙' }
				if (weatherCode === 2) return { text: '多云', symbol: '⛅' }
				if (weatherCode === 3) return { text: '阴', symbol: '☁️' }
				if (weatherCode === 45 || weatherCode === 48) return { text: '有雾', symbol: '🌫️' }
				if (weatherCode >= 51 && weatherCode <= 57) return { text: '毛毛雨', symbol: '🌦️' }
				if (weatherCode >= 61 && weatherCode <= 67) return { text: '有雨', symbol: '🌧️' }
				if (weatherCode >= 71 && weatherCode <= 77) return { text: '有雪', symbol: '🌨️' }
				if (weatherCode >= 80 && weatherCode <= 82) return { text: '阵雨', symbol: '🌦️' }
				if (weatherCode === 85 || weatherCode === 86) return { text: '阵雪', symbol: '🌨️' }
				if (weatherCode >= 95) return { text: '雷雨', symbol: '⛈️' }
				return { text: '天气', symbol: '⛅' }
			},
			loadWeather(force = false) {
				const cacheKey = 'dongchangfuWeatherCache'
				const cacheDuration = 10 * 60 * 1000
				let cached = null

				try {
					cached = uni.getStorageSync(cacheKey)
					if (cached && cached.info) this.weatherInfo = Object.assign({}, this.weatherInfo, cached.info)
				} catch (error) {}

				if (!force && cached && Date.now() - cached.time < cacheDuration) return
				if (this.weatherRequesting) return
				this.weatherRequesting = true

				uni.request({
					url: 'https://api.open-meteo.com/v1/forecast',
					data: {
						latitude: 36.45,
						longitude: 115.98,
						current: 'temperature_2m,weather_code,is_day',
						timezone: 'Asia/Shanghai'
					},
					method: 'GET',
					timeout: 8000,
					success: (res) => {
						const current = res && res.data && res.data.current
						if (!current || current.temperature_2m === undefined) return
						const temperature = Number(current.temperature_2m)
						if (!Number.isFinite(temperature)) return
						const meta = this.getWeatherMeta(current.weather_code, Number(current.is_day) === 1)
						const info = {
							symbol: meta.symbol,
							text: meta.text,
							temperature: Math.round(temperature),
							code: Number(current.weather_code),
							isDay: Number(current.is_day) === 1,
							observedAt: current.time || ''
						}
						this.weatherInfo = info
						try {
							uni.setStorageSync(cacheKey, { info: info, time: Date.now() })
						} catch (error) {}
					},
					complete: () => {
						this.weatherRequesting = false
					}
				})
			},
			loadMore() {
				var that = this;
				that.moreText = "加载中...";
				that.isLoad = 1;
				// 分类页必须继续加载当前分类，不能把“全部内容”混入分类列表。
				if (that.TabCur == 0) {
					that.getContentsList(true);
				} else {
					that.getMetaContents(true, that.TabCur);
				}
			},
			goAds2(url) {
				var that = this;
				// #ifdef APP-PLUS
				plus.runtime.openWeb(url);
				// #endif
				// #ifdef H5
				window.open(url)
				// #endif
			},
			getTopPic() {
				var that = this;
				if (that.submitStatus6) {
					return false;
				}
				that.submitStatus6 = true;
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
						that.submitStatus6 = false;
						var timer = setTimeout(function() {
							that.isLoading = 1;
							clearTimeout('timer')
						}, 300)
					},
					fail: function(res) {
						that.submitStatus6 = false;
						var timer = setTimeout(function() {
							that.isLoading = 1;
							clearTimeout('timer')
						}, 300)
					}
				})
			},
			searchClose() {
				this.searchText = "";
			},
			getAdsCache() {
				var that = this;
				if (localStorage.getItem('bannerAds')) {
					that.bannerAds = JSON.parse(localStorage.getItem('bannerAds'));

					var num = that.bannerAds.length;
					if (num > 0) {
						var rand = Math.floor(Math.random() * num);
						that.bannerAdsInfo = that.bannerAds[rand];
					}
				}
			},
			loading(force = false) {
				var that = this;
				const now = Date.now();
				if (!force && that.lastHomeRefresh && now - that.lastHomeRefresh < 12000) return;
				that.lastHomeRefresh = now;
				that.page = 1;
				clearTimeout(that.deferredHomeTimer);
				// First paint only requests content visible above the fold.
				that.getSwiper();

				that.getTopContents();
				// 两个请求都会写 contentsList。并发请求会造成列表互相覆盖，因此只刷新当前页签。
				if (that.TabCur == 0) {
					that.getContentsList(false);
				} else {
					that.getMetaContents(false, that.TabCur);
				}
				// Discovery data waits until the route transition has settled.
				that.deferredHomeTimer = setTimeout(function() {
					that.getRecommend();
					that.getTopList();
					that.getMetaList();
					that.getTagList();
					that.getTopPic();
					that.deferredHomeTimer = null;
				}, 140);
			},
			replaceSpecialChar(text) {
				text = text.replace(/&quot;/g, '"');
				text = text.replace(/&amp;/g, '&');
				text = text.replace(/&lt;/g, '<');
				text = text.replace(/&gt;/g, '>');
				text = text.replace(/&nbsp;/g, ' ');
				text = text.replace("||rn||","\n");
				return text;
			},
			getAds() {
				var that = this;

				that.$Net.request({
					url: that.$API.GetAds(),
					header: {
						'Content-Type': 'application/x-www-form-urlencoded'
					},
					method: "post",
					dataType: 'json',
					success: function(res) {
						if (res.data.isAds == 1) {
							that.ads = res.data.ad1.split("|");
						}

					},
					fail: function(res) {

					}
				})
			},
			goLogin() {
				var that = this;
				uni.navigateTo({
					url: '/pages/user/login'
				});
			},
			getRecommend() {
				var that = this;
				if (that.submitStatus3) {
					return false;
				}
				that.submitStatus3 = true;
				var data = {
					"type": "post",
					"isrecommend": 1
				}
				var token = "";
				if (localStorage.getItem('userinfo')) {
					try {
						var userInfo = JSON.parse(localStorage.getItem('userinfo'));
						token = userInfo && userInfo.token ? userInfo.token : '';
					} catch (error) {
						localStorage.removeItem('userinfo');
					}
				}
				that.$Net.request({
					url: that.$API.getContentsList(),
					data: {
						"searchParams": JSON.stringify(that.$API.removeObjectEmptyKey(data)),
						"limit": 5,
						"page": 1,
						"order": "modified",
						"token": token
					},
					header: {
						'Content-Type': 'application/x-www-form-urlencoded'
					},
					method: "post",
					dataType: 'json',
					success: function(res) {

						if (res.data.code == 1) {
							that.noLogin = false;
							var list = res.data.data;
							if (list.length > 0) {

								that.recommendList = list;

							} else {
								that.recommendList = [];
							}
							localStorage.setItem('recommendList', JSON.stringify(that.recommendList));
						} else {
							if (res.data.msg == "用户未登录或Token验证失败") {
								that.noLogin = true;
							}
						}
						that.submitStatus3 = false;
					},
					fail: function(res) {
						that.submitStatus3 = false;
					}
				})
			},

			goAds(data) {
				var that = this;
				var url = data.url;
				var type = data.urltype;
				// #ifdef APP-PLUS
				if (type == 1) {
					plus.runtime.openURL(url);
				}
				if (type == 0) {
					plus.runtime.openWeb(url);
				}
				// #endif
				// #ifdef H5
				window.open(url)
				// #endif
			},
			toSearch() {
				var that = this;
				if (that.noLogin) {
					uni.navigateTo({
						url: '/pages/user/login'
					});
					return false;
				}
				uni.navigateTo({
					url: '/pages/contents/search'
				});
			},
			toCategoryContents(title, id) {
				var that = this;
				var type = "meta";
				uni.navigateTo({
					url: '/pages/contents/contentlist?title=' + title + "&type=" + type + "&id=" + id
				});
			},
			toTopContents(title, id) {
				var that = this;
				var type = "meta";
				uni.navigateTo({
					url: '/pages/contents/contentlist?title=' + title + "&type=top&id=" + id
				});
			},
			toAllcategory() {
				var that = this;
				uni.navigateTo({
					url: '/pages/contents/allcategory'
				});
			},
			toRecommend() {
				var that = this;

				uni.navigateTo({
					url: '/pages/contents/recommend'
				});
			},
			postSpace(type) {
				var that = this;
				if (type == 1) {
					uni.navigateTo({
						url: '/pages/user/post?isSpace=1'
					});
				} else if (type == 5) {
					uni.navigateTo({
						url: '/pages/user/addshop?isSpace=1&returnTo=mutualAidList'
					});
				} else {
					uni.navigateTo({
						url: '/pages/space/post?type=' + type
					});
				}
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
			toAds(url) {
				// #ifdef APP-PLUS
				plus.runtime.openURL(url)
				// #endif
				// #ifdef H5
				window.open(url)
				// #endif
			},
			startCountDown() {
				if (this.countDown > 0) {
					this.countDown--
					setTimeout(this.startCountDown, 1000)
				} else {
					this.toStart()
				}
			},
			getCID() {
				var that = this;
				let cid = ''
				// #ifdef APP-PLUS
				let pinf = plus.push.getClientInfo();
				cid = pinf.clientid;
				if (cid) {
					that.setClientId(cid);
				}
				// #endif
			},
			setClientId(cid) {
				var that = this;
				var token = "";
				if (localStorage.getItem('token')) {

					token = localStorage.getItem('token');
				} else {
					return false;
				}
				that.$Net.request({

					url: that.$API.setClientId(),
					data: {
						"clientId": cid,
						"token": token
					},
					header: {
						'Content-Type': 'application/x-www-form-urlencoded'
					},
					method: "get",
					dataType: 'json',
					success: function(res) {
						if (res.data.code == 1) {


						}
					},
					fail: function(res) {
						uni.showToast({
							title: "网络不太好哦",
							icon: 'none'
						})
					}
				})
			},
			//获取并缓存广告
			getAds() {
				var that = this;
				// #ifdef APP-PLUS || H5
				//获取推流广告
				that.getAdsList(0);
				//获取横幅广告
				that.getAdsList(1);
				//#endif
				// #ifdef APP-PLUS
				//获取启动图广告
				that.getAdsList(2);
				//#endif
			},
			getAdsCache() {
				var that = this;
				if (localStorage.getItem('pushAds')) {
					that.pushAds = JSON.parse(localStorage.getItem('pushAds'));
				}

				if (localStorage.getItem('bannerAds')) {
					that.bannerAds = JSON.parse(localStorage.getItem('bannerAds'));

					var num = that.bannerAds.length;
					if (num > 0) {
						var rand = Math.floor(Math.random() * num);
						that.bannerAdsInfo = that.bannerAds[rand];
					}
				}

			},
			getAdsList(type) {
				var that = this;
				var data = {
					"type": type,
				}
				that.$Net.request({
					url: that.$API.adsList(),
					data: {
						"searchParams": JSON.stringify(that.$API.removeObjectEmptyKey(data)),
						"limit": 100,
					},
					header: {
						'Content-Type': 'application/x-www-form-urlencoded'
					},
					method: "get",
					dataType: 'json',
					success: function(res) {
						if (res.data.code == 1) {
							var list = res.data.data;
							if (type == 0) {
								that.pushAds = res.data.data;
								localStorage.setItem('pushAds', JSON.stringify(that.pushAds));
							}
							if (type == 1) {
								that.bannerAds = res.data.data;

								localStorage.setItem('bannerAds', JSON.stringify(that.bannerAds));

							}
							if (type == 2) {
								that.startAds = res.data.data;
								localStorage.setItem('startAds', JSON.stringify(that.startAds));
							}
						}


					},
					fail: function(res) {

					}
				})
			},
			toAlltag() {
				uni.navigateTo({ url: '/pages/space/topics' });
			},
			toSpaceTopic(topic) {
				if (!topic || !topic.mid) return;
				uni.navigateTo({
					url: '/pages/space/topics?mid=' + encodeURIComponent(String(topic.mid))
						+ '&name=' + encodeURIComponent(topic.name || '')
				});
			},
			swiperclick(index) {
				const data = this.swiperList2[index];
				this.goAds2(data.zt)
				
			},
			tabSelect(e) {
				var that = this;
				that.TabCur = e.currentTarget.dataset.id;
				that.page = 1;
				that.scrollLeft = (e.currentTarget.dataset.id - 1) * 60;
				that.contentsList = [];
				that.dataLoad = false;
				if (that.TabCur == 0) {
					that.getContentsList(false);
				} else {
					that.getMetaContents(false, that.TabCur);
				}
			},
			// 读取浏览器缓存时容忍旧版本或异常退出留下的损坏 JSON。
			readCache(key, fallback) {
				var value = localStorage.getItem(key);
				if (!value) {
					return fallback;
				}
				try {
					return JSON.parse(value);
				} catch (error) {
					localStorage.removeItem(key);
					return fallback;
				}
			},
			// 公共缓存只负责首屏占位；网络成功后即使返回空数组，也必须覆盖这些旧数据。
			filterRecommendedQuestions(list) {
				const recommended = (Array.isArray(list) ? list : [])
					.filter(function(item) { return item && Number(item.recommended) === 1 })
				return shuffleQuestions(recommended)
					.slice(0, 4)
			},
			allCache() {
				var that = this;
				var meta = that.TabCur;
				if (localStorage.getItem('swiperList')) {
					that.swiperList = that.readCache('swiperList', []);
					var timer = setTimeout(function() {
						that.isLoading = 1;
						clearTimeout('timer')
					}, 300)
				}
				if (localStorage.getItem('topList')) {
					that.topList = that.readCache('topList', []);
					var timer = setTimeout(function() {
						that.isLoading = 1;
						clearTimeout('timer')
					}, 300)
				}
				if (localStorage.getItem('recommendList')) {
					that.recommendList = that.readCache('recommendList', []);
				}
				if (localStorage.getItem('qaRecommendedQuestionList')) {
					that.questionList = that.filterRecommendedQuestions(that.readCache('qaRecommendedQuestionList', []));
				}
				// 旧键曾缓存全部问答，不能继续作为首页推荐数据源。
				if (localStorage.getItem('qaQuestionList')) localStorage.removeItem('qaQuestionList');
				if (localStorage.getItem('find_metaList')) {
					that.metaList = that.readCache('find_metaList', []);
				}
				if (localStorage.getItem('find_tagList')) {
					that.tagList = that.readCache('find_tagList', []);
				}
				if (localStorage.getItem('metaList')) {
					that.metaList = that.readCache('metaList', []);
				}
				if (localStorage.getItem('contentsList_' + meta)) {
					that.contentsList = that.readCache('contentsList_' + meta, []);
				}
				if (localStorage.getItem('topContents')) {
					that.topContents = that.readCache('topContents', []);
				}

				if (localStorage.getItem('Topic')) {
					that.Topic = that.readCache('Topic', []);
				}
			},
			toView2(url) {
				var url = 'https://' + url;
				if (!localStorage.getItem('userinfo')) {
					uni.showToast({
						title: '请先登录！',
						icon: 'none'
					});
				} else {
					uni.getStorage({
						key: 'username',
						success(res) {
							uni.navigateTo({
								url: '/pages/user/webview?url=' + url
							})
						},
						fail() {
							uni.showToast({
								icon: 'none',
								title: '请先登录！'
							})
						}
					})
				}

			},
			getSwiper() {
				var that = this;
				var data = {
					"type": "post",
					"isswiper": 1
				}
				that.$Net.request({
					url: that.$API.getContentsList(),
					data: {
						"searchParams": JSON.stringify(that.$API.removeObjectEmptyKey(data)),
						"limit": 8,
						"page": 1,
						"order": "modified"
					},
					header: {
						'Content-Type': 'application/x-www-form-urlencoded'
					},
					method: "get",
					dataType: 'json',
					success: function(res) {

						if (res.data.code == 1) {
							var list = res.data.data;
							var swiper = [];
							if (list.length > 0) {
								for (var i in list) {
									if (list[i].images.length > 0) {
										var arr = {
											cid: list[i].cid,
											type: 'image',
											url: list[i].images[0],
											title: list[i].title,
											intro: that.subText(list[i].text, 20),
										}
										swiper.push(arr);
									}

								}
								that.swiperList = swiper;

							} else {
								that.swiperList = [];
							}
							localStorage.setItem('swiperList', JSON.stringify(that.swiperList));
						}
					},
					fail: function(res) {

					}
				})
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

			getadimg() {
				var that = this;
				uni.request({
					url: that.$API.SMadimg(),
					method: 'GET',
					dataType: "json",
					success(res) {
						that.adimage_sl = res.data.adimage_sl;

						that.swiperList2 = [];

						for (let i = 1; i <= that.adimage_sl; i++) {
							that.swiperList2.push({
								url: res.data['adimage' + i],
								zt: res.data['link_url' + i]
							});
						}
					},
					fail(error) {
						uni.showToast({
							title: '网络请求失败',
							icon: 'none'
						});
						console.error('获取广告图失败:', error);
					}
				})
			},
			getgg() {
				var that = this;
				uni.request({
					url: that.$API.SMgonggao(),
					method: 'GET',
					data: {
						id: 1
					},
					dataType: "json",
					success(res) {
						that.findtop = res.data.findtop;
						that.bannerswitch = res.data.bannerswitch;
						that.hometop = res.data.hometop;
						that.noticeList[0] = res.data.gonggao;
						that.sousuok = res.data.sousuok;
						that.lunbo_of = res.data.lunbo_of;
						that.gonggao_of = res.data.gonggao_of;
						that.top_of = res.data.top_of;
						that.act_of = res.data.act_of;
						that.weburl = res.data.weburl;
						that.gonggaotime = res.data.ggtime;


						console.log(res);
					},
					fail(error) {
						console.log(error);
					}
				})

			},

			goLogin() {
				var that = this;
				uni.navigateTo({
					url: '/pages/user/login'
				});
			},
			getTopList() {
				var that = this;
				if (that.submitStatus5) {
					return false;
				}
				that.submitStatus5 = true;
				var info = {
					"type": "post"
				}
				var token = "";
				if (localStorage.getItem('userinfo')) {
					try {
						var userInfo = JSON.parse(localStorage.getItem('userinfo'));
						token = userInfo && userInfo.token ? userInfo.token : '';
					} catch (error) {
						localStorage.removeItem('userinfo');
					}
				}
				that.$Net.request({
					url: that.$API.getContentsList(),
					data: {
						"searchParams": JSON.stringify(that.$API.removeObjectEmptyKey(info)),
						"limit": 5,
						"page": 1,
						"order": "commentsNum",
						"token": token
					},
					header: {
						'Content-Type': 'application/x-www-form-urlencoded'
					},
					method: "post",
					dataType: 'json',
					success: function(res) {
						if (res.data.code == 1) {
							var list = Array.isArray(res.data.data) ? res.data.data : [];
							that.topList = list;
							localStorage.setItem('topList', JSON.stringify(list));
						}
						that.submitStatus5 = false;
						var timer = setTimeout(function() {
							that.isLoading = 1;
							clearTimeout('timer')
						}, 300)
					},
					fail: function(res) {
						uni.showToast({
							title: "网络不太好哦",
							icon: 'none'
						})
						that.submitStatus5 = false;
						var timer = setTimeout(function() {
							that.isLoading = 1;
							clearTimeout('timer')
						}, 300)
					}
				})
			},
			getTagList() {
				var that = this;
				if (that.submitStatus4) {
					return false;
				}
				that.submitStatus4 = true;
				var data = {
					"type": "tag"
				}
				that.$Net.request({
					url: that.$API.getMetasList(),
					data: {
						"searchParams": JSON.stringify(that.$API.removeObjectEmptyKey(data)),
						"limit": 20,
						"page": 1,
						"order": "count"
					},
					header: {
						'Content-Type': 'application/x-www-form-urlencoded'
					},
					method: "post",
					dataType: 'json',
					success: function(res) {
						if (res.data.code == 1) {
							var list = Array.isArray(res.data.data) ? res.data.data : [];
							that.tagList = list;
							localStorage.setItem('find_tagList', JSON.stringify(list));
						}
						that.submitStatus4 = false;
					},
					fail: function(res) {}
				})
			},

			showModal(e) {
				this.modalName = e.currentTarget.dataset.target
			},
			hideModal(e) {
				this.modalName = null
			},
			
			getTopContents() {
				var that = this;
				var data = {
					"type": "post",
					"istop": 1,
				}
				that.$Net.request({
					url: that.$API.getContentsList(),
					data: {
						"searchParams": JSON.stringify(that.$API.removeObjectEmptyKey(data)),
						"limit": 5,
						"page": 1,
						"order": "modified"
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

								that.topContents = contentsList;
							} else {
								that.topContents = [];
							}
							localStorage.setItem('topContents', JSON.stringify(that.topContents));
						}
					},
					fail: function(res) {}
				})
			},
			getContentsList(isPage) {
				var that = this;
				if (that.submitStatus1) {
					return false;
				}
				that.submitStatus1 = true;
				var data = {
					"type": "post",
					"istop": 0,
				}
				var page = that.page;
				if (isPage) {
					page++;
				}
				that.$Net.request({
					url: that.$API.getContentsList(),
					data: {
						"searchParams": JSON.stringify(that.$API.removeObjectEmptyKey(data)),
						"limit": 5,
						"page": page,
						"order": "created"
					},
					method: "get",
					dataType: 'json',
					success: function(res) {
						that.submitStatus1 = false;
						that.isLoad = 0;
						that.moreText = "加载更多";
						if (!isPage) {
							that.dataLoad = true;
						}
						if (res.data.code == 1) {
							var list = res.data.data;
							if (list.length > 0) {

								var num = res.data.data.length;
								var rand = Math.floor(Math.random() * num);
								var pushAdsInfo = null;
								// #ifdef APP-PLUS || H5
								if (localStorage.getItem('pushAds')) {
									var pushAds = JSON.parse(localStorage.getItem('pushAds'));
									var adsNum = pushAds.length;
									if (adsNum > 0) {
										var adsRand = Math.floor(Math.random() * adsNum);
										pushAdsInfo = that.pushAds[adsRand];
										pushAdsInfo.isAds = 1;
									}
								}
								// #endif
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
									// #ifdef APP-PLUS || H5
									var isAds = Math.round(Math.random());
									if (isAds == 1) {
										if (i == rand && pushAdsInfo != null) {
											contentsList.push(pushAdsInfo);
										}
									}

									// #endif

								}
								var num = contentsList.length;
								if (isPage) {
									that.page++;
									that.contentsList = that.contentsList.concat(contentsList);
								} else {
									that.contentsList = contentsList;
								}


								localStorage.setItem('contentsList_0', JSON.stringify(that.contentsList));
							} else {
								that.moreText = "再怎么找也没有了哦~";
								if (!isPage) {
									that.contentsList = [];
									localStorage.setItem('contentsList_0', JSON.stringify([]));
								}
							}
						}
					},
					fail: function(res) {
						that.submitStatus1 = false;
						that.moreText = "加载更多";
						that.isLoad = 0;
					}
				})
			},
			getQuestionList() {
				var that = this;
				that.$Net.request({
					url: that.$API.qaQuestionList(),
					data: {
						limit: 30,
						page: 1,
						recommended: 1
					},
					method: 'get',
					dataType: 'json',
					success: function(res) {
						if (res.data && res.data.code == 1) {
							that.questionList = that.filterRecommendedQuestions(res.data.data);
							localStorage.setItem('qaRecommendedQuestionList', JSON.stringify(that.questionList));
						}
					}
				})
			},
			openQuestion(item) {
				if (!item || !item.id) return;
				uni.navigateTo({
					url: '/pages/qa/info?id=' + item.id
				})
			},
			getMetaContents(isPage, meta) {
				var that = this;
				if (that.submitStatus2) {
					return false;
				}
				that.submitStatus2 = true;
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
						that.submitStatus2 = false;
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
								that.moreText = "到底啦~";
								if (!isPage) {
									that.contentsList = [];
									localStorage.setItem('contentsList_' + meta, JSON.stringify([]));
								}
							}
						}
					},
					fail: function(res) {
						that.submitStatus2 = false;
						that.moreText = "加载更多";
						that.isLoad = 0;
					}
				})
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
			toForeverblog() {
				var that = this;

				uni.navigateTo({
					url: '/pages/contents/foreverblog'
				});

			},
			goPage(url,isApp) {
				if (!localStorage.getItem('userinfo')&&!isApp) {
					uni.showToast({
						title: '请先登录！',
						icon: 'none'
					});
				} else {
					var that = this;

					uni.navigateTo({
						url: url
					});
				}
			},
			toUsers() {
				var that = this;
				var token;
				if (!localStorage.getItem('token')) {
					uni.showToast({
						title: "请先登录！",
						icon: 'none'
					})
					return false;
				}
				uni.navigateTo({
					url: '/pages/user/userexp'
				});
			},
			toCategoryContents(title, id) {
				var that = this;
				var type = "meta";
				uni.navigateTo({
					url: '/pages/contents/contentlist?title=' + title + "&type=" + type + "&id=" + id
				});
			},
			readAnnouncement() {
				var that = this;
				that.isAnnouncement = false;
				if (that.announcement) localStorage.setItem('isAnnouncement', that.announcement);

			},
			toAllContents() {
				var that = this;
				var type = "all";
				var title = "全部帖子";
				uni.navigateTo({
					url: '/pages/contents/contentlist?title=' + title + "&type=" + type + "&id=0"
				});
			},
			toInfo(data) {
				var that = this;

				uni.navigateTo({
					url: '/pages/contents/info?cid=' + data.cid + "&title=" + data.title
				});
			},
			subText(text, num) {
				if (text.length < null) {
					return text.substring(0, num) + "……"
				} else {
					return text;
				}

			},
			toShop() {
				uni.navigateTo({
					url: '/pages/contents/shop'
				});
			},
			formatNumber(num) {
				return num >= 1e3 && num < 1e4 ? (num / 1e3).toFixed(1) + 'k' : num >= 1e4 ? (num / 1e4).toFixed(1) + 'w' :
					num
			},
			getAnnouncement() {
				var that = this;
				that.$Net.request({
					url: that.$API.GetUpdateUrl(),
					header: {
						'content-type': 'application/json'
					},
					method: 'get',
					success: function(res) {
						that.announcement = res.data && typeof res.data.announcement === 'string'
							? res.data.announcement.trim() : '';
						that.isAnnouncement = Boolean(that.announcement &&
							localStorage.getItem('isAnnouncement') !== that.announcement);
			
					},
					fail: function(res) {
			
					}
				})
			},
			isUpdate(Status) {
				var that = this;
				checkAndroidWgtUpdate().then(function(result) {
					that.wgtVer = result.runtime.version;
					that.versionCode = result.runtime.versionCode;
					if (result.available) {
						that.updateSource = 'wgt';
						that.updatePackage = result.update;
						that.versionTitle = result.update.version || '新版本';
						that.versionIntro = result.update.description || '';
						that.qzgx = result.update.force ? 1 : 0;
						that.Update = 1;
						uni.hideTabBar({ animation: true });
						if (Status) that.openUpdate();
						return;
					}
					that.updateSource = '';
					that.updatePackage = null;
					that.$Net.request({
						url: that.$API.GetUpdateUrl(),
						header: { 'content-type': 'application/json' },
						method: 'get',
						success: function(res) {
							var update = res.data || {};
							var versionCode = Number(update.versionCode);
							that.versionUrl = update.versionUrl || '';
							that.versionTitle = update.version || '';
							that.versionIntro = update.versionIntro || '';
							that.qzgx = update.qzgx === true || update.qzgx === 'true' || Number(update.qzgx) === 1 ? 1 : 0;
							if (Number.isFinite(versionCode) && versionCode > that.versionCode) {
								that.Update = 1;
								uni.hideTabBar({ animation: true });
								if (Status && that.versionUrl) plus.runtime.openURL(that.versionUrl);
							}
						}
					});
				});
			},
			dismissUpdate() {
				if (this.qzgx === 1) return;
				this.Update = 0;
			},
			openUpdate() {
				if (this.updateSource === 'wgt' && this.updatePackage) {
					if (this.updateInstalling) return;
					this.updateInstalling = true;
					uni.showLoading({ title: '正在下载更新', mask: true });
					installAndroidWgt(this.updatePackage, function(progress) {
						uni.showLoading({ title: '正在下载 ' + progress + '%', mask: true });
					}).catch((error) => {
						this.updateInstalling = false;
						uni.hideLoading();
						uni.showModal({ title: '更新失败', content: error.message || 'WGT 安装失败，请稍后重试', showCancel: false });
					});
					return;
				}
				if (!this.versionUrl) {
					uni.showToast({ title: '暂无下载地址', icon: 'none' });
					return;
				}
				plus.runtime.openURL(this.versionUrl);
			},
			toImagetoday() {
				var that = this;

				uni.navigateTo({
					url: '/pages/contents/imagetoday'
				});
			},
			toWebview(url, title) {
				var that = this;
				var token;
				if (!localStorage.getItem('token')) {
					uni.showToast({
						title: "请先登录！",
						icon: 'none'
					})
					return false;
				}
				uni.navigateTo({
					url: '/pages/user/webview?url=' + url + "&title=" + title
				});
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
			goCategory() {
				var that = this;
				uni.navigateTo({
					url: '/pages/contents/allcategory'
				});
			},
			closeUpdate() {
				var that = this;
				that.Update = 0;
				// uni.showTabBar({
				// 	animation: true
				// });
			},
			toRand() {
				var that = this;
				var token;
				if (!localStorage.getItem('token')) {
					uni.showToast({
						title: "请先登录！",
						icon: 'none'
					})
					return false;
				}
				uni.navigateTo({
					url: '/pages/user/renwu'
				});
			},
			toLink(text) {
				var that = this;

				if (!localStorage.getItem('token') || localStorage.getItem('token') == "") {
					uni.showToast({
						title: "请先登录！",
						icon: 'none'
					})
					return false;
				}
				uni.navigateTo({
					url: text
				});
			},
			scanLogin(text) {
				var that = this;
				var token;
				if (!localStorage.getItem('token')) {
					uni.showToast({
						title: "请先登录！",
						icon: 'none'
					})
					return false;
				}
				uni.navigateTo({
					url: '/pages/user/scan?text=' + text
				});
			},
			//自定义启动图广告相关
			toStartUrl() {
				if (localStorage.getItem('appStart')) {
					var imgData = JSON.parse(localStorage.getItem('appStart'));
					//如果线上的图片与本地缓存图片相同，就不再进行下载
					if (imgData.url) {
						var url = imgData.url;
						var type = imgData.urltype;
						if (url.indexOf("http") != -1) {
							// #ifdef APP-PLUS
							if (type == 1) {
								plus.runtime.openURL(url);
							}
							if (type == 0) {
								plus.runtime.openWeb(url);
							}
							// #endif
							// #ifdef H5
							window.open(url)
							// #endif

						} else {
							uni.navigateTo({
								url: url
							});
						}

					} else {
						return false
					}

				} else {
					return false
				}
			},
			toStart() {
				var that = this;
				that.isStart = true;
			},
			appStartImg() {

				var that = this;
				// #ifdef APP-PLUS
				if (localStorage.getItem('appStart')) {
					var imgData = JSON.parse(localStorage.getItem('appStart'));

					if (!imgData.localUrl || imgData.localUrl == "") {
						console.log("启动图文件本地不存在");
						localStorage.removeItem('appStart');
						that.isStart = true;
						return false;
					}
					var localUrl = imgData.localUrl;
					//在请求之前，先为了性能载入上次图片
					plus.io.resolveLocalFileSystemURL(imgData.localUrl, function(entry) {
						console.log("启动图文件本地存在");
						imgData.localUrl = localUrl;
						that.startImg = imgData;

						that.isStart = false;
					}, function(e) {
						console.log("启动图文件本地不存在");
						localStorage.removeItem('appStart');
						that.isStart = true;
					});
				} else {
					console.log("启动图未缓存")
				}
				if (localStorage.getItem('startAds')) {
					var data = JSON.parse(localStorage.getItem('startAds'));
					var adsNum = data.length;
					if (adsNum > 0) {

						var adsRand = Math.floor(Math.random() * adsNum);
						var appStartPic = data[adsRand].img;
						if (appStartPic != "") {
							appStartPic = appStartPic.replace(/[\r\n]/g, "");
							var imgData = data[adsRand];
							imgData.appStartPic = appStartPic;
							that.Download(imgData);
						}
					} else {
						console.log("广告信息不存在，删除缓存");
						localStorage.removeItem('appStart');
						that.isStart = true;
					}

				}
				// #endif
			},
			Download(startImg) {
				var that = this;
				// #ifdef APP-PLUS
				var url = startImg.appStartPic;
				if (localStorage.getItem('appStart')) {
					var imgData = JSON.parse(localStorage.getItem('appStart'));
					//如果线上的图片与本地缓存图片相同，就不再进行下载
					if (url == imgData.appStartPic) {
						console.log("启动图不更新");
						//但是链接可能变化，所以需要载入缓存
						var oldStartImg = imgData;
						localStorage.setItem('appStart', JSON.stringify(oldStartImg));
						return false;
					}
				}
				uni.downloadFile({
					url: url, //下载地址接口返回
					success: (data) => {
						if (data.statusCode === 200) {
							//文件保存到本地
							uni.saveFile({
								tempFilePath: data.tempFilePath, //临时路径
								success: function(res) {
									// uni.showToast({
									// 	icon: 'none',
									// 	mask: true,
									// 	title: '文件已保存：' + res.savedFilePath, //保存路径
									// 	duration: 3000,
									// });

									startImg.localUrl = res.savedFilePath;
									localStorage.setItem('appStart', JSON.stringify(startImg));
									console.log("启动图已更新" + startImg.localUrl);

									that.startImg = startImg;
								}
							});
						}
					},
					fail: (err) => {
						console.log(err);
						// uni.showToast({
						// 	icon: 'none',
						// 	mask: true,
						// 	title: '失败请重新下载',
						// });
					},
				});
				// #endif

			},
		},

		// #ifdef APP-PLUS
		components: {
			waves,
			Tabbar,
			metas,
			QixiEasterEgg
		},
		// #endif

		// #ifdef H5

		components: {
			waves,
			QixiEasterEgg,
			'metas': {
				// 组件选项
			}
		},
		// #endif

		// #ifdef MP
		components: {
			waves,
			QixiEasterEgg,
			'metas': {
				// 组件选项
			}
		},
		// #endif
	}
</script>

<style scoped>
	.app-update {
		position: fixed;
		inset: 0;
		z-index: 2000;
		display: flex;
		align-items: center;
		justify-content: center;
		padding: 32rpx;
		box-sizing: border-box;
	}

	.app-update-bg {
		position: absolute;
		inset: 0;
		background: rgba(15, 26, 28, 0.58);
	}

	.app-update-main {
		position: relative;
		z-index: 1;
		width: min(620rpx, 100%);
		padding: 38rpx 34rpx 30rpx;
		border: 1rpx solid rgba(255, 255, 255, 0.7);
		border-radius: 18rpx;
		background: #fff;
		box-shadow: 0 24rpx 70rpx rgba(12, 34, 36, 0.28);
		box-sizing: border-box;
	}

	.app-update-title {
		font-size: 38rpx;
		font-weight: 700;
		line-height: 1.3;
		text-align: center;
		color: #1d3537;
	}

	.app-update-version {
		margin-top: 12rpx;
		font-size: 26rpx;
		text-align: center;
		color: #16827e;
	}

	.app-update-content {
		max-height: 360rpx;
		margin: 28rpx 0;
		padding: 22rpx;
		overflow: auto;
		border-radius: 10rpx;
		background: #f1f7f6;
		font-size: 28rpx;
		line-height: 1.65;
		color: #405452;
	}

	.app-update-btn {
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 14rpx;
	}

	.app-update-btn button {
		width: 100%;
		margin: 0;
	}

	.app-update-later {
		background: transparent;
		color: #71817f;
	}

	.hero-subtitle-line {
		display: flex;
		align-items: center;
		gap: 12rpx;
	}

	.qixi-secret-hint {
		display: inline-flex;
		width: 34rpx;
		height: 34rpx;
		align-items: center;
		justify-content: center;
		color: #d94f78;
		font-size: 25rpx;
		text-shadow: 0 0 14rpx rgba(255, 255, 255, 0.74);
		animation: qixiHintGlow 1.8s ease-in-out infinite alternate;
	}

	@keyframes qixiHintGlow {
		from { opacity: 0.62; transform: rotate(-8deg) scale(0.92); }
		to { opacity: 1; transform: rotate(8deg) scale(1.08); }
	}

	@media (prefers-reduced-motion: reduce) {
		.qixi-secret-hint { animation: none; }
	}

	.qa-home-section {
		margin: 22rpx 0 8rpx;
	}

	.qa-home-heading {
		display: flex;
		align-items: baseline;
		gap: 18rpx;
		padding: 8rpx 22rpx 18rpx;
		color: var(--campus-card-text, #20302d);
		font-size: 32rpx;
		font-weight: 600;
	}

	.qa-home-subtitle {
		color: var(--campus-card-muted, #77837f);
		font-size: 23rpx;
		font-weight: 400;
	}

	.qa-home-list {
		overflow: hidden;
		border: 1rpx solid var(--campus-card-border, #e9eeec);
		border-radius: 16rpx;
		background: var(--campus-card, #ffffff);
	}

	.tab-wrap-index {
		color: #454545;
		position: relative;
		z-index: 1;
	}

	.tab-wrap-index::after {
		position: absolute;
		border-radius: 50px;
		color: #797979;
		right: 5%;
		bottom: 6rpx;
		z-index: -1;
		display: block;
		content: "";
		width: 100%;
		height: 13rpx;
		background-color: #3cc9a4;
	}

	.square-box {
		font-weight: bold;
		font-size: 17px;
	}

	.square-box2 {
		font-weight: bold;
		font-size: 14px;
	}

	.square-box,
	.square-box2 {
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

	.tab-wrap-index {
		color: #454545;
		position: relative;
		z-index: 1;
	}

	.tab-wrap-index::after {
		position: absolute;
		border-radius: 50px;
		color: #797979;
		right: 5%;
		bottom: 6rpx;
		z-index: -1;
		display: block;
		content: "";
		width: 100%;
		height: 13rpx;
		background-color: #3cc9a4;
	}

	.square-box {
		font-weight: bold;
		font-size: 32upx;
	}

	.square-box2 {
		font-weight: bold;
		font-size: 28upx;
	}

	.square-box,
	.square-box2 {
		transition: font-size 0.5s ease-in-out
	}

	.text-content {
		overflow: hidden;
		-webkit-line-clamp: 3;
		text-overflow: ellipsis;
		display: -webkit-box;
		-webkit-box-orient: vertical;
	}

	.font-size-small {
		font-size: small;
	}

	::v-deep .tn-row-notice-class {
		border-radius: 8rpx;
	}

	::v-deep .uni-swiper-slides uni-swiper-item {
		padding: 0;
	}

	.swiper-container {
		margin-bottom: 26rpx;
		box-shadow: 0 3px 5px 1px #d5d5d5;
	}

	.extra-count {
		position: absolute;
		background-color: #00000078;
		color: white;
		font-size: 40upx;
		border-radius: 20upx;
		font-weight: bold;
		width: 100%;
		height: 100%;
		display: flex;
		align-items: center;
		justify-content: center;
	}

	.grid.grid-square>uni-view {
		border-radius: 20upx;
	}

	.tags1 {
		padding: 10px;
	}

	.tags {
		display: flex;
		white-space: nowrap;
		overflow-x: auto;
	}

	.tags-box {
		display: inline-block;
		margin: 5px;
		border-radius: 10px;
	}

	.campus-home {
		position: relative;
		isolation: isolate;
		min-height: 100vh;
		min-height: 100dvh;
		padding-bottom: 0;
		background: transparent !important;
		overflow-x: hidden;
	}

	/* Keep the modal above uni-app's root-level H5 tabbar while it is open. */
	.campus-home.is-qixi-open {
		z-index: 1001;
	}

	.home-ambient {
		position: fixed;
		inset: 0;
		z-index: -2;
		overflow: hidden;
		background: #c1dde0;
		contain: strict;
		pointer-events: none;
	}

	.home-ambient-layer,
	.home-ambient-sheen {
		position: absolute;
		inset: 0;
	}

	.home-ambient-layer {
		opacity: 0;
		background-size: cover;
		background-position: center;
		backface-visibility: hidden;
		transition: opacity 720ms cubic-bezier(0.22, 1, 0.36, 1);
	}

	.home-ambient.is-transitioning .home-ambient-layer {
		will-change: opacity;
	}

	.home-ambient-layer.is-visible {
		opacity: 1;
	}

	.home-ambient-sheen {
		content: '';
		background:
			radial-gradient(circle at 82% 8%, rgba(255, 255, 255, 0.22) 0%, rgba(255, 255, 255, 0) 32%),
			linear-gradient(180deg, rgba(255, 255, 255, 0.07) 0%, rgba(236, 247, 247, 0.02) 58%, rgba(236, 247, 247, 0.1) 100%);
		pointer-events: none;
	}

	.home-hero {
		position: relative;
		z-index: 1;
		display: flex;
		align-items: center;
		min-height: 304rpx;
		padding-right: 32rpx;
		padding-bottom: 42rpx;
		padding-left: 32rpx;
		box-sizing: border-box;
	}

	.hero-main {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 24rpx;
		width: 100%;
	}

	.hero-copy {
		display: flex;
		flex: 1 1 38.2%;
		flex-direction: column;
		min-width: 210rpx;
	}

	.hero-greeting {
		font-size: 58rpx;
		font-weight: 700;
		line-height: 1.12;
		color: var(--weather-foreground, #17272a);
		letter-spacing: 0;
		transition: color 520ms ease;
	}

	.hero-copy.has-qixi-secret .hero-greeting {
		color: #d94f78;
	}

	.hero-subtitle {
		margin-top: 24rpx;
		font-size: 28rpx;
		line-height: 1.55;
		color: var(--weather-muted, rgba(23, 39, 42, 0.82));
		transition: color 520ms ease;
	}

	.hero-copy.has-qixi-secret .hero-subtitle {
		color: #a94465;
	}

	.hero-actions {
		display: flex;
		flex: 0 0 auto;
		align-items: center;
		gap: 12rpx;
		max-width: 61.8%;
	}

	.weather-pill,
	.hero-icon-button {
		display: flex;
		align-items: center;
		justify-content: center;
		height: 64rpx;
		border: 1rpx solid var(--weather-chrome-border, rgba(255, 255, 255, 0.8));
		background: var(--weather-chrome, rgba(255, 255, 255, 0.2));
		box-shadow: inset 0 1rpx 0 rgba(255, 255, 255, 0.45);
	}

	.weather-pill {
		flex: 0 0 206rpx;
		width: 206rpx;
		gap: 8rpx;
		padding: 0 14rpx;
		border-radius: 34rpx;
		box-sizing: border-box;
		font-size: 25rpx;
		color: var(--weather-foreground, #203034);
		white-space: nowrap;
		transition: transform 180ms ease, background-color 180ms ease, color 520ms ease, border-color 520ms ease;
	}

	.weather-pill:active {
		transform: scale(0.96);
		background: var(--weather-chrome-active, rgba(255, 255, 255, 0.36));
	}

	.weather-symbol {
		font-size: 29rpx;
	}

	.hero-icon-button {
		width: 64rpx;
		border-radius: 50%;
		font-size: 32rpx;
		color: var(--weather-foreground, #203034);
		transition: transform 180ms ease, background-color 180ms ease, color 520ms ease, border-color 520ms ease;
	}

	.hero-icon-button:active {
		transform: scale(0.92);
		background: var(--weather-chrome-active, rgba(255, 255, 255, 0.36));
	}

	.hero-user-button .cu-avatar {
		width: 54rpx;
		height: 54rpx;
		border: 0;
		box-shadow: none;
	}

	.home-stage {
		position: relative;
		z-index: 2;
		min-height: calc(100vh - 304rpx);
		min-height: calc(100dvh - 304rpx);
		padding: 24rpx 16rpx calc(180rpx + env(safe-area-inset-bottom));
		border-top: 2rpx solid rgba(255, 255, 255, 0.72);
		border-radius: 50rpx 50rpx 0 0;
		background-color: rgba(250, 254, 254, 0.68);
		box-shadow: 0 -16rpx 70rpx rgba(39, 74, 78, 0.13);
		box-sizing: border-box;
		animation: stageRise 420ms cubic-bezier(0.22, 1, 0.36, 1) both;
	}

	.home-mode-switch {
		display: flex;
		width: 38.2%;
		min-width: 252rpx;
		max-width: 320rpx;
		height: 64rpx;
		margin: 0 auto 24rpx;
		padding: 6rpx;
		border: 1rpx solid rgba(255, 255, 255, 0.78);
		border-radius: 34rpx;
		background: rgba(255, 255, 255, 0.2);
		box-sizing: border-box;
	}

	.home-mode-item {
		display: flex;
		flex: 1;
		align-items: center;
		justify-content: center;
		gap: 7rpx;
		border-radius: 28rpx;
		font-size: 25rpx;
		font-weight: 500;
		color: rgba(37, 56, 59, 0.64);
		transition: color 240ms ease, background-color 240ms ease, box-shadow 240ms ease, transform 240ms ease;
	}

	.home-mode-item.is-active {
		color: var(--campus-primary, #237c74);
		background: rgba(255, 255, 255, 0.78);
		box-shadow: 0 6rpx 18rpx rgba(34, 76, 73, 0.1);
	}

	.home-mode-item:active {
		transform: scale(0.96);
	}

	.home-panel {
		width: 100%;
	}

	.panel-enter {
		animation: panelFade 420ms cubic-bezier(0.22, 1, 0.36, 1) both;
	}

	.campus-home .swiper-container {
		display: block;
		width: calc(100% - 16rpx) !important;
		height: auto !important;
		min-height: 0 !important;
		max-height: none !important;
		aspect-ratio: 1.618 / 1;
		margin: 0 auto 24rpx;
		border: 1rpx solid rgba(255, 255, 255, 0.68);
		border-radius: 28rpx !important;
		background: rgba(255, 255, 255, 0.34);
		box-shadow: 0 14rpx 36rpx rgba(44, 74, 78, 0.1);
		box-sizing: border-box;
		overflow: hidden;
	}

	.campus-home .swiper-box,
	.campus-home .swiper-box image,
	.campus-home .swiper-box video {
		display: block;
		width: 100%;
		height: 100%;
		border-radius: 28rpx !important;
	}

	/* Mobile polish: keep the first screen calm and leave room for the dock. */
	@media (max-width: 759px) {
		/* H5 does not provide the native status-bar inset used by App-plus. */
		/* #ifdef H5 */
		.campus-home .home-hero {
			padding-top: 32px !important;
			padding-bottom: 28rpx;
		}
		/* #endif */

		.hero-greeting {
			font-size: 52rpx;
			line-height: 1.14;
		}

		.hero-subtitle {
			margin-top: 14rpx;
			font-size: 25rpx;
		}

		.home-stage {
			padding-top: 20rpx;
			padding-bottom: calc(220rpx + env(safe-area-inset-bottom));
			border-radius: 48rpx 48rpx 0 0;
		}

		.campus-home .swiper-container {
			aspect-ratio: 1.618 / 1;
			margin-bottom: 18rpx;
			border-radius: 28rpx !important;
		}

		.campus-home .swiper-box,
		.campus-home .swiper-box image,
		.campus-home .swiper-box video {
			border-radius: 28rpx !important;
		}

		.campus-home .home-shortcuts {
			margin-right: 10rpx;
			margin-bottom: 18rpx;
			margin-left: 10rpx;
			padding: 18rpx 10rpx 16rpx;
			border: 1rpx solid rgba(255, 255, 255, 0.9) !important;
			border-radius: 28rpx !important;
			background: rgba(232, 240, 239, 0.94) !important;
			box-shadow: 0 8rpx 22rpx rgba(19, 42, 43, 0.14) !important;
		}

		.campus-home .home-shortcuts .index-sort-main {
			padding: 14rpx 0 16rpx;
		}

		.campus-home .home-shortcuts .index-sort-i {
			width: 84rpx;
			height: 84rpx;
			margin-bottom: 14rpx;
			border-radius: 24rpx !important;
			line-height: 84rpx;
			font-size: 40rpx;
		}

		.campus-home .home-shortcuts .index-sort-text {
			font-size: 28rpx;
			font-weight: 600;
			color: #35484a;
		}
	}

	.home-shortcuts,
	.home-notice,
	.discovery-card {
		margin: 0 8rpx 24rpx;
		border: 2rpx solid rgba(255, 255, 255, 0.82) !important;
		border-radius: 28rpx !important;
		background:
			linear-gradient(135deg, rgba(255, 255, 255, 0.66), rgba(221, 249, 250, 0.58) 48%, rgba(248, 255, 251, 0.54)) !important;
		box-shadow: 0 12rpx 30rpx rgba(44, 74, 78, 0.07) !important;
	}

	.home-shortcuts {
		padding: 18rpx 10rpx 16rpx;
		overflow: visible !important;
	}

	.home-shortcuts .index-sort-main {
		padding: 10rpx 0;
	}

	.home-shortcuts .index-sort-i {
		width: 66rpx;
		height: 66rpx;
		margin: 0 auto 12rpx;
		border-radius: 20rpx !important;
		line-height: 66rpx;
		color: #fff;
		box-shadow: inset 0 1rpx 0 rgba(255, 255, 255, 0.45), 0 8rpx 18rpx rgba(28, 86, 89, 0.08) !important;
		transition: transform 180ms ease;
	}

	.home-shortcuts .index-sort-text {
		font-size: 25rpx;
		color: rgba(31, 46, 50, 0.86);
	}

	.home-shortcuts .index-sort-main:active .index-sort-i {
		transform: translateY(2rpx) scale(0.92);
	}

	.shortcut-green { background: #4a9b85 !important; }
	.shortcut-blue { background: #5c93bf !important; }
	.shortcut-violet { background: #8079ad !important; }
	.shortcut-coral { background: #c88778 !important; }

	.home-notice {
		display: flex;
		align-items: center;
		min-height: 72rpx;
		padding: 8rpx 18rpx;
		box-sizing: border-box;
	}

	.notice-badge {
		display: flex;
		flex: 0 0 auto;
		align-items: center;
		gap: 8rpx;
		padding: 7rpx 13rpx;
		border-radius: 15rpx;
		background: #c9853a;
		font-size: 22rpx;
		font-weight: 600;
		color: #fff;
		box-shadow: 0 6rpx 14rpx rgba(255, 102, 80, 0.14);
	}

	.home-notice marquee {
		min-width: 0;
		margin-left: 16rpx;
		background: transparent !important;
		font-size: 25rpx;
		color: #26383b;
	}

	.campus-home .ads-banner {
		margin: 0 8rpx 24rpx;
		border: 2rpx solid rgba(255, 255, 255, 0.72);
		border-radius: 28rpx !important;
		box-shadow: 0 14rpx 36rpx rgba(44, 74, 78, 0.08);
		overflow: hidden;
	}

	.home-feed {
		background: transparent !important;
	}

	.campus-home .load-more {
		width: auto;
		min-height: 78rpx;
		height: auto;
		margin: 18rpx 8rpx 0;
		padding: 20rpx 28rpx;
		border: 1rpx solid rgba(35, 123, 101, 0.12);
		border-radius: 24rpx;
		background: rgba(255, 255, 255, 0.7);
		color: #526663;
		box-sizing: border-box;
		line-height: 38rpx;
		transition: transform 180ms ease, background-color 420ms ease, border-color 420ms ease, color 420ms ease;
	}

	.campus-home .load-more:active {
		transform: scale(0.985);
	}

	::v-deep .home-feed .article-item-shell {
		padding: 0 8rpx;
	}

	::v-deep .home-feed .cu-card.article.no-card > .cu-item {
		border: 2rpx solid rgba(255, 255, 255, 0.86);
		border-radius: 30rpx !important;
		background:
			linear-gradient(135deg, rgba(255, 236, 246, 0.72) 0%, rgba(213, 246, 250, 0.88) 46%, rgba(197, 232, 218, 0.78) 100%);
		box-shadow: 0 18rpx 42rpx rgba(37, 87, 91, 0.12);
	}

	.discovery-card {
		padding: 8rpx 10rpx 16rpx;
		overflow: hidden;
	}

	.qa-discovery-entry {
		margin: 0 8rpx 24rpx;
		overflow: hidden;
		border: 2rpx solid rgba(255, 255, 255, 0.72);
		border-radius: 28rpx;
		background: rgba(255, 255, 255, 0.62);
		box-shadow: 0 12rpx 30rpx rgba(44, 74, 78, 0.08);
	}

	.qa-discovery-heading {
		display: flex;
		align-items: center;
		justify-content: space-between;
		padding: 22rpx 24rpx 8rpx;
	}

	.qa-discovery-heading > view {
		display: flex;
		align-items: center;
		gap: 12rpx;
	}

	.qa-discovery-mark {
		padding: 4rpx 10rpx;
		border-radius: 10rpx;
		background: #328d79;
		color: #f1fffa;
		font-size: 20rpx;
		font-weight: 700;
	}

	.qa-discovery-title {
		color: #213437;
		font-size: 30rpx;
		font-weight: 700;
	}

	.qa-discovery-more {
		color: #607772;
		font-size: 22rpx;
	}

	.qa-discovery-entry .qa-card {
		border-bottom: 0;
		background: transparent;
	}

	.discovery-card .cu-bar {
		background: transparent !important;
	}

	.discovery-card .data-box-title,
	.section-heading > text {
		font-size: 30rpx;
		font-weight: 700;
		color: #213437;
	}

	.hot-dot {
		width: 12rpx;
		height: 12rpx;
		margin-right: 12rpx;
		border-radius: 50%;
		background: #ff624b;
	}

	.section-heading {
		display: flex;
		align-items: center;
		justify-content: space-between;
		margin: 32rpx 26rpx 20rpx;
	}

	.home-compose-button {
		bottom: calc(22rpx + env(safe-area-inset-bottom));
		left: 18rpx;
		right: auto;
		z-index: 1001;
	}

	.home-compose-button .round-button {
		width: 104rpx;
		height: 104rpx;
		border: 2rpx solid rgba(255, 255, 255, 0.82);
		background: rgba(232, 249, 250, 0.62) !important;
		box-shadow: 0 14rpx 40rpx rgba(26, 94, 107, 0.22) !important;
		backdrop-filter: blur(18px) saturate(1.5);
		-webkit-backdrop-filter: blur(18px) saturate(1.5);
		font-size: 38rpx;
		color: #16827e;
		transition: transform 180ms ease;
	}

	.home-compose-button .round-button:active {
		transform: scale(0.9) rotate(-4deg);
	}

	.publish-modal .cu-dialog {
		background: transparent !important;
		border: 0 !important;
		box-shadow: none !important;
	}

	.publish-sheet {
		margin: 0 24rpx calc(138rpx + env(safe-area-inset-bottom));
		padding: 30rpx;
		border: 2rpx solid rgba(255, 255, 255, 0.78);
		border-radius: 30rpx;
		background: rgba(245, 250, 250, 0.78);
		box-shadow: 0 24rpx 70rpx rgba(23, 55, 58, 0.24);
		backdrop-filter: blur(28px) saturate(1.4);
		-webkit-backdrop-filter: blur(28px) saturate(1.4);
		animation: sheetIn 340ms cubic-bezier(0.22, 1, 0.36, 1) both;
	}

	.publish-sheet-title {
		margin-bottom: 24rpx;
		font-size: 30rpx;
		font-weight: 700;
		color: #213437;
	}

	.publish-sheet-actions {
		display: flex;
		gap: 18rpx;
	}

	.publish-action {
		display: flex;
		flex: 1;
		align-items: center;
		gap: 16rpx;
		padding: 18rpx;
		border-radius: 22rpx;
		background: rgba(255, 255, 255, 0.7);
		font-size: 27rpx;
		font-weight: 600;
		color: #26383b;
		transition: transform 180ms ease, background-color 180ms ease;
	}

	.publish-action:active {
		transform: scale(0.96);
		background: #fff;
	}

	.publish-action image {
		width: 68rpx;
		height: 68rpx;
	}


	/* Liaocheng No.1 school color layer: richer green, gold accent, calm night mode. */
	.campus-home {
		--school-green: #00843d;
		--school-green-bright: #00a85a;
		--school-gold: #d8af3f;
		--school-red: #e60012;
		--campus-stage: rgba(250, 254, 254, 0.74);
		--campus-card: rgba(255, 255, 255, 0.9);
		--campus-card-border: rgba(255, 255, 255, 0.78);
		--campus-card-text: #213437;
		--campus-card-muted: #26383b;
	}

	.home-stage {
		background-color: var(--campus-stage, rgba(250, 254, 254, 0.74));
		transition: background-color 520ms ease, border-color 520ms ease, box-shadow 520ms ease;
	}

	.home-mode-item.is-active {
		color: var(--school-green, #00843d);
		box-shadow: 0 6rpx 18rpx rgba(0, 132, 61, 0.14);
	}

	.home-shortcuts,
	.home-notice,
	.discovery-card {
		border-color: rgba(255, 255, 255, 0.82) !important;
		background:
			linear-gradient(135deg, rgba(255, 255, 255, 0.66), rgba(221, 249, 250, 0.58) 48%, rgba(248, 255, 251, 0.54)) !important;
	}

	.shortcut-green { background: linear-gradient(145deg, #55c993, var(--school-green, #00843d)) !important; }
	.shortcut-blue { background: linear-gradient(145deg, #62c8ff, #2896e8) !important; }
	.shortcut-violet { background: linear-gradient(145deg, #a895ee, #7563d9) !important; }
	.shortcut-coral { background: linear-gradient(145deg, #ffac86, #e77a5f) !important; }

	.notice-badge {
		background: linear-gradient(135deg, #ff6450, #ff8870);
	}

	.home-notice marquee,
	.discovery-card .more,
	.discovery-card .tags-box,
	.discovery-card .top-box {
		color: var(--campus-card-muted, #26383b) !important;
	}

	::v-deep .home-feed .cu-card.article.no-card > .cu-item {
		border-color: rgba(255, 255, 255, 0.86);
		background:
			linear-gradient(135deg, rgba(255, 236, 246, 0.72) 0%, rgba(213, 246, 250, 0.88) 46%, rgba(197, 232, 218, 0.78) 100%);
	}

	.discovery-card .data-box-title,
	.section-heading > text {
		color: var(--campus-card-text, #213437);
	}

	.hot-dot {
		background: var(--school-gold, #d8af3f);
	}

	.campus-home.campus-night .home-ambient-sheen {
		background: linear-gradient(180deg, rgba(255, 255, 255, 0.025) 0%, rgba(21, 25, 27, 0.1) 58%, rgba(21, 25, 27, 0.34) 100%);
	}

	.campus-home.campus-night .home-stage {
		border-top-color: rgba(226, 232, 230, 0.08);
		box-shadow: 0 -12rpx 42rpx rgba(0, 0, 0, 0.2);
	}

	.campus-home.campus-night .home-mode-switch {
		border-color: rgba(226, 232, 230, 0.1);
		background: #202527;
	}

	.campus-home.campus-night .home-mode-item {
		color: #99a39f;
	}

	.campus-home.campus-night .home-mode-item.is-active {
		color: #edf0ef;
		background: #303739;
		box-shadow: none;
	}

	.campus-home.campus-night .home-shortcuts,
	.campus-home.campus-night .home-notice,
	.campus-home.campus-night .discovery-card,
	.campus-home.campus-night ::v-deep .home-feed .cu-card.article.no-card > .cu-item {
		box-shadow: 0 10rpx 28rpx rgba(0, 0, 0, 0.18) !important;
	}

	.campus-home.campus-night .home-shortcuts {
		border-color: rgba(226, 232, 230, 0.14) !important;
		background: #263334 !important;
	}

	.campus-home.campus-night .home-shortcuts .index-sort-text {
		color: #dce8e3 !important;
	}

	.campus-home.campus-night .home-notice {
		border-color: rgba(222, 232, 228, 0.14) !important;
		background: #202729 !important;
	}

	.campus-home.campus-night .home-notice .notice-badge {
		background: #a9473b !important;
		color: #ffffff !important;
	}

	.campus-home.campus-night .home-notice marquee {
		color: #edf2f0 !important;
	}

	.campus-home.campus-night .discovery-card .cu-bar,
	.campus-home.campus-night .discovery-card .bg-white {
		background: transparent !important;
		color: var(--campus-card-text, #edf0ef) !important;
	}

	.campus-home.campus-night .discovery-card,
	.campus-home.campus-night .qa-discovery-entry {
		border-color: rgba(226, 232, 230, 0.12) !important;
		background: #202728 !important;
		box-shadow: 0 10rpx 28rpx rgba(0, 0, 0, 0.18) !important;
	}

	.campus-home.campus-night .discovery-card .data-box-title,
	.campus-home.campus-night .qa-discovery-title,
	.campus-home.campus-night .section-heading > text {
		color: #edf3f0 !important;
	}

	.campus-home.campus-night .discovery-card .more,
	.campus-home.campus-night .qa-discovery-more,
	.campus-home.campus-night .section-heading .more {
		color: #b8c8c1 !important;
	}

	.campus-home.campus-night .discovery-card .tags-box {
		border-color: rgba(111, 205, 191, 0.35) !important;
		background: #2a3b38 !important;
		color: #d3e2dc !important;
	}

	.campus-home.campus-night .discovery-card .top-box {
		border-color: rgba(226, 232, 230, 0.1) !important;
		color: #d3dfda !important;
	}

	.campus-home.campus-night .discovery-card .top-box > text {
		color: #62c5a7 !important;
	}

	.campus-home.campus-night .load-more {
		border-color: rgba(226, 232, 230, 0.09);
		background: #212628;
		color: #a1aaa7;
		box-shadow: none;
	}

	/* Homepage posts now follow the reference's soft gradient information-card style. */
	::v-deep .home-feed .article-item-shell.is-home-feed {
		padding-right: 10rpx;
		padding-left: 10rpx;
	}

	::v-deep .home-feed .article-item-shell.is-home-feed .cu-card.article.no-card > .cu-item {
		border: 2rpx solid rgba(255, 255, 255, 0.88);
		border-radius: 30rpx !important;
		background:
			linear-gradient(135deg, rgba(255, 236, 246, 0.72) 0%, rgba(213, 246, 250, 0.88) 46%, rgba(197, 232, 218, 0.78) 100%);
		box-shadow: 0 18rpx 42rpx rgba(37, 87, 91, 0.12);
	}

	::v-deep .home-feed .article-item-shell.is-home-feed .home-article-card {
		background:
			radial-gradient(circle at 8% 16%, rgba(255, 255, 255, 0.48), rgba(255, 255, 255, 0) 34%),
			linear-gradient(135deg, rgba(255, 255, 255, 0.1), rgba(255, 255, 255, 0));
	}

	.campus-home.campus-night ::v-deep .home-feed .article-item-shell.is-home-feed .cu-card.article.no-card > .cu-item {
		border-color: rgba(226, 232, 230, 0.16);
		background:
			linear-gradient(135deg, rgba(48, 54, 58, 0.94) 0%, rgba(36, 56, 58, 0.94) 52%, rgba(44, 64, 57, 0.94) 100%);
		box-shadow: 0 12rpx 30rpx rgba(0, 0, 0, 0.2) !important;
	}

	.campus-home.campus-night ::v-deep .home-feed .article-item-shell.is-home-feed .home-article-title {
		color: #eef4f1 !important;
	}

	.campus-home.campus-night ::v-deep .home-feed .article-item-shell.is-home-feed .home-article-desc,
	.campus-home.campus-night ::v-deep .home-feed .article-item-shell.is-home-feed .home-article-clock,
	.campus-home.campus-night ::v-deep .home-feed .article-item-shell.is-home-feed .home-article-meta {
		color: #aebbb7 !important;
	}

	.campus-home.campus-night ::v-deep .home-feed .article-item-shell.is-home-feed .home-article-footer {
		border-top-color: rgba(226, 232, 230, 0.12);
	}

	.campus-home.campus-night ::v-deep .home-feed .article-item-shell.is-home-feed .home-read-link {
		color: #7fc4ff;
	}

	@media (max-width: 759px) {
		.campus-home.campus-night ::v-deep .home-feed .article-item-shell.is-home-feed .cu-card.article.no-card > .cu-item {
			border-radius: 30rpx !important;
			border-color: rgba(132, 177, 169, 0.22);
			background: linear-gradient(135deg, #303d3e 0%, #263b3b 56%, #2b433c 100%);
			box-shadow: 0 12rpx 30rpx rgba(0, 0, 0, 0.2) !important;
		}

		.campus-home.campus-night ::v-deep .home-feed .article-item-shell.is-home-feed .home-article-card {
			background: transparent;
		}
	}

	@keyframes stageRise {
		from { opacity: 0; transform: translateY(34rpx); }
		to { opacity: 1; transform: translateY(0); }
	}

	@keyframes panelFade {
		from { opacity: 0; transform: translateY(14rpx); }
		to { opacity: 1; transform: translateY(0); }
	}

	@keyframes sheetIn {
		from { opacity: 0; transform: translateY(36rpx) scale(0.98); }
		to { opacity: 1; transform: translateY(0) scale(1); }
	}

	@media (max-width: 370px) {
		.hero-main { align-items: flex-start; flex-direction: column; gap: 22rpx; }
		.hero-actions { width: 100%; max-width: none; }
		.home-hero { min-height: 348rpx; }
		.home-stage { min-height: calc(100vh - 348rpx); min-height: calc(100dvh - 348rpx); }
		.weather-pill { margin-right: auto; }
	}

	@media (max-height: 700px) {
		.home-hero { min-height: 270rpx; padding-bottom: 28rpx; }
		.home-stage { min-height: calc(100vh - 270rpx); min-height: calc(100dvh - 270rpx); padding-top: 20rpx; }
	}

	@media (prefers-reduced-motion: reduce) {
		.home-ambient,
		.home-ambient-layer,
		.home-stage,
		.panel-enter,
		.publish-sheet { animation: none; transition: none; }
	}
</style>
