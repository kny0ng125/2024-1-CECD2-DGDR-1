import { BrowserRouter as Router, Route, Routes, Navigate, useNavigate } from 'react-router-dom'
import Sidebar from './Mainbody/Sidebar'
import ManualSection from './Mainbody/ManualSection'
import ConversationBox from './Mainbody/ConversationBox'
import IncidentHeader from './Mainbody/IncidentHeader'
import LoginForm from './Login/Login'
import SignUpForm from './Login/Signup'
import CallHistory from './History/CallHistory'
import HospitalPage from './Hospital/HospitalPage'
import NavbarForm from './Navbar'
import IncomingCallBar from './Mainbody/IncomingCallBar'
import { useHotkey } from './hooks/useHotkey'
import { useAgentEvents } from './hooks/useAgentEvents'
import FakeCallPanel from './dev/FakeCallPanel'
import { RequireAuth, PublicOnly } from './components/RouteGuards'
import { useAuthStore, isTokenValid } from '@/stores/useAuthStore'

function AppRoutes() {
  const navigate = useNavigate()
  const isAuthed = isTokenValid(useAuthStore(s => s.accessToken))

  // [control 채널] 로그인해 있는 내내 통화 라이프사이클을 구독한다.
  // 프런트가 callId 를 얻는 유일한 경로이므로 앱 루트에서 한 번만 연다.
  useAgentEvents(isAuthed)

  useHotkey('ctrl+1', () => {
    document.getElementById('manual-check-button')?.click()
  })
  useHotkey('ctrl+2', () => navigate('/hospital'))

  return (
    <div className="flex flex-col h-screen bg-dispatch-bg">
      <NavbarForm />
      <Routes>
        {/* 비로그인 전용 — 이미 로그인했으면 메인으로 되돌린다 */}
        <Route element={<PublicOnly />}>
          <Route path="/login"  element={<LoginForm />} />
          <Route path="/signup" element={<SignUpForm />} />
        </Route>

        {/* 로그인 필요 — 미인증이면 /login 으로 리다이렉트 */}
        <Route element={<RequireAuth />}>
          <Route path="/history"  element={<CallHistory />} />
          <Route path="/hospital" element={<HospitalPage />} />
          <Route
            path="/"
            element={
              <div className="flex flex-col flex-1 min-h-0">
                {/* 착신 알림은 화면을 덮지 않고 상단에 끼어든다.
                    벨이 울리는 동안에도 요원은 직전 신고의 매뉴얼을
                    읽고 있을 수 있고, 그것을 가리면 안 된다. */}
                <IncomingCallBar />
                <IncidentHeader />
                <div className="grid grid-cols-[1fr_3fr_1fr] gap-2.5 p-2.5 flex-1 min-h-0 box-border">
                  <Sidebar />
                  <ManualSection />
                  <ConversationBox />
                </div>
              </div>
            }
          />
        </Route>

        {/* 정의되지 않은 경로 → 메인. 미인증이면 위 가드가 다시 /login 으로 넘긴다 */}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
      {/* 개발용 패널도 로그인 상태에서만 노출한다 */}
      {import.meta.env.DEV && isAuthed && <FakeCallPanel />}
    </div>
  )
}

function App() {
  return (
    <Router>
      <AppRoutes />
    </Router>
  )
}

export default App
