import { useState, useEffect } from 'react'
import { MessagesSquare } from 'lucide-react'
import { authFetch } from '@/lib/authFetch'

interface Call { id: number; startTime: string }

interface CallRecord {
  id: number
  transcription: string
  speaker: 'agent' | 'caller'
  speakerPhoneNumber: string
  time: string
}

function PanelHeader({ title, trailing }: {
  title: string; trailing?: React.ReactNode
}) {
  return (
    <div className="flex items-center px-3.5 py-3 border-b border-dispatch-lineSoft shrink-0">
      <div className="flex-1 min-w-0">
        <div className="text-sm font-semibold text-dispatch-text tracking-[-0.2px]">{title}</div>
      </div>
      {trailing}
    </div>
  )
}

function Bubble({ isAgent, text, time }: { isAgent: boolean; text: string; time: string }) {
  return (
    <div className={`flex flex-col mb-3.5 ${isAgent ? 'items-end' : 'items-start'}`}>
      <div
        className={`text-[11px] font-medium mb-1 px-0.5 ${
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
      </div>
      <div className="text-[10px] text-dispatch-textMuted font-mono mt-1 px-0.5">{time}</div>
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

  const selected = callList.find(c => c.id === selectedCallId)

  return (
    <div
      className="bg-dispatch-bg grid gap-2.5 p-2.5 font-ui text-dispatch-text box-border"
      style={{ height: 'calc(100vh - 52px)', gridTemplateColumns: '320px 1fr' }}
    >
      {/* LEFT — Call list */}
      <div className="bg-dispatch-elev rounded-md ring-1 ring-inset ring-dispatch-line flex flex-col min-h-0">
        <PanelHeader
          title="통화 기록"
          trailing={
            <span className="text-[11px] text-dispatch-textMuted">
              {callList.length}건
            </span>
          }
        />

        <div className="p-3 border-b border-dispatch-lineSoft shrink-0">
          <label className="block text-xs font-medium text-dispatch-textDim mb-[5px]">
            조회 날짜
          </label>
          <input
            type="date"
            value={selectedDate}
            onChange={handleDateChange}
            className="w-full font-mono text-[13px] py-2 px-2.5 bg-dispatch-card text-dispatch-text border-0 rounded ring-1 ring-inset ring-dispatch-line outline-none box-border"
            style={{ colorScheme: 'dark' }}
          />
        </div>

        <div className="dispatch-scroll flex-1 min-h-0 overflow-y-auto p-2">
          {loading ? (
            <div className="flex justify-center py-10">
              <div
                className="w-5 h-5 rounded-full border-2 border-dispatch-line"
                style={{ borderTopColor: '#3b82f6', animation: 'dispatchSpin 0.9s linear infinite' }}
              />
            </div>
          ) : callList.length === 0 ? (
            <div className="py-6 px-2 text-dispatch-textMuted text-xs text-center">
              해당 날짜의 통화 기록이 없습니다.
            </div>
          ) : callList.map(c => {
            const active   = c.id === selectedCallId
            const callTime = new Date(c.startTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
            return (
              <button
                key={c.id}
                onClick={() => handleCallClick(c.id)}
                className={`w-full block text-left border-0 cursor-pointer font-ui py-2.5 pr-2.5 pl-3.5 rounded mb-0.5 relative text-dispatch-text ${
                  active ? 'bg-dispatch-blue-soft' : 'bg-transparent'
                }`}
              >
                {active && (
                  <span className="absolute left-0 top-1.5 bottom-1.5 w-[3px] bg-dispatch-blue rounded-sm" />
                )}
                <div className="flex justify-between items-baseline mb-[3px]">
                  <span className={`font-mono text-[15px] font-semibold tracking-[0.3px] ${active ? 'text-[#dbeafe]' : 'text-dispatch-text'}`}>
                    {callTime}
                  </span>
                  <span className="font-mono text-[10px] text-dispatch-textMuted">
                    #{String(c.id).padStart(4, '0')}
                  </span>
                </div>
                <div className="text-[11px] text-dispatch-textMuted">
                  {selectedDate.replace(/-/g, '.')}
                </div>
              </button>
            )
          })}
        </div>
      </div>

      {/* RIGHT — Transcript */}
      <div className="bg-dispatch-elev rounded-md ring-1 ring-inset ring-dispatch-line flex flex-col min-h-0">
        {!selected || !records ? (
          <div className="flex-1 flex flex-col items-center justify-center text-dispatch-textMuted gap-3">
            <MessagesSquare size={28} className="text-dispatch-textMuted" strokeWidth={1.5} />
            <div className="text-[13px]">왼쪽 목록에서 통화를 선택해 주세요.</div>
          </div>
        ) : (
          <>
            <PanelHeader
              title={`#${String(selected.id).padStart(4, '0')} 통화`}
              trailing={
                <div className="text-[11px] text-dispatch-textDim text-right">
                  {selectedDate.replace(/-/g, '.')}{' '}
                  {new Date(selected.startTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                </div>
              }
            />

            <div className="dispatch-scroll flex-1 min-h-0 overflow-y-auto py-[18px] px-6">
              {records.length === 0 ? (
                <div className="text-center py-10 text-dispatch-textMuted text-xs">
                  전사 데이터가 없습니다.
                </div>
              ) : records.map(entry => (
                <Bubble key={entry.id}
                  isAgent={entry.speaker === 'agent'}
                  text={entry.transcription}
                  time={new Date(entry.time).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                />
              ))}
            </div>

            <div className="py-3 px-6 border-t border-dispatch-lineSoft text-[11px] text-dispatch-textDim shrink-0">
              총 {records.length}개 발화
            </div>
          </>
        )}
      </div>
    </div>
  )
}

export default CallHistory
