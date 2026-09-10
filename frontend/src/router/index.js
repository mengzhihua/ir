import { createRouter, createWebHistory } from 'vue-router'
import Layout from '../layout/Layout.vue'
import { auth, isAdmin } from '../auth'

export const menus = [
  {
    path: '/dashboard',
    name: '控制塔总览',
    icon: 'Odometer',
    component: () => import('../views/Dashboard.vue')
  },
  {
    path: '/trace',
    name: '订单追踪',
    icon: 'Search',
    component: () => import('../views/Trace.vue')
  },
  {
    path: '/objective',
    name: '业务目标',
    icon: 'Aim',
    component: () => import('../views/Objective.vue')
  },
  {
    path: '/balance',
    name: '自动平衡',
    icon: 'Operation',
    component: () => import('../views/Balance.vue')
  },
  {
    path: '/supply',
    name: '供应协同',
    icon: 'Box',
    component: () => import('../views/Supply.vue')
  },
  { path: '/alert', name: '预警中心', icon: 'Bell', component: () => import('../views/Alert.vue') },
  { path: '/rule', name: '规则管理', icon: 'SetUp', component: () => import('../views/Rule.vue') },
  {
    path: '/action',
    name: '联动指令',
    icon: 'Promotion',
    component: () => import('../views/Action.vue')
  },
  {
    path: '/forecast',
    name: '需求预测',
    icon: 'TrendCharts',
    component: () => import('../views/Forecast.vue')
  },
  {
    path: '/replenish',
    name: '补货建议',
    icon: 'TakeawayBox',
    component: () => import('../views/Replenish.vue')
  },
  {
    path: '/sandbox',
    name: '沙盘模拟',
    icon: 'DataAnalysis',
    component: () => import('../views/Sandbox.vue')
  },
  {
    path: '/compare',
    name: '场景对比',
    icon: 'Histogram',
    component: () => import('../views/Compare.vue')
  },
  { path: '/cost', name: '成本分析', icon: 'Money', component: () => import('../views/Cost.vue') },
  {
    path: '/integration',
    name: '系统集成',
    icon: 'Connection',
    component: () => import('../views/Integration.vue')
  },
  {
    path: '/system/user',
    name: '用户管理',
    icon: 'User',
    adminOnly: true,
    component: () => import('../views/User.vue')
  },
  {
    path: '/system/oplog',
    name: '操作日志',
    icon: 'Document',
    adminOnly: true,
    component: () => import('../views/OpLog.vue')
  }
]
export const visibleMenus = () => menus.filter((item) => !item.adminOnly || isAdmin())
const routes = [
  { path: '/login', component: () => import('../views/Login.vue') },
  {
    path: '/',
    component: Layout,
    redirect: '/dashboard',
    children: menus.map((item) => ({ path: item.path.slice(1), component: item.component }))
  }
]
const router = createRouter({ history: createWebHistory(), routes })
router.beforeEach((to) => {
  if (to.path === '/login') return auth.token ? '/dashboard' : true
  if (!auth.token) return { path: '/login', query: { redirect: to.fullPath } }
  if (to.path.startsWith('/system') && !isAdmin()) return '/dashboard'
  return true
})
export default router
