<template>
  <div class="page">
    <div class="page-title">
      <div>
        <h2>需求预测</h2>
        <p class="subtitle">按SKU和仓库运行预测，辅助补货决策</p>
      </div>
      <el-button @click="loadHistory">刷新历史</el-button>
    </div>
    <div class="panel">
      <el-form :model="form" inline>
        <el-form-item label="SKU"
          ><el-input v-model="form.sku" placeholder="SKU001"
        /></el-form-item>
        <el-form-item label="仓库"
          ><el-select v-model="form.warehouseCode" clearable
            ><el-option label="WH-SH" value="WH-SH" /><el-option
              label="WH-BJ"
              value="WH-BJ" /><el-option label="WH-GZ" value="WH-GZ" /></el-select
        ></el-form-item>
        <el-form-item label="方法"
          ><el-select v-model="form.method"
            ><el-option label="自动选择" value="AUTO" /><el-option
              label="移动平均"
              value="MOVING_AVERAGE" /><el-option
              label="季节朴素"
              value="SEASONAL_NAIVE" /></el-select
        ></el-form-item>
        <el-form-item label="预测天数"
          ><el-input-number v-model="form.horizon" :min="1" :max="90"
        /></el-form-item>
        <el-button type="primary" :loading="running" @click="run">运行预测</el-button>
      </el-form>
    </div>
    <div v-if="result" class="panel">
      <div class="panel-title">
        <h3>预测结果</h3>
        <el-tag type="success">MAPE {{ formatNumber(result.mape, 2) }}%</el-tag>
      </div>
      <Chart :option="forecastOption" />
      <el-table :data="backtestRows" size="small"
        ><el-table-column prop="method" label="方法" /><el-table-column
          prop="mape"
          label="MAPE"
          align="right" /><template #empty><el-empty description="暂无回测明细" /></template
      ></el-table>
    </div>
    <div class="panel">
      <div class="panel-title"><h3>预测运行历史</h3></div>
      <el-table v-loading="loading" :data="history" stripe
        ><el-table-column prop="runNo" label="运行号" /><el-table-column
          prop="sku"
          label="SKU" /><el-table-column prop="warehouseCode" label="仓库" /><el-table-column
          prop="method"
          label="方法" /><el-table-column
          prop="horizon"
          label="天数"
          align="right" /><el-table-column prop="createdAt" label="运行时间"
          ><template #default="{ row }">{{ formatDate(row.createdAt) }}</template></el-table-column
        ><el-table-column label="操作"
          ><template #default="{ row }"
            ><el-button link type="primary" @click="reopen(row)">查看</el-button></template
          ></el-table-column
        ><template #empty><el-empty description="暂无预测历史" /></template
      ></el-table>
      <div class="pagination">
        <el-pagination
          v-model:current-page="pager.current"
          v-model:page-size="pager.size"
          :total="pager.total"
          layout="total, prev, pager, next"
          @current-change="loadHistory"
        />
      </div>
    </div>
  </div>
</template>
<script setup>
import { computed, reactive, ref } from 'vue'
import { forecastApi } from '../api'
import Chart from '../components/Chart.vue'
import { formatDate, formatNumber, pageResult, parseJson } from '../utils/format'
import { labelOf } from '../utils/labels'
const form = reactive({ sku: 'SKU001', warehouseCode: 'WH-SH', method: 'AUTO', horizon: 14 })
const result = ref(null)
const history = ref([])
const running = ref(false)
const loading = ref(false)
const pager = reactive({ current: 1, size: 20, total: 0 })
const forecastOption = computed(() => {
  const historyValues = result.value?.history || []
  const forecastRows = result.value?.forecast || []
  const historyLabels = historyValues.map((_, index) => {
    const date = new Date()
    date.setDate(date.getDate() - historyValues.length + index + 1)
    return date.toISOString().slice(0, 10)
  })
  const labels = [...historyLabels, ...forecastRows.map((item) => item.date)]
  const actual = [...historyValues, ...forecastRows.map(() => null)]
  const predicted = [...historyValues.map(() => null), ...forecastRows.map((item) => item.qty)]
  const lower = [...historyValues.map(() => null), ...forecastRows.map((item) => item.lower)]
  const band = [
    ...historyValues.map(() => null),
    ...forecastRows.map((item) => Number(item.upper || 0) - Number(item.lower || 0))
  ]
  return {
    tooltip: { trigger: 'axis' },
    legend: { data: ['历史', '预测', '置信区间'] },
    xAxis: { type: 'category', data: labels },
    yAxis: { type: 'value' },
    series: [
      { name: '历史', type: 'line', data: actual },
      { name: '预测', type: 'line', data: predicted, lineStyle: { type: 'dashed' } },
      {
        name: '下界',
        type: 'line',
        stack: 'band',
        data: lower,
        lineStyle: { opacity: 0 },
        areaStyle: { opacity: 0 }
      },
      {
        name: '置信区间',
        type: 'line',
        stack: 'band',
        data: band,
        lineStyle: { opacity: 0 },
        areaStyle: { opacity: 0.18 }
      }
    ]
  }
})
const backtestRows = computed(() => {
  const value = result.value?.backtest
  if (Array.isArray(value) && value.length === 2 && typeof value[0] === 'string') {
    return [{ method: labelOf(value[0], { AUTO: '自动选择' }), mape: value[1] }]
  }
  return Array.isArray(value) ? value : []
})
async function run() {
  running.value = true
  try {
    result.value = await forecastApi.run(form)
    loadHistory()
  } finally {
    running.value = false
  }
}
async function loadHistory() {
  loading.value = true
  try {
    const page = pageResult(await forecastApi.page({ ...pager }))
    history.value = page.records
    pager.total = page.total
  } finally {
    loading.value = false
  }
}
async function reopen(row) {
  result.value =
    row.result || row.resultJson
      ? parseJson(row.result || row.resultJson)
      : await forecastApi.run({ ...row, horizon: row.horizon || 14 })
}
loadHistory()
</script>
