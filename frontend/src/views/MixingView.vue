<template>
  <div>
    <div class="panel">
      <div class="panel-head">
        <h3>作业前配药复核台</h3>
        <span class="panel-sub">
          计划亩数 × 亩用量 = 总药量，按最大载荷最少分桶；每桶 &gt; 0、不超载、合计精确守恒；另一名三级/教员飞手复核锁定后方可起飞
        </span>
        <div class="panel-extra">
          <el-select v-model="currentPreparerId" placeholder="当前配药人（在队飞手）" filterable
                     style="width: 190px" @change="saveIdentity">
            <el-option v-for="p in pilotOptions" :key="p.id"
                       :label="p.pilotNo + ' · ' + p.name + ' · ' + p.licenseName" :value="p.id" />
          </el-select>
          <el-select v-model="currentReviewerId" placeholder="当前复核人（三级/教员）" filterable
                     style="width: 190px" @change="saveIdentity">
            <el-option v-for="p in reviewerOptions" :key="p.id"
                       :label="p.pilotNo + ' · ' + p.name + ' · ' + p.licenseName" :value="p.id" />
          </el-select>
          <el-select v-model="filterStatus" clearable placeholder="全部状态" style="width: 130px"
                     @change="onFilterChange">
            <el-option v-for="s in statuses" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
          <el-button size="small" @click="load">刷新</el-button>
          <el-button size="small" type="primary" @click="openCreate">发起配药</el-button>
        </div>
      </div>

      <div v-if="scopeOrderId" class="scope-bar">
        <el-tag type="primary" size="small" closable @close="clearScope">
          正在只看作业单 #{{ scopeOrderId }} 的批次（点 × 查看全部）
        </el-tag>
        <el-button size="small" link type="primary" @click="loadScoped">刷新该单</el-button>
      </div>

      <div class="summary-bar" style="margin-bottom: 12px">
        <div class="sb-item" v-for="s in statuses" :key="s.value">
          <span>{{ s.label }}</span><b>{{ count(s.value) }}</b>
        </div>
      </div>

      <el-table :data="batches" border size="small" :row-class-name="rowClass">
        <el-table-column prop="batchNo" label="批次号" width="126" />
        <el-table-column label="作业单 / 地块" min-width="200">
          <template #default="{ row }">
            <el-link type="primary" :underline="false" @click="openDetail(row)">{{ row.orderNo }}</el-link>
            <span class="muted"> · {{ row.plotNo }} · {{ row.farmerName }} · {{ row.jobDate }}</span>
          </template>
        </el-table-column>
        <el-table-column label="配方 / 口径" min-width="210">
          <template #default="{ row }">
            <div>{{ row.formulaSnapshot }}</div>
            <span class="muted">{{ row.plannedMuSnapshot }} 亩 × {{ row.dosePerMuSnapshot }} L/亩
              = {{ row.totalL }} L</span>
          </template>
        </el-table-column>
        <el-table-column label="无人机 / 载荷" width="140">
          <template #default="{ row }">
            {{ row.droneBodyNoSnapshot }} · {{ row.droneModelSnapshot }}
            <div class="muted">≤ {{ row.maxPayloadSnapshot }} L/桶 · {{ row.bucketCount }} 桶</div>
          </template>
        </el-table-column>
        <el-table-column label="配药人 / 复核人" width="150">
          <template #default="{ row }">
            <div>{{ row.preparedPilotNo }} {{ row.preparedPilotName }}</div>
            <div class="muted">核：{{ row.reviewerName || '—' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="tagType(row.status)" size="small">{{ row.statusName }}</el-tag>
            <div v-if="row.status === 'EXPIRED'" class="muted" style="color:#c62828">
              {{ row.expireReason }}
            </div>
            <div v-if="row.status === 'REJECTED' && row.rejectReason" class="muted" style="color:#c62828">
              驳回：{{ row.rejectReason }}
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openDetail(row)">分桶/记录</el-button>
            <el-button v-if="canAdjust(row)" size="small" type="primary" @click="openEdit(row)">改桶</el-button>
            <el-button v-if="canSubmit(row)" size="small" type="warning" @click="submit(row)">提交复核</el-button>
            <template v-if="row.status === 'PENDING_REVIEW'">
              <el-button size="small" type="success" @click="lock(row)">复核锁定</el-button>
              <el-button size="small" type="danger" @click="reject(row)">驳回</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 发起配药 -->
    <el-dialog v-model="createVisible" title="从待飞作业单发起配药" width="640">
      <el-form label-width="120px">
        <el-form-item label="待飞作业单">
          <el-select v-model="createForm.jobOrderId" filterable placeholder="只列待飞作业单" style="width: 100%"
                     @change="onCreateOrderChange">
            <el-option v-for="o in pendingOrders" :key="o.id"
                       :label="`${o.orderNo} · ${o.plotNo} · ${o.farmerName} · ${o.droneBodyNo} 载荷${o.dronePayload}L · ${o.plannedMu}亩×${o.dosePerMu}L`"
                       :value="o.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="配药人">
          <el-select v-model="createForm.preparedPilotId" filterable placeholder="在队飞手" style="width: 100%">
            <el-option v-for="p in pilotOptions" :key="p.id"
                       :label="p.pilotNo + ' · ' + p.name + ' · ' + p.licenseName" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-alert v-if="createPreview" type="info" :closable="false" style="margin: 8px 0">
          <template #title>
            总药量 <b>{{ createPreview.total }}</b> L，{{ createPreview.payload }} L 载荷最少需要
            <b>{{ createPreview.buckets.length }}</b> 桶（{{ createPreview.buckets.join(' / ') }} L），
            余量从第 1 桶起以 0.01L 均摊，不留给最后一桶
          </template>
        </el-alert>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCreate">按默认分桶发起</el-button>
      </template>
    </el-dialog>

    <!-- 分桶编辑 -->
    <el-dialog v-model="editVisible" title="调整各桶药量" width="560">
      <div v-if="editBatch" class="muted" style="margin-bottom: 10px">
        {{ editBatch.batchNo }} · {{ editBatch.droneBodyNoSnapshot }} 单桶上限
        {{ editBatch.maxPayloadSnapshot }} L，计划总药量 {{ editBatch.totalL }} L
      </div>
      <el-table :data="editRows" border size="small">
        <el-table-column label="桶序" width="70">
          <template #default="{ $index }">第 {{ $index + 1 }} 桶</template>
        </el-table-column>
        <el-table-column label="药量（L）">
          <template #default="{ row }">
            <el-input-number v-model="row.amount" :min="0.01" :max="Number(editBatch.maxPayloadSnapshot)"
                             :step="0.1" :precision="2" :controls="false" style="width: 130px" />
          </template>
        </el-table-column>
        <el-table-column label="校验" width="110">
          <template #default="{ row }">
            <span v-if="!(row.amount > 0)" style="color:#c62828">必须 &gt; 0</span>
            <span v-else-if="Number(row.amount) > Number(editBatch.maxPayloadSnapshot)" style="color:#c62828">
              超载
            </span>
            <span v-else style="color:#2e7d32">合格</span>
          </template>
        </el-table-column>
        <el-table-column width="80">
          <template #default="{ $index }">
            <el-button v-if="editRows.length > 1" size="small" link type="danger"
                       @click="editRows.splice($index, 1)">删</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div style="margin-top: 10px">
        <el-button size="small" @click="addBucket">加一桶</el-button>
        <el-button size="small" @click="resetAuto">恢复最少桶数默认方案</el-button>
      </div>
      <el-alert style="margin-top: 10px" :type="editDelta === 0 ? 'success' : 'error'" :closable="false"
                :title="`合计 ${editSum.toFixed(2)} L / 计划 ${editBatch.totalL} L`
                  + (editDelta === 0 ? '，总量守恒' : `，相差 ${editDelta > 0 ? '+' : ''}${editDelta.toFixed(2)} L，必须改到分毫不差`)" />
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :disabled="editDelta !== 0 || !editValid" @click="saveBuckets">保存分桶</el-button>
      </template>
    </el-dialog>

    <!-- 批次详情抽屉 -->
    <el-drawer v-model="detailVisible" size="560px" :title="detail ? detail.batchNo + ' · 配药复核详情' : ''">
      <template v-if="detail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="状态">
            <el-tag :type="tagType(detail.status)" size="small">{{ detail.statusName }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="作业单">{{ detail.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="地块">{{ detail.plotNo }} · {{ detail.farmerName }}</el-descriptions-item>
          <el-descriptions-item label="作业日期">{{ detail.jobDate }}</el-descriptions-item>
          <el-descriptions-item label="药剂配方" :span="2">{{ detail.formulaSnapshot }}</el-descriptions-item>
          <el-descriptions-item label="计划亩数">{{ detail.plannedMuSnapshot }} 亩</el-descriptions-item>
          <el-descriptions-item label="亩用量">{{ detail.dosePerMuSnapshot }} L/亩</el-descriptions-item>
          <el-descriptions-item label="总药量">{{ detail.totalL }} L</el-descriptions-item>
          <el-descriptions-item label="分桶">{{ detail.bucketCount }} 桶，合计 {{ detail.bucketSum }} L</el-descriptions-item>
          <el-descriptions-item label="无人机">{{ detail.droneBodyNoSnapshot }} {{ detail.droneModelSnapshot }}</el-descriptions-item>
          <el-descriptions-item label="最大载荷">{{ detail.maxPayloadSnapshot }} L/桶</el-descriptions-item>
          <el-descriptions-item label="配药人">{{ detail.preparedPilotNo }} {{ detail.preparedPilotName }}</el-descriptions-item>
          <el-descriptions-item label="复核人">{{ detail.reviewerName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="提交时间">{{ fmt(detail.submittedAt) }}</el-descriptions-item>
          <el-descriptions-item label="锁定时间">{{ fmt(detail.lockedAt) }}</el-descriptions-item>
        </el-descriptions>

        <el-alert v-if="detail.status === 'EXPIRED'" type="error" :closable="false" style="margin-top: 12px"
                  :title="'失效原因：' + (detail.expireReason || '—')" />
        <el-alert v-if="detail.status === 'REJECTED'" type="error" :closable="false" style="margin-top: 12px"
                  :title="'驳回原因：' + (detail.rejectReason || '—')" />
        <el-alert v-if="detail.status !== 'EXPIRED' && !detail.snapshotConsistent" type="warning"
                  :closable="false" style="margin-top: 12px" title="快照与当前作业口径已不一致，批次将不能起飞：">
          <div v-for="(d, i) in detail.snapshotDiffs" :key="i">· {{ d }}</div>
        </el-alert>
        <el-alert v-if="detail.status === 'LOCKED' && detail.snapshotConsistent" type="success"
                  :closable="false" style="margin-top: 12px" title="快照一致、分桶守恒，可以起飞" />

        <h4 style="margin: 16px 0 8px">分桶明细（每桶 &gt; 0、≤ 载荷）</h4>
        <el-table :data="detail.buckets" border size="small">
          <el-table-column prop="seqNo" label="桶序" width="70" />
          <el-table-column prop="amountL" label="药量（L）" />
          <el-table-column label="占载荷">
            <template #default="{ row }">
              <el-progress :percentage="payloadPct(row)" :stroke-width="10"
                           :color="payloadPct(row) > 100 ? '#c62828' : '#2e7d32'" />
            </template>
          </el-table-column>
        </el-table>

        <h4 style="margin: 16px 0 8px">复核记录</h4>
        <el-timeline>
          <el-timeline-item v-for="r in detail.reviews" :key="r.id"
                            :type="timelineType(r.action)" :timestamp="fmt(r.createdAt)">
            <b>{{ r.actionName }}</b> · {{ r.actorName || '系统' }}
            <div v-if="r.comment" class="muted">{{ r.comment }}</div>
          </el-timeline-item>
        </el-timeline>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { droneApi, jobOrderApi, mixingApi } from '../api'

const route = useRoute()
const batches = ref([])
const filterStatus = ref('')
const pilotOptions = ref([])
const reviewerOptions = ref([])
const pendingOrders = ref([])
const droneOptions = ref([])

const currentPreparerId = ref(null)
const currentReviewerId = ref(null)

const createVisible = ref(false)
const createForm = ref({ jobOrderId: null, preparedPilotId: null })
const createPreview = ref(null)

const editVisible = ref(false)
const editBatch = ref(null)
const editRows = ref([])

const detailVisible = ref(false)
const detail = ref(null)

const statuses = [
  { value: 'DRAFT', label: '草稿' },
  { value: 'PENDING_REVIEW', label: '待复核' },
  { value: 'LOCKED', label: '已锁定' },
  { value: 'REJECTED', label: '已驳回' },
  { value: 'EXPIRED', label: '已失效' }
]

function count(status) {
  return batches.value.filter((b) => b.status === status).length
}

function tagType(status) {
  return { DRAFT: 'info', PENDING_REVIEW: 'warning', LOCKED: 'success', REJECTED: 'danger', EXPIRED: 'info' }[status]
}

function rowClass({ row }) {
  return row.status === 'EXPIRED' ? 'row-hold' : ''
}

function fmt(t) {
  return t ? String(t).replace('T', ' ').slice(0, 16) : '—'
}

function timelineType(action) {
  return { CREATE: 'primary', SUBMIT: 'warning', REJECT: 'danger', LOCK: 'success', EXPIRE: 'info' }[action]
}

function payloadPct(row) {
  return Math.round((Number(row.amountL) / Number(row.maxPayload)) * 100)
}

/** 与后端同一口径的默认分桶：ceil(总量/载荷) 桶，余量从第 1 桶起均摊 */
function autoPlan(total, payload) {
  const cents = Math.round(total * 100)
  const n = Math.ceil(total / payload)
  const base = Math.floor(cents / n)
  const rem = cents % n
  return Array.from({ length: n }, (_, i) => Number(((base + (i < rem ? 1 : 0)) / 100).toFixed(2)))
}

function canAdjust(row) {
  return (row.status === 'DRAFT' || row.status === 'REJECTED')
    && currentPreparerId.value === row.preparedPilotId
}

function canSubmit(row) {
  return (row.status === 'DRAFT' || row.status === 'REJECTED')
    && currentPreparerId.value === row.preparedPilotId
}

const editSum = computed(() => editRows.value.reduce((s, r) => s + (Number(r.amount) || 0), 0))
const editDelta = computed(() => editBatch.value
  ? Math.round((editSum.value - Number(editBatch.value.totalL)) * 100) / 100
  : 0)
const editValid = computed(() => editRows.value.length > 0
  && editRows.value.every((r) => Number(r.amount) > 0
    && Number(r.amount) <= Number(editBatch.value.maxPayloadSnapshot)))

function saveIdentity() {
  localStorage.setItem('mixing-preparer', String(currentPreparerId.value || ''))
  localStorage.setItem('mixing-reviewer', String(currentReviewerId.value || ''))
}

async function load() {
  if (scopeOrderId.value) {
    await loadScoped()
    return
  }
  batches.value = await mixingApi.list(filterStatus.value)
}

function onFilterChange() {
  // 作业单直达模式下状态筛选只在前端过；全局模式走后端按状态查
  if (scopeOrderId.value) {
    loadScoped()
  } else {
    load()
  }
}

const scopeOrderId = ref(null)

async function loadScoped() {
  if (!scopeOrderId.value) {
    await load()
    return
  }
  const rows = await mixingApi.byOrder(scopeOrderId.value)
  batches.value = filterStatus.value ? rows.filter((b) => b.status === filterStatus.value) : rows
}

function clearScope() {
  scopeOrderId.value = null
  filterStatus.value = ''
  load()
}

async function openCreate() {
  if (!currentPreparerId.value) {
    ElMessage.warning('请先在顶部选择当前配药人')
    return
  }
  const [orders, drones] = await Promise.all([
    jobOrderApi.list({ status: 'PENDING' }), droneApi.options()
  ])
  droneOptions.value = drones
  pendingOrders.value = orders.map((o) => ({
    ...o,
    dronePayload: drones.find((d) => d.id === o.droneId)?.maxPayload ?? null
  }))
  createForm.value = { jobOrderId: pendingOrders.value[0]?.id || null, preparedPilotId: currentPreparerId.value }
  onCreateOrderChange()
  createVisible.value = true
}

function onCreateOrderChange() {
  const order = pendingOrders.value.find((o) => o.id === createForm.value.jobOrderId)
  createPreview.value = null
  if (order && order.dronePayload) {
    const total = Number(order.plannedMu) * Number(order.dosePerMu)
    createPreview.value = {
      total: total.toFixed(2),
      payload: order.dronePayload,
      buckets: autoPlan(total, Number(order.dronePayload))
    }
  }
}

async function submitCreate() {
  if (!createForm.value.jobOrderId || !createForm.value.preparedPilotId) {
    ElMessage.warning('作业单与配药人都要选')
    return
  }
  const batch = await mixingApi.create({
    jobOrderId: createForm.value.jobOrderId,
    preparedPilotId: createForm.value.preparedPilotId
  })
  ElMessage.success('配药批次已发起，已按最少桶数自动分桶')
  createVisible.value = false
  await load()
  await openDetail({ id: batch.id })
}

async function openEdit(row) {
  const full = await mixingApi.detail(row.id)
  editBatch.value = full
  editRows.value = full.buckets.map((b) => ({ amount: Number(b.amountL) }))
  editVisible.value = true
}

function addBucket() {
  editRows.value.push({ amount: 0.01 })
}

function resetAuto() {
  const b = editBatch.value
  // 前端无载荷数字时恢复等比分摊：仍由后端做守恒与超载的最终校验
  const n = Math.ceil(Number(b.totalL) / Number(b.maxPayloadSnapshot))
  editRows.value = autoPlan(Number(b.totalL), Number(b.maxPayloadSnapshot)).map((amount) => ({ amount }))
}

async function saveBuckets() {
  await mixingApi.updateBuckets(editBatch.value.id, currentPreparerId.value,
    editRows.value.map((r) => Number(r.amount)))
  ElMessage.success('分桶已保存：每桶不超载、合计与总药量一致')
  editVisible.value = false
  await load()
  if (detail.value) await openDetail({ id: editBatch.value.id })
}

async function submit(row) {
  await mixingApi.submit(row.id, currentPreparerId.value)
  ElMessage.success('已提交复核')
  await load()
}

async function reject(row) {
  if (!currentReviewerId.value) {
    ElMessage.warning('请先在顶部选择当前复核人（三级/教员）')
    return
  }
  const { value } = await ElMessageBox.prompt('驳回原因（配药人将据此修改后再次提交）', '驳回复核', {
    inputPlaceholder: '如：第 2 桶超过载荷、合计与总药量不符',
    inputValidator: (v) => (v && v.trim() ? true : '驳回必须填写原因')
  })
  await mixingApi.reject(row.id, currentReviewerId.value, value.trim())
  ElMessage.success('已驳回')
  await load()
}

async function lock(row) {
  if (!currentReviewerId.value) {
    ElMessage.warning('请先在顶部选择当前复核人（三级/教员）')
    return
  }
  try {
    await mixingApi.lock(row.id, currentReviewerId.value)
    ElMessage.success('复核通过，批次已锁定')
  } finally {
    await load()
    if (detail.value && detail.value.id === row.id) await openDetail({ id: row.id })
  }
}

async function openDetail(row) {
  detail.value = await mixingApi.detail(row.id)
  detailVisible.value = true
}

onMounted(async () => {
  const [pilots, reviewers] = await Promise.all([
    mixingApi.pilotOptions(), mixingApi.reviewerOptions()
  ])
  pilotOptions.value = pilots
  reviewerOptions.value = reviewers
  const preparer = localStorage.getItem('mixing-preparer')
  const reviewer = localStorage.getItem('mixing-reviewer')
  if (preparer) currentPreparerId.value = Number(preparer)
  if (reviewer) currentReviewerId.value = Number(reviewer)

  // 从作业单“配药批次 / 发起配药”带参直达：先只看这张单的批次历史
  const jobOrderId = route.query.jobOrderId ? Number(route.query.jobOrderId) : null
  if (jobOrderId) {
    scopeOrderId.value = jobOrderId
    await loadScoped()
    if (route.query.create) {
      await openCreateFor(jobOrderId)
    }
  } else {
    await load()
  }
})

/** 作业单直达发起：预选该单、预览默认分桶 */
async function openCreateFor(jobOrderId) {
  if (!currentPreparerId.value) {
    ElMessage.info('请先在顶部选择当前配药人，再发起配药')
    return
  }
  const [orders, drones] = await Promise.all([
    jobOrderApi.list({ status: 'PENDING' }), droneApi.options()
  ])
  droneOptions.value = drones
  pendingOrders.value = orders.map((o) => ({
    ...o,
    dronePayload: drones.find((d) => d.id === o.droneId)?.maxPayload ?? null
  }))
  createForm.value = { jobOrderId, preparedPilotId: currentPreparerId.value }
  onCreateOrderChange()
  createVisible.value = true
}
</script>
