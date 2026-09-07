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

export const scenarioStatusLabels = {
  RUN: '已运行',
  DRAFT: '草稿',
  FAILED: '失败'
}

export const alertStatusLabels = {
  OPEN: '待处理',
  ACKED: '已确认',
  RESOLVED: '已解决',
  IGNORED: '已忽略'
}

export const actionStatusLabels = {
  PENDING: '待执行',
  SUCCESS: '成功',
  FAILED: '失败'
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
  alertStatus: {
    OPEN: 'danger',
    ACKED: 'warning',
    RESOLVED: 'success',
    IGNORED: 'info'
  },
  actionStatus: {
    PENDING: 'warning',
    SUCCESS: 'success',
    FAILED: 'danger'
  },
  severity: {
    HIGH: 'danger',
    MEDIUM: 'warning',
    LOW: 'info'
  }
}

export function labelOf(value, labels) {
  return labels[value] || value || '-'
}
