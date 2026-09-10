<template>
  <div class="page">
    <div class="page-title">
      <div>
        <h2>业务目标</h2>
        <p class="subtitle">成本、服务(OTIF/NPS)、库存、供应四类目标的达成度看板,驱动自动平衡</p>
      </div>
      <div>
        <el-button @click="load">刷新</el-button>
        <el-button v-if="canWrite()" type="primary" @click="openEdit()">新增目标</el-button>
      </div>
    </div>
    <div class="stats">
      <div class="stat">
        <div class="label">综合达成分</div>
        <div class="value" :class="scoreClass(board.score)">{{ formatNumber(board.score, 1) }}</div>
      </div>
      <div class="stat">
        <div class="label">NPS 估算</div>
        <div class="value" :class="scoreClass(metrics.npsEstimate)">
          {{ formatNumber(metrics.npsEstimate, 1) }}
        </div>
      </div>
      <div class="stat">
        <div class="label">OTIF(30天)</div>
        <div class="value">{{ percent(metrics.otif30d) }}</div>
      </div>
      <div class="stat">
        <div class="label">单均履约成本</div>
        <div class="value">{{ formatMoney(metrics.costPerOrder30d) }}</div>
      </div>
      <div class="stat">
        <div class="label">缺货率</div>
        <div class="value">{{ percent(metrics.stockoutRate) }}</div>
      </div>
      <div class="stat">
        <div class="label">供应商准时率</div>
        <div class="value">{{ percent(metrics.supplierOnTimeRate) }}</div>
      </div>
    </div>
    <div class="grid-2">
      <div class="panel">
        <h3>目标达成度</h3>
        <Chart :option="attainmentOption" />
      </div>
      <div class="panel">
        <h3>NPS 构成(基于履约表现估算)</h3>
        <Chart :option="npsOption" />
        <p class="muted">
          推荐者:准时无异常送达;贬损者:延误超 24 小时、异常运单或付款后取消;其余为中立。样本
          {{ metrics.npsSample || 0 }} 单,贬损者平均延误
          {{ formatNumber(metrics.avgDelayHoursDetractor, 1) }}
          小时。
        </p>
      </div>
    </div>
    <div class="panel">
      <div class="panel-title"><h3>目标明细</h3></div>
      <el-table :data="board.objectives" stripe>
        <el-table-column prop="code" label="编码" width="150" />
        <el-table-column prop="name" label="目标" />
        <el-table-column prop="category" label="类别" width="100">
          <template #default="{ row }">{{ categoryLabels[row.category] || row.category }}</template>
        </el-table-column>
        <el-table-column label="方向" width="80">
          <template #default="{ row }">{{
            row.direction === 'MIN' ? '越低越好' : '越高越好'
          }}</template>
        </el-table-column>
        <el-table-column label="目标值" align="right">
          <template #default="{ row }">{{ formatValue(row.target, row.unit) }}</template>
        </el-table-column>
        <el-table-column label="实际值" align="right">
          <template #default="{ row }">{{ formatValue(row.actual, row.unit) }}</template>
        </el-table-column>
        <el-table-column label="差距" align="right">
          <template #default="{ row }">
            <span :class="gapClass(row)">{{ formatValue(row.gap, row.unit, true) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="达成率" width="180">
          <template #default="{ row }">
            <el-progress
              :percentage="Math.round(Number(row.attainment || 0) * 100)"
              :status="progressStatus(row.status)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="weight" label="权重" width="80" align="right" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTag[row.status] || 'info'">{{
              statusLabels[row.status] || row.status
            }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="canWrite()" label="操作" width="140">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty description="暂无目标" /></template>
      </el-table>
    </div>
    <el-dialog v-model="editVisible" :title="form.id ? '编辑目标' : '新增目标'" width="520px">
      <el-form label-width="90px">
        <el-form-item label="编码"
          ><el-input v-model="form.code" :disabled="!!form.id" placeholder="如 OTIF"
        /></el-form-item>
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="类别">
          <el-select v-model="form.category">
            <el-option
              v-for="(label, key) in categoryLabels"
              :key="key"
              :label="label"
              :value="key"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="指标">
          <el-select v-model="form.metric" filterable>
            <el-option v-for="key in metricKeys" :key="key" :label="key" :value="key" />
          </el-select>
        </el-form-item>
        <el-form-item label="方向">
          <el-radio-group v-model="form.direction">
            <el-radio-button value="MAX">越高越好</el-radio-button>
            <el-radio-button value="MIN">越低越好</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="目标值"
          ><el-input-number v-model="form.targetValue" :precision="2" :step="0.1"
        /></el-form-item>
        <el-form-item label="权重"><el-input-number v-model="form.weight" :min="0" /></el-form-item>
        <el-form-item label="单位"
          ><el-input v-model="form.unit" placeholder="CNY / RATE / SCORE"
        /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="form.enabled" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { objectiveApi } from '../api'
import { canWrite } from '../auth'
import Chart from '../components/Chart.vue'
import { formatMoney, formatNumber, percent } from '../utils/format'

const board = reactive({ score: 0, objectives: [] })
const metrics = reactive({})
const editVisible = ref(false)
const form = reactive({})
const categoryLabels = { COST: '成本', SERVICE: '服务', INVENTORY: '库存', SUPPLY: '供应' }
const statusLabels = { ON_TRACK: '达标', AT_RISK: '有风险', OFF_TRACK: '偏离' }
const statusTag = { ON_TRACK: 'success', AT_RISK: 'warning', OFF_TRACK: 'danger' }
const metricKeys = computed(() =>
  Object.keys(metrics).filter((key) => typeof metrics[key] === 'number')
)
const attainmentOption = computed(() => ({
  tooltip: { trigger: 'axis', valueFormatter: (value) => `${value}%` },
  grid: { left: 120 },
  xAxis: { type: 'value', max: 100 },
  yAxis: { type: 'category', data: board.objectives.map((row) => row.name) },
  series: [
    {
      type: 'bar',
      data: board.objectives.map((row) => ({
        value: Math.round(Number(row.attainment || 0) * 100),
        itemStyle: {
          color:
            row.status === 'ON_TRACK' ? '#67c23a' : row.status === 'AT_RISK' ? '#e6a23c' : '#f56c6c'
        }
      })),
      label: { show: true, position: 'right', formatter: '{c}%' }
    }
  ]
}))
const npsOption = computed(() => ({
  tooltip: {},
  legend: { bottom: 0 },
  series: [
    {
      type: 'pie',
      radius: ['40%', '65%'],
      data: [
        { name: '推荐者', value: metrics.npsPromoters || 0, itemStyle: { color: '#67c23a' } },
        { name: '中立', value: metrics.npsPassives || 0, itemStyle: { color: '#e6a23c' } },
        { name: '贬损者', value: metrics.npsDetractors || 0, itemStyle: { color: '#f56c6c' } }
      ]
    }
  ]
}))
function scoreClass(value) {
  const number = Number(value || 0)
  return number >= 80 ? 'success' : number >= 60 ? 'warning' : 'danger'
}
function progressStatus(status) {
  return status === 'ON_TRACK' ? 'success' : status === 'AT_RISK' ? 'warning' : 'exception'
}
function gapClass(row) {
  const gap = Number(row.gap || 0)
  const good = row.direction === 'MIN' ? gap <= 0 : gap >= 0
  return good ? 'success' : 'danger'
}
function formatValue(value, unit, signed = false) {
  const number = Number(value || 0)
  const sign = signed && number > 0 ? '+' : ''
  if (unit === 'CNY') {
    return sign + formatMoney(number)
  }
  if (unit === 'RATE') {
    return sign + percent(number)
  }
  return sign + formatNumber(number, 1)
}
async function load() {
  const result = await objectiveApi.scoreboard()
  board.score = result.score
  board.objectives = result.objectives || []
  Object.keys(metrics).forEach((key) => delete metrics[key])
  Object.assign(metrics, result.metrics || {})
}
function openEdit(row) {
  Object.keys(form).forEach((key) => delete form[key])
  Object.assign(
    form,
    row
      ? {
          id: row.id,
          code: row.code,
          name: row.name,
          category: row.category,
          metric: row.metric,
          direction: row.direction,
          targetValue: Number(row.target),
          weight: Number(row.weight),
          unit: row.unit,
          enabled: true
        }
      : {
          code: '',
          name: '',
          category: 'SERVICE',
          metric: 'otif30d',
          direction: 'MAX',
          targetValue: 0.95,
          weight: 10,
          unit: 'RATE',
          enabled: true
        }
  )
  editVisible.value = true
}
async function save() {
  if (!form.code || !form.name) {
    ElMessage.warning('请填写编码与名称')
    return
  }
  await objectiveApi.save(form)
  editVisible.value = false
  ElMessage.success('目标已保存')
  load()
}
async function remove(row) {
  await ElMessageBox.confirm(`确认删除目标 ${row.name} 吗？`, '确认')
  await objectiveApi.remove(row.id)
  ElMessage.success('已删除')
  load()
}
load()
</script>
