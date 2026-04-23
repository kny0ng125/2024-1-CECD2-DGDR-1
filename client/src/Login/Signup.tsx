import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { authFetch } from '@/lib/authFetch'

const INPUT_CLASS =
  'flex-1 text-sm py-2.5 px-[13px] bg-dispatch-card text-dispatch-text border-0 rounded-[5px] ring-1 ring-inset ring-dispatch-line outline-none transition-shadow duration-150 focus:ring-[1.5px] focus:ring-dispatch-blue'

const INPUT_FULL_CLASS = `w-full box-border ${INPUT_CLASS.replace('flex-1', '')}`

const LABEL_CLASS =
  'block font-mono text-[9.5px] text-dispatch-textMuted tracking-[1.4px] mb-1.5 uppercase'

const PILL_BUTTON_CLASS =
  'py-2.5 px-4 border-0 rounded-[5px] bg-dispatch-blue-soft text-[#93c5fd] font-mono text-[11px] font-bold tracking-[0.5px] cursor-pointer ring-1 ring-inset ring-dispatch-blue-edge whitespace-nowrap'

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

  return (
    <div className="flex items-center justify-center min-h-screen bg-dispatch-bg py-8 px-4 font-ui text-dispatch-text box-border">
      <div
        className="w-[520px] bg-dispatch-elev rounded-lg py-9 px-8 ring-1 ring-inset ring-dispatch-line"
        style={{ boxShadow: '0 20px 60px rgba(0,0,0,0.5)' }}
      >
        {/* Header */}
        <div className="text-center mb-7">
          <div className="inline-flex items-center justify-center w-10 h-10 rounded-lg bg-dispatch-red mb-3.5 font-mono text-sm font-bold text-white tracking-[-0.5px]">
            119
          </div>
          <div className="text-[18px] font-semibold tracking-[-0.3px] text-dispatch-text">요원 등록</div>
          <div className="font-mono text-[10px] text-dispatch-textMuted tracking-[1.4px] mt-1">
            DISPATCH · OPERATOR REGISTRATION
          </div>
        </div>

        <div className="h-px bg-dispatch-line mb-6" />

        <form onSubmit={handleSignup} className="flex flex-col gap-4">

          {/* 아이디 */}
          <div>
            <label className={LABEL_CLASS}>아이디 · ID</label>
            <div className="flex gap-2">
              <input type="text" placeholder="아이디 입력" value={account}
                onChange={e => setAccount(e.target.value)}
                className={INPUT_CLASS} />
              <button type="button" onClick={checkAccount} className={PILL_BUTTON_CLASS}>중복 확인</button>
            </div>
            {accountValid !== null && (
              <div className={`mt-1.5 font-mono text-[10.5px] tracking-[0.3px] ${accountValid ? 'text-[#4ade80]' : 'text-[#fca5a5]'}`}>
                {accountValid ? '✔ 사용 가능한 아이디입니다.' : '✖ 이미 사용 중인 아이디입니다.'}
              </div>
            )}
          </div>

          {/* 전화번호 */}
          <div>
            <label className={LABEL_CLASS}>전화번호 · PHONE</label>
            <div className="flex gap-2">
              <input type="text" placeholder="010-0000-0000" value={phoneNumber}
                onChange={e => setPhoneNumber(e.target.value)}
                className={INPUT_CLASS} />
              <button type="button" onClick={() => setPhoneValid(true)} className={PILL_BUTTON_CLASS}>인증하기</button>
            </div>
            {phoneValid !== null && (
              <div className={`mt-1.5 font-mono text-[10.5px] tracking-[0.3px] ${phoneValid ? 'text-[#4ade80]' : 'text-[#fca5a5]'}`}>
                {phoneValid ? '✔ 사용 가능한 번호입니다.' : '✖ 이미 사용 중인 번호입니다.'}
              </div>
            )}
          </div>

          {/* 이름 */}
          <div>
            <label className={LABEL_CLASS}>이름 · NAME</label>
            <input type="text" placeholder="성함 입력" value={name}
              onChange={e => setName(e.target.value)}
              className={INPUT_FULL_CLASS} />
          </div>

          {/* 비밀번호 */}
          <div>
            <label className={LABEL_CLASS}>비밀번호 · PASSWORD</label>
            <input type="password" placeholder="••••••••" value={password}
              onChange={e => { setPassword(e.target.value); setPasswordValid(validatePassword(e.target.value)) }}
              className={INPUT_FULL_CLASS} />
            {password && (
              <div className={`mt-1.5 font-mono text-[10.5px] tracking-[0.2px] ${passwordValid ? 'text-[#4ade80]' : 'text-[#fca5a5]'}`}>
                {passwordValid
                  ? '✔ 비밀번호가 유효합니다.'
                  : '✖ 8~12자, 영대소문자·숫자·특수문자(@$!%*?&) 모두 포함'}
              </div>
            )}
          </div>

          {/* 비밀번호 확인 */}
          <div>
            <label className={LABEL_CLASS}>비밀번호 확인 · CONFIRM</label>
            <input type="password" placeholder="••••••••" value={confirmPassword}
              onChange={e => setConfirmPassword(e.target.value)}
              className={INPUT_FULL_CLASS} />
            {confirmPassword && password !== confirmPassword && (
              <div className="mt-1.5 font-mono text-[10.5px] text-[#fca5a5]">
                ✖ 비밀번호가 일치하지 않습니다.
              </div>
            )}
          </div>

          {/* Submit */}
          <button
            type="submit"
            disabled={isSubmitting}
            className={`w-full py-[11px] mt-1 border-0 rounded-[5px] text-sm font-semibold font-ui tracking-[-0.2px] transition-colors duration-150 ${
              isSubmitting
                ? 'bg-dispatch-blue-soft text-[#93c5fd] cursor-not-allowed'
                : 'bg-dispatch-blue text-white cursor-pointer'
            }`}
          >
            {isSubmitting ? '등록 중…' : '요원 등록'}
          </button>

          <button
            type="button"
            onClick={() => navigate('/login')}
            className="w-full py-[9px] bg-transparent text-dispatch-textDim border-0 rounded-[5px] text-[13px] cursor-pointer font-ui ring-1 ring-inset ring-dispatch-line"
          >
            로그인으로 돌아가기
          </button>
        </form>

        {/* Result messages */}
        {signupSuccess && (
          <div className="mt-4 py-3 px-3.5 bg-dispatch-green-soft ring-1 ring-inset ring-dispatch-green-edge rounded-[5px] text-[#4ade80] font-mono text-[11px] text-center tracking-[0.3px]">
            ✔ 회원가입 성공! 로그인 페이지로 이동합니다…
          </div>
        )}
        {errorMessage && (
          <div className="mt-4 py-3 px-3.5 bg-dispatch-red-soft ring-1 ring-inset ring-dispatch-red-edge rounded-[5px] text-[#fca5a5] font-mono text-[11px] text-center tracking-[0.3px]">
            ✖ {errorMessage}
          </div>
        )}

        <div className="mt-6 pt-[18px] border-t border-dispatch-line text-center font-mono text-[9.5px] text-dispatch-textMuted tracking-[1px]">
          AUTHORIZED PERSONNEL ONLY · 권한 있는 요원 전용
        </div>
      </div>
    </div>
  )
}

export default SignUpForm
