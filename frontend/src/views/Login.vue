<template>
  <div class="login-page">
    <div class="login-card">
      <div class="logo"><el-icon><Compass /></el-icon><span>IR 供应链控制塔</span></div>
      <p class="subtitle">跨系统订单、仓储与运输运营中心</p>
      <el-form @submit.prevent="submit">
        <el-form-item><el-input v-model="form.username" size="large" placeholder="用户名" prefix-icon="User" /></el-form-item>
        <el-form-item><el-input v-model="form.password" size="large" type="password" show-password placeholder="密码" prefix-icon="Lock" @keyup.enter="submit" /></el-form-item>
        <el-button type="primary" size="large" class="login-btn" :loading="loading" @click="submit">登录控制塔</el-button>
      </el-form>
      <div class="hint">默认管理员：admin / admin123</div>
    </div>
  </div>
</template>
<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi } from '../api'
import { setAuth } from '../auth'
const router = useRouter(); const route = useRoute(); const loading = ref(false)
const form = reactive({ username: 'admin', password: 'admin123' })
async function submit() {
  loading.value = true
  try { const data = await authApi.login(form); setAuth(data.token, data.user); router.replace(route.query.redirect || '/dashboard') } catch (e) { ElMessage.error(e.message || '登录失败') } finally { loading.value = false }
}
</script>
<style scoped>
.login-page { height:100%; display:flex; align-items:center; justify-content:center; background: radial-gradient(circle at top right,#dceeff,#14253e 70%); }.login-card { width:410px; padding:42px; border-radius:16px; background:#fff; box-shadow:0 18px 60px rgba(0,0,0,.2); }.logo { display:flex; align-items:center; justify-content:center; gap:10px; color:#1d65c1; font-size:25px; font-weight:700; }.subtitle,.hint { text-align:center; color:#8b99ac; }.subtitle { margin:12px 0 28px; }.login-btn { width:100%; }.hint { font-size:12px; margin-top:18px; }
</style>
