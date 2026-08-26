import { apiRequest } from './http'

export type SearchType = 'ALL' | 'POST' | 'KNOWLEDGE' | 'USER' | 'TOPIC'
export type SearchSort = 'RELEVANCE' | 'LATEST'
export type SearchDateRange = 'ALL' | 'LAST_7_DAYS' | 'LAST_30_DAYS' | 'LAST_YEAR'

export interface SearchResultItem {
  id: string
  type: Exclude<SearchType, 'ALL'>
  title: string
  snippet: string
  authorName: string | null
  avatarUrl: string | null
  sourceUrl: string | null
  routePath: string
  petType: string | null
  category: string | null
  trustLevel: string | null
  publishedAt: string | null
  score: number
  matchedFields: string[]
}

export interface SearchGroup { type: Exclude<SearchType, 'ALL'>; total: number; items: SearchResultItem[] }
export interface UnifiedSearchResult {
  query: string
  type: SearchType
  page: number
  size: number
  total: number
  backend: 'OPENSEARCH' | 'MYSQL'
  degraded: boolean
  indexVersion: number
  groups: SearchGroup[]
}
export interface SearchSuggestion { text: string; source: 'HISTORY' | 'TRENDING' | 'PUBLIC_CONTENT' }
export interface SearchHistory {
  id: string
  query: string
  filtersJson: string
  resultCount: number
  searchCount: number
  lastSearchedAt: string
}
export interface SearchTrend { query: string; score: number }

export interface SearchParams {
  query: string
  type?: SearchType
  petType?: string
  category?: string
  trustLevel?: string
  dateRange?: SearchDateRange
  sort?: SearchSort
  page?: number
  size?: number
}

/** 所有筛选进入 URL 查询参数，页面刷新和分享链接都能恢复相同条件。 */
export function unifiedSearch(params: SearchParams): Promise<UnifiedSearchResult> {
  const query = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== '') query.set(key, String(value))
  })
  return apiRequest(`/api/v1/search?${query.toString()}`)
}

export function searchSuggestions(queryText: string, limit = 8): Promise<SearchSuggestion[]> {
  const query = new URLSearchParams({ query: queryText, limit: String(limit) })
  return apiRequest(`/api/v1/search/suggestions?${query.toString()}`)
}

export const listSearchHistory = (limit = 20) => apiRequest<SearchHistory[]>(`/api/v1/search/history?limit=${limit}`)
export const clearSearchHistory = () => apiRequest<void>('/api/v1/search/history', { method: 'DELETE' })
export const deleteSearchHistory = (id: string) => apiRequest<void>(`/api/v1/search/history/${id}`, { method: 'DELETE' })
export const listSearchTrending = (limit = 10) => apiRequest<SearchTrend[]>(`/api/v1/search/trending?limit=${limit}`)
