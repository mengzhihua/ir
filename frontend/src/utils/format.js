export function formatDate(value) {
  if (!value) {
    return '-'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return String(value)
  }
  return date.toLocaleString('zh-CN', {
    hour12: false,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

export function formatMoney(value) {
  const number = Number(value || 0)
  return `¥${number.toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  })}`
}

export function formatNumber(value, digits = 2) {
  const number = Number(value || 0)
  return number.toLocaleString('zh-CN', {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits
  })
}

export function pageResult(value) {
  return {
    records: value?.records || [],
    total: Number(value?.total || 0),
    current: Number(value?.current || 1),
    size: Number(value?.size || 20)
  }
}

export function percent(value) {
  return `${(Number(value || 0) * 100).toFixed(1)}%`
}

export function parseJson(value, fallback = {}) {
  if (value === null || value === undefined || value === '') {
    return fallback
  }
  if (typeof value !== 'string') {
    return value
  }
  try {
    return JSON.parse(value)
  } catch {
    return fallback
  }
}

export function jsonText(value) {
  return JSON.stringify(parseJson(value, value || {}), null, 2)
}
