<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'

import type { ChatResponse } from '../api/chat'
import { ApiRequestError } from '../api/http'
import {
  createAdminKnowledgeSubmission, extractPdf, getAdminKnowledgeSubmission, getKnowledgeSubmissionStats,
  listKnowledgeDocuments, listKnowledgeSubmissions, reindexKnowledge, reviewKnowledgeSubmission,
  testKnowledgeAnswer, type KnowledgeDocument, type KnowledgeSubmission, type KnowledgeSubmissionStats,
  type PdfExtractResult,
} from '../api/knowledge'
import PageState from '../components/PageState.vue'

const loading = ref(true)
const submitting = ref(false)
const documents = ref<KnowledgeDocument[]>([])
const submissions = ref<KnowledgeSubmission[]>([])
const stats = ref<KnowledgeSubmissionStats>({ pendingReview: 0, published: 0, rejected: 0, highRisk: 0 })
const selected = ref<KnowledgeSubmission>()
const drawerOpen = ref(false)
const pdfPreview = ref<PdfExtractResult>()
const testResult = ref<ChatResponse>()
const filter = reactive({ status: 'PENDING_REVIEW', riskLevel: '', sourceType: '' })
const form = reactive({ title: '', sourceName: '', sourceAuthor: '', sourceUrl: '', fileName: '', documentType: 'TEXT' as 'TEXT' | 'PDF', petType: 'CAT', category: 'FEEDING', content: '', sourcePublishedAt: '', expiresAt: '' })
const reviewForm = reactive({ trustLevel: 'B', reason: '' })
const testForm = reactive({ question: '幼猫一天应该喂几次？', petType: 'CAT', category: 'FEEDING' })

async function load(): Promise<void> {
  loading.value = true
  try {
    const [page, currentStats, currentDocuments] = await Promise.all([
      listKnowledgeSubmissions({ ...filter, size: 100 }), getKnowledgeSubmissionStats(), listKnowledgeDocuments(100),
    ])
    submissions.value = page.items; stats.value = currentStats; documents.value = currentDocuments
  } catch (cause) { ElMessage.error(readable(cause)) }
  finally { loading.value = false }
}

/** PDF 只做只读提取，确认后仍创建待审核投稿。 */
async function readFile(event: Event): Promise<void> {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  pdfPreview.value = undefined
  try {
    if (file.name.toLowerCase().endsWith('.pdf')) {
      if (file.size > 15 * 1024 * 1024) throw new Error('PDF 不能超过 15 MiB')
      const result = await extractPdf(file.name, arrayBufferToBase64(await file.arrayBuffer()))
      pdfPreview.value = result
      if (result.status === 'OCR_REQUIRED') throw new Error('扫描型 PDF 需要先完成 OCR')
      form.content = result.content; form.documentType = 'PDF'
    } else { form.content = await file.text(); form.documentType = 'TEXT' }
    form.fileName = file.name
    if (!form.title) form.title = file.name.replace(/\.(md|markdown|txt|pdf)$/i, '')
  } catch (cause) { ElMessage.error(readable(cause)) }
}

async function createSubmission(): Promise<void> {
  submitting.value = true
  try {
    await createAdminKnowledgeSubmission({ ...form, sourceName: form.sourceName || undefined, sourceAuthor: form.sourceAuthor || undefined, sourceUrl: form.sourceUrl || undefined, sourcePublishedAt: form.sourcePublishedAt || undefined, expiresAt: form.expiresAt || undefined })
    ElMessage.success('资料已进入异步预检，不会直接进入 RAG')
    Object.assign(form, { title: '', sourceName: '', sourceAuthor: '', sourceUrl: '', fileName: '', documentType: 'TEXT', content: '', sourcePublishedAt: '', expiresAt: '' })
    pdfPreview.value = undefined; await load()
  } catch (cause) { ElMessage.error(readable(cause)) }
  finally { submitting.value = false }
}

async function openReview(item: KnowledgeSubmission): Promise<void> {
  try { selected.value = await getAdminKnowledgeSubmission(item.id); reviewForm.trustLevel = item.sourceType === 'COMMUNITY_POST' ? 'C' : 'B'; reviewForm.reason = ''; drawerOpen.value = true }
  catch (cause) { ElMessage.error(readable(cause)) }
}

