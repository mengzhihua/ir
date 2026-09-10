<template>
  <div class="page">
    <div class="page-title">
      <div>
        <h2>自动平衡</h2>
        <p class="subtitle">
          围绕业务目标自动生成跨系统(OMS/WMS/TMS/SRM)决策:低风险自动执行,高风险进入审批
        </p>
      </div>
      <div>
        <el-button @click="load">刷新</el-button>
        <el-button v-if="canWrite()" type="primary" :loading="running" @click="runNow"
          >立即平衡</el-button
        >
      </div>
    </div>
    <div class="stats">
      <div class="stat">
        <div class="label">运行模式</div>
        <div class="value">{{ modeLabels[config.mode] || config.mode }}</div>
      </div>
      <div class="stat">
        <div class="label">待审批决策</div>
        <div class="value" :class="overview.pendingDecisions ? 'warning' : ''">
          {{ overview.pendingDecisions || 0 }}
        </div>
      </div>
      <div class="stat">
        <div class="label">近30天已执行</div>
        <div class="value">{{ overview.executed30d || 0 }}</div>
      </div>
      <div class="stat">
        <div class="label">近30天预计节省</div>
        <div class="value success">{{ formatMoney(overview.saving30d) }}</div>
      </div>
      <div class="stat">
        <div class="label">最近运行</div>
        <div class="value small">
          {{ overview.lastRun ? formatDate(overview.lastRun.finishedAt) : '-' }}
        </div>
        <div v-if="overview.lastRun" class="muted">
          得分 {{ formatNumber(overview.lastRun.scoreBefore, 1) }} →
          {{ formatNumber(overview.lastRun.scoreAfter, 1) }}
        </div>
      </div>
    </div>
    <div class="grid-2">
      <div class="panel">
        <div class="panel-title">
          <h3>平衡策略</h3>
          <el-button v-if="canWrite()" size="small" @click="configVisible = true"
            >护栏配置</el-button
          >
        </div>
        <el-table :data="overview.strategies || []" size="small">
          <el-table-column prop="name" label="策略" width="120" />
          <el-table-column prop="objective" label="目标" width="150" />
          <el-table-column prop="description" label="说明" />
        </el-table>
      </div>
      <div class="panel">
        <h3>运行历史</h3>
        <el-table :data="runs" size="small" @row-click="showRun" highlight-current-row>
          <el-table-column prop="runNo" label="运行号" width="200" />
          <el-table-column label="触发" width="80">
            <template #default="{ row }">{{
              triggerLabels[row.triggerType] || row.triggerType
            }}</template>
          </el-table-column>
          <el-table-column prop="mode" label="模式" width="80" />
          <el-table-column label="得分" width="120">
            <template #default="{ row }"
              >{{ formatNumber(row.scoreBefore, 1) }} →
              {{ formatNumber(row.scoreAfter, 1) }}</template
            >
          </el-table-column>
          <el-table-column label="决策/执行/待审" width="120">
            <template #default="{ row }"
              >{{ row.decisionCount }} / {{ row.executedCount }} / {{ row.pendingCount }}</template
            >
          </el-table-column>
          <el-table-column label="时间">
            <template #default="{ row }">{{ formatDate(row.startedAt) }}</template>
          </el-table-column>
        </el-table>
      </div>
    </div>
    <div class="panel">
      <div class="panel-title"><h3>决策列表</h3></div>
      <div class="toolbar">
        <el-select v-model="filters.status" clearable placeholder="状态" @change="search">
          <el-option v-for="(label, key) in statusLabels" :key="key" :label="label" :value="key" />
        </el-select>
        <el-select v-model="filters.strategy" clearable placeholder="策略" @change="search">
          <el-option
            v-for="item in overview.strategies || []"
            :key="item.code"
            :label="item.name"
            :value="item.code"
          />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button
          v-if="canWrite()"
          type="success"
          :disabled="!selectedPending.length"
          @click="approveBatch"
          >批量通过({{ selectedPending.length }})</el-button
        >
      </div>
      <el-table
        v-loading="loading"
        :data="decisions"
        stripe
        @selection-change="(rows) => (selected = rows)"
      >
        <el-table-column type="selection" width="45" />
        <el-table-column label="策略" width="120">
          <template #default="{ row }">{{ strategyName(row.strategy) }}</template>
        </el-table-column>
        <el-table-column prop="targetSystem" label="系统" width="70" />
        <el-table-column prop="actionType" label="动作" width="180" />
        <el-table-column prop="targetKey" label="对象" width="110" />
        <el-table-column prop="reason" label="决策依据" min-width="260" show-overflow-tooltip />
        <el-table-column label="成本影响" width="100" align="right">
          <template #default="{ row }">
            <span :class="Number(row.expectedCostDelta) > 0 ? 'danger' : 'success'">{{
              formatMoney(row.expectedCostDelta)
            }}</span>
          </template>
        </el-table-column>
        <el-table-column label="NPS影响" width="90" align="right">
          <template #default="{ row }">+{{ formatNumber(row.expectedNpsDelta, 1) }}</template>
        </el-table-column>
        <el-table-column label="风险" width="70">
          <template #default="{ row }">
            <el-tag :type="riskTag[row.riskLevel] || 'info'" size="small">{{
              severityLabels[row.riskLevel] || row.riskLevel
            }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusTag[row.status] || 'info'" size="small">{{
              statusLabels[row.status] || row.status
            }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="130">
          <template #default="{ row }">
            <template v-if="row.status === 'PENDING' && canWrite()">
              <el-button link type="success" @click="approve(row)">通过</el-button>
              <el-button link type="danger" @click="reject(row)">拒绝</el-button>
            </template>
            <span v-else class="muted">{{ row.decidedBy || '-' }}</span>
          </template>
        </el-table-column>
        <template #empty><el-empty description="暂无决策,点击“立即平衡”生成" /></template>
      </el-table>
      <div class="pagination">
        <el-pagination
          v-model:current-page="pager.current"
          v-model:page-size="pager.size"
          :total="pager.total"
          layout="total, sizes, prev, pager, next"
          @current-change="loadDecisions"
          @size-change="loadDecisions"
        />
      </div>
    </div>
    <el-drawer v-model="runVisible" title="运行详情" size="55%">
      <template v-if="runDetail.run">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="运行号">{{ runDetail.run.runNo }}</el-descriptions-item>
          <el-descriptions-item label="模式">{{ runDetail.run.mode }}</el-descriptions-item>
          <el-descriptions-item label="候选决策">{{
            runDetail.summary.candidates
          }}</el-descriptions-item>
          <el-descriptions-item label="跳过">{{
            JSON.stringify(runDetail.summary.skipped || {})
          }}</el-descriptions-item>
          <el-descriptions-item label="策略分布">{{
            JSON.stringify(runDetail.summary.byStrategy || {})
          }}</el-descriptions-item>
          <el-descriptions-item label="预计成本变化">{{
            formatMoney(runDetail.summary.expectedCostDelta)
          }}</el-descriptions-item>
        </el-descriptions>
        <div class="drawer-section">
          <h4>目标变化</h4>
          <el-table :data="runDetail.summary.objectives || []" size="small">
            <el-table-column prop="name" label="目标" />
            <el-table-column label="运行前" align="right">
              <template #default="{ row }">{{
                formatNumber(runDetail.summary.metricsBefore?.[row.metric], 2)
              }}</template>
            </el-table-column>
            <el-table-column label="运行后" align="right">
              <template #default="{ row }">{{
                formatNumber(runDetail.summary.metricsAfter?.[row.metric], 2)
              }}</template>
            </el-table-column>
            <el-table-column label="达成率" align="right">
              <template #default="{ row }">{{ percent(row.attainment) }}</template>
            </el-table-column>
          </el-table>
        </div>
        <div class="drawer-section">
          <h4>本次决策</h4>
          <el-table :data="runDetail.decisions || []" size="small">
            <el-table-column label="策略" width="110">
              <template #default="{ row }">{{ strategyName(row.strategy) }}</template>
            </el-table-column>
            <el-table-column prop="actionType" label="动作" width="170" />
            <el-table-column prop="targetKey" label="对象" width="100" />
            <el-table-column prop="reason" label="依据" show-overflow-tooltip />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">{{ statusLabels[row.status] || row.status }}</template>
            </el-table-column>
          </el-table>
        </div>
      </template>
    </el-drawer>
    <el-dialog v-model="configVisible" title="平衡护栏配置" width="480px">
      <el-form label-width="150px">
        <el-form-item label="运行模式">
          <el-radio-group v-model="configForm.mode">
            <el-radio-button value="OFF">关闭</el-radio-button>
            <el-radio-button value="SUGGEST">仅建议</el-radio-button>
            <el-radio-button value="AUTO">自动执行</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="定时运行"
          ><el-switch v-model="configForm.scheduleEnabled"
        /></el-form-item>
        <el-form-item label="每次最多决策"
          ><el-input-number v-model="configForm.maxDecisionsPerRun" :min="1" :max="200"
        /></el-form-item>
        <el-form-item label="每次最多自动执行"
          ><el-input-number v-model="configForm.maxAutoExecutePerRun" :min="0" :max="200"
        /></el-form-item>
        <el-form-item label="自动采购金额上限"
          ><el-input-number v-model="configForm.autoPurchaseAmountLimit" :min="0" :step="1000"
        /></el-form-item>
        <el-form-item label="同对象冷却(小时)"
          ><el-input-number v-model="configForm.cooldownHours" :min="0"
        /></el-form-item>
        <el-form-item label="服务护栏达成率"
          ><el-input-number
            v-model="configForm.serviceGuardAttainment"
            :min="0"
            :max="1"
            :step="0.05"
            :precision="2"
        /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="configVisible = false">取消</el-button>
        <el-button type="primary" @click="saveConfig">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { balanceApi } from '../api'
