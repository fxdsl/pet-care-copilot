<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'

import {
  cancelQuestionStream,
  streamQuestion,
  type AgentStep,
  type ChatRequest,
  type ChatResponse,
  type SourceReference,
} from '../api/chat'
import { listConversationMessages, listConversations, type Conversation } from '../api/conversation'
import { ApiRequestError } from '../api/http'
import { listPetProfiles, type PetProfile } from '../api/pet-profile'

interface Message {
  role: 'user' | 'assistant'
  content: string
  sources?: SourceReference[]
  stage?: string
  modelName?: string
  routingReason?: string
  maxScore?: number
  agentSteps?: AgentStep[]
  terminationReason?: string
  toolCallCount?: number
  streaming?: boolean
  streamStage?: string
}

const route = useRoute()
const conversations = ref<Conversation[]>([])
const messages = ref<Message[]>([])
const profiles = ref<PetProfile[]>([])
const conversationId = ref<string>()
const profileId = ref(typeof route.query.profile === 'string' ? route.query.profile : '')
const petType = ref('')
const category = ref('')
const question = ref('幼猫一天应该喂几次？')
const loading = ref(false)
const historyLoading = ref(false)
const error = ref('')
const mobileHistoryOpen = ref(false)
const messageStream = ref<HTMLElement>()
const canSend = computed(() => question.value.trim().length > 0 && !loading.value)
const activeRequestId = ref('')
const streamStatus = ref('')
const lastFailedRequest = ref<ChatRequest>()
let streamController: AbortController | undefined

/** 公网 HTTP 不提供 crypto.randomUUID；使用仍可用的 getRandomValues 生成 UUID v4。 */
function createRequestId(): string {
  if (typeof window.crypto?.randomUUID === 'function') return window.crypto.randomUUID()
  if (typeof window.crypto?.getRandomValues !== 'function') {
    return `legacy-${Date.now()}-${Math.random().toString(16).slice(2)}`.slice(0, 36)
  }
  const bytes = window.crypto.getRandomValues(new Uint8Array(16))
  bytes[6] = (bytes[6]! & 0x0f) | 0x40
  bytes[8] = (bytes[8]! & 0x3f) | 0x80
  const hex = Array.from(bytes, (value) => value.toString(16).padStart(2, '0'))
  return [
    hex.slice(0, 4).join(''),
    hex.slice(4, 6).join(''),
    hex.slice(6, 8).join(''),
    hex.slice(8, 10).join(''),
    hex.slice(10, 16).join(''),
  ].join('-')
}

/** 新消息和历史加载后只滚动消息容器，不推动浏览器整个页面。 */
async function scrollMessagesToEnd(): Promise<void> {
  await nextTick()
  messageStream.value?.scrollTo({ top: messageStream.value.scrollHeight, behavior: 'smooth' })
}

async function initialize(): Promise<void> {
  const [conversationResult, profileResult] = await Promise.allSettled([listConversations(), listPetProfiles()])
  if (conversationResult.status === 'fulfilled') conversations.value = conversationResult.value
  if (profileResult.status === 'fulfilled') profiles.value = profileResult.value
}

/** 首次发送由 Java 创建会话，成功后保存返回的 conversationId。 */
async function submit(): Promise<void> {
  const content = question.value.trim()
  if (!content || loading.value) return
  const request: ChatRequest = {
    question: content, conversationId: conversationId.value, petProfileId: profileId.value || undefined,
    petType: profileId.value ? undefined : petType.value || undefined, category: category.value || undefined,
  }
  messages.value.push({ role: 'user', content })
  const assistant: Message = { role: 'assistant', content: '', streaming: true, streamStage: '正在建立安全流…' }
  messages.value.push(assistant)
  void scrollMessagesToEnd()
  question.value = ''
  loading.value = true
  streamStatus.value = '正在连接 Agent…'
  error.value = ''
  lastFailedRequest.value = undefined
  streamController = new AbortController()
  try {
    activeRequestId.value = createRequestId()
    await streamQuestion({ ...request, requestId: activeRequestId.value }, (event) => {
      const data = asRecord(event.data)
      if (event.event === 'stage' || event.event === 'heartbeat') {
        const text = String(data.message ?? 'Agent 正在处理')
        assistant.streamStage = event.reconnected ? `已重连 · ${text}` : text
        streamStatus.value = assistant.streamStage
      } else if (event.event === 'token') {
        assistant.content += String(data.text ?? '')
        assistant.streamStage = '正在生成回答…'
        streamStatus.value = assistant.streamStage
        void scrollMessagesToEnd()
      } else if (event.event === 'result') {
        applyResult(assistant, data as unknown as ChatResponse)
        conversationId.value = (data.conversationId as string | null) ?? undefined
      } else if (event.event === 'error') {
        error.value = String(data.message ?? '流式回答失败')
      } else if (event.event === 'cancelled') {
        assistant.streamStage = '已停止生成'
      }
    }, streamController.signal)
    assistant.streaming = false
    assistant.streamStage = '回答已完成'
    streamStatus.value = ''
    conversations.value = await listConversations()
  } catch (cause) {
    assistant.streaming = false
    if (!(cause instanceof DOMException && cause.name === 'AbortError')) {
      error.value = readable(cause)
      assistant.streamStage = '生成失败'
      lastFailedRequest.value = request
    }
  } finally {
    loading.value = false
    activeRequestId.value = ''
    streamController = undefined
  }
}

