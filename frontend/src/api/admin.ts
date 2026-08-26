import { apiRequest } from './http'

export type UserRole = 'USER' | 'VERIFIED_SELLER' | 'MODERATOR' | 'ADMIN'
export type UserStatus = 'ACTIVE' | 'DISABLED' | 'LOCKED'

export interface AdminUser {
  id: string
  username: string
  displayName: string
  role: UserRole
  status: UserStatus
  securityVersion: number
  region: string | null
  lastLoginAt: string | null
  createdAt: string
  updatedAt: string
}

export interface AdminAudit {
  id: string
  actorUserId: string
  actorUsername: string
  targetUserId: string | null
  targetUsername: string | null
  action: string
  beforeValue: string | null
  afterValue: string | null
  createdAt: string
}

interface Page<T> { items: T[]; page: number; size: number; total: number }

/** ADMIN 用户治理 API；普通用户即使伪造页面请求也会被后端拒绝。 */
export function listAdminUsers(keyword = ''): Promise<Page<AdminUser>> {
  const query = keyword ? `?keyword=${encodeURIComponent(keyword)}` : ''
  return apiRequest(`/api/v1/admin/users${query}`)
}

export function updateAdminUserRole(userId: string, role: UserRole): Promise<AdminUser> {
  return apiRequest(`/api/v1/admin/users/${userId}/role`, {
    method: 'PATCH', body: JSON.stringify({ role }),
  })
}

export function updateAdminUserStatus(userId: string, status: 'ACTIVE' | 'DISABLED'): Promise<AdminUser> {
  return apiRequest(`/api/v1/admin/users/${userId}/status`, {
    method: 'PATCH', body: JSON.stringify({ status }),
  })
}

export function listAdminAudits(): Promise<Page<AdminAudit>> {
  return apiRequest('/api/v1/admin/audit-logs')
}
