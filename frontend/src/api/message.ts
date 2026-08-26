import { apiRequest } from './http'

export type NotificationType = 'COMMENT' | 'LIKE' | 'FOLLOW' | 'MODERATION' | 'SYSTEM'

export interface NotificationItem {
  id: string
  actorId: string | null
  actorUsername: string | null
  actorDisplayName: string | null
  actorAvatarUrl: string | null
  type: NotificationType
  targetType: string | null
  targetId: string | null
  title: string
  content: string
  read: boolean
  createdAt: string
}

export interface MessageUnread {
  total: number
  directMessages: number
  notifications: Record<NotificationType, number>
}

export interface DirectConversation {
  id: string
  otherUserId: string
  otherUsername: string
  otherDisplayName: string
  otherAvatarUrl: string | null
  lastMessageContent: string | null
  lastMessageMine: boolean
  lastMessageAt: string | null
  unreadCount: number
  createdAt: string
  updatedAt: string
}

export interface DirectMessage {
  id: string
  conversationId: string
  senderId: string
  senderUsername: string
  senderDisplayName: string
  senderAvatarUrl: string | null
  recipientId: string
  clientMessageId: string
  content: string
  read: boolean
  createdAt: string
}

interface Page<T> { items: T[]; page: number; size: number; total: number }

export const listNotifications = (type?: NotificationType) => apiRequest<Page<NotificationItem>>(
  `/api/v1/messages/notifications${type ? `?type=${type}` : ''}`,
)
export const markNotificationRead = (id: string) => apiRequest<NotificationItem>(
  `/api/v1/messages/notifications/${id}/read`, { method: 'PUT' },
)
export const markAllNotificationsRead = (type?: NotificationType) => apiRequest<void>(
  `/api/v1/messages/notifications/read-all${type ? `?type=${type}` : ''}`, { method: 'PUT' },
)
export const getMessageUnread = () => apiRequest<MessageUnread>('/api/v1/messages/unread')
export const listDirectConversations = () => apiRequest<Page<DirectConversation>>('/api/v1/messages/conversations')
export const listDirectMessages = (conversationId: string) => apiRequest<Page<DirectMessage>>(
  `/api/v1/messages/conversations/${conversationId}`,
)
export const sendDirectMessage = (input: { recipientId: string; clientMessageId: string; content: string }) =>
  apiRequest<DirectMessage>('/api/v1/messages/direct', { method: 'POST', body: JSON.stringify(input) })
