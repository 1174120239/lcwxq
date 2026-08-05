<template>
	<view class="campus-subpage identity-manage" :class="$store.state.AppStyle">
		<view class="header" :style="{height: CustomBar + 'px'}">
			<view class="cu-bar bg-white" :style="{height: CustomBar + 'px', paddingTop: StatusBar + 'px'}">
				<view class="action" @tap="back"><text class="cuIcon-back"></text></view>
				<view class="content text-bold" :style="{top: StatusBar + 'px'}">校园身份选项</view>
				<view class="action" @tap="openCreate"><text class="cuIcon-add"></text></view>
			</view>
		</view>
		<view :style="{height: NavBar + 'px'}"></view>
		<view class="identity-tabs">
			<view :class="{'is-active': type === 'campus'}" @tap="type='campus'">校区管理</view>
			<view :class="{'is-active': type === 'grade'}" @tap="type='grade'">年级管理</view>
		</view>
		<view class="identity-list">
			<view class="identity-row" v-for="item in currentOptions" :key="item.id">
				<view class="identity-main" @tap="openEdit(item)">
					<text class="identity-name">{{item.name}}</text>
					<text class="identity-meta">排序 {{item.sortOrder}} · {{item.userCount || 0}} 位用户</text>
				</view>
				<switch color="#168c67" :checked="item.enabled == 1" @change="toggleEnabled(item, $event)"></switch>
			</view>
			<view class="no-data" v-if="!currentOptions.length">暂无选项</view>
		</view>

		<view class="cu-modal" :class="editing ? 'show' : ''" @tap="closeEdit">
			<view class="cu-dialog identity-dialog" @tap.stop>
				<view class="cu-bar justify-end"><view class="content">{{form.id ? '修改' : '新增'}}{{type === 'campus' ? '校区' : '年级'}}</view><view class="action" @tap="closeEdit"><text class="cuIcon-close"></text></view></view>
				<view class="cu-form-group"><view class="title">名称</view><input v-model="form.name" maxlength="40" :placeholder="type === 'campus' ? '例如 东校区' : '例如 2024级'"></input></view>
				<view class="cu-form-group"><view class="title">排序</view><input v-model="form.sortOrder" type="number" placeholder="数字越大越靠前"></input></view>
				<view class="cu-form-group"><view class="title">启用</view><switch color="#168c67" :checked="form.enabled == 1" @change="form.enabled = $event.detail.value ? 1 : 0"></switch></view>
				<view class="identity-actions"><button class="cu-btn bg-green" @tap="save">保存</button></view>
			</view>
		</view>
	</view>
</template>

<script>
	import { localStorage } from '../../js_sdk/mp-storage/mp-storage/index.js'
	export default {
		data() {
			return {
				StatusBar: this.StatusBar, CustomBar: this.CustomBar,
				NavBar: this.StatusBar + this.CustomBar,
				type: 'campus', campuses: [], grades: [], editing: false,
				form: { id: 0, name: '', sortOrder: 0, enabled: 1 }, token: ''
			}
		},
		computed: { currentOptions() { return this.type === 'campus' ? this.campuses : this.grades } },
		onLoad() {
			this.token = localStorage.getItem('token') || ''
			this.load()
		},
		methods: {
			back() { uni.navigateBack({ delta: 1 }) },
			load() {
				this.$Net.request({
					url: this.$API.campusIdentityManage(), data: { token: this.token }, method: 'get', dataType: 'json',
					success: res => {
						if (res.data.code !== 1) return uni.showToast({ title: res.data.msg, icon: 'none' })
						this.campuses = res.data.data.campuses || []
						this.grades = res.data.data.grades || []
					}
				})
			},
			openCreate() { this.form = { id: 0, name: '', sortOrder: 0, enabled: 1 }; this.editing = true },
			openEdit(item) { this.form = { id: item.id, name: item.name, sortOrder: item.sortOrder, enabled: item.enabled }; this.editing = true },
			closeEdit() { this.editing = false },
			toggleEnabled(item, event) {
				this.saveOption({ id: item.id, name: item.name, sortOrder: item.sortOrder, enabled: event.detail.value ? 1 : 0 })
			},
			save() {
				if (!this.form.name.trim()) return uni.showToast({ title: '请输入名称', icon: 'none' })
				this.saveOption(this.form)
			},
			saveOption(value) {
				const params = Object.assign({}, value, { type: this.type, sortOrder: Number(value.sortOrder || 0) })
				this.$Net.request({
					url: this.$API.campusIdentitySave(), data: { token: this.token, params: JSON.stringify(params) }, method: 'post', dataType: 'json',
					success: res => {
						uni.showToast({ title: res.data.msg, icon: 'none' })
						if (res.data.code === 1) { this.editing = false; this.load() }
					}
				})
			}
		}
	}
</script>

<style scoped>
	.identity-manage { min-height: 100vh; background: #f4f7f6; }
	.identity-tabs { display: grid; grid-template-columns: 1fr 1fr; background: #fff; border-bottom: 1px solid #e5ebe9; }
	.identity-tabs view { height: 82rpx; display: flex; align-items: center; justify-content: center; color: #68746f; border-bottom: 4rpx solid transparent; }
	.identity-tabs .is-active { color: #168c67; border-bottom-color: #168c67; font-weight: 600; }
	.identity-list { margin: 18rpx; overflow: hidden; border: 1px solid #e4eae8; border-radius: 8rpx; background: #fff; }
	.identity-row { min-height: 112rpx; padding: 18rpx 22rpx; display: flex; align-items: center; border-bottom: 1px solid #edf0ef; }
	.identity-main { flex: 1; }
	.identity-name, .identity-meta { display: block; }
	.identity-name { font-weight: 600; }
	.identity-meta { margin-top: 7rpx; color: #7b8582; font-size: 23rpx; }
	.identity-dialog { border-radius: 8rpx; overflow: hidden; }
	.identity-actions { padding: 24rpx; }
	.identity-actions button { width: 100%; }
	.campus-night .identity-tabs, .campus-night .identity-list, .campus-night .identity-dialog { background: #202527; border-color: #333b3c; }
	.campus-night .identity-row { border-color: #333b3c; }
</style>
