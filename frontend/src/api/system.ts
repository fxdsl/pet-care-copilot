import { apiRequest } from './http'

export interface SystemHealth {
  businessService: 'UP' | 'DOWN'
  aiService: 'UP' | 'DOWN'
  database: 'UP' | 'DOWN'
  redis: 'UP' | 'DOWN'
  rabbitmq: 'UP' | 'DOWN'
  minio: 'UP' | 'DOWN'
  opensearch: 'UP' | 'DOWN'
}

/** 聚合检查 Java、FastAPI、MySQL、Redis、RabbitMQ 和 MinIO。 */
export const getSystemHealth = () => apiRequest<SystemHealth>('/api/v1/system/health')

export interface SearchIndexJob {
  id: string
  indexName: string
  indexVersion: number
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED'
  totalCount: number
  indexedCount: number
  failedCount: number
  errorMessage: string | null
  startedAt: string | null
  completedAt: string | null
  createdAt: string
  updatedAt: string
}

/** 全量重建异步执行，前端通过任务 ID 轮询事实状态。 */
export const rebuildSearchIndex = () => apiRequest<SearchIndexJob>('/api/v1/admin/search/rebuild', { method: 'POST' })
export const getSearchIndexJob = (jobId: string) => apiRequest<SearchIndexJob>(`/api/v1/admin/search/rebuild/${jobId}`)
