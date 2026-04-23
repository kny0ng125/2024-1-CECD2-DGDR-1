import { useState, useEffect } from 'react'
import { useCallStore } from '@/stores/useCallStore'

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
    <div
      className={`flex items-center gap-5 py-1.5 px-4 bg-dispatch-card border-b border-dispatch-line font-ui min-h-[34px] text-dispatch-text text-xs shrink-0 ${
        isCallActive
          ? 'shadow-[inset_3px_0_0_#ef4444]'
          : 'shadow-[inset_3px_0_0_#242935]'
      }`}
    >
      {/* Caller indicator */}
      <div className="flex items-center gap-2">
        <span
          className={`inline-flex items-center justify-center w-[18px] h-[18px] rounded text-[10px] ring-1 ring-inset ${
            isCallActive
              ? 'bg-dispatch-blue-soft text-[#93c5fd] ring-dispatch-blue-edge'
              : 'bg-transparent text-dispatch-textMuted ring-dispatch-line'
          }`}
        >
          ☎
        </span>
        <span className="font-mono text-[9px] tracking-[1.2px] text-dispatch-textMuted">발신번호</span>
        <span className="font-mono text-[13px] font-semibold text-dispatch-text">
          {isCallActive ? '통화 중' : '대기 중'}
        </span>
      </div>

      <div className="flex-1" />

      {/* elapsed + LIVE badge */}
      <div className="flex items-center gap-2">
        <span className="font-mono text-[9px] text-dispatch-textMuted tracking-[1.2px]">경과</span>
        <span
          className={`font-mono text-sm font-bold tabular-nums ${
            elapsed > 180 ? 'text-dispatch-amber' : 'text-dispatch-text'
          }`}
        >
          {fmtElapsed(elapsed)}
        </span>
        {isCallActive && (
          <span className="inline-flex items-center gap-1 font-mono text-[9px] font-bold tracking-[1.4px] text-dispatch-red ml-1">
            <span
              className="w-1.5 h-1.5 rounded-full bg-dispatch-red"
              style={{ animation: 'dispatchPulse 1.6s infinite' }}
            />
            LIVE
          </span>
        )}
      </div>
    </div>
  )
}

export default IncidentHeader
