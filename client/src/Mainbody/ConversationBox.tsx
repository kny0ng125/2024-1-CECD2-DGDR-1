import { useState, useEffect, useRef } from 'react'
import { useCallStore } from '@/stores/useCallStore'
import { useHotkey } from '@/hooks/useHotkey'
import { authFetch, buildSseUrl } from '@/lib/authFetch'
import { T } from '@/lib/theme'

interface Conversation {
  id: number
  text: string
  sender: 'agent' | 'patient'
  time: string
}


function Bubble({ sender, text, time, partial = false }: {
  sender: 'agent' | 'patient'
  text: string
  time: string
  partial?: boolean
}) {
  const isAgent = sender === 'agent'
  return (
    <div style={{
      display: 'flex', flexDirection: 'column',
      alignItems: isAgent ? 'flex-end' : 'flex-start',
      marginBottom: 14,
      opacity: partial ? 0.75 : 1,
    }}>
      <div style={{
        fontSize: 10, fontWeight: 600, letterSpacing: 1,
        color: isAgent ? '#7aa8f5' : T.textMuted,
        textTransform: 'uppercase' as const, fontFamily: T.mono,
        marginBottom: 4, padding: '0 2px',
      }}>{isAgent ? '요원' : '신고자'}</div>

      <div style={{
        maxWidth: '82%',
        padding: '9px 12px',
        borderRadius: 8,
        background: isAgent ? T.accentBlueSoft : T.slateSoft,
        color: isAgent ? '#dbeafe' : '#cbd5e1',
        boxShadow: isAgent
          ? `inset 0 0 0 1px ${T.accentBlueEdge}`
          : `inset 0 0 0 1px rgba(148,163,184,0.25)`,
        fontSize: 13, lineHeight: 1.55,
        borderTopRightRadius: isAgent ? 2 : 8,
        borderTopLeftRadius:  isAgent ? 8 : 2,
      }}>
        {text}
        {partial && (
          <span style={{
            display: 'inline-block', width: 2, height: 13,
            background: T.text, marginLeft: 3, verticalAlign: -2,
            animation: 'dispatchBlink 1s steps(2) infinite',
          }} />
        )}
      </div>

      <div style={{
        fontSize: 10, color: T.textMuted, fontFamily: T.mono,
        marginTop: 4, padding: '0 2px',
      }}>{time}</div>
    </div>
  )
}

