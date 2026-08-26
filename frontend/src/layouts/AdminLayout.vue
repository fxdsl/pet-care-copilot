<script setup lang="ts">
import { BellFilled, Collection, DataAnalysis, Operation, Setting, User } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'

import AppBrand from '../components/AppBrand.vue'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const router = useRouter()

async function logout(): Promise<void> {
  await auth.logout()
  await router.replace('/login')
}
</script>

<template>
  <div class="admin-shell">
    <aside class="admin-sidebar">
      <AppBrand />
      <p class="sidebar-caption">{{ auth.user?.role === 'ADMIN' ? '管理控制台' : '内容审核台' }}</p>
      <nav aria-label="管理端导航">
        <RouterLink v-if="auth.user?.role === 'ADMIN'" to="/manage/knowledge"><el-icon><Collection /></el-icon>知识库</RouterLink>
        <RouterLink to="/manage/moderation"><el-icon><BellFilled /></el-icon>内容审核</RouterLink>
        <RouterLink v-if="auth.user?.role === 'ADMIN'" to="/manage/users"><el-icon><User /></el-icon>用户权限</RouterLink>
        <RouterLink v-if="auth.user?.role === 'ADMIN'" to="/manage/audit"><el-icon><Operation /></el-icon>操作审计</RouterLink>
        <RouterLink v-if="auth.user?.role === 'ADMIN'" to="/manage/system"><el-icon><DataAnalysis /></el-icon>系统状态</RouterLink>
        <RouterLink to="/manage/account"><el-icon><Setting /></el-icon>账号设置</RouterLink>
      </nav>
      <div class="sidebar-user">
        <span class="avatar">{{ auth.user?.displayName?.slice(0, 1) }}</span>
        <div><strong>{{ auth.user?.displayName }}</strong><small>{{ auth.user?.role }}</small></div>
      </div>
      <button class="button button-secondary" @click="logout">退出登录</button>
    </aside>
    <main class="admin-content"><RouterView /></main>
  </div>
</template>
