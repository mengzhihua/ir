<template>
  <div class="chart-wrap">
    <v-chart v-if="!empty" class="chart" :option="option" autoresize />
    <el-empty v-else class="chart-empty" description="暂无数据" />
  </div>
</template>
<script setup>
import { computed } from 'vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import {
  DatasetComponent,
  DataZoomComponent,
  GridComponent,
  LegendComponent,
  TooltipComponent,
  TitleComponent
} from 'echarts/components'
import VChart from 'vue-echarts'

use([
  CanvasRenderer,
  BarChart,
  LineChart,
  PieChart,
  DatasetComponent,
  DataZoomComponent,
  GridComponent,
  LegendComponent,
  TooltipComponent,
  TitleComponent
])

const props = defineProps({
  option: {
    type: Object,
    default: () => ({})
  }
})

const empty = computed(() => {
  const series = props.option.series || []
  return (
    !series.length ||
    series.every((item) => {
      return !item.data || item.data.length === 0
    })
  )
})
</script>
<style scoped>
.chart-wrap {
  position: relative;
  width: 100%;
  min-height: 300px;
}

.chart {
  height: 300px;
  width: 100%;
}

.chart-empty {
  height: 300px;
}
</style>
