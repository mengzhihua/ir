<template><div class="page"><div class="page-title"><h2>预警规则</h2><span class="muted">系统规则由后端统一配置，参数以 JSON 保存</span></div><div class="panel"><el-table :data="rows" stripe><el-table-column prop="code" label="编码" /><el-table-column prop="name" label="规则名称" /><el-table-column prop="type" label="类型" /><el-table-column prop="severity" label="等级" /><el-table-column prop="params" label="参数 JSON" min-width="260" /><el-table-column prop="enabled" label="启用"><template #default="{ row }"><el-switch v-model="row.enabled" disabled /></template></el-table-column></el-table></div></div></template>
<script setup>
import { ref } from 'vue'; import { ruleApi } from '../api'
const rows = ref([]); async function load() { rows.value = await ruleApi.page() }; load()
</script>
