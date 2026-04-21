import { useState, useEffect, useRef } from 'react'
import { useCallStore } from '@/stores/useCallStore'
import { useHotkey } from '@/hooks/useHotkey'
import { Loader2 } from 'lucide-react'
import { API_BASE_URL } from '@/lib/config'

interface CallRecord {
  transcription: string
  speakerPhoneNumber: string
  time: string
  call?: {
    id: number
    user?: { phoneNumber: string }
  }
}

interface Conversation {
  id: number
  text: string
  sender: 'agent' | 'patient'
  time: string
}

// 임시 어댑터 (02 PR에서 정식 구현)
const isAgent = (record: CallRecord): boolean =>
  record.speakerPhoneNumber === record.call?.user?.phoneNumber

const ConversationBox = () => {
  const { setCallId } = useCallStore()
  const [conversations, setConversations] = useState<Conversation[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const conversationEndRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    fetchConversations()
    const intervalId = setInterval(fetchConversations, 5000)
    return () => clearInterval(intervalId)
  }, [])

  useEffect(() => {
    conversationEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [conversations])

  const fetchConversations = async () => {
    try {
      const response = await fetch(`${API_BASE_URL}/api/v1/call/latest`, {
        method: 'GET',
        headers: {
          'Content-Type': 'application/json',
          'ngrok-skip-browser-warning': 'true',
        },
        credentials: 'same-origin',
      })
      if (!response.ok) throw new Error(`API error: ${response.statusText}`)
      const data: CallRecord[] = await response.json()
      setConversations(
        data.map((item, index) => ({
          id: index + 1,
          text: item.transcription,
          sender: isAgent(item) ? 'agent' : 'patient',
          time: new Date(item.time).toLocaleTimeString([], {
            hour: '2-digit',
            minute: '2-digit',
          }),
        }))
      )
      if (data.length > 0 && data[0].call) {
        setCallId(data[0].call.id)
      }
      setError(null)
    } catch (err) {
      console.error('Fetch error:', err)
      setError((err as Error).message)
      setConversations([])
    } finally {
      setLoading(false)
    }
  }

  useHotkey('ctrl+alt+r', () => setConversations([]))

  if (loading) {
    return (
      <div className="flex items-center justify-center h-[92vh]">
        <Loader2 className="animate-spin w-8 h-8" />
      </div>
    )
  }

  return (
    <div className="ml-5 bg-[#f7f7f7] p-5 rounded-[10px] h-full max-h-[97vh] shadow-[0_4px_10px_rgba(0,0,0,0.1)] flex flex-col">
      <div className="flex justify-between items-center">
        <h3 className="font-bold mt-5 mb-[30px]">응급 신고 통화 내용</h3>
        <button
          onClick={() => setConversations([])}
          className="bg-[#ff6f61] text-white px-4 py-2 rounded border-none cursor-pointer"
        >
          새로고침
        </button>
      </div>

      {error ? (
        <div className="bg-[#f8d7da] text-[#721c24] border border-[#f5c6cb] p-4 rounded-[10px]">
          서버가 응답하지 않습니다.
        </div>
      ) : (
        <div className="flex-1 p-5 overflow-y-auto flex flex-col gap-[25px] bg-[#e9ecef] rounded-[10px]">
          {conversations.length > 0 ? (
            conversations.map((conv, index) => (
              <div
                key={conv.id}
                className={`flex items-end mb-5 gap-[10px] ${
                  conv.sender === 'agent' ? 'justify-end' : 'justify-start'
                }`}
              >
                {conv.sender === 'agent' ? (
                  <>
                    <span className="text-[0.8em] text-[#888] whitespace-nowrap">{conv.time}</span>
                    <div
                      className={`text-[1.3em] flex justify-between p-[20px_25px] rounded-[10px] max-w-[50%] break-words border border-[#ccc] shadow-[0_2px_5px_rgba(0,0,0,0.1)] ${
                        index === conversations.length - 1
                          ? '!bg-[#d4edda] border-[#c3e6cb] text-[#155724]'
                          : 'bg-[#d0e0ff]'
                      }`}
                    >
                      <span>{conv.text}</span>
                    </div>
                  </>
                ) : (
                  <>
                    <div
                      className={`text-[1.3em] flex justify-between p-[20px_25px] rounded-[10px] max-w-[50%] break-words border border-[#ccc] shadow-[0_2px_5px_rgba(0,0,0,0.1)] ${
                        index === conversations.length - 1
                          ? '!bg-[#d4edda] border-[#c3e6cb] text-[#155724]'
                          : 'bg-[#f0f0f0]'
                      }`}
                    >
                      <span>{conv.text}</span>
                    </div>
                    <span className="text-[0.8em] text-[#888] whitespace-nowrap">{conv.time}</span>
                  </>
                )}
              </div>
            ))
          ) : (
            <div className="bg-[#d1ecf1] text-[#0c5460] border border-[#bee5eb] p-4 rounded-[10px]">
              대화 내용을 가져오는 중입니다....
            </div>
          )}
          <div ref={conversationEndRef} />
        </div>
      )}
    </div>
  )
}

export default ConversationBox
