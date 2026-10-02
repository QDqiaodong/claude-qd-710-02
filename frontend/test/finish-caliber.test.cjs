/**
 * 完成口径页面验证（不依赖浏览器，node --test 即可跑）：
 *  1. jobOrderApi.finish 只发 POST /job-orders/{id}/finish，请求体恒为空对象，绝不携带 actualMu；
 *  2. 完成弹窗 / 列表合计只读日志汇总字段（loggedMu / totalAreaMu），没有任何可写的实际亩数入口；
 *  3. 后端 JobOrderView 出参含 loggedMu 字段（与 Views.JobOrderView record 对齐）。
 */
const test = require('node:test')
const assert = require('node:assert')
const fs = require('node:fs')
const path = require('node:path')

const apiSource = fs.readFileSync(path.join(__dirname, '../src/api/index.js'), 'utf8')
const viewSource = fs.readFileSync(path.join(__dirname, '../src/views/JobOrderView.vue'), 'utf8')
const backendViewSource = fs.readFileSync(
  path.join(__dirname, '../../backend/src/main/java/com/drone/plant/dto/Views.java'), 'utf8')
const backendDoneSource = fs.readFileSync(
  path.join(__dirname, '../../backend/src/main/java/com/drone/plant/service/JobOrderService.java'), 'utf8')

test('finish 接口不允许前端提交实际亩数：请求体恒为 {}', () => {
  const line = apiSource.split('\n').find((l) => l.includes('/finish'))
  assert.ok(line, '应存在 /finish 接口定义')
  assert.match(line, /http\.post\(`\/job-orders\/\$\{id\}\/finish`,\s*\{\}\)/,
    'finish 必须以空对象为请求体，实际亩数只能由后端汇总')
  assert.doesNotMatch(line, /actualMu/, 'finish 请求体里不能出现 actualMu')
})

test('作业单页没有任何可编辑的实际亩数输入框', () => {
  const editableActual = /v-model="[^"]*actualMu"/.test(viewSource)
  assert.strictEqual(editableActual, false, '页面不得绑定可编辑 actualMu 输入')
  // 完成弹窗中的实际亩数必须是只读展示日志汇总
  assert.match(viewSource, /实际作业亩数\(日志汇总\)/, '完成弹窗要标注亩数来自日志汇总')
  assert.match(viewSource, /finishSummary\.totalAreaMu/, '完成弹窗只读展示 summary.totalAreaMu')
  assert.match(viewSource, /（只读）/, '完成弹窗的实际亩数要明确只读')
})

test('列表与合计条只读日志汇总字段 loggedMu', () => {
  assert.match(viewSource, /row\.loggedMu/, '列表“计划 / 日志汇总”列读 row.loggedMu')
  assert.match(viewSource, /loggedTotal/, '合计条按 loggedMu 求和')
  assert.match(viewSource, /日志汇总亩数合计/, '合计条标签说明取日志汇总')
})

test('汇总为零时前端禁用完成并提示先登记架次', () => {
  assert.match(viewSource, /totalAreaMu \|\| 0\) <= 0/, '零汇总要有禁用 / 拦截判断')
  assert.match(viewSource, /还没有登记飞行日志/, '零汇总要提示先登记架次')
})

test('重复提交有 loading 防重入保护', () => {
  assert.match(viewSource, /if \(finishBusy\.value\) return/, '提交入口要防重入')
  assert.match(viewSource, /:loading="finishBusy"/, '完成按钮要绑定 loading')
})

test('后端 JobOrderView 暴露 loggedMu 出参', () => {
  const recordBlock = backendViewSource.slice(backendViewSource.indexOf('record JobOrderView'))
  assert.match(recordBlock, /BigDecimal loggedMu/, 'JobOrderView 必须包含 loggedMu 字段')
})

test('后端完成口径：拒绝前端亩数覆盖并同时提示提交值与汇总值', () => {
  const doneStart = backendDoneSource.indexOf('public Views.JobOrderView done')
  const doneBlock = backendDoneSource.slice(doneStart, backendDoneSource.indexOf('public Views.JobOrderView cancel'))
  assert.match(doneBlock, /累计亩数为 0/, '零汇总要拒绝完成')
  assert.match(doneBlock, /两者不一致/, '亩数不一致要明确提示')
  assert.match(doneBlock, /请求提交/, '提示中要有请求提交的数字')
  assert.match(doneBlock, /飞行日志汇总/, '提示中要有日志汇总的数字')
  assert.match(doneBlock, /order\.setActualMu\(logged\)/, '落库 actualMu 只能取日志汇总')
})
