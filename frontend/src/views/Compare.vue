<template>
  <div class="page">
    <div class="page-title">
      <div>
        <h2>场景对比</h2>
        <p class="subtitle">比较多套供应链策略相对基准的收益</p>
      </div>
      <el-button type="primary" @click="compare">开始对比</el-button>
    </div>
    <div class="panel toolbar">
      <el-select
        v-model="selected"
        multiple
        collapse-tags
        placeholder="选择场景"
        style="width: 480px"
        ><el-option v-for="row in scenarios" :key="row.id" :label="row.name" :value="row.id"
      /></el-select>
    </div>
    <div v-if="result" class="panel">
      <el-table :data="metrics" border
        ><el-table-column prop="metric" label="指标" fixed /><el-table-column
          v-for="scenario in result.scenarios || []"
          :key="scenario.id"
          :label="scenario.name"
          align="right"
          ><template #default="{ row }">{{
            formatMetric(row.metric, row.values?.[scenario.id])
          }}</template></el-table-column
        ><el-table-column label="差值"
          ><template #default="{ row }">{{ row.delta ?? '-' }}</template></el-table-column
        ><el-table-column label="节省率"
          ><template #default="{ row }">{{ percent(row.savingPct) }}</template></el-table-column
        ></el-table
      ><Chart :option="chartOption" />
    </div>
    <el-empty v-else description="请选择场景并开始对比" />
  </div>
</template>
<script setup>
import { computed, ref } from 'vue'
import { sandboxApi } from '../api'
import Chart from '../components/Chart.vue'
import { formatMoney, percent, parseJson } from '../utils/format'
const scenarios = ref([])
const selected = ref([])
const result = ref(null)
const metrics = computed(() => {
  const rows = result.value?.scenarios || []
  const definitions = [
    ['总成本', 'totalCost'],
    ['服务水平', 'serviceLevel'],
    ['缺货件数', 'stockoutUnits'],
    ['平均时效', 'avgLeadDays']
  ]
  return definitions.map(([metric, key]) => ({
    metric,
    values: Object.fromEntries(rows.map((row) => [row.id, row[key] ?? 0])),
    delta: rows.length ? (rows[rows.length - 1].delta?.[key] ?? '-') : '-',
    savingPct: rows.length ? rows[rows.length - 1].savingPct : 0
  }))
})
function formatMetric(metric, value) {
  return metric === '总成本' ? formatMoney(value) : Number(value || 0).toFixed(2)
}
const chartOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  legend: {},
  xAxis: { type: 'category', data: (result.value?.scenarios || []).map((item) => item.name) },
  yAxis: { type: 'value' },
  series: metrics.value.slice(0, 4).map((metric) => ({
    name: metric.metric,
    type: 'bar',
    data: (result.value?.scenarios || []).map((item) => metric.values?.[item.id] || 0)
  }))
}))
async function load() {
  const page = await sandboxApi.page({ current: 1, size: 100 })
  scenarios.value = page.records || []
}
async function compare() {
  if (selected.value.length < 2) return
  const rows = await sandboxApi.compare(selected.value)
  result.value = {
    scenarios: rows.map((row) => {
      const scenario = row.scenario || {}
      return {
        ...scenario,
        ...parseJson(scenario.result || scenario.resultJson, {}),
        savingPct: row.savingPct,
        delta: row.delta
      }
    })
  }
}
load()
</script>