async function review(action: 'APPROVE' | 'REJECT'): Promise<void> {
  if (!selected.value) return
  submitting.value = true
  try {
    selected.value = await reviewKnowledgeSubmission(selected.value.id, { action, expectedVersion: selected.value.currentVersion, trustLevel: action === 'APPROVE' ? reviewForm.trustLevel : undefined, reason: reviewForm.reason || undefined })
    ElMessage.success(action === 'APPROVE' ? '已批准，正在异步生成向量' : '已驳回并通知投稿人')
    drawerOpen.value = false; await load()
  } catch (cause) { ElMessage.error(readable(cause)) }
  finally { submitting.value = false }
}

async function reindex(): Promise<void> {
  submitting.value = true
  try { const result = await reindexKnowledge(); ElMessage.success(`已重建 ${result.documentCount} 份文档、${result.chunkCount} 个分块`); await load() }
  catch (cause) { ElMessage.error(readable(cause)) }
  finally { submitting.value = false }
}

async function runTest(): Promise<void> {
  submitting.value = true
  try { testResult.value = await testKnowledgeAnswer(testForm) }
  catch (cause) { ElMessage.error(readable(cause)) }
  finally { submitting.value = false }
}

function arrayBufferToBase64(buffer: ArrayBuffer): string { const bytes = new Uint8Array(buffer); let binary = ''; for (let offset = 0; offset < bytes.length; offset += 0x8000) binary += String.fromCharCode(...bytes.subarray(offset, offset + 0x8000)); return btoa(binary) }
function readable(cause: unknown): string { return cause instanceof ApiRequestError ? cause.message : cause instanceof Error ? cause.message : '知识服务处理失败' }
onMounted(load)
</script>

