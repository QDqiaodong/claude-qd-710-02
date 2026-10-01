<template>
  <div>
    <div class="panel">
      <div class="panel-head">
        <h3>作业地图</h3>
        <span class="panel-sub">色块＝地块作业状态，已完成地块叠加往复喷洒航线（虚线条纹）</span>
        <div class="panel-extra">
          <span class="muted">共 {{ plots.length }} 块 · 喷洒完成 {{ sprayedCount }} 块</span>
          <el-button size="small" @click="loadMap">刷新</el-button>
          <el-button size="small" type="primary" @click="createVisible = true">新增地块</el-button>
        </div>
      </div>

      <div class="field-wrap">
        <!-- 左：地块列表，点击后在地图上选中并高亮描边 -->
        <div class="plot-list">
          <div
            v-for="p in plots"
            :key="p.id"
            class="plot-card"
            :class="{ 'is-on': selectedId === p.id }"
            :style="{ borderLeftColor: p.color }"
            @click="focus(p)"
          >
            <div class="pc-top">
              <span class="dot" :style="{ background: p.color }"></span>
              <span class="pc-no">{{ p.plotNo }}</span>
              <span class="pc-area">{{ p.areaMu }} 亩</span>
            </div>
            <div class="pc-meta">{{ p.farmerName }} · {{ p.cropTypeName }}</div>
            <div class="tag-line">
              <span class="mini-tag">{{ p.statusName }}</span>
              <span v-if="p.sortCount > 0" class="mini-tag">{{ p.sortCount }} 架次</span>
              <span v-if="p.doneMu > 0" class="mini-tag">已喷 {{ p.doneMu }} 亩</span>
            </div>
            <div class="tag-line">
              <el-link type="primary" :underline="false" @click.stop="openDrawer(p)">查看详情 ›</el-link>
            </div>
          </div>
        </div>

        <!-- 右：纯 SVG 手绘的多边形地块 + 喷洒航线条纹 -->
        <div>
          <svg class="field-svg" viewBox="0 0 880 620" preserveAspectRatio="xMidYMid meet">
            <defs>
              <clipPath v-for="p in plots" :id="'clip-' + p.id" :key="'c' + p.id">
                <polygon :points="p.points" />
              </clipPath>
            </defs>

            <g v-for="p in plots" :key="p.id" class="plot-cell" @click="openDrawer(p)">
              <polygon
                class="plot-shape"
                :points="p.points"
                :fill="p.color"
                stroke="#ffffff"
                stroke-width="2"
              />
              <!-- 已完成地块：往复式喷洒航线条纹（被多边形裁剪） -->
              <polyline
                v-if="p.sprayed"
                class="spray-line"
                :points="sprayTrack(p.points)"
                :clip-path="'url(#clip-' + p.id + ')'"
              />
              <polygon
                v-if="selectedId === p.id"
                class="plot-editor"
                :points="p.points"
                fill="none"
                stroke="#ff6f00"
                stroke-width="3.5"
              />
              <text class="plot-no" :x="p.cx" :y="p.cy - 8" text-anchor="middle" :fill="labelColor(p.status)">
                {{ p.plotNo }}
              </text>
              <text class="plot-area" :x="p.cx" :y="p.cy + 10" text-anchor="middle" :fill="labelColor(p.status)">
                {{ p.areaMu }} 亩
              </text>
              <text class="plot-crop" :x="p.cx" :y="p.cy + 26" text-anchor="middle" :fill="labelColor(p.status)">
                {{ p.cropTypeName }} · {{ p.statusName }}
              </text>
            </g>
          </svg>

          <div class="legend">
            <span><i style="background: #cfd8dc"></i>待作业</span>
            <span><i style="background: #7b1fa2"></i>作业中</span>
            <span><i style="background: #2e7d32"></i>已完成</span>
            <span><i style="background: #fff59d"></i>暂缓</span>
            <span class="dash"><i></i>喷洒航线条纹</span>
            <span class="muted">单击地块查看作业单与飞行日志</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 地块抽屉：详情 + 名下作业单 + 选中作业单的飞行日志 -->
    <el-drawer v-model="drawerVisible" :title="detail && detail.plot ? detail.plot.plotNo + ' · ' + detail.plot.farmerName : '地块详情'" size="52%">
      <template v-if="detail">
        <div class="kv-grid">
          <div class="kv"><span>地块编号</span><b>{{ detail.plot.plotNo }}</b></div>
          <div class="kv"><span>承包农户 / 合作社</span><b>{{ detail.plot.farmerName }}</b></div>
          <div class="kv"><span>作物类型</span><b>{{ detail.plot.cropTypeName }}</b></div>
          <div class="kv"><span>面积</span><b>{{ detail.plot.areaMu }} 亩</b></div>
          <div class="kv"><span>所属村组</span><b>{{ detail.plot.villageGroup }}</b></div>
          <div class="kv"><span>地形</span><b>{{ detail.plot.terrainName }}</b></div>
          <div class="kv"><span>灌溉条件</span><b>{{ detail.plot.irrigationName }}</b></div>
          <div class="kv"><span>作业状态</span><b>{{ detail.plot.statusName }}</b></div>
        </div>

        <div class="drawer-title">该地块的作业单（点击一行查看飞行日志）</div>
        <el-table :data="detail.orders" size="small" border highlight-current-row @row-click="pickOrder">
          <el-table-column prop="orderNo" label="单号" width="130" />
          <el-table-column prop="jobDate" label="作业日期" width="110" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <span :style="{ color: statusColor(row.status) }">{{ row.statusName }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="plannedMu" label="计划(亩)" width="90" />
          <el-table-column prop="actualMu" label="实际(亩)" width="90" />
          <el-table-column prop="droneBodyNo" label="机身" width="90" />
          <el-table-column prop="pilotName" label="飞手" width="90" />
          <el-table-column prop="sortCount" label="架次" width="70" />
        </el-table>

        <div class="drawer-title">
          飞行日志<template v-if="activeOrder"> · {{ activeOrder.orderNo }}</template>
        </div>
        <el-table v-if="logs.length" :data="logs" size="small" border :row-class-name="rowClass">
          <el-table-column prop="seqNo" label="架次" width="60" />
          <el-table-column label="起飞" width="150">
            <template #default="{ row }">{{ fmt(row.takeoffAt) }}</template>
          </el-table-column>
          <el-table-column label="降落" width="150">
            <template #default="{ row }">{{ fmt(row.landingAt) }}</template>
          </el-table-column>
          <el-table-column prop="durationMin" label="时长(分)" width="90" />
          <el-table-column prop="areaMu" label="亩数" width="80" />
          <el-table-column prop="chemicalL" label="药量(L)" width="90" />
          <el-table-column label="异常" min-width="200">
            <template #default="{ row }">
              <span v-if="row.abnormal" style="color: #c62828">{{ row.abnormalDesc }}</span>
              <span v-else class="muted">—</span>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-else description="选择上方的作业单查看架次明细" :image-size="70" />
      </template>
    </el-drawer>

    <!-- 新增地块 -->
    <el-dialog v-model="createVisible" title="新增地块" width="520">
      <el-form :model="form" label-width="110px">
        <el-form-item label="地块编号"><el-input v-model="form.plotNo" placeholder="如 P-13" /></el-form-item>
        <el-form-item label="承包农户"><el-input v-model="form.farmerName" placeholder="农户或合作社" /></el-form-item>
        <el-form-item label="作物类型">
          <el-select v-model="form.cropType" placeholder="请选择" style="width: 100%">
            <el-option v-for="c in crops" :key="c.value" :label="c.label" :value="c.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="面积(亩)"><el-input-number v-model="form.areaMu" :min="1" :precision="2" /></el-form-item>
        <el-form-item label="所属村组"><el-input v-model="form.villageGroup" /></el-form-item>
        <el-form-item label="地形">
          <el-select v-model="form.terrain" placeholder="请选择" style="width: 100%">
            <el-option v-for="c in terrains" :key="c.value" :label="c.label" :value="c.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="灌溉条件">
          <el-select v-model="form.irrigation" placeholder="请选择" style="width: 100%">
            <el-option v-for="c in irrigations" :key="c.value" :label="c.label" :value="c.value" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCreate">建档</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, inject, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { farmApi, flightLogApi, plotApi } from '../api'

const plant = inject('plant')
const plots = ref([])
const selectedId = ref(null)
const drawerVisible = ref(false)
const createVisible = ref(false)
const detail = ref(null)
const activeOrder = ref(null)
const logs = ref([])

const crops = [
  { value: 'RICE', label: '水稻' },
  { value: 'WHEAT', label: '小麦' },
  { value: 'CORN', label: '玉米' },
  { value: 'COTTON', label: '棉花' },
  { value: 'FRUIT', label: '果树' },
  { value: 'OILSEED', label: '油菜' }
]
const terrains = [
  { value: 'PLAIN', label: '平原' },
  { value: 'HILL', label: '丘陵' },
  { value: 'MOUNTAIN', label: '山地' }
]
const irrigations = [
  { value: 'DITCH', label: '沟灌' },
  { value: 'SPRINKLER', label: '喷灌' },
  { value: 'DRIP', label: '滴灌' },
  { value: 'RAINFED', label: '雨养' }
]

const form = ref({ plotNo: '', farmerName: '', cropType: 'RICE', areaMu: 100, villageGroup: '', terrain: 'PLAIN', irrigation: 'DITCH' })

const sprayedCount = computed(() => plots.value.filter((p) => p.sprayed).length)

/** 往复式喷洒航线：在地块包围盒里来回折返，超出部分由 clipPath 裁掉 */
function sprayTrack(points) {
  const arr = points.trim().split(/\s+/).map((pair) => pair.split(',').map(Number))
  const xs = arr.map((p) => p[0])
  const ys = arr.map((p) => p[1])
  const minX = Math.min(...xs)
  const maxX = Math.max(...xs)
  const minY = Math.min(...ys)
  const maxY = Math.max(...ys)
  const step = 21
  const pad = 12
  const out = []
  let left = true
  let y = minY + 8
  while (y < maxY - 2) {
    out.push([left ? minX - pad : maxX + pad, y])
    out.push([left ? maxX + pad : minX - pad, y])
    left = !left
    y += step
  }
  return out.map((p) => `${p[0].toFixed(1)},${p[1].toFixed(1)}`).join(' ')
}

function labelColor(status) {
  return status === 'DONE' || status === 'WORKING' ? '#ffffff' : '#37474f'
}

function statusColor(status) {
  if (status === 'DONE') return '#2e7d32'
  if (status === 'WORKING') return '#7b1fa2'
  if (status === 'CANCELED') return '#c62828'
  return '#6b5f7d'
}

function rowClass({ row }) {
  return row.abnormal ? 'row-abnormal' : ''
}

function fmt(value) {
  return value ? String(value).replace('T', ' ') : '—'
}

function focus(plot) {
  selectedId.value = plot.id
}

async function openDrawer(plot) {
  selectedId.value = plot.id
  drawerVisible.value = true
  activeOrder.value = null
  logs.value = []
  detail.value = await plotApi.detail(plot.id)
  if (detail.value.orders && detail.value.orders.length) {
    pickOrder(detail.value.orders[0])
  }
}

async function pickOrder(row) {
  activeOrder.value = row
  logs.value = await flightLogApi.list({ jobOrderId: row.id })
}

async function loadMap() {
  plots.value = await farmApi.map(plant.from.value, plant.to.value)
  if (!selectedId.value && plots.value.length) {
    selectedId.value = plots.value[0].id
  }
}

async function submitCreate() {
  const payload = { ...form.value }
  if (!payload.plotNo || !payload.farmerName || !payload.villageGroup) {
    ElMessage.warning('地块编号、承包农户、所属村组都要填')
    return
  }
  await plotApi.create(payload)
  ElMessage.success('地块已建档')
  createVisible.value = false
  await loadMap()
  plant.reloadOverview()
}

onMounted(loadMap)
watch([plant.from, plant.to], loadMap)
</script>
