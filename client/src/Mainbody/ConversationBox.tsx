import { useState, useEffect, useRef } from 'react'
import { useCallStore } from '@/stores/useCallStore'
import { useHotkey } from '@/hooks/useHotkey'
import { buildSseUrl } from '@/lib/authFetch'

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
    <div
      className={`flex flex-col mb-3.5 ${isAgent ? 'items-end' : 'items-start'} ${partial ? 'opacity-75' : ''}`}
    >
      <div
        className={`text-[10px] font-semibold tracking-[1px] uppercase font-mono mb-1 px-0.5 ${
          isAgent ? 'text-[#7aa8f5]' : 'text-dispatch-textMuted'
        }`}
      >
        {isAgent ? '요원' : '신고자'}
      </div>

      <div
        className={`max-w-[82%] py-[9px] px-3 rounded-lg text-[13px] leading-[1.55] ring-1 ring-inset ${
          isAgent
            ? 'bg-dispatch-blue-soft text-[#dbeafe] ring-dispatch-blue-edge rounded-tr-sm'
            : 'bg-dispatch-slateSoft text-[#cbd5e1] ring-[rgba(148,163,184,0.25)] rounded-tl-sm'
        }`}
      >
        {text}
        {partial && (
          <span
            className="inline-block w-0.5 h-[13px] bg-dispatch-text ml-[3px] align-[-2px]"
            style={{ animation: 'dispatchBlink 1s steps(2) infinite' }}
          />
        )}
      </div>

      <div className="text-[10px] text-dispatch-textMuted font-mono mt-1 px-0.5">{time}</div>
    </div>
  )
}

const ConversationBox = () => {
  const { callId, isCallActive, callStartedAt } = useCallStore()
  const [conversations, setConversations] = useState<Conversation[]>([])
  const [partials, setPartials]           = useState<Map<'agent' | 'patient', string>>(new Map())
  const [elapsed, setElapsed]             = useState(0)
  const esRef    = useRef<EventSource | null>(null)
  const bottomRef = useRef<HTMLDivElement>(null)

  // SSE: subscribe when callId is set
  useEffect(() => {
    if (!callId) return
    setConversations([])
    setPartials(new Map())
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
        setPartials(prev => new Map(prev).set(sender, entry.text))
        return
      }
      setPartials(prev => { const next = new Map(prev); next.delete(sender); return next })
      const nextId = idx++
      setConversations(prev => [...prev, {
        id: nextId,
        text: entry.text,
        sender,
        time: new Date(entry.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
      }])
    })
    es.addEventListener('end', () => { setPartials(new Map()); es.close() })
    return () => { setPartials(new Map()); es.close() }
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
  }, [conversations, partials])

  useHotkey('ctrl+alt+r', () => setConversations([]))

  const mm = String(Math.floor(elapsed / 60)).padStart(2, '0')
  const ss = String(elapsed % 60).padStart(2, '0')
  const active = !!callId
  const hasContent = conversations.length > 0 || partials.size > 0

  return (
    <div className="bg-dispatch-elev rounded-md ring-1 ring-inset ring-dispatch-line flex flex-col h-full min-h-0">
      {/* Panel header */}
      <div className="flex items-center px-3.5 py-3 border-b border-dispatch-lineSoft shrink-0">
        <div className="flex-1 min-w-0">
          <div className="text-[10px] font-semibold tracking-[1.6px] uppercase text-dispatch-textMuted font-mono">
            {active ? 'LIVE TRANSCRIPT' : 'IDLE'}
          </div>
          <div className="text-sm font-semibold text-dispatch-text tracking-[-0.2px] mt-px">
            {active ? '응급 신고 통화 내용' : '수신된 통화가 없습니다'}
          </div>
        </div>
        {active && (
          <div className="flex items-center gap-2.5">
            <button
              onClick={() => setConversations([])}
              className="font-ui cursor-pointer border-0 bg-transparent text-dispatch-textDim text-[11px] py-1 px-2 rounded-[3px] ring-1 ring-inset ring-dispatch-line"
            >
              새로고침
            </button>
          </div>
        )}
      </div>

      {/* Body */}
      {!active ? (
        <div className="flex-1 min-h-0 flex flex-col items-center justify-center gap-3.5 text-dispatch-textMuted p-8">
          <div className="w-[54px] h-[54px] rounded-[27px] bg-dispatch-card ring-1 ring-inset ring-dispatch-line flex items-center justify-center font-mono text-xl text-dispatch-textMuted">
            ◌
          </div>
          <div className="text-[13px] text-dispatch-textDim">현재 진행 중인 통화가 없습니다.</div>
          <div className="font-mono text-[10px] text-dispatch-textMuted tracking-[1px]">
            STANDBY · 통화 수신 대기 중
          </div>
        </div>
      ) : (
        <div className="dispatch-scroll flex-1 min-h-0 overflow-y-auto pt-3.5 px-3.5 pb-2">
          {!hasContent ? (
            <div className="my-3 p-3.5 bg-dispatch-blue-soft ring-1 ring-inset ring-dispatch-blue-edge rounded-md text-[#93c5fd] text-xs text-center">
              대화 내용을 가져오는 중입니다…
            </div>
          ) : conversations.map(c => (
            <Bubble key={c.id} sender={c.sender} text={c.text} time={c.time} />
          ))}
          {(['patient', 'agent'] as const).map(sender =>
            partials.has(sender) ? (
              <Bubble
                key={`partial-${sender}`}
                sender={sender}
                text={partials.get(sender)!}
                time=""
                partial
              />
            ) : null
          )}
          <div ref={bottomRef} />
        </div>
      )}

      {/* Footer */}
      <div className="px-3.5 py-2.5 border-t border-dispatch-lineSoft flex justify-between font-mono text-[10px] text-dispatch-textMuted tracking-[1px] shrink-0">
        {active ? (
          <>
            <span>CALL · {conversations.length} 발화</span>
            <span>DURATION · {mm}:{ss}</span>
          </>
        ) : (
          <>
            <span>STANDBY</span>
            <span>00:00</span>
          </>
        )}
      </div>
    </div>
  )
}

export default ConversationBox
