import axios from 'axios'
import { ElMessage } from 'element-plus'

/**
 * 统一走 nginx 反代到后端 /api：
 *   成功 → 后端包成 { ok:true, message, data }，这里直接把 data 交给页面
 *   失败 → 后端包成 { ok:false, message:"中文业务提示" }，这里统一弹提示并 reject
 */
const http = axios.create({ baseURL: '/api', timeout: 15000 })

http.interceptors.response.use(
  (res) => {
    const body = res.data
    if (body && typeof body === 'object' && Object.prototype.hasOwnProperty.call(body, 'ok')) {
      if (body.ok) return body.data
      ElMessage.error(body.message || '操作失败')
      return Promise.reject(new Error(body.message || '操作失败'))
    }
    return body
  },
  (err) => {
    const body = err.response && err.response.data
    const message = (body && body.message) || err.message || '网络异常，请稍后再试'
    ElMessage.error(message)
    return Promise.reject(new Error(message))
  }
)

/** 首页：作业地图与总览 */
export const farmApi = {
  overview: () => http.get('/farm/overview'),
  map: (from, to) => http.get('/farm/map', { params: { from: from || '', to: to || '' } })
}

/** 模块一：地块 */
export const plotApi = {
  list: (params) => http.get('/plots', { params }),
  detail: (id) => http.get(`/plots/${id}/detail`),
  /** 同一个后端查询方法，这里拿到的是 PlotOption 形状的瘦身数据 */
  options: () => http.get('/plots/options'),
  create: (data) => http.post('/plots', data),
  setStatus: (id, status) => http.post(`/plots/${id}/status`, { status })
}

/** 模块二：植保无人机 */
export const droneApi = {
  list: (params) => http.get('/drones', { params }),
  /** DroneOption 形状 */
  options: () => http.get('/drones/options'),
  create: (data) => http.post('/drones', data),
  setStatus: (id, status) => http.post(`/drones/${id}/status`, { status }),
  dispatch: (id, data) => http.post(`/drones/${id}/dispatch`, data)
}

/** 模块三：飞手 */
export const pilotApi = {
  list: (params) => http.get('/pilots', { params }),
  /** PilotOption 形状 */
  options: () => http.get('/pilots/options'),
  create: (data) => http.post('/pilots', data),
  setStatus: (id, status) => http.post(`/pilots/${id}/status`, { status })
}

/** 模块四：作业单 */
export const jobOrderApi = {
  list: (params) => http.get('/job-orders', { params }),
  options: () => http.get('/job-orders/options'),
  create: (data) => http.post('/job-orders', data),
  update: (id, data) => http.put(`/job-orders/${id}`, data),
  takeoff: (id) => http.post(`/job-orders/${id}/takeoff`),
  // 完成口径在后端：实际亩数只取已落库飞行日志汇总，前端不提交任何亩数
  finish: (id) => http.post(`/job-orders/${id}/finish`, {}),
  cancel: (id, reason) => http.post(`/job-orders/${id}/cancel`, reason ? { reason } : {})
}

/** 模块五-延：异常架次停飞处置 */
export const handlingApi = {
  list: (params) => http.get('/abnormal-handlings', { params }),
  detail: (id) => http.get(`/abnormal-handlings/${id}`),
  supervisorOptions: () => http.get('/abnormal-handlings/supervisor-options'),
  observe: (id, supervisorId, comment) => http.post(`/abnormal-handlings/${id}/observe`, { supervisorId, comment }),
  ground: (id, supervisorId, comment) => http.post(`/abnormal-handlings/${id}/ground`, { supervisorId, comment }),
  release: (id, supervisorId, conclusion) => http.post(`/abnormal-handlings/${id}/release`, { supervisorId, comment: conclusion })
}

/** 模块六：作业前配药复核台 */
export const mixingApi = {
  list: (status) => http.get('/mixing-batches', { params: status ? { status } : {} }),
  byOrder: (jobOrderId) => http.get(`/mixing-batches/by-order/${jobOrderId}`),
  detail: (id) => http.get(`/mixing-batches/${id}`),
  pilotOptions: () => http.get('/mixing-batches/pilot-options'),
  reviewerOptions: () => http.get('/mixing-batches/reviewer-options'),
  create: (data) => http.post('/mixing-batches', data),
  updateBuckets: (id, pilotId, amounts) => http.put(`/mixing-batches/${id}/buckets`, { pilotId, amounts }),
  submit: (id, pilotId) => http.post(`/mixing-batches/${id}/submit`, { pilotId }),
  reject: (id, pilotId, reason) => http.post(`/mixing-batches/${id}/reject`, { pilotId, reason }),
  lock: (id, pilotId) => http.post(`/mixing-batches/${id}/lock`, { pilotId })
}

/** 模块五：飞行日志 */
export const flightLogApi = {
  list: (params) => http.get('/flight-logs', { params: params || {} }),
  abnormal: (params) => http.get('/flight-logs/abnormal', { params: params || {} }),
  summary: (jobOrderId) => http.get('/flight-logs/summary', { params: jobOrderId ? { jobOrderId } : {} }),
  create: (data) => http.post('/flight-logs', data)
}
