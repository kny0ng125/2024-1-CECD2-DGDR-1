import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { API_BASE_URL } from '@/lib/config'

const SignUpForm = () => {
  const [phoneNumber, setPhoneNumber] = useState('')
  const [account, setAccount] = useState('')
  const [name, setName] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [accountValid, setAccountValid] = useState<boolean | null>(null)
  const [phoneValid, setPhoneValid] = useState<boolean | null>(null)
  const [passwordValid, setPasswordValid] = useState<boolean | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [signupSuccess, setSignupSuccess] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const navigate = useNavigate()

  const validatePassword = (pw: string) => {
    const regex = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&])[A-Za-z\d@$!%*?&]{8,12}$/
    return regex.test(pw)
  }

  const checkAccountAvailability = async () => {
    try {
      const response = await fetch(
        `${API_BASE_URL}/api/v1/user/auth/id/check?id=${account}`,
        {
          method: 'GET',
          headers: {
            'Content-Type': 'application/json',
            'ngrok-skip-browser-warning': 'true',
          },
        }
      )
      const data = await response.json()
      setAccountValid(!data.isExist)
    } catch {
      setAccountValid(false)
    }
  }

  const phoneNumberAvailability = () => setPhoneValid(true)

  const handleSignup = async (event: React.FormEvent) => {
    event.preventDefault()
    setIsSubmitting(true)

    if (!account || !name || !password || !confirmPassword || !phoneNumber) {
      alert('모든 필드를 채워주세요.')
      setIsSubmitting(false)
      return
    }
    if (password !== confirmPassword) {
      alert('비밀번호가 일치하지 않습니다.')
      setIsSubmitting(false)
      return
    }
    if (!passwordValid) {
      alert('유효한 비밀번호를 입력하세요.')
      setIsSubmitting(false)
      return
    }

    try {
      const response = await fetch(`${API_BASE_URL}/api/v1/user/auth/signup`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'ngrok-skip-browser-warning': 'true',
        },
        body: JSON.stringify({ id: account, name, password, phone: phoneNumber }),
      })

      if (response.ok) {
        setSignupSuccess(true)
        setTimeout(() => navigate('/login'), 2000)
      } else {
        const errorData = await response.json()
        setErrorMessage(errorData.message || '회원가입에 실패했습니다.')
        setSignupSuccess(false)
      }
    } catch {
      setErrorMessage('서버와의 통신에 실패했습니다.')
    }

    setIsSubmitting(false)
  }

  const fieldClass = 'p-[10px] border border-[#ccc] rounded-[5px] box-border'
  const actionBtnClass =
    'ml-5 w-[140px] h-[40px] bg-[#00c853] text-white border-none rounded-[5px] cursor-pointer flex items-center justify-center'

  return (
    <div className="flex justify-center items-center h-screen bg-[#f0f0f0]">
      <div className="w-[700px] mx-auto p-5 border border-[#d1e7f5] rounded-[10px] bg-[#f9f9f9]">
        <h2 className="text-center text-[#555] mt-5 mb-5">회원 가입</h2>
        <form onSubmit={handleSignup}>
          {/* 아이디 */}
          <div className="mb-5 flex flex-col">
            <label className="mb-[5px] text-[#555]">아이디</label>
            <div className="flex items-center w-[660px]">
              <input
                type="text"
                placeholder="아이디"
                value={account}
                onChange={(e) => setAccount(e.target.value)}
                className={`flex-1 ${fieldClass}`}
              />
              <button type="button" onClick={checkAccountAvailability} className={actionBtnClass}>
                중복 확인
              </button>
            </div>
            {accountValid !== null && (
              <p className={accountValid ? 'text-green-600 mt-[5px]' : 'text-[#d32f2f] mt-[5px]'}>
                {accountValid ? '✔ 사용할 수 있는 아이디입니다.' : '✖ 이미 사용 중인 아이디입니다.'}
              </p>
            )}
          </div>

          {/* 전화번호 */}
          <div className="mb-5 flex flex-col">
            <label className="mb-[5px] text-[#555]">전화번호</label>
            <div className="flex items-center w-[660px]">
              <input
                type="text"
                placeholder="전화번호"
                value={phoneNumber}
                onChange={(e) => setPhoneNumber(e.target.value)}
                className={`flex-1 ${fieldClass}`}
              />
              <button type="button" onClick={phoneNumberAvailability} className={actionBtnClass}>
                인증하기
              </button>
            </div>
            {phoneValid !== null && (
              <p className={phoneValid ? 'text-green-600 mt-[5px]' : 'text-[#d32f2f] mt-[5px]'}>
                {phoneValid ? '✔ 사용 가능한 번호입니다.' : '✖ 이미 사용 중인 번호입니다.'}
              </p>
            )}
          </div>

          {/* 이름 */}
          <div className="mb-5 flex flex-col">
            <label className="mb-[5px] text-[#555]">이름</label>
            <input
              type="text"
              placeholder="이름"
              value={name}
              onChange={(e) => setName(e.target.value)}
              className={fieldClass}
            />
          </div>

          {/* 비밀번호 */}
          <div className="mb-5 flex flex-col">
            <label className="mb-[5px] text-[#555]">비밀번호</label>
            <input
              type="password"
              placeholder="비밀번호"
              value={password}
              onChange={(e) => {
                setPassword(e.target.value)
                setPasswordValid(validatePassword(e.target.value))
              }}
              className={fieldClass}
            />
            {password && (
              <p className={passwordValid ? 'text-green-600 mt-[5px]' : 'text-[#d32f2f] mt-[5px]'}>
                {passwordValid
                  ? '✔ 비밀번호가 유효합니다.'
                  : '✖ 비밀번호는 8~12자여야 하며, 특수문자 및 영어 대소문자를 모두 포함해야 합니다.'}
              </p>
            )}
          </div>

          {/* 비밀번호 확인 */}
          <div className="mb-5 flex flex-col">
            <label className="mb-[5px] text-[#555]">비밀번호 확인하기</label>
            <input
              type="password"
              placeholder="비밀번호 확인하기"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              className={fieldClass}
            />
            {confirmPassword && password !== confirmPassword && (
              <p className="text-[#d32f2f] mt-[5px]">✖ 비밀번호가 일치하지 않습니다.</p>
            )}
          </div>

          <button
            type="submit"
            disabled={isSubmitting}
            className="w-full p-[10px] bg-[#d1e7f5] border-none rounded-[5px] cursor-pointer hover:bg-[#bcdff1]"
          >
            {isSubmitting ? '제출 중...' : '제출하기'}
          </button>
        </form>

        {signupSuccess && (
          <p className="text-green-600 text-center mt-[10px]">
            회원가입이 성공했습니다! 로그인 페이지로 이동합니다...
          </p>
        )}
        {errorMessage && (
          <p className="text-[#d32f2f] text-center mt-[10px]">{errorMessage}</p>
        )}
      </div>
    </div>
  )
}

export default SignUpForm
