import { apiRequest } from './http'
import {
  clearAuthSession,
  loadAuthSession,
  saveAuthSession,
  type AuthSession,
  type CurrentUser,
} from './session'

export type { AuthSession, CurrentUser }

export interface RegisterRequest {
  username: string
  password: string
  displayName?: string
}

export interface LoginRequest {
  username: string
  password: string
}

/** 注册成功即建立登录会话。 */
export async function register(request: RegisterRequest): Promise<AuthSession> {
  const session = await apiRequest<AuthSession>('/api/v1/auth/register', {
    method: 'POST',
    body: JSON.stringify(request),
  })
  saveAuthSession(session)
  return session
}

/** 登录成功后只保存令牌和安全用户摘要。 */
export async function login(request: LoginRequest): Promise<AuthSession> {
  const session = await apiRequest<AuthSession>('/api/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify(request),
  })
  saveAuthSession(session)
  return session
}

/** 从后端重新读取当前用户，避免长期只相信 localStorage 里的旧资料。 */
export function getCurrentUser(): Promise<CurrentUser> {
  return apiRequest<CurrentUser>('/api/v1/users/me')
}

export function updateCurrentUser(request: {
  displayName?: string
  avatarUrl?: string
  bio?: string
  region?: string
}): Promise<CurrentUser> {
  return apiRequest<CurrentUser>('/api/v1/users/me', {
    method: 'PATCH',
    body: JSON.stringify(request),
  })
}

/** 无论网络退出是否成功都清除本机令牌，避免公用电脑继续使用。 */
export async function logout(): Promise<void> {
  const session = loadAuthSession()
  try {
    if (session?.refreshToken) {
      await apiRequest<void>('/api/v1/auth/logout', {
        method: 'POST',
        body: JSON.stringify({ refreshToken: session.refreshToken }),
      })
    }
  } finally {
    clearAuthSession()
  }
}
