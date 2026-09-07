<template>
  <div class="page">
    <div class="page-title"><div><h2>控制塔总览</h2><span class="muted">实时掌握订单、库存、运输与成本健康度</span></div><el-button type="primary" :loading="loading" @click="load"><el-icon><Refresh /></el-icon>刷新数据</el-button></div>
    <div class="stats"><div v-for="item in stats" :key="item.label" class="stat"><div class="label">{{ item.label }}</div><div class="value">{{ item.value }}</div></div></div>
    <div class="grid-2"><div class="panel"><h3>OTW 履约漏斗</h3><Chart :option="funnelOption" /></div><div class="panel"><h3>30 天成本趋势</h3><Chart :option="costOption" /></div></div>
    <div class="grid-2"><div class="panel"><h3>仓库负载</h3><el-table :data="overview.warehouseLoad || []" stripe><el-table-column prop="warehouseCode" label="仓库" /><el-table-column prop="pendingOrders" label="待处理订单" /><el-table-column prop="inventoryQty" label="库存量" /><el-table-column prop="lowStockSkus" label="低库存 SKU" /></el-table></div><div class="panel"><h3>重点预警</h3><el-table :data="overview.alertsTop || []" stripe><el-table-column prop="severity" label="等级" width="90" /><el-table-column prop="title" label="预警" /><el-table-column prop="warehouseCode" label="仓库" /></el-table></div></div>
    <div class="panel"><h3>系统健康</h3><div class="health-list"><div v-for="system in overview.systems || []" :key="system.code" class="health"><i :class="{ ok: system.lastHealthOk }"></i><b>{{ system.name }}</b><span>{{ system.mode }} · {{ system.lastHealthOk ? '正常' : '待检查' }}</span></div></div></div>
  </div>
</template>
<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { towerApi } from '../api'
import Chart from '../components/Chart.vue'
const overview = reactive({ kpi: {}, funnel: {}, costTrend: {}, warehouseLoad: [], alertsTop: [], systems: [] }); const loading = ref(false)
const stats = computed(() => [{ label: '今日订单', value: overview.kpi.todayOrders || 0 }, { label: '待处理订单', value: overview.kpi.pendingOrders || 0 }, { label: '在途运单', value: overview.kpi.inTransit || 0 }, { label: '延迟运单', value: overview.kpi.delayedShipments || 0 }, { label: '低库存 SKU', value: overview.kpi.lowStockSkus || 0 }, { label: '30天成本', value: overview.kpi.totalCost30d || 0 }])
const funnelOption = computed(() => ({ tooltip: {}, legend: { bottom: 0 }, xAxis: { type: 'category', data: ['OMS', 'WMS', 'TMS'] }, yAxis: { type: 'value' }, series: Object.entries(overview.funnel || {}).map(([name, values]) => ({ name, type: 'bar', data: Object.values(values || {}) })) }))
const costOption = computed(() => ({ tooltip: { trigger: 'axis' }, legend: { bottom: 0 }, xAxis: { type: 'category', data: Object.keys(overview.costTrend || {}) }, yAxis: { type: 'value' }, series: [{ name: '成本', type: 'line', smooth: true, areaStyle: {}, data: Object.values(overview.costTrend || {}).map((item) => typeof item === 'object' ? Object.values(item).reduce((a, b) => a + Number(b || 0), 0) : item) }] }))
async function load() { loading.value = true; try { Object.assign(overview, await towerApi.overview()) } finally { loading.value = false } }
onMounted(load)
</script>
<style scoped>.health-list { display:flex; gap:30px; flex-wrap:wrap; }.health { display:flex; gap:8px; align-items:center; }.health i { width:10px; height:10px; border-radius:50%; background:#f56c6c; }.health i.ok { background:#67c23a; }.health span { color:#909399; font-size:13px; }</style>
