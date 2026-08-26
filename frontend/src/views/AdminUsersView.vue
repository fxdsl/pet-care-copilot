<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import { listAdminUsers, updateAdminUserRole, updateAdminUserStatus, type AdminUser, type UserRole } from '../api/admin'
import { ApiRequestError } from '../api/http'
import PageState from '../components/PageState.vue'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const users = ref<AdminUser[]>([])
const keyword = ref('')
const loading = ref(true)
const error = ref('')

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try { users.value = (await listAdminUsers(keyword.value)).items }
  catch (cause) { error.value = cause instanceof ApiRequestError ? cause.message : '用户列表加载失败' }
  finally { loading.value = false }
}

async function changeRole(user: AdminUser, role: UserRole): Promise<void> {
  try { await updateAdminUserRole(user.id, role); ElMessage.success(`${user.username} 已调整为 ${role}`); await load() }
  catch (cause) { ElMessage.error(cause instanceof ApiRequestError ? cause.message : '角色调整失败'); await load() }
}

async function toggleStatus(user: AdminUser): Promise<void> {
  const target = user.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
  await ElMessageBox.confirm(`确认将 ${user.username} 设置为 ${target} 吗？`, '账号状态', { type: 'warning' })
  await updateAdminUserStatus(user.id, target)
  ElMessage.success('账号状态已更新')
  await load()
}

onMounted(load)
</script>

<template>
  <section class="admin-page"><header class="admin-page-heading"><div><p class="eyebrow">USER GOVERNANCE</p><h1>用户与权限</h1><p>普通用户先注册，再由管理员授予审核员、认证卖家或管理员角色。</p></div></header><article class="surface-card admin-table-card"><div class="table-toolbar"><el-input v-model="keyword" clearable placeholder="搜索用户名或昵称" @keyup.enter="load" /><button class="button button-primary" @click="load">查询</button></div><PageState v-if="loading" type="loading" message="正在加载用户…" /><PageState v-else-if="error" type="error" :message="error" @retry="load" /><el-table v-else :data="users" stripe><el-table-column label="用户" min-width="190"><template #default="scope"><div class="table-user"><span class="avatar avatar-small">{{ scope.row.displayName.slice(0, 1) }}</span><div><strong>{{ scope.row.displayName }}</strong><small>@{{ scope.row.username }}</small></div></div></template></el-table-column><el-table-column prop="region" label="地区" width="150" /><el-table-column label="角色" width="190"><template #default="scope"><el-select :model-value="scope.row.role" @change="changeRole(scope.row, $event)"><el-option label="USER" value="USER" /><el-option label="VERIFIED_SELLER" value="VERIFIED_SELLER" /><el-option label="MODERATOR" value="MODERATOR" /><el-option label="ADMIN" value="ADMIN" /></el-select></template></el-table-column><el-table-column prop="status" label="状态" width="120" /><el-table-column prop="securityVersion" label="安全版本" width="100" /><el-table-column fixed="right" label="操作" width="120"><template #default="scope"><button class="table-action" :disabled="scope.row.id === auth.user?.id" @click="toggleStatus(scope.row)">{{ scope.row.status === 'ACTIVE' ? '禁用' : '恢复' }}</button></template></el-table-column></el-table></article></section>
</template>
