<template>
  <div>
    <div class="panel">
      <div class="panel-head">
        <h3>作业单</h3>
        <span class="panel-sub">待飞 → 作业中 → 已完成 / 已取消，亩数按已登记飞行日志累加</span>
        <div class="panel-extra">
          <el-select v-model="filterStatus" clearable placeholder="状态筛选" style="width: 140px" @change="load">
            <el-option v-for="s in statuses" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
          <el-button size="small" @click="load">刷新</el-button>
          <el-button size="small" type="primary" @click="createVisible = true">新建作业单</el-button>
        </div>
      </div>

      <div v-if="focusId" class="scope-bar">
        <el-tag type="danger" size="small" closable @close="clearFocus">
          正在定位待改派作业单 #{{ focusId }}：机身已不可用，请点「改派/改口径」换一台在库机身
        </el-tag>
      </div>

      <div class="summary-bar" style="margin-bottom: 12px">
        <div class="sb-item"><span>作业单总数</span><b>{{ orders.length }}</b></div>
        <div class="sb-item"><span>待飞</span><b>{{ count('PENDING') }}</b></div>
        <div class="sb-item"><span>作业中</span><b>{{ count('WORKING') }}</b></div>
        <div class="sb-item"><span>已完成</span><b>{{ count('DONE') }}</b></div>
        <div class="sb-item"><span>计划亩数合计</span><b>{{ plannedTotal.toFixed(1) }}</b></div>
        <div class="sb-item"><span>日志汇总亩数合计</span><b>{{ loggedTotal.toFixed(1) }}</b></div>
        <div class="sb-item"><span>累计用药(L)</span><b>{{ chemicalTotal.toFixed(1) }}</b></div>
      </div>

      <el-table :data="orders" border size="small" :row-class-name="rowClass" :row-key="(row) => row.id"
                :row-style="rowStyle">
        <el-table-column prop="orderNo" label="单号" width="132" />
        <el-table-column label="地块 / 承包户" min-width="180">
          <template #default="{ row }">{{ row.plotNo }} · {{ row.farmerName }}</template>
        </el-table-column>
        <el-table-column prop="droneBodyNo" label="机身" width="120">
          <template #default="{ row }">
            <div>{{ row.droneBodyNo }}</div>
            <el-tag v-if="row.status === 'PENDING' && row.droneStatus !== 'IDLE'" size="small" type="danger"
                    effect="plain">
              {{ row.droneStatusName }}·须改派
            </el-tag>
            <el-tag v-else-if="row.droneOpenHandling" size="small" type="warning" effect="plain">
              有异常处置
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="pilotName" label="飞手" width="90" />
        <el-table-column prop="formula" label="药剂配方" min-width="200" show-overflow-tooltip />
        <el-table-column prop="dosePerMu" label="亩用量" width="90" />
        <el-table-column prop="jobDate" label="作业日期" width="110" />
        <el-table-column prop="weather" label="天气" width="80" />
        <el-table-column label="状态" width="92">
          <template #default="{ row }">
            <span :style="{ color: statusColor(row.status) }">{{ row.statusName }}</span>
          </template>
        </el-table-column>
        <el-table-column label="计划 / 日志汇总(亩)" width="150">
          <template #default="{ row }">
            {{ row.plannedMu }} / <b :style="{ color: '#2e7d32' }">{{ Number(row.loggedMu || 0).toFixed(2) }}</b>
          </template>
        </el-table-column>
        <el-table-column label="进度" width="130">
          <template #default="{ row }">
            <el-progress :percentage="row.progress" :stroke-width="10" :color="row.status === 'DONE' ? '#2e7d32' : '#7b1fa2'" />
          </template>
        </el-table-column>
        <el-table-column prop="sortCount" label="架次" width="66" />
        <el-table-column label="配药批次" width="118">
          <template #default="{ row }">
            <el-button v-if="row.mixingBatchId" size="small" link
                       :type="row.mixingStatus === 'LOCKED' ? 'success' : 'primary'"
                       @click="openMixing(row)">
              {{ row.mixingStatusName }}
            </el-button>
            <el-button v-else-if="row.status === 'PENDING'" size="small" link type="primary"
                       @click="goCreateMixing(row)">
              发起配药
            </el-button>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'PENDING' && row.droneStatus === 'IDLE'" size="small" type="primary"
                       @click="takeoff(row)">起飞</el-button>
            <el-tooltip v-if="row.status === 'PENDING' && row.droneStatus !== 'IDLE'" placement="top"
                        :content="'机身' + row.droneStatusName + '中，不能起飞，请先改派在库机身'">
              <el-button size="small" type="primary" disabled>起飞</el-button>
            </el-tooltip>
            <el-button v-if="row.status === 'WORKING'" size="small" type="success" @click="finish(row)">完成</el-button>
            <el-button v-if="row.status === 'PENDING'" size="small" @click="openEdit(row)">改派/改口径</el-button>
            <el-button v-if="row.status === 'PENDING' || row.status === 'WORKING'" size="small" type="danger" @click="cancel(row)">取消</el-button>
            <span v-else class="muted">已归档</span>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="createVisible" title="新建作业单" width="560">
      <el-form :model="form" label-width="110px">
        <el-form-item label="作业单号"><el-input v-model="form.orderNo" placeholder="如 JO-24092801" /></el-form-item>
        <el-form-item label="地块">
          <el-select v-model="form.plotId" filterable placeholder="选择地块" style="width: 100%">
            <el-option v-for="p in plotOptions" :key="p.id" :label="p.plotNo + ' · ' + p.farmerName + ' · ' + p.areaMu + '亩'" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="无人机">
          <el-select v-model="form.droneId" filterable placeholder="只有在库且无未结束处置的机身可派航" style="width: 100%">
            <el-option v-for="d in droneOptions" :key="d.id"
                       :disabled="d.status !== 'IDLE' || d.openHandling"
                       :label="d.bodyNo + ' · ' + d.model + droneOptionSuffix(d)" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="飞手">
          <el-select v-model="form.pilotId" filterable placeholder="选择飞手" style="width: 100%">
            <el-option v-for="p in pilotOptions" :key="p.id" :label="p.pilotNo + ' · ' + p.name + ' · ' + p.licenseName" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="药剂配方"><el-input v-model="form.formula" /></el-form-item>
        <el-form-item label="亩用量(L/亩)"><el-input-number v-model="form.dosePerMu" :min="0.1" :max="3" :step="0.1" :precision="2" /></el-form-item>
        <el-form-item label="计划亩数"><el-input-number v-model="form.plannedMu" :min="1" :precision="2" /></el-form-item>
        <el-form-item label="作业日期">
          <el-date-picker v-model="form.jobDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="天气"><el-input v-model="form.weather" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCreate">生成作业单</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="editVisible" title="改派无人机 / 修改配药口径" width="560">
      <el-alert v-if="editTarget && editTarget.mixingBatchId" type="warning" :closable="false"
        style="margin-bottom: 12px"
        title="无人机、药剂配方、亩用量、计划亩数任一变化，原已锁定配药批次会立即失效，需按新条件重新发起配药复核" />
      <el-form :model="editForm" label-width="110px">
        <el-form-item label="作业单">
          <el-input :model-value="editTarget ? editTarget.orderNo : ''" disabled />
        </el-form-item>
        <el-form-item label="地块">
          <el-select v-model="editForm.plotId" filterable style="width: 100%">
            <el-option v-for="p in plotOptions" :key="p.id" :label="p.plotNo + ' · ' + p.farmerName + ' · ' + p.areaMu + '亩'" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="改派无人机">
          <el-select v-model="editForm.droneId" filterable style="width: 100%">
            <el-option v-for="d in droneOptions" :key="d.id"
                       :disabled="d.status !== 'IDLE' || d.openHandling"
                       :label="d.bodyNo + ' · ' + d.model + ' · 载荷' + d.maxPayload + 'L'
                         + droneOptionSuffix(d)"
                       :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="飞手">
          <el-select v-model="editForm.pilotId" filterable style="width: 100%">
            <el-option v-for="p in pilotOptions" :key="p.id" :label="p.pilotNo + ' · ' + p.name + ' · ' + p.licenseName" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="药剂配方"><el-input v-model="editForm.formula" /></el-form-item>
        <el-form-item label="亩用量(L/亩)"><el-input-number v-model="editForm.dosePerMu" :min="0.1" :max="3" :step="0.1" :precision="2" /></el-form-item>
        <el-form-item label="计划亩数"><el-input-number v-model="editForm.plannedMu" :min="1" :precision="2" /></el-form-item>
        <el-form-item label="作业日期">
          <el-date-picker v-model="editForm.jobDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="天气"><el-input v-model="editForm.weather" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" @click="submitEdit">保存修改</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="finishVisible" title="完成作业单" width="520">
      <el-alert type="info" :closable="false" show-icon style="margin-bottom: 12px"
        title="实际作业亩数只认已登记飞行日志的累计值，页面只读展示，不能手工填写或覆盖" />
      <el-descriptions v-if="finishTarget" :column="1" border size="small">
        <el-descriptions-item label="作业单号">{{ finishTarget.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="地块 / 承包户">{{ finishTarget.plotNo }} · {{ finishTarget.farmerName }}</el-descriptions-item>
        <el-descriptions-item label="计划亩数(亩)">{{ finishTarget.plannedMu }}</el-descriptions-item>
        <el-descriptions-item label="已登记架次">{{ finishSummary.rows || 0 }} 架次</el-descriptions-item>
        <el-descriptions-item label="累计飞行时长">{{ finishSummary.totalMinutes || 0 }} 分钟</el-descriptions-item>
        <el-descriptions-item label="累计消耗药量(L)">{{ Number(finishSummary.chemicalTotal || 0).toFixed(2) }}</el-descriptions-item>
        <el-descriptions-item label="实际作业亩数(日志汇总)">
          <b :style="{ color: '#2e7d32' }">{{ Number(finishSummary.totalAreaMu || 0).toFixed(2) }}</b> 亩（只读）
        </el-descriptions-item>
      </el-descriptions>
      <el-alert v-if="!finishBusy && Number(finishSummary.totalAreaMu || 0) <= 0" type="error" :closable="false"
                show-icon style="margin-top: 12px"
                title="该作业单还没有登记飞行日志，日志汇总为 0 亩，不能完成；请先到飞行日志页登记架次" />
      <template #footer>
        <el-button @click="finishVisible = false" :disabled="finishBusy">取消</el-button>
        <el-button type="success" :loading="finishBusy"
                   :disabled="Number(finishSummary.totalAreaMu || 0) <= 0" @click="submitFinish">
          按日志汇总 {{ Number(finishSummary.totalAreaMu || 0).toFixed(2) }} 亩完成
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, inject, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { droneApi, flightLogApi, jobOrderApi, pilotApi, plotApi } from '../api'

const route = useRoute()
const router = useRouter()
const plant = inject('plant')
const orders = ref([])
const filterStatus = ref('')
const createVisible = ref(false)
const plotOptions = ref([])
const droneOptions = ref([])
const pilotOptions = ref([])
const editVisible = ref(false)
const editTarget = ref(null)
const editForm = ref({})
const focusId = ref(null)
const finishVisible = ref(false)
const finishTarget = ref(null)
const finishSummary = ref({})
const finishBusy = ref(false)

const statuses = [
  { value: 'PENDING', label: '待飞' },
  { value: 'WORKING', label: '作业中' },
  { value: 'DONE', label: '已完成' },
  { value: 'CANCELED', label: '已取消' }
]

const form = ref({
  orderNo: '', plotId: null, droneId: null, pilotId: null,
  formula: '吡蚜酮 25% + 磷酸二氢钾', dosePerMu: 1.2, plannedMu: 100, jobDate: '', weather: '晴'
})

const plannedTotal = computed(() => orders.value.reduce((s, r) => s + Number(r.plannedMu || 0), 0))
const loggedTotal = computed(() => orders.value.reduce((s, r) => s + Number(r.loggedMu || 0), 0))
const chemicalTotal = computed(() => orders.value.reduce((s, r) => s + Number(r.chemicalL || 0), 0))

function count(status) {
  return orders.value.filter((r) => r.status === status).length
}

function statusColor(status) {
  if (status === 'DONE') return '#2e7d32'
  if (status === 'WORKING') return '#7b1fa2'
  if (status === 'CANCELED') return '#c62828'
  return '#6b5f7d'
}

function droneOptionSuffix(d) {
  if (d.openHandling) return '（异常处置中，不可选）'
  return d.status === 'IDLE' ? '' : '（' + d.statusName + '）'
}

function rowClass({ row }) {
  return row.status === 'CANCELED' ? 'row-hold' : ''
}

function rowStyle({ row }) {
  return focusId.value && focusId.value === row.id
    ? { background: '#fff3e0', fontWeight: 700 }
    : {}
}

function clearFocus() {
  focusId.value = null
  router.replace({ path: '/orders' })
}

async function load() {
  orders.value = await jobOrderApi.list(filterStatus.value ? { status: filterStatus.value } : {})
  plant.reloadOverview()
}

async function takeoff(row) {
  try {
    await jobOrderApi.takeoff(row.id)
    ElMessage.success(row.orderNo + ' 已起飞')
  } catch (e) {
    // 拦截器已弹出后端返回的具体不一致项 / 复核提示，这里只需中断流转
    return
  }
  await load()
}

/** 从作业单直接进入对应批次（复核台会按作业单筛出其批次历史） */
function openMixing(row) {
  router.push({ path: '/mixing', query: { jobOrderId: row.id } })
}

function goCreateMixing(row) {
  router.push({ path: '/mixing', query: { jobOrderId: row.id, create: 1 } })
}

function openEdit(row) {
  editTarget.value = row
  editForm.value = {
    plotId: row.plotId,
    droneId: row.droneId,
    pilotId: row.pilotId,
    formula: row.formula,
    dosePerMu: Number(row.dosePerMu),
    plannedMu: Number(row.plannedMu),
    jobDate: row.jobDate,
    weather: row.weather
  }
  editVisible.value = true
}

async function submitEdit() {
  const f = editForm.value
  if (!f.plotId || !f.droneId || !f.pilotId || !f.formula || !f.jobDate) {
    ElMessage.warning('地块、机身、飞手、配方、作业日期都要填')
    return
  }
  await jobOrderApi.update(editTarget.value.id, f)
  ElMessage.success('作业单已更新；如配药依据变化，原批次已立即失效')
  editVisible.value = false
  await load()
}

/** 打开完成弹窗：只读拉取该作业单的飞行日志汇总，实际亩数不在前端填写 */
async function finish(row) {
  finishTarget.value = row
  finishSummary.value = {}
  finishVisible.value = true
  try {
    finishSummary.value = await flightLogApi.summary(row.id)
  } catch (e) {
    finishVisible.value = false
  }
}

/** 提交完成：不传亩数，后端按已落库日志汇总收口；按钮 loading 防止重复提交二次改统计 */
async function submitFinish() {
  if (finishBusy.value) return
  const row = finishTarget.value
  if (Number(finishSummary.value.totalAreaMu || 0) <= 0) {
    ElMessage.warning('飞行日志汇总为 0 亩，不能完成；请先登记架次')
    return
  }
  finishBusy.value = true
  try {
    await jobOrderApi.finish(row.id)
    ElMessage.success(row.orderNo + ' 已完成，实际亩数按飞行日志汇总回填')
    finishVisible.value = false
    await load()
  } catch (e) {
    // 拦截器已弹出后端的不一致 / 零汇总提示，保持弹窗开着供核对
  } finally {
    finishBusy.value = false
  }
}

async function cancel(row) {
  const { value } = await ElMessageBox.prompt('取消原因（天气 / 农户变更 / 机型故障）', '取消作业单', {
    inputPlaceholder: '如：午后风力超阈值'
  })
  await jobOrderApi.cancel(row.id, value)
  ElMessage.success(row.orderNo + ' 已取消')
  await load()
}

async function openCreate() {
  plotOptions.value = await plotApi.options()
  droneOptions.value = await droneApi.options()
  pilotOptions.value = await pilotApi.options()
  const d = new Date()
  form.value.orderNo = 'JO-' + String(d.getFullYear()).slice(2)
    + String(d.getMonth() + 1).padStart(2, '0') + String(d.getDate()).padStart(2, '0')
    + String(Math.floor(Math.random() * 90) + 10)
  createVisible.value = true
}

async function submitCreate() {
  const f = form.value
  if (!f.orderNo || !f.plotId || !f.droneId || !f.pilotId || !f.formula || !f.jobDate) {
    ElMessage.warning('单号、地块、机身、飞手、配方、作业日期都要填')
    return
  }
  await jobOrderApi.create(f)
  ElMessage.success('作业单已生成')
  createVisible.value = false
  await load()
}

onMounted(async () => {
  await load()
  // 下拉框数据先备好，新建弹窗一开就有选项
  plotOptions.value = await plotApi.options()
  droneOptions.value = await droneApi.options()
  pilotOptions.value = await pilotApi.options()
  if (route.query.focus) {
    const id = Number(route.query.focus)
    focusId.value = id
    const target = orders.value.find((o) => o.id === id)
    if (target && target.status === 'PENDING') {
      ElMessage.warning('该作业单绑定的机身已停飞维护，请改派一台在库机身')
      openEdit(target)
    }
  }
})
</script>
