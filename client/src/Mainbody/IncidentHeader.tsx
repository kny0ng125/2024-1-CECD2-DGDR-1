import { useState, useEffect } from 'react'
import { useCallStore } from '@/stores/useCallStore'
import { T } from '@/lib/theme'

function fmtElapsed(sec: number): string {
  const h = Math.floor(sec / 3600)
  const m = Math.floor((sec % 3600) / 60)
  const s = sec % 60
  const pad = (n: number) => String(n).padStart(2, '0')
  return h > 0 ? `${h}:${pad(m)}:${pad(s)}` : `${pad(m)}:${pad(s)}`
}

const IncidentHeader = () => {
  const { isCallActive, callStartedAt } = useCallStore()
  const [elapsed, setElapsed] = useState(0)

  useEffect(() => {
    if (!callStartedAt) { setElapsed(0); return }
    const initial = Math.max(0, Math.floor((Date.now() - new Date(callStartedAt).getTime()) / 1000))
    setElapsed(initial)
    if (!isCallActive) return
    const i = setInterval(() => setElapsed(e => e + 1), 1000)
    return () => clearInterval(i)
  }, [callStartedAt, isCallActive])

  return (
    <div style={{
      display: 'flex', alignItems: 'center', gap: 20,
      padding: '6px 16px',
      background: T.bgCard,
      borderBottom: `1px solid ${T.line}`,
      boxShadow: `inset 3px 0 0 ${isCallActive ? T.red : T.line}`,
      fontFamily: T.ui,
      minHeight: 34,
      color: T.text,
      fontSize: 12,
      flexShrink: 0,
    }}>
      {/* Caller indicator */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        <span style={{
          display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
          width: 18, height: 18, borderRadius: 4,
          background: isCallActive ? T.accentBlueSoft : 'transparent',
          color: isCallActive ? '#93c5fd' : T.textMuted,
          boxShadow: `inset 0 0 0 1px ${isCallActive ? T.accentBlueEdge : T.line}`,
          fontSize: 10,
        }}>☎</span>
        <span style={{ fontFamily: T.mono, fontSize: 9, letterSpacing: 1.2, color: T.textMuted }}>발신번호</span>
        <span style={{ fontFamily: T.mono, fontSize: 13, fontWeight: 600, color: T.text }}>
          {isCallActive ? '통화 중' : '대기 중'}
        </span>
      </div>

      <div style={{ flex: 1 }} />

      {/* elapsed + LIVE badge */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        <span style={{ fontFamily: T.mono, fontSize: 9, color: T.textMuted, letterSpacing: 1.2 }}>경과</span>
        <span style={{
          fontFamily: T.mono, fontSize: 14, fontWeight: 700,
          color: elapsed > 180 ? T.amber : T.text,
          fontVariantNumeric: 'tabular-nums',
        }}>
          {fmtElapsed(elapsed)}
        </span>
        {isCallActive && (
          <span style={{
            display: 'inline-flex', alignItems: 'center', gap: 4,
            fontFamily: T.mono, fontSize: 9, fontWeight: 700,
            letterSpacing: 1.4, color: T.red,
            marginLeft: 4,
          }}>
            <span style={{
              width: 6, height: 6, borderRadius: 3,
              background: T.red,
              animation: 'dispatchPulse 1.6s infinite',
            }} />
            LIVE
          </span>
        )}
      </div>
    </div>
  )
}

export default IncidentHeader
