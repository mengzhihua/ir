<template><div class="page"><div class="page-title"><h2>需求预测</h2></div><div class="panel"><div class="toolbar"><el-input v-model="form.sku" placeholder="SKU" /><el-input v-model="form.warehouseCode" placeholder="仓库" /><el-select v-model="form.method" style="width:150px"><el-option v-for="m in ['AUTO','MOVING_AVERAGE','SES','HOLT','SEASONAL_NAIVE']" :key="m" :label="m" :value="m" /></el-select><el-input-number v-model="form.horizon" :min="1" :max="90" /><el-button type="primary" @click="run">运行预测</el-button></div><Chart :option="option" /></div><div class="panel"><h3>运行历史</h3><el-table :data="history" stripe><el-table-column prop="runNo" label="运行号" /><el-table-column prop="sku" label="SKU" /><el-table-column prop="method" label="方法" /><el-table-column prop="horizon" label="预测天数" /><el-table-column prop="mape" label="MAPE" /><el-table-column prop="createdAt" label="时间" /></el-table></div></div></template>
<script setup>
import { reactive, ref, computed } from 'vue'; import { forecastApi } from '../api'; import Chart from '../components/Chart.vue'
const form = reactive({ sku: 'SKU001', warehouseCode: 'WH-SH', method: 'AUTO', horizon: 14 })
const result = ref({})
const history = ref([])
const option = computed(() => ({
  tooltip: { trigger: 'axis' },
  legend: { bottom: 0 },
  xAxis: {
    type: 'category',
    data: [
      ...(result.value.history || []).map((_, index) => index + 1),
      ...(result.value.forecast || []).map((_, index) => `+${index + 1}`)
    ]
  },
  yAxis: { type: 'value' },
  series: [
    { name: '历史', type: 'line', data: result.value.history || [] },
    {
      name: '预测',
      type: 'line',
      data: [
        ...(result.value.history || []).map(() => null),
        ...(result.value.forecast || []).map((point) => point.qty || point)
      ]
    }
  ]
}))
async function run() {
  result.value = await forecastApi.run(form)
  history.value = await forecastApi.page()
}
async function load() { history.value = await forecastApi.page() }
load()
</script>
