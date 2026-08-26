<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { getSearchIndexJob, getSystemHealth, rebuildSearchIndex, type SearchIndexJob, type SystemHealth } from '../api/system'
import { ApiRequestError } from '../api/http'
import PageState from '../components/PageState.vue'

const health = ref<SystemHealth>()
const loading = ref(true)
const error = ref('')
const rebuildJob = ref<SearchIndexJob>()
const rebuilding = ref(false)
const rebuildError = ref('')
let pollTimer: number | undefined
async function load(): Promise<void> { loading.value = true; error.value = ''; try { health.value = await getSystemHealth() } catch (cause) { error.value = cause instanceof ApiRequestError ? cause.message : '状态检查失败' } finally { loading.value = false } }
/** 创建异步任务后轮询 MySQL 状态，不让管理页请求长时间占用。 */
async function rebuild(): Promise<void> {
  rebuilding.value = true
  rebuildError.value = ''
  try { rebuildJob.value = await rebuildSearchIndex(); poll() }
  catch (cause) { rebuildError.value = cause instanceof Error ? cause.message : '创建重建任务失败'; rebuilding.value = false }
}
function poll(): void {
  window.clearTimeout(pollTimer)
  if (!rebuildJob.value || ['COMPLETED', 'FAILED'].includes(rebuildJob.value.status)) { rebuilding.value = false; return }
  pollTimer = window.setTimeout(async () => {
    try { rebuildJob.value = await getSearchIndexJob(rebuildJob.value!.id); poll() }
    catch (cause) {
      rebuildError.value = cause instanceof Error ? cause.message : '读取重建进度失败'
      rebuilding.value = false
    }
  }, 1500)
}
onMounted(load)
onUnmounted(() => window.clearTimeout(pollTimer))
</script>

<template><section class="admin-page"><header class="admin-page-heading"><div><p class="eyebrow">SYSTEM HEALTH</p><h1>基础设施状态</h1><p>分别检查业务、AI、数据库、缓存、消息、对象存储和搜索副本。</p></div><button class="button button-secondary" @click="load">重新检查</button></header><PageState v-if="loading" type="loading" message="正在检查各项服务…" /><PageState v-else-if="error" type="error" :message="error" @retry="load" /><div v-else-if="health" class="health-dashboard"><article v-for="(state, name) in health" :key="name" :class="{ down: state !== 'UP' }"><span><i />{{ state }}</span><strong>{{ name }}</strong><p>{{ state === 'UP' ? '连接与基础检查正常' : '当前不可用，请查看对应服务日志' }}</p></article></div><article class="index-panel"><div><p class="eyebrow">SEARCH INDEX</p><h2>公开搜索索引</h2><p>MySQL 是事实源；该操作删除并重建 OpenSearch 查询副本，不会修改业务数据。</p></div><button class="button" :disabled="rebuilding" @click="rebuild">{{ rebuilding ? '正在重建…' : '重建全部索引' }}</button><dl v-if="rebuildJob"><div><dt>状态</dt><dd>{{ rebuildJob.status }}</dd></div><div><dt>进度</dt><dd>{{ rebuildJob.indexedCount }} / {{ rebuildJob.totalCount }}</dd></div><div><dt>失败</dt><dd>{{ rebuildJob.failedCount }}</dd></div><div><dt>版本</dt><dd>v{{ rebuildJob.indexVersion }}</dd></div></dl><p v-if="rebuildError || rebuildJob?.errorMessage" class="index-error">{{ rebuildError || rebuildJob?.errorMessage }}</p></article></section></template>

<style scoped>
.index-panel { display:grid; grid-template-columns:minmax(0,1fr) auto; gap:18px; margin-top:24px; padding:26px; border:1px solid var(--color-line); border-radius:24px; background:#fff; }
.index-panel h2 { margin:4px 0; font-family:var(--font-display); font-size:28px; }.index-panel p { margin:0; color:var(--color-muted); }.index-panel dl { grid-column:1/-1; display:grid; grid-template-columns:repeat(4,1fr); gap:12px; margin:0; }.index-panel dl div { padding:14px; border-radius:14px; background:#f1f6f2; }.index-panel dt { color:var(--color-muted); font-size:12px; }.index-panel dd { margin:5px 0 0; color:var(--color-ink); font-weight:800; }.index-error { grid-column:1/-1; color:#b34335!important; }
@media(max-width:700px){.index-panel{grid-template-columns:1fr}.index-panel dl{grid-template-columns:1fr 1fr}.index-panel button{width:100%}}
</style>
