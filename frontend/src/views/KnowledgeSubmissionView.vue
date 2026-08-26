<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'

import { listMyCommunityPosts, type CommunityPost } from '../api/community'
import { ApiRequestError } from '../api/http'
import {
  getMyKnowledgeSubmission,
  listMyKnowledgeSubmissions,
  submitCommunityKnowledge,
  withdrawKnowledgeSubmission,
  type KnowledgeSubmission,
} from '../api/knowledge'
import PageState from '../components/PageState.vue'

const step = ref(0)
const loading = ref(true)
const submitting = ref(false)
const posts = ref<CommunityPost[]>([])
const submissions = ref<KnowledgeSubmission[]>([])
const selected = ref<KnowledgeSubmission>()
const form = reactive({ postId: '', petType: 'CAT', category: 'FEEDING', consentGranted: false })
const selectedPost = computed(() => posts.value.find((post) => post.id === form.postId))

const statusText: Record<string, string> = {
  PRECHECKING: '风险预检中', PENDING_REVIEW: '等待管理员审核', PUBLISHING: '正在生成向量',
  PUBLISHED: '已进入知识库', REJECTED: '未通过审核', WITHDRAWN: '已撤回', FAILED: '处理失败',
}

async function load(): Promise<void> {
  loading.value = true
  try {
    const [postPage, submissionPage] = await Promise.all([listMyCommunityPosts(), listMyKnowledgeSubmissions()])
    posts.value = postPage.items.filter((post) => post.status === 'PUBLISHED')
    submissions.value = submissionPage.items
  } catch (cause) { ElMessage.error(readable(cause)) }
  finally { loading.value = false }
}

function next(): void {
  if (step.value === 0 && !form.postId) { ElMessage.warning('请先选择一篇已发布帖子'); return }
  if (step.value === 2 && !form.consentGranted) { ElMessage.warning('请确认授权与风险提示'); return }
  step.value = Math.min(3, step.value + 1)
}

async function submit(): Promise<void> {
  submitting.value = true
  try {
    const result = await submitCommunityKnowledge({ ...form })
    selected.value = result
    step.value = 0
    form.postId = ''
    form.consentGranted = false
    ElMessage.success('已提交，系统将异步预检后进入人工审核')
    await load()
  } catch (cause) { ElMessage.error(readable(cause)) }
  finally { submitting.value = false }
}

async function withdraw(item: KnowledgeSubmission): Promise<void> {
  try {
    selected.value = await withdrawKnowledgeSubmission(item.id)
    ElMessage.success('已撤回授权，已发布内容将立即退出 RAG')
    await load()
  } catch (cause) { ElMessage.error(readable(cause)) }
}

/** 点击列表项后再读取不可变审核时间线，列表请求不制造 N+1 查询。 */
async function openSubmission(item: KnowledgeSubmission): Promise<void> {
  try { selected.value = await getMyKnowledgeSubmission(item.id) }
  catch (cause) { ElMessage.error(readable(cause)) }
}

function readable(cause: unknown): string {
  return cause instanceof ApiRequestError ? cause.message : cause instanceof Error ? cause.message : '知识投稿处理失败'
}

onMounted(load)
</script>

