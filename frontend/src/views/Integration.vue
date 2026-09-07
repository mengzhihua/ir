<template>
  <div class="page">
    <div class="page-title"><div><h2>系统集成</h2><p class="subtitle">统一管理OMS、WMS、TMS、BMS连接与同步</p></div><el-button @click="load">刷新</el-button></div>
    <div class="system-grid"><el-card v-for="system in systems" :key="system.id" class="system-card"><template #header><div class="card-head"><strong>{{ system.name || system.systemName || system.code }}</strong><el-tag :type="system.systemCode === 'SRM' ? 'info' : 'success'">{{ system.systemCode === 'SRM' ? '预留' : system.mode }}</el-tag></div></template><el-descriptions :column="1" size="small"><el-descriptions-item label="地址">{{ system.baseUrl || 'Mock数据源' }}</el-descriptions-item><el-descriptions-item label="最近同步">{{ formatDate(system.lastSyncAt) }}</el-descriptions-item><el-descriptions-item label="最近健康">{{ formatDate(system.lastHealthAt) }}</el-descriptions-item></el-descriptions><div class="card-actions"><el-radio-group v-model="system.mode" :disabled="system.systemCode === 'SRM' || !canWrite()" @change="saveMode(system)"><el-radio-button value="MOCK" /><el-radio-button value="HTTP" /></el-radio-group><el-button size="small" @click="edit(system)">编辑</el-button><el-button size="small" @click="health(system)">健康检查</el-button><el-button size="small" type="primary" :disabled="system.systemCode === 'SRM' || !canWrite()" @click="sync(system)">立即同步</el-button></div></el-card></div>
    <div class="panel"><div class="panel-title"><h3>同步日志</h3></div><el-table :data="logs" stripe><el-table-column prop="systemCode" label="系统" /><el-table-column prop="status" label="状态"><template #default="{ row }"><el-tag :type="row.status === 'SUCCESS' ? 'success' : 'danger'">{{ row.status }}</el-tag></template></el-table-column><el-table-column prop="message" label="结果" /><el-table-column prop="startedAt" label="开始时间"><template #default="{ row }">{{ formatDate(row.startedAt) }}</template></el-table-column><template #empty><el-empty description="暂无同步日志" /></template></el-table><div class="pagination"><el-pagination v-model:current-page="pager.current" v-model:page-size="pager.size" :total="pager.total" layout="total, prev, pager, next" @current-change="loadLogs" /></div></div>
    <el-dialog v-model="visible" title="编辑系统连接"><el-form label-width="100px"><el-form-item label="Base URL"><el-input v-model="editing.baseUrl" /></el-form-item><el-form-item label="认证方式"><el-select v-model="editing.authType"><el-option label="无" value="NONE" /><el-option label="Bearer" value="BEARER" /><el-option label="API Key" value="API_KEY" /></el-select></el-form-item><el-form-item label="用户名"><el-input v-model="editing.username" /></el-form-item><el-form-item label="密码"><el-input v-model="editing.password" type="password" show-password /></el-form-item><el-form-item label="API Key"><el-input v-model="editing.apiKey" /></el-form-item></el-form><template #footer><el-button @click="visible=false">取消</el-button><el-button type="primary" @click="save">保存</el-button></template></el-dialog>
  </div>
</template>
<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { integrationApi } from '../api'
import { canWrite } from '../auth'
import { formatDate, pageResult } from '../utils/format'
const systems = ref([])
const logs = ref([])
const visible = ref(false)
const editing = reactive({})
const pager = reactive({ current: 1, size: 20, total: 0 })
async function load() { systems.value = await integrationApi.systems(); loadLogs() }
async function loadLogs() { const page = pageResult(await integrationApi.logs({ ...pager })); logs.value = page.records; pager.total = page.total }
function edit(system) { Object.assign(editing, system); visible.value = true }
async function save() { await integrationApi.update(editing.id, editing); visible.value = false; ElMessage.success('连接配置已保存'); load() }
async function saveMode(system) { await integrationApi.update(system.id, { mode: system.mode }); ElMessage.success('模式已切换') }
async function health(system) { const result = await integrationApi.health(system.systemCode); ElMessage.success(result?.message || '健康检查完成'); load() }
async function sync(system) { await integrationApi.sync(system.systemCode); ElMessage.success('同步任务已提交'); loadLogs() }
load()
</script>
<style scoped>
.system-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; margin-bottom: 16px; }
.card-head, .card-actions { display: flex; align-items: center; justify-content: space-between; gap: 8px; flex-wrap: wrap; }
.card-actions { justify-content: flex-start; margin-top: 18px; }
@media (max-width: 1100px) { .system-grid { grid-template-columns: 1fr 1fr; } }
</style>
