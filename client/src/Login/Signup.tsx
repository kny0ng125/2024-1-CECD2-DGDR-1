import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { authFetch } from '@/lib/authFetch'
import { T } from '@/lib/theme'

const inputStyle: React.CSSProperties = {
  flex: 1, fontFamily: 'inherit', fontSize: 14,
  padding: '10px 13px',
  background: T.bgCard, color: T.text,
  border: 'none', borderRadius: 5,
  boxShadow: `inset 0 0 0 1px ${T.line}`,
  outline: 'none',
  transition: 'box-shadow .15s',
}

const labelStyle: React.CSSProperties = {
  display: 'block',
  fontFamily: T.mono, fontSize: 9.5,
  color: T.textMuted, letterSpacing: 1.4,
  marginBottom: 6,
  textTransform: 'uppercase',
}

const SignUpForm = () => {
  const navigate = useNavigate()
  const [account, setAccount]               = useState('')
  const [phoneNumber, setPhoneNumber]       = useState('')
  const [name, setName]                     = useState('')
  const [password, setPassword]             = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [accountValid, setAccountValid]     = useState<boolean | null>(null)
  const [phoneValid, setPhoneValid]         = useState<boolean | null>(null)
  const [passwordValid, setPasswordValid]   = useState<boolean | null>(null)
  const [isSubmitting, setIsSubmitting]     = useState(false)
  const [signupSuccess, setSignupSuccess]   = useState(false)
  const [errorMessage, setErrorMessage]     = useState<string | null>(null)

  const validatePassword = (pw: string) =>
    /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&])[A-Za-z\d@$!%*?&]{8,12}$/.test(pw)

  const checkAccount = async () => {
    try {
      const res    = await authFetch(`/api/v1/user/auth/checkId?id=${encodeURIComponent(account)}`, { method: 'GET', skipAuth: true })
      const exists = await res.json()
      setAccountValid(!exists)
    } catch { setAccountValid(false) }
  }

  const handleSignup = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!account || !name || !password || !confirmPassword || !phoneNumber) { alert('모든 필드를 채워주세요.'); return }
    if (password !== confirmPassword) { alert('비밀번호가 일치하지 않습니다.'); return }
    if (!passwordValid) { alert('유효한 비밀번호를 입력하세요.'); return }
    setIsSubmitting(true)
    try {
      const res = await authFetch('/api/v1/user/auth/signup', {
        method: 'POST', skipAuth: true,
        body: JSON.stringify({ id: account, name, password, phone: phoneNumber }),
      })
      if (res.ok) {
        setSignupSuccess(true)
        setTimeout(() => navigate('/login'), 2000)
      } else {
        const data = await res.json()
        setErrorMessage(data.message || '회원가입에 실패했습니다.')
      }
    } catch { setErrorMessage('서버와의 통신에 실패했습니다.') }
    finally { setIsSubmitting(false) }
  }

  const onFocus  = (e: React.FocusEvent<HTMLInputElement>) =>
    e.currentTarget.style.boxShadow = `inset 0 0 0 1.5px ${T.accentBlue}`
  const onBlur   = (e: React.FocusEvent<HTMLInputElement>) =>
    e.currentTarget.style.boxShadow = `inset 0 0 0 1px ${T.line}`

  return (
    <div style={{
      display: 'flex', alignItems: 'center', justifyContent: 'center',
      minHeight: '100vh', background: T.bg, padding: '32px 16px',
      fontFamily: T.ui, color: T.text, boxSizing: 'border-box',
    }}>
      <div style={{
        width: 520,
        background: T.bgElev,
        borderRadius: 8,
        boxShadow: `0 20px 60px rgba(0,0,0,0.5), inset 0 0 0 1px ${T.line}`,
        padding: '36px 32px',
      }}>
        {/* Header */}
        <div style={{ textAlign: 'center', marginBottom: 28 }}>
          <div style={{
            display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
            width: 40, height: 40, borderRadius: 8,
            background: T.red, marginBottom: 14,
            fontFamily: T.mono, fontSize: 14, fontWeight: 700, color: '#fff', letterSpacing: -0.5,
          }}>119</div>
          <div style={{ fontSize: 18, fontWeight: 600, letterSpacing: -0.3, color: T.text }}>요원 등록</div>
          <div style={{ fontFamily: T.mono, fontSize: 10, color: T.textMuted, letterSpacing: 1.4, marginTop: 4 }}>
            DISPATCH · OPERATOR REGISTRATION
          </div>
        </div>

        <div style={{ height: 1, background: T.line, marginBottom: 24 }} />

        <form onSubmit={handleSignup} style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>

          {/* 아이디 */}
          <div>
            <label style={labelStyle}>아이디 · ID</label>
            <div style={{ display: 'flex', gap: 8 }}>
              <input type="text" placeholder="아이디 입력" value={account}
                onChange={e => setAccount(e.target.value)}
                style={inputStyle} onFocus={onFocus} onBlur={onBlur} />
              <button type="button" onClick={checkAccount}
                style={{
                  padding: '10px 16px', border: 'none', borderRadius: 5,
                  background: T.accentBlueSoft, color: '#93c5fd',
                  fontFamily: T.mono, fontSize: 11, fontWeight: 700, letterSpacing: 0.5,
                  cursor: 'pointer',
                  boxShadow: `inset 0 0 0 1px ${T.accentBlueEdge}`,
                  whiteSpace: 'nowrap',
                }}>중복 확인</button>
            </div>
            {accountValid !== null && (
              <div style={{
                marginTop: 6, fontFamily: T.mono, fontSize: 10.5, letterSpacing: 0.3,
                color: accountValid ? '#4ade80' : '#fca5a5',
              }}>
                {accountValid ? '✔ 사용 가능한 아이디입니다.' : '✖ 이미 사용 중인 아이디입니다.'}
              </div>
            )}
          </div>

          {/* 전화번호 */}
          <div>
            <label style={labelStyle}>전화번호 · PHONE</label>
            <div style={{ display: 'flex', gap: 8 }}>
              <input type="text" placeholder="010-0000-0000" value={phoneNumber}
                onChange={e => setPhoneNumber(e.target.value)}
                style={inputStyle} onFocus={onFocus} onBlur={onBlur} />
              <button type="button" onClick={() => setPhoneValid(true)}
                style={{
                  padding: '10px 16px', border: 'none', borderRadius: 5,
                  background: T.accentBlueSoft, color: '#93c5fd',
                  fontFamily: T.mono, fontSize: 11, fontWeight: 700, letterSpacing: 0.5,
                  cursor: 'pointer',
                  boxShadow: `inset 0 0 0 1px ${T.accentBlueEdge}`,
                  whiteSpace: 'nowrap',
                }}>인증하기</button>
            </div>
            {phoneValid !== null && (
              <div style={{
                marginTop: 6, fontFamily: T.mono, fontSize: 10.5, letterSpacing: 0.3,
                color: phoneValid ? '#4ade80' : '#fca5a5',
              }}>
                {phoneValid ? '✔ 사용 가능한 번호입니다.' : '✖ 이미 사용 중인 번호입니다.'}
              </div>
            )}
          </div>

          {/* 이름 */}
          <div>
            <label style={labelStyle}>이름 · NAME</label>
            <input type="text" placeholder="성함 입력" value={name}
              onChange={e => setName(e.target.value)}
              style={{ ...inputStyle, width: '100%', flex: 'none', boxSizing: 'border-box' }}
              onFocus={onFocus} onBlur={onBlur} />
          </div>

          {/* 비밀번호 */}
          <div>
            <label style={labelStyle}>비밀번호 · PASSWORD</label>
            <input type="password" placeholder="••••••••" value={password}
              onChange={e => { setPassword(e.target.value); setPasswordValid(validatePassword(e.target.value)) }}
              style={{ ...inputStyle, width: '100%', flex: 'none', boxSizing: 'border-box' }}
              onFocus={onFocus} onBlur={onBlur} />
            {password && (
              <div style={{
                marginTop: 6, fontFamily: T.mono, fontSize: 10.5, letterSpacing: 0.2,
                color: passwordValid ? '#4ade80' : '#fca5a5',
              }}>
                {passwordValid
                  ? '✔ 비밀번호가 유효합니다.'
                  : '✖ 8~12자, 영대소문자·숫자·특수문자(@$!%*?&) 모두 포함'}
              </div>
            )}
          </div>

          {/* 비밀번호 확인 */}
          <div>
            <label style={labelStyle}>비밀번호 확인 · CONFIRM</label>
            <input type="password" placeholder="••••••••" value={confirmPassword}
              onChange={e => setConfirmPassword(e.target.value)}
              style={{ ...inputStyle, width: '100%', flex: 'none', boxSizing: 'border-box' }}
              onFocus={onFocus} onBlur={onBlur} />
            {confirmPassword && password !== confirmPassword && (
              <div style={{ marginTop: 6, fontFamily: T.mono, fontSize: 10.5, color: '#fca5a5' }}>
                ✖ 비밀번호가 일치하지 않습니다.
              </div>
            )}
          </div>

          {/* Submit */}
          <button type="submit" disabled={isSubmitting}
            style={{
              width: '100%', padding: '11px 0', marginTop: 4,
              background: isSubmitting ? T.accentBlueSoft : T.accentBlue,
              color: isSubmitting ? '#93c5fd' : '#fff',
              border: 'none', borderRadius: 5,
              fontSize: 14, fontWeight: 600,
              cursor: isSubmitting ? 'not-allowed' : 'pointer',
              fontFamily: T.ui, letterSpacing: -0.2,
              transition: 'background .15s',
            }}>
            {isSubmitting ? '등록 중…' : '요원 등록'}
          </button>

          <button type="button" onClick={() => navigate('/login')}
            style={{
              width: '100%', padding: '9px 0',
              background: 'transparent', color: T.textDim,
              border: 'none', borderRadius: 5, fontSize: 13,
              cursor: 'pointer', fontFamily: T.ui,
              boxShadow: `inset 0 0 0 1px ${T.line}`,
            }}>
            로그인으로 돌아가기
          </button>
        </form>

        {/* Result messages */}
        {signupSuccess && (
          <div style={{
            marginTop: 16, padding: '12px 14px',
            background: T.greenSoft, boxShadow: `inset 0 0 0 1px ${T.greenEdge}`,
            borderRadius: 5, color: '#4ade80',
            fontFamily: T.mono, fontSize: 11, textAlign: 'center', letterSpacing: 0.3,
          }}>
            ✔ 회원가입 성공! 로그인 페이지로 이동합니다…
          </div>
        )}
        {errorMessage && (
          <div style={{
            marginTop: 16, padding: '12px 14px',
            background: T.redSoft, boxShadow: `inset 0 0 0 1px ${T.redEdge}`,
            borderRadius: 5, color: '#fca5a5',
            fontFamily: T.mono, fontSize: 11, textAlign: 'center', letterSpacing: 0.3,
          }}>
            ✖ {errorMessage}
          </div>
        )}

        <div style={{
          marginTop: 24, paddingTop: 18,
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

export default SignUpForm
