import { useState, useEffect } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { useAuthStore } from '@/stores/useAuthStore'
import { useHotkey } from '@/hooks/useHotkey'

interface NavbarFormProps {
  onShowBedsModal: () => void
}

const NavbarForm = ({ onShowBedsModal }: NavbarFormProps) => {
  const location = useLocation()
  const navigate = useNavigate()
  const { userId } = useAuthStore()
  const [currentTime, setCurrentTime] = useState('')

  useEffect(() => {
    const updateTime = () => {
      const now = new Date()
      setCurrentTime(now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }))
    }
    updateTime()
    const id = setInterval(updateTime, 60000)
    return () => clearInterval(id)
  }, [])

  const isAuthPage =
    location.pathname === '/login' || location.pathname === '/signup'

  useHotkey('shift+1', () => navigate('/history'))
  useHotkey('shift+2', () => navigate('/'))
  useHotkey('shift+3', onShowBedsModal)

  return (
    <nav className="flex items-center px-5 py-[10px] bg-[#f8f9fa] border-b border-[#dee2e6]">
      {isAuthPage ? (
        <div className="flex gap-[10px]">
          <Button variant="outline" onClick={() => navigate('/login')}>로그인</Button>
          <Button variant="outline" onClick={() => navigate('/signup')}>회원가입</Button>
        </div>
      ) : (
        <>
          <div className="flex gap-[10px]">
            <Button variant="outline" onClick={() => navigate('/history')}>History</Button>
            <Button variant="outline" onClick={() => navigate('/')}>Main</Button>
            <Button variant="danger" onClick={onShowBedsModal}>응급 병상 확인하기</Button>
          </div>
          <span className="ml-auto text-base text-[#333]">
            {userId ?? '(미인증)'} 님 | 접속 시간: {currentTime}
          </span>
        </>
      )}
    </nav>
  )
}

export default NavbarForm
