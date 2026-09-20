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
  COMPLETED: '已完成'
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
  OA_START_WORKFLOW: '发起审批',
  OA_APPROVE_TASK: '完成待办'
}

export function labelOf(value, labels) {
  return labels[value] || value || '-'
}