/** 用户停止只终止当前 requestId，已保存的历史轮次不受影响。 */
async function stopGeneration(): Promise<void> {
  const requestId = activeRequestId.value
  if (!requestId) return
  try { await cancelQuestionStream(requestId) } catch { /* 连接已断时本地中止仍然有效。 */ }
  streamController?.abort()
  const active = [...messages.value].reverse().find((message) => message.streaming)
  if (active) { active.streaming = false; active.streamStage = '已停止生成' }
  streamStatus.value = '已停止生成'
  loading.value = false
}

async function retryLast(): Promise<void> {
  if (!lastFailedRequest.value || loading.value) return
  question.value = lastFailedRequest.value.question
  // 去掉失败轮次，避免重试后同一问题在界面显示两遍。
  if (messages.value[messages.value.length - 1]?.role === 'assistant') messages.value.pop()
  if (messages.value[messages.value.length - 1]?.role === 'user') messages.value.pop()
  await submit()
}

function applyResult(message: Message, response: ChatResponse): void {
  message.content = response.answer
  message.sources = response.sources
  message.stage = response.stage
  message.modelName = response.modelName
  message.routingReason = response.routingReason
  message.maxScore = response.maxScore
  message.agentSteps = response.agentSteps
  message.terminationReason = response.terminationReason
  message.toolCallCount = response.toolCallCount
}

function asRecord(value: unknown): Record<string, unknown> {
  return typeof value === 'object' && value !== null ? value as Record<string, unknown> : { message: String(value ?? '') }
}

async function openConversation(id: string): Promise<void> {
  historyLoading.value = true
  error.value = ''
  try {
    const history = await listConversationMessages(id)
    conversationId.value = id
    messages.value = history.filter((item) => item.role === 'USER' || item.role === 'ASSISTANT').map((item) => ({
      role: item.role === 'USER' ? 'user' : 'assistant', content: item.content, modelName: item.modelName,
    }))
    void scrollMessagesToEnd()
    mobileHistoryOpen.value = false
  } catch (cause) { error.value = readable(cause) }
  finally { historyLoading.value = false }
}

function newConversation(): void {
  if (loading.value) void stopGeneration()
  conversationId.value = undefined
  messages.value = []
  error.value = ''
}

function readable(cause: unknown): string {
  return cause instanceof ApiRequestError ? cause.message : '问答服务暂时不可用'
}

onMounted(initialize)
</script>

