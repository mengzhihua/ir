<template><v-chart class="chart" :option="option" autoresize /></template>
<script setup>
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent, TitleComponent } from 'echarts/components'
import VChart from 'vue-echarts'
use([CanvasRenderer, BarChart, LineChart, PieChart, GridComponent, LegendComponent, TooltipComponent, TitleComponent])
defineProps({ option: { type: Object, default: () => ({}) } })
</script>
