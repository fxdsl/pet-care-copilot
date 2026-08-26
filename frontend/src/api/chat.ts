/** Spring Boot 返回的知识来源，score 为问题与分块的余弦相似度。 */
export interface SourceReference {
  title: string
  url?: string
  chunkId: string
  score: number
  fileName?: string
  pageStart?: number
  pageEnd?: number
}

import { apiRequest, businessApiBaseUrl, ApiRequestError } from './http'
import { accessToken } from './session'

/** Agent 对外展示的脱敏节点摘要，不包含隐藏思维过程和完整工具观察。 */
export interface AgentStep {
  sequence: number
  node: 'reason' | 'tool' | 'finish' | 'guard' | 'fallback'
  action: string
  toolName?: string
  status: 'SUCCESS' | 'ERROR' | 'BLOCKED'
  summary: string
}

/** 第六周完整问答响应，conversationId 会在首次提问时由后端创建。 */
export interface ChatResponse {
  answer: string
  conversationId: string | null
  sources: SourceReference[]
  stage: string
  modelName?: string
  routingReason: string
  maxScore?: number
  agentSteps: AgentStep[]
  terminationReason: string
  toolCallCount: number
}

/** 问答过滤条件和多轮会话标识。 */
export interface ChatRequest {
  question: string
  conversationId?: string
  petProfileId?: string
  petType?: string
  category?: string
}

export interface ChatStreamRequest extends ChatRequest { requestId: string }
export type ChatStreamEventName = 'stage' | 'token' | 'heartbeat' | 'result' | 'done' | 'error' | 'cancelled'
export interface ChatStreamEvent { id: number; event: ChatStreamEventName; data: unknown; reconnected: boolean }

/** 发送问题，由 Java 自动管理会话、持久化消息并返回带来源答案。 */
export function sendQuestion(request: ChatRequest): Promise<ChatResponse> {
  return apiRequest<ChatResponse>('/api/v1/chat/preview', {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

/**
 * 使用 fetch 读取 POST SSE；断线后携带同一 requestId 与 Last-Event-ID 最多重连三次。
 * 事件重放由 Java 管理，重连不会重复写入会话消息。
 */
export async function streamQuestion(
  request: ChatStreamRequest,
  onEvent: (event: ChatStreamEvent) => void,
  signal?: AbortSignal,
): Promise<void> {
  let lastEventId = 0
  let attempts = 0
  let finished = false
  while (!finished && attempts <= 3) {
    const reconnected = attempts > 0
    let response: Response
    try {
      response = await fetch(`${businessApiBaseUrl}/api/v1/chat/stream`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${accessToken()}`,
          ...(lastEventId ? { 'Last-Event-ID': String(lastEventId) } : {}),
        },
        body: JSON.stringify(request),
        signal,
      })
    } catch (cause) {
      if (signal?.aborted) throw cause
      attempts += 1
      if (attempts > 3) throw new ApiRequestError('STREAM_DISCONNECTED', 0, '流式连接已断开，请点击重试')
      onEvent({ id: lastEventId, event: 'stage', data: { stage: 'RECONNECTING', message: '连接中断，正在恢复…' }, reconnected: true })
      await new Promise((resolve) => window.setTimeout(resolve, attempts * 800))
      continue
    }
    if (!response.ok || !response.body) {
      const error = await response.json().catch(() => ({})) as { message?: string }
      throw new ApiRequestError('STREAM_HTTP_ERROR', response.status, error.message ?? `流式请求失败（HTTP ${response.status}）`)
    }
    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    try {
      while (true) {
        const { done, value } = await reader.read()
        buffer += decoder.decode(value, { stream: !done }).replace(/\r\n/g, '\n')
        let boundary = buffer.indexOf('\n\n')
        while (boundary >= 0) {
          const frame = buffer.slice(0, boundary)
          buffer = buffer.slice(boundary + 2)
          const parsed = parseSseFrame(frame)
          if (parsed) {
            lastEventId = Math.max(lastEventId, parsed.id)
            onEvent({ ...parsed, reconnected })
            if (parsed.event === 'done' || parsed.event === 'cancelled') finished = true
          }
          boundary = buffer.indexOf('\n\n')
        }
        if (done) break
      }
    } finally {
      reader.releaseLock()
    }
    if (!finished) {
      attempts += 1
      if (attempts > 3) throw new ApiRequestError('STREAM_DISCONNECTED', 0, '回答尚未完成但连接已关闭，请点击重试')
    }
  }
}

export const cancelQuestionStream = (requestId: string) => apiRequest<void>(
  `/api/v1/chat/streams/${requestId}`, { method: 'DELETE' },
)

function parseSseFrame(frame: string): Omit<ChatStreamEvent, 'reconnected'> | undefined {
  let id = 0
  let event: ChatStreamEventName = 'stage'
  const data: string[] = []
  frame.split('\n').forEach((line) => {
    if (line.startsWith('id:')) id = Number(line.slice(3).trim()) || 0
    else if (line.startsWith('event:')) event = line.slice(6).trim() as ChatStreamEventName
    else if (line.startsWith('data:')) data.push(line.slice(5).trim())
  })
  if (!data.length) return undefined
  const raw = data.join('\n')
  let parsed: unknown = raw
  try {
    parsed = JSON.parse(raw)
    if (typeof parsed === 'string') {
      try { parsed = JSON.parse(parsed) } catch { /* token 文本允许保持字符串。 */ }
    }
  } catch { /* 非 JSON 事件按纯文本显示。 */ }
  return { id, event, data: parsed }
}
