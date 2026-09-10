<template>
  <div class="page">
    <div class="page-title">
      <div>
        <h2>供应协同</h2>
        <p class="subtitle">SRM 供应商绩效与采购/到货跟踪,SAP 库存与财务对账</p>
      </div>
      <div>
        <el-button @click="load">刷新</el-button>
        <el-button v-if="canWrite()" type="primary" @click="sync">同步 SRM/SAP</el-button>
      </div>
    </div>
    <div class="stats">
      <div class="stat">
        <div class="label">在途采购金额</div>
        <div class="value">{{ formatMoney(overview.openAmount) }}</div>
      </div>
      <div class="stat">
        <div class="label">延误 ASN</div>
        <div class="value" :class="overview.delayedAsn ? 'danger' : ''">
          {{ overview.delayedAsn || 0 }}
        </div>
      </div>
      <div class="stat">
        <div class="label">SAP 库存价值</div>
        <div class="value">{{ formatMoney(finance.STOCK_VALUE) }}</div>
      </div>
      <div class="stat">
        <div class="label">应付未清</div>
        <div class="value">{{ formatMoney(finance.AP_OPEN) }}</div>
      </div>
      <div class="stat">
        <div class="label">应收未清</div>
        <div class="value">{{ formatMoney(finance.AR_OPEN) }}</div>
      </div>
      <div class="stat">
        <div class="label">本月成本/收入</div>
        <div class="value small">
          {{ formatMoney(finance.MONTH_COST) }} / {{ formatMoney(finance.MONTH_REVENUE) }}
        </div>
      </div>
    </div>
    <div class="grid-2">
      <div class="panel">
        <h3>供应商绩效</h3>
        <el-table :data="overview.suppliers || []" size="small">
          <el-table-column prop="supplierCode" label="供应商" width="90" />
          <el-table-column label="等级" width="70">
            <template #default="{ row }">
              <el-tag :type="gradeTag[row.grade] || 'info'" size="small">{{
                row.grade || '-'
              }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="综合分" align="right" width="80">
            <template #default="{ row }">{{ formatNumber(row.avgScore, 1) }}</template>
          </el-table-column>
          <el-table-column label="准时率" align="right" width="80">
            <template #default="{ row }">{{ percent(row.onTimeRate) }}</template>
          </el-table-column>
          <el-table-column label="质量" align="right" width="80">
            <template #default="{ row }">{{ percent(row.qualityRate) }}</template>
          </el-table-column>
          <el-table-column prop="openPo" label="在途PO" align="right" width="80" />
          <el-table-column label="在途金额" align="right">
            <template #default="{ row }">{{ formatMoney(row.openAmount) }}</template>
          </el-table-column>
          <el-table-column label="延误ASN" align="right" width="80">
            <template #default="{ row }">
              <span :class="row.delayedAsn ? 'danger' : ''">{{ row.delayedAsn }}</span>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <div class="panel">
        <h3>采购/到货状态</h3>
        <Chart :option="statusOption" />
      </div>
    </div>
    <div class="grid-2">
      <div class="panel">
        <div class="panel-title"><h3>延误到货</h3></div>
        <el-table :data="overview.delayedList || []" size="small">
          <el-table-column prop="code" label="ASN" width="120" />
          <el-table-column prop="poCode" label="采购单" width="120" />
          <el-table-column prop="supplierCode" label="供应商" width="90" />
          <el-table-column prop="sku" label="SKU" width="90" />
          <el-table-column prop="qty" label="数量" align="right" width="70" />
          <el-table-column prop="expectedDate" label="预计到货" />
          <el-table-column v-if="canWrite()" label="操作" width="80">
            <template #default="{ row }">
              <el-button link type="primary" @click="expedite(row)">催单</el-button>
            </template>
          </el-table-column>
          <template #empty><el-empty description="暂无延误到货" /></template>
        </el-table>
      </div>
      <div class="panel">
        <div class="panel-title"><h3>SAP 与 WMS 库存对账</h3></div>
        <el-table :data="sap.stockReconcile || []" size="small" max-height="320">
          <el-table-column prop="sku" label="SKU" />
          <el-table-column prop="sapQty" label="SAP 库存" align="right" />
          <el-table-column prop="wmsQty" label="WMS 库存" align="right" />
          <el-table-column label="差异" align="right">
            <template #default="{ row }">
              <span :class="Number(row.diff) === 0 ? 'success' : 'warning'">{{ row.diff }}</span>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>
    <div class="panel">
      <div class="panel-title"><h3>采购单据</h3></div>
      <div class="toolbar">
        <el-select v-model="filters.docType" clearable placeholder="单据类型" @change="search">
          <el-option label="采购订单" value="PO" />
          <el-option label="发货通知(ASN)" value="ASN" />
        </el-select>
        <el-input v-model="filters.supplierCode" clearable placeholder="供应商" />
        <el-input v-model="filters.sku" clearable placeholder="SKU" />
        <el-input v-model="filters.status" clearable placeholder="状态" />
        <el-button type="primary" @click="search">查询</el-button>
      </div>
      <el-table v-loading="loading" :data="records" stripe>
        <el-table-column prop="docType" label="类型" width="70" />
        <el-table-column prop="code" label="单号" width="130" />
        <el-table-column prop="refCode" label="关联单" width="130" />
        <el-table-column prop="supplierCode" label="供应商" width="90" />
        <el-table-column prop="sku" label="SKU" width="90" />
        <el-table-column prop="status" label="状态" width="110" />
        <el-table-column prop="qty" label="数量" align="right" width="80" />
        <el-table-column prop="receivedQty" label="已收" align="right" width="80" />
        <el-table-column label="金额" align="right" width="110">
          <template #default="{ row }">{{
            row.amount == null ? '-' : formatMoney(row.amount)
          }}</template>
        </el-table-column>
        <el-table-column prop="expectedDate" label="预计到货" width="110" />
        <el-table-column label="同步时间">
          <template #default="{ row }">{{ formatDate(row.syncedAt) }}</template>
        </el-table-column>
        <template #empty><el-empty description="暂无单据,请先同步 SRM" /></template>
      </el-table>
      <div class="pagination">
        <el-pagination
          v-model:current-page="pager.current"
          v-model:page-size="pager.size"
          :total="pager.total"
          layout="total, sizes, prev, pager, next"
          @current-change="loadRecords"
          @size-change="loadRecords"
        />
      </div>
    </div>
  </div>
