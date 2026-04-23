import { useState, useEffect } from 'react'
import { useNavigate, useLocation } from 'react-router-dom'
import { useAuthStore } from '@/stores/useAuthStore'
import { useHotkey } from '@/hooks/useHotkey'

function useClock() {
  const [t, setT] = useState(new Date())
  useEffect(() => {
    const i = setInterval(() => setT(new Date()), 1000)
    return () => clearInterval(i)
  }, [])
  return t
}

function fmtClock(d: Date): string {
  return [d.getHours(), d.getMinutes(), d.getSeconds()]
    .map(n => String(n).padStart(2, '0'))
    .join(':')
}

const NavbarForm = () => {
  const navigate   = useNavigate()
  const location   = useLocation()
  const userId     = useAuthStore(s => s.userId)
  const now        = useClock()

  const current =
    location.pathname === '/history'  ? 'history'  :
    location.pathname === '/hospital' ? 'hospital' : 'main'

  useHotkey('shift+1', () => navigate('/history'))
  useHotkey('shift+2', () => navigate('/'))
  useHotkey('shift+3', () => navigate('/hospital'))

  const tabs = [
    { key: 'history',  label: 'History',  path: '/history'  },
    { key: 'main',     label: 'Main',     path: '/'         },
    { key: 'hospital', label: '병상 확인', path: '/hospital' },
  ]

  const agentName    = userId ? userId.slice(0, 4) : '요원'
  const agentInitial = agentName[0]?.toUpperCase() ?? '?'

  const isAuthPage = location.pathname === '/login' || location.pathname === '/signup'
  if (isAuthPage) return null

  return (
    <div className="h-[52px] shrink-0 bg-dispatch-elev border-b border-dispatch-line flex items-center px-5 gap-6 font-ui text-dispatch-text relative z-10">
      {/* App badge + title */}
      <div className="flex items-center gap-2.5">
        <div className="w-[26px] h-[26px] rounded bg-dispatch-red flex items-center justify-center font-mono text-[11px] font-bold text-white tracking-[-0.5px]">
          119
        </div>
        <div className="text-sm font-semibold tracking-[-0.2px]">
          119 수보 시스템
          <span className="text-dispatch-textMuted font-normal ml-2 text-[11px] font-mono">
            DISPATCH · v2.4
          </span>
        </div>
      </div>

      <div className="w-px h-[22px] bg-dispatch-line" />

      {/* Tabs */}
      <div className="flex gap-0.5">
        {tabs.map(tab => {
          const active = tab.key === current
          return (
            <button
              key={tab.key}
              onClick={() => navigate(tab.path)}
              className={`border-0 cursor-pointer font-ui py-1.5 px-3 rounded text-[13px] font-medium transition-colors duration-[120ms] ${
                active
                  ? 'bg-dispatch-blue-soft text-[#93c5fd] ring-1 ring-inset ring-dispatch-blue-edge'
                  : 'bg-transparent text-dispatch-textDim'
              }`}
            >
              {tab.label}
            </button>
          )
        })}
      </div>

      <div className="flex-1" />

      {/* Right side */}
      <div className="flex items-center gap-[18px] text-xs">
        <div className="flex items-center gap-1.5">
          <span
            className="inline-block w-1.5 h-1.5 rounded-full bg-dispatch-green"
            style={{ boxShadow: '0 0 8px #22c55e' }}
          />
          <span className="font-mono text-dispatch-textDim">SHIFT · DAY</span>
        </div>

        {userId && (
          <div className="flex items-center gap-2">
            <div className="w-[22px] h-[22px] rounded-full bg-dispatch-blue-soft text-[#93c5fd] flex items-center justify-center text-[11px] font-semibold ring-1 ring-inset ring-dispatch-blue-edge">
              {agentInitial}
            </div>
            <span className="text-dispatch-text text-[13px]">
              {agentName} <span className="text-dispatch-textMuted">요원</span>
            </span>
          </div>
        )}

        <div className="font-mono text-dispatch-text text-[13px] tracking-[0.5px]">
          {fmtClock(now)}
        </div>
      </div>
    </div>
  )
}

export default NavbarForm
