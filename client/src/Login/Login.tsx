import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuthStore } from '@/stores/useAuthStore'
import { authFetch } from '@/lib/authFetch'

const LoginForm = () => {
  const navigate = useNavigate()
  const { login } = useAuthStore()
  const [account, setAccount] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')

  const handleLogin = async (event: React.FormEvent) => {
    event.preventDefault()
    setError('')

    try {
      const response = await authFetch('/api/v1/user/auth/login', {
        method: 'POST',
        skipAuth: true,
        body: JSON.stringify({ id: account, password }),
      })

      if (!response.ok) {
        const errorData = await response.json()
        setError(errorData.message || '로그인에 실패했습니다.')
        return
      }

      const data = await response.json()
      const { accessToken, refreshToken } = data

      if (accessToken && refreshToken) {
        login(accessToken, refreshToken)
        navigate('/')
      } else {
        setError('토큰을 받아오지 못했습니다.')
      }
    } catch {
      setError('서버와의 통신에 실패했습니다.')
    }
  }

  return (
    <div className="flex justify-center items-center h-screen bg-[#f0f0f0]">
      <div className="w-[600px] p-5 border border-[#d1e7f5] rounded-[10px] bg-[#f9f9f9]">
        <h2 className="text-center text-[#555] mb-5">로그인</h2>
        <form onSubmit={handleLogin}>
          <div className="mb-5">
            <label className="block mb-[5px] text-[#555]">아이디</label>
            <input
              type="text"
              placeholder="아이디"
              value={account}
              onChange={(e) => setAccount(e.target.value)}
              className="w-full p-[10px] border border-[#ccc] rounded-[5px] box-border"
            />
          </div>
          <div className="mb-5">
            <label className="block mb-[5px] text-[#555]">비밀번호</label>
            <input
              type="password"
              placeholder="비밀번호"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="w-full p-[10px] border border-[#ccc] rounded-[5px] box-border"
            />
          </div>
          {error && <p className="text-[#d32f2f] mt-[5px]">{error}</p>}
          <button
            type="submit"
            className="w-full p-[10px] bg-[#00c853] border-none rounded-[5px] text-white cursor-pointer mt-5"
          >
            로그인
          </button>
          <div className="flex justify-between mt-5">
            <button
              type="button"
              className="w-[48%] p-[10px] bg-[#d1e7f5] border-none rounded-[5px] cursor-pointer"
            >
              아이디/PW 찾기
            </button>
            <button
              type="button"
              className="w-[48%] p-[10px] bg-[#d1e7f5] border-none rounded-[5px] cursor-pointer"
              onClick={() => navigate('/signup')}
            >
              회원가입
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

export default LoginForm
