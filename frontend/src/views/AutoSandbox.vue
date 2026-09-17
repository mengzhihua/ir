<template>
  <div class="page">
    <div class="page-title">
      <div>
        <h2>系统自动沙盘</h2>
        <p class="subtitle">按仓配、承运和供应策略网格批量推演，落地时会同时驱动 SRM / SAP / OA / WMS</p>
      </div>
      <el-button v-if="canWrite()" type="primary" :loading="running" @click="run">立即推演</el-button>
    </div>
    <div class="stats" v-if="recommended">
      <div class="stat">
        <div class="label">推荐方案</div>
        <div class="value">{{ recommended.name }}</div>
      </div>
      <div class="stat">
        <div class="label">综合分</div>
        <div class="value">{{ formatNumber(recommended.balanceScore, 4) }}</div>
      </div>
      <div class="stat">
        <div class="label">成本分</div>
        <div class="value">{{ formatNumber(recommended.costScore, 4) }}</div>
      </div>
      <div class="stat">
        <div class="label">效率分</div>
        <div class="value">{{ formatNumber(recommended.efficiencyScore, 4) }}</div>
      </div>
    </div>
    <div class="grid-2">
      <div class="panel">
        <div class="panel-title">
          <h3>本轮候选 {{ latest.runNo || '' }}</h3>
          <el-button @click="load">刷新</el-button>
        </div>
        <el-table v-loading="loading" :data="rows" stripe @row-click="select">
          <el-table-column prop="name" label="场景" min-width="180">
            <template #default="{ row }">
              {{ row.name }}
              <el-tag v-if="row.recommended" type="success" size="small" class="rec-tag">推荐</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="总成本" align="right" width="120">
            <template #default="{ row }">{{ formatMoney(row.totalCost) }}</template>
          </el-table-column>
          <el-table-column label="服务水平" align="right" width="100">
            <template #default="{ row }">{{ percent(row.serviceLevel) }}</template>
          </el-table-column>
          <el-table-column label="成本分" align="right" width="90">
            <template #default="{ row }">{{ formatNumber(row.costScore, 4) }}</template>
          </el-table-column>
          <el-table-column label="效率分" align="right" width="90">
            <template #default="{ row }">{{ formatNumber(row.efficiencyScore, 4) }}</template>
          </el-table-column>
          <el-table-column label="综合分" align="right" width="90">
            <template #default="{ row }">{{ formatNumber(row.balanceScore, 4) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="160">
            <template #default="{ row }">
              <el-button v-if="canWrite()" link type="primary" @click.stop="apply(row, false)"
                >生成指令</el-button
              >
              <el-button v-if="canWrite()" link type="warning" @click.stop="apply(row, true)"
                >立即执行</el-button
              >
            </template>
          </el-table-column>
          <template #empty><el-empty description="尚未自动推演，点击右上角立即推演" /></template>
        </el-table>
      </div>
      <div class="panel">
        <div class="panel-title">
          <h3>{{ selected?.name || '选择方案查看结果' }}</h3>
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
        </div>
        <div v-if="selected" class="grid-2">
          <Chart :option="typeOption" /><Chart :option="dailyOption" /><Chart :option="warehouseOption" /><Chart
            :option="carrierOption"
          />
        </div>
        <el-empty v-else description="选择一个自动方案查看结果" />
      </div>
    </div>
    <el-dialog v-model="actionDialog" title="已生成协同指令" width="680px">
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
      </el-table>
    </el-dialog>
  </div>
</template>
<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { sandboxApi } from '../api'
import { canWrite } from '../auth'
import Chart from '../components/Chart.vue'
import { formatMoney, formatNumber, parseJson, percent } from '../utils/format'
import { actionStatusLabels, labelOf, tagTypes } from '../utils/labels'

const loading = ref(false)
const running = ref(false)
const latest = ref({})
const rows = ref([])
const recommended = ref(null)
const selected = ref(null)
const actionDialog = ref(false)
const pendingActions = ref([])

const typeOption = computed(() => ({
  tooltip: {},
  series: [
    {
      type: 'pie',
      radius: '60%',
      data: Object.entries(selected.value?.costByType || {}).map(([name, value]) => ({ name, value }))
    }
  ]
}))
const dailyOption = computed(() => ({
  tooltip: { trigger: 'axis' },
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

async function load() {
  loading.value = true
  try {
    const data = await sandboxApi.autoLatest()
    latest.value = data || {}
    rows.value = data?.scenarios || []
    recommended.value = data?.recommended || null
    if (recommended.value) {
      await select(recommended.value)
    }
  } finally {
    loading.value = false
  }
}

async function run() {
  running.value = true
  try {
    const data = await sandboxApi.autoRun()
    latest.value = data || {}
    rows.value = data?.scenarios || []
    recommended.value = data?.recommended || null
    ElMessage.success('自动沙盘推演完成，已按成本与效率综合分选出推荐方案')
    if (recommended.value) {
      await select(recommended.value)
    }
  } finally {
    running.value = false
  }
}

async function select(row) {
  const value = await sandboxApi.get(row.id)
  const result = parseJson(value.result || value.resultJson, {})
  selected.value = { ...value, ...result }
}

async function apply(row, execute) {
  const result = await sandboxApi.apply(row.id, execute)
  pendingActions.value = result?.records || result || []
  actionDialog.value = true
  ElMessage.success(execute ? '已向 OMS/TMS/SRM 下发指令' : '已生成待执行指令')
}

load()
</script>
<style scoped>
.rec-tag {
  margin-left: 6px;
}
</style>
