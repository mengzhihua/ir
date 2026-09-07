<template>
  <el-table :data="rows" stripe border v-loading="loading" height="100%">
    <el-table-column v-for="column in columns" :key="column.prop" :prop="column.prop" :label="column.label" :min-width="column.width || 120" show-overflow-tooltip />
    <el-table-column v-if="$slots.actions" label="操作" width="180" fixed="right"><template #default="scope"><slot name="actions" v-bind="scope" /></template></el-table-column>
  </el-table>
</template>
<script setup>defineProps({ rows: { type: Array, default: () => [] }, columns: { type: Array, default: () => [] }, loading: Boolean })</script>
