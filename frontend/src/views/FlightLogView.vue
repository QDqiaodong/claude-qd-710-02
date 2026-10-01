<template>
  <div>
    <div class="panel">
      <div class="panel-head">
        <h3>飞行日志</h3>
        <span class="panel-sub">按架次登记的起降时间、作业亩数与消耗药量，异常架次整行标红，登记即自动进入停飞处置流程</span>
        <div class="panel-extra">
          <el-select v-model="orderId" clearable filterable placeholder="按作业单筛选" style="width: 240px" @change="load">
            <el-option v-for="o in orderOptions" :key="o.id" :label="o.orderNo + ' · ' + (o.plotNo || '') + ' · ' + o.jobDate" :value="o.id" />
          </el-select>
          <el-select v-model="droneId" clearable filterable placeholder="按机身筛选" style="width: 170px" @change="load">
            <el-option v-for="d in droneOptions" :key="d.id" :label="d.bodyNo + ' · ' + d.model" :value="d.id" />
          </el-select>
          <el-switch v-model="onlyAbnormal" active-text="只看异常" @change="load" />
          <el-button size="small" @click="load">刷新</el-button>
          <el-button size="small" type="primary" @click="createVisible = true">登记架次</el-button>
        </div>
      </div>

      <div v-if="scopeDrone" class="scope-bar">
        <el-tag type="warning" size="small" closable @close="clearDroneScope">
          正在只看机身 {{ scopeDrone.bodyNo }} 的飞行日志（点 × 查看全部）
        </el-tag>
      </div>

      <div class="summary-bar" style="margin-bottom: 12px">
        <div class="sb-item"><span>架次总数</span><b>{{ summary.rows || 0 }}</b></div>
        <div class="sb-item"><span>总飞行时长(分)</span><b>{{ summary.totalMinutes || 0 }}</b></div>
        <div class="sb-item"><span>总飞行小时</span><b>{{ ((summary.totalMinutes || 0) / 60).toFixed(1) }}</b></div>
        <div class="sb-item"><span>累计作业亩数</span><b>{{ Number(summary.totalAreaMu || 0).toFixed(1) }}</b></div>
        <div class="sb-item"><span>累计消耗药量(L)</span><b>{{ Number(summary.chemicalTotal || 0).toFixed(1) }}</b></div>
        <div class="sb-item is-alert-card">
          <span>异常架次</span><b :style="{ color: '#c62828' }">{{ summary.abnormalRows || 0 }}</b>
        </div>
      </div>

      <el-table :data="logs" border size="small" :row-class-name="rowClass">
        <el-table-column prop="seqNo" label="架次" width="64" />
        <el-table-column prop="orderNo" label="作业单" width="132" />
        <el-table-column prop="plotNo" label="地块" width="80" />
        <el-table-column label="机身" width="100">
          <template #default="{ row }">
            <el-link type="primary" :underline="false" @click="goDrone(row.droneId)">{{ row.droneBodyNo }}</el-link>
          </template>
        </el-table-column>
        <el-table-column prop="pilotName" label="飞手" width="88" />
        <el-table-column label="起飞" width="152">
          <template #default="{ row }">{{ fmt(row.takeoffAt) }}</template>
        </el-table-column>
        <el-table-column label="降落" width="152">
          <template #default="{ row }">{{ fmt(row.landingAt) }}</template>
        </el-table-column>
        <el-table-column prop="durationMin" label="时长(分)" width="90" />
        <el-table-column prop="areaMu" label="亩数" width="80" />
        <el-table-column prop="chemicalL" label="药量(L)" width="90" />
        <el-table-column label="风速(m/s)" width="100">
          <template #default="{ row }">
            <span :style="{ color: Number(row.windSpeed) >= 5 ? '#c62828' : '' }">{{ row.windSpeed }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="temperature" label="气温" width="76" />
        <el-table-column label="异常 / 处置" min-width="300">
          <template #default="{ row }">
            <template v-if="row.abnormal">
              <div style="color: #c62828">{{ row.abnormalDesc }}</div>
              <div class="handling-line">
                <el-tag size="small" :type="handlingTagType(row.handlingStatus)">
                  {{ row.handlingStatusName || '待处置' }}
                </el-tag>
                <el-button v-if="row.handlingId" size="small" link type="primary"
                           @click="goHandling(row)">查看处置</el-button>
                <el-button v-else size="small" link type="primary" @click="goHandling(row)">去处置</el-button>
              </div>
            </template>
            <span v-else class="muted">正常</span>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="createVisible" title="登记飞行架次" width="540">
      <el-alert type="warning" :closable="false" show-icon style="margin-bottom: 12px"
        title="本架次若标记为异常，提交后系统会自动生成「待处置」停飞处置单，由值班主管跟进观察 / 停飞 / 解除" />
      <el-form :model="form" label-width="120px">
        <el-form-item label="作业单">
          <el-select v-model="form.jobOrderId" filterable placeholder="选择作业中的作业单" style="width: 100%">
            <el-option v-for="o in orderOptions" :key="o.id" :label="o.orderNo + ' · ' + (o.plotNo || '') + ' · ' + o.statusName" :value="o.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="起飞时间"><el-date-picker v-model="form.takeoffAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width: 100%" /></el-form-item>
        <el-form-item label="降落时间"><el-date-picker v-model="form.landingAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width: 100%" /></el-form-item>
        <el-form-item label="本架次亩数"><el-input-number v-model="form.areaMu" :min="1" :max="80" :precision="2" /></el-form-item>
        <el-form-item label="消耗药量(L)"><el-input-number v-model="form.chemicalL" :min="1" :precision="2" /></el-form-item>
        <el-form-item label="风速(m/s)"><el-input-number v-model="form.windSpeed" :min="0" :max="12" :step="0.1" :precision="1" /></el-form-item>
        <el-form-item label="气温"><el-input-number v-model="form.temperature" :min="-10" :max="45" :step="0.1" :precision="1" /></el-form-item>
        <el-form-item label="是否异常"><el-switch v-model="form.abnormal" /></el-form-item>
        <el-form-item v-if="form.abnormal" label="异常描述"><el-input v-model="form.abnormalDesc" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCreate">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, inject, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { droneApi, flightLogApi, jobOrderApi } from '../api'

const route = useRoute()
const router = useRouter()
const plant = inject('plant')
const logs = ref([])
const orderOptions = ref([])
const droneOptions = ref([])
const orderId = ref(null)
const droneId = ref(null)
const onlyAbnormal = ref(false)
const summary = ref({})
const createVisible = ref(false)

const form = ref({
  jobOrderId: null, takeoffAt: '', landingAt: '', areaMu: 25, chemicalL: 30,
  windSpeed: 2.0, temperature: 24, abnormal: false, abnormalDesc: ''
})

const scopeDrone = computed(() => droneOptions.value.find((d) => d.id === droneId.value) || null)

const fmt = (value) => (value ? String(value).replace('T', ' ') : '—')
const rowClass = ({ row }) => (row.abnormal ? 'row-abnormal' : '')
const handlingTagType = (status) => ({
  PENDING: 'danger', OBSERVING: 'warning', GROUNDED: 'info', CLOSED: 'success'
}[status] || 'danger')

async function load() {
  const params = {}
  if (orderId.value) params.jobOrderId = orderId.value
  if (droneId.value) params.droneId = droneId.value
  logs.value = onlyAbnormal.value ? await flightLogApi.abnormal(params) : await flightLogApi.list(params)
  summary.value = await flightLogApi.summary(orderId.value)
}

async function loadOptions() {
  orderOptions.value = await jobOrderApi.options()
  droneOptions.value = await droneApi.list({})
}

async function submitCreate() {
  const f = form.value
  if (!f.jobOrderId || !f.takeoffAt || !f.landingAt) {
    ElMessage.warning('作业单、起飞与降落时间都要选')
    return
  }
  await flightLogApi.create(f)
  ElMessage.success(f.abnormal ? '架次已登记，异常已自动立案进入待处置' : '架次已登记，累计数据已回填')
  createVisible.value = false
  await load()
  plant.reloadOverview()
}

function goDrone(id) {
  router.push({ path: '/drones', query: { droneId: id } })
}

function goHandling(row) {
  if (row.handlingId) {
    router.push({ path: '/handlings', query: { id: row.handlingId } })
  } else {
    router.push({ path: '/handlings', query: { droneId: row.droneId, status: 'PENDING' } })
  }
}

function clearDroneScope() {
  droneId.value = null
  router.replace({ path: '/logs' })
  load()
}

onMounted(async () => {
  await loadOptions()
  if (route.query.droneId) droneId.value = Number(route.query.droneId)
  await load()
})
</script>

<style scoped>
.is-alert-card {
  background: #fdecea;
  border-color: #f5c6c2;
}
.handling-line {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 2px;
}
</style>
