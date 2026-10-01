<template>
  <div>
    <div class="panel">
      <div class="panel-head">
        <h3>异常架次停飞处置</h3>
        <span class="panel-sub">
          异常架次登记即自动立案进入「待处置」；值班主管可选择继续观察、停飞机身或解除。停飞后机身转维护，待飞作业单须改派；解除须填写处置结论
        </span>
        <div class="panel-extra">
          <el-select v-model="supervisorId" placeholder="当前值班主管" filterable style="width: 210px"
                     @change="saveSupervisor">
            <el-option v-for="p in supervisors" :key="p.id"
                       :label="p.pilotNo + ' · ' + p.name + ' · ' + p.licenseName" :value="p.id" />
          </el-select>
          <el-select v-model="droneId" clearable filterable placeholder="按机身筛选" style="width: 170px"
                     @change="load">
            <el-option v-for="d in droneOptions" :key="d.id"
                       :label="d.bodyNo + ' · ' + d.model" :value="d.id" />
          </el-select>
          <el-button size="small" @click="load">刷新</el-button>
        </div>
      </div>

      <div v-if="scopeDrone" class="scope-bar">
        <el-tag type="warning" size="small" closable @close="clearDroneScope">
          正在只看机身 {{ scopeDrone.bodyNo }} 的处置记录（点 × 查看全部）
        </el-tag>
      </div>

      <div class="summary-bar" style="margin-bottom: 12px">
        <div class="sb-item" v-for="s in statuses" :key="s.value" :class="s.cls">
          <span>{{ s.label }}</span><b>{{ count(s.value) }}</b>
        </div>
      </div>

      <el-radio-group v-model="filterStatus" size="small" style="margin-bottom: 10px" @change="load">
        <el-radio-button label="">全部</el-radio-button>
        <el-radio-button v-for="s in statuses" :key="s.value" :label="s.value">{{ s.label }}</el-radio-button>
      </el-radio-group>

      <el-table :data="handlings" border size="small">
        <el-table-column prop="handlingNo" label="处置单号" width="132" />
        <el-table-column label="异常架次" min-width="230">
          <template #default="{ row }">
            <el-link type="primary" :underline="false" @click="openDetail(row)">{{ row.orderNo }} #{{ row.seqNo }}</el-link>
            <span class="muted"> · {{ row.plotNo }} · {{ row.pilotName }} 飞</span>
            <div style="color:#c62828">{{ row.abnormalDesc }}</div>
          </template>
        </el-table-column>
        <el-table-column label="机身" width="150">
          <template #default="{ row }">
            <el-link type="primary" :underline="false" @click="goDrone(row.droneId)">{{ row.droneBodyNo }}</el-link>
            <span class="muted"> · {{ row.droneModel }}</span>
            <el-tag size="small" :type="droneTagType(row.droneStatus)" style="margin-left:4px">
              {{ row.droneStatusName }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="立案人" width="110">
          <template #default="{ row }">{{ row.registerName || '系统' }}</template>
        </el-table-column>
        <el-table-column label="值班主管" width="100">
          <template #default="{ row }">{{ row.supervisorName || '—' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="tagType(row.status)" size="small">{{ row.statusName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="立案时间" width="152">
          <template #default="{ row }">{{ fmt(row.registeredAt) }}</template>
        </el-table-column>
        <el-table-column label="处置结论 / 意见" min-width="200">
          <template #default="{ row }">
            <span v-if="row.status === 'CLOSED'">{{ row.conclusion }}</span>
            <span v-else-if="row.status === 'GROUNDED'" style="color:#ef6c00">{{ row.groundComment }}</span>
            <span v-else-if="row.status === 'OBSERVING'" style="color:#0277bd">{{ row.observeComment }}</span>
            <span v-else class="muted">等待值班主管处置</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="270" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openDetail(row)">记录</el-button>
            <template v-if="row.status === 'PENDING' || row.status === 'OBSERVING'">
              <el-button size="small" type="warning" @click="openAct(row, 'observe')">继续观察</el-button>
              <el-button size="small" type="danger" @click="openAct(row, 'ground')">停飞机身</el-button>
            </template>
            <el-button v-if="row.status === 'GROUNDED'" size="small" type="success"
                       @click="openAct(row, 'release')">解除</el-button>
            <el-button v-if="row.status === 'PENDING'" size="small" type="success"
                       @click="openAct(row, 'release')">直接解除</el-button>
            <el-tag v-if="row.status === 'CLOSED'" size="small" type="info">已闭环</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 处置详情抽屉 -->
    <el-drawer v-model="detailVisible" size="560px" :title="detail ? '处置单 ' + detail.handlingNo : '处置详情'">
      <div v-if="detail" class="detail-box">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="状态">
            <el-tag :type="tagType(detail.status)" size="small">{{ detail.statusName }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="机身">
            <el-link type="primary" :underline="false" @click="goDrone(detail.droneId)">
              {{ detail.droneBodyNo }} · {{ detail.droneModel }}
            </el-link>
            <el-tag size="small" :type="droneTagType(detail.droneStatus)" style="margin-left:4px">
              {{ detail.droneStatusName }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="作业单">{{ detail.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="地块 / 飞手">{{ detail.plotNo }} · {{ detail.pilotName }}</el-descriptions-item>
          <el-descriptions-item label="架次">第 {{ detail.seqNo }} 架次</el-descriptions-item>
          <el-descriptions-item label="起飞时间">{{ fmt(detail.takeoffAt) }}</el-descriptions-item>
          <el-descriptions-item label="立案人">{{ detail.registerName || '系统自动立案' }}</el-descriptions-item>
          <el-descriptions-item label="值班主管">{{ detail.supervisorName || '尚未处置' }}</el-descriptions-item>
          <el-descriptions-item label="立案时间" :span="2">{{ fmt(detail.registeredAt) }}</el-descriptions-item>
          <el-descriptions-item label="异常情况" :span="2">
            <span style="color:#c62828">{{ detail.abnormalDesc }}</span>
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.observeComment" label="观察意见" :span="2">
            <span style="color:#0277bd">{{ detail.observeComment }}</span>
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.groundComment" label="停飞意见" :span="2">
            <span style="color:#ef6c00">{{ detail.groundComment }}</span>
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.conclusion" label="处置结论" :span="2">
            <b style="color:#2e7d32">{{ detail.conclusion }}</b>
          </el-descriptions-item>
        </el-descriptions>

        <el-alert v-if="detail.status === 'GROUNDED'" type="error" :closable="false" show-icon
                  style="margin: 12px 0"
                  title="机身已停飞维护：所有尚未起飞的作业单不得再选用该机，已有待飞单必须改派后才能起飞" />
        <el-alert v-if="detail.status !== 'CLOSED' && detail.otherOpenCount > 0" type="warning"
                  :closable="false" show-icon style="margin: 12px 0"
                  :title="`该机身另有 ${detail.otherOpenCount} 条未结束处置，本单即使解除，机身也会保持维护直到全部闭环`" />

        <div v-if="detail.pendingOrders.length" class="pending-box">
          <div class="pb-title">该机身尚未起飞的作业单（{{ detail.pendingOrders.length }}）— 请改派：</div>
          <div v-for="o in detail.pendingOrders" :key="o.id" class="pb-item">
            <el-button size="small" type="danger" plain @click="goOrder(o.id)">
              {{ o.orderNo }} · {{ o.plotNo }} · {{ o.jobDate }} 改派 →
            </el-button>
          </div>
        </div>

        <div class="tl-title">处置留痕</div>
        <el-timeline>
          <el-timeline-item v-for="a in detail.actions" :key="a.id"
                            :type="actionColor(a.action)" :timestamp="fmt(a.createdAt)" placement="top">
            <b>{{ a.actionName }}</b>
            <span class="muted"> · {{ a.actorName || '系统' }}</span>
            <div v-if="a.comment" class="tl-comment">{{ a.comment }}</div>
          </el-timeline-item>
        </el-timeline>
      </div>

      <template #footer>
        <template v-if="detail && detail.status !== 'CLOSED'">
          <el-button v-if="detail.status === 'PENDING' || detail.status === 'OBSERVING'"
                     type="warning" @click="openAct(detail, 'observe')">继续观察</el-button>
          <el-button v-if="detail.status === 'PENDING' || detail.status === 'OBSERVING'"
                     type="danger" @click="openAct(detail, 'ground')">停飞机身</el-button>
          <el-button type="success" @click="openAct(detail, 'release')">
            {{ detail.status === 'GROUNDED' ? '解除停飞' : '解除' }}
          </el-button>
        </template>
      </template>
    </el-drawer>

    <!-- 处置动作弹窗 -->
    <el-dialog v-model="actVisible" :title="actTitle" width="520">
      <el-alert v-if="actKind === 'ground'" type="error" :closable="false" show-icon style="margin-bottom: 12px"
        title="确认停飞后，关联无人机将立即转为「维护中」；所有尚未起飞的作业单不得再选用该机，已有待飞单须改派" />
      <el-alert v-if="actKind === 'release'" type="success" :closable="false" show-icon style="margin-bottom: 12px"
        title="解除后处置闭环；若该机身没有其他未结束处置，机身将恢复「在库」，否则继续保持维护" />
      <el-form label-width="100px">
        <el-form-item label="值班主管">
          <el-select v-model="supervisorId" filterable placeholder="选择值班主管（三级/教员）" style="width: 100%">
            <el-option v-for="p in supervisors" :key="p.id"
                       :label="p.pilotNo + ' · ' + p.name + ' · ' + p.licenseName" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="actKind === 'release' ? '处置结论' : actKind === 'ground' ? '停飞意见' : '观察意见'">
          <el-input v-model="actComment" type="textarea" :rows="4"
                    :placeholder="actKind === 'release'
                      ? '必填：故障检查情况、处理措施与能否恢复作业的结论'
                      : actKind === 'ground'
                        ? '停飞原因与检修要求（待飞作业单会自动追加到改派提示）'
                        : '继续观察的重点与后续架次要求'" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="actVisible = false">取消</el-button>
        <el-button :type="actKind === 'ground' ? 'danger' : actKind === 'release' ? 'success' : 'warning'"
                   @click="submitAct">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, inject, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { droneApi, handlingApi } from '../api'

const route = useRoute()
const router = useRouter()
const plant = inject('plant')

const handlings = ref([])
const supervisors = ref([])
const droneOptions = ref([])
const filterStatus = ref('')
const droneId = ref(null)
const supervisorId = ref(null)

const detailVisible = ref(false)
const detail = ref(null)
const actVisible = ref(false)
const actTarget = ref(null)
const actKind = ref('observe')
const actComment = ref('')

const statuses = [
  { value: 'PENDING', label: '待处置', cls: 'is-alert-card' },
  { value: 'OBSERVING', label: '继续观察', cls: '' },
  { value: 'GROUNDED', label: '停飞中', cls: 'is-warn-card' },
  { value: 'CLOSED', label: '已解除', cls: '' }
]

const scopeDrone = computed(() => droneOptions.value.find((d) => d.id === droneId.value) || null)

const actTitle = computed(() => {
  if (actKind.value === 'ground') return '停飞机身'
  if (actKind.value === 'release') return '解除处置'
  return '继续观察'
})

const fmt = (value) => (value ? String(value).replace('T', ' ') : '—')

function count(status) {
  return handlings.value.filter((h) => h.status === status).length
}

function tagType(status) {
  return { PENDING: 'danger', OBSERVING: 'warning', GROUNDED: 'info', CLOSED: 'success' }[status] || ''
}

function droneTagType(status) {
  return { IDLE: 'success', WORKING: 'warning', MAINT: 'danger', DISABLED: 'info' }[status] || ''
}

function actionColor(action) {
  return { REGISTER: 'primary', OBSERVE: 'warning', GROUND: 'danger', RELEASE: 'success' }[action] || ''
}

async function load() {
  const params = {}
  if (filterStatus.value) params.status = filterStatus.value
  if (droneId.value) params.droneId = droneId.value
  handlings.value = await handlingApi.list(params)
  plant.reloadOverview()
}

async function loadBase() {
  const [sups, drones] = await Promise.all([
    handlingApi.supervisorOptions(),
    droneApi.list({})
  ])
  supervisors.value = sups
  droneOptions.value = drones
  const saved = Number(localStorage.getItem('plant-supervisor-id') || 0)
  if (saved && sups.some((p) => p.id === saved)) {
    supervisorId.value = saved
  } else if (sups.length) {
    supervisorId.value = sups[0].id
  }
}

function saveSupervisor(id) {
  localStorage.setItem('plant-supervisor-id', String(id))
}

function clearDroneScope() {
  droneId.value = null
  router.replace({ path: '/handlings' })
  load()
}

async function openDetail(row) {
  detail.value = await handlingApi.detail(row.id)
  detailVisible.value = true
}

function openAct(row, kind) {
  actTarget.value = row
  actKind.value = kind
  actComment.value = kind === 'release' ? (row.conclusion || '') : kind === 'ground' ? (row.groundComment || '') : (row.observeComment || '')
  actVisible.value = true
}

async function submitAct() {
  if (!supervisorId.value) {
    ElMessage.warning('请先选择当前值班主管')
    return
  }
  if (actKind.value === 'release' && !actComment.value.trim()) {
    ElMessage.warning('解除前必须填写处置结论')
    return
  }
  if (actKind.value !== 'release' && !actComment.value.trim()) {
    ElMessage.warning(actKind.value === 'ground' ? '请填写停飞意见' : '请填写观察意见')
    return
  }
  const id = actTarget.value.id
  if (actKind.value === 'observe') {
    await handlingApi.observe(id, supervisorId.value, actComment.value.trim())
    ElMessage.success('已登记继续观察，机身状态不变')
  } else if (actKind.value === 'ground') {
    await handlingApi.ground(id, supervisorId.value, actComment.value.trim())
    ElMessage.success('已停飞，机身转入维护；待飞作业单须改派')
  } else {
    await handlingApi.release(id, supervisorId.value, actComment.value.trim())
    ElMessage.success('处置已解除，结论已留档')
  }
  actVisible.value = false
  detailVisible.value = false
  await load()
}

function goDrone(id) {
  detailVisible.value = false
  router.push({ path: '/drones', query: { droneId: id } })
}

function goOrder(id) {
  detailVisible.value = false
  router.push({ path: '/orders', query: { focus: id } })
}

onMounted(async () => {
  await loadBase()
  if (route.query.status) filterStatus.value = String(route.query.status)
  if (route.query.droneId) droneId.value = Number(route.query.droneId)
  if (route.query.id) {
    load()
    detail.value = await handlingApi.detail(Number(route.query.id))
    detailVisible.value = true
  } else {
    await load()
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
.detail-box {
  padding: 0 4px;
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
.tl-title {
  font-weight: 700;
  margin: 14px 0 10px;
}
.tl-comment {
  color: #6b5f7d;
  margin-top: 2px;
}
</style>
