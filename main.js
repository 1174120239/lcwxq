import Vue from 'vue'
import TuniaoUI from '@/uni_modules/tuniao-ui'
Vue.use(TuniaoUI)

import uView from '@/uni_modules/uview-ui'
Vue.use(uView)
import App from './App'
import store from './store/index.js'
import { CAMPUS_THEME_RESOLVED_EVENT } from './utils/campusTheme.js'
Vue.prototype.$store = store

Vue.mixin({
	computed: {
		campusResolvedAppStyle() {
			return this.$store ? this.$store.state.AppStyle : ''
		}
	},
	watch: {
		campusResolvedAppStyle: {
			immediate: true,
			handler(appStyle) {
				if (!this.$data || !Object.prototype.hasOwnProperty.call(this.$data, 'AppStyle')) return
				this.AppStyle = appStyle
			}
		}
	}
})

uni.$on(CAMPUS_THEME_RESOLVED_EVENT, function(night) {
	store.commit('setAppStyle', Boolean(night))
})

import API from './utils/api.js'
import Net from './utils/net.js'
Vue.prototype.$API = API
Vue.prototype.$Net = Net
import articleItem from './pages/components/articleItem.vue'
Vue.component('articleItem',articleItem)

import commentItem from './pages/components/commentItem.vue'
Vue.component('commentItem',commentItem)

import spaceItem from './pages/components/spaceItem.vue'
Vue.component('spaceItem',spaceItem)
import spaceReplyHistoryItem from './pages/components/spaceReplyHistoryItem.vue'
Vue.component('spaceReplyHistoryItem',spaceReplyHistoryItem)
import followItem from './pages/components/followItem.vue'
Vue.component('followItem',followItem)

import PublishPanel from './pages/components/publishPanel.vue'
Vue.component('PublishPanel', PublishPanel)

Vue.config.productionTip = false

App.mpType = 'app'
const app = new Vue({
	store,
    ...App
})
app.$mount()

 