import { canWrite } from '../auth'
import { formatDate, formatMoney, formatNumber, pageResult, percent } from '../utils/format'
import { severityLabels } from '../utils/labels'

const overview = reactive({})
const config = reactive({})
const configForm = reactive({})
const runs = ref([])
const decisions = ref([])
const selected = ref([])
const loading = ref(false)
const running = ref(false)
const runVisible = ref(false)
const configVisible = ref(false)
const runDetail = reactive({ run: null, summary: {}, decisions: [] })
const filters = reactive({ status: '', strategy: '' })
const pager = reactive({ current: 1, size: 20, total: 0 })
const modeLabels = { OFF: '关闭', SUGGEST: '仅建议', AUTO: '自动执行' }
const triggerLabels = { MANUAL: '手动', SCHEDULED: '定时' }
const statusLabels = {
  PENDING: '待审批',
  EXECUTED: '已执行',
  REJECTED: '已拒绝',
  FAILED: '执行失败'
}
const statusTag = { PENDING: 'warning', EXECUTED: 'success', REJECTED: 'info', FAILED: 'danger' }
const riskTag = { HIGH: 'danger', MEDIUM: 'warning', LOW: 'success' }
const selectedPending = computed(() => selected.value.filter((row) => row.status === 'PENDING'))
function strategyName(code) {
  return (overview.strategies || []).find((item) => item.code === code)?.name || code
}
async function loadDecisions() {
  loading.value = true
  try {
    const page = pageResult(await balanceApi.decisions({ ...filters, ...pager }))
    decisions.value = page.records
    pager.total = page.total
  } finally {
    loading.value = false
  }
}
async function load() {
  const result = await balanceApi.overview()
  Object.assign(overview, result)
  Object.assign(config, result.config || {})
  Object.assign(configForm, result.config || {})
  runs.value = pageResult(await balanceApi.runs({ current: 1, size: 10 })).records
  loadDecisions()
}
function search() {
  pager.current = 1
  loadDecisions()
}
async function runNow() {
  running.value = true
  try {
    const result = await balanceApi.run()
    ElMessage.success(
      `平衡完成:生成 ${result.run.decisionCount} 条决策,自动执行 ${result.run.executedCount} 条,待审批 ${result.run.pendingCount} 条`
    )
    await load()
    showRunDetail(result)
  } finally {
    running.value = false
  }
}
function showRunDetail(result) {
  runDetail.run = result.run
  runDetail.summary = result.summary || {}
  runDetail.decisions = result.decisions || []
  runVisible.value = true
}
async function showRun(row) {
  showRunDetail(await balanceApi.runDetail(row.id))
}
async function approve(row) {
  const result = await balanceApi.approve(row.id)
  if (result.status === 'EXECUTED') {
    ElMessage.success('已执行')
  } else {
    ElMessage.error('执行失败,详见决策依据')
  }
  load()
}
async function approveBatch() {
  await ElMessageBox.confirm(`确认通过并执行 ${selectedPending.value.length} 条决策吗？`, '确认')
  for (const row of selectedPending.value) {
    await balanceApi.approve(row.id)
  }
  ElMessage.success('批量处理完成')
  load()
}
async function reject(row) {
  const { value } = await ElMessageBox.prompt('请输入拒绝原因(可选)', '拒绝决策', {
    inputPlaceholder: '例如:人工已处理'
  })
  await balanceApi.reject(row.id, value)
  ElMessage.success('已拒绝')
  load()
}
async function saveConfig() {
  Object.assign(config, await balanceApi.saveConfig(configForm))
  configVisible.value = false
  ElMessage.success('配置已保存')
}
load()
</script>
<style scoped>
.value.small {
  font-size: 16px;
}
</style>
