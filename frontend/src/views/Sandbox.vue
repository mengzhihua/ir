<template>
  <div class="page">
    <div class="page-title">
      <div>
        <h2>沙盘演练</h2>
        <p class="subtitle">在成本与效率之间兼顾取舍，支持人工沙盘与系统自动推演</p>
      </div>
    </div>

    <el-tabs v-model="activeTab" class="sandbox-tabs">
      <!-- ============ 人工沙盘 ============ -->
      <el-tab-pane label="人工沙盘" name="manual">
        <div class="panel-title">
          <span class="hint">由业务人员自定义需求、仓配与承运策略并运行评估</span>
          <el-button v-if="canWrite()" type="primary" @click="openCreate">创建场景</el-button>
        </div>
        <div class="grid-2">
          <div class="panel">
            <div class="panel-title">
              <h3>场景列表</h3>
              <el-button @click="load">刷新</el-button>
            </div>
            <el-table v-loading="loading" :data="rows" stripe @row-click="select">
              <el-table-column prop="name" label="场景名称" min-width="150" />
              <el-table-column label="类型" width="110">
                <template #default="{ row }">
                  <el-tag :type="tagTypes.scenarioMode[row.mode] || 'info'" size="small">{{
                    labelOf(row.mode || 'MANUAL', scenarioModeLabels)
                  }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="totalCost" label="总成本" align="right">
                <template #default="{ row }">{{ formatMoney(row.totalCost) }}</template>
              </el-table-column>
              <el-table-column label="服务水平" align="right">
                <template #default="{ row }">{{ percent(row.serviceLevel) }}</template>
              </el-table-column>
              <el-table-column label="操作" width="170">
                <template #default="{ row }">
                  <el-button v-if="canWrite()" link @click.stop="run(row)">运行</el-button>
                  <el-button v-if="canWrite()" link type="primary" @click.stop="apply(row)"
                    >应用到OTW</el-button
                  >
                </template>
              </el-table-column>
              <template #empty><el-empty description="暂无沙盘场景" /></template>
            </el-table>
            <div class="pagination">
              <el-pagination
                v-model:current-page="pager.current"
                v-model:page-size="pager.size"
                :total="pager.total"
                layout="total, prev, pager, next"
                @current-change="load"
              />
            </div>
          </div>
          <div class="panel">
            <div class="panel-title">
              <h3>{{ selected?.name || '选择场景查看结果' }}</h3>
              <el-tag v-if="selected" :type="tagTypes.scenarioStatus[selected.status]">{{
                labelOf(selected.status, scenarioStatusLabels)
              }}</el-tag>
            </div>
            <div v-if="selected" class="stats">
              <div class="stat">
                <div class="label">成本-效率综合得分</div>
                <div class="value primary">{{ formatNumber(selected.costEfficiencyScore, 2) }}</div>
              </div>
              <div class="stat">
                <div class="label">总成本</div>
                <div class="value">{{ formatMoney(selected.totalCost) }}</div>
              </div>
              <div class="stat">
                <div class="label">服务水平</div>
                <div class="value">{{ percent(selected.serviceLevel) }}</div>
              </div>
              <div class="stat">
                <div class="label">缺货件数</div>
                <div class="value danger">{{ formatNumber(selected.stockoutUnits, 2) }}</div>
              </div>
              <div class="stat">
                <div class="label">平均时效</div>
                <div class="value">{{ formatNumber(selected.avgLeadDays, 2) }}天</div>
              </div>
            </div>
            <div v-if="selected" class="grid-2">
              <Chart :option="typeOption" /><Chart :option="dailyOption" /><Chart
                :option="warehouseOption"
              /><Chart :option="carrierOption" />
            </div>
            <el-table v-if="selected" class="sandbox-sku-table" :data="selected.perSkuSummary || []" size="small">
              <el-table-column prop="sku" label="SKU" />
              <el-table-column prop="demand" label="需求" align="right">
                <template #default="{ row }">{{ formatNumber(row.demand, 2) }}</template>
              </el-table-column>
              <el-table-column prop="fulfilled" label="满足" align="right">
                <template #default="{ row }">{{ formatNumber(row.fulfilled, 2) }}</template>
              </el-table-column>
              <el-table-column prop="stockout" label="缺货" align="right">
                <template #default="{ row }">{{ formatNumber(row.stockout, 2) }}</template>
              </el-table-column>
            </el-table>
            <el-empty v-else description="选择一个场景查看结果" />
          </div>
        </div>
      </el-tab-pane>

      <!-- ============ 系统自动沙盘推演 ============ -->
      <el-tab-pane label="系统自动沙盘推演" name="auto">
        <div class="panel">
          <div class="panel-title">
            <h3>推演参数</h3>
            <span class="hint">系统自动遍历“分仓策略 × 安全库存 × 承运结构”候选空间并按成本-效率兼顾排名</span>
          </div>
          <el-form :inline="true" label-width="96px" class="auto-form">
            <el-form-item label="成本权重">
              <div class="weight-slider">
                <el-slider
                  v-model="auto.costWeightPct"
                  :min="0"
                  :max="100"
                  :step="5"
                  :marks="{ 0: '效率优先', 50: '均衡', 100: '成本优先' }"
                />
                <div class="weight-text">
                  成本 {{ auto.costWeightPct }}% · 效率 {{ 100 - auto.costWeightPct }}%
                </div>
              </div>
            </el-form-item>
            <el-form-item label="需求倍率">
              <el-input-number v-model="auto.demandMultiplier" :min="0.1" :step="0.1" />
            </el-form-item>
            <el-form-item label="推演周期">
              <el-input-number v-model="auto.horizonDays" :min="7" :max="90" :step="1" />
              <span class="unit">天</span>
            </el-form-item>
            <el-form-item>
              <el-button
                v-if="canWrite()"
                type="primary"
                :loading="auto.loading"
                @click="runAuto"
                >开始推演</el-button
              >
            </el-form-item>
          </el-form>
        </div>

        <div v-if="autoResult" class="panel recommend-panel">
          <div class="panel-title"><h3>推荐方案</h3></div>
          <el-alert type="success" :closable="false" show-icon>
            <template #title>
              <span class="recommend-title"
                >{{ recommendedRow?.name }}（综合得分
                {{ formatNumber(recommendedRow?.costEfficiencyScore, 2) }}）</span
              >
            </template>
          </el-alert>
          <div class="stats">
            <div class="stat">
              <div class="label">综合得分</div>
              <div class="value primary">
                {{ formatNumber(recommendedRow?.costEfficiencyScore, 2) }}
              </div>
            </div>
            <div class="stat">
              <div class="label">总成本</div>
              <div class="value">{{ formatMoney(recommendedRow?.totalCost) }}</div>
            </div>
            <div class="stat">
              <div class="label">服务水平</div>
              <div class="value">{{ percent(recommendedRow?.serviceLevel) }}</div>
            </div>
            <div class="stat">
              <div class="label">平均时效</div>
              <div class="value">{{ formatNumber(recommendedRow?.avgLeadDays, 2) }}天</div>
            </div>
            <div class="stat">
              <div class="label">单位履约成本</div>
              <div class="value">{{ formatMoney(recommendedRow?.costPerFulfilledUnit) }}</div>
            </div>
          </div>
          <el-button
            v-if="canWrite() && autoResult.recommended"
            type="primary"
            @click="applyRecommended"
            >应用推荐方案到 OTW</el-button
          >
        </div>

        <div v-if="autoResult" class="panel">
          <div class="panel-title">
            <h3>候选方案排名（共 {{ autoResult.candidateCount }} 个）</h3>
          </div>
          <el-table :data="autoResult.candidates" stripe size="small" :row-class-name="rowClass">
            <el-table-column type="index" label="排名" width="64" />
            <el-table-column prop="name" label="方案" min-width="220" />
            <el-table-column label="分配策略" width="110">
              <template #default="{ row }">{{
                labelOf(row.allocationStrategy, allocationStrategyLabels)
              }}</template>
            </el-table-column>
            <el-table-column prop="safetyDays" label="安全天数" align="right" width="90" />
            <el-table-column label="承运结构" width="110">
              <template #default="{ row }">{{
                labelOf(row.carrierProfile, carrierProfileLabels)
              }}</template>
            </el-table-column>
            <el-table-column label="总成本" align="right">
              <template #default="{ row }">{{ formatMoney(row.totalCost) }}</template>
            </el-table-column>
            <el-table-column label="服务水平" align="right">
              <template #default="{ row }">{{ percent(row.serviceLevel) }}</template>
            </el-table-column>
            <el-table-column label="平均时效" align="right">
              <template #default="{ row }">{{ formatNumber(row.avgLeadDays, 2) }}</template>
            </el-table-column>
            <el-table-column label="缺货" align="right">
              <template #default="{ row }">{{ formatNumber(row.stockoutUnits, 2) }}</template>
            </el-table-column>
            <el-table-column label="综合得分" align="right" width="100">
              <template #default="{ row }">
                <strong>{{ formatNumber(row.costEfficiencyScore, 2) }}</strong>
              </template>
            </el-table-column>
            <el-table-column label="推荐" width="80">
              <template #default="{ row }">
                <el-tag v-if="row.recommended" type="success" size="small">推荐</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </div>
        <el-empty v-else description="设置成本权重后点击“开始推演”" />
      </el-tab-pane>
    </el-tabs>

    <!-- 创建人工场景 -->
    <el-dialog v-model="visible" title="创建沙盘场景" width="760px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-divider content-position="left">基础</el-divider>
        <el-form-item label="场景名称" prop="name"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="成本权重">
          <div class="weight-slider">
            <el-slider
              v-model="form.costWeightPct"
              :min="0"
              :max="100"
              :step="5"
              :marks="{ 0: '效率优先', 50: '均衡', 100: '成本优先' }"
            />
            <div class="weight-text">成本 {{ form.costWeightPct }}% · 效率 {{ 100 - form.costWeightPct }}%</div>
          </div>
        </el-form-item>
        <el-divider content-position="left">需求</el-divider>
        <el-form-item label="需求倍率">
          <el-input-number v-model="form.params.demandMultiplier" :min="0" :step="0.1" />
        </el-form-item>
        <el-form-item label="渠道倍率">
          <el-table :data="channelRows" size="small">
            <el-table-column prop="key" label="渠道" />
            <el-table-column label="倍率">
              <template #default="{ row }">
                <el-input-number
                  v-model="form.params.channelDemandMultiplier[row.key]"
                  :min="0"
                  :step="0.1"
                />
              </template>
            </el-table-column>
          </el-table>
        </el-form-item>
        <el-divider content-position="left">仓配</el-divider>
        <el-form-item label="分配策略">
          <el-select v-model="form.params.allocationStrategy">
            <el-option label="就近分配" value="NEAREST" />
            <el-option label="最低成本" value="LOWEST_COST" />
            <el-option label="均衡分配" value="BALANCED" />
            <el-option label="单仓发货" value="SINGLE_WAREHOUSE" />
          </el-select>
        </el-form-item>
        <el-form-item label="补货提前期">
          <el-input-number v-model="form.params.replenishLeadDays" :min="0" />
        </el-form-item>
        <el-divider content-position="left">承运</el-divider>
        <el-form-item label="承运商比例">
          <el-table :data="carrierRows" size="small">
            <el-table-column prop="key" label="承运商" />
            <el-table-column label="比例">
              <template #default="{ row }">
                <el-input-number v-model="form.params.carrierMix[row.key]" :min="0" :step="0.05" />
              </template>
            </el-table-column>
            <el-table-column label="费率">
              <template #default="{ row }">
                <el-input-number v-model="form.params.carrierRate[row.key]" :min="0" :step="0.1" />
              </template>
            </el-table-column>
          </el-table>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">创建场景</el-button>
      </template>
    </el-dialog>

    <!-- 待执行动作 -->
    <el-dialog v-model="actionDialog" title="已生成待执行动作" width="680px">
      <el-table :data="pendingActions">
        <el-table-column prop="type" label="类型" />
        <el-table-column prop="targetKey" label="目标" />
        <el-table-column prop="status" label="状态">
          <template #default="{ row }">
            <el-tag :type="tagTypes.actionStatus[row.status]">{{
              labelOf(row.status, actionStatusLabels)
            }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="参数">
          <template #default="{ row }">{{ jsonText(row.params) }}</template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>
<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { sandboxApi } from '../api'
import { canWrite } from '../auth'
import Chart from '../components/Chart.vue'
import {
  formatMoney,
  formatNumber,
  jsonText,
  pageResult,
  parseJson,
  percent
} from '../utils/format'
import {
  actionStatusLabels,
  allocationStrategyLabels,
  carrierProfileLabels,
  labelOf,
  scenarioModeLabels,
  scenarioStatusLabels,
  tagTypes
} from '../utils/labels'
const channels = ['TMALL', 'JD', 'DOUYIN', 'OFFLINE', 'API']
const carriers = ['SF', 'JDL', 'ZTO', 'SELF']
const activeTab = ref('manual')
const rows = ref([])
const selected = ref(null)
const loading = ref(false)
const saving = ref(false)
const visible = ref(false)
const actionDialog = ref(false)
const pendingActions = ref([])
const formRef = ref()
const pager = reactive({ current: 1, size: 10, total: 0 })
const form = reactive({
  name: '新场景',
  costWeightPct: 50,
  params: {
    demandMultiplier: 1,
    allocationStrategy: 'NEAREST',
    replenishLeadDays: 3,
    channelDemandMultiplier: {},
    carrierMix: {},
    carrierRate: {}
  }
})
const rules = { name: [{ required: true, message: '请输入场景名称', trigger: 'blur' }] }
const auto = reactive({ costWeightPct: 50, demandMultiplier: 1, horizonDays: 30, loading: false })
const autoResult = ref(null)
const recommendedRow = computed(
  () => (autoResult.value?.candidates || []).find((row) => row.recommended) || null
)
const channelRows = computed(() => channels.map((key) => ({ key })))
const carrierRows = computed(() => carriers.map((key) => ({ key })))
const typeOption = computed(() => ({
  tooltip: {},
  series: [
    {
      type: 'pie',
      radius: '60%',
      data: Object.entries(selected.value?.costByType || {}).map(([name, value]) => ({
        name,
        value
      }))
    }
  ]
}))
const dailyOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  legend: {},
  xAxis: { type: 'category', data: (selected.value?.dailySeries || []).map((row) => row.date) },
  yAxis: [{ type: 'value' }, { type: 'value' }],
  series: [
    { name: '需求', type: 'line', data: (selected.value?.dailySeries || []).map((row) => row.demand) },
    { name: '满足', type: 'line', data: (selected.value?.dailySeries || []).map((row) => row.fulfilled) },
    {
      name: '成本',
      type: 'line',
      yAxisIndex: 1,
      data: (selected.value?.dailySeries || []).map((row) => row.cost)
    }
  ]
}))
const warehouseOption = computed(() => ({
  xAxis: { type: 'category', data: Object.keys(selected.value?.costByWarehouse || {}) },
  yAxis: { type: 'value' },
  series: [{ type: 'bar', data: Object.values(selected.value?.costByWarehouse || {}) }]
}))
const carrierOption = computed(() => ({
  xAxis: { type: 'category', data: Object.keys(selected.value?.costByCarrier || {}) },
  yAxis: { type: 'value' },
  series: [{ type: 'bar', data: Object.values(selected.value?.costByCarrier || {}) }]
}))
function rowClass({ row }) {
  return row.recommended ? 'recommend-row' : ''
}
async function load() {
  loading.value = true
  try {
    const page = pageResult(await sandboxApi.page({ ...pager }))
    rows.value = page.records
    pager.total = page.total
  } finally {
    loading.value = false
  }
}
async function select(row) {
  const value = await sandboxApi.get(row.id)
  const result = parseJson(value.result || value.resultJson, {})
  selected.value = { ...value, ...result }
}
async function run(row) {
  await sandboxApi.run(row.id)
  ElMessage.success('场景运行完成')
  await load()
  await select(row)
}
async function apply(row) {
  const result = await sandboxApi.apply(row.id)
  pendingActions.value = result?.records || result || []
  actionDialog.value = true
}
async function runAuto() {
  auto.loading = true
  try {
    autoResult.value = await sandboxApi.auto({
      costWeight: auto.costWeightPct / 100,
      demandMultiplier: auto.demandMultiplier,
      horizonDays: auto.horizonDays
    })
    ElMessage.success('自动推演完成')
    load()
  } finally {
    auto.loading = false
  }
}
async function applyRecommended() {
  const id = autoResult.value?.recommended?.id
  if (!id) return
  const result = await sandboxApi.apply(id)
  pendingActions.value = result?.records || result || []
  actionDialog.value = true
}
async function openCreate() {
  const defaults = await sandboxApi.defaults()
  Object.assign(form.params, defaults?.params || defaults || {})
  form.params.channelDemandMultiplier = {
    ...(defaults?.channelDemandMultiplier || defaults?.params?.channelDemandMultiplier || {})
  }
  form.params.carrierMix = { ...(defaults?.carrierMix || defaults?.params?.carrierMix || {}) }
  form.params.carrierRate = { ...(defaults?.carrierRate || defaults?.params?.carrierRate || {}) }
  channels.forEach((key) => {
    form.params.channelDemandMultiplier[key] = form.params.channelDemandMultiplier[key] ?? 1
  })
  carriers.forEach((key) => {
    form.params.carrierMix[key] = form.params.carrierMix[key] ?? 0
    form.params.carrierRate[key] = form.params.carrierRate[key] ?? 1
  })
  visible.value = true
}
async function save() {
  await formRef.value.validate()
  saving.value = true
  try {
    await sandboxApi.create({
      name: form.name,
      params: { ...form.params, costWeight: form.costWeightPct / 100 }
    })
    visible.value = false
    ElMessage.success('场景已创建')
    load()
  } finally {
    saving.value = false
  }
}
load()
</script>
<style scoped>
.sandbox-tabs .hint {
  color: #909399;
  font-size: 13px;
}
.auto-form .unit {
  margin-left: 6px;
  color: #909399;
}
.weight-slider {
  width: 320px;
}
.weight-text {
  color: #606266;
  font-size: 12px;
  margin-top: 18px;
}
.recommend-panel .recommend-title {
  font-weight: 600;
}
.stat .value.primary {
  color: #409eff;
}
:deep(.recommend-row) {
  background: #f0f9eb;
}
</style>
