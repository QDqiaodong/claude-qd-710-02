<template>
  <div>
    <div class="panel">
      <div class="panel-head">
        <h3>植保无人机</h3>
        <span class="panel-sub">载荷 / 喷幅 / 累计飞行小时与作业亩数 · 在库的机可派机 · 停飞处置中的机身自动维护</span>
        <div class="panel-extra">
          <el-select v-model="filterStatus" clearable placeholder="状态筛选" style="width: 150px" @change="load">
            <el-option v-for="s in droneStatuses" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
          <el-button size="small" @click="load">刷新</el-button>
          <el-button size="small" type="primary" @click="createVisible = true">新机入库</el-button>
        </div>
      </div>

      <div v-if="scopeDrone" class="scope-bar">
        <el-tag type="warning" size="small" closable @close="clearScope">
          正在定位机身 {{ scopeDrone.bodyNo }}（来自飞行日志 / 处置台，点 × 取消定位）
        </el-tag>
      </div>

      <div class="summary-bar" style="margin-bottom: 12px">
        <div class="sb-item"><span>机身总数</span><b>{{ drones.length }}</b></div>
        <div class="sb-item"><span>在库</span><b>{{ count('IDLE') }}</b></div>
        <div class="sb-item"><span>作业中</span><b>{{ count('WORKING') }}</b></div>
        <div class="sb-item is-warn-card"><span>维修</span><b>{{ count('MAINT') }}</b></div>
        <div class="sb-item"><span>停用</span><b>{{ count('DISABLED') }}</b></div>
        <div class="sb-item is-alert-card"><span>未结束处置</span><b>{{ totalOpenHandling }}</b></div>
        <div class="sb-item"><span>累计作业亩数</span><b>{{ totalArea.toFixed(1) }}</b></div>
        <div class="sb-item"><span>累计飞行小时</span><b>{{ totalHours.toFixed(1) }}</b></div>
      </div>

      <div class="card-grid">
        <div v-for="d in drones" :key="d.id" class="unit-card"
             :class="{ 'is-scoped': scopeDrone && scopeDrone.id === d.id }">
          <div class="uc-head">
            <span class="uc-icon">🚁</span>
            <div>
              <div class="uc-id">
                <el-link type="primary" :underline="false" @click="openDetail(d)">{{ d.bodyNo }}</el-link>
                <el-tag v-if="d.openHandlingCount" size="small" type="danger" effect="plain"
                        class="handling-badge" @click="goHandling(d)">
                  {{ d.openHandlingCount }} 条处置
                </el-tag>
              </div>
              <div class="uc-sub">{{ d.model }} · {{ d.droneTypeName }}</div>
            </div>
            <span class="mini-tag" style="margin-left: auto" :style="statusStyle(d.status)">
              {{ d.statusName }}
            </span>
          </div>

          <div class="kv-grid">
            <div class="kv"><span>最大载荷</span><b>{{ d.maxPayload }} L</b></div>
            <div class="kv"><span>喷幅</span><b>{{ d.sprayWidth }} m</b></div>
            <div class="kv"><span>累计作业亩数</span><b>{{ d.totalAreaMu }}</b></div>
            <div class="kv"><span>累计飞行小时</span><b>{{ d.totalFlightHours }}</b></div>
            <div class="kv"><span>电池组数</span><b>{{ d.batteryPacks }} 组</b></div>
            <div class="kv"><span>购入日期</span><b>{{ d.purchaseDate || '—' }}</b></div>
          </div>

          <div class="tag-line">
            <span class="mini-tag">在手作业单 {{ d.openOrders }}</span>
            <span v-if="d.pendingOrders" class="mini-tag" :style="{ color: '#c62828', borderColor: '#f5c6c2' }">
              待飞 {{ d.pendingOrders }} 张
            </span>
            <span v-if="d.currentPlotNo" class="mini-tag">正在喷：{{ d.currentPlotNo }}</span>
            <span class="mini-tag">累计 {{ d.sortCount }} 架次</span>
            <el-button v-if="d.openHandlingCount" size="small" link type="danger" @click="goHandling(d)">
              停飞处置 {{ d.openHandlingCount }}
            </el-button>
          </div>

          <div class="card-actions">
            <el-button size="small" @click="openDetail(d)">详情</el-button>
            <el-select v-model="statusMap[d.id]" placeholder="切换状态" size="small" style="width: 120px"
                       @change="changeStatus(d)">
              <el-option v-for="s in allowedStatus(d.status)" :key="s.value"
                         :label="s.label" :value="s.value"
                         :disabled="s.value === 'IDLE' && d.openHandlingCount > 0" />
            </el-select>
            <el-button size="small" type="primary"
                       :disabled="d.status !== 'IDLE' || d.openHandlingCount > 0"
                       @click="openDispatch(d)">派机</el-button>
          </div>
        </div>
      </div>
    </div>

    <!-- 机身详情抽屉：台账 + 待飞改派提示 + 异常处置记录 -->
    <el-drawer v-model="detailVisible" size="600px" :title="detail ? '机身详情 · ' + detail.bodyNo : '机身详情'">
      <div v-if="detail" class="detail-box">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="型号">{{ detail.model }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ detail.droneTypeName }}</el-descriptions-item>
          <el-descriptions-item label="当前状态">
            <span class="mini-tag" :style="statusStyle(detail.status)">{{ detail.statusName }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="购入日期">{{ detail.purchaseDate || '—' }}</el-descriptions-item>
          <el-descriptions-item label="最大载荷">{{ detail.maxPayload }} L</el-descriptions-item>
          <el-descriptions-item label="喷幅">{{ detail.sprayWidth }} m</el-descriptions-item>
          <el-descriptions-item label="累计飞行小时">{{ detail.totalFlightHours }}</el-descriptions-item>
          <el-descriptions-item label="累计作业亩数">{{ detail.totalAreaMu }}</el-descriptions-item>
          <el-descriptions-item label="电池组数">{{ detail.batteryPacks }} 组</el-descriptions-item>
          <el-descriptions-item label="累计架次">{{ detail.sortCount }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.currentPlotNo" label="正在作业地块" :span="2">
            {{ detail.currentPlotNo }}
          </el-descriptions-item>
        </el-descriptions>

        <el-alert v-if="detail.openHandlingCount" type="error" :closable="false" show-icon
                  style="margin: 12px 0"
                  :title="`该机有 ${detail.openHandlingCount} 条未结束的异常处置，状态不能切回在库；待飞作业单不得起飞，须改派`" />

        <div v-if="pendingOrders.length" class="pending-box">
          <div class="pb-title">尚未起飞的作业单（{{ pendingOrders.length }}）— 机身非在库，必须改派：</div>
          <div v-for="o in pendingOrders" :key="o.id" class="pb-item">
            <el-button size="small" :type="detail.openHandlingCount ? 'danger' : 'warning'" plain
                       @click="goOrder(o.id)">
              {{ o.orderNo }} · {{ o.plotNo }} · {{ o.jobDate }} 改派 →
            </el-button>
          </div>
        </div>

        <div class="sec-title">
          异常处置记录
          <el-button size="small" link type="primary" @click="goHandling(detail)">查看全部 →</el-button>
        </div>
        <el-empty v-if="!handlingList.length" description="暂无异常处置记录" :image-size="60" />
        <div v-for="h in handlingList" :key="h.id" class="handling-card">
          <div class="hc-head">
            <el-link type="primary" :underline="false" @click="goHandlingDetail(h.id)">{{ h.handlingNo }}</el-link>
            <el-tag size="small" :type="handlingTagType(h.status)">{{ h.statusName }}</el-tag>
            <span class="muted" style="margin-left:auto">{{ fmt(h.registeredAt) }}</span>
          </div>
          <div class="muted">{{ h.orderNo }} 第 {{ h.seqNo }} 架次 · {{ fmt(h.takeoffAt) }}</div>
          <div style="color:#c62828">{{ h.abnormalDesc }}</div>
          <div v-if="h.status === 'CLOSED'" class="muted">结论：{{ h.conclusion }}</div>
          <div v-else-if="h.status === 'GROUNDED'" style="color:#ef6c00">停飞：{{ h.groundComment }}</div>
          <div v-else-if="h.status === 'OBSERVING'" style="color:#0277bd">观察：{{ h.observeComment }}</div>
          <div class="muted">值班主管：{{ h.supervisorName || '尚未处置' }}</div>
        </div>

        <div class="sec-title">飞行日志</div>
        <el-button size="small" @click="goLogs(detail)">查看该机全部架次 →</el-button>
      </div>
    </el-drawer>

    <!-- 派机：给这台机配地块 + 飞手，直接生成一张待飞作业单 -->
    <el-dialog v-model="dispatchVisible" :title="dispatchDrone ? '派机 · ' + dispatchDrone.bodyNo : '派机'" width="560">
      <el-form :model="dispatchForm" label-width="110px">
        <el-form-item label="作业单号">
          <el-input v-model="dispatchForm.orderNo" placeholder="如 JO-24092801" />
        </el-form-item>
        <el-form-item label="地块">
          <el-select v-model="dispatchForm.plotId" filterable placeholder="选择地块" style="width: 100%">
            <el-option v-for="p in plotOptions" :key="p.id" :label="p.plotNo + ' · ' + p.farmerName + ' · ' + p.areaMu + '亩'" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="飞手">
          <el-select v-model="dispatchForm.pilotId" filterable placeholder="选择飞手" style="width: 100%">
            <el-option v-for="p in pilotOptions" :key="p.id" :label="p.pilotNo + ' · ' + p.name + ' · ' + p.licenseName" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="药剂配方"><el-input v-model="dispatchForm.formula" /></el-form-item>
        <el-form-item label="亩用量(L/亩)"><el-input-number v-model="dispatchForm.dosePerMu" :min="0.1" :max="3" :step="0.1" :precision="2" /></el-form-item>
        <el-form-item label="计划亩数"><el-input-number v-model="dispatchForm.plannedMu" :min="1" :precision="2" /></el-form-item>
        <el-form-item label="作业日期">
          <el-date-picker v-model="dispatchForm.jobDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="天气"><el-input v-model="dispatchForm.weather" placeholder="晴 / 多云 / 阴" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dispatchVisible = false">取消</el-button>
        <el-button type="primary" @click="submitDispatch">生成作业单</el-button>
      </template>
    </el-dialog>

    <!-- 新机入库 -->
    <el-dialog v-model="createVisible" title="新机入库" width="520">
      <el-form :model="form" label-width="110px">
        <el-form-item label="机身编号"><el-input v-model="form.bodyNo" placeholder="如 DP-13" /></el-form-item>
        <el-form-item label="型号"><el-input v-model="form.model" placeholder="如 大疆 T70" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.droneType" style="width: 100%">
            <el-option v-for="t in types" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="最大载荷(L)"><el-input-number v-model="form.maxPayload" :min="1" :precision="2" /></el-form-item>
        <el-form-item label="喷幅(m)"><el-input-number v-model="form.sprayWidth" :min="1" :precision="2" /></el-form-item>
        <el-form-item label="电池组数"><el-input-number v-model="form.batteryPacks" :min="1" :max="20" /></el-form-item>
        <el-form-item label="购入日期">
          <el-date-picker v-model="form.purchaseDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCreate">入库</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, inject, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { droneApi, handlingApi, jobOrderApi, pilotApi, plotApi } from '../api'

const route = useRoute()
const router = useRouter()
const plant = inject('plant')
const drones = ref([])
const filterStatus = ref('')
const statusMap = reactive({})
const createVisible = ref(false)
const dispatchVisible = ref(false)
const dispatchDrone = ref(null)
const plotOptions = ref([])
const pilotOptions = ref([])

const detailVisible = ref(false)
const detail = ref(null)
const pendingOrders = ref([])
const handlingList = ref([])
const scopeId = ref(null)

const droneStatuses = [
  { value: 'IDLE', label: '在库' },
  { value: 'WORKING', label: '作业中' },
  { value: 'MAINT', label: '维修' },
  { value: 'DISABLED', label: '停用' }
]
const types = [
  { value: 'MULTI', label: '多旋翼' },
  { value: 'SINGLE', label: '单旋翼' },
  { value: 'SPRAY', label: '植保专用机' }
]

const form = ref({ bodyNo: '', model: '', droneType: 'MULTI', maxPayload: 40, sprayWidth: 7, batteryPacks: 6, purchaseDate: null })
const dispatchForm = ref({ orderNo: '', plotId: null, pilotId: null, formula: '', dosePerMu: 1.2, plannedMu: 100, jobDate: '', weather: '' })

const totalArea = computed(() => drones.value.reduce((sum, d) => sum + Number(d.totalAreaMu || 0), 0))
const totalHours = computed(() => drones.value.reduce((sum, d) => sum + Number(d.totalFlightHours || 0), 0))
const totalOpenHandling = computed(() => drones.value.reduce((sum, d) => sum + Number(d.openHandlingCount || 0), 0))
const scopeDrone = computed(() => drones.value.find((d) => d.id === scopeId.value) || null)

function count(status) {
  return drones.value.filter((d) => d.status === status).length
}

function statusStyle(status) {
  const map = { IDLE: '#2e7d32', WORKING: '#7b1fa2', MAINT: '#ef6c00', DISABLED: '#c62828' }
  return { color: '#fff', background: map[status] || '#6b5f7d', borderColor: 'transparent' }
}

function handlingTagType(status) {
  return { PENDING: 'danger', OBSERVING: 'warning', GROUNDED: 'info', CLOSED: 'success' }[status] || ''
}

function allowedStatus(current) {
  // 作业中由作业单起飞推进，这里只能手切在库 / 维修 / 停用
  return droneStatuses.filter((s) => s.value !== 'WORKING' && s.value !== current)
}

const fmt = (value) => (value ? String(value).replace('T', ' ') : '—')

async function load() {
  drones.value = await droneApi.list(filterStatus.value ? { status: filterStatus.value } : {})
  drones.value.forEach((d) => {
    statusMap[d.id] = ''
  })
  plant.reloadOverview()
  if (scopeId.value) {
    const hit = drones.value.find((d) => d.id === scopeId.value)
    if (!hit) {
      // 当前状态筛选把目标机滤掉了：清筛选后再来一次
      filterStatus.value = ''
      drones.value = await droneApi.list({})
    }
  }
}

async function changeStatus(drone) {
  const target = statusMap[drone.id]
  if (!target) return
  try {
    await droneApi.setStatus(drone.id, target)
    ElMessage.success(drone.bodyNo + ' 已切到' + droneStatuses.find((s) => s.value === target).label)
  } finally {
    await load()
  }
}

async function openDetail(drone) {
  detail.value = drone
  detailVisible.value = true
  const [orders, handlings] = await Promise.all([
    jobOrderApi.list({ droneId: drone.id }),
    handlingApi.list({ droneId: drone.id })
  ])
  pendingOrders.value = orders.filter((o) => o.status === 'PENDING')
  handlingList.value = handlings.slice(0, 5)
}

function goHandling(drone) {
  router.push({ path: '/handlings', query: { droneId: drone.id } })
}

function goHandlingDetail(id) {
  detailVisible.value = false
  router.push({ path: '/handlings', query: { id } })
}

function goLogs(drone) {
  detailVisible.value = false
  router.push({ path: '/logs', query: { droneId: drone.id } })
}

function goOrder(id) {
  detailVisible.value = false
  router.push({ path: '/orders', query: { focus: id } })
}

function clearScope() {
  scopeId.value = null
  router.replace({ path: '/drones' })
}

async function openDispatch(drone) {
  dispatchDrone.value = drone
  dispatchForm.value = {
    orderNo: 'JO-' + String(new Date().getFullYear()).slice(2) + String(new Date().getMonth() + 1).padStart(2, '0')
      + String(new Date().getDate()).padStart(2, '0') + String(Math.floor(Math.random() * 90) + 10),
    plotId: null,
    pilotId: null,
    formula: '吡蚜酮 25% + 磷酸二氢钾',
    dosePerMu: 1.2,
    plannedMu: 100,
    jobDate: '',
    weather: '晴'
  }
  plotOptions.value = await plotApi.options()
  pilotOptions.value = await pilotApi.options()
  dispatchVisible.value = true
}

async function submitDispatch() {
  const f = dispatchForm.value
  if (!f.orderNo || !f.plotId || !f.pilotId || !f.formula || !f.jobDate) {
    ElMessage.warning('单号、地块、飞手、配方、作业日期都要填')
    return
  }
  await droneApi.dispatch(dispatchDrone.value.id, f)
  ElMessage.success('派机成功，作业单已生成')
  dispatchVisible.value = false
  await load()
}

async function submitCreate() {
  const payload = { ...form.value }
  if (!payload.bodyNo || !payload.model) {
    ElMessage.warning('机身编号与型号都要填')
    return
  }
  await droneApi.create(payload)
  ElMessage.success('新机已入库')
  createVisible.value = false
  await load()
}

onMounted(async () => {
  await load()
  if (route.query.droneId) {
    scopeId.value = Number(route.query.droneId)
    const target = drones.value.find((d) => d.id === scopeId.value)
    if (target) openDetail(target)
  }
})
</script>

<style scoped>
.is-alert-card {
  background: #fdecea;
  border-color: #f5c6c2;
}
.is-warn-card {
  background: #fff4e5;
  border-color: #ffcc80;
}
.unit-card.is-scoped {
  outline: 3px solid #ef6c00;
  outline-offset: 2px;
}
.handling-badge {
  margin-left: 6px;
  cursor: pointer;
}
.detail-box {
  padding: 0 4px;
}
.sec-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 700;
  margin: 16px 0 10px;
}
.pending-box {
  margin: 12px 0;
  padding: 10px 12px;
  background: #fdecea;
  border: 1px solid #f5c6c2;
  border-radius: 8px;
}
.pb-title {
  color: #c62828;
  font-weight: 700;
  margin-bottom: 8px;
}
.pb-item {
  margin: 6px 0;
}
.handling-card {
  border: 1px solid #e3d6ec;
  border-radius: 8px;
  padding: 8px 10px;
  margin-bottom: 8px;
  background: #fcfaff;
}
.hc-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 2px;
}
</style>
