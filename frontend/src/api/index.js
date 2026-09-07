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
  page: () => http.get('/rule/page'),
  update: (id, data) => http.put(`/rule/${id}`, data)
}
export const actionApi = {
  page: () => http.get('/action/page'),
  types: () => http.get('/action/types'),
  create: (data) => http.post('/action', data),
  retry: (id) => http.post(`/action/${id}/retry`)
}
export const forecastApi = {
  history: (params) => http.get('/forecast/history', { params }),
  run: (data) => http.post('/forecast/run', data),
  replenish: (params) => http.get('/forecast/replenish', { params }),
  toAction: (data) => http.post('/forecast/replenish/to-action', data),
  page: () => http.get('/forecast/page')
}
export const sandboxApi = {
  baseline: () => http.post('/sandbox/baseline'),
  create: (data) => http.post('/sandbox/scenario', data),
  run: (id) => http.post(`/sandbox/scenario/${id}/run`),
  page: () => http.get('/sandbox/scenario/page'),
  get: (id) => http.get(`/sandbox/scenario/${id}`),
  compare: (ids) => http.get('/sandbox/compare', { params: { ids: ids.join(',') } }),
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
  sync: (code) => code ? http.post(`/integration/sync/${code}`) : http.post('/integration/sync'),
  logs: (params) => http.get('/integration/sync-log/page', { params })
}
export const systemApi = {
  users: () => http.get('/system/user'),
  createUser: (data) => http.post('/system/user', data),
  updateUser: (id, data) => http.put(`/system/user/${id}`, data),
  deleteUser: (id) => http.delete(`/system/user/${id}`),
  opLogs: () => http.get('/system/op-log/page')
}
