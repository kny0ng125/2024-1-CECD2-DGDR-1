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

function parseUserId(token: string | null): string | null {
  if (!token) return null
  try {
    const payload = jwtDecode<JwtPayload>(token)
    return payload.type === 'ACCESS' ? payload.sub : null
  } catch {
    return null
  }
}

export const useAuthStore = create<AuthState>((set) => ({
  accessToken: localStorage.getItem('accessToken'),
  refreshToken: localStorage.getItem('refreshToken'),
  userId: parseUserId(localStorage.getItem('accessToken')),
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
