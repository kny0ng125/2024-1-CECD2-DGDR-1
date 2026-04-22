import { useEffect } from 'react'
import { useManualStore } from '@/stores/useManualStore'
import { useCallStore } from '@/stores/useCallStore'
import { T } from '@/lib/theme'

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
    <div style={{
      display: 'flex', alignItems: 'center',
      padding: '12px 14px',
      borderBottom: `1px solid ${T.lineSoft}`,
      flexShrink: 0,
    }}>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{
          fontSize: 10, fontWeight: 600, letterSpacing: 1.6,
          textTransform: 'uppercase' as const, color: T.textMuted, fontFamily: T.mono,
        }}>{subtitle}</div>
        <div style={{ fontSize: 14, fontWeight: 600, color: T.text, letterSpacing: -0.2, marginTop: 1 }}>{title}</div>
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
    <div style={{ display: 'flex', flexDirection: 'column', flex: 1, minHeight: 0 }}>
      {/* Progress bar */}
      <div style={{ padding: '8px 14px 10px', borderBottom: `1px solid ${T.lineSoft}`, flexShrink: 0 }}>
        <div style={{
          display: 'flex', justifyContent: 'space-between',
          fontFamily: T.mono, fontSize: 10, letterSpacing: 0.8,
          color: T.textMuted, marginBottom: 5,
        }}>
          <span>{doneCount}<span style={{ color: T.textMuted }}>/{protocols.length}</span> 완료</span>
          <span>{pct}%</span>
        </div>
        <div style={{ height: 4, background: T.lineSoft, borderRadius: 2, overflow: 'hidden' }}>
          <div style={{
            width: `${pct}%`, height: '100%',
            background: pct === 100 ? T.green : T.accentBlue,
            borderRadius: 2,
            transition: 'width .3s',
          }} />
        </div>
      </div>

      {/* Steps */}
      <div className="dispatch-scroll" style={{ padding: '8px 0 4px', overflowY: 'auto', flex: 1 }}>
        {protocols.map((p, i) => {
          const isDone   = p.completed
          const isActive = !isDone && firstUndone?.id === p.id
          const isLast   = i === protocols.length - 1
          return (
            <div key={p.id} style={{ display: 'flex', gap: 10, position: 'relative', padding: '4px 14px' }}>
              {!isLast && (
                <div style={{
                  position: 'absolute', left: 14 + 9, top: 28, bottom: -4,
                  width: 1, background: isDone ? T.green : T.line,
                }} />
              )}
              {/* step dot */}
              <div style={{
                width: 18, height: 18, borderRadius: 10, flexShrink: 0,
                background: isDone ? T.green : isActive ? T.accentBlue : T.bgCard,
                color: isDone || isActive ? '#0b0d10' : T.textMuted,
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                fontFamily: T.mono, fontSize: 10, fontWeight: 800,
                boxShadow: isActive
                  ? `0 0 0 3px ${T.accentBlueSoft}`
                  : isDone ? 'none'
                  : `inset 0 0 0 1px ${T.line}`,
                marginTop: 4,
                animation: isActive ? 'dispatchPulse 2s infinite' : 'none',
              }}>
                {isDone ? '✓' : i + 1}
              </div>

              <button
                onClick={() => onToggle(p.id)}
                style={{
                  flex: 1, textAlign: 'left', cursor: 'pointer',
                  background: isActive ? T.accentBlueSoft : 'transparent',
                  border: 'none', borderRadius: 4,
                  padding: '4px 8px',
                  display: 'flex', flexDirection: 'column', gap: 2,
                  boxShadow: isActive ? `inset 0 0 0 1px ${T.accentBlueEdge}` : 'none',
                }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <span style={{
                    fontSize: 13, fontWeight: 600,
                    color: isDone ? T.textDim : T.text,
                    textDecoration: isDone ? 'line-through' : 'none',
                    textDecorationColor: T.textMuted,
                    letterSpacing: -0.2,
                  }}>{p.text}</span>
                  <span style={{ flex: 1 }} />
                  <span style={{ fontFamily: T.mono, fontSize: 9, color: T.textMuted }}>Alt+{i + 1}</span>
                </div>
                <div style={{ fontFamily: T.mono, fontSize: 10, color: T.textMuted }}>
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
    <div style={{ display: 'flex', flexDirection: 'column', gap: 10, height: '100%', minHeight: 0 }}>

      {/* Panel A — Viewed manuals */}
      <div style={{
        background: T.bgElev, borderRadius: 6,
        boxShadow: `inset 0 0 0 1px ${T.line}`,
        display: 'flex', flexDirection: 'column',
        flex: '0 0 auto', maxHeight: '40%',
      }}>
        <PanelHeader
          title="열람 매뉴얼 목록"
          subtitle="VIEWED MANUALS"
          trailing={
            <span style={{ fontFamily: T.mono, fontSize: 10, color: T.textMuted, letterSpacing: 1 }}>
              {savedManuals.length} · SESSION
            </span>
          }
        />
        <div className="dispatch-scroll" style={{
          padding: 12, overflowY: 'auto',
          display: 'flex', flexWrap: 'wrap', gap: 6, alignContent: 'flex-start',
        }}>
          {savedManuals.length === 0 ? (
            <div style={{
              width: '100%', padding: '16px 8px',
              color: T.textMuted, fontSize: 12, textAlign: 'center',
            }}>
              조회된 매뉴얼이 없습니다
            </div>
          ) : savedManuals.map(m => (
            <div key={m.id} style={{
              background: T.accentBlue, color: '#fff',
              borderRadius: 4,
              display: 'inline-flex', alignItems: 'stretch',
              overflow: 'hidden',
            }}>
              <button onClick={() => selectManual(m)}
                style={{
                  cursor: 'pointer', fontFamily: T.ui, border: 'none',
                  background: 'transparent', color: 'inherit',
                  fontSize: 12, fontWeight: 500,
                  padding: '5px 4px 5px 10px',
                  display: 'inline-flex', alignItems: 'center', gap: 6,
                }}>
                <span style={{ fontFamily: T.mono, fontSize: 9, opacity: 0.7 }}>#{String(m.id).padStart(3, '0')}</span>
                {m.title}
              </button>
              <button onClick={() => removeManual(m.id)}
                title="열람 목록에서 제거"
                style={{
                  cursor: 'pointer', fontFamily: T.mono, border: 'none',
                  background: 'rgba(0,0,0,0.15)', color: 'rgba(255,255,255,0.85)',
                  fontSize: 13, fontWeight: 700, lineHeight: 1,
                  padding: '0 8px',
                }}>×</button>
            </div>
          ))}
        </div>
      </div>

      {/* Panel B — Protocol wizard */}
      <div style={{
        background: T.bgElev, borderRadius: 6,
        boxShadow: `inset 0 0 0 1px ${T.line}`,
        display: 'flex', flexDirection: 'column',
        flex: 1, minHeight: 0,
      }}>
        <PanelHeader
          title="수보 프로토콜 진행"
          subtitle="PROTOCOL · STEPPER"
          trailing={
            <span style={{ fontFamily: T.mono, fontSize: 11, color: T.textDim, letterSpacing: 0.5 }}>
              {protocols.filter(p => p.completed).length}
              <span style={{ color: T.textMuted }}>/{protocols.length}</span>
            </span>
          }
        />
        <ProtocolWizard protocols={protocols} onToggle={toggleProtocol} />
      </div>
    </div>
  )
}

export default Sidebar
