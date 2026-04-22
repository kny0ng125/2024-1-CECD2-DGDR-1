import { useState, useEffect } from 'react'
import { authFetch } from '@/lib/authFetch'
import { T } from '@/lib/theme'

interface Call { id: number; startTime: string }

interface CallRecord {
  id: number
  transcription: string
  speakerPhoneNumber: string
  time: string
  call?: { user?: { phoneNumber: string } }
}

function PanelHeader({ title, subtitle, trailing }: {
  title: string; subtitle: string; trailing?: React.ReactNode
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

function Bubble({ isAgent, text, time }: { isAgent: boolean; text: string; time: string }) {
  return (
    <div style={{
      display: 'flex', flexDirection: 'column',
      alignItems: isAgent ? 'flex-end' : 'flex-start',
      marginBottom: 14,
    }}>
      <div style={{
        fontSize: 10, fontWeight: 600, letterSpacing: 1,
        color: isAgent ? '#7aa8f5' : T.textMuted,
        textTransform: 'uppercase' as const, fontFamily: T.mono,
        marginBottom: 4, padding: '0 2px',
      }}>{isAgent ? '요원' : '신고자'}</div>
      <div style={{
        maxWidth: '82%', padding: '9px 12px', borderRadius: 8,
        background: isAgent ? T.accentBlueSoft : T.slateSoft,
        color: isAgent ? '#dbeafe' : '#cbd5e1',
        boxShadow: isAgent
          ? `inset 0 0 0 1px ${T.accentBlueEdge}`
          : `inset 0 0 0 1px rgba(148,163,184,0.25)`,
        fontSize: 13, lineHeight: 1.55,
        borderTopRightRadius: isAgent ? 2 : 8,
        borderTopLeftRadius:  isAgent ? 8 : 2,
      }}>{text}</div>
      <div style={{ fontSize: 10, color: T.textMuted, fontFamily: T.mono, marginTop: 4, padding: '0 2px' }}>{time}</div>
    </div>
  )
}

const CallHistory = () => {
  const today = new Date().toISOString().split('T')[0]
  const [selectedDate, setSelectedDate] = useState(today)
  const [callList, setCallList]         = useState<Call[]>([])
  const [records, setRecords]           = useState<CallRecord[] | null>(null)
  const [selectedCallId, setSelectedCallId] = useState<number | null>(null)
  const [loading, setLoading]           = useState(false)

  const fetchCallList = async (dateStr: string) => {
    setLoading(true)
    try {
      const res  = await authFetch(`/api/v1/call/date?startDate=${dateStr}&endDate=${dateStr}`)
      const data = await res.json()
      setCallList(data)
    } catch {
      setCallList([])
    } finally {
      setLoading(false)
    }
  }

  const fetchCallDetails = async (callId: number) => {
    setLoading(true)
    try {
      const res  = await authFetch(`/api/v1/${callId}/call-record`)
      const data = await res.json()
      setRecords(data)
    } catch {
      setRecords(null)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { fetchCallList(selectedDate) }, [])

  const handleDateChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const d = e.target.value
    setSelectedDate(d)
    fetchCallList(d)
  }

  const handleCallClick = (id: number) => {
    setSelectedCallId(id)
    fetchCallDetails(id)
  }

  const selected  = callList.find(c => c.id === selectedCallId)
  const totalSec  = records ? records.length * 7 + 12 : 0
  const mm        = String(Math.floor(totalSec / 60)).padStart(2, '0')
  const ss        = String(totalSec % 60).padStart(2, '0')

  return (
    <div style={{
      height: 'calc(100vh - 52px)',
      background: T.bg,
      display: 'grid',
      gridTemplateColumns: '320px 1fr',
      gap: 10, padding: 10,
      fontFamily: T.ui, color: T.text,
      boxSizing: 'border-box',
    }}>
      {/* LEFT — Call list */}
      <div style={{
        background: T.bgElev, borderRadius: 6,
        boxShadow: `inset 0 0 0 1px ${T.line}`,
        display: 'flex', flexDirection: 'column', minHeight: 0,
      }}>
        <PanelHeader
          title="통화 기록"
          subtitle="CALL HISTORY"
          trailing={
            <span style={{ fontFamily: T.mono, fontSize: 10, color: T.textMuted, letterSpacing: 1 }}>
              {callList.length} CALLS
            </span>
          }
        />

        <div style={{ padding: 12, borderBottom: `1px solid ${T.lineSoft}`, flexShrink: 0 }}>
          <label style={{
            display: 'block', fontFamily: T.mono, fontSize: 9,
            color: T.textMuted, letterSpacing: 1.4, marginBottom: 5,
          }}>DATE · 조회 날짜</label>
          <input
            type="date"
            value={selectedDate}
            onChange={handleDateChange}
            style={{
              width: '100%', fontFamily: T.mono, fontSize: 13,
              padding: '8px 10px',
              background: T.bgCard, color: T.text,
              border: 'none', borderRadius: 4,
              boxShadow: `inset 0 0 0 1px ${T.line}`,
              outline: 'none', colorScheme: 'dark',
              boxSizing: 'border-box',
            }}
          />
        </div>

        <div className="dispatch-scroll" style={{ flex: 1, minHeight: 0, overflowY: 'auto', padding: 8 }}>
          {loading ? (
            <div style={{ display: 'flex', justifyContent: 'center', padding: 40 }}>
              <div style={{
                width: 20, height: 20, borderRadius: '50%',
                border: `2px solid ${T.line}`, borderTopColor: T.accentBlue,
                animation: 'dispatchSpin 0.9s linear infinite',
              }} />
            </div>
          ) : callList.length === 0 ? (
            <div style={{ padding: '24px 8px', color: T.textMuted, fontSize: 12, textAlign: 'center' }}>
              해당 날짜의 통화 기록이 없습니다.
            </div>
          ) : callList.map(c => {
            const active   = c.id === selectedCallId
            const callTime = new Date(c.startTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
            return (
              <button key={c.id}
                onClick={() => handleCallClick(c.id)}
                style={{
                  width: '100%', display: 'block', textAlign: 'left',
                  border: 'none', cursor: 'pointer', fontFamily: T.ui,
                  background: active ? T.accentBlueSoft : 'transparent',
                  padding: '10px 10px 10px 14px',
                  borderRadius: 4, marginBottom: 2,
                  position: 'relative', color: T.text,
                }}>
                {active && (
                  <span style={{
                    position: 'absolute', left: 0, top: 6, bottom: 6,
                    width: 3, background: T.accentBlue, borderRadius: 2,
                  }} />
                )}
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: 3 }}>
                  <span style={{
                    fontFamily: T.mono, fontSize: 15, fontWeight: 600,
                    color: active ? '#dbeafe' : T.text, letterSpacing: 0.3,
                  }}>{callTime}</span>
                  <span style={{ fontFamily: T.mono, fontSize: 10, color: T.textMuted }}>
                    #{String(c.id).padStart(4, '0')}
                  </span>
                </div>
                <div style={{ fontFamily: T.mono, fontSize: 9.5, color: T.textMuted, letterSpacing: 0.5 }}>
                  {selectedDate.replace(/-/g, '.')}
                </div>
              </button>
            )
          })}
        </div>
      </div>

      {/* RIGHT — Transcript */}
      <div style={{
        background: T.bgElev, borderRadius: 6,
        boxShadow: `inset 0 0 0 1px ${T.line}`,
        display: 'flex', flexDirection: 'column', minHeight: 0,
      }}>
        {!selected || !records ? (
          <div style={{
            flex: 1, display: 'flex', flexDirection: 'column',
            alignItems: 'center', justifyContent: 'center',
            color: T.textMuted, gap: 16,
          }}>
            <div style={{
              width: 64, height: 64, borderRadius: 32,
              background: T.bgCard, boxShadow: `inset 0 0 0 1px ${T.line}`,
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              fontFamily: T.mono, fontSize: 20, color: T.textMuted,
            }}>◇</div>
            <div style={{ fontSize: 14 }}>통화 기록을 선택해 주세요</div>
            <div style={{ fontFamily: T.mono, fontSize: 10, letterSpacing: 1 }}>← SELECT A CALL FROM THE LIST</div>
          </div>
        ) : (
          <>
            <PanelHeader
              title={`#${String(selected.id).padStart(4, '0')} 통화`}
              subtitle={`CALL · ${selectedDate.replace(/-/g, '.')}`}
              trailing={
                <div style={{ fontFamily: T.mono, fontSize: 11, color: T.textDim, textAlign: 'right' }}>
                  <div>{new Date(selected.startTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</div>
                  <div style={{ fontSize: 9, color: T.textMuted, letterSpacing: 1, marginTop: 2 }}>
                    DURATION · {mm}:{ss}
                  </div>
                </div>
              }
            />

            <div className="dispatch-scroll" style={{ flex: 1, minHeight: 0, overflowY: 'auto', padding: '18px 24px' }}>
              <div style={{
                textAlign: 'center', marginBottom: 18,
                fontFamily: T.mono, fontSize: 10, letterSpacing: 1.5, color: T.textMuted,
              }}>
                ── {selectedDate.replace(/-/g, '.')} · {new Date(selected.startTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })} 통화 시작 ──
              </div>

              {records.length === 0 ? (
                <div style={{ textAlign: 'center', padding: 40, color: T.textMuted, fontSize: 12 }}>
                  전사 데이터가 없습니다.
                </div>
              ) : records.map(entry => (
                <Bubble key={entry.id}
                  isAgent={entry.speakerPhoneNumber === entry.call?.user?.phoneNumber}
                  text={entry.transcription}
                  time={new Date(entry.time).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                />
              ))}
            </div>

            <div style={{
              padding: '12px 24px',
              borderTop: `1px solid ${T.lineSoft}`,
              display: 'flex', justifyContent: 'space-between',
              fontFamily: T.mono, fontSize: 11, color: T.textDim, letterSpacing: 0.5,
              flexShrink: 0,
            }}>
              <span>총 {records.length}개 발화</span>
              <span>{mm}분 {ss}초</span>
            </div>
          </>
        )}
      </div>
    </div>
  )
}

export default CallHistory
