import { defineStore } from 'pinia'
import { ref } from 'vue'

import { businessApiBaseUrl } from '../api/http'
import { getMessageUnread, type MessageUnread } from '../api/message'
import { accessToken } from '../api/session'

export interface RealtimeEvent {
  eventId: string
  recipientId: string
  type: 'NOTIFICATION_CREATED' | 'DIRECT_MESSAGE_CREATED'
  payload: Record<string, string>
  createdAt: string
}

/** 全站未读角标与 WebSocket 生命周期；业务正文仍通过 REST 从 MySQL 获取。 */
export const useMessageStore = defineStore('message', () => {
  const unread = ref<MessageUnread>({ total: 0, directMessages: 0, notifications: {
    COMMENT: 0, LIKE: 0, FOLLOW: 0, MODERATION: 0, SYSTEM: 0,
  } })
  const connected = ref(false)
  const reconnecting = ref(false)
  const listeners = new Set<(event: RealtimeEvent) => void>()
  let socket: WebSocket | undefined
  let retryTimer: number | undefined
  let heartbeatTimer: number | undefined
  let retryCount = 0
  let stopped = false

  async function refreshUnread(): Promise<void> {
    try { unread.value = await getMessageUnread() } catch { /* 导航角标失败不阻塞当前页面。 */ }
  }

  function connect(): void {
    if (!accessToken() || socket?.readyState === WebSocket.OPEN || socket?.readyState === WebSocket.CONNECTING) return
    stopped = false
    const url = `${businessApiBaseUrl.replace(/^http/, 'ws')}/ws/realtime`
    socket = new WebSocket(url)
    socket.onopen = () => {
      socket?.send(JSON.stringify({ type: 'AUTH', token: accessToken() }))
    }
    socket.onmessage = (message) => {
      const frame = JSON.parse(String(message.data)) as RealtimeEvent | { type: string }
      if (frame.type === 'AUTHENTICATED') {
        connected.value = true
        reconnecting.value = false
        retryCount = 0
        window.clearInterval(heartbeatTimer)
        heartbeatTimer = window.setInterval(() => socket?.send(JSON.stringify({ type: 'PING' })), 25_000)
        return
      }
      if (frame.type === 'NOTIFICATION_CREATED' || frame.type === 'DIRECT_MESSAGE_CREATED') {
        void refreshUnread()
        listeners.forEach((listener) => listener(frame as RealtimeEvent))
      }
    }
    socket.onclose = () => {
      connected.value = false
      window.clearInterval(heartbeatTimer)
      if (!stopped) scheduleReconnect()
    }
    socket.onerror = () => socket?.close()
  }

  function scheduleReconnect(): void {
    reconnecting.value = true
    window.clearTimeout(retryTimer)
    const delay = [1_000, 2_000, 5_000, 10_000, 20_000][Math.min(retryCount++, 4)]
    retryTimer = window.setTimeout(connect, delay)
  }

  function disconnect(): void {
    stopped = true
    connected.value = false
    reconnecting.value = false
    window.clearTimeout(retryTimer)
    window.clearInterval(heartbeatTimer)
    socket?.close()
    socket = undefined
  }

  function subscribe(listener: (event: RealtimeEvent) => void): () => void {
    listeners.add(listener)
    return () => listeners.delete(listener)
  }

  return { unread, connected, reconnecting, refreshUnread, connect, disconnect, subscribe }
})
