<template>
  <div class="page">
    <div class="page-title">
      <div>
        <h2>人工沙盘</h2>
        <p class="subtitle">手工设定需求、仓配、承运和资金盘金额，评估成本与效率后再下发协同指令</p>
      </div>
      <div>
        <el-button v-if="canWrite()" @click="openCapital">资金盘推演</el-button>
        <el-button v-if="canWrite()" type="primary" @click="openCreate">创建场景</el-button>
      </div>
    </div>
    <div class="grid-2">
      <div class="panel">
        <div class="panel-title">
          <h3>场景列表</h3>
          <el-button @click="load">刷新</el-button>
        </div>
        <el-table v-loading="loading" :data="rows" stripe @row-click="select"
          ><el-table-column prop="name" label="场景名称" min-width="150" /><el-table-column
            prop="status"
            label="状态"
            ><template #default="{ row }"
              ><el-tag :type="tagTypes.scenarioStatus[row.status]">{{
                labelOf(row.status, scenarioStatusLabels)
              }}</el-tag></template
            ></el-table-column
          ><el-table-column prop="totalCost" label="总成本" align="right"
            ><template #default="{ row }">{{
              formatMoney(row.totalCost)
            }}</template></el-table-column
          ><el-table-column label="成本分" align="right" width="90"
            ><template #default="{ row }">{{
              formatNumber(row.costScore, 4)
            }}</template></el-table-column
          ><el-table-column label="效率分" align="right" width="90"
            ><template #default="{ row }">{{
              formatNumber(row.efficiencyScore, 4)
            }}</template></el-table-column
          ><el-table-column label="综合分" align="right" width="90"
            ><template #default="{ row }">{{
              formatNumber(row.balanceScore, 4)
            }}</template></el-table-column
          ><el-table-column prop="createdAt" label="创建时间" min-width="160"
            ><template #default="{ row }">{{
              formatDate(row.createdAt)
            }}</template></el-table-column
          ><el-table-column label="服务水平" align="right"
            ><template #default="{ row }">{{
              percent(row.serviceLevel)
            }}</template></el-table-column
          ><el-table-column label="操作" width="240"
            ><template #default="{ row }"
              ><el-button v-if="canWrite()" link @click.stop="run(row)">运行</el-button
              ><el-button v-if="canWrite()" link type="primary" @click.stop="apply(row, false)"
                >生成指令</el-button
              ><el-button v-if="canWrite()" link type="warning" @click.stop="apply(row, true)"
                >立即执行</el-button
              ></template
            ></el-table-column
          ><template #empty><el-empty description="暂无沙盘场景" /></template
        ></el-table>
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
          <div class="stat">
            <div class="label">成本分</div>
            <div class="value">{{ formatNumber(selected.costScore, 4) }}</div>
          </div>
          <div class="stat">
            <div class="label">效率分</div>
            <div class="value">{{ formatNumber(selected.efficiencyScore, 4) }}</div>
          </div>
          <div class="stat">
            <div class="label">综合分</div>
            <div class="value">{{ formatNumber(selected.balanceScore, 4) }}</div>
          </div>
          <div class="stat">
            <div class="label">资金盘</div>
            <div class="value">{{ formatMoney(selected.workingCapital) }}</div>
          </div>
          <div class="stat">
            <div class="label">现金占用</div>
            <div class="value">{{ percent(selected.capitalUtilization) }}</div>
          </div>
          <div class="stat">
            <div class="label">资金结论</div>
            <div class="value">
              <el-tag :type="tagTypes.capitalVerdict[selected.capitalVerdict]">
                {{ labelOf(selected.capitalVerdict, capitalVerdictLabels) }}
              </el-tag>
            </div>
          </div>
        </div>
        <div v-if="selected" class="grid-2">
          <Chart :option="typeOption" /><Chart :option="dailyOption" /><Chart
            :option="warehouseOption"
          /><Chart :option="carrierOption" />
        </div>
        <el-table
          v-if="selected"
          class="sandbox-sku-table"
          :data="selected.perSkuSummary || []"
          size="small"
          ><el-table-column prop="sku" label="SKU" /><el-table-column
            prop="demand"
            label="需求"
            align="right"
            ><template #default="{ row }">{{
              formatNumber(row.demand, 2)
            }}</template></el-table-column
          ><el-table-column prop="fulfilled" label="满足" align="right"
            ><template #default="{ row }">{{
              formatNumber(row.fulfilled, 2)
            }}</template></el-table-column
          ><el-table-column prop="stockout" label="缺货" align="right"
            ><template #default="{ row }">{{
              formatNumber(row.stockout, 2)
            }}</template></el-table-column
          ></el-table
        ><el-empty v-else description="选择一个场景查看结果" />
        <p v-if="selected?.skuSummaryTruncated" class="muted">仅展示缺货最多的 50 个 SKU</p>
      </div>
    </div>
    <el-dialog v-model="visible" title="创建沙盘场景" width="760px"
      ><el-form ref="formRef" :model="form" :rules="rules" label-width="120px"
        ><el-divider content-position="left">基础</el-divider
        ><el-form-item label="场景名称" prop="name"><el-input v-model="form.name" /></el-form-item
        ><el-divider content-position="left">需求</el-divider
        ><el-form-item label="需求倍率"
          ><el-input-number
            v-model="form.params.demandMultiplier"
            :min="0"
            :step="0.1" /></el-form-item
        ><el-form-item label="渠道倍率"
          ><el-table :data="channelRows" size="small"
            ><el-table-column prop="key" label="渠道" /><el-table-column label="倍率"
              ><template #default="{ row }"
                ><el-input-number
                  v-model="form.params.channelDemandMultiplier[row.key]"
                  :min="0"
                  :step="0.1" /></template></el-table-column></el-table></el-form-item
        ><el-divider content-position="left">仓配</el-divider
        ><el-form-item label="分配策略"
          ><el-select v-model="form.params.allocationStrategy"
            ><el-option label="就近分配" value="NEAREST" /><el-option
              label="最低成本"
              value="LOWEST_COST" /><el-option label="均衡分配" value="BALANCED" /><el-option
              label="单仓发货"
              value="SINGLE_WAREHOUSE" /></el-select></el-form-item
        ><el-form-item label="成本权重"
          ><el-input-number
            v-model="form.params.costWeight"
            :min="0"
            :max="1"
            :step="0.1" /></el-form-item
        ><el-form-item label="效率权重"
          ><el-input-number
            v-model="form.params.efficiencyWeight"
            :min="0"
            :max="1"
            :step="0.1" /></el-form-item
        ><el-form-item label="补货提前期"
          ><el-input-number v-model="form.params.replenishLeadDays" :min="0" /></el-form-item
        ><el-form-item label="资金盘"
          ><div class="tier-row">
            <el-button
              v-for="tier in capitalTiers"
              :key="tier.code"
              size="small"
              :type="Number(form.params.workingCapital) === Number(tier.amount) ? 'primary' : ''"
              @click="form.params.workingCapital = Number(tier.amount)"
              >{{ tier.label }}</el-button
            >
          </div>
          <el-input-number
            v-model="form.params.workingCapital"
            :min="1"
            :step="100000"
            :max="100000000000" /></el-form-item
        ><el-divider content-position="left">承运</el-divider
        ><el-form-item label="承运商比例"
          ><el-table :data="carrierRows" size="small"
            ><el-table-column prop="key" label="承运商" /><el-table-column label="比例"
              ><template #default="{ row }"
                ><el-input-number
                  v-model="form.params.carrierMix[row.key]"
                  :min="0"
                  :step="0.05" /></template></el-table-column
            ><el-table-column label="费率"
              ><template #default="{ row }"
                ><el-input-number
                  v-model="form.params.carrierRate[row.key]"
                  :min="0"
                  :step="0.1" /></template></el-table-column></el-table></el-form-item></el-form
      ><template #footer
        ><el-button @click="visible = false">取消</el-button
        ><el-button type="primary" :loading="saving" @click="save">创建场景</el-button></template
      ></el-dialog
    >
    <el-dialog v-model="capitalDialog" title="资金盘量级推演" width="920px">
      <div class="tier-row">
        <el-button
          v-for="tier in capitalTiers"
          :key="tier.code"
          :type="Number(capitalAmount) === Number(tier.amount) ? 'primary' : ''"
          @click="capitalAmount = Number(tier.amount)"
          >{{ tier.label }}</el-button
        >
      </div>
      <el-form label-width="120px" class="capital-form">
        <el-form-item label="自定义金额">
          <el-input-number v-model="capitalAmount" :min="1" :step="100000" :max="100000000000" />
        </el-form-item>
        <el-form-item label="SKU 个数">
          <el-input-number v-model="scaleSkuCount" :min="0" :max="100000" :step="100" />
          <span class="muted"> 0 表示用当前快照；上限 10 万</span>
        </el-form-item>
        <el-form-item label="单 SKU 库存">
          <el-input-number v-model="scaleInventoryQty" :min="0" :max="10000000" :step="1000" />
          <span class="muted"> 上限 1000 万</span>
        </el-form-item>
      </el-form>
      <div v-if="capitalResult">
        <p>{{ capitalResult.reason }}</p>
        <p v-if="capitalResult.recommended" class="subtitle">
          推荐策略：{{ capitalResult.recommended.name }}（安全 {{ capitalResult.recommended.safetyDays }} 天 / 补货 {{ capitalResult.recommended.replenishLeadDays }} 天）
        </p>
        <div class="stats">
          <div class="stat">
            <div class="label">结论</div>
            <div class="value">
              <el-tag :type="tagTypes.capitalVerdict[capitalResult.verdict]">
                {{ labelOf(capitalResult.verdict, capitalVerdictLabels) }}
              </el-tag>
            </div>
          </div>
          <div class="stat">
            <div class="label">资金盘</div>
            <div class="value">{{ formatMoney(capitalResult.workingCapital) }}</div>
          </div>
          <div class="stat">
            <div class="label">档位</div>
            <div class="value">{{ capitalResult.label || '-' }}</div>
          </div>
          <div class="stat">
            <div class="label">SKU / 库存</div>
            <div class="value">{{ capitalResult.skuCount ?? '-' }} / {{ formatNumber(capitalResult.inventoryUnits, 0) }}</div>
          </div>
          <div class="stat">
            <div class="label">安全垫</div>
            <div class="value">{{ formatNumber(capitalResult.headroom, 1) }}倍</div>
          </div>
          <div class="stat">
            <div class="label">最低可靠资金</div>
            <div class="value">{{ formatMoney(capitalResult.minReliableCapital) }}</div>
          </div>
        </div>
        <el-table :data="capitalRows" size="small">
          <el-table-column prop="name" label="情景" />
          <el-table-column label="占用">
            <template #default="{ row }">{{ percent(row.capitalUtilization) }}</template>
          </el-table-column>
          <el-table-column label="服务水平">
            <template #default="{ row }">{{ percent(row.serviceLevel) }}</template>
          </el-table-column>
          <el-table-column label="缺货">
            <template #default="{ row }">{{ formatNumber(row.stockoutUnits, 2) }}</template>
          </el-table-column>
          <el-table-column label="结论">
            <template #default="{ row }">{{
              labelOf(row.capitalVerdict, capitalVerdictLabels)
            }}</template>
          </el-table-column>
        </el-table>
        <el-table
          v-if="capitalResult.playbook?.length"
          :data="capitalResult.playbook"
          size="small"
          style="margin-top: 12px"
          :row-class-name="playbookRowClass"
        >
          <el-table-column label="策略" min-width="180">
            <template #default="{ row }">
              {{ row.name }}
              <el-tag v-if="row.recommended" type="success" size="small">推荐</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="现金">
            <template #default="{ row }">{{ formatMoney(row.cashUsed) }}</template>
          </el-table-column>
          <el-table-column label="成本">
            <template #default="{ row }">{{ formatMoney(row.totalCost) }}</template>
          </el-table-column>
          <el-table-column label="服务水平">
            <template #default="{ row }">{{ percent(row.serviceLevel) }}</template>
          </el-table-column>
          <el-table-column label="缺货">
            <template #default="{ row }">{{ formatNumber(row.stockoutUnits, 2) }}</template>
          </el-table-column>
        </el-table>
        <ul v-if="capitalResult.optimizations?.length" class="subtitle" style="margin-top: 12px">
          <li v-for="item in capitalResult.optimizations" :key="item">{{ item }}</li>
        </ul>
      </div>
      <div v-if="sweepResult" class="sweep-block">
        <h4>
          量级扫描
          <el-tag :type="sweepResult.flowOk ? 'success' : 'danger'" size="small">
            {{ sweepResult.flowOk ? '全流程通过' : '发现问题' }}
          </el-tag>
        </h4>
        <el-table :data="sweepResult.rows || []" size="small">
          <el-table-column prop="label" label="档位" width="90" />
          <el-table-column label="金额">
            <template #default="{ row }">{{ formatMoney(row.workingCapital) }}</template>
          </el-table-column>
          <el-table-column label="结论" width="90">
            <template #default="{ row }">
              {{ labelOf(row.verdict, capitalVerdictLabels) }}
            </template>
          </el-table-column>
          <el-table-column label="占用现金">
            <template #default="{ row }">{{ formatMoney(row.cashUsed) }}</template>
          </el-table-column>
          <el-table-column label="服务水平">
            <template #default="{ row }">{{ percent(row.serviceLevel) }}</template>
          </el-table-column>
          <el-table-column prop="recommendedName" label="推荐策略" min-width="140" />
          <el-table-column label="问题">
            <template #default="{ row }">{{ (row.issues || []).join('；') || '无' }}</template>
          </el-table-column>
        </el-table>
        <p v-if="sweepResult.issues?.length" class="danger">{{ sweepResult.issues.join('；') }}</p>
      </div>
      <template #footer>
        <el-button @click="capitalDialog = false">关闭</el-button>
        <el-button :loading="capitalLoading" @click="analyzeCapital">推演当前金额</el-button>
        <el-button type="warning" :loading="sweeping" @click="sweepCapital">自动跑完所有档位</el-button>
        <el-button
          v-if="canWrite() && capitalResult?.recommended"
          type="primary"
          :loading="adopting"
          @click="adoptRecommended"
        >采纳推荐策略</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="actionDialog" title="已生成待执行动作" width="680px"
      ><el-table :data="pendingActions"
        ><el-table-column prop="type" label="类型" /><el-table-column
          prop="targetKey"
          label="目标"
        /><el-table-column prop="status" label="状态"
          ><template #default="{ row }"
            ><el-tag :type="tagTypes.actionStatus[row.status]">{{
              labelOf(row.status, actionStatusLabels)
            }}</el-tag></template
          ></el-table-column
        ><el-table-column label="参数"
          ><template #default="{ row }">{{ jsonText(row.params) }}</template></el-table-column
        ></el-table
      ></el-dialog
    >
  </div>
