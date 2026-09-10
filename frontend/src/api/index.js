import http from './request'

export const authApi = {
  login: (data) => http.post('/auth/login', data),
  me: () => http.get('/auth/me'),
  logout: () => http.post('/auth/logout'),
  password: (data) => http.post('/auth/password', data)
}
export const towerApi = { overview: () => http.get('/tower/overview') }
export const traceApi = {
  page: (params) => http.get('/trace/page', { params }),
  detail: (id) => http.get(`/trace/${id}`)
}
export const alertApi = {
  page: (params) => http.get('/alert/page', { params }),
  stats: () => http.get('/alert/stats'),
  evaluate: () => http.post('/alert/evaluate'),
  action: (id) => http.post(`/alert/${id}/execute-suggested`),
  ack: (id) => http.post(`/alert/${id}/ack`),
  resolve: (id) => http.post(`/alert/${id}/resolve`),
  ignore: (id) => http.post(`/alert/${id}/ignore`)
}
export const ruleApi = {
  page: (params) => http.get('/rule/page', { params }),
  update: (id, data) => http.put(`/rule/${id}`, data)
}
export const actionApi = {
  page: (params) => http.get('/action/page', { params }),
  types: () => http.get('/action/types'),
  create: (data) => http.post('/action', data),
  retry: (id) => http.post(`/action/${id}/retry`)
}
export const forecastApi = {
  history: (params) => http.get('/forecast/history', { params }),
  run: (data) => http.post('/forecast/run', data),
  replenish: (params) => http.get('/forecast/replenish', { params }),
  toAction: (data) => http.post('/forecast/replenish/to-action', data),
  page: (params) => http.get('/forecast/page', { params })
}
export const sandboxApi = {
  baseline: () => http.post('/sandbox/baseline'),
  create: (data) => http.post('/sandbox/scenario', data),
  run: (id) => http.post(`/sandbox/scenario/${id}/run`),
  page: (params) => http.get('/sandbox/scenario/page', { params }),
  get: (id) => http.get(`/sandbox/scenario/${id}`),
  compare: (ids) => http.get('/sandbox/compare', { params: { ids: ids.join(',') } }),
  defaults: (id) => (id ? http.get(`/sandbox/scenario/${id}`) : http.get('/sandbox/defaults')),
  apply: (id) => http.post(`/sandbox/scenario/${id}/apply`)
}
export const costApi = {
  summary: (days = 30) => http.get('/cost/summary', { params: { days } }),
  page: (params) => http.get('/cost/page', { params }),
  saving: () => http.get('/cost/saving'),
  targets: () => http.get('/cost/target'),
  saveTarget: (data) => http.post('/cost/target', data),
  deleteTarget: (id) => http.delete(`/cost/target/${id}`)
}
export const integrationApi = {
  systems: () => http.get('/integration/system'),
  create: (data) => http.post('/integration/system', data),
  update: (id, data) => http.put(`/integration/system/${id}`, data),
  remove: (id) => http.delete(`/integration/system/${id}`),
  health: (code) => http.post(`/integration/system/${code}/health`),
  sync: (code) => (code ? http.post(`/integration/sync/${code}`) : http.post('/integration/sync')),
  logs: (params) => http.get('/integration/sync-log/page', { params })
}
export const systemApi = {
  users: (params) => http.get('/system/user', { params }),
  createUser: (data) => http.post('/system/user', data),
  updateUser: (id, data) => http.put(`/system/user/${id}`, data),
  deleteUser: (id) => http.delete(`/system/user/${id}`),
  opLogs: (params) => http.get('/system/op-log/page', { params })
}
export const objectiveApi = {
  list: () => http.get('/objective'),
  save: (data) => http.post('/objective', data),
  remove: (id) => http.delete(`/objective/${id}`),
  scoreboard: () => http.get('/objective/scoreboard'),
  metrics: () => http.get('/objective/metrics')
}
export const balanceApi = {
  overview: () => http.get('/balance/overview'),
  run: () => http.post('/balance/run'),
  runs: (params) => http.get('/balance/run/page', { params }),
  runDetail: (id) => http.get(`/balance/run/${id}`),
  decisions: (params) => http.get('/balance/decision/page', { params }),
  approve: (id) => http.post(`/balance/decision/${id}/approve`),
  reject: (id, reason) => http.post(`/balance/decision/${id}/reject`, { reason }),
  config: () => http.get('/balance/config'),
  saveConfig: (data) => http.post('/balance/config', data)
}
export const supplyApi = {
  overview: () => http.get('/supply/overview'),
  page: (params) => http.get('/supply/purchase/page', { params }),
  sap: () => http.get('/supply/sap')
}
