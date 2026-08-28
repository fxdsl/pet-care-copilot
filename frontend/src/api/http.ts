/** 后端统一错误响应。 */
interface ApiErrorBody {
  code?: string
  message?: string
}

import {
  accessToken,
  clearAuthSession,
  refreshToken,
  saveAuthSession,
  type AuthSession,
} from './session'

/** 带业务错误码的前端异常，页面可按 code 给出更具体的恢复建议。 */
export class ApiRequestError extends Error {
  constructor(
    public readonly code: string,
    public readonly status: number,
    message: string,
  ) {
    super(message)
    this.name = 'ApiRequestError'
  }
}

// 所有浏览器请求只访问 Spring Boot，禁止前端绕过业务层直连 FastAPI。
export const businessApiBaseUrl =
  import.meta.env.VITE_BUSINESS_API_BASE_URL ?? 'http://localhost:8080'

let refreshInFlight: Promise<boolean> | undefined

/** 多个请求同时遇到 401 时只向 Redis 刷新一次，后续请求复用同一结果。 */
async function refreshAccessToken(): Promise<boolean> {
  if (refreshInFlight) return refreshInFlight
  refreshInFlight = (async () => {
    const token = refreshToken()
    if (!token) return false
    try {
      const response = await fetch(`${businessApiBaseUrl}/api/v1/auth/refresh`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken: token }),
      })
      if (!response.ok) {
        if (response.status === 400 || response.status === 401) clearAuthSession()
        return false
      }
      saveAuthSession((await response.json()) as AuthSession)
      return true
    } catch {
      return false
    } finally {
      refreshInFlight = undefined
    }
  })()
  return refreshInFlight
}

/** 调用 Spring Boot 并把网络错误、HTTP 错误转换为统一异常。 */
export async function apiRequest<T>(
  path: string,
  init?: RequestInit,
  allowRefresh = true,
): Promise<T> {
  let response: Response
  try {
    response = await fetch(`${businessApiBaseUrl}${path}`, {
      ...init,
      headers: {
        ...(init?.body ? { 'Content-Type': 'application/json' } : {}),
        ...(accessToken() ? { Authorization: `Bearer ${accessToken()}` } : {}),
        ...init?.headers,
      },
    })
  } catch {
    throw new ApiRequestError(
      'NETWORK_ERROR',
      0,
      '无法连接业务服务，请确认 Spring Boot 8080 已启动',
    )
  }

  if (
    response.status === 401 &&
    allowRefresh &&
    !path.startsWith('/api/v1/auth/') &&
    (await refreshAccessToken())
  ) {
    return apiRequest<T>(path, init, false)
  }

  if (!response.ok) {
    const error = (await response.json().catch(() => ({}))) as ApiErrorBody
    throw new ApiRequestError(
      error.code ?? 'HTTP_ERROR',
      response.status,
      error.message ?? `请求失败（HTTP ${response.status}）`,
    )
  }

  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}
