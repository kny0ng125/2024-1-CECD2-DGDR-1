import { useEffect } from 'react'
import { useManualStore } from '@/stores/useManualStore'
import { useCallStore } from '@/stores/useCallStore'

const HINTS: Record<number, string> = {
  1: '의식 · 호흡 · 맥박',
  2: '위치 · 안전 · 목격자',
  3: '응급처치 안내 · 자세 조정',
  4: '출동 팀 배정 · 도착 예정',
}

function PanelHeader({ title, subtitle, trailing }: {
  title: string
  subtitle: string
  trailing?: React.ReactNode
}) {
  return (
    <div className="flex items-center px-3.5 py-3 border-b border-dispatch-lineSoft shrink-0">
      <div className="flex-1 min-w-0">
        <div className="text-[10px] font-semibold tracking-[1.6px] uppercase text-dispatch-textMuted font-mono">
          {subtitle}
        </div>
        <div className="text-sm font-semibold text-dispatch-text tracking-[-0.2px] mt-px">{title}</div>
      </div>
      {trailing}
    </div>
  )
}

function ProtocolWizard({ protocols, onToggle }: {
  protocols: { id: number; text: string; completed: boolean }[]
  onToggle: (id: number) => void
}) {
  const doneCount  = protocols.filter(p => p.completed).length
  const pct        = Math.round((doneCount / protocols.length) * 100)
  const firstUndone = protocols.find(p => !p.completed)

  return (
    <div className="flex flex-col flex-1 min-h-0">
      {/* Progress bar */}
      <div className="pt-2 pb-2.5 px-3.5 border-b border-dispatch-lineSoft shrink-0">
        <div className="flex justify-between font-mono text-[10px] tracking-[0.8px] text-dispatch-textMuted mb-[5px]">
          <span>{doneCount}<span className="text-dispatch-textMuted">/{protocols.length}</span> 완료</span>
          <span>{pct}%</span>
        </div>
        <div className="h-1 bg-dispatch-lineSoft rounded-sm overflow-hidden">
          <div
            className={`h-full rounded-sm transition-[width] duration-300 ${pct === 100 ? 'bg-dispatch-green' : 'bg-dispatch-blue'}`}
            style={{ width: `${pct}%` }}
          />
        </div>
      </div>

      {/* Steps */}
      <div className="dispatch-scroll pt-2 pb-1 overflow-y-auto flex-1">
        {protocols.map((p, i) => {
          const isDone   = p.completed
          const isActive = !isDone && firstUndone?.id === p.id
          const isLast   = i === protocols.length - 1
          return (
            <div key={p.id} className="flex gap-2.5 relative py-1 px-3.5">
              {!isLast && (
                <div
                  className={`absolute w-px ${isDone ? 'bg-dispatch-green' : 'bg-dispatch-line'}`}
                  style={{ left: 23, top: 28, bottom: -4 }}
                />
              )}
              {/* step dot */}
              <div
                className={`w-[18px] h-[18px] rounded-full shrink-0 flex items-center justify-center font-mono text-[10px] font-extrabold mt-1 ${
                  isDone
                    ? 'bg-dispatch-green text-[#0b0d10]'
                    : isActive
                    ? 'bg-dispatch-blue text-[#0b0d10] ring-[3px] ring-dispatch-blue-soft'
                    : 'bg-dispatch-card text-dispatch-textMuted ring-1 ring-inset ring-dispatch-line'
                }`}
                style={isActive ? { animation: 'dispatchPulse 2s infinite' } : undefined}
              >
                {isDone ? '✓' : i + 1}
              </div>

              <button
                onClick={() => onToggle(p.id)}
                className={`flex-1 text-left cursor-pointer border-0 rounded py-1 px-2 flex flex-col gap-0.5 ${
                  isActive
                    ? 'bg-dispatch-blue-soft ring-1 ring-inset ring-dispatch-blue-edge'
                    : 'bg-transparent'
                }`}
              >
                <div className="flex items-center gap-2">
                  <span
                    className={`text-[13px] font-semibold tracking-[-0.2px] ${
                      isDone ? 'text-dispatch-textDim line-through decoration-dispatch-textMuted' : 'text-dispatch-text'
                    }`}
                  >
                    {p.text}
                  </span>
                  <span className="flex-1" />
                  <span className="font-mono text-[9px] text-dispatch-textMuted">Alt+{i + 1}</span>
                </div>
                <div className="font-mono text-[10px] text-dispatch-textMuted">
                  {HINTS[p.id] ?? ''}
                </div>
              </button>
            </div>
          )
        })}
      </div>
    </div>
  )
}

const Sidebar = () => {
  const { savedManuals, selectManual, removeManual } = useManualStore()
  const { protocols, toggleProtocol }               = useCallStore()

  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if (!e.altKey) return
      const n = parseInt(e.key, 10)
      if (n >= 1 && n <= 4) { e.preventDefault(); toggleProtocol(n) }
    }
    window.addEventListener('keydown', handler)
    return () => window.removeEventListener('keydown', handler)
  }, [toggleProtocol])

  return (
    <div className="flex flex-col gap-2.5 h-full min-h-0">

      {/* Panel A — Viewed manuals */}
      <div className="bg-dispatch-elev rounded-md ring-1 ring-inset ring-dispatch-line flex flex-col flex-none max-h-[40%]">
        <PanelHeader
          title="열람 매뉴얼 목록"
          subtitle="VIEWED MANUALS"
          trailing={
            <span className="font-mono text-[10px] text-dispatch-textMuted tracking-[1px]">
              {savedManuals.length} · SESSION
            </span>
          }
        />
        <div className="dispatch-scroll p-3 overflow-y-auto flex flex-wrap gap-1.5 content-start">
          {savedManuals.length === 0 ? (
            <div className="w-full py-4 px-2 text-dispatch-textMuted text-xs text-center">
              조회된 매뉴얼이 없습니다
            </div>
          ) : savedManuals.map(m => (
            <div key={m.id} className="bg-dispatch-blue text-white rounded inline-flex items-stretch overflow-hidden">
              <button
                onClick={() => selectManual(m)}
                className="cursor-pointer font-ui border-0 bg-transparent text-inherit text-xs font-medium py-[5px] pl-2.5 pr-1 inline-flex items-center gap-1.5"
              >
                <span className="font-mono text-[9px] opacity-70">#{String(m.id).padStart(3, '0')}</span>
                {m.title}
              </button>
              <button
                onClick={() => removeManual(m.id)}
                title="열람 목록에서 제거"
                className="cursor-pointer font-mono border-0 bg-black/15 text-white/85 text-[13px] font-bold leading-none px-2"
              >×</button>
            </div>
          ))}
        </div>
      </div>

      {/* Panel B — Protocol wizard */}
      <div className="bg-dispatch-elev rounded-md ring-1 ring-inset ring-dispatch-line flex flex-col flex-1 min-h-0">
        <PanelHeader
          title="수보 프로토콜 진행"
          subtitle="PROTOCOL · STEPPER"
          trailing={
            <span className="font-mono text-[11px] text-dispatch-textDim tracking-[0.5px]">
              {protocols.filter(p => p.completed).length}
              <span className="text-dispatch-textMuted">/{protocols.length}</span>
            </span>
          }
        />
        <ProtocolWizard protocols={protocols} onToggle={toggleProtocol} />
      </div>
    </div>
  )
}

export default Sidebar
