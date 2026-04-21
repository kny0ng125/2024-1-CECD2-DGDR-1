import { API_BASE_URL } from '@/lib/config'
import { useAuthStore } from '@/stores/useAuthStore'

interface AuthFetchOptions extends RequestInit {
  skipAuth?: boolean
}

export async function authFetch(path: string, options: AuthFetchOptions = {}): Promise<Response> {
  const { skipAuth = false, headers: extraHeaders, ...rest } = options

  const baseHeaders: Record<string, string> = {
    'Content-Type': 'application/json',
    'ngrok-skip-browser-warning': 'true',
  }

  if (!skipAuth) {
    const token = useAuthStore.getState().accessToken
    if (token) baseHeaders['Authorization'] = `Bearer ${token}`
  }

  const res = await fetch(`${API_BASE_URL}${path}`, {
    ...rest,
    headers: { ...baseHeaders, ...(extraHeaders as Record<string, string>) },
  })

  if (res.status === 401 && !skipAuth) {
    useAuthStore.getState().logout()
  }

  return res
}

/** EventSource는 헤더를 설정할 수 없으므로 token을 쿼리로 전달 */
export function buildSseUrl(path: string): string {
  const token = useAuthStore.getState().accessToken
  const base = `${API_BASE_URL}${path}`
  return token ? `${base}?token=${encodeURIComponent(token)}` : base
}
