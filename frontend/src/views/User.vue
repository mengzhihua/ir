<template>
  <div class="page">
    <div class="page-title">
      <div>
        <h2>用户管理</h2>
        <p class="subtitle">维护控制塔账号、角色和启用状态</p>
      </div>
      <el-button type="primary" @click="open()">新增用户</el-button>
    </div>
    <div class="panel toolbar">
      <el-input
        v-model="filters.keyword"
        clearable
        placeholder="用户名/姓名"
        @keyup.enter="load"
      /><el-select v-model="filters.role" clearable placeholder="角色" @change="load"
        ><el-option label="管理员" value="ADMIN" /><el-option
          label="计划员"
          value="PLANNER" /><el-option label="查看者" value="VIEWER" /></el-select
      ><el-button type="primary" @click="load">查询</el-button>
    </div>
    <div class="panel">
      <el-table v-loading="loading" :data="rows" stripe
        ><el-table-column prop="username" label="用户名" /><el-table-column
          prop="realName"
          label="姓名" /><el-table-column prop="role" label="角色"
          ><template #default="{ row }"
            ><el-tag>{{
              { ADMIN: '管理员', PLANNER: '计划员', VIEWER: '查看者' }[row.role] || row.role
            }}</el-tag></template
          ></el-table-column
        ><el-table-column prop="enabled" label="启用"
          ><template #default="{ row }"
            ><el-switch v-model="row.enabled" @change="save(row)" /></template></el-table-column
        ><el-table-column prop="lastLoginAt" label="最后登录" /><el-table-column label="操作"
          ><template #default="{ row }"
            ><el-button link type="primary" @click="open(row)">编辑</el-button
            ><el-button link @click="reset(row)">重置密码</el-button
            ><el-button link type="danger" @click="remove(row)">删除</el-button></template
          ></el-table-column
        ><template #empty><el-empty description="暂无用户" /></template
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
    <el-dialog v-model="visible" :title="editing.id ? '编辑用户' : '新增用户'"
      ><el-form :model="editing" label-width="90px"
        ><el-form-item label="用户名" required
          ><el-input v-model="editing.username" :disabled="!!editing.id" /></el-form-item
        ><el-form-item label="姓名"><el-input v-model="editing.realName" /></el-form-item
        ><el-form-item label="角色"
          ><el-select v-model="editing.role"
            ><el-option label="管理员" value="ADMIN" /><el-option
              label="计划员"
              value="PLANNER" /><el-option
              label="查看者"
              value="VIEWER" /></el-select></el-form-item
        ><el-form-item label="启用"><el-switch v-model="editing.enabled" /></el-form-item
        ><el-form-item v-if="!editing.id" label="密码"
          ><el-input
            v-model="editing.password"
            type="password"
            show-password /></el-form-item></el-form
      ><template #footer
        ><el-button @click="visible = false">取消</el-button
        ><el-button type="primary" @click="saveUser">保存</el-button></template
      ></el-dialog
    >
  </div>
</template>
<script setup>
import { reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { systemApi } from '../api'
import { pageResult } from '../utils/format'
const rows = ref([])
const loading = ref(false)
const visible = ref(false)
const filters = reactive({ keyword: '', role: '' })
const pager = reactive({ current: 1, size: 20, total: 0 })
const editing = reactive({})
async function load() {
  loading.value = true
  try {
    const page = pageResult(await systemApi.users({ ...filters, ...pager }))
    rows.value = page.records
    pager.total = page.total
  } finally {
    loading.value = false
  }
}
function open(row) {
  Object.assign(
    editing,
    row || { username: '', realName: '', role: 'VIEWER', enabled: true, password: '' }
  )
  visible.value = true
}
async function save(row) {
  await systemApi.updateUser(row.id, { enabled: row.enabled })
  ElMessage.success('状态已更新')
}
async function saveUser() {
  if (editing.id) await systemApi.updateUser(editing.id, editing)
  else await systemApi.createUser(editing)
  visible.value = false
  ElMessage.success('用户已保存')
  load()
}
async function reset(row) {
  await ElMessageBox.confirm('确认重置密码吗？', '确认')
  await systemApi.updateUser(row.id, { password: 'admin123' })
  ElMessage.success('密码已重置为admin123')
}
async function remove(row) {
  await ElMessageBox.confirm('确认删除该用户吗？', '确认')
  await systemApi.deleteUser(row.id)
  ElMessage.success('用户已删除')
  load()
}
load()
</script>