<template>
  <section class="knowledge-contribute-page">
    <header class="contribute-hero">
      <div><p class="eyebrow">COMMUNITY KNOWLEDGE</p><h1>把真实经验，变成可靠知识</h1><p>社区内容不会自动进入 RAG。只有你主动授权、通过风险预检和管理员审核后才会发布。</p></div>
      <span class="trust-mark">授权可撤回<br><strong>全程可追溯</strong></span>
    </header>

    <div class="contribute-layout">
      <article class="surface-card wizard-card">
        <el-steps :active="step" finish-status="success" align-center>
          <el-step title="授权与来源" /><el-step title="内容确认" /><el-step title="风险提示" /><el-step title="提交" />
        </el-steps>

        <div v-if="step === 0" class="wizard-panel">
          <h2>选择一篇自己的已发布帖子</h2>
          <p>草稿、隐藏内容和其他用户的帖子不能申请收录。</p>
          <el-select v-model="form.postId" placeholder="选择社区经验" class="wide-select">
            <el-option v-for="post in posts" :key="post.id" :label="post.title" :value="post.id" />
          </el-select>
          <div class="two-fields"><label>宠物类型<el-select v-model="form.petType"><el-option label="猫" value="CAT" /><el-option label="狗" value="DOG" /><el-option label="其他" value="OTHER" /></el-select></label><label>知识分类<el-select v-model="form.category"><el-option label="喂养" value="FEEDING" /><el-option label="健康" value="HEALTH" /><el-option label="疫苗" value="VACCINE" /><el-option label="行为" value="BEHAVIOR" /><el-option label="护理" value="GROOMING" /><el-option label="其他" value="OTHER" /></el-select></label></div>
        </div>
        <div v-else-if="step === 1" class="wizard-panel content-preview">
          <h2>{{ selectedPost?.title }}</h2><p>{{ selectedPost?.content }}</p>
          <small>当前内容会作为版本快照保存。之后修改帖子不会悄悄改变正在审核的版本。</small>
        </div>
        <div v-else-if="step === 2" class="wizard-panel risk-notice">
          <h2>发布前需要知道</h2>
          <ul><li>系统会检查手机号、邮箱、广告、危险建议与提示注入。</li><li>健康、疫苗等高风险知识不能以普通用户经验直接进入 RAG。</li><li>审核通过后回答会标明社区经验来源，不能替代兽医诊断。</li><li>你随时可以撤回授权，撤回后文档立即退出检索。</li></ul>
          <label class="consent-control">
            <input v-model="form.consentGranted" type="checkbox">
            <span>我拥有该内容的发布权，并同意将审核通过的版本用于知识问答</span>
          </label>
        </div>
        <div v-else class="wizard-panel submit-summary">
          <span>准备提交</span><h2>{{ selectedPost?.title }}</h2><p>{{ form.petType }} · {{ form.category }} · 授权状态 GRANTED</p>
        </div>
        <div class="wizard-actions"><button v-if="step > 0" class="button button-secondary" @click="step--">上一步</button><button v-if="step < 3" class="button button-primary" @click="next">下一步</button><button v-else class="button button-primary" :disabled="submitting" @click="submit">{{ submitting ? '提交中…' : '确认提交审核' }}</button></div>
      </article>

      <aside class="submission-rail" :class="{ 'has-selection': selected }">
        <section class="surface-card submission-list">
          <div class="list-heading"><div><p class="eyebrow">MY SUBMISSIONS</p><h2>我的投稿</h2></div><button class="text-button" @click="load">刷新</button></div>
          <div class="submission-scroll">
            <PageState v-if="loading" type="loading" message="正在加载投稿…" />
            <PageState v-else-if="!submissions.length" type="empty" message="还没有知识投稿。" />
            <template v-else>
              <button v-for="item in submissions" :key="item.id" class="submission-row" :class="{ active: selected?.id === item.id }" @click="openSubmission(item)">
                <span :class="['status-dot', item.status.toLowerCase()]" /><div><strong>{{ item.title }}</strong><small>{{ statusText[item.status] || item.status }} · v{{ item.currentVersion }}</small></div><b>{{ item.riskLevel || '—' }}</b>
              </button>
            </template>
          </div>
        </section>

        <article v-if="selected" class="surface-card submission-detail">
          <div><p class="eyebrow">AUDIT TIMELINE</p><h2>{{ selected.title }}</h2><p>{{ selected.aiSummary || '预检尚未完成' }}</p></div>
          <div class="detail-meta"><span>状态 <strong>{{ statusText[selected.status] || selected.status }}</strong></span><span>风险 <strong>{{ selected.riskLevel || '等待预检' }}</strong></span><span>质量分 <strong>{{ selected.qualityScore ?? '—' }}</strong></span></div>
          <div class="risk-labels"><span v-for="label in selected.riskLabels" :key="label">{{ label }}</span></div>
          <ol class="timeline"><li v-for="record in selected.timeline" :key="record.id"><b>{{ record.action }}</b><span>v{{ record.version }} · {{ new Date(record.createdAt).toLocaleString() }}</span><p>{{ record.reason }}</p></li></ol>
          <button v-if="!['WITHDRAWN'].includes(selected.status)" class="button danger-button" @click="withdraw(selected)">撤回授权</button>
        </article>
      </aside>
    </div>
  </section>
</template>

<style scoped>
.knowledge-contribute-page {
  display: grid;
  overflow: hidden;
  width: 100%;
  max-width: 1440px;
  height: 100%;
  min-height: 0;
  margin: 0 auto;
  padding: 0 20px;
  grid-template-rows: auto minmax(0, 1fr);
  gap: 12px;
}

