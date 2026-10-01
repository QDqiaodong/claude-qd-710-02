<template>
  <div class="app-frame">
    <!-- 顶部条：品牌 + 全局作业日期筛选 + 计数器 -->
    <header class="topbar">
      <div class="brand">
        <span class="brand-mark">🛩</span>
        <div>
          植保无人机飞行队 · 田间作业调度台
          <div class="brand-sub">{{ todayText }} · 已完成地块 {{ overview.plotDone || 0 }}/{{ overview.plotTotal || 0 }}</div>
        </div>
      </div>

      <!-- 全局日期筛选：地图 / 作业单共用 -->
      <div class="date-picker">
        <span class="dp-label">作业日期</span>
        <el-date-picker
          v-model="range"
          type="daterange"
          value-format="YYYY-MM-DD"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          :clearable="true"
          style="width: 248px"
          @change="onRangeChange"
        />
      </div>

      <div class="top-stats">
        <div class="stat-chip"><b>{{ overview.plotWorking || 0 }}</b><span>作业中地块</span></div>
        <div class="stat-chip"><b>{{ overview.droneIdle || 0 }}</b><span>在库机身</span></div>
        <div class="stat-chip"><b>{{ overview.pilotActive || 0 }}</b><span>在队飞手</span></div>
        <div class="stat-chip"><b>{{ overview.orderDone || 0 }}</b><span>已完成单</span></div>
        <div class="stat-chip"><b>{{ hoursText }}</b><span>累计飞行小时</span></div>
        <div class="stat-chip clickable" :class="{ 'is-alert': (overview.abnormalRows || 0) > 0 }"
             title="点击进入异常停飞处置台" @click="goHandlings()">
          <b>{{ overview.abnormalRows || 0 }}</b><span>异常架次</span>
        </div>
        <div v-if="overview.handlingOpen" class="stat-chip clickable is-alert"
             title="有待处置 / 观察 / 停飞中的异常处置，点击处理" @click="goHandlings()">
          <b>{{ overview.handlingOpen }}</b><span>未结束处置</span>
        </div>
      </div>
    </header>

    <div class="shell">
      <!-- 左侧竖向模块条 -->
      <aside class="sidebar">
        <div class="side-title">作业模块</div>
        <button
          v-for="item in navItems"
          :key="item.key"
          class="nav-btn"
          :class="{ 'is-on': activeKey === item.key }"
          type="button"
          @click="go(item.path)"
        >
          <span class="nav-ico">{{ item.icon }}</span>
          <span>{{ item.label }}<em class="nav-hint">{{ item.hint }}</em></span>
        </button>
      </aside>

      <main class="stage">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed, getCurrentInstance, onMounted, provide, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import router, { navItems } from './router'
import { farmApi } from './api'

/**
 * 模板自带的 main.js 只装了 Element Plus，没装 router（那份文件不动它），
 * 所以在这里把 router 装到根组件所属的 app 实例上。
 * vue-router 的 install 自带幂等保护，重复调用无副作用。
 */
getCurrentInstance().appContext.app.use(router)

const route = useRoute()
const nav = useRouter()

const overview = ref({})
const range = ref(['', ''])

/** 全局上下文：所有页面 inject('plant') 拿同一段作业日期区间与总览计数 */
const plant = {
  overview,
  range,
  from: computed(() => (range.value && range.value[0]) || ''),
  to: computed(() => (range.value && range.value[1]) || ''),
  reloadOverview: loadOverview
}
provide('plant', plant)

const todayText = computed(() => {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
})

const hoursText = computed(() => Number(overview.value.droneHourTotal || 0).toFixed(1))

const activeKey = computed(() => {
  const hit = navItems.find((item) => route.path.startsWith(item.path))
  return hit ? hit.key : 'field'
})

function go(path) {
  nav.push(path)
}

function goHandlings() {
  nav.push({ path: '/handlings' })
}

function onRangeChange(value) {
  const saved = value || ['', '']
  localStorage.setItem('plant-range', JSON.stringify(saved))
}

async function loadOverview() {
  overview.value = await farmApi.overview()
}

onMounted(async () => {
  try {
    const saved = JSON.parse(localStorage.getItem('plant-range') || 'null')
    if (Array.isArray(saved) && saved.length === 2) {
      range.value = saved
    }
  } catch (e) {
    range.value = ['', '']
  }
  await loadOverview()
})
</script>

<style>
.app-frame {
  min-height: 100%;
  display: flex;
  flex-direction: column;
}
.stat-chip.clickable {
  cursor: pointer;
}
.stat-chip.clickable:hover {
  box-shadow: 0 0 0 2px rgba(255, 255, 255, 0.45);
}
</style>
