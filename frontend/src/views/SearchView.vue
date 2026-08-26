<script setup lang="ts">
import { Clock, Close, Delete, Search, TrendCharts } from '@element-plus/icons-vue'
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import {
  clearSearchHistory,
  deleteSearchHistory,
  listSearchHistory,
  listSearchTrending,
  searchSuggestions,
  unifiedSearch,
  type SearchDateRange,
  type SearchHistory,
  type SearchSort,
  type SearchSuggestion,
  type SearchTrend,
  type SearchType,
  type UnifiedSearchResult,
} from '../api/search'
import HighlightedText from '../components/HighlightedText.vue'

const route = useRoute()
const router = useRouter()
const keyword = ref('')
const type = ref<SearchType>('ALL')
const petType = ref('')
const category = ref('')
const trustLevel = ref('')
const dateRange = ref<SearchDateRange>('ALL')
const sort = ref<SearchSort>('RELEVANCE')
const page = ref(0)
const result = ref<UnifiedSearchResult>()
const suggestions = ref<SearchSuggestion[]>([])
const history = ref<SearchHistory[]>([])
const trends = ref<SearchTrend[]>([])
const loading = ref(false)
const error = ref('')
const suggestionOpen = ref(false)
let suggestionTimer: number | undefined
let requestSequence = 0

const tabs: { value: SearchType; label: string }[] = [
  { value: 'ALL', label: '全部' }, { value: 'POST', label: '社区动态' },
  { value: 'KNOWLEDGE', label: '可信知识' }, { value: 'USER', label: '宠友' },
  { value: 'TOPIC', label: '话题' },
]
const groupLabels: Record<string, string> = { POST: '社区动态', KNOWLEDGE: '可信知识', USER: '宠友', TOPIC: '社区话题' }
const sourceLabels: Record<string, string> = { HISTORY: '历史', TRENDING: '热门', PUBLIC_CONTENT: '内容' }
const hasQuery = computed(() => keyword.value.trim().length > 0)
/** ALL 模式每一页并行分页四个分组，页数应取最大分组而不是分组总和。 */
const paginationTotal = computed(() => {
  if (!result.value) return 0
  return result.value.type === 'ALL'
    ? Math.max(0, ...result.value.groups.map((group) => group.total))
    : result.value.total
})

/** 以路由为搜索状态事实源，浏览器前进、后退和刷新都能恢复结果。 */
watch(() => route.query, async (query) => {
  keyword.value = textQuery(query.query)
  type.value = enumValue(query.type, ['ALL', 'POST', 'KNOWLEDGE', 'USER', 'TOPIC'], 'ALL')
  petType.value = textQuery(query.petType)
  category.value = textQuery(query.category)
  trustLevel.value = textQuery(query.trustLevel)
  dateRange.value = enumValue(query.dateRange, ['ALL', 'LAST_7_DAYS', 'LAST_30_DAYS', 'LAST_YEAR'], 'ALL')
  sort.value = enumValue(query.sort, ['RELEVANCE', 'LATEST'], 'RELEVANCE')
  page.value = Math.max(0, Number(textQuery(query.page)) || 0)
  if (keyword.value) await loadResults()
  else result.value = undefined
}, { immediate: true })

watch(keyword, (value) => {
  window.clearTimeout(suggestionTimer)
  if (!value.trim()) { suggestions.value = []; return }
  suggestionTimer = window.setTimeout(async () => {
    try {
      suggestions.value = await searchSuggestions(value.trim())
      suggestionOpen.value = true
    } catch { suggestions.value = [] }
  }, 220)
})

async function submit(resetPage = true): Promise<void> {
  if (!keyword.value.trim()) return
  const nextPage = resetPage ? 0 : page.value
  const query: Record<string, string> = {
    query: keyword.value.trim(), type: type.value, dateRange: dateRange.value,
    sort: sort.value, page: String(nextPage), size: '10',
  }
  if (petType.value) query.petType = petType.value
  if (category.value) query.category = category.value
  if (trustLevel.value) query.trustLevel = trustLevel.value
  suggestionOpen.value = false
  if (JSON.stringify(route.query) === JSON.stringify(query)) await loadResults()
  else await router.replace({ name: 'search', query })
}

async function loadResults(): Promise<void> {
  const sequence = ++requestSequence
  loading.value = true
  error.value = ''
  try {
    const response = await unifiedSearch({
      query: keyword.value, type: type.value, petType: petType.value || undefined,
      category: category.value || undefined, trustLevel: trustLevel.value || undefined,
      dateRange: dateRange.value, sort: sort.value, page: page.value, size: 10,
    })
    if (sequence === requestSequence) result.value = response
    await loadSidebars()
  } catch (cause) {
    if (sequence === requestSequence) error.value = cause instanceof Error ? cause.message : '搜索失败，请稍后重试'
  } finally {
    if (sequence === requestSequence) loading.value = false
  }
}

