import { useState, useEffect } from 'react'
import { Loader2 } from 'lucide-react'
import { API_BASE_URL } from '@/lib/config'

interface Call {
  id: number
  startTime: string
}

interface CallRecord {
  id: number
  transcription: string
  speakerPhoneNumber: string
  time: string
  call?: { user?: { phoneNumber: string } }
}

// 임시 어댑터 (02 PR에서 정식 구현)
const isAgentRecord = (entry: CallRecord): boolean =>
  entry.speakerPhoneNumber === entry.call?.user?.phoneNumber

const CallHistory = () => {
  const today = new Date().toISOString().split('T')[0]
  const [selectedDate, setSelectedDate] = useState(today)
  const [callList, setCallList] = useState<Call[]>([])
  const [selectedCall, setSelectedCall] = useState<CallRecord[] | null>(null)
  const [selectedCallId, setSelectedCallId] = useState<number | null>(null)
  const [loading, setLoading] = useState(false)

  const fetchCallList = async (dateStr: string) => {
    setLoading(true)
    try {
      const response = await fetch(
        `${API_BASE_URL}/api/v1/call/date?startDate=${dateStr}&endDate=${dateStr}`,
        {
          headers: {
            'Content-Type': 'application/json',
            'ngrok-skip-browser-warning': 'true',
          },
        }
      )
      const data = await response.json()
      setCallList(data)
    } catch (err) {
      console.error('Error fetching call list:', err)
      setCallList([])
    } finally {
      setLoading(false)
    }
  }

  const fetchCallDetails = async (callId: number) => {
    setLoading(true)
    try {
      const response = await fetch(`${API_BASE_URL}/api/v1/${callId}/call-record`, {
        headers: {
          'Content-Type': 'application/json',
          'ngrok-skip-browser-warning': 'true',
        },
      })
      const data = await response.json()
      setSelectedCall(data)
    } catch (err) {
      console.error('Error fetching call details:', err)
      setSelectedCall(null)
    } finally {
      setLoading(false)
    }
  }

  const handleDateChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setSelectedDate(e.target.value)
    fetchCallList(e.target.value)
  }

  const handleCallClick = (callId: number) => {
    setSelectedCallId(callId)
    fetchCallDetails(callId)
  }

  useEffect(() => {
    fetchCallList(selectedDate)
  }, [])

  return (
    <div className="flex justify-center items-center gap-[50px] h-screen p-5 bg-[#f0f0f0]">
      {/* 통화 기록 목록 */}
      <div className="w-[1000px] bg-white p-5 rounded-[10px] shadow-[0_4px_10px_rgba(0,0,0,0.1)] h-[80vh] overflow-y-auto">
        <h3 className="mb-10 pt-[10px] pb-[10px] border-t border-b border-[#ddd]">통화 기록</h3>
        <div className="mb-5">
          {/* TODO: 다음 PR에서 shadcn DatePicker로 교체 */}
          <input
            type="date"
            value={selectedDate}
            onChange={handleDateChange}
            className="w-full text-[1.2em] p-[10px] border border-[#ccc] rounded"
          />
        </div>
        <div>
          {loading ? (
            <div className="flex justify-center py-4">
              <Loader2 className="animate-spin w-6 h-6" />
            </div>
          ) : (
            callList.map((call) => (
              <div
                key={call.id}
                onClick={() => handleCallClick(call.id)}
                className={`cursor-pointer p-[10px] mt-[15px] mb-[15px] border rounded-[5px] transition-colors ${
                  selectedCallId === call.id
                    ? 'bg-[#d0e0ff] border-[#007bff]'
                    : 'bg-[#f9f9f9] border-[#ddd] hover:bg-[#e9ecef]'
                }`}
              >
                {new Date(call.startTime).toLocaleTimeString([], {
                  hour: '2-digit',
                  minute: '2-digit',
                })}
              </div>
            ))
          )}
        </div>
      </div>

      {/* 통화 상세 내역 */}
      <div className="w-[1000px] bg-white p-5 rounded-[10px] shadow-[0_4px_10px_rgba(0,0,0,0.1)] h-[80vh] overflow-y-auto">
        {selectedCall ? (
          <div className="p-5 h-[1130px] bg-[#e9ecef] rounded-[10px]">
            {selectedCall.map((entry) => (
              <div
                key={entry.id}
                className={`flex mb-[15px] ${
                  isAgentRecord(entry) ? 'justify-end' : 'justify-start'
                }`}
              >
                {isAgentRecord(entry) ? (
                  <>
                    <span className="text-[0.8em] text-[#888] mr-[10px]">
                      {new Date(entry.time).toLocaleTimeString([], {
                        hour: '2-digit',
                        minute: '2-digit',
                      })}
                    </span>
                    <div className="bg-[#d0e0ff] p-[10px] rounded-[10px] max-w-[70%] break-words border border-[#ccc] shadow-[0_2px_5px_rgba(0,0,0,0.1)]">
                      <span>{entry.transcription}</span>
                    </div>
                  </>
                ) : (
                  <>
                    <div className="bg-[#f0f0f0] p-[10px] rounded-[10px] max-w-[70%] break-words border border-[#ccc] shadow-[0_2px_5px_rgba(0,0,0,0.1)]">
                      <span>{entry.transcription}</span>
                    </div>
                    <span className="text-[0.8em] text-[#888] ml-[10px]">
                      {new Date(entry.time).toLocaleTimeString([], {
                        hour: '2-digit',
                        minute: '2-digit',
                      })}
                    </span>
                  </>
                )}
              </div>
            ))}
          </div>
        ) : (
          <div className="text-center text-[#888]">통화 내역을 선택하세요.</div>
        )}
      </div>
    </div>
  )
}

export default CallHistory
