/**
 * 生成客户端请求 ID。
 * randomUUID 仅在 HTTPS/localhost 等安全上下文可用；公网 HTTP 仍可使用 getRandomValues。
 */
export function createRequestId(): string {
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