</template>
<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { actionApi, integrationApi, supplyApi } from '../api'
import { canWrite } from '../auth'
import Chart from '../components/Chart.vue'
import { formatDate, formatMoney, formatNumber, pageResult, percent } from '../utils/format'

const overview = reactive({})
const sap = reactive({})
const finance = computed(() => sap.finance || {})
const records = ref([])
const loading = ref(false)
const filters = reactive({ docType: '', supplierCode: '', sku: '', status: '' })
const pager = reactive({ current: 1, size: 20, total: 0 })
const gradeTag = { A: 'success', B: 'primary', C: 'warning', D: 'danger' }
const statusOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  legend: { bottom: 0 },
  xAxis: {
    type: 'category',
    data: [
      ...new Set([
        ...Object.keys(overview.poStatus || {}),
        ...Object.keys(overview.asnStatus || {})
      ])
    ]
  },
  yAxis: { type: 'value' },
  series: [
    {
      name: '采购订单',
      type: 'bar',
      data: Object.keys(overview.poStatus || {}).map((key) => overview.poStatus[key])
    },
    {
      name: 'ASN',
      type: 'bar',
      data: [
        ...new Set([
          ...Object.keys(overview.poStatus || {}),
          ...Object.keys(overview.asnStatus || {})
        ])
      ].map((key) => (overview.asnStatus || {})[key] || 0)
    }
  ]
}))
async function loadRecords() {
  loading.value = true
  try {
    const page = pageResult(await supplyApi.page({ ...filters, ...pager }))
    records.value = page.records
    pager.total = page.total
  } finally {
    loading.value = false
  }
}
async function load() {
  Object.assign(overview, await supplyApi.overview())
  Object.assign(sap, overview.sap || {})
  loadRecords()
}
function search() {
  pager.current = 1
  loadRecords()
}
async function sync() {
  await integrationApi.sync('SRM')
  await integrationApi.sync('SAP')
  ElMessage.success('SRM/SAP 同步完成')
  load()
}
async function expedite(row) {
  const result = await actionApi.create({
    type: 'SRM_EXPEDITE_PO',
    targetKey: row.poCode,
    params: { poCode: row.poCode, asnCode: row.code, reason: '控制塔人工催单' }
  })
  if (result.status === 'SUCCESS') {
    ElMessage.success('催单已下发 SRM')
  } else {
    ElMessage.error(result.result || '催单失败')
  }
  load()
}
load()
</script>
<style scoped>
.value.small {
  font-size: 16px;
}
</style>