<template>
  <section class="admin-page knowledge-workbench">
    <header class="admin-page-heading"><div><p class="eyebrow">KNOWLEDGE GOVERNANCE</p><h1>可信知识审核台</h1><p>资料上传、社区经验、风险预检、人工审核、版本发布与撤回统一治理。</p></div><div class="heading-actions"><button class="button button-secondary" @click="load">刷新工作台</button><button class="button button-secondary" :disabled="submitting" @click="reindex">重建已发布向量</button></div></header>
    <div class="knowledge-metrics"><article><span>待审核</span><strong>{{ stats.pendingReview }}</strong></article><article><span>高风险待审</span><strong>{{ stats.highRisk }}</strong></article><article><span>已发布</span><strong>{{ stats.published }}</strong></article><article><span>已驳回</span><strong>{{ stats.rejected }}</strong></article></div>

    <div class="workbench-grid">
      <article class="surface-card upload-panel"><div class="card-title-row"><div><span class="step-number">01</span><h2>登记管理员资料</h2></div><span class="status-pill">提交后待审</span></div>
        <el-form label-position="top"><div class="form-grid"><el-form-item class="span-2" label="Markdown / TXT / PDF"><input type="file" accept=".md,.markdown,.txt,.pdf,text/plain,application/pdf" @change="readFile"></el-form-item><el-form-item label="标题"><el-input v-model="form.title" maxlength="300" /></el-form-item><el-form-item label="来源机构"><el-input v-model="form.sourceName" maxlength="200" /></el-form-item><el-form-item label="作者"><el-input v-model="form.sourceAuthor" maxlength="120" /></el-form-item><el-form-item label="来源链接"><el-input v-model="form.sourceUrl" maxlength="1000" /></el-form-item><el-form-item label="宠物类型"><el-select v-model="form.petType"><el-option label="猫" value="CAT" /><el-option label="狗" value="DOG" /><el-option label="其他" value="OTHER" /></el-select></el-form-item><el-form-item label="知识分类"><el-select v-model="form.category"><el-option label="喂养" value="FEEDING" /><el-option label="健康" value="HEALTH" /><el-option label="疫苗" value="VACCINE" /><el-option label="行为" value="BEHAVIOR" /><el-option label="护理" value="GROOMING" /><el-option label="其他" value="OTHER" /></el-select></el-form-item><el-form-item label="来源发布日期"><el-input v-model="form.sourcePublishedAt" placeholder="2026-08-25T10:00:00Z" /></el-form-item><el-form-item label="有效期"><el-input v-model="form.expiresAt" placeholder="可留空" /></el-form-item><el-form-item class="span-2" label="原始正文"><el-input v-model="form.content" type="textarea" :rows="8" maxlength="500000" /></el-form-item></div></el-form>
        <div v-if="pdfPreview" class="pdf-preview"><strong>PDF 预览 · {{ pdfPreview.pageCount }} 页</strong><pre>{{ pdfPreview.preview }}</pre></div><button class="button button-primary button-wide" :disabled="submitting || !form.title || !form.content" @click="createSubmission">提交预检与审核</button>
      </article>

      <article class="surface-card queue-panel"><div class="card-title-row"><div><span class="step-number">02</span><h2>审核队列</h2></div><span class="status-pill">高风险优先</span></div><div class="queue-filters"><el-select v-model="filter.status" clearable placeholder="全部状态" @change="load"><el-option label="待审核" value="PENDING_REVIEW" /><el-option label="预检中" value="PRECHECKING" /><el-option label="发布中" value="PUBLISHING" /><el-option label="已发布" value="PUBLISHED" /><el-option label="已驳回" value="REJECTED" /><el-option label="失败" value="FAILED" /></el-select><el-select v-model="filter.riskLevel" clearable placeholder="全部风险" @change="load"><el-option label="高" value="HIGH" /><el-option label="中" value="MEDIUM" /><el-option label="低" value="LOW" /></el-select><el-select v-model="filter.sourceType" clearable placeholder="全部来源" @change="load"><el-option label="社区经验" value="COMMUNITY_POST" /><el-option label="管理员资料" value="ADMIN_UPLOAD" /></el-select></div>
        <PageState v-if="loading" type="loading" message="正在加载审核队列…" /><PageState v-else-if="!submissions.length" type="empty" message="当前筛选条件下没有投稿。" /><div v-else class="review-list"><button v-for="item in submissions" :key="item.id" class="review-row" @click="openReview(item)"><span :class="['risk-badge', (item.riskLevel || 'unknown').toLowerCase()]">{{ item.riskLevel || '预检中' }}</span><div><strong>{{ item.title }}</strong><small>{{ item.sourceType }} · {{ item.authorName || item.sourceName || '未知来源' }} · v{{ item.currentVersion }}</small><p>{{ item.aiSummary || '等待异步预检' }}</p></div><b>{{ item.status }}</b></button></div>
      </article>
    </div>

    <div class="bottom-grid"><article class="surface-card"><div class="table-toolbar"><div><h2>已发布 RAG 文档</h2><p>只有 READY + APPROVED + 未撤回 + 未过期内容可被召回。</p></div><span>{{ documents.length }} 份</span></div><el-table :data="documents" stripe><el-table-column prop="title" label="标题" min-width="200" /><el-table-column prop="sourceName" label="来源" min-width="150" /><el-table-column label="范围" width="150"><template #default="scope">{{ scope.row.petType }} / {{ scope.row.category }}</template></el-table-column><el-table-column label="向量" width="120"><template #default="scope">{{ scope.row.embeddedChunkCount }}/{{ scope.row.chunkCount }}</template></el-table-column><el-table-column prop="status" label="状态" width="100" /></el-table></article>
      <aside class="surface-card rag-test"><h2>发布后 RAG 验证</h2><el-input v-model="testForm.question" type="textarea" :rows="3" /><button class="button button-primary button-wide" :disabled="submitting" @click="runTest">开始测试</button><div v-if="testResult" class="test-result"><small>{{ testResult.modelName }}</small><p>{{ testResult.answer }}</p><span v-for="source in testResult.sources" :key="source.chunkId">{{ source.title }} · {{ Math.round(source.score * 100) }}%</span></div></aside></div>

    <el-drawer v-model="drawerOpen" size="72%" title="知识审核详情"><div v-if="selected" class="review-drawer"><header><div><span>{{ selected.sourceType }}</span><h2>{{ selected.title }}</h2><p>{{ selected.authorName || selected.sourceName }} · v{{ selected.currentVersion }}</p></div><div class="risk-labels"><b>{{ selected.riskLevel || '预检中' }}</b><span v-for="label in selected.riskLabels" :key="label">{{ label }}</span></div></header><div class="content-compare"><article><h3>原始快照</h3><pre>{{ selected.originalContent }}</pre></article><article><h3>清洗后正文</h3><pre>{{ selected.cleanedContent || '预检尚未完成' }}</pre></article></div><div class="review-summary"><p><strong>预检摘要：</strong>{{ selected.aiSummary || '—' }}</p><p><strong>质量分：</strong>{{ selected.qualityScore ?? '—' }}</p></div><ol class="audit-timeline"><li v-for="item in selected.timeline" :key="item.id"><strong>{{ item.action }}</strong><span>{{ item.reviewerName || '系统' }} · {{ new Date(item.createdAt).toLocaleString() }}</span><p>{{ item.reason }}</p></li></ol><div v-if="selected.status === 'PENDING_REVIEW'" class="review-actions"><el-select v-model="reviewForm.trustLevel" :disabled="selected.sourceType === 'COMMUNITY_POST'"><el-option label="A · 官方权威" value="A" /><el-option label="B · 可验证专业" value="B" /><el-option label="C · 用户经验" value="C" /></el-select><el-input v-model="reviewForm.reason" type="textarea" :rows="3" maxlength="1000" placeholder="填写审核依据；驳回时必填" /><div><button class="button reject-button" @click="review('REJECT')">驳回</button><button class="button button-primary" @click="review('APPROVE')">批准并异步发布</button></div></div></div></el-drawer>
  </section>
