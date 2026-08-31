<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'

import { getPublicUserProfile, type PublicUserProfile } from '../api/community'
import { ApiRequestError } from '../api/http'
import {
  listDirectConversations,
  listDirectMessages,
  listNotifications,
  markAllNotificationsRead,
  markNotificationRead,
  sendDirectMessage,
  type DirectConversation,
  type DirectMessage,
  type NotificationItem,
  type NotificationType,
} from '../api/message'
import PageState from '../components/PageState.vue'
import { useAuthStore } from '../stores/auth'
import { useMessageStore, type RealtimeEvent } from '../stores/message'
import { createRequestId } from '../utils/requestId'

type CenterMode = 'DIRECT' | 'NOTIFICATION'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const messageStore = useMessageStore()
const mode = ref<CenterMode>('DIRECT')
const notificationType = ref<NotificationType | ''>('')
const notifications = ref<NotificationItem[]>([])
const conversations = ref<DirectConversation[]>([])
const directMessages = ref<DirectMessage[]>([])
const selectedConversationId = ref('')
const draft = ref('')
const loading = ref(true)
const contentLoading = ref(false)
const error = ref('')
const mobileConversationOpen = ref(false)
const messageScroller = ref<HTMLElement>()
const directTarget = ref<PublicUserProfile>()
let unsubscribe: (() => void) | undefined

const selectedConversation = computed(() => conversations.value.find((item) => item.id === selectedConversationId.value))
const recipientId = computed(() => selectedConversation.value?.otherUserId ?? directTarget.value?.id ?? '')
const categoryOptions: Array<{ value: NotificationType | ''; label: string }> = [
  { value: '', label: '全部通知' }, { value: 'COMMENT', label: '评论' }, { value: 'LIKE', label: '点赞' },
  { value: 'FOLLOW', label: '关注' }, { value: 'MODERATION', label: '审核' }, { value: 'SYSTEM', label: '系统' },
]

async function initialize(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    await Promise.all([loadConversations(), loadNotifications()])
    const targetId = typeof route.query.user === 'string' ? route.query.user : ''
    if (targetId && targetId !== auth.user?.id) {
      directTarget.value = await getPublicUserProfile(targetId)
      const existing = conversations.value.find((item) => item.otherUserId === targetId)
      if (existing) await openConversation(existing)
    } else if (conversations.value.length) {
      await openConversation(conversations.value[0])
    }
  } catch (cause) { error.value = readable(cause) }
  finally { loading.value = false }
}

async function loadNotifications(): Promise<void> {
  notifications.value = (await listNotifications(notificationType.value || undefined)).items
}

async function changeNotificationType(): Promise<void> {
  contentLoading.value = true
  try { await loadNotifications() } finally { contentLoading.value = false }
}

async function loadConversations(): Promise<void> {
  conversations.value = (await listDirectConversations()).items
}

async function openConversation(conversation: DirectConversation): Promise<void> {
  mode.value = 'DIRECT'
  selectedConversationId.value = conversation.id
  directTarget.value = undefined
  mobileConversationOpen.value = true
  contentLoading.value = true
  try {
    directMessages.value = (await listDirectMessages(conversation.id)).items
    await Promise.all([loadConversations(), messageStore.refreshUnread()])
    await scrollToEnd()
  } catch (cause) { ElMessage.error(readable(cause)) }
  finally { contentLoading.value = false }
}

async function send(): Promise<void> {
  const content = draft.value.trim()
  if (!content || !recipientId.value) return
  draft.value = ''
  try {
    const created = await sendDirectMessage({
      recipientId: recipientId.value, clientMessageId: createRequestId(), content,
    })
    if (!selectedConversationId.value) {
      await loadConversations()
      selectedConversationId.value = created.conversationId
      directTarget.value = undefined
    }
    directMessages.value.push(created)
    await loadConversations()
    await scrollToEnd()
  } catch (cause) {
    draft.value = content
    ElMessage.error(readable(cause))
  }
}

async function openNotification(item: NotificationItem): Promise<void> {
  if (!item.read) await markNotificationRead(item.id)
  item.read = true
  await messageStore.refreshUnread()
  if (item.actorId && item.targetType === 'USER') await router.push(`/app/users/${item.actorId}`)
  else if (item.targetType === 'POST' && item.targetId) await router.push({ path: '/app/community', query: { post: item.targetId } })
}

async function markAll(): Promise<void> {
  await markAllNotificationsRead(notificationType.value || undefined)
  notifications.value.forEach((item) => { item.read = true })
  await messageStore.refreshUnread()
  ElMessage.success('当前分类已全部标为已读')
}

async function handleRealtime(event: RealtimeEvent): Promise<void> {
  if (event.type === 'NOTIFICATION_CREATED') await loadNotifications()
  if (event.type === 'DIRECT_MESSAGE_CREATED') {
    await loadConversations()
    if (event.payload.conversationId === selectedConversationId.value) {
      directMessages.value = (await listDirectMessages(selectedConversationId.value)).items
      await scrollToEnd()
    }
  }
}

async function scrollToEnd(): Promise<void> {
  await nextTick()
  messageScroller.value?.scrollTo({ top: messageScroller.value.scrollHeight, behavior: 'smooth' })
}

function readable(cause: unknown): string {
  return cause instanceof ApiRequestError ? cause.message : '消息中心暂时无法加载'
}

onMounted(() => {
  void initialize()
  unsubscribe = messageStore.subscribe((event) => { void handleRealtime(event) })
})
onUnmounted(() => unsubscribe?.())
</script>

