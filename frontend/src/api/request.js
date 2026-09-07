import axios from 'axios'
import { ElMessage } from 'element-plus'
import { auth, clearAuth } from '../auth'

const http = axios.create({ baseURL: '/api', timeout: 20000 })
http.interceptors.request.use((config) => {
  if (auth.token) config.headers.Authorization = `Bearer ${auth.token}`
  return config
})
http.interceptors.response.use((response) => {
  const body = response.data
  if (body?.code !== undefined && body.code !== 0) {
    ElMessage.error(body.msg || '请求失败')
    return Promise.reject(new Error(body.msg))
  }
  return body?.data ?? body
}, (error) => {
  if (error.response?.status === 401) {
    clearAuth()
    if (location.pathname !== '/login') location.assign('/login')
  } else {
    ElMessage.error(error.response?.data?.msg || error.message || '网络错误')
  }
  return Promise.reject(error)
})
export default http
