<template>
  <div class="page">
    <div class="page-title">
      <div>
        <h2>沙盘模拟</h2>
        <p class="subtitle">评估需求、仓配、承运和成本策略变化</p>
      </div>
      <el-button v-if="canWrite()" type="primary" @click="openCreate">创建场景</el-button>
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
          ><el-table-column prop="createdAt" label="创建时间" min-width="160"
            ><template #default="{ row }">{{
              formatDate(row.createdAt)
            }}</template></el-table-column
          ><el-table-column label="服务水平" align="right"
            ><template #default="{ row }">{{
              percent(row.serviceLevel)
            }}</template></el-table-column
          ><el-table-column label="操作" width="180"
            ><template #default="{ row }"
              ><el-button v-if="canWrite()" link @click.stop="run(row)">运行</el-button
              ><el-button v-if="canWrite()" link type="primary" @click.stop="apply(row)"
                >应用到OTW</el-button
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
        ><el-form-item label="补货提前期"
          ><el-input-number v-model="form.params.replenishLeadDays" :min="0" /></el-form-item
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
import { actionStatusLabels, labelOf, scenarioStatusLabels, tagTypes } from '../utils/labels'
const channels = ['TMALL', 'JD', 'DOUYIN', 'OFFLINE', 'API']
const carriers = ['SF', 'JDL', 'ZTO', 'SELF']
const rows = ref([])
const selected = ref(null)
const loading = ref(false)
const saving = ref(false)
const visible = ref(false)
const actionDialog = ref(false)
const pendingActions = ref([])
const formRef = ref()
const pager = reactive({ current: 1, size: 10, total: 0 })
const form = reactive({
  name: '新场景',
  params: {
    demandMultiplier: 1,
    allocationStrategy: 'NEAREST',
    replenishLeadDays: 3,
    channelDemandMultiplier: {},
    carrierMix: {},
    carrierRate: {}
  }
})
const rules = { name: [{ required: true, message: '请输入场景名称', trigger: 'blur' }] }
const channelRows = computed(() => channels.map((key) => ({ key })))
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
    const page = pageResult(await sandboxApi.page({ ...pager }))
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
async function apply(row) {
  const result = await sandboxApi.apply(row.id)
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
load()
</script>
