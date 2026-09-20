<template>
  <div class="page">
    <div class="page-title">
      <div>
        <h2>补货建议</h2>
        <p class="subtitle">
          按再订货点（提前期 + 保障天数）补货，不按 14 天预测全量下单 · 当前保障
          {{ policy.safetyDays }} 天 / 提前期 {{ policy.replenishLeadDays }} 天（跟随沙盘推荐）
        </p>
      </div>
      <el-button @click="load">刷新</el-button>
    </div>
    <div class="panel toolbar">
      <el-input v-model="filters.sku" clearable placeholder="SKU" /><el-select
        v-model="filters.warehouseCode"
        clearable
        placeholder="仓库"
        ><el-option label="WH-SH" value="WH-SH" /><el-option
          label="WH-BJ"
          value="WH-BJ" /><el-option label="WH-GZ" value="WH-GZ" /></el-select
      ><el-button type="primary" @click="search">查询</el-button
      ><el-button v-if="canWrite() && selected.length" type="primary" @click="batchAction"
        >批量转指令</el-button
      >
    </div>
    <div class="panel">
      <el-table v-loading="loading" :data="rows" stripe @selection-change="selected = $event"
        ><el-table-column type="selection" width="50" /><el-table-column
          prop="sku"
          label="SKU" /><el-table-column prop="warehouseCode" label="仓库" /><el-table-column
          prop="available"
          label="可用"
          align="right" /><el-table-column
          prop="inTransit"
          label="采购在途"
          align="right" /><el-table-column
          prop="suggestQty"
          label="建议数量"
          align="right" /><el-table-column
          prop="coverDays"
          label="再订货点天数"
          align="right" /><el-table-column
          prop="stockoutDate"
          label="预计缺货日期" /><el-table-column
          prop="orderByDate"
          label="最晚下单" /><el-table-column
          prop="serviceDays"
          label="保障天数"
          align="right" /><el-table-column
          prop="replenishLeadDays"
          label="提前期"
          align="right" /><el-table-column label="操作"
          ><template #default="{ row }"
            ><el-button v-if="canWrite()" link type="primary" @click="toAction(row)"
              >转指令</el-button
            ></template
          ></el-table-column
        ><template #empty><el-empty description="暂无补货建议" /></template
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
    <el-dialog v-model="visible" title="选择补货指令类型" width="420px"
      ><el-form label-width="110px"
        ><el-form-item label="指令类型"
          ><el-select v-model="actionType"
            ><el-option label="SRM采购建议" value="SRM_PURCHASE_SUGGEST" /><el-option
              label="WMS仓内补货"
              value="WMS_REPLENISH" /></el-select></el-form-item></el-form
      ><template #footer
        ><el-button @click="visible = false">取消</el-button
        ><el-button type="primary" @click="submitAction">确定</el-button></template
      ></el-dialog
    >
  </div>
</template>
<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { forecastApi, sandboxApi } from '../api'
import { canWrite } from '../auth'
const filters = reactive({ sku: '', warehouseCode: '' })
const policy = reactive({ safetyDays: 3, replenishLeadDays: 3 })
const pager = reactive({ current: 1, size: 20, total: 0 })
const rows = ref([])
const selected = ref([])
const loading = ref(false)
const visible = ref(false)
const actionType = ref('SRM_PURCHASE_SUGGEST')
const currentRows = ref([])
async function load() {
  loading.value = true
  try {
    const data = await forecastApi.replenish({ ...filters })
    const all = Array.isArray(data) ? data : data?.records || []
    pager.total = all.length
    const start = (pager.current - 1) * pager.size
    rows.value = all.slice(start, start + pager.size)
    const current = await sandboxApi.policy()
    if (current) {
      policy.safetyDays = current.safetyDays ?? policy.safetyDays
      policy.replenishLeadDays = current.replenishLeadDays ?? policy.replenishLeadDays
    }
  } finally {
    loading.value = false
  }
}
function search() {
  pager.current = 1
  load()
}
function toAction(row) {
  currentRows.value = [row]
  visible.value = true
}
function batchAction() {
  currentRows.value = selected.value
  visible.value = true
}
async function submitAction() {
  const payload = currentRows.value.map((row) => ({ ...row, type: actionType.value }))
  const results = await forecastApi.toAction(payload)
  visible.value = false
  const failed = (Array.isArray(results) ? results : []).filter((item) => item.status === 'FAILED')
  if (failed.length) {
    ElMessage.error(failed[0].result || '部分指令执行失败')
  } else {
    ElMessage.success('已下发补货指令')
  }
}
load()
</script>
