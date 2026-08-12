import { create } from 'zustand'
import { jwtDecode } from 'jwt-decode'

interface JwtPayload {
  sub: string
  type: 'ACCESS' | 'REFRESH'
  exp: number
}

interface AuthState {
  accessToken: string | null
  refreshToken: string | null
  userId: string | null
  login: (access: string, refresh: string) => void
  logout: () => void
}

/**
 * 토큰이 "지금 사용 가능한 ACCESS 토큰"인지 판정한다.
 *
 * localStorage 에 남아 있다는 사실만으로는 로그인 상태라고 볼 수 없다.
 * 만료된 토큰이 그대로 남아 있는 경우가 흔하므로 exp 까지 확인해야
 * 라우트 가드가 "화면은 열리는데 API는 전부 401" 인 상태를 막을 수 있다.
 *
 * exp 는 초 단위(UNIX epoch)이고 Date.now() 는 밀리초라 1000 으로 나눠 비교한다.
 * 서버-클라이언트 시계 오차를 감안해 30초 여유(skew)를 둔다.
 */
const CLOCK_SKEW_SEC = 30

export function isTokenValid(token: string | null): boolean {
  if (!token) return false
  try {
    const payload = jwtDecode<JwtPayload>(token)
    if (payload.type !== 'ACCESS') return false
    if (typeof payload.exp !== 'number') return false
    return payload.exp - CLOCK_SKEW_SEC > Date.now() / 1000
  } catch {
    // 서명이 깨졌거나 JWT 형식이 아닌 문자열
    return false
  }
}

function parseUserId(token: string | null): string | null {
  if (!token) return null
  try {
    const payload = jwtDecode<JwtPayload>(token)
    return payload.type === 'ACCESS' ? payload.sub : null
  } catch {
    return null
  }
}

/**
 * 앱 부팅 시점에 localStorage 를 정리한다.
 * 만료/손상된 토큰을 그대로 스토어에 싣으면 가드가 통과되어 버린다.
 */
function loadInitialAuth(): Pick<AuthState, 'accessToken' | 'refreshToken' | 'userId'> {
  const access = localStorage.getItem('accessToken')
  if (!isTokenValid(access)) {
    localStorage.removeItem('accessToken')
    localStorage.removeItem('refreshToken')
    return { accessToken: null, refreshToken: null, userId: null }
  }
  return {
    accessToken: access,
    refreshToken: localStorage.getItem('refreshToken'),
    userId: parseUserId(access),
  }
}

export const useAuthStore = create<AuthState>((set) => ({
  ...loadInitialAuth(),
  login: (access, refresh) => {
    localStorage.setItem('accessToken', access)
    localStorage.setItem('refreshToken', refresh)
    const payload = jwtDecode<JwtPayload>(access)
    set({ accessToken: access, refreshToken: refresh, userId: payload.sub })
  },
  logout: () => {
    localStorage.removeItem('accessToken')
    localStorage.removeItem('refreshToken')
    set({ accessToken: null, refreshToken: null, userId: null })
  },
}))
