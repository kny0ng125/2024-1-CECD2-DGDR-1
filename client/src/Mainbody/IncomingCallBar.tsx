import { useState } from 'react'
import { Phone, PhoneOff } from 'lucide-react'
import { authFetch } from '@/lib/authFetch'
import { useCallStore, useIsRinging } from '@/stores/useCallStore'
import { useHotkey } from '@/hooks/useHotkey'

/**
 * 착신 알림 + 수락/거절.
 *
 * <h2>왜 모달이 아닌 상단 고정 바인가</h2>
 * <p>모달은 화면을 덮는다. 벨이 울리는 동안에도 요원은 직전 신고의 매뉴얼을
 * 읽거나 병원 현황을 확인하고 있을 수 있고, 그것을 가리면 안 된다.
 * 착신은 화면을 빼앗을 일이 아니라 눈에 띄기만 하면 되는 일이다.
 *
 * <h2>왜 핫키가 먼저인가</h2>
 * <p>골든타임에서 마우스를 집어 커서를 옮기고 조준해 클릭하는 동작은
 * 1~2초를 먹는다. 수보 업무는 키보드에서 손을 떼지 않는 것이 기본이므로
 * 수락은 <kbd>Enter</kbd>, 거절은 <kbd>Esc</kbd> 로 처리한다.
 * 버튼은 핫키를 모르는 사람을 위한 보조 수단이다.
 */
const IncomingCallBar = () => {
  const ringing = useIsRinging()
  const { callId, callerPhone } = useCallStore()
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const act = async (action: 'answer' | 'reject') => {
    if (!callId || busy) return
    setBusy(true)
    setError(null)
    try {
      const res = await authFetch(`/api/v1/call/${callId}/${action}`, { method: 'POST' })

      // 409 = 이미 끝난 통화. 발신자가 먼저 끊는 것과 흔하게 경합하므로
      // 오류로 다루지 않는다. 컨트롤 채널이 곧 call_ended 를 보내 준다.
      if (res.status === 409) return
      if (!res.ok) throw new Error(`${res.status}`)
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setBusy(false)
    }
  }

  // 벨이 울릴 때만 리스너를 붙인다. 통화 중에도 Enter 를 잡고 있으면
  // 다른 조작을 하다 의도치 않게 발동한다.
  useHotkey('Enter', () => act('answer'), { enabled: ringing })
  useHotkey('Escape', () => act('reject'), { enabled: ringing })

  if (!ringing) return null

  return (
    <div
      role="alert"
      aria-live="assertive"
      className="flex items-center gap-3 px-4 py-2.5 bg-dispatch-blue-soft
                 ring-1 ring-inset ring-dispatch-blue-edge"
    >
      <span
        className="w-2.5 h-2.5 rounded-full bg-red-500 shrink-0"
        style={{ animation: 'dispatchBlink 1s steps(2) infinite' }}
        aria-hidden="true"
      />

      <div className="flex flex-col min-w-0">
        <span className="text-[13px] font-medium text-[#dbeafe]">신고 수신 중</span>
        <span className="text-[11px] font-mono text-dispatch-textMuted truncate">
          {callerPhone ?? '발신번호 표시제한'}
        </span>
      </div>

      <div className="flex items-center gap-2 ml-auto">
        <button
          onClick={() => act('answer')}
          disabled={busy}
          className="flex items-center gap-1.5 px-3 py-1.5 rounded bg-emerald-600
                     hover:bg-emerald-500 text-white text-[13px] disabled:opacity-50"
        >
          <Phone size={14} aria-hidden="true" />
          수락
          <kbd className="ml-1 text-[10px] opacity-70">Enter</kbd>
        </button>

        <button
          onClick={() => act('reject')}
          disabled={busy}
          className="flex items-center gap-1.5 px-3 py-1.5 rounded bg-red-600
                     hover:bg-red-500 text-white text-[13px] disabled:opacity-50"
        >
          <PhoneOff size={14} aria-hidden="true" />
          거절
          <kbd className="ml-1 text-[10px] opacity-70">Esc</kbd>
        </button>
      </div>

      {error && <span className="text-[11px] text-red-300 ml-2">{error}</span>}
    </div>
  )
}

export default IncomingCallBar
