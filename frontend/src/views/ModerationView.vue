<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'

import {
  getCommunityAnalytics,
  listModerationReports,
  moderateCommunityReport,
  type CommunityAnalytics,
  type CommunityReport,
} from '../api/community'
import { ApiRequestError } from '../api/http'
import PageState from '../components/PageState.vue'

const reports = ref<CommunityReport[]>([])
const analytics = ref<CommunityAnalytics>()
const status = ref('PENDING')
const loading = ref(true)
const error = ref('')
const selected = ref<CommunityReport>()
const drawerOpen = ref(false)
const action = ref<'NO_ACTION' | 'HIDE_CONTENT' | 'WARN_USER'>('NO_ACTION')
const note = ref('')
const saving = ref(false)

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const [page, summary] = await Promise.all([listModerationReports(status.value), getCommunityAnalytics()])
    reports.value = page.items
    analytics.value = summary
  } catch (cause) { error.value = readable(cause) }
  finally { loading.value = false }
}

function open(report: CommunityReport): void {
  selected.value = report
  action.value = 'NO_ACTION'
  note.value = ''
  drawerOpen.value = true
}

async function submit(): Promise<void> {
  if (!selected.value) return
  saving.value = true
  try {
    await moderateCommunityReport(selected.value.id, { action: action.value, note: note.value || undefined, version: selected.value.version })
    ElMessage.success(action.value === 'HIDE_CONTENT' ? '举报已处理，目标内容已隐藏' : '举报已处理')
    drawerOpen.value = false
    await load()
  } catch (cause) { ElMessage.error(readable(cause)) }
  finally { saving.value = false }
}

function readable(cause: unknown): string {
  return cause instanceof ApiRequestError ? cause.message : '审核队列加载失败'
}

onMounted(load)
</script>

<template>
  <section class="admin-page">
    <header class="admin-page-heading"><div><p class="eyebrow">CONTENT MODERATION</p><h1>内容审核</h1><p>处理社区举报并保留每一次审核结果。</p></div><button class="button button-secondary" @click="load">刷新数据</button></header>
    <div class="metric-grid"><article><small>待处理举报</small><strong>{{ analytics?.pendingReports ?? '—' }}</strong><span>按提交时间进入 Redis ZSet 队列</span></article><article><small>今日信息流 UV</small><strong>{{ analytics?.approximateFeedUv ?? '—' }}</strong><span>HyperLogLog 近似统计，不用于计费</span></article><article><small>当前筛选</small><strong>{{ reports.length }}</strong><span>{{ status }}</span></article></div>
    <article class="surface-card admin-table-card"><div class="table-toolbar"><div><h2>举报队列</h2><p>隐藏内容会同步失效详情缓存、热榜和 GEO。</p></div><el-segmented v-model="status" :options="['PENDING', 'RESOLVED', 'REJECTED']" @change="load" /></div>
      <PageState v-if="loading" type="loading" message="正在读取审核队列…" /><PageState v-else-if="error" type="error" :message="error" @retry="load" /><PageState v-else-if="reports.length === 0" type="empty" message="当前筛选下没有举报。" />
      <el-table v-else :data="reports" stripe><el-table-column prop="createdAt" label="提交时间" width="180"><template #default="scope">{{ new Date(scope.row.createdAt).toLocaleString('zh-CN') }}</template></el-table-column><el-table-column prop="reporterUsername" label="举报人" width="140" /><el-table-column label="目标" min-width="180"><template #default="scope"><span class="status-pill">{{ scope.row.targetType }}</span> {{ scope.row.targetId.slice(0, 12) }}</template></el-table-column><el-table-column prop="reasonType" label="原因" width="170" /><el-table-column prop="description" label="说明" min-width="240" show-overflow-tooltip /><el-table-column prop="status" label="状态" width="120" /><el-table-column fixed="right" label="操作" width="120"><template #default="scope"><button class="table-action" :disabled="scope.row.status !== 'PENDING'" @click="open(scope.row)">审核</button></template></el-table-column></el-table>
    </article>
    <el-drawer v-model="drawerOpen" title="举报审核" size="min(520px, 100vw)"><div v-if="selected" class="moderation-drawer"><dl><dt>举报人</dt><dd>{{ selected.reporterUsername }}</dd><dt>目标</dt><dd>{{ selected.targetType }} / {{ selected.targetId }}</dd><dt>原因</dt><dd>{{ selected.reasonType }}</dd><dt>用户说明</dt><dd>{{ selected.description || '未填写' }}</dd></dl><el-form label-position="top"><el-form-item label="处理动作"><el-radio-group v-model="action"><el-radio-button value="NO_ACTION">无需处理</el-radio-button><el-radio-button value="WARN_USER">警告用户</el-radio-button><el-radio-button value="HIDE_CONTENT">隐藏内容</el-radio-button></el-radio-group></el-form-item><el-form-item label="审核说明"><el-input v-model="note" type="textarea" :rows="5" maxlength="1000" show-word-limit /></el-form-item></el-form><div class="drawer-footer"><button class="button button-secondary" @click="drawerOpen = false">取消</button><button class="button button-primary" :disabled="saving" @click="submit">确认处理</button></div></div></el-drawer>
  </section>
</template>