function chooseSuggestion(value: string): void { keyword.value = value; void submit() }
function chooseType(value: SearchType): void { type.value = value; void submit() }
function chooseHistory(item: SearchHistory): void { keyword.value = item.query; void submit() }
function chooseTrend(value: string): void { keyword.value = value; void submit() }

async function removeHistory(id: string): Promise<void> {
  await deleteSearchHistory(id)
  history.value = history.value.filter((item) => item.id !== id)
}
async function removeAllHistory(): Promise<void> { await clearSearchHistory(); history.value = [] }
async function loadSidebars(): Promise<void> {
  const [historyResult, trendResult] = await Promise.allSettled([listSearchHistory(12), listSearchTrending(10)])
  if (historyResult.status === 'fulfilled') history.value = historyResult.value
  if (trendResult.status === 'fulfilled') trends.value = trendResult.value
}
async function changePage(next: number): Promise<void> { page.value = next; await submit(false) }
function openResult(path: string): void { void router.push(path) }
function textQuery(value: unknown): string { return typeof value === 'string' ? value : '' }
function enumValue<T extends string>(value: unknown, allowed: T[], fallback: T): T {
  const normalized = textQuery(value).toUpperCase() as T
  return allowed.includes(normalized) ? normalized : fallback
}

onMounted(loadSidebars)
</script>

<template>
  <section class="search-page">
    <aside class="search-rail search-history-rail">
      <div class="rail-heading"><div><span>RECENT</span><h2>搜索历史</h2></div><button v-if="history.length" @click="removeAllHistory">清空</button></div>
      <div v-if="!history.length" class="rail-empty"><el-icon><Clock /></el-icon><span>还没有搜索记录</span></div>
      <button v-for="item in history" :key="item.id" class="history-item" @click="chooseHistory(item)">
        <span><strong>{{ item.query }}</strong><small>{{ item.resultCount }} 条结果 · 搜索 {{ item.searchCount }} 次</small></span>
        <el-icon title="删除" @click.stop="removeHistory(item.id)"><Close /></el-icon>
      </button>
    </aside>

    <main class="search-center">
      <header class="search-hero">
        <span class="eyebrow">UNIFIED DISCOVERY</span>
        <h1>搜索宠物社区与可信知识</h1>
        <form class="search-box" @submit.prevent="submit()">
          <el-icon><Search /></el-icon>
          <input v-model="keyword" placeholder="搜索动态、知识、宠友或话题" maxlength="120" @focus="suggestionOpen = true">
          <button type="submit">搜索</button>
          <div v-if="suggestionOpen && suggestions.length" class="suggestion-panel">
            <button v-for="item in suggestions" :key="`${item.source}-${item.text}`" type="button" @click="chooseSuggestion(item.text)">
              <span>{{ item.text }}</span><small>{{ sourceLabels[item.source] }}</small>
            </button>
          </div>
        </form>
        <nav class="search-tabs">
          <button v-for="tab in tabs" :key="tab.value" :class="{ active: type === tab.value }" @click="chooseType(tab.value)">{{ tab.label }}</button>
        </nav>
        <div class="search-filters">
          <select v-model="petType" @change="submit()"><option value="">全部宠物</option><option value="CAT">猫</option><option value="DOG">犬</option><option value="OTHER">其他</option></select>
          <select v-model="category" @change="submit()"><option value="">全部分类</option><option value="FEEDING">喂养</option><option value="HEALTH">健康</option><option value="VACCINE">疫苗</option><option value="BEHAVIOR">行为</option><option value="GROOMING">护理</option></select>
          <select v-model="trustLevel" @change="submit()"><option value="">全部可信度</option><option value="ADMIN_VERIFIED">管理员审核</option><option value="COMMUNITY_REVIEWED">社区审核</option></select>
          <select v-model="dateRange" @change="submit()"><option value="ALL">不限时间</option><option value="LAST_7_DAYS">最近 7 天</option><option value="LAST_30_DAYS">最近 30 天</option><option value="LAST_YEAR">最近一年</option></select>
          <select v-model="sort" @change="submit()"><option value="RELEVANCE">相关度优先</option><option value="LATEST">最新发布</option></select>
        </div>
      </header>

      <div class="search-results" aria-live="polite">
        <div v-if="loading" class="search-state"><span class="state-paw">🐾</span><h2>正在检索公开内容</h2><p>会同时考虑关键词与本地语义向量。</p></div>
        <div v-else-if="error" class="search-state error"><h2>暂时无法完成搜索</h2><p>{{ error }}</p><button @click="loadResults">重新尝试</button></div>
        <div v-else-if="!hasQuery" class="search-state"><span class="state-paw">宠</span><h2>从一个关键词开始</h2><p>例如“幼猫喂养”“犬疫苗”或感兴趣的宠友。</p></div>
        <div v-else-if="result && result.total === 0" class="search-state"><h2>没有找到匹配内容</h2><p>试试减少筛选条件，或换一个更简短的关键词。</p></div>
        <template v-else-if="result">
          <div class="result-summary">
            <span>找到 {{ result.total }} 条公开结果</span>
            <small v-if="result.degraded">OpenSearch 暂不可用，当前使用 MySQL 安全降级</small>
            <small v-else>关键词 + 本地向量混合检索</small>
          </div>
          <section v-for="group in result.groups" :key="group.type" class="result-group">
            <header><h2>{{ groupLabels[group.type] }}</h2><span>{{ group.total }}</span></header>
            <button v-for="item in group.items" :key="`${item.type}-${item.id}`" class="result-card" @click="openResult(item.routePath)">
              <span class="result-avatar">{{ item.type === 'USER' ? item.title.slice(0, 1) : item.type === 'KNOWLEDGE' ? '知' : '#' }}</span>
              <span class="result-copy">
                <strong><HighlightedText :text="item.title" :query="keyword" /></strong>
                <small>{{ item.authorName || groupLabels[item.type] }}<template v-if="item.publishedAt"> · {{ new Date(item.publishedAt).toLocaleDateString('zh-CN') }}</template></small>
                <p><HighlightedText :text="item.snippet" :query="keyword" /></p>
                <span class="result-tags"><i v-if="item.petType">{{ item.petType }}</i><i v-if="item.category">{{ item.category }}</i><i v-if="item.trustLevel">{{ item.trustLevel }}</i></span>
              </span>
              <span class="result-arrow">→</span>
            </button>
          </section>
          <el-pagination v-if="paginationTotal > result.size" class="search-pagination" background layout="prev, pager, next" :page-size="result.size" :total="paginationTotal" :current-page="page + 1" @current-change="(value: number) => changePage(value - 1)" />
        </template>
      </div>
    </main>

    <aside class="search-rail search-trend-rail">
      <div class="rail-heading"><div><span>DISCOVER</span><h2>正在热搜</h2></div><el-icon><TrendCharts /></el-icon></div>
      <button v-for="(item, index) in trends" :key="item.query" class="trend-item" @click="chooseTrend(item.query)">
        <b>{{ index + 1 }}</b><span><strong>{{ item.query }}</strong><small>热度 {{ Math.round(item.score) }}</small></span>
      </button>
      <div class="privacy-note"><el-icon><Delete /></el-icon><p>搜索历史只对你可见；含手机号或邮箱的查询不会进入公开热词。</p></div>
    </aside>
  </section>
