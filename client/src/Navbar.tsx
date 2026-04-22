import { useState, useEffect } from 'react'
import { useNavigate, useLocation } from 'react-router-dom'
import { useAuthStore } from '@/stores/useAuthStore'
import { useHotkey } from '@/hooks/useHotkey'
import { T } from '@/lib/theme'

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
    <div style={{
      height: 52, flexShrink: 0,
      background: T.bgElev,
      borderBottom: `1px solid ${T.line}`,
      display: 'flex', alignItems: 'center',
      padding: '0 20px', gap: 24,
      fontFamily: T.ui, color: T.text,
      position: 'relative', zIndex: 10,
    }}>
      {/* App badge + title */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
        <div style={{
          width: 26, height: 26, borderRadius: 4,
          background: T.red,
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          fontFamily: T.mono, fontSize: 11, fontWeight: 700, color: '#fff',
          letterSpacing: -0.5,
        }}>119</div>
        <div style={{ fontSize: 14, fontWeight: 600, letterSpacing: -0.2 }}>
          119 수보 시스템
          <span style={{ color: T.textMuted, fontWeight: 400, marginLeft: 8, fontSize: 11, fontFamily: T.mono }}>
            DISPATCH · v2.4
          </span>
        </div>
      </div>

      <div style={{ width: 1, height: 22, background: T.line }} />

      {/* Tabs */}
      <div style={{ display: 'flex', gap: 2 }}>
        {tabs.map(tab => {
          const active = tab.key === current
          return (
            <button key={tab.key}
              onClick={() => navigate(tab.path)}
              style={{
                border: 'none', cursor: 'pointer', fontFamily: T.ui,
                padding: '6px 12px', borderRadius: 4,
                background: active ? T.accentBlueSoft : 'transparent',
                color: active ? '#93c5fd' : T.textDim,
                fontSize: 13, fontWeight: 500,
                boxShadow: active ? `inset 0 0 0 1px ${T.accentBlueEdge}` : 'none',
                transition: 'background .12s, color .12s',
              }}>
              {tab.label}
            </button>
          )
        })}
      </div>

      <div style={{ flex: 1 }} />

      {/* Right side */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 18, fontSize: 12 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
          <span style={{
            display: 'inline-block',
            width: 6, height: 6, borderRadius: 3,
            background: T.green, boxShadow: `0 0 8px ${T.green}`,
          }} />
          <span style={{ fontFamily: T.mono, color: T.textDim }}>SHIFT · DAY</span>
        </div>

        {userId && (
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <div style={{
              width: 22, height: 22, borderRadius: 11,
              background: T.accentBlueSoft, color: '#93c5fd',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              fontSize: 11, fontWeight: 600,
              boxShadow: `inset 0 0 0 1px ${T.accentBlueEdge}`,
            }}>{agentInitial}</div>
            <span style={{ color: T.text, fontSize: 13 }}>
              {agentName} <span style={{ color: T.textMuted }}>요원</span>
            </span>
          </div>
        )}

        <div style={{ fontFamily: T.mono, color: T.text, fontSize: 13, letterSpacing: 0.5 }}>
          {fmtClock(now)}
        </div>
      </div>
    </div>
  )
}

export default NavbarForm
