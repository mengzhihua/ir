<template>
  <el-container class="shell">
    <el-aside width="230px" class="aside">
      <div class="brand">
        <el-icon><Compass /></el-icon><span>IR 控制塔</span>
      </div>
      <el-menu
        router
        :default-active="route.path"
        background-color="#13233b"
        text-color="#b9c7da"
        active-text-color="#fff"
      >
        <el-menu-item v-for="item in sideMenus" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon><span>{{ item.name }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <div>
          <span class="crumb">供应链控制塔</span><span class="muted"> / {{ currentName }}</span>
        </div>
        <el-dropdown @command="command">
          <span class="user"
            ><el-icon><User /></el-icon>{{ auth.user?.realName || auth.user?.username
            }}<el-tag size="small">{{ ROLE_LABEL[auth.user?.role] }}</el-tag
            ><el-icon><ArrowDown /></el-icon
          ></span>
          <template #dropdown
            ><el-dropdown-menu
              ><el-dropdown-item command="password">修改密码</el-dropdown-item
              ><el-dropdown-item command="logout" divided
                >退出登录</el-dropdown-item
              ></el-dropdown-menu
            ></template
          >
        </el-dropdown>
      </el-header>
      <el-main class="main"><router-view /></el-main>
    </el-container>
  </el-container>
  <el-dialog v-model="passwordVisible" title="修改密码" width="420px">
    <el-form label-width="90px"
      ><el-form-item label="新密码"
        ><el-input v-model="password" type="password" show-password /></el-form-item
    ></el-form>
    <template #footer
      ><el-button @click="passwordVisible = false">取消</el-button
      ><el-button type="primary" @click="changePassword">保存</el-button></template
    >
  </el-dialog>
</template>
<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { auth, clearAuth, ROLE_LABEL } from '../auth'
import { authApi } from '../api'
import { visibleMenus } from '../router'
const route = useRoute()
const router = useRouter()
const sideMenus = computed(() => visibleMenus())
const currentName = computed(
  () => sideMenus.value.find((item) => item.path === route.path)?.name || '工作台'
)
const passwordVisible = ref(false)
const password = ref('')
async function command(value) {
  if (value === 'password') {
    password.value = ''
    passwordVisible.value = true
    return
  }
  await authApi.logout().catch(() => {})
  clearAuth()
  router.replace('/login')
}
async function changePassword() {
  if (password.value.length < 6) return ElMessage.warning('新密码至少 6 位')
  await authApi.password({ password: password.value })
  ElMessage.success('密码修改成功，请重新登录')
  clearAuth()
  router.replace('/login')
}
</script>
<style scoped>
.shell {
  height: 100%;
}
.aside {
  background: #13233b;
}
.brand {
  height: 64px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 22px;
  color: #fff;
  font-weight: 700;
  font-size: 20px;
}
.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #fff;
  border-bottom: 1px solid #e9eef5;
}
.crumb {
  font-weight: 600;
}
.user {
  display: flex;
  align-items: center;
  gap: 7px;
  cursor: pointer;
}
.main {
  padding: 0;
  overflow: auto;
}
</style>