</template>

<style scoped>
.search-page { display:grid; grid-template-columns:260px minmax(0,1fr) 280px; gap:20px; height:100%; min-height:0; color:var(--color-ink); }
.search-rail,.search-center { min-height:0; border:1px solid var(--color-line); border-radius:28px; background:rgba(255,255,255,.92); box-shadow:var(--shadow-sm); }
.search-rail { padding:24px 18px; overflow:auto; }
.search-center { display:grid; grid-template-rows:auto minmax(0,1fr); overflow:hidden; }
.search-hero { position:relative; padding:26px 30px 18px; border-bottom:1px solid var(--color-line); background:linear-gradient(135deg,#fff 45%,#f2f7f2); }
.eyebrow,.rail-heading span { color:#db6b38; font-size:12px; font-weight:800; letter-spacing:.18em; }
h1 { margin:5px 0 18px; font-family:var(--font-display); font-size:clamp(27px,3vw,38px); }
.search-box { position:relative; display:grid; grid-template-columns:auto minmax(0,1fr) auto; align-items:center; gap:10px; padding:7px 8px 7px 16px; border:1px solid #bfd1c9; border-radius:18px; background:#fff; box-shadow:0 12px 28px rgba(38,83,67,.08); }
.search-box input { min-width:0; border:0; outline:0; color:var(--color-ink); font:inherit; font-size:16px; }
.search-box>button,.search-state button { padding:11px 22px; border:0; border-radius:13px; color:#fff; background:var(--color-brand); font-weight:800; }
.suggestion-panel { position:absolute; z-index:12; top:calc(100% + 8px); right:0; left:0; padding:8px; border:1px solid var(--color-line); border-radius:16px; background:#fff; box-shadow:var(--shadow-md); }
.suggestion-panel button { display:flex; justify-content:space-between; width:100%; padding:10px 12px; border:0; border-radius:10px; background:transparent; text-align:left; }
.suggestion-panel button:hover { background:#f2f7f3; }
.suggestion-panel small { color:#c9683d; }
.search-tabs { display:flex; gap:5px; margin-top:17px; overflow:auto; }
.search-tabs button { white-space:nowrap; padding:9px 14px; border:0; border-radius:999px; color:var(--color-ink-soft); background:transparent; font-weight:700; }
.search-tabs button.active { color:#fff; background:var(--color-brand); }
.search-filters { display:flex; gap:8px; margin-top:12px; overflow:auto; }
.search-filters select { min-width:105px; padding:8px 10px; border:1px solid var(--color-line); border-radius:10px; color:var(--color-ink-soft); background:#fff; }
.search-results { min-height:0; padding:20px 28px 32px; overflow:auto; }
.result-summary { display:flex; justify-content:space-between; gap:12px; margin-bottom:17px; color:var(--color-muted); }
.result-summary small { color:#b45d35; }
.result-group { margin-bottom:24px; }
.result-group>header { display:flex; align-items:center; gap:9px; padding:0 2px 9px; }
.result-group h2 { margin:0; font-family:var(--font-display); font-size:23px; }
.result-group>header span { display:grid; place-items:center; min-width:25px; height:25px; border-radius:50%; color:var(--color-brand); background:#e8f1ec; font-size:12px; }
.result-card { display:grid; grid-template-columns:46px minmax(0,1fr) auto; gap:14px; width:100%; padding:17px 14px; border:0; border-top:1px solid #e5ebe7; color:inherit; background:transparent; text-align:left; }
.result-card:hover { border-radius:14px; background:#f7faf7; }
.result-avatar { display:grid; place-items:center; width:46px; height:46px; border-radius:15px; color:#fff; background:var(--color-brand); font-family:var(--font-display); font-size:21px; }
.result-copy { min-width:0; }
.result-copy>strong { display:block; color:var(--color-ink); font-size:18px; }
.result-copy>small { display:block; margin-top:3px; color:var(--color-muted); }
.result-copy p { margin:8px 0 0; color:var(--color-ink-soft); line-height:1.65; }
.result-tags { display:flex; gap:6px; margin-top:8px; }
.result-tags i { padding:3px 8px; border-radius:999px; color:#44705f; background:#eaf3ee; font-size:11px; font-style:normal; }
.result-arrow { align-self:center; color:#87a094; font-size:22px; }
.search-state { display:grid; place-items:center; align-content:center; min-height:360px; color:var(--color-muted); text-align:center; }
.search-state h2 { margin:12px 0 4px; color:var(--color-ink); font-family:var(--font-display); font-size:27px; }
.search-state p { margin:0 0 18px; }
.state-paw { display:grid; place-items:center; width:64px; height:64px; border-radius:21px; color:#fff; background:var(--color-brand); font-family:var(--font-display); font-size:25px; }
.search-pagination { justify-content:center; padding-top:12px; }
.rail-heading { display:flex; align-items:center; justify-content:space-between; gap:10px; margin-bottom:18px; }
.rail-heading h2 { margin:4px 0 0; font-family:var(--font-display); font-size:25px; }
.rail-heading>button { border:0; color:var(--color-muted); background:transparent; }
.history-item,.trend-item { display:flex; align-items:center; justify-content:space-between; gap:9px; width:100%; padding:13px 8px; border:0; border-bottom:1px solid #e6ece8; color:inherit; background:transparent; text-align:left; }
.history-item:hover,.trend-item:hover { color:var(--color-brand); background:#f7faf8; }
.history-item span,.trend-item span { display:grid; min-width:0; }
.history-item strong,.trend-item strong { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.history-item small,.trend-item small { margin-top:3px; color:var(--color-muted); }
.trend-item { justify-content:flex-start; }
.trend-item>b { width:25px; color:#dc6e3c; font:italic 700 19px Georgia,serif; }
.rail-empty { display:grid; place-items:center; gap:8px; min-height:160px; color:var(--color-muted); }
.privacy-note { display:flex; gap:10px; margin-top:22px; padding:14px; border-radius:15px; color:var(--color-muted); background:#f2f6f3; line-height:1.55; }
.privacy-note p { margin:0; font-size:12px; }
@media (max-width:1180px) { .search-page { grid-template-columns:220px minmax(0,1fr); }.search-trend-rail { display:none; } }
@media (max-width:760px) { .search-page { display:block; overflow:auto; }.search-history-rail { display:none; }.search-center { min-height:100%; border-radius:22px; }.search-hero { padding:20px 16px 14px; }.search-results { padding:15px; }.search-filters { padding-bottom:4px; }.result-summary { display:grid; }.result-card { grid-template-columns:40px minmax(0,1fr); }.result-avatar { width:40px; height:40px; }.result-arrow { display:none; } }
</style>
