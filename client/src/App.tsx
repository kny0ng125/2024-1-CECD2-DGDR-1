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
import { useHotkey } from './hooks/useHotkey'
import FakeCallPanel from './dev/FakeCallPanel'

function AppRoutes() {
  const navigate = useNavigate()

  useHotkey('ctrl+1', () => {
    document.getElementById('manual-check-button')?.click()
  })
  useHotkey('ctrl+2', () => navigate('/hospital'))

  return (
    <div className="flex flex-col h-screen bg-dispatch-bg">
      <NavbarForm />
      <Routes>
        <Route path="/login"   element={<LoginForm />} />
        <Route path="/signup"  element={<SignUpForm />} />
        <Route path="/history" element={<CallHistory />} />
        <Route path="/hospital" element={<HospitalPage />} />
        <Route
          path="/"
          element={
            <div className="flex flex-col flex-1 min-h-0">
              <IncidentHeader />
              <div className="grid grid-cols-[1fr_3fr_1fr] gap-2.5 p-2.5 flex-1 min-h-0 box-border">
                <Sidebar />
                <ManualSection />
                <ConversationBox />
              </div>
            </div>
          }
        />
        <Route path="*" element={<Navigate to="/" />} />
      </Routes>
      {import.meta.env.DEV && <FakeCallPanel />}
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
