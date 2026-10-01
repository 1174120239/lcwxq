<template>
	<view class="reading-page" :class="['layout-' + layout, 'theme-' + theme]">
		<view v-if="heroImage" class="ambient" :style="{backgroundImage: 'url(' + heroImage + ')'}"/>
		<view class="nav"><text @tap="back" class="cuIcon-back"/><text>{{ journalName || '见字' }}</text><text @tap="shareArticle" class="cuIcon-share"/></view>
		<view class="header"><text class="kicker">{{ journalName || 'CAMPUS EDITIONS' }}</text><text class="title">{{ title }}</text><text v-if="subtitle" class="subtitle">{{ subtitle }}</text><text class="byline">{{ authorName }} · {{ displayDate(created) }} · {{ readingMinutes }} 分钟阅读</text></view>
		<image v-if="heroImage" class="hero" :src="heroImage" mode="widthFix"/>
		<view class="content"><mp-html v-if="body" :content="body" :markdown="true" :tag-style="articleTagStyle" :container-style="articleContainerStyle" :selectable="true" :scroll-table="true"/><view v-else class="loading">正在打开文章</view></view>
		<view class="actions"><text :class="{active: vote === 1}" @tap="voteArticle(1)">喜欢 {{ likes || '' }}</text><text :class="{active: vote === -1}" @tap="voteArticle(-1)">踩 {{ dislikes || '' }}</text></view>
		<view class="end">— END —</view>
		<web-install-badge />
	</view>
</template>
<script>
import mpHtml from '@/components/mp-html/mp-html'
import { localStorage } from '../../js_sdk/mp-storage/mp-storage/index.js'
import { buildContentShareUrl, shareContent, shareSummary } from '@/utils/contentShare.js'
import WebInstallBadge from '@/components/web-install-badge/web-install-badge.vue'

