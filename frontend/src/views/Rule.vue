<template>
  <div class="page">
    <div class="page-title"><div><h2>预警规则</h2><p class="subtitle">维护规则启用状态、等级和参数</p></div><el-button @click="load">刷新</el-button></div>
    <div class="panel">
      <el-table v-loading="loading" :data="rows" stripe>
        <el-table-column prop="code" label="编码" width="180" />
        <el-table-column prop="name" label="规则名称" min-width="180" />
        <el-table-column prop="type" label="类型" width="180" />
        <el-table-column label="等级" width="130"><template #default="{ row }"><el-select v-model="row.severity" size="small" :disabled="!canWrite()" @change="save(row)"><el-option label="高" value="HIGH" /><el-option label="中" value="MEDIUM" /><el-option label="低" value="LOW" /></el-select></template></el-table-column>
        <el-table-column label="启用" width="90"><template #default="{ row }"><el-switch v-model="row.enabled" :disabled="!canWrite()" @change="save(row)" /></template></el-table-column>
        <el-table-column prop="params" label="参数" min-width="220" show-overflow-tooltip />
        <el-table-column label="操作" width="100"><template #default="{ row }"><el-button link type="primary" @click="edit(row)">编辑参数</el-button></template></el-table-column>
        <template #empty><el-empty description="暂无规则" /></template>
      </el-table>
      <div class="pagination"><el-pagination v-model:current-page="pager.current" v-model:page-size="pager.size" :total="pager.total" layout="total, prev, pager, next" @current-change="load" /></div>
    </div>
    <el-dialog v-model="visible" title="编辑规则参数" width="560px">
      <el-form label-position="top"><el-form-item label="JSON参数" :error="jsonError"><el-input v-model="jsonText" type="textarea" :rows="10" /></el-form-item></el-form>
      <template #footer><el-button @click="visible = false">取消</el-button><el-button type="primary" :disabled="!canWrite()" @click="saveParams">保存</el-button></template>
    </el-dialog>
  </div>
</template>
<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { ruleApi } from '../api'
import { canWrite } from '../auth'
import { pageResult } from '../utils/format'
const rows = ref([])
const loading = ref(false)
const pager = reactive({ current: 1, size: 20, total: 0 })
const visible = ref(false)
const editing = ref(null)
const jsonText = ref('{}')
const jsonError = ref('')
async function load() { loading.value = true; try { const result = pageResult(await ruleApi.page({ ...pager })); rows.value = result.records; pager.total = result.total } finally { loading.value = false } }
async function save(row) { await ruleApi.update(row.id, { enabled: row.enabled, severity: row.severity, params: row.params }); ElMessage.success('规则已更新') }
function edit(row) { editing.value = row; jsonText.value = row.params || '{}'; jsonError.value = ''; visible.value = true }
async function saveParams() { try { JSON.parse(jsonText.value); jsonError.value = ''; await ruleApi.update(editing.value.id, { params: jsonText.value }); editing.value.params = jsonText.value; visible.value = false; ElMessage.success('参数已更新') } catch { jsonError.value = '请输入合法JSON' } }
load()
</script>
