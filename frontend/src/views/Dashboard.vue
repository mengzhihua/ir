<template>
  <div class="page">
    <div class="page-title">
      <div>
        <h2>控制塔总览</h2>
        <p class="subtitle">订单、库存、运输、采购、ERP 与经销商网络实时看板</p>
      </div>
      <el-button type="primary" :loading="loading" @click="load">刷新数据</el-button>
    </div>
    <div class="panel policy-panel">
      <div class="panel-title">
        <h3>成本 / 效率策略</h3>
        <div>
          <el-tag :type="stanceType">{{ stanceLabel }}</el-tag>
          <el-button
            v-if="canWrite()"
            type="primary"
            :loading="saving"
            style="margin-left: 8px"
            @click="savePolicy"
            >保存并重评预警</el-button
          >
        </div>
      </div>
      <p class="muted">
        拖动滑块改变控制塔立场。成本侧重会挂起卡单、换便宜承运商、加大采购批量；效率侧重会自动过审、追更快运力、仓内补货。
      </p>
      <div class="policy-row">
        <span>效率</span>
        <el-slider
          v-model="costPercent"
          :disabled="!canWrite()"
          :show-tooltip="true"
          :format-tooltip="policyTip"
        />
        <span>成本</span>
      </div>
      <div class="muted">
        成本权重 {{ (costPercent / 100).toFixed(2) }} · 效率权重
        {{ ((100 - costPercent) / 100).toFixed(2) }}
        · 补货安全 {{ overview.policy?.safetyDays ?? '-' }} 天 / 提前期
        {{ overview.policy?.replenishLeadDays ?? '-' }} 天
        <template v-if="carrierSummary"> · 在途承运 {{ carrierSummary }}</template>
      </div>
    </div>
    <div class="stats">
      <div
        v-for="item in cards"
        :key="item.label"
        class="stat"
        :style="{ borderColor: item.color }"
      >
        <div class="label">{{ item.label }}</div>
        <div class="value">{{ item.value }}</div>
      </div>
    </div>
    <div class="grid-2">
      <div class="panel">
        <div class="panel-title">
          <h3>OTW履约漏斗</h3>
          <span class="muted">OMS / WMS / TMS</span>
        </div>
        <Chart :option="funnelOption" />
      </div>
      <div class="panel">
        <div class="panel-title">
          <h3>30日成本趋势</h3>
          <span class="muted">按成本类型堆叠</span>
        </div>
        <Chart :option="costOption" />
      </div>
    </div>
    <div class="grid-2">
      <div class="panel">
        <div class="panel-title">
          <h3>仓库负载</h3>
          <span class="muted">订单和库存风险</span>
        </div>
        <el-table :data="overview.warehouseLoad || []" stripe>
          <el-table-column prop="warehouseCode" label="仓库" />
          <el-table-column prop="pendingOrders" label="待处理订单" align="right" />
          <el-table-column prop="inventoryQty" label="库存量" align="right" />
          <el-table-column prop="lowStockSkus" label="低库存SKU" align="right">
            <template #default="{ row }"
              ><span :class="{ danger: row.lowStockSkus > 0 }">{{
                row.lowStockSkus
              }}</span></template
            >
          </el-table-column>
          <template #empty><el-empty description="暂无仓库数据" /></template>
        </el-table>
      </div>
      <div class="panel">
        <div class="panel-title">
          <h3>重点预警</h3>
          <el-button link type="primary" @click="$router.push('/alert')">查看全部</el-button>
        </div>
        <el-table :data="overview.alertsTop || []" stripe>
          <el-table-column prop="title" label="预警" min-width="180" />
          <el-table-column prop="severity" label="等级" width="85"
            ><template #default="{ row }"
              ><el-tag :type="tagTypes.severity[row.severity]">{{
                labelOf(row.severity, severityLabels)
              }}</el-tag></template
            ></el-table-column
          >
          <el-table-column prop="warehouseCode" label="仓库" width="90" />
          <el-table-column prop="createdAt" label="时间" width="155"
            ><template #default="{ row }">{{
              formatDate(row.createdAt)
            }}</template></el-table-column
          >
          <template #empty><el-empty description="暂无开放预警" /></template>
        </el-table>
      </div>
      <div class="panel">
        <div class="panel-title">
          <h3>自动沙盘推荐</h3>
          <el-button link type="primary" @click="$router.push('/sandbox/auto')">去推演</el-button>
        </div>
        <div v-if="overview.recommendation" class="stats">
          <div class="stat">
            <div class="label">方案</div>
            <div class="value">{{ overview.recommendation.name }}</div>
          </div>
          <div class="stat">
            <div class="label">综合分</div>
            <div class="value">{{ formatNumber(overview.recommendation.balanceScore, 4) }}</div>
          </div>
          <div class="stat">
            <div class="label">成本 / 效率</div>
            <div class="value">
              {{ formatNumber(overview.recommendation.costScore, 4) }} /
              {{ formatNumber(overview.recommendation.efficiencyScore, 4) }}
            </div>
          </div>
          <div class="stat">
            <div class="label">占用现金</div>
            <div class="value">{{ formatMoney(overview.recommendation.cashUsed) }}</div>
          </div>
          <div class="stat">
            <div class="label">补货策略</div>
            <div class="value">
              安全 {{ overview.recommendation.safetyDays ?? '-' }} /
              提前期 {{ overview.recommendation.replenishLeadDays ?? '-' }} 天
            </div>
          </div>
          <div class="stat">
            <div class="label">总成本</div>
            <div class="value">{{ formatMoney(overview.recommendation.totalCost) }}</div>
          </div>
          <div class="stat">
            <div class="label">2倍需求</div>
            <div class="value">{{ overview.recommendation.stressReliable ? '稳健' : '未过' }}</div>
          </div>
          <div class="stat">
            <div class="label">资金盘</div>
            <div class="value">
              {{ formatMoney(overview.recommendation.workingCapital) }}
              · {{ labelOf(overview.recommendation.capitalVerdict, capitalVerdictLabels) }}
            </div>
          </div>
        </div>
        <p v-if="overview.recommendation?.pickRationale?.reason" class="muted">
          {{ overview.recommendation.pickRationale.reason }}
        </p>
        <el-empty v-if="!overview.recommendation" description="尚未产生自动沙盘推荐" />
      </div>
    </div>
    <div class="panel">
      <div class="panel-title">
        <h3>生态单据</h3>
        <span class="muted">SAP / SRM / BOM / INV / CRM / DMS / OA 快照条数</span>
      </div>
      <el-table :data="ecosystemRows" stripe>
        <el-table-column prop="system" label="系统" width="90" />
        <el-table-column prop="summary" label="单据类型" />
        <template #empty><el-empty description="暂无生态快照" /></template>
      </el-table>
    </div>
    <div class="panel">
      <div class="panel-title">
        <h3>系统健康</h3>
        <span class="muted">最后同步时间</span>
      </div>
      <div class="health-list">
        <div
          v-for="system in overview.systems || []"
          :key="system.id || system.code"
          class="health"
        >
          <i :class="{ ok: healthOk(system) }" />
          <div>
            <b>{{ system.name || system.systemName || system.code }}</b>
            <div class="muted">
              {{ labelOf(system.mode, systemModeLabels) }} ·
              {{ formatDate(system.lastHealthAt) }}
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { sandboxApi, towerApi } from '../api'
import { canWrite } from '../auth'
import Chart from '../components/Chart.vue'
import { formatDate, formatMoney, formatNumber, percent } from '../utils/format'
import { capitalVerdictLabels, labelOf, severityLabels, systemModeLabels, tagTypes } from '../utils/labels'

