<template>
  <div class="page">
    <div class="page-title">
      <div>
        <h2>订单全链路追踪</h2>
        <p class="subtitle">从 OMS 下单到 WMS 出库、TMS 运输和成本核算的完整视图</p>
      </div>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </div>
    <div class="panel toolbar">
      <el-input
        v-model="filters.keyword"
        clearable
        placeholder="订单号"
        style="width: 220px"
        @keyup.enter="search"
      />
      <el-select
        v-model="filters.status"
        clearable
        placeholder="OMS订单状态"
        style="width: 180px"
      >
        <el-option
          v-for="item in statusOptions"
          :key="item"
          :label="item"
          :value="item"
        />
      </el-select>
      <el-select
        v-model="filters.warehouseCode"
        clearable
        placeholder="仓库"
        style="width: 150px"
      >
        <el-option label="上海仓" value="WH-SH" />
        <el-option label="北京仓" value="WH-BJ" />
        <el-option label="广州仓" value="WH-GZ" />
      </el-select>
      <el-select
        v-model="filters.stuck"
        clearable
        placeholder="卡滞状态"
        style="width: 150px"
      >
        <el-option label="仅看卡滞" :value="true" />
        <el-option label="仅看正常" :value="false" />
      </el-select>
      <el-button type="primary" :icon="Search" @click="search">查询</el-button>
      <el-button @click="reset">重置</el-button>
    </div>
    <div class="panel">
      <el-table
        v-loading="loading"
        :data="rows"
        row-key="orderNo"
        stripe
        @row-click="openDetail"
      >
        <el-table-column prop="orderNo" label="订单号" min-width="170" />
        <el-table-column label="OMS状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusType(row.oms?.status)">
              {{ row.oms?.status || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="当前阶段" width="120">
          <template #default="{ row }">
            <el-tag :type="stageType(row.stage)">
              {{ stageLabel(row.stage) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="oms.channelCode" label="渠道" width="100" />
        <el-table-column prop="oms.warehouseCode" label="仓库" width="110" />
        <el-table-column prop="oms.carrierCode" label="承运商" width="100" />
        <el-table-column label="订单金额" width="130" align="right">
          <template #default="{ row }">
            {{ formatMoney(row.oms?.payAmount) }}
          </template>
        </el-table-column>
        <el-table-column label="成本合计" width="130" align="right">
          <template #default="{ row }">
            {{ formatMoney(row.costTotal) }}
          </template>
        </el-table-column>
        <el-table-column label="卡滞小时" width="110" align="right">
          <template #default="{ row }">
            <span :class="{ danger: Number(row.stuckHours) > 0 }">
              {{ formatNumber(row.stuckHours, 0) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="openDetail(row)">
              详情
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无订单追踪数据" />
        </template>
      </el-table>
      <div class="pagination">
        <el-pagination
          v-model:current-page="pager.current"
          v-model:page-size="pager.size"
          :total="pager.total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          @current-change="load"
          @size-change="pageSizeChanged"
        />
      </div>
    </div>
    <el-drawer v-model="drawerVisible" title="订单详情" size="760px">
      <el-skeleton v-if="detailLoading" :rows="8" animated />
      <template v-else-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="订单号">{{ detail.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="渠道">{{ detail.oms?.channelCode || '-' }}</el-descriptions-item>
          <el-descriptions-item label="仓库">{{ detail.oms?.warehouseCode || '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusType(detail.oms?.status)">
              {{ detail.oms?.status || '-' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="订单金额">{{ formatMoney(detail.oms?.payAmount) }}</el-descriptions-item>
          <el-descriptions-item label="运费">{{ formatMoney(detail.oms?.freight) }}</el-descriptions-item>
        </el-descriptions>
        <div class="drawer-section">
          <h4>OMS → WMS → TMS → 成本时间线</h4>
          <el-timeline>
            <el-timeline-item
              v-for="node in detail.timeline || []"
              :key="`${node.system}-${node.node}-${node.time}`"
              :timestamp="formatDate(node.time)"
              placement="top"
            >
              <el-card shadow="never">
                <div class="timeline-head">
                  <el-tag size="small">{{ node.system }}</el-tag>
                  <strong>{{ node.node }}</strong>
                  <span class="muted">{{ node.status }}</span>
                </div>
                <div class="muted">{{ node.detail || '暂无说明' }}</div>
              </el-card>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-if="!detail.timeline?.length" description="暂无时间线" />
        </div>
        <div class="drawer-section">
          <h4>成本明细</h4>
          <el-table :data="detail.costs || []" size="small">
            <el-table-column prop="costType" label="成本类型" />
            <el-table-column prop="bizDate" label="日期" />
            <el-table-column prop="warehouseCode" label="仓库" />
            <el-table-column label="金额" align="right">
              <template #default="{ row }">{{ formatMoney(row.amount) }}</template>
            </el-table-column>
          </el-table>
          <div class="drawer-total">成本合计：{{ formatMoney(detail.costTotal) }}</div>
        </div>
        <div class="drawer-section">
          <h4>关联预警</h4>
          <el-table :data="detail.alerts || []" size="small">
            <el-table-column prop="title" label="预警" min-width="180" />
            <el-table-column prop="severity" label="等级" />
            <el-table-column prop="status" label="状态" />
            <el-table-column label="操作" width="150">
              <template #default="{ row }">
                <el-button
                  v-if="row.suggestedAction"
                  link
                  type="primary"
                  :disabled="!canWrite()"
                  @click="executeSuggested(row)"
                >
                  执行建议指令
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
        <div class="drawer-section">
          <h4>关联指令</h4>
          <el-table :data="detail.actions || []" size="small">
            <el-table-column prop="actionNo" label="指令号" />
            <el-table-column prop="type" label="类型" />
            <el-table-column prop="status" label="状态" />
            <el-table-column prop="result" label="执行结果" min-width="180" />
          </el-table>
        </div>
        <div class="drawer-actions">
          <el-button type="primary" :disabled="!canWrite()" @click="openActionDialog">
            下发指令
          </el-button>
        </div>
      </template>
    </el-drawer>
    <el-dialog v-model="actionVisible" title="下发订单指令" width="520px">
      <el-form label-width="100px">
        <el-form-item label="指令类型">
          <el-select v-model="actionForm.type" style="width: 100%">
            <el-option label="OMS挂起" value="OMS_HOLD" />
            <el-option label="OMS解挂" value="OMS_UNHOLD" />
            <el-option label="OMS优先级" value="OMS_PRIORITIZE" />
            <el-option label="OMS自动处理" value="OMS_AUTO_PROCESS" />
          </el-select>
        </el-form-item>
        <el-form-item label="订单号"><el-input v-model="actionForm.targetKey" disabled /></el-form-item>
        <el-form-item label="参数JSON"><el-input v-model="actionForm.params" type="textarea" :rows="4" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="actionVisible = false">取消</el-button>
        <el-button type="primary" :loading="actionLoading" @click="submitAction">下发</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import { actionApi, alertApi, traceApi } from '../api'
import { canWrite } from '../auth'
import { formatDate, formatMoney, formatNumber, pageResult } from '../utils/format'

const statusOptions = [
  'CREATED',
  'HOLD',
  'AUDITED',
  'ALLOCATED',
  'PUSHED',
  'SHIPPED',
  'COMPLETED',
  'CANCELLED'
]
const filters = reactive({
  keyword: '',
  status: '',
  warehouseCode: '',
  carrierCode: '',
  stuck: undefined
})
const pager = reactive({
  current: 1,
  size: 20,
  total: 0
})
const rows = ref([])
const loading = ref(false)
const drawerVisible = ref(false)
const detailLoading = ref(false)
const detail = ref(null)
const actionVisible = ref(false)
const actionLoading = ref(false)
const actionForm = reactive({
  type: 'OMS_HOLD',
  targetKey: '',
  params: '{}'
})

function statusType(status) {
  return {
    COMPLETED: 'success',
    SHIPPED: 'success',
    CANCELLED: 'info',
    HOLD: 'danger',
    AUDITED: 'warning',
    ALLOCATED: 'primary'
  }[status]
}

function stageType(stage) {
  return {
    DELIVERED: 'success',
    CANCELLED: 'info',
    TRANSPORT: 'primary',
    WAREHOUSE: 'warning',
    ORDER: 'danger'
  }[stage]
}

function stageLabel(stage) {
  return {
    ORDER: '订单处理',
    WAREHOUSE: '仓内作业',
    TRANSPORT: '运输中',
    DELIVERED: '已送达',
    CANCELLED: '已取消'
  }[stage] || stage || '-'
}

async function load() {
  loading.value = true
  try {
    const result = await traceApi.page({
      ...filters,
      current: pager.current,
      size: pager.size
    })
    const page = pageResult(result)
    rows.value = page.records
    pager.total = page.total
  } finally {
    loading.value = false
  }
}

function search() {
  pager.current = 1
  load()
}

function reset() {
  Object.assign(filters, {
    keyword: '',
    status: '',
    warehouseCode: '',
    carrierCode: '',
    stuck: undefined
  })
  search()
}

function pageSizeChanged(size) {
  pager.size = size
  pager.current = 1
  load()
}

async function openDetail(row) {
  drawerVisible.value = true
  detailLoading.value = true
  try {
    detail.value = await traceApi.detail(row.orderNo)
  } finally {
    detailLoading.value = false
  }
}

async function executeSuggested(alert) {
  await alertApi.action(alert.id)
  ElMessage.success('建议指令已执行')
  if (detail.value) {
    detail.value = await traceApi.detail(detail.value.orderNo)
  }
}

function openActionDialog() {
  actionForm.targetKey = detail.value?.orderNo || ''
  actionForm.params = JSON.stringify({ orderNo: actionForm.targetKey }, null, 2)
  actionVisible.value = true
}

async function submitAction() {
  let params
  try {
    params = JSON.parse(actionForm.params || '{}')
  } catch {
    ElMessage.warning('参数JSON格式不正确')
    return
  }
  actionLoading.value = true
  try {
    await actionApi.create({
      type: actionForm.type,
      targetKey: actionForm.targetKey,
      params
    })
    ElMessage.success('指令已下发')
    actionVisible.value = false
    if (detail.value) {
      detail.value = await traceApi.detail(detail.value.orderNo)
    }
  } finally {
    actionLoading.value = false
  }
}

load()
</script>

<style scoped>
.timeline-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.drawer-total {
  margin-top: 10px;
  color: #303133;
  font-weight: 700;
  text-align: right;
}

.drawer-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 24px;
}
</style>
