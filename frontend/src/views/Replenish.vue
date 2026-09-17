<template>
  <div class="page">
    <div class="page-title">
      <div>
        <h2>补货建议</h2>
        <p class="subtitle">将预测缺口同时转成 SRM 采购、SAP 申请、OA 审批和仓内补货</p>
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
      ><el-button type="primary" @click="load">查询</el-button
      ><el-button v-if="canWrite() && selected.length" type="primary" @click="batchAction"
        >批量转指令</el-button
      >
    </div>
    <div class="panel">
      <el-table v-loading="loading" :data="rows" stripe @selection-change="selected = $event"
        ><el-table-column type="selection" width="50" /><el-table-column
          prop="sku"
          label="SKU" /><el-table-column prop="warehouseCode" label="仓库" /><el-table-column
          prop="stockoutDate"
          label="预计缺货日期" /><el-table-column
          prop="suggestQty"
          label="建议数量"
          align="right" /><el-table-column
          prop="available"
          label="可用库存"
          align="right" /><el-table-column
          prop="safety"
          label="安全库存"
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
            ><el-option label="跨系统协同补货（SRM+SAP+OA+WMS）" value="COORDINATE_REPLENISH" /><el-option
              label="SRM采购建议"
              value="SRM_PURCHASE_SUGGEST" /><el-option
              label="SAP创建采购申请"
              value="SAP_CREATE_PR" /><el-option
              label="OA发起审批"
              value="OA_START_WORKFLOW" /><el-option
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
import { forecastApi } from '../api'
import { canWrite } from '../auth'
import { pageResult } from '../utils/format'
const filters = reactive({ sku: '', warehouseCode: '' })
const pager = reactive({ current: 1, size: 20, total: 0 })
const rows = ref([])
const selected = ref([])
const loading = ref(false)
const visible = ref(false)
const actionType = ref('COORDINATE_REPLENISH')
const currentRows = ref([])
async function load() {
  loading.value = true
  try {
    const page = pageResult(await forecastApi.replenish({ ...filters, ...pager }))
    rows.value = page.records
    pager.total = page.total
  } finally {
    loading.value = false
  }
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
  for (const row of currentRows.value)
    await forecastApi.toAction({ ...row, type: actionType.value })
  visible.value = false
  ElMessage.success('已生成跨系统协同指令')
}
load()
</script>
