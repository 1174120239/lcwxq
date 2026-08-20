<template>
	<mp-html class="rich-content" :content="content" :selectable="selectable" :show-img-menu="true" :lazy-load="true" :scroll-table="true" />
</template>

<script>
	import mpHtml from '@/components/mp-html/mp-html'
	import { renderRichContent } from '@/utils/richContent.js'

	export default {
		name: 'richContent',
		components: { mpHtml },
		props: {
			value: { type: String, default: '' },
			selectable: { type: Boolean, default: true }
		},
		computed: {
			content() { return renderRichContent(this.value) }
		}
	}
</script>

<style scoped>
	.rich-content { display: block; color: inherit; font-size: inherit; line-height: inherit; }
</style>
