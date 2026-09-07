import { reactive } from 'vue'

const TOKEN_KEY = 'ir_token'
const USER_KEY = 'ir_user'

export const auth = reactive({
  token: localStorage.getItem(TOKEN_KEY) || '',
  user: JSON.parse(localStorage.getItem(USER_KEY) || 'null')
})

export function setAuth(token, user) {
  auth.token = token
  auth.user = user
  localStorage.setItem(TOKEN_KEY, token)
  localStorage.setItem(USER_KEY, JSON.stringify(user))
}

export function clearAuth() {
  auth.token = ''
  auth.user = null
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

export const isAdmin = () => auth.user?.role === 'ADMIN'
export const canWrite = () => ['ADMIN', 'PLANNER'].includes(auth.user?.role)
export const ROLE_LABEL = { ADMIN: '管理员', PLANNER: '计划员', VIEWER: '只读用户' }