export default {
	components: { mpHtml, WebInstallBadge },
	data() { return { id: 0, journalId: 0, returnTo: '', title: '', subtitle: '', body: '', heroImage: '', journalName: '', authorName: '见字作者', created: 0, likes: 0, dislikes: 0, vote: 0, layout: 'classic', theme: 'paper' } },
	computed: {
		readingMinutes() { return Math.max(1, Math.ceil(String(this.body).replace(/\s/g, '').length / 420)) },
		articleInk() { return this.theme === 'night' ? '#eef6f1' : '#1d2823' },
		articleMuted() { return this.theme === 'night' ? '#b9cbc1' : '#52635a' },
		articleTagStyle() { const ink = this.articleInk; return { p: `color:${ink} !important`, div: `color:${ink} !important`, span: `color:${ink} !important`, li: `color:${ink} !important`, td: `color:${ink} !important`, th: `color:${ink} !important`, h1: `color:${ink} !important`, h2: `color:${ink} !important`, h3: `color:${ink} !important`, h4: `color:${ink} !important`, h5: `color:${ink} !important`, h6: `color:${ink} !important`, strong: `color:${ink} !important`, em: `color:${ink} !important`, a: `color:${this.theme === 'night' ? '#c9dfd3' : '#315f50'} !important`, blockquote: `color:${this.articleMuted} !important` } },
		articleContainerStyle() { return `color:${this.articleInk} !important;` }
	},
	onLoad(q) { this.id = Number(q.id || 0); this.returnTo = q && q.returnTo ? decodeURIComponent(q.returnTo) : ''; this.load() },
	methods: {
		token() { try { return JSON.parse(localStorage.getItem('userinfo') || '{}').token || '' } catch (e) { return '' } },
		back() { if (this.returnTo && this.returnTo.indexOf('/pages/') === 0) return uni.redirectTo({ url: this.returnTo }); uni.navigateBack({ delta: 1, fail: () => uni.redirectTo({ url: this.journalId ? '/pages/journal/info?id=' + this.journalId : '/pages/journal/index' }) }) },
		shareArticle() {
			if (!this.id || !this.title) return false
			return shareContent({ url: buildContentShareUrl(this.$API.GetWebUrl(), '/pages/journal/article', { id: this.id }), title: '文章：' + this.title, text: shareSummary(this.subtitle || this.body, 120) || this.title })
		},
		load() { this.$Net.request({ url: this.$API.journalArticleInfo(), data: { id: this.id, token: this.token() }, method: 'get', dataType: 'json', success: r => { let x = r.data && r.data.code === 1 ? r.data.data : null; if (!x) return uni.showToast({ title: '文章不存在或已下线', icon: 'none' }); this.journalId = Number(x.journalId || 0); uni.setStorageSync('journalArticleReturnJournalId', this.journalId); this.title = x.title; this.subtitle = x.subtitle || ''; this.body = x.bodyMarkdown || ''; this.heroImage = x.coverUrl || ''; this.journalName = x.journalName || '见字'; this.authorName = x.authorName || '见字作者'; this.created = x.publishedAt || x.created; this.likes = Number(x.likes || 0); this.dislikes = Number(x.dislikes || 0); this.vote = x.isLiked ? 1 : (x.isDisliked ? -1 : 0); this.layout = x.layoutPreset || 'classic'; this.theme = x.themePreset || 'paper' } }) },
		voteArticle(v) { if (!this.token()) return uni.navigateTo({ url: '/pages/user/login' }); let next = this.vote === v ? 0 : v; this.$Net.request({ url: this.$API.journalArticleVote(), data: { articleId: this.id, vote: next, token: this.token() }, method: 'get', dataType: 'json', success: r => { if (r.data && r.data.code === 1) { this.vote = next; let x = r.data.data || {}; this.likes = Number(x.likes || 0); this.dislikes = Number(x.dislikes || 0) } } }) },
		displayDate(v) { let d = new Date(Number(v) > 10000000000 ? Number(v) : Number(v) * 1000); return isNaN(d.getTime()) ? '' : d.getFullYear() + '年' + (d.getMonth() + 1) + '月' + d.getDate() + '日' }
	}
}
</script>
<style scoped>.reading-page{position:relative;isolation:isolate;min-height:100vh;overflow:hidden;background:#f4f1eb;color:#171c1a}.ambient{position:absolute;z-index:-1;top:-12vh;left:-12vw;width:124vw;height:58vh;background-position:center;background-size:cover;filter:blur(42px) saturate(.78);opacity:.16;transform:scale(1.1)}.reading-page:after{content:"";position:absolute;z-index:-1;inset:0;background:linear-gradient(180deg,rgba(244,241,235,.78) 0%,#f4f1eb 42%,#f4f1eb 100%);pointer-events:none}.nav{position:relative;z-index:2;display:flex;justify-content:space-between;padding:calc(16px + env(safe-area-inset-top)) 24px 16px;background:rgba(244,241,235,.58);backdrop-filter:blur(18px);font:15px Georgia,serif}.header{position:relative;max-width:850px;margin:auto;padding:60px 28px 38px}.kicker{display:block;font-size:11px;letter-spacing:2px;color:#a16f49}.title{display:block;margin-top:18px;font:clamp(38px,8vw,76px)/1.04 Georgia,serif;color:#172d26}.subtitle{display:block;margin-top:18px;font-size:19px;line-height:1.6;color:#53645b}.byline{display:block;margin-top:30px;font-size:12px;color:#586a61}.hero{position:relative;z-index:1;display:block;width:100%;max-height:620px;object-fit:cover;box-shadow:0 18px 50px rgba(34,52,44,.14)}.content{position:relative;z-index:1;max-width:720px;margin:0 auto;padding:46px 28px 28px;font-size:18px;line-height:1.95}.content :deep(p){margin-bottom:1.45em}.content :deep(h1),.content :deep(h2),.content :deep(h3){margin:2em 0 .7em;font-family:Georgia,serif;color:#1b3b30}.content :deep(blockquote){margin:2em 0;padding:18px 22px;border-left:3px solid #b1784e;background:#faf6f0;color:#63756c;font:20px Georgia,serif}.content :deep(img){display:block;max-width:100%;height:auto;margin:2em auto;border-radius:3px;box-shadow:0 14px 35px rgba(31,47,40,.12)}.actions{position:relative;z-index:1;display:flex;justify-content:center;gap:14px;padding:18px 24px 42px}.actions text{padding:10px 17px;border:1px solid #c7d2cc;border-radius:24px;color:#52675d;font-size:13px;background:rgba(255,255,255,.28)}.actions text.active{border-color:#c9875d;color:#a35e31;background:#fff8f2}.end{position:relative;z-index:1;text-align:center;padding:20px 24px 70px;color:#a16f49;letter-spacing:2px}.theme-paper,.theme-gray{background:#f0ede7}.theme-night{background:#151918;color:#e5eee9}.theme-night:after{background:linear-gradient(180deg,rgba(21,25,24,.78) 0%,#151918 42%,#151918 100%)}.theme-night .ambient{opacity:.22}.theme-night .nav{background:rgba(21,25,24,.62)}.theme-night .title,.theme-night .content :deep(h1),.theme-night .content :deep(h2){color:#eef6f1}.theme-night .subtitle,.theme-night .byline,.theme-night .content{color:#d2ded8}.theme-night .content :deep(blockquote){background:#24332d;color:#b9cbc1}.theme-night .actions text{border-color:#4c6559;color:#c8d8d0;background:rgba(30,48,41,.5)}.layout-modern .title{font-family:Arial,sans-serif;font-weight:800}.layout-minimal .header{text-align:center}.layout-minimal .content{max-width:640px}.layout-photo .header{margin-top:-140px;position:relative;color:#fff}.layout-photo .title,.layout-photo .kicker,.layout-photo .byline{color:#fff}@media(max-width:480px){.header{padding:48px 24px 32px}.title{font-size:42px}.content{padding:34px 24px;font-size:17px;line-height:1.9}}
</style>
<style scoped>
/* Keep the article shell and mp-html body on one readable theme surface. */
.reading-page{background:#f0ede7;color:#26342f}
.theme-paper,.theme-gray{background:#e6e7e3;color:#29332f}
.theme-paper .nav,.theme-gray .nav,.theme-paper .header,.theme-gray .header,.theme-paper .content,.theme-gray .content{background:#e6e7e3;color:#29332f}
.content :deep(*),.content :deep(p),.content :deep(li),.content :deep(td),.content :deep(th){color:inherit}
.theme-paper .content :deep(blockquote),.theme-gray .content :deep(blockquote){background:#daddd8;color:#4f6259}
.theme-night,.theme-night .nav,.theme-night .header,.theme-night .content,.theme-night .actions,.theme-night .end{background:#151918;color:#e5eee9}
.theme-night .content :deep(*),.theme-night .content :deep(p),.theme-night .content :deep(li),.theme-night .content :deep(td),.theme-night .content :deep(th){color:#e5eee9}
.theme-night .content :deep(blockquote){background:#24332d;color:#b5c4bc;border-color:#c18a60}
.theme-night .actions text{border-color:#4c6559;color:#b5c4bc}
</style>