const loading = ref(false)
const overview = reactive({
  kpi: {},
  funnel: {},
  costTrend: [],
  warehouseLoad: [],
  alertsTop: [],
  systems: [],
  recommendation: null,
  ecosystem: {},
  policy: { costWeight: 0.5, efficiencyWeight: 0.5, stance: 'BALANCED', safetyDays: 3, replenishLeadDays: 3 }
})
const saving = ref(false)
const costPercent = ref(50)

const stanceLabel = computed(() => {
  const stance = overview.policy?.stance
  if (stance === 'COST') return '成本优先'
  if (stance === 'EFFICIENCY') return '效率优先'
  return '均衡'
})
const stanceType = computed(() => {
  const stance = overview.policy?.stance
  if (stance === 'COST') return 'warning'
  if (stance === 'EFFICIENCY') return 'success'
  return 'info'
})
const carrierSummary = computed(() => {
  const mix = overview.kpi?.carrierMix || {}
  return Object.entries(mix)
    .map(([code, count]) => `${code} ${count}`)
    .join(' / ')
})
function policyTip(value) {
  return `成本 ${((value || 0) / 100).toFixed(2)} / 效率 ${((100 - (value || 0)) / 100).toFixed(2)}`
}

const cards = computed(() => [
  { label: '今日订单', value: formatNumber(overview.kpi.todayOrders, 0), color: '#409eff' },
  { label: '待处理订单', value: formatNumber(overview.kpi.pendingOrders, 0), color: '#e6a23c' },
  { label: '在途运单', value: formatNumber(overview.kpi.inTransit, 0), color: '#67c23a' },
  { label: '延迟运单', value: formatNumber(overview.kpi.delayedShipments, 0), color: '#f56c6c' },
  { label: '低库存SKU', value: formatNumber(overview.kpi.lowStockSkus, 0), color: '#f56c6c' },
  { label: '开放预警', value: formatNumber(overview.kpi.openAlerts, 0), color: '#f56c6c' },
  { label: '待执行指令', value: formatNumber(overview.kpi.pendingActions, 0), color: '#e6a23c' },
  { label: '30日总成本', value: formatMoney(overview.kpi.totalCost30d), color: '#409eff' },
  { label: '单均成本', value: formatMoney(overview.kpi.costPerOrder30d), color: '#409eff' },
  { label: 'OTIF', value: percent(overview.kpi.otif30d), color: '#67c23a' },
  {
    label: '平均时效',
    value: `${formatNumber(overview.kpi.avgLeadTimeHours, 1)}小时`,
    color: '#909399'
  },
  { label: 'SAP低库存', value: formatNumber(overview.kpi.sapLowStock, 0), color: '#f56c6c' },
  { label: 'SRM待提交PR', value: formatNumber(overview.kpi.srmOpenPr, 0), color: '#e6a23c' },
  { label: 'DMS备件缺货', value: formatNumber(overview.kpi.dmsShortage, 0), color: '#f56c6c' },
  { label: 'CRM新工单', value: formatNumber(overview.kpi.crmOpenCases, 0), color: '#e6a23c' },
  { label: 'OA待办', value: formatNumber(overview.kpi.oaPendingTasks, 0), color: '#909399' }
])