const ConversationBox = () => {
  const { callId, isCallActive, callStartedAt } = useCallStore()
  const [conversations, setConversations] = useState<Conversation[]>([])
  const [partial, setPartial]             = useState<{ text: string; sender: 'agent' | 'patient' } | null>(null)
  const [loading, setLoading]             = useState(true)
  const [error, setError]                 = useState<string | null>(null)
  const [elapsed, setElapsed]             = useState(0)
  const esRef    = useRef<EventSource | null>(null)
  const bottomRef = useRef<HTMLDivElement>(null)

  // Boot: load latest call records once
  useEffect(() => {
    const boot = async () => {
      try {
        const res = await authFetch('/api/v1/call/latest')
        if (!res.ok) throw new Error(`API error: ${res.statusText}`)
        const data = await res.json()
        setConversations(
          data.map((item: any, index: number) => ({
            id: item.id ?? index + 1,
            text: item.transcription,
            sender: item.speakerPhoneNumber === item.call?.user?.phoneNumber ? 'agent' : 'patient',
            time: new Date(item.time).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
          }))
        )
        if (data.length > 0 && data[0].call?.id) {
          useCallStore.getState().startCall(
            data[0].call.id,
            data[0].call.startTime ?? new Date().toISOString()
          )
        }
      } catch (err) {
        setError((err as Error).message)
      } finally {
        setLoading(false)
      }
    }
    boot()
  }, [])

  // SSE: subscribe when callId is set
  useEffect(() => {
    if (!callId) return
    setConversations([])
    setPartial(null)
    const url = buildSseUrl(`/api/v1/call/${callId}/transcript/stream`)
    const es  = new EventSource(url)
    esRef.current = es
    let idx = 0
    es.addEventListener('transcript', (e) => {
      const entry = JSON.parse((e as MessageEvent).data) as {
        speaker: 'agent' | 'caller'
        text: string
        isFinal: boolean
        timestamp: string
      }
      const sender: 'agent' | 'patient' = entry.speaker === 'agent' ? 'agent' : 'patient'
      if (!entry.isFinal) {
        setPartial({ text: entry.text, sender })
        return
      }
      setPartial(null)
      const nextId = idx++
      setConversations(prev => [...prev, {
        id: nextId,
        text: entry.text,
        sender,
        time: new Date(entry.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
      }])
    })
    es.addEventListener('end', () => { setPartial(null); es.close() })
    return () => { setPartial(null); es.close() }
  }, [callId])

  // Elapsed timer
  useEffect(() => {
    if (!callStartedAt) { setElapsed(0); return }
    const initial = Math.max(0, Math.floor((Date.now() - new Date(callStartedAt).getTime()) / 1000))
    setElapsed(initial)
    if (!isCallActive) return
    const i = setInterval(() => setElapsed(e => e + 1), 1000)
    return () => clearInterval(i)
  }, [callStartedAt, isCallActive])

  // Auto-scroll on new messages and partial updates
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [conversations, partial])

  useHotkey('ctrl+alt+r', () => setConversations([]))

  const mm = String(Math.floor(elapsed / 60)).padStart(2, '0')
  const ss = String(elapsed % 60).padStart(2, '0')

  if (loading) {
    return (
      <div style={{
        background: T.bgElev, borderRadius: 6,
        boxShadow: `inset 0 0 0 1px ${T.line}`,
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        height: '100%',
      }}>
        <div style={{
          width: 24, height: 24, borderRadius: '50%',
          border: `2px solid ${T.line}`, borderTopColor: T.accentBlue,
          animation: 'dispatchSpin 0.9s linear infinite',
        }} />
      </div>
    )
  }

  return (
    <div style={{
      background: T.bgElev, borderRadius: 6,
      boxShadow: `inset 0 0 0 1px ${T.line}`,
      display: 'flex', flexDirection: 'column',
      height: '100%', minHeight: 0,
    }}>
      {/* Panel header */}
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
          }}>LIVE TRANSCRIPT</div>
          <div style={{ fontSize: 14, fontWeight: 600, color: T.text, letterSpacing: -0.2, marginTop: 1 }}>
            응급 신고 통화 내용
          </div>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <button onClick={() => setConversations([])}
            style={{
              fontFamily: T.ui, cursor: 'pointer', border: 'none',
              background: 'transparent', color: T.textDim,
              fontSize: 11, padding: '4px 8px', borderRadius: 3,
              boxShadow: `inset 0 0 0 1px ${T.line}`,
            }}>새로고침</button>
        </div>
      </div>

      {/* Messages */}
      {error ? (
        <div style={{
          margin: 14, padding: 14,
          background: T.redSoft, boxShadow: `inset 0 0 0 1px ${T.redEdge}`,
          borderRadius: 6, color: '#fca5a5', fontSize: 12,
        }}>
          서버가 응답하지 않습니다.
        </div>
      ) : (
        <div className="dispatch-scroll"
          style={{ flex: 1, minHeight: 0, overflowY: 'auto', padding: '14px 14px 8px' }}>
          {conversations.length === 0 ? (
            <div style={{
              margin: '12px 0', padding: '14px',
              background: T.accentBlueSoft, boxShadow: `inset 0 0 0 1px ${T.accentBlueEdge}`,
              borderRadius: 6, color: '#93c5fd', fontSize: 12, textAlign: 'center',
            }}>
              대화 내용을 가져오는 중입니다…
            </div>
          ) : conversations.map(c => (
            <Bubble key={c.id} sender={c.sender} text={c.text} time={c.time} />
          ))}
          {partial && (
            <Bubble
              key="partial"
              sender={partial.sender}
              text={partial.text}
              time=""
              partial
            />
          )}
          <div ref={bottomRef} />
        </div>
      )}

      {/* Footer */}
      <div style={{
        padding: '10px 14px',
        borderTop: `1px solid ${T.lineSoft}`,
        display: 'flex', justifyContent: 'space-between',
        fontFamily: T.mono, fontSize: 10, color: T.textMuted, letterSpacing: 1,
        flexShrink: 0,
      }}>
        <span>CALL · {conversations.length} 발화</span>
        <span>DURATION · {mm}:{ss}</span>
      </div>
    </div>
  )
}

export default ConversationBox
