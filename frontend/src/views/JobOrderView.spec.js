import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { nextTick } from 'vue'
import ElementPlus from 'element-plus'
import { createMemoryHistory, createRouter } from 'vue-router'
import JobOrderView from './JobOrderView.vue'

/**
 * 完成口径的页面验证：
 * 1. 完成弹窗只读展示已落库飞行日志汇总（没有可编辑亩数的输入框）
 * 2. 汇总为 0 时确认按钮禁用
 * 3. 提交完成不带 actualMu，只按日志汇总收口
 * 4. 重复提交（已完成行没有完成按钮）不会二次改变统计
 */
const finish = vi.fn().mockResolvedValue({})
const list = vi.fn()
const options = vi.fn().mockResolvedValue([])
const reloadOverview = vi.fn().mockResolvedValue({})

function workingRow(overrides = {}) {
  return {
    id: 7,
    orderNo: 'JO-24092801',
    plotId: 6,
    plotNo: 'P-06',
    farmerName: '孙德海',
    droneId: 6,
    droneBodyNo: 'DP-06',
    droneModel: '汉和 CD-15',
    droneStatus: 'WORKING',
    droneStatusName: '作业中',
    droneOpenHandling: false,
    pilotId: 6,
    pilotName: '黄子谦',
    formula: '菌核净 40% + 硼肥',
    dosePerMu: 1.2,
    plannedMu: 88,
    actualMu: 52,
    loggedMu: 52,
    jobDate: '2026-09-28',
    weather: '晴',
    status: 'WORKING',
    statusName: '作业中',
    progress: 59,
    sortCount: 2,
    chemicalL: 62.4,
    finishedAt: null,
    mixingBatchId: null,
    mixingStatus: null,
    mixingStatusName: null,
    ...overrides
  }
}

const mountedWrappers = []

async function mountPage(rows) {
  // 每一次 list 调用（含完成后的刷新）都给同样的数据，避免卸载后延迟 resolve 造成未处理拒绝
  list.mockReset().mockResolvedValue(rows)
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/orders', name: 'orders', component: { template: '<div />' } }]
  })
  await router.push('/orders')
  const wrapper = mount(JobOrderView, {
    attachTo: document.body,
    global: {
      plugins: [ElementPlus, router],
      provide: { plant: { reloadOverview } }
    }
  })
  mountedWrappers.push(wrapper)
  await flushPromises()
  await nextTick()
  // el-table 布局在下一帧完成，等行内按钮挂载
  await new Promise((r) => setTimeout(r, 50))
  return wrapper
}

afterEach(() => {
  mountedWrappers.splice(0).forEach((w) => w.unmount())
  document.body.innerHTML = ''
})

function rowButtons() {
  return [...document.querySelectorAll('.el-table__body-wrapper button')]
}

async function openFinishDialog(wrapper) {
  await rowButtons().find((b) => b.textContent.trim() === '完成').click()
  await nextTick()
  await new Promise((r) => setTimeout(r, 50))
  return [...document.querySelectorAll('.el-dialog')].find((d) => d.textContent.includes('完成作业单'))
}

beforeEach(() => {
  vi.clearAllMocks()
})

vi.mock('../api', () => ({
  jobOrderApi: {
    list: (...args) => list(...args),
    takeoff: vi.fn(),
    finish: (...args) => finish(...args),
    cancel: vi.fn(),
    update: vi.fn(),
    create: vi.fn(),
    options: () => options()
  },
  plotApi: { options: () => options() },
  droneApi: { options: () => options() },
  pilotApi: { options: () => options() }
}))

describe('JobOrderView 完成口径', () => {
  it('列表只读展示飞行日志汇总，列里显示的是 loggedMu', async () => {
    await mountPage([workingRow()])
    expect(document.body.textContent).toContain('日志汇总')
    const cell = [...document.querySelectorAll('.el-table__body-wrapper td')]
      .find((td) => td.textContent.includes('88') && td.textContent.includes('52'))
    expect(cell).toBeTruthy()
  })

  it('完成弹窗只读展示日志汇总，没有可编辑的亩数输入框', async () => {
    const wrapper = await mountPage([workingRow()])
    const dialog = await openFinishDialog(wrapper)
    expect(dialog).toBeTruthy()
    expect(dialog.textContent).toContain('日志汇总作业亩数')
    expect(dialog.textContent).toContain('52 亩')
    expect(dialog.querySelector('input')).toBeNull()
  })

  it('汇总为 0 时确认按钮禁用并提示先登记架次', async () => {
    const wrapper = await mountPage([workingRow({ loggedMu: 0, actualMu: 0, sortCount: 0, chemicalL: 0 })])
    const dialog = await openFinishDialog(wrapper)
    expect(dialog.textContent).toContain('汇总亩数为 0')
    const confirm = [...dialog.querySelectorAll('button')].find((b) => b.textContent.includes('按日志汇总'))
    expect(confirm.disabled).toBe(true)
    expect(finish).not.toHaveBeenCalled()
  })

  it('提交完成不带 actualMu，成功后刷新列表与总览', async () => {
    const wrapper = await mountPage([workingRow()])
    const dialog = await openFinishDialog(wrapper)
    const confirm = [...dialog.querySelectorAll('button')].find((b) => b.textContent.includes('按日志汇总'))
    await confirm.click()
    await flushPromises()
    expect(finish).toHaveBeenCalledTimes(1)
    expect(finish).toHaveBeenCalledWith(7)
    expect(list).toHaveBeenCalledTimes(2)
    expect(reloadOverview).toHaveBeenCalled()
  })

  it('已完成行没有完成按钮，重复提交入口不存在', async () => {
    await mountPage([workingRow({ status: 'DONE', statusName: '已完成', finishedAt: '2026-09-28T12:00:00' })])
    const labels = rowButtons().map((b) => b.textContent.trim())
    expect(labels.find((t) => t === '完成')).toBeFalsy()
  })
})
