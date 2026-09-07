<template>
  <div class="page">
    <div class="page-title"><div><h2>场景对比</h2><p class="subtitle">比较多套供应链策略相对基准的收益</p></div><el-button type="primary" @click="compare">开始对比</el-button></div>
    <div class="panel toolbar"><el-select v-model="selected" multiple collapse-tags placeholder="选择场景" style="width: 480px"><el-option v-for="row in scenarios" :key="row.id" :label="row.name" :value="row.id" /></el-select></div>
    <div v-if="result" class="panel"><el-table :data="result.metrics || []" border><el-table-column prop="metric" label="指标" fixed /><el-table-column v-for="scenario in result.scenarios || []" :key="scenario.id" :label="scenario.name" align="right"><template #default="{ row }">{{ row.values?.[scenario.id] ?? '-' }}</template></el-table-column><el-table-column label="差值"><template #default="{ row }">{{ row.delta ?? '-' }}</template></el-table-column><el-table-column label="节省率"><template #default="{ row }">{{ percent(row.savingPct) }}</template></el-table-column></el-table><Chart :option="chartOption" /></div><el-empty v-else description="请选择场景并开始对比" />
  </div>
</template>
<script setup>
import { computed, ref } from 'vue'
import { sandboxApi } from '../api'
import Chart from '../components/Chart.vue'
import { percent } from '../utils/format'
const scenarios = ref([])
const selected = ref([])
const result = ref(null)
const chartOption = computed(() => ({ tooltip: { trigger: 'axis' }, legend: {}, xAxis: { type: 'category', data: (result.value?.scenarios || []).map((item) => item.name) }, yAxis: { type: 'value' }, series: (result.value?.metrics || []).slice(0, 4).map((metric) => ({ name: metric.metric, type: 'bar', data: (result.value?.scenarios || []).map((item) => metric.values?.[item.id] || 0) })) }))
async function load() { const page = await sandboxApi.page({ current: 1, size: 100 }); scenarios.value = page.records || [] }
async function compare() { if (selected.value.length < 2) return; result.value = await sandboxApi.compare(selected.value) }
load()
</script>
