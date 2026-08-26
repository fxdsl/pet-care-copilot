import { apiRequest } from './http'

/** 会话列表项。 */
export interface Conversation {
  id: string
  title: string
  status: string
  createdAt: string
  updatedAt: string
}

/** 已持久化的单条会话消息。 */
export interface ConversationMessage {
  id: string
  conversationId: string
  role: 'USER' | 'ASSISTANT' | 'SYSTEM' | 'TOOL'
  content: string
  modelName?: string
  tokenCount?: number
  createdAt: string
}

/** 查询最近会话，用于页面左侧历史列表。 */
export function listConversations(limit = 30): Promise<Conversation[]> {
  return apiRequest<Conversation[]>(`/api/v1/conversations?limit=${limit}`)
}

/** 加载一个会话的完整消息历史。 */
export function listConversationMessages(
  conversationId: string,
): Promise<ConversationMessage[]> {
  return apiRequest<ConversationMessage[]>(
    `/api/v1/conversations/${encodeURIComponent(conversationId)}/messages`,
  )
}
