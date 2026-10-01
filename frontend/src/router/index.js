import { createRouter, createWebHistory } from 'vue-router'

/**
 * 导航形态：左侧一条竖向模块条（作业地图在最上，作为首页主视觉）。
 * 全局日期区间由 App.vue 顶栏筛选，落到地图与作业单页共用。
 */
export const navItems = [
  { key: 'field', path: '/field', icon: '🛩', label: '作业地图', hint: '地块多边形 + 已完成地块喷洒航线' },
  { key: 'drones', path: '/drones', icon: '🚁', label: '植保无人机', hint: '载荷·喷幅·累计亩数·派机' },
  { key: 'pilots', path: '/pilots', icon: '🧑‍✈️', label: '飞手', hint: '执照等级·累计架次' },
  { key: 'orders', path: '/orders', icon: '🧾', label: '作业单', hint: '待飞 → 作业中 → 已完成' },
  { key: 'mixing', path: '/mixing', icon: '🧪', label: '配药复核台', hint: '分桶·双人复核·锁定后才允许起飞' },
  { key: 'logs', path: '/logs', icon: '📊', label: '飞行日志', hint: '架次时长·亩数·药量·异常标红' },
  { key: 'handlings', path: '/handlings', icon: '🛑', label: '异常停飞处置', hint: '异常架次登记即立案·观察/停飞/解除' }
]

const routes = [
  { path: '/', redirect: '/field' },
  { path: '/field', name: 'field', component: () => import('../views/FieldView.vue') },
  { path: '/drones', name: 'drones', component: () => import('../views/DroneView.vue') },
  { path: '/pilots', name: 'pilots', component: () => import('../views/PilotView.vue') },
  { path: '/orders', name: 'orders', component: () => import('../views/JobOrderView.vue') },
  { path: '/mixing', name: 'mixing', component: () => import('../views/MixingView.vue') },
  { path: '/logs', name: 'logs', component: () => import('../views/FlightLogView.vue') },
  { path: '/handlings', name: 'handlings', component: () => import('../views/HandlingView.vue') },
  { path: '/:pathMatch(.*)*', redirect: '/field' }
]

export default createRouter({
  history: createWebHistory(),
  routes
})
