<script>
	import './index.css'
	import {
		pageIndexSetting
	} from './setting.js'

	export default {
		// #ifdef H5
		onLaunch: function() {
			this.pageAnimationDuration = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 0 : 190
			this.isBack = false
			window.addEventListener('popstate', () => {
				this.isBack = true
			}, false)
			requestAnimationFrame(() => this.show())
			this.$router.beforeEach((toPage, fromPage, next) => {
				next()
			})
			this.$router.afterEach(() => {
				requestAnimationFrame(() => this.show())
			})
		},
		methods: {
			show() {
				const pagePath = window.location.hash.split('?')[0]
				const uniPageElement = document.querySelector('uni-page:last-of-type') || document.querySelector('uni-page')
				if (!uniPageElement) {
					setTimeout(() => {
						const pageElement = document.querySelector('uni-page:last-of-type') || document.querySelector('uni-page')
						if (pageElement) pageElement.classList.add('page-show')
					}, 0)
					return
				}
				const classList = uniPageElement.classList
				const page = pageIndexSetting.find(item => '#/' + item.path == pagePath)

				if (pagePath == '#/' || !page || page.pageIn === false || this.pageAnimationDuration === 0) {
					classList.add('page-show')
					this.isBack = false
					return
				}

				const animationName = page.animaName || 'slide-in-right'
				const beforeClass = animationName + (this.isBack ? '-back-enter' : '-enter')
				const afterClass = animationName + (this.isBack ? '-back-leave' : '-leave')
				this.isBack = false
				classList.add(beforeClass)
				requestAnimationFrame(() => {
					classList.add('page-animation-enter', afterClass, 'page-show')
					setTimeout(() => {
						classList.remove('page-animation-enter', beforeClass, afterClass)
					}, this.pageAnimationDuration)
				})
			}
		}
		// #endif
	}
</script>
