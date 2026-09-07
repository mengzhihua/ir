<template>
  <div class="page">
    <div class="page-title"><div><h2>操作日志</h2><p class="subtitle">审计所有非查询类操作</p></div><el-button @click="load">刷新</el-button></div>
    <div class="panel toolbar"><el-input v-model="filters.operator" clearable placeholder="操作人" /><el-input v-model="filters.module" clearable placeholder="模块" /><el-input v-model="filters.action" clearable placeholder="动作" /><el-button type="primary" @click="load">查询</el-button></div>
    <div class="panel"><el-table v-loading="loading" :data="rows" stripe><el-table-column prop="operator" label="操作人" width="120" /><el-table-column prop="module" label="模块" width="130" /><el-table-column prop="action" label="动作" width="150" /><el-table-column prop="path" label="请求路径" min-width="220" /><el-table-column prop="method" label="方法" width="80" /><el-table-column prop="createdAt" label="时间" width="165"><template #default="{ row }">{{ formatDate(row.createdAt) }}</template></el-table-column><el-table-column prop="success" label="结果"><template #default="{ row }"><el-tag :type="row.success ? 'success' : 'danger'">{{ row.success ? '成功' : '失败' }}</el-tag></template></el-table-column><template #empty><el-empty description="暂无操作日志" /></template></el-table><div class="pagination"><el-pagination v-model:current-page="pager.current" v-model:page-size="pager.size" :total="pager.total" layout="total, prev, pager, next" @current-change="load" /></div></div>
  </div>
</template>
<script setup>
import { reactive, ref } from 'vue'
import { systemApi } from '../api'
import { formatDate, pageResult } from '../utils/format'
const rows = ref([])
const loading = ref(false)
const filters = reactive({ operator: '', module: '', action: '' })
const pager = reactive({ current: 1, size: 20, total: 0 })
async function load() { loading.value = true; try { const page = pageResult(await systemApi.opLogs({ ...filters, ...pager })); rows.value = page.records; pager.total = page.total } finally { loading.value = false } }
load()
</script>
