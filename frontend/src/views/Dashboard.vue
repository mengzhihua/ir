<template>
  <div class="page">
    <div class="page-title">
      <div>
        <h2>控制塔总览</h2>
        <p class="subtitle">订单、库存、运输、成本和系统健康度实时看板</p>
      </div>
      <el-button type="primary" :loading="loading" @click="load">刷新数据</el-button>
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
import { balanceApi, objectiveApi, towerApi } from '../api'
import Chart from '../components/Chart.vue'
import { formatDate, formatMoney, formatNumber, percent } from '../utils/format'
import { labelOf, severityLabels, systemModeLabels, tagTypes } from '../utils/labels'

const loading = ref(false)
const board = reactive({ score: 0, metrics: {} })
const balance = reactive({})
const overview = reactive({
  kpi: {},
  funnel: {},
  costTrend: [],
  warehouseLoad: [],
  alertsTop: [],
  systems: []
})

const cards = computed(() => [
  { label: '今日订单', value: formatNumber(overview.kpi.todayOrders, 0), color: '#409eff' },
  { label: '待处理订单', value: formatNumber(overview.kpi.pendingOrders, 0), color: '#e6a23c' },
  { label: '在途运单', value: formatNumber(overview.kpi.inTransit, 0), color: '#67c23a' },
  { label: '延迟运单', value: formatNumber(overview.kpi.delayedShipments, 0), color: '#f56c6c' },
  { label: '低库存SKU', value: formatNumber(overview.kpi.lowStockSkus, 0), color: '#f56c6c' },
  { label: '开放预警', value: formatNumber(overview.kpi.openAlerts, 0), color: '#f56c6c' },
  { label: '30日总成本', value: formatMoney(overview.kpi.totalCost30d), color: '#409eff' },
  { label: '单均成本', value: formatMoney(overview.kpi.costPerOrder30d), color: '#409eff' },
  { label: 'OTIF', value: percent(overview.kpi.otif30d), color: '#67c23a' },
  {
    label: '平均时效',
    value: `${formatNumber(overview.kpi.avgLeadTimeHours, 1)}小时`,
    color: '#909399'
  },
  { label: 'NPS估算', value: formatNumber(board.metrics.npsEstimate, 1), color: '#67c23a' },
  { label: '目标达成分', value: formatNumber(board.score, 1), color: '#409eff' },
  { label: '待审批决策', value: formatNumber(balance.pendingDecisions, 0), color: '#e6a23c' }
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

async function load() {
  loading.value = true
  try {
    Object.assign(overview, await towerApi.overview())
    Object.assign(board, await objectiveApi.scoreboard())
    Object.assign(balance, await balanceApi.overview())
  } finally {
    loading.value = false
  }
}

load()
</script>

<style scoped>
.health-list {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
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

@media (max-width: 1200px) {
  .health-list {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
</style>
