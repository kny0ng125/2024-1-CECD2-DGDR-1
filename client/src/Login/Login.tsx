import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuthStore } from '@/stores/useAuthStore'
import { authFetch } from '@/lib/authFetch'
import { T } from '@/lib/theme'

const inputStyle: React.CSSProperties = {
  width: '100%', fontFamily: T.ui, fontSize: 14,
  padding: '10px 13px',
  background: T.bgCard, color: T.text,
  border: 'none', borderRadius: 5,
  boxShadow: `inset 0 0 0 1px ${T.line}`,
  outline: 'none',
  boxSizing: 'border-box',
  transition: 'box-shadow .15s',
}

const labelStyle: React.CSSProperties = {
  display: 'block',
  fontFamily: T.mono, fontSize: 9.5,
  color: T.textMuted, letterSpacing: 1.4,
  marginBottom: 6,
  textTransform: 'uppercase',
}

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
    <div style={{
      display: 'flex', alignItems: 'center', justifyContent: 'center',
      height: '100vh', background: T.bg,
      fontFamily: T.ui, color: T.text,
    }}>
      <div style={{
        width: 420,
        background: T.bgElev,
        borderRadius: 8,
        boxShadow: `0 20px 60px rgba(0,0,0,0.5), inset 0 0 0 1px ${T.line}`,
        padding: '36px 32px',
      }}>
        {/* Header */}
        <div style={{ textAlign: 'center', marginBottom: 32 }}>
          <div style={{
            display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
            width: 40, height: 40, borderRadius: 8,
            background: T.red, marginBottom: 14,
            fontFamily: T.mono, fontSize: 14, fontWeight: 700, color: '#fff',
            letterSpacing: -0.5,
          }}>119</div>
          <div style={{ fontSize: 18, fontWeight: 600, letterSpacing: -0.3, color: T.text }}>
            119 수보 시스템
          </div>
          <div style={{ fontFamily: T.mono, fontSize: 10, color: T.textMuted, letterSpacing: 1.4, marginTop: 4 }}>
            DISPATCH · OPERATOR LOGIN
          </div>
        </div>

        {/* Divider */}
        <div style={{ height: 1, background: T.line, marginBottom: 28 }} />

        <form onSubmit={handleLogin}>
          {/* ID */}
          <div style={{ marginBottom: 18 }}>
            <label style={labelStyle}>아이디 · ID</label>
            <input
              type="text"
              placeholder="요원 아이디 입력"
              value={account}
              onChange={e => setAccount(e.target.value)}
              style={inputStyle}
              onFocus={e => e.currentTarget.style.boxShadow = `inset 0 0 0 1.5px ${T.accentBlue}`}
              onBlur={e => e.currentTarget.style.boxShadow = `inset 0 0 0 1px ${T.line}`}
            />
          </div>

          {/* Password */}
          <div style={{ marginBottom: 24 }}>
            <label style={labelStyle}>비밀번호 · PASSWORD</label>
            <input
              type="password"
              placeholder="••••••••"
              value={password}
              onChange={e => setPassword(e.target.value)}
              style={inputStyle}
              onFocus={e => e.currentTarget.style.boxShadow = `inset 0 0 0 1.5px ${T.accentBlue}`}
              onBlur={e => e.currentTarget.style.boxShadow = `inset 0 0 0 1px ${T.line}`}
            />
          </div>

          {/* Error */}
          {error && (
            <div style={{
              padding: '10px 13px', marginBottom: 16,
              background: T.redSoft, boxShadow: `inset 0 0 0 1px ${T.redEdge}`,
              borderRadius: 5, color: '#fca5a5',
              fontFamily: T.mono, fontSize: 11, letterSpacing: 0.3,
            }}>
              ✖ {error}
            </div>
          )}

          {/* Login button */}
          <button type="submit" disabled={loading}
            style={{
              width: '100%', padding: '11px 0',
              background: loading ? T.accentBlueSoft : T.accentBlue,
              color: loading ? '#93c5fd' : '#fff',
              border: 'none', borderRadius: 5,
              fontSize: 14, fontWeight: 600, cursor: loading ? 'not-allowed' : 'pointer',
              fontFamily: T.ui, letterSpacing: -0.2,
              transition: 'background .15s',
              boxShadow: loading ? `inset 0 0 0 1px ${T.accentBlueEdge}` : 'none',
            }}>
            {loading ? (
              <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                <span style={{
                  width: 12, height: 12, borderRadius: '50%',
                  border: `2px solid rgba(147,197,253,0.3)`,
                  borderTopColor: '#93c5fd',
                  display: 'inline-block',
                  animation: 'dispatchSpin 0.9s linear infinite',
                }} />
                로그인 중…
              </span>
            ) : '로그인'}
          </button>

          {/* Secondary actions */}
          <div style={{ display: 'flex', gap: 8, marginTop: 12 }}>
            <button type="button"
              style={{
                flex: 1, padding: '9px 0',
                background: 'transparent', color: T.textDim,
                border: 'none', borderRadius: 5, fontSize: 13,
                cursor: 'pointer', fontFamily: T.ui,
                boxShadow: `inset 0 0 0 1px ${T.line}`,
              }}>
              아이디/PW 찾기
            </button>
            <button type="button"
              onClick={() => navigate('/signup')}
              style={{
                flex: 1, padding: '9px 0',
                background: 'transparent', color: T.textDim,
                border: 'none', borderRadius: 5, fontSize: 13,
                cursor: 'pointer', fontFamily: T.ui,
                boxShadow: `inset 0 0 0 1px ${T.line}`,
              }}>
              회원가입
            </button>
          </div>
        </form>

        {/* Footer */}
        <div style={{
          marginTop: 28, paddingTop: 20,
          borderTop: `1px solid ${T.line}`,
          textAlign: 'center',
          fontFamily: T.mono, fontSize: 9.5, color: T.textMuted, letterSpacing: 1,
        }}>
          AUTHORIZED PERSONNEL ONLY · 권한 있는 요원 전용
        </div>
      </div>
    </div>
  )
}

export default LoginForm
