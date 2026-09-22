export const orderStatusLabels = {
  CREATED: '已创建',
  HOLD: '已挂起',
  AUDITED: '已审核',
  ALLOCATED: '已分配',
  PUSHED: '已推送',
  SHIPPED: '已出库',
  COMPLETED: '已完成',
  CANCELLED: '已取消'
}

export const scenarioKindLabels = {
  MANUAL: '人工沙盘',
  AUTO: '系统自动',
  BASELINE: '基线'
}

export const scenarioStatusLabels = {
  RUN: '已运行',
  DRAFT: '草稿',
  FAILED: '失败'
}

export const capitalVerdictLabels = {
  RELIABLE: '可靠',
  TIGHT: '偏紧',
  INSUFFICIENT: '不足'
}

export const commandKindLabels = {
  ALERT: '预警',
  ACTION: '待办指令',
  DECISION: '平衡决策',
  SANDBOX: '沙盘方案'
}

export const objectiveStatusLabels = {
  ON_TRACK: '达标',
  AT_RISK: '预警',
  OFF_TRACK: '未达标'
}

export const alertStatusLabels = {
  OPEN: '待处理',
  ACKED: '已确认',
  RESOLVED: '已解决',
  IGNORED: '已忽略'
}

export const actionStatusLabels = {
  PENDING: '待执行',
  RUNNING: '执行中',
  SUCCESS: '成功',
  FAILED: '失败',
  UNKNOWN: '待对账',
  RETRIED: '已重试',
  SUPERSEDED: '已作废'
}

export const severityLabels = {
  HIGH: '高',
  MEDIUM: '中',
  LOW: '低'
}

export const ruleTypeLabels = {
  ORDER_STUCK: '订单卡单',
  WMS_STUCK: '仓库卡单',
  TMS_DELAY: '运输延误',
  TMS_OPEN: '待调度',
  LOW_STOCK: '低库存',
  COST_OVERRUN: '成本超支',
  FORECAST_STOCKOUT: '预测缺货',
  EXT_STATUS: '外部状态',
  ASN_DELAY: 'ASN 延误',
  SUPPLIER_RISK: '供应商风险',
  SAP_LOW_STOCK: 'ERP 低库存',
  SAP_MO_OPEN: '生产订单待释放'
}

export const systemModeLabels = {
  MOCK: '模拟',
  HTTP: '在线'
}

export const integrationStatusLabels = {
  PENDING: '待处理',
  CREATED: '已创建',
  DRAFT: '草稿',
  LOW: '偏低',
  OPEN: '开放',
  CLOSED: '已关闭',
  COMPLETED: '已完成',
  NEW: '新建',
  NEGOTIATION: '谈判中',
  RELEASED: '已发布',
  SHORT: '短缺',
  RUNNING: '执行中',
  SYNCED: '已同步',
  CONFIRMED: '已确认',
  IN_TRANSIT: '运输中'
}

export const supplyStatusLabels = {
  NEW: '新建',
  CONFIRMED: '已确认',
  RELEASED: '已发布',
  IN_TRANSIT: '运输中',
  CLOSED: '已关闭',
  RECEIVED: '已收货',
  CANCELLED: '已取消',
  DELAYED: '已延误',
  PARTIAL: '部分收货',
  DELIVERED: '已送达',
  SIGNED: '已签收',
  SHORT: '短缺',
  RUNNING: '执行中',
  COMPLETED: '已完成'
}

export const traceStatusLabels = {
  ...orderStatusLabels,
  DELIVERED: '已送达',
  SIGNED: '已签收',
  IN_TRANSIT: '运输中',
  CLOSED: '已关闭'
}

export const forecastMethodLabels = {
  MA: '移动平均',
  SES: '简单指数平滑',
  HOLT: '霍尔特',
  SEASONAL_NAIVE: '季节朴素',
  AUTO: '自动选择'
}

export const tagTypes = {
  orderStatus: {
    COMPLETED: 'success',
    SHIPPED: 'success',
    CANCELLED: 'info',
    HOLD: 'danger',
    AUDITED: 'warning',
    ALLOCATED: 'primary',
    CREATED: 'info',
    PUSHED: 'primary'
  },
  scenarioStatus: {
    RUN: 'success',
    DRAFT: 'info',
    FAILED: 'danger'
  },
  capitalVerdict: {
    RELIABLE: 'success',
    TIGHT: 'warning',
    INSUFFICIENT: 'danger'
  },
  alertStatus: {
    OPEN: 'danger',
    ACKED: 'warning',
    RESOLVED: 'success',
    IGNORED: 'info'
  },
  actionStatus: {
    PENDING: 'warning',
    RUNNING: 'primary',
    SUCCESS: 'success',
    FAILED: 'danger',
    UNKNOWN: 'info',
    RETRIED: 'info',
    SUPERSEDED: 'info'
  },
  severity: {
    HIGH: 'danger',
    MEDIUM: 'warning',
    LOW: 'info'
  }
}

export const actionTypeLabels = {
  OMS_HOLD: '挂起订单',
  OMS_UNHOLD: '恢复订单',
  OMS_PRIORITIZE: '订单加急',
  OMS_AUTO_PROCESS: '自动过审',
  OMS_CANCEL: '取消订单',
  OMS_REROUTE_WAREHOUSE: '改仓发货',
  WMS_ALLOCATE: '仓内分配',
  WMS_REPLENISH: '仓内补货',
  TMS_DISPATCH: '调度发运',
  TMS_SYNC_TRACK: '同步轨迹',
  TMS_SWITCH_CARRIER: '更换承运商',
  SRM_PURCHASE_SUGGEST: '采购建议',
  SRM_SUBMIT_PR: '提交采购申请',
  SRM_APPROVE_PR: '批准采购申请',
  SAP_CREATE_PR: 'ERP 创建采购申请',
  SAP_RELEASE_PR: 'ERP 释放采购申请',
  SAP_RELEASE_MO: 'ERP 释放生产订单',
  BOM_EXPLODE: 'BOM 展开',
  INV_SUBMIT_REQUEST: '提交开票',
  INV_APPROVE_REQUEST: '批准开票',
  CRM_ADVANCE_STAGE: '推进商机',
  CRM_ESCALATE_CASE: '升级工单',
  DMS_REPLENISH_SHORTAGE: '经销商补货',
  DMS_PUSH_REPLENISH: '下发补货单',
  OA_START_WORKFLOW: '发起审批',
  OA_APPROVE_TASK: '完成待办',
  APPLY_SANDBOX: '采用沙盘推荐'
}

export function labelOf(value, labels) {
  return labels[value] || value || '-'
}