<template>
  <section class="chat-page">
    <aside class="conversation-sidebar" :class="{ mobileOpen: mobileHistoryOpen }">
      <div class="conversation-sidebar-heading"><div><p class="eyebrow">CONVERSATIONS</p><h2>会话历史</h2></div><button class="round-button" @click="newConversation">＋</button></div>
      <div class="conversation-list">
        <button v-for="item in conversations" :key="item.id" class="conversation-link" :class="{ active: item.id === conversationId }" @click="openConversation(item.id)"><strong>{{ item.title }}</strong><small>{{ new Date(item.updatedAt).toLocaleString('zh-CN') }}</small></button>
        <p v-if="conversations.length === 0" class="muted">第一次提问后会自动创建会话。</p>
      </div>
    </aside>
    <main class="chat-surface">
      <header class="chat-heading"><div><button class="mobile-history-button" @click="mobileHistoryOpen = !mobileHistoryOpen">会话</button><p class="eyebrow">PET CARE AI</p><h1>智能养宠问答</h1></div><span class="online-pill"><i /> AI 服务</span></header>
      <div class="chat-filters"><el-select v-model="profileId" placeholder="不使用宠物档案" clearable><el-option v-for="profile in profiles" :key="profile.id" :label="`${profile.name} · ${profile.petType}`" :value="profile.id" /></el-select><el-select v-if="!profileId" v-model="petType" placeholder="全部宠物" clearable><el-option label="猫" value="CAT" /><el-option label="狗" value="DOG" /><el-option label="其他" value="OTHER" /></el-select><el-select v-model="category" placeholder="全部知识" clearable><el-option label="喂养" value="FEEDING" /><el-option label="健康" value="HEALTH" /><el-option label="疫苗" value="VACCINE" /><el-option label="行为" value="BEHAVIOR" /><el-option label="护理" value="GROOMING" /></el-select></div>
      <section ref="messageStream" class="message-stream" aria-live="polite">
        <div v-if="messages.length === 0" class="chat-welcome"><span>宠</span><h2>你好，今天想了解什么？</h2><p>我会优先检索经过管理的知识资料，并清楚标记回答来源。</p><div><button @click="question = '幼猫一天应该喂几次？'">幼猫喂养频率</button><button @click="question = '小狗应该打什么疫苗？'">幼犬疫苗计划</button><button @click="question = '猫咪突然不吃饭怎么办？'">食欲异常观察</button></div></div>
        <article v-for="(message, index) in messages" :key="index" class="chat-message" :class="message.role"><span class="message-avatar">{{ message.role === 'user' ? '我' : '宠' }}</span><div><small>{{ message.role === 'user' ? '你' : '宠里个宠' }}</small><p v-if="message.content">{{ message.content }}<i v-if="message.streaming" class="stream-cursor" /></p><p v-else-if="message.streaming" class="stream-stage-copy"><i class="stream-pulse" />{{ message.streamStage }}</p><div v-if="message.role === 'assistant'" class="answer-meta"><span v-if="message.streaming || message.streamStage" class="stream-stage-pill">{{ message.streamStage }}</span><span v-if="message.modelName">{{ message.modelName }}</span><span v-if="message.routingReason">{{ message.routingReason }}</span></div><ul v-if="message.sources?.length" class="answer-sources"><li v-for="source in message.sources" :key="source.chunkId"><a v-if="source.url" :href="source.url" target="_blank" rel="noreferrer">{{ source.title }}</a><strong v-else>{{ source.title }}</strong><small>相关度 {{ Math.round(source.score * 100) }}%<template v-if="source.pageStart"> · 第 {{ source.pageStart }} 页</template></small></li></ul><details v-if="message.agentSteps?.length" class="agent-trace"><summary>查看脱敏执行轨迹 · {{ message.toolCallCount }} 次工具</summary><ol><li v-for="step in message.agentSteps" :key="step.sequence"><strong>{{ step.node }} / {{ step.action }}</strong><span>{{ step.summary }}</span></li></ol></details></div></article>
        <article v-if="historyLoading" class="chat-message assistant typing"><span class="message-avatar">宠</span><div><small>宠里个宠</small><p>正在从 MySQL 加载历史消息…<i>•••</i></p></div></article>
      </section>
      <div v-if="error" class="inline-error chat-error" role="alert"><span>{{ error }}</span><button v-if="lastFailedRequest" @click="retryLast">重试上一次</button></div>
      <form class="chat-composer" @submit.prevent="submit"><div v-if="loading" class="stream-live-status"><i />{{ streamStatus || '正在生成回答…' }}</div><el-input v-model="question" type="textarea" :autosize="{ minRows: 2, maxRows: 5 }" maxlength="2000" placeholder="描述宠物类型、年龄、体重和持续时间，会得到更准确的参考…" @keydown.ctrl.enter.prevent="submit" /><button v-if="loading" class="send-button stop-button" type="button" @click="stopGeneration">■ 停止</button><button v-else class="send-button" type="submit" :disabled="!canSend">发送 ↗</button><small>Ctrl + Enter 发送 · 支持断线恢复 · 回答不能代替专业兽医诊断<template v-if="conversationId"> · 会话 {{ conversationId.slice(0, 8) }}</template></small></form>
    </main>
  </section>
</template>
