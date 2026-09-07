<template>
  <div class="page">
    <div class="page-title">
      <div><h2>预警中心</h2><p class="subtitle">统一处理订单、库存、运输和成本风险</p></div>
      <el-button v-if="canWrite()" type="primary" @click="evaluate">立即评估</el-button>
    </div>
    <div class="stats">
      <div class="stat"><div class="label">开放预警</div><div class="value">{{ stats.open || 0 }}</div></div>
      <div class="stat"><div class="label">高等级</div><div class="value danger">{{ stats.high || 0 }}</div></div>
      <div class="stat"><div class="label">今日新增</div><div class="value">{{ stats.today || 0 }}</div></div>
      <div class="stat"><div class="label">已处理</div><div class="value success">{{ stats.resolved || 0 }}</div></div>
    </div>
    <div class="panel toolbar">
      <el-select v-model="filters.status" clearable placeholder="状态" @change="search">
        <el-option label="开放" value="OPEN" /><el-option label="已确认" value="ACKED" />
        <el-option label="已解决" value="RESOLVED" /><el-option label="已忽略" value="IGNORED" />
      </el-select>
      <el-select v-model="filters.severity" clearable placeholder="等级" @change="search">
        <el-option label="高" value="HIGH" /><el-option label="中" value="MEDIUM" /><el-option label="低" value="LOW" />
      </el-select>
      <el-input v-model="filters.type" clearable placeholder="规则类型" style="width: 180px" @keyup.enter="search" />
      <el-button type="primary" @click="search">查询</el-button>
    </div>
    <div class="panel">
      <el-table v-loading="loading" :data="rows" stripe>
        <el-table-column prop="title" label="预警标题" min-width="220" />
        <el-table-column prop="type" label="规则类型" width="150" />
        <el-table-column prop="severity" label="等级" width="90"><template #default="{ row }"><el-tag :type="severityType(row.severity)">{{ row.severity }}</el-tag></template></el-table-column>
        <el-table-column prop="targetKey" label="对象" width="170" />
        <el-table-column prop="status" label="状态" width="100"><template #default="{ row }"><el-tag :type="statusType(row.status)">{{ row.status }}</el-tag></template></el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="165"><template #default="{ row }">{{ formatDate(row.createdAt) }}</template></el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button v-if="canWrite() && row.status === 'OPEN'" link @click="operate(row, 'ack')">确认</el-button>
            <el-button v-if="canWrite() && row.status !== 'RESOLVED'" link @click="operate(row, 'resolve')">解决</el-button>
            <el-button v-if="canWrite() && row.status === 'OPEN'" link @click="operate(row, 'ignore')">忽略</el-button>
            <el-button v-if="canWrite() && row.suggestedAction" link type="primary" @click="suggest(row)">执行建议</el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty description="暂无预警" /></template>
      </el-table>
      <div class="pagination"><el-pagination v-model:current-page="pager.current" v-model:page-size="pager.size" :total="pager.total" layout="total, sizes, prev, pager, next" @current-change="load" @size-change="resize" /></div>
    </div>
  </div>
</template>
<script setup>
import { reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { alertApi } from '../api'
import { canWrite } from '../auth'
import { formatDate, pageResult } from '../utils/format'
const filters = reactive({ status: '', severity: '', type: '' })
const pager = reactive({ current: 1, size: 20, total: 0 })
const stats = reactive({})
const rows = ref([])
const loading = ref(false)
function severityType(value) { return { HIGH: 'danger', MEDIUM: 'warning', LOW: 'info' }[value] }
function statusType(value) { return { OPEN: 'danger', ACKED: 'warning', RESOLVED: 'success', IGNORED: 'info' }[value] }
async function load() {
  loading.value = true
  try {
    const result = pageResult(await alertApi.page({ ...filters, ...pager }))
    rows.value = result.records
    pager.total = result.total
    Object.assign(stats, await alertApi.stats())
  } finally {
    loading.value = false
  }
}
function search() { pager.current = 1; load() }
function resize(size) { pager.size = size; pager.current = 1; load() }
async function evaluate() { await alertApi.evaluate(); ElMessage.success('评估完成'); load() }
async function operate(row, action) {
  await ElMessageBox.confirm(`确认执行“${action}”操作吗？`, '操作确认')
  await alertApi[action](row.id)
  ElMessage.success('操作成功')
  load()
}
async function suggest(row) { await ElMessageBox.confirm('确认执行建议指令吗？', '操作确认'); await alertApi.action(row.id); ElMessage.success('建议指令已执行'); load() }
load()
</script>
