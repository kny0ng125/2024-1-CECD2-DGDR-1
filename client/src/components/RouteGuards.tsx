import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuthStore, isTokenValid } from '@/stores/useAuthStore'

/**
 * 라우트 가드.
 *
 * 주의: 이것은 UX 장치이지 보안 장치가 아니다. 번들은 이미 브라우저에 내려가
 * 있으므로 사용자가 마음먹으면 우회할 수 있다. 실제 접근 통제는 서버의
 * SecurityConfig(`.anyRequest().authenticated()`)가 담당하고, 여기서는
 * "토큰 없이 화면만 열려서 빈 껍데기가 보이는" 상황을 막는다.
 *
 * <Outlet /> 기반이라 App.tsx 에서 여러 route 를 한 번에 감쌀 수 있다.
 */

/** 로그인 필요. 미인증이면 /login 으로 보내고, 원래 가려던 곳을 기억한다. */
export function RequireAuth() {
  const accessToken = useAuthStore(s => s.accessToken)
  const location = useLocation()

  if (!isTokenValid(accessToken)) {
    // replace: 뒤로가기로 보호된 화면에 되돌아가지 못하게 히스토리를 대체한다.
    // state.from: 로그인 성공 후 원래 목적지로 복귀시키기 위한 정보.
    return <Navigate to="/login" replace state={{ from: location }} />
  }
  return <Outlet />
}

/**
 * 이미 로그인한 사용자는 볼 필요가 없는 화면(로그인/회원가입).
 * 로그인 상태로 /login 에 들어오면 메인으로 되돌린다.
 */
export function PublicOnly() {
  const accessToken = useAuthStore(s => s.accessToken)

  if (isTokenValid(accessToken)) {
    return <Navigate to="/" replace />
  }
  return <Outlet />
}
