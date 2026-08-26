import { apiRequest } from './http'
import type { ChatResponse } from './chat'

export interface KnowledgeReviewRecord {
  id: string
  version: number
  reviewerUserId: string | null
  reviewerName: string | null
  action: string
  trustLevel: string | null
  reason: string | null
  createdAt: string
}

/** 投稿详情同时展示原文、清洗结果、风险标签和不可变审核时间线。 */
export interface KnowledgeSubmission {
  id: string
  sourceType: 'COMMUNITY_POST' | 'ADMIN_UPLOAD'
  sourceBusinessId: string | null
  authorUserId: string | null
  authorName: string | null
  title: string
  sourceName: string | null
  sourceAuthor: string | null
  sourceUrl: string | null
  fileName: string | null
  documentType: 'TEXT' | 'PDF'
  petType: string
  category: string
  originalContent: string
  cleanedContent: string | null
  consentStatus: string
  status: string
  riskLevel: string | null
  riskLabels: string[]
  aiSummary: string | null
  qualityScore: number | null
  currentVersion: number
  reviewerUserId: string | null
  reviewerName: string | null
  publishedDocumentId: string | null
  sourcePublishedAt: string | null
  reviewedAt: string | null
  publishedAt: string | null
  expiresAt: string | null
  errorMessage: string | null
  createdAt: string
  updatedAt: string
  timeline: KnowledgeReviewRecord[]
}

export interface KnowledgeSubmissionPage { items: KnowledgeSubmission[]; page: number; size: number; total: number }
export interface KnowledgeSubmissionStats { pendingReview: number; published: number; rejected: number; highRisk: number }

/** 知识文档列表项和向量化完成度。 */
export interface KnowledgeDocument {
  id: string
  title: string
  sourceName?: string
  fileName?: string
  documentType: 'TEXT' | 'PDF'
  petType: string
  category: string
  status: string
  chunkCount: number
  embeddedChunkCount: number
  embeddingModel?: string
  createdAt: string
}

/** PDF 解析预览中的单页信息。 */
export interface PdfPage {
  pageNumber: number
  text: string
  charCount: number
}

/** PDF 只读提取结果；OCR_REQUIRED 时禁止直接导入。 */
export interface PdfExtractResult {
  fileName: string
  status: 'READY' | 'OCR_REQUIRED'
  extractionMode: 'TEXT' | 'SCANNED'
  pageCount: number
  charCount: number
  content: string
  preview: string
  pages: PdfPage[]
}

/** 全量专业向量重建结果。 */
export interface KnowledgeReindexResult {
  documentCount: number
  chunkCount: number
  embeddingModel?: string
}

/** 用户把自己已发布的社区经验提交到审核队列。 */
export const submitCommunityKnowledge = (input: {
  postId: string; petType: string; category: string; consentGranted: boolean
}) => apiRequest<KnowledgeSubmission>('/api/v1/knowledge-submissions/community', {
  method: 'POST', body: JSON.stringify(input),
})

export const listMyKnowledgeSubmissions = (page = 0, size = 20) =>
  apiRequest<KnowledgeSubmissionPage>(`/api/v1/knowledge-submissions/mine?page=${page}&size=${size}`)

export const getMyKnowledgeSubmission = (id: string) =>
  apiRequest<KnowledgeSubmission>(`/api/v1/knowledge-submissions/${id}`)

export const withdrawKnowledgeSubmission = (id: string) =>
  apiRequest<KnowledgeSubmission>(`/api/v1/knowledge-submissions/${id}`, { method: 'DELETE' })

/** 管理员资料上传只创建投稿，不再绕过审核直接进入 RAG。 */
export const createAdminKnowledgeSubmission = (input: {
  title: string; sourceName?: string; sourceAuthor?: string; sourceUrl?: string; fileName?: string
  documentType?: 'TEXT' | 'PDF'; petType: string; category: string; content: string
  sourcePublishedAt?: string; expiresAt?: string
}) => apiRequest<KnowledgeSubmission>('/api/v1/admin/knowledge-submissions/uploads', {
  method: 'POST', body: JSON.stringify(input),
})

export function listKnowledgeSubmissions(params: {
  status?: string; riskLevel?: string; sourceType?: string; page?: number; size?: number
} = {}): Promise<KnowledgeSubmissionPage> {
  const query = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => { if (value !== undefined && value !== '') query.set(key, String(value)) })
  return apiRequest(`/api/v1/admin/knowledge-submissions?${query.toString()}`)
}

export const getKnowledgeSubmissionStats = () =>
  apiRequest<KnowledgeSubmissionStats>('/api/v1/admin/knowledge-submissions/stats')

export const getAdminKnowledgeSubmission = (id: string) =>
  apiRequest<KnowledgeSubmission>(`/api/v1/admin/knowledge-submissions/${id}`)

export const reviewKnowledgeSubmission = (id: string, input: {
  action: 'APPROVE' | 'REJECT'; expectedVersion: number; trustLevel?: string; reason?: string
}) => apiRequest<KnowledgeSubmission>(`/api/v1/admin/knowledge-submissions/${id}/review`, {
  method: 'POST', body: JSON.stringify(input),
})

/** 先提取并预览 PDF，不在确认前写入知识库。 */
export function extractPdf(
  fileName: string,
  contentBase64: string,
): Promise<PdfExtractResult> {
  return apiRequest<PdfExtractResult>('/api/v1/knowledge/documents/pdf/extract', {
    method: 'POST',
    body: JSON.stringify({ fileName, contentBase64 }),
  })
}

/** 使用当前专业模型重建全部文档分块，清除旧模型向量。 */
export function reindexKnowledge(): Promise<KnowledgeReindexResult> {
  return apiRequest<KnowledgeReindexResult>('/api/v1/knowledge/documents/reindex', {
    method: 'POST',
  })
}

/** 查询最近导入的知识文档。 */
export function listKnowledgeDocuments(limit = 50): Promise<KnowledgeDocument[]> {
  return apiRequest<KnowledgeDocument[]>(
    `/api/v1/knowledge/documents?limit=${limit}`,
  )
}

/** 管理员独立测试真实 RAG 链路，不会创建普通用户会话。 */
export function testKnowledgeAnswer(request: {
  question: string
  petType?: string
  category?: string
}): Promise<ChatResponse> {
  return apiRequest<ChatResponse>('/api/v1/knowledge/documents/test-answer', {
    method: 'POST',
    body: JSON.stringify(request),
  })
}
