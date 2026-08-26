/** 浏览器保存的当前用户资料；绝不保存密码。 */
export interface CurrentUser {
  id: string
  username: string
  displayName: string
  role: 'USER' | 'VERIFIED_SELLER' | 'MODERATOR' | 'ADMIN'
  status: 'ACTIVE' | 'DISABLED' | 'LOCKED'
  avatarUrl: string | null
  bio: string | null
  region: string | null
  lastLoginAt: string | null
  createdAt: string
  updatedAt: string
}

/** 后端注册、登录和刷新返回的令牌对。 */
export interface AuthSession {
  accessToken: string
  refreshToken: string
  tokenType: 'Bearer'
  expiresIn: number
  user: CurrentUser
}

const STORAGE_KEY = 'pet-assistant-auth-session'

/** 读取本地会话；格式损坏时立即清除，不能带着半个令牌继续请求。 */
export function loadAuthSession(): AuthSession | undefined {
  const raw = localStorage.getItem(STORAGE_KEY)
  if (!raw) return undefined
  try {
    const parsed = JSON.parse(raw) as AuthSession
    if (!parsed.accessToken || !parsed.refreshToken || !parsed.user?.id) {
      throw new Error('invalid auth session')
    }
    return parsed
  } catch {
    localStorage.removeItem(STORAGE_KEY)
    return undefined
  }
}

export function saveAuthSession(session: AuthSession): void {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(session))
}

export function clearAuthSession(): void {
  localStorage.removeItem(STORAGE_KEY)
}

export function accessToken(): string | undefined {
  return loadAuthSession()?.accessToken
}

export function refreshToken(): string | undefined {
  return loadAuthSession()?.refreshToken
}
