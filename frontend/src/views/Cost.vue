<template>
  <div class="page">
    <div class="page-title">
      <div>
        <h2>成本分析</h2>
        <p class="subtitle">按类型、仓库、承运商分析供应链成本</p>
      </div>
      <el-button @click="load">刷新</el-button>
    </div>
    <div class="stats">
      <div class="stat">
        <div class="label">总成本</div>
        <div class="value">{{ formatMoney(summary.total) }}</div>
      </div>
      <div class="stat">
        <div class="label">单均成本</div>
        <div class="value">{{ formatMoney(summary.costPerOrder) }}</div>
      </div>
      <div class="stat">
        <div class="label">节省</div>
        <div class="value success">{{ formatMoney(saving.total) }}</div>
      </div>
    </div>
    <div class="grid-2">
      <div class="panel">
        <h3>成本类型</h3>
        <Chart :option="typeOption" />
      </div>
      <div class="panel">
        <h3>成本趋势</h3>
        <Chart :option="trendOption" />
      </div>
    </div>
    <div class="grid-2">
      <div class="panel">
        <h3>仓库成本</h3>
        <Chart :option="warehouseOption" />
      </div>
      <div class="panel">
        <h3>承运商成本</h3>
        <Chart :option="carrierOption" />
      </div>
    </div>
    <div class="panel">
      <div class="panel-title">
        <h3>成本目标</h3>
        <el-button v-if="canWrite()" type="primary" @click="openTarget()">新增目标</el-button>
      </div>
      <el-table :data="targets">
        <el-table-column prop="month" label="月份" />
        <el-table-column prop="targetAmount" label="目标金额" align="right">
          <template #default="{ row }">{{ formatMoney(row.targetAmount) }}</template>
        </el-table-column>
        <el-table-column label="操作"
          ><template #default="{ row }"
            ><el-button v-if="canWrite()" link type="danger" @click="removeTarget(row)"
              >删除</el-button
            ></template
          ></el-table-column
        ></el-table
      >
    </div>
    <div class="panel">
      <div class="panel-title"><h3>成本记录</h3></div>
      <div class="toolbar">
        <el-input v-model="filters.orderNo" clearable placeholder="订单号" /><el-select
          v-model="filters.costType"
          clearable
          placeholder="成本类型"
          ><el-option label="运费" value="FREIGHT" /><el-option
            label="仓储"
            value="STORAGE" /><el-option label="操作" value="HANDLING" /><el-option
            label="包装"
            value="PACKAGING" /></el-select
        ><el-button type="primary" @click="search">查询</el-button>
      </div>
      <el-table v-loading="loading" :data="records" stripe
        ><el-table-column prop="bizDate" label="日期" /><el-table-column
          prop="orderNo"
          label="订单号" /><el-table-column prop="costType" label="类型" /><el-table-column
          prop="warehouseCode"
          label="仓库" /><el-table-column prop="carrierCode" label="承运商" /><el-table-column
          label="金额"
          align="right"
          ><template #default="{ row }">{{ formatMoney(row.amount) }}</template></el-table-column
        ><template #empty><el-empty description="暂无成本记录" /></template
      ></el-table>
      <div class="pagination">
        <el-pagination
          v-model:current-page="pager.current"
          v-model:page-size="pager.size"
          :total="pager.total"
          layout="total, sizes, prev, pager, next"
          @current-change="loadRecords"
          @size-change="loadRecords"
        />
      </div>
    </div>
    <el-dialog v-model="targetVisible" title="新增成本目标"
      ><el-form label-width="100px"
        ><el-form-item label="目标金额"
          ><el-input-number v-model="target.targetAmount" :min="0" /></el-form-item
        ><el-form-item label="月份"
          ><el-input v-model="target.month" placeholder="2026-09" /></el-form-item></el-form
      ><template #footer
        ><el-button @click="targetVisible = false">取消</el-button
        ><el-button type="primary" @click="saveTarget">保存</el-button></template
      ></el-dialog
    >
  </div>
</template>
<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { costApi } from '../api'
import { canWrite } from '../auth'
import Chart from '../components/Chart.vue'
import { formatMoney, pageResult } from '../utils/format'
const summary = reactive({})
const saving = reactive({})
const targets = ref([])
const records = ref([])
const loading = ref(false)
const targetVisible = ref(false)
const filters = reactive({ orderNo: '', costType: '' })
const pager = reactive({ current: 1, size: 20, total: 0 })
const target = reactive({ targetAmount: 0, month: '' })
const typeOption = computed(() => ({
  tooltip: {},
  series: [
    {
      type: 'pie',
      radius: '60%',
      data: Object.entries(summary.byType || {}).map(([name, value]) => ({ name, value }))
    }
  ]
}))
const trendOption = computed(() => {
  const rows = Object.entries(summary.trend || {}).map(([date, amount]) => ({
    date,
    amount
  }))
  return {
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: rows.map((row) => row.date) },
    yAxis: { type: 'value' },
    series: [{ name: '成本', type: 'line', areaStyle: {}, data: rows.map((row) => row.amount) }]
  }
})
const warehouseOption = computed(() => ({
  xAxis: { type: 'category', data: Object.keys(summary.byWarehouse || {}) },
  yAxis: { type: 'value' },
  series: [{ type: 'bar', data: Object.values(summary.byWarehouse || {}) }]
}))
const carrierOption = computed(() => ({
  xAxis: { type: 'category', data: Object.keys(summary.byCarrier || {}) },
  yAxis: { type: 'value' },
  series: [{ type: 'bar', data: Object.values(summary.byCarrier || {}) }]
}))
async function loadRecords() {
  loading.value = true
  try {
    const page = pageResult(await costApi.page({ ...filters, ...pager }))
    records.value = page.records
    pager.total = page.total
  } finally {
    loading.value = false
  }
}
async function load() {
  Object.assign(summary, await costApi.summary())
  Object.assign(saving, await costApi.saving())
  targets.value = await costApi.targets()
  loadRecords()
}
function search() {
  pager.current = 1
  loadRecords()
}
function openTarget() {
  Object.assign(target, { warehouseCode: '', costType: 'FREIGHT', targetAmount: 0, month: '' })
  targetVisible.value = true
}
async function saveTarget() {
  await costApi.saveTarget(target)
  targetVisible.value = false
  ElMessage.success('目标已保存')
  load()
}
async function removeTarget(row) {
  await ElMessageBox.confirm('确认删除这个成本目标吗？', '确认')
  await costApi.deleteTarget(row.id)
  ElMessage.success('已删除')
  load()
}
load()
</script>
