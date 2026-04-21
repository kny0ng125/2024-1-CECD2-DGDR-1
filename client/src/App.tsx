import { useState } from 'react'
import { BrowserRouter as Router, Route, Routes, Navigate } from 'react-router-dom'
import Sidebar from './Mainbody/Sidebar'
import ManualSection from './Mainbody/ManualSection'
import ConversationBox from './Mainbody/ConversationBox'
import HospitalListModal from './Modal/HospitalListModal'
import LoginForm from './Login/Login'
import SignUpForm from './Login/Signup'
import CallHistory from './Store/CallHistory'
import NavbarForm from './Navbar'
import { useHotkey } from './hooks/useHotkey'

function App() {
  const [showBedsModal, setShowBedsModal] = useState(false)

  const handleShowBedsModal = () => setShowBedsModal(true)
  const handleCloseBedsModal = () => setShowBedsModal(false)

  useHotkey('ctrl+1', () => {
    document.getElementById('manual-check-button')?.click()
  })
  useHotkey('ctrl+2', handleShowBedsModal)

  return (
    <Router>
      <NavbarForm onShowBedsModal={handleShowBedsModal} />
      <Routes>
        <Route path="/login" element={<LoginForm />} />
        <Route path="/signup" element={<SignUpForm />} />
        <Route path="/history" element={<CallHistory />} />
        <Route
          path="/"
          element={
            <div className="grid grid-cols-[1fr_3fr_1fr] gap-6 p-6 h-screen box-border">
              <Sidebar />
              <ManualSection />
              <ConversationBox />
              {showBedsModal && <HospitalListModal onClose={handleCloseBedsModal} />}
            </div>
          }
        />
        <Route path="*" element={<Navigate to="/" />} />
      </Routes>
    </Router>
  )
}

export default App
