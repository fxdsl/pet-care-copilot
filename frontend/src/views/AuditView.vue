<script setup lang="ts">
import { onMounted, ref } from 'vue'

import { listAdminAudits, type AdminAudit } from '../api/admin'
import { ApiRequestError } from '../api/http'
import PageState from '../components/PageState.vue'

const audits = ref<AdminAudit[]>([])
const loading = ref(true)
const error = ref('')
async function load(): Promise<void> { loading.value = true; error.value = ''; try { audits.value = (await listAdminAudits()).items } catch (cause) { error.value = cause instanceof ApiRequestError ? cause.message : '审计记录加载失败' } finally { loading.value = false } }
onMounted(load)
</script>

<template><section class="admin-page"><header class="admin-page-heading"><div><p class="eyebrow">ADMIN AUDIT</p><h1>权限操作审计</h1><p>角色与状态变更的不可省略追踪记录。</p></div><button class="button button-secondary" @click="load">刷新</button></header><article class="surface-card admin-table-card"><PageState v-if="loading" type="loading" message="正在读取审计记录…" /><PageState v-else-if="error" type="error" :message="error" @retry="load" /><el-timeline v-else><el-timeline-item v-for="audit in audits" :key="audit.id" :timestamp="new Date(audit.createdAt).toLocaleString('zh-CN')" placement="top"><article class="audit-event"><span class="status-pill">{{ audit.action }}</span><h3>{{ audit.actorUsername }} → {{ audit.targetUsername || audit.targetUserId }}</h3><p>{{ audit.beforeValue || '—' }} → {{ audit.afterValue || '—' }}</p></article></el-timeline-item></el-timeline><PageState v-if="!loading && !error && audits.length === 0" type="empty" message="暂无权限变更记录。" /></article></section></template>
