import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuthStore } from '@/stores/useAuthStore'
import { authFetch } from '@/lib/authFetch'

const INPUT_CLASS =
  'w-full font-ui text-sm py-2.5 px-[13px] bg-dispatch-card text-dispatch-text border-0 rounded-[5px] ring-1 ring-inset ring-dispatch-line outline-none box-border transition-shadow duration-150 focus:ring-[1.5px] focus:ring-dispatch-blue'

const LABEL_CLASS =
  'block font-mono text-[9.5px] text-dispatch-textMuted tracking-[1.4px] mb-1.5 uppercase'

const LoginForm = () => {
  const navigate        = useNavigate()
  const { login }       = useAuthStore()
  const [account, setAccount]   = useState('')
  const [password, setPassword] = useState('')
  const [error, setError]       = useState('')
  const [loading, setLoading]   = useState(false)

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      const res = await authFetch('/api/v1/user/auth/login', {
        method: 'POST',
        skipAuth: true,
        body: JSON.stringify({ id: account, password }),
      })
      if (!res.ok) {
        const data = await res.json()
        setError(data.message || '로그인에 실패했습니다.')
        return
      }
      const data = await res.json()
      if (data.accessToken && data.refreshToken) {
        login(data.accessToken, data.refreshToken)
        navigate('/')
      } else {
        setError('토큰을 받아오지 못했습니다.')
      }
    } catch {
      setError('서버와의 통신에 실패했습니다.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="flex items-center justify-center h-screen bg-dispatch-bg font-ui text-dispatch-text">
      <div
        className="w-[420px] bg-dispatch-elev rounded-lg py-9 px-8 ring-1 ring-inset ring-dispatch-line"
        style={{ boxShadow: '0 20px 60px rgba(0,0,0,0.5)' }}
      >
        {/* Header */}
        <div className="text-center mb-8">
          <div className="inline-flex items-center justify-center w-10 h-10 rounded-lg bg-dispatch-red mb-3.5 font-mono text-sm font-bold text-white tracking-[-0.5px]">
            119
          </div>
          <div className="text-[18px] font-semibold tracking-[-0.3px] text-dispatch-text">
            119 수보 시스템
          </div>
          <div className="font-mono text-[10px] text-dispatch-textMuted tracking-[1.4px] mt-1">
            DISPATCH · OPERATOR LOGIN
          </div>
        </div>

        {/* Divider */}
        <div className="h-px bg-dispatch-line mb-7" />

        <form onSubmit={handleLogin}>
          {/* ID */}
          <div className="mb-[18px]">
            <label className={LABEL_CLASS}>아이디 · ID</label>
            <input
              type="text"
              placeholder="요원 아이디 입력"
              value={account}
              onChange={e => setAccount(e.target.value)}
              className={INPUT_CLASS}
            />
          </div>

          {/* Password */}
          <div className="mb-6">
            <label className={LABEL_CLASS}>비밀번호 · PASSWORD</label>
            <input
              type="password"
              placeholder="••••••••"
              value={password}
              onChange={e => setPassword(e.target.value)}
              className={INPUT_CLASS}
            />
          </div>

          {/* Error */}
          {error && (
            <div className="py-2.5 px-[13px] mb-4 bg-dispatch-red-soft ring-1 ring-inset ring-dispatch-red-edge rounded-[5px] text-[#fca5a5] font-mono text-[11px] tracking-[0.3px]">
              ✖ {error}
            </div>
          )}

          {/* Login button */}
          <button
            type="submit"
            disabled={loading}
            className={`w-full py-[11px] border-0 rounded-[5px] text-sm font-semibold font-ui tracking-[-0.2px] transition-colors duration-150 ${
              loading
                ? 'bg-dispatch-blue-soft text-[#93c5fd] cursor-not-allowed ring-1 ring-inset ring-dispatch-blue-edge'
                : 'bg-dispatch-blue text-white cursor-pointer'
            }`}
          >
            {loading ? (
              <span className="inline-flex items-center gap-2">
                <span
                  className="w-3 h-3 rounded-full inline-block"
                  style={{
                    border: '2px solid rgba(147,197,253,0.3)',
                    borderTopColor: '#93c5fd',
                    animation: 'dispatchSpin 0.9s linear infinite',
                  }}
                />
                로그인 중…
              </span>
            ) : '로그인'}
          </button>

          {/* Secondary actions */}
          <div className="flex gap-2 mt-3">
            <button
              type="button"
              className="flex-1 py-[9px] bg-transparent text-dispatch-textDim border-0 rounded-[5px] text-[13px] cursor-pointer font-ui ring-1 ring-inset ring-dispatch-line"
            >
              아이디/PW 찾기
            </button>
            <button
              type="button"
              onClick={() => navigate('/signup')}
              className="flex-1 py-[9px] bg-transparent text-dispatch-textDim border-0 rounded-[5px] text-[13px] cursor-pointer font-ui ring-1 ring-inset ring-dispatch-line"
            >
              회원가입
            </button>
          </div>
        </form>

        {/* Footer */}
        <div className="mt-7 pt-5 border-t border-dispatch-line text-center font-mono text-[9.5px] text-dispatch-textMuted tracking-[1px]">
          AUTHORIZED PERSONNEL ONLY · 권한 있는 요원 전용
        </div>
      </div>
    </div>
  )
}

export default LoginForm