<template>
  <section class="message-center-page">
    <aside class="message-center-nav surface-card">
      <div><p class="eyebrow">INBOX</p><h1>消息中心</h1><small :class="{ online: messageStore.connected }">{{ messageStore.connected ? '● 实时连接正常' : messageStore.reconnecting ? '○ 正在重新连接' : '○ 离线消息可继续查看' }}</small></div>
      <nav>
        <button :class="{ active: mode === 'DIRECT' }" @click="mode = 'DIRECT'"><span>✉</span><div><strong>私信</strong><small>宠友之间的交流</small></div><b v-if="messageStore.unread.directMessages">{{ messageStore.unread.directMessages }}</b></button>
        <button :class="{ active: mode === 'NOTIFICATION' }" @click="mode = 'NOTIFICATION'; mobileConversationOpen = true"><span>♢</span><div><strong>互动通知</strong><small>评论、点赞与关注</small></div><b v-if="messageStore.unread.total - messageStore.unread.directMessages">{{ messageStore.unread.total - messageStore.unread.directMessages }}</b></button>
      </nav>
    </aside>

    <PageState v-if="loading" type="loading" message="正在从 MySQL 加载消息…" />
    <PageState v-else-if="error" type="error" :message="error" @retry="initialize" />

    <template v-else-if="mode === 'DIRECT'">
      <aside class="direct-conversation-list surface-card" :class="{ mobileHidden: mobileConversationOpen }">
        <header><div><p class="eyebrow">DIRECT</p><h2>私信会话</h2></div><span>{{ conversations.length }}</span></header>
        <button v-if="directTarget && !selectedConversationId" class="direct-conversation active"><span class="avatar">{{ directTarget.displayName.slice(0, 1) }}</span><div><strong>{{ directTarget.displayName }}</strong><small>开始一段新对话</small></div></button>
        <button v-for="item in conversations" :key="item.id" class="direct-conversation" :class="{ active: item.id === selectedConversationId }" @click="openConversation(item)"><span class="avatar">{{ item.otherDisplayName.slice(0, 1) }}</span><div><strong>{{ item.otherDisplayName }}</strong><small>{{ item.lastMessageContent || '还没有消息' }}</small></div><b v-if="item.unreadCount">{{ item.unreadCount }}</b></button>
        <p v-if="!conversations.length && !directTarget" class="message-empty-copy">从宠友主页点击“发私信”，即可开始交流。</p>
      </aside>

      <main class="direct-message-panel surface-card" :class="{ mobileOpen: mobileConversationOpen }">
        <header v-if="selectedConversation || directTarget"><button class="mobile-back" @click="mobileConversationOpen = false">‹ 会话</button><RouterLink :to="`/app/users/${recipientId}`" class="direct-user-heading"><span class="avatar">{{ (selectedConversation?.otherDisplayName || directTarget?.displayName || '').slice(0, 1) }}</span><div><strong>{{ selectedConversation?.otherDisplayName || directTarget?.displayName }}</strong><small>@{{ selectedConversation?.otherUsername || directTarget?.username }}</small></div></RouterLink><span :class="{ online: messageStore.connected }">{{ messageStore.connected ? '实时' : '离线可达' }}</span></header>
        <div v-if="selectedConversation || directTarget" ref="messageScroller" class="direct-message-stream">
          <PageState v-if="contentLoading" type="loading" message="正在加载会话…" />
          <p v-else-if="!directMessages.length" class="message-date-divider">尊重彼此，友善交流养宠经验</p>
          <article v-for="item in directMessages" :key="item.id" class="direct-bubble" :class="{ mine: item.senderId === auth.user?.id }"><span v-if="item.senderId !== auth.user?.id" class="avatar avatar-small">{{ item.senderDisplayName.slice(0, 1) }}</span><div><p>{{ item.content }}</p><small>{{ new Date(item.createdAt).toLocaleString('zh-CN') }}{{ item.senderId === auth.user?.id ? (item.read ? ' · 已读' : ' · 已送达') : '' }}</small></div></article>
        </div>
        <div v-if="selectedConversation || directTarget" class="direct-composer"><el-input v-model="draft" type="textarea" :rows="3" maxlength="2000" placeholder="输入私信，Ctrl + Enter 发送" @keydown.ctrl.enter.prevent="send" /><button class="button button-primary" :disabled="!draft.trim()" @click="send">发送</button></div>
        <div v-else class="message-panel-welcome"><span>✉</span><h2>选择一段会话</h2><p>在线时通过 WebSocket 即时提醒，离线消息始终保存在 MySQL。</p></div>
      </main>
    </template>

    <main v-else class="notification-panel surface-card">
      <header><div><p class="eyebrow">NOTIFICATIONS</p><h2>互动通知</h2></div><button class="button button-secondary" @click="markAll">全部已读</button></header>
      <nav class="notification-tabs"><button v-for="option in categoryOptions" :key="option.value" :class="{ active: notificationType === option.value }" @click="notificationType = option.value; changeNotificationType()">{{ option.label }}<b v-if="option.value && messageStore.unread.notifications[option.value]">{{ messageStore.unread.notifications[option.value] }}</b></button></nav>
      <PageState v-if="contentLoading" type="loading" message="正在加载通知…" />
      <PageState v-else-if="!notifications.length" type="empty" message="这个分类暂时没有通知。" />
      <div v-else class="notification-list"><button v-for="item in notifications" :key="item.id" class="notification-item" :class="{ unread: !item.read }" @click="openNotification(item)"><span class="avatar">{{ item.actorDisplayName?.slice(0, 1) || '宠' }}</span><div><strong>{{ item.title }}</strong><p>{{ item.content }}</p><small>{{ item.actorDisplayName || '系统' }} · {{ new Date(item.createdAt).toLocaleString('zh-CN') }}</small></div><i v-if="!item.read" /></button></div>
    </main>
  </section>
</template>
