export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? ''
export const WS_BASE_URL = import.meta.env.VITE_WS_BASE_URL ?? ''
// TODO: 다음 PR에서 서버 프록시 전환. 현재는 기존 whitex 서버 직접 호출, 404 허용
export const HOSPITAL_API_BASE_URL =
  import.meta.env.VITE_HOSPITAL_API_BASE_URL ?? API_BASE_URL
