<template>
  <div>
    <div class="panel">
      <div class="panel-head">
        <h3>飞手</h3>
        <span class="panel-sub">执照等级 · 累计架次与作业亩数 · 山地地块只能派高级及以上执照</span>
        <div class="panel-extra">
          <el-select v-model="filterStatus" clearable placeholder="状态筛选" style="width: 140px" @change="load">
            <el-option v-for="s in statuses" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
          <el-select v-model="filterLevel" clearable placeholder="执照等级" style="width: 140px" @change="load">
            <el-option v-for="l in levels" :key="l.value" :label="l.label" :value="l.value" />
          </el-select>
          <el-button size="small" @click="load">刷新</el-button>
          <el-button size="small" type="primary" @click="createVisible = true">飞手入队</el-button>
        </div>
      </div>

      <div class="summary-bar" style="margin-bottom: 12px">
        <div class="sb-item"><span>在册飞手</span><b>{{ pilots.length }}</b></div>
        <div class="sb-item"><span>在队</span><b>{{ pilots.filter((p) => p.status === 'ACTIVE').length }}</b></div>
        <div class="sb-item"><span>累计架次</span><b>{{ totalSorts }}</b></div>
        <div class="sb-item"><span>累计作业亩数</span><b>{{ totalArea.toFixed(1) }}</b></div>
        <div class="sb-item"><span>教员 / 高级</span><b>{{ pilots.filter((p) => p.licenseLevel === 'INSTRUCTOR' || p.licenseLevel === 'LEVEL3').length }}</b></div>
      </div>

      <div class="card-grid">
        <div v-for="p in pilots" :key="p.id" class="unit-card">
          <div class="uc-head">
            <span class="uc-icon">🧑‍✈️</span>
            <div>
              <div class="uc-id">{{ p.name }}</div>
              <div class="uc-sub">{{ p.pilotNo }} · {{ p.phone }}</div>
            </div>
            <span class="badge-license" :style="{ background: p.licenseColor }">{{ p.licenseName }}</span>
          </div>

          <div class="kv-grid">
            <div class="kv"><span>累计架次</span><b>{{ p.totalSorts }}</b></div>
            <div class="kv"><span>累计作业亩数</span><b>{{ p.totalAreaMu }}</b></div>
            <div class="kv"><span>在队状态</span><b>{{ p.statusName }}</b></div>
            <div class="kv"><span>在手作业单</span><b>{{ p.openOrders }}</b></div>
          </div>

          <div class="tag-line">
            <span v-if="p.currentPlotNo" class="mini-tag">正在喷：{{ p.currentPlotNo }}</span>
            <span v-else class="mini-tag">当前无在手作业</span>
          </div>

          <div class="card-actions">
            <el-select v-model="statusMap[p.id]" placeholder="切换状态" size="small" style="width: 130px" @change="changeStatus(p)">
              <el-option v-for="s in statuses.filter((x) => x.value !== p.status)" :key="s.value" :label="s.label" :value="s.value" />
            </el-select>
          </div>
        </div>
      </div>
    </div>

    <el-dialog v-model="createVisible" title="飞手入队" width="500">
      <el-form :model="form" label-width="100px">
        <el-form-item label="飞手编号"><el-input v-model="form.pilotNo" placeholder="如 PL-11" /></el-form-item>
        <el-form-item label="姓名"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="手机号"><el-input v-model="form.phone" placeholder="11 位手机号" /></el-form-item>
        <el-form-item label="执照等级">
          <el-select v-model="form.licenseLevel" style="width: 100%">
            <el-option v-for="l in levels" :key="l.value" :label="l.label" :value="l.value" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCreate">入队</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, inject, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { pilotApi } from '../api'

const plant = inject('plant')
const pilots = ref([])
const filterStatus = ref('')
const filterLevel = ref('')
const statusMap = reactive({})
const createVisible = ref(false)

const statuses = [
  { value: 'ACTIVE', label: '在队' },
  { value: 'LEAVE', label: '请假' },
  { value: 'RESIGNED', label: '离职' }
]
const levels = [
  { value: 'LEVEL1', label: '初级' },
  { value: 'LEVEL2', label: '中级' },
  { value: 'LEVEL3', label: '高级' },
  { value: 'INSTRUCTOR', label: '教员' }
]

const form = ref({ pilotNo: '', name: '', phone: '', licenseLevel: 'LEVEL1' })

const totalSorts = computed(() => pilots.value.reduce((sum, p) => sum + Number(p.totalSorts || 0), 0))
const totalArea = computed(() => pilots.value.reduce((sum, p) => sum + Number(p.totalAreaMu || 0), 0))

async function load() {
  const params = {}
  if (filterStatus.value) params.status = filterStatus.value
  if (filterLevel.value) params.licenseLevel = filterLevel.value
  pilots.value = await pilotApi.list(params)
  pilots.value.forEach((p) => {
    statusMap[p.id] = ''
  })
  plant.reloadOverview()
}

async function changeStatus(pilot) {
  const target = statusMap[pilot.id]
  if (!target) return
  await pilotApi.setStatus(pilot.id, target)
  ElMessage.success(pilot.name + ' 已切到' + statuses.find((s) => s.value === target).label)
  await load()
}

async function submitCreate() {
  const payload = { ...form.value }
  if (!payload.pilotNo || !payload.name || !payload.phone) {
    ElMessage.warning('编号、姓名、手机号都要填')
    return
  }
  await pilotApi.create(payload)
  ElMessage.success('飞手已入队')
  createVisible.value = false
  await load()
}

onMounted(load)
</script>