.contribute-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 4px 10px 8px;
}
.contribute-hero h1 { margin: 3px 0 4px; color: #123f38; font-size: clamp(28px, 3vw, 40px); }
.contribute-hero p { margin-bottom: 0; color: #6f837e; }
.trust-mark { flex: 0 0 auto; padding: 10px 18px; border: 1px solid #efd4c1; border-radius: 18px; color: #b65c32; background: #fff1e5; text-align: center; }

.contribute-layout {
  display: grid;
  overflow: hidden;
  height: 100%;
  min-height: 0;
  grid-template-columns: minmax(0, 1.7fr) minmax(300px, .75fr);
  gap: 18px;
}
.wizard-card {
  display: grid;
  overflow: hidden;
  min-height: 0;
  padding: 24px 28px;
  grid-template-rows: auto minmax(0, 1fr) auto;
}
.wizard-panel {
  overflow-y: auto;
  min-height: 0;
  padding: 28px 20px 18px;
  overscroll-behavior: contain;
  scrollbar-gutter: stable;
}
.wizard-panel h2 { color: #16453e; font-size: 28px; }
.wide-select { width: 100%; margin: 24px 0; }
.two-fields { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.two-fields label { display: grid; gap: 8px; font-weight: 700; }
.content-preview p { padding: 24px; border-radius: 18px; background: #f1f6f2; line-height: 1.9; white-space: pre-wrap; }
.risk-notice li { margin: 14px 0; line-height: 1.7; }

.consent-control {
  display: flex;
  width: fit-content;
  max-width: 100%;
  align-items: flex-start;
  gap: 12px;
  margin-top: 22px;
  padding: 14px 16px;
  border: 1px solid #c8d9d0;
  border-radius: 14px;
  background: #f6faf7;
  cursor: pointer;
  line-height: 1.65;
}
.consent-control input { width: 21px; height: 21px; flex: 0 0 auto; margin: 2px 0 0; accent-color: #2f6b5c; cursor: pointer; }
.consent-control:has(input:checked) { border-color: #79a28f; background: #e9f3ed; }

.wizard-actions { display: flex; flex: 0 0 auto; justify-content: flex-end; gap: 12px; padding-top: 16px; border-top: 1px solid #e2e9e4; }
.submission-rail { display: grid; overflow: hidden; min-height: 0; grid-template-rows: minmax(0, 1fr); gap: 14px; }
.submission-rail.has-selection { grid-template-rows: minmax(190px, .8fr) minmax(230px, 1.2fr); }
.submission-list { display: grid; overflow: hidden; min-height: 0; padding: 22px; grid-template-rows: auto minmax(0, 1fr); }
.submission-scroll, .submission-detail { overflow-y: auto; min-height: 0; overscroll-behavior: contain; scrollbar-gutter: stable; }
.submission-detail { margin: 0; padding: 22px; }
.list-heading { display: flex; justify-content: space-between; }
.submission-row { display: grid; width: 100%; grid-template-columns: 10px 1fr auto; align-items: center; gap: 12px; padding: 16px 4px; border: 0; border-bottom: 1px solid #e6ece8; background: transparent; cursor: pointer; text-align: left; }
.submission-row.active { background: #f3f8f4; }
.submission-row div { display: grid; gap: 5px; }
.submission-row small { color: #82928d; }
.submission-row b { font-size: 12px; }
.status-dot { width: 9px; height: 9px; border-radius: 50%; background: #d1a671; }
.status-dot.published { background: #2f795f; }
.status-dot.rejected, .status-dot.failed { background: #c45b52; }
.detail-meta { display: flex; flex-wrap: wrap; gap: 12px; }
.detail-meta span, .risk-labels span { padding: 8px 12px; border-radius: 999px; background: #eef5f0; }
.risk-labels { display: flex; flex-wrap: wrap; gap: 8px; margin: 18px 0; }
.timeline { margin: 26px 8px; padding-left: 26px; border-left: 2px solid #cadbd1; }
.timeline li { margin: 22px 0; }
.timeline li span { margin-left: 12px; color: #80918b; }
.timeline p { margin: 6px 0; }
.danger-button { border: 1px solid #e3aaa4; color: #a94138; background: #fff; }
.eyebrow { color: #d46a3a; font-weight: 800; letter-spacing: .18em; }
.text-button { border: 0; color: #2e705f; background: none; cursor: pointer; }

@media (max-width: 900px) {
  .knowledge-contribute-page { overflow-y: auto; padding: 0 4px 24px; grid-template-rows: auto auto; overscroll-behavior: contain; }
  .contribute-layout { overflow: visible; height: auto; grid-template-columns: 1fr; }
  .contribute-hero { align-items: flex-start; }
  .wizard-card { min-height: 580px; }
  .submission-rail { min-height: 460px; }
  .submission-rail.has-selection { grid-template-rows: 300px auto; }
  .submission-detail { max-height: 520px; }
  .two-fields { grid-template-columns: 1fr; }
}
</style>