const costOption = computed(() => {
  const rows = Array.isArray(overview.costTrend) ? overview.costTrend : []
  const types = [...new Set(rows.flatMap((row) => Object.keys(row.byType || {})))]
  return {
    tooltip: { trigger: 'axis' },
    legend: { top: 0, data: types },
    grid: { left: 45, right: 20, top: 40, bottom: 30 },
    xAxis: { type: 'category', data: rows.map((row) => row.date) },
    yAxis: { type: 'value' },
    series: types.map((type) => ({
      name: type,
      type: 'line',
      stack: 'cost',
      areaStyle: {},
      data: rows.map((row) => Number(row.byType?.[type] || 0))
    }))
  }
})

const funnelOption = computed(() => {
  const labels = ['创建', '审核', '分配', '推送', '出库', '完成']
  const aliases = {
    创建: ['CREATED'],
    审核: ['AUDITED'],
    分配: ['ALLOCATED'],
    推送: ['PUSHED'],
    出库: ['SHIPPED', 'PACKED'],
    完成: ['COMPLETED', 'DELIVERED', 'CLOSED']
  }
  const value = (group, label) =>
    (aliases[label] || []).reduce((sum, key) => sum + Number(group?.[key] || 0), 0)
  return {
    tooltip: { trigger: 'axis' },
    legend: { data: ['OMS', 'WMS', 'TMS'] },
    grid: { left: 45, right: 20, top: 40, bottom: 30 },
    xAxis: { type: 'category', data: labels },
    yAxis: { type: 'value' },
    series: ['oms', 'wms', 'tms'].map((name) => ({
      name: name.toUpperCase(),
      type: 'bar',
      data: labels.map((label) => value(overview.funnel?.[name], label))
    }))
  }
})

function healthOk(system) {
  return system.lastHealthOk === true || String(system.healthStatus).toUpperCase() === 'UP'
}

const ecosystemRows = computed(() =>
  Object.entries(overview.ecosystem || {}).map(([system, types]) => ({
    system,
    summary: Object.entries(types || {})
      .map(([type, count]) => `${type} ${count}`)
      .join(' · ')
  }))
)

async function load() {
  loading.value = true
  try {
    Object.assign(overview, await towerApi.overview())
    const cost = Number(overview.policy?.costWeight)
    if (!Number.isNaN(cost)) {
      costPercent.value = Math.round(cost * 100)
    }
  } finally {
    loading.value = false
  }
}

async function savePolicy() {
  saving.value = true
  try {
    await sandboxApi.savePolicy({
      costWeight: costPercent.value / 100,
      efficiencyWeight: (100 - costPercent.value) / 100,
      reevaluate: true
    })
    ElMessage.success('策略已保存，预警建议已按新权重重算')
    await load()
  } finally {
    saving.value = false
  }
}

load()
</script>

<style scoped>
.health-list {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.health {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 14px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
}

.health i {
  width: 11px;
  height: 11px;
  margin-top: 4px;
  border-radius: 50%;
  background: #f56c6c;
}

.health i.ok {
  background: #67c23a;
}

.policy-panel {
  margin-bottom: 16px;
}

.policy-row {
  display: grid;
  grid-template-columns: 48px 1fr 48px;
  gap: 12px;
  align-items: center;
  max-width: 640px;
  margin: 8px 0 4px;
}

@media (max-width: 1200px) {
  .health-list {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
</style>