</template>

<style scoped>
.knowledge-workbench{max-width:1600px}.knowledge-metrics{display:grid;grid-template-columns:repeat(4,1fr);gap:16px;margin:20px 0}.knowledge-metrics article{padding:20px 24px;border:1px solid #dbe5df;border-radius:18px;background:#fff}.knowledge-metrics span{color:#72847e}.knowledge-metrics strong{display:block;margin-top:8px;font-size:34px;color:#17463e}.workbench-grid{display:grid;grid-template-columns:minmax(520px,.9fr) minmax(560px,1.1fr);gap:20px}.upload-panel,.queue-panel,.bottom-grid>.surface-card{padding:24px}.queue-filters{display:grid;grid-template-columns:repeat(3,1fr);gap:10px;margin:18px 0}.review-list{max-height:720px;overflow:auto}.review-row{display:grid;width:100%;grid-template-columns:70px minmax(0,1fr) auto;gap:12px;padding:18px 4px;border:0;border-bottom:1px solid #e4ebe7;background:transparent;text-align:left;cursor:pointer}.review-row div{display:grid;gap:5px}.review-row small,.review-row p{color:#7d8e88}.review-row p{margin:0;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.risk-badge{align-self:start;padding:7px 8px;border-radius:999px;text-align:center;background:#edf2ef}.risk-badge.high{background:#fde8e4;color:#af4139}.risk-badge.medium{background:#fff0d9;color:#a3681d}.risk-badge.low{background:#e5f4ec;color:#276e55}.bottom-grid{display:grid;grid-template-columns:minmax(0,1.5fr) 360px;gap:20px;margin-top:20px}.rag-test{padding:24px}.rag-test .button{margin-top:12px}.review-drawer{padding:0 20px 40px}.review-drawer header{display:flex;justify-content:space-between;gap:20px}.risk-labels{display:flex;align-items:flex-start;gap:7px;flex-wrap:wrap}.risk-labels span,.risk-labels b{padding:7px 10px;border-radius:999px;background:#eef4f0}.content-compare{display:grid;grid-template-columns:1fr 1fr;gap:16px;margin-top:20px}.content-compare article{min-width:0;padding:18px;border-radius:16px;background:#f3f7f4}.content-compare pre{max-height:380px;overflow:auto;white-space:pre-wrap;word-break:break-word;line-height:1.7}.audit-timeline{border-left:2px solid #c8d8ce;margin:24px 8px;padding-left:24px}.audit-timeline li{margin:20px 0}.audit-timeline span{margin-left:12px;color:#82918c}.review-actions{position:sticky;bottom:0;display:grid;gap:12px;padding:18px;border:1px solid #dbe6df;border-radius:18px;background:#fff}.review-actions>div{display:flex;justify-content:flex-end;gap:12px}.reject-button{border:1px solid #dc9e97;color:#a83e37;background:#fff}@media(max-width:1200px){.workbench-grid,.bottom-grid{grid-template-columns:1fr}.knowledge-metrics{grid-template-columns:repeat(2,1fr)}}@media(max-width:760px){.knowledge-metrics{grid-template-columns:1fr 1fr}.content-compare{grid-template-columns:1fr}.queue-filters{grid-template-columns:1fr}}
</style>