</template>
<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { sandboxApi } from '../api'
import { canWrite } from '../auth'
import Chart from '../components/Chart.vue'
import {
  formatDate,
  formatMoney,
  formatNumber,
  jsonText,
  pageResult,
  parseJson,
  percent
} from '../utils/format'
import { actionStatusLabels, capitalVerdictLabels, labelOf, scenarioStatusLabels, tagTypes } from '../utils/labels'
const channels = ['TMALL', 'JD', 'DOUYIN', 'OFFLINE', 'API']
const carriers = ['SF', 'JD', 'SELF01']
const rows = ref([])
const selected = ref(null)
const loading = ref(false)
const saving = ref(false)
const visible = ref(false)
const actionDialog = ref(false)
const FALLBACK_TIERS = [
  { code: '100K', label: '10 万', amount: 100000 },
  { code: '1M', label: '百万', amount: 1000000 },
  { code: '10M', label: '千万', amount: 10000000 },
  { code: '100M', label: '亿', amount: 100000000 },
  { code: '1B', label: '十亿', amount: 1000000000 }
]
const capitalDialog = ref(false)
const capitalResult = ref(null)
const capitalTiers = ref(FALLBACK_TIERS)
const capitalAmount = ref(100000000)
const scaleSkuCount = ref(0)
const scaleInventoryQty = ref(1000)
const sweepResult = ref(null)
const sweeping = ref(false)
const capitalLoading = ref(false)
const adopting = ref(false)
const pendingActions = ref([])
const formRef = ref()
const pager = reactive({ current: 1, size: 10, total: 0 })
const form = reactive({
  name: '新场景',
  params: {
    demandMultiplier: 1,
    allocationStrategy: 'NEAREST',
    replenishLeadDays: 3,
    costWeight: 0.5,
    efficiencyWeight: 0.5,
    workingCapital: 100000000,
    channelDemandMultiplier: {},
    carrierMix: {},
    carrierRate: {}
  }
})
const rules = { name: [{ required: true, message: '请输入场景名称', trigger: 'blur' }] }
const channelRows = computed(() => channels.map((key) => ({ key })))
const capitalRows = computed(() => {
  if (!capitalResult.value) return []
  return [
    { name: '常态 30 天', ...(capitalResult.value.baseline || {}) },
    { name: '2 倍需求', ...(capitalResult.value.demand2x || {}) },
    { name: '5 倍需求', ...(capitalResult.value.demand5x || {}) }
  ]
})
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
    {
      name: '需求',
      type: 'line',
      data: (selected.value?.dailySeries || []).map((row) => row.demand)
    },
    {
      name: '满足',
      type: 'line',
      data: (selected.value?.dailySeries || []).map((row) => row.fulfilled)
    },
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
async function load() {
  loading.value = true
  try {
    const page = pageResult(await sandboxApi.page({ ...pager, kind: 'MANUAL' }))
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
async function apply(row, execute = false) {
  const result = await sandboxApi.apply(row.id, execute)
  pendingActions.value = result?.records || result || []
  actionDialog.value = true
}
async function openCreate() {
  const defaults = await sandboxApi.defaults()
  Object.assign(form.params, defaults?.params || defaults || {})
  form.params.channelDemandMultiplier = {
    ...(defaults?.channelDemandMultiplier || defaults?.params?.channelDemandMultiplier || {})
  }
  form.params.carrierMix = {
    ...(defaults?.carrierMix || defaults?.params?.carrierMix || {})
  }
  form.params.carrierRate = {
    ...(defaults?.carrierRate || defaults?.params?.carrierRate || {})
  }
  channels.forEach((key) => {
    form.params.channelDemandMultiplier[key] = form.params.channelDemandMultiplier[key] ?? 1
  })
  carriers.forEach((key) => {
    form.params.carrierMix[key] = form.params.carrierMix[key] ?? 0
    form.params.carrierRate[key] = form.params.carrierRate[key] ?? 1
  })
  visible.value = true
}
function playbookRowClass({ row }) {
  return row.recommended ? 'is-recommended' : ''
}
function scalePayload(extra = {}) {
  const payload = { workingCapital: capitalAmount.value, ...extra }
  if (Number(scaleSkuCount.value) > 0) {
    payload.skuCount = Number(scaleSkuCount.value)
  }
  if (Number(scaleInventoryQty.value) > 0) {
    payload.inventoryQty = Number(scaleInventoryQty.value)
  }
  return payload
}
async function loadTiers() {
  try {
    const data = await sandboxApi.capitalTiers()
    const presets = data?.presets || data
    if (Array.isArray(presets) && presets.length) {
      capitalTiers.value = presets
    }
  } catch (error) {
    capitalTiers.value = FALLBACK_TIERS
  }
}
async function openCapital() {
  capitalDialog.value = true
  await loadTiers()
}
async function analyzeCapital() {
  capitalLoading.value = true
  try {
    capitalResult.value = await sandboxApi.capital(scalePayload())
    const label = capitalResult.value?.label || ''
    ElMessage.success(
      capitalResult.value?.reliable
        ? `${label}资金盘推演完成，结论可靠`
        : `${label}资金盘推演完成，需要关注资金压力`
    )
  } finally {
    capitalLoading.value = false
  }
}
async function sweepCapital() {
  sweeping.value = true
  try {
    const payload = scalePayload()
    const presetAmounts = new Set(capitalTiers.value.map((tier) => Number(tier.amount)))
    if (!presetAmounts.has(Number(capitalAmount.value))) {
      payload.customAmount = capitalAmount.value
    }
    sweepResult.value = await sandboxApi.capitalSweep(payload)
    ElMessage.success(
      sweepResult.value?.flowOk
        ? '量级扫描通过，全流程没有发现问题'
        : '量级扫描完成，存在需要关注的问题'
    )
  } finally {
    sweeping.value = false
  }
}
async function adoptRecommended() {
  adopting.value = true
  try {
    const scenario = await sandboxApi.adoptCapital({ workingCapital: capitalAmount.value })
    capitalDialog.value = false
    ElMessage.success(`已生成场景「${scenario.name}」`)
    await load()
    if (scenario?.id) {
      await select(scenario)
      await apply(scenario, false)
    }
  } finally {
    adopting.value = false
  }
}
async function save() {
  await formRef.value.validate()
  saving.value = true
  try {
    await sandboxApi.create(form)
    visible.value = false
    ElMessage.success('场景已创建')
    load()
  } finally {
    saving.value = false
  }
}
loadTiers()
load()
</script>
<style scoped>
:deep(.is-recommended) td {
  background: var(--el-color-success-light-9);
}
.tier-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}
.capital-form {
  margin-top: 4px;
}
.muted {
  color: var(--el-text-color-secondary);
  margin-left: 8px;
  font-size: 12px;
}
.danger {
  color: var(--el-color-danger);
}
.sweep-block {
  margin-top: 16px;
}
.sweep-block h4 {
  margin: 0 0 8px;
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
