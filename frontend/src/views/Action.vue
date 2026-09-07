<template>
  <div class="page">
    <div class="page-title">
      <div>
        <h2>联动指令</h2>
        <p class="subtitle">统一下发跨系统动作并跟踪执行结果</p>
      </div>
      <el-button v-if="canWrite()" type="primary" @click="openCreate">新建指令</el-button>
    </div>
    <div class="panel toolbar">
      <el-select v-model="filters.type" clearable placeholder="指令类型" @change="search"
        ><el-option
          v-for="item in types"
          :key="item.type"
          :label="item.label || item.type"
          :value="item.type" /></el-select
      ><el-select v-model="filters.status" clearable placeholder="状态" @change="search"
        ><el-option label="待执行" value="PENDING" /><el-option
          label="成功"
          value="SUCCESS" /><el-option label="失败" value="FAILED" /></el-select
      ><el-input
        v-model="filters.targetKey"
        clearable
        placeholder="目标对象"
        style="width: 180px"
        @keyup.enter="search"
      /><el-button type="primary" @click="search">查询</el-button>
    </div>
    <div class="panel">
      <el-table v-loading="loading" :data="rows" stripe
        ><el-table-column prop="actionNo" label="指令号" width="180" /><el-table-column
          prop="type"
          label="类型"
          width="180" /><el-table-column
          prop="targetKey"
          label="目标对象"
          width="180" /><el-table-column prop="status" label="状态" width="100"
          ><template #default="{ row }"
            ><el-tag :type="tagTypes.actionStatus[row.status]">{{
              labelOf(row.status, actionStatusLabels)
            }}</el-tag></template
          ></el-table-column
        ><el-table-column prop="result" label="执行结果" min-width="240" /><el-table-column
          prop="createdAt"
          label="创建时间"
          width="165"
          ><template #default="{ row }">{{ formatDate(row.createdAt) }}</template></el-table-column
        ><el-table-column label="操作" width="100"
          ><template #default="{ row }"
            ><el-button
              v-if="canWrite() && row.status === 'FAILED'"
              link
              type="primary"
              @click="retry(row)"
              >重试</el-button
            ></template
          ></el-table-column
        ><template #empty><el-empty description="暂无指令" /></template
      ></el-table>
      <div class="pagination">
        <el-pagination
          v-model:current-page="pager.current"
          v-model:page-size="pager.size"
          :total="pager.total"
          layout="total, sizes, prev, pager, next"
          @current-change="load"
          @size-change="resize"
        />
      </div>
    </div>
    <el-dialog v-model="visible" title="新建指令" width="560px"
      ><el-form label-width="100px"
        ><el-form-item label="类型"
          ><el-select v-model="form.type" style="width: 100%" @change="typeChanged"
            ><el-option
              v-for="item in types"
              :key="item.type"
              :label="item.label || item.type"
              :value="item.type" /></el-select></el-form-item
        ><el-form-item label="目标对象"><el-input v-model="form.targetKey" /></el-form-item
        ><el-form-item
          v-for="field in fields"
          :key="field.name"
          :label="field.label || field.name"
          :required="field.required"
          ><el-input
            v-model="form.params[field.name]"
            :placeholder="field.required ? '必填' : '选填'" /></el-form-item></el-form
      ><template #footer
        ><el-button @click="visible = false">取消</el-button
        ><el-button type="primary" @click="create">下发</el-button></template
      ></el-dialog
    >
  </div>
</template>
<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { actionApi } from '../api'
import { canWrite } from '../auth'
import { formatDate, pageResult } from '../utils/format'
import { actionStatusLabels, labelOf, tagTypes } from '../utils/labels'
const rows = ref([])
const types = ref([])
const loading = ref(false)
const visible = ref(false)
const filters = reactive({ type: '', status: '', targetKey: '' })
const pager = reactive({ current: 1, size: 20, total: 0 })
const form = reactive({ type: '', targetKey: '', params: {} })
const fields = computed(() => types.value.find((item) => item.type === form.type)?.params || [])
async function load() {
  loading.value = true
  try {
    const result = pageResult(await actionApi.page({ ...filters, ...pager }))
    rows.value = result.records
    pager.total = result.total
  } finally {
    loading.value = false
  }
}
async function loadTypes() {
  types.value = await actionApi.types()
}
function search() {
  pager.current = 1
  load()
}
function resize(size) {
  pager.size = size
  pager.current = 1
  load()
}
function typeChanged() {
  form.params = {}
}
function openCreate() {
  form.type = types.value[0]?.type || 'OMS_HOLD'
  form.targetKey = ''
  form.params = {}
  visible.value = true
}
async function create() {
  await actionApi.create({ ...form })
  visible.value = false
  ElMessage.success('指令已创建')
  load()
}
async function retry(row) {
  await actionApi.retry(row.id)
  ElMessage.success('已提交重试')
  load()
}
loadTypes()
load()
</script>
