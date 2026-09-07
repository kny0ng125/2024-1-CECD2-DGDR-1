import { useEffect, useRef } from 'react'
import { buildSseUrl } from '@/lib/authFetch'
import { useAuthStore } from '@/stores/useAuthStore'
import { useCallStore } from '@/stores/useCallStore'
import type {
  CallOfferedEvent,
  CallAnsweredEvent,
  CallStartedEvent,
  CallEndedEvent,
} from '@/types/transcript'

/**
 * [control 채널] 요원 단위 통화 라이프사이클 구독.
 *
 * <p>자막 채널과 수명이 다르다. 이 구독은 <b>로그인해 있는 내내</b> 유지되고,
 * 자막 구독은 통화 한 건 동안만 산다. 프런트가 callId 를 얻는 유일한 경로가
 * 이 채널이므로, 이것이 없으면 실제 전화가 걸려와도 화면이 반응하지 못한다.
 *
 * <p>앱 루트에서 한 번만 호출할 것. 여러 곳에서 부르면 EventSource 가
 * 중복 생성되어 같은 이벤트를 여러 번 처리하게 된다.
 */
export function useAgentEvents(enabled: boolean) {
  const accessToken = useAuthStore((s) => s.accessToken)
  const esRef = useRef<EventSource | null>(null)

  useEffect(() => {
    if (!enabled || !accessToken) return

    const es = new EventSource(buildSseUrl('/api/v1/agent/events'))
    esRef.current = es

    const store = useCallStore.getState

    es.addEventListener('call_offered', (e) => {
      const d: CallOfferedEvent = JSON.parse((e as MessageEvent).data)
      store().offerCall(d.callId, d.callerPhone, d.offeredAt)
    })

    es.addEventListener('call_answered', (e) => {
      const d: CallAnsweredEvent = JSON.parse((e as MessageEvent).data)
      store().answerCall(d.callId, d.answeredAt)
    })

    // 수락 절차가 없는 통화 소스(Vonage 등)는 곧바로 통화 중으로 시작한다.
    es.addEventListener('call_started', (e) => {
      const d: CallStartedEvent = JSON.parse((e as MessageEvent).data)
      store().startCall(d.callId, d.startedAt, d.callerPhone)
    })

    es.addEventListener('call_ended', (e) => {
      const d: CallEndedEvent = JSON.parse((e as MessageEvent).data)
      store().endCall(d.missed)
    })

    // EventSource 는 끊기면 스스로 재연결한다. 그게 SSE 를 고른 이유 중
    // 하나이므로, 여기서 재연결 로직을 따로 만들지 않는다.
    es.onerror = () => {
      if (es.readyState === EventSource.CLOSED) {
        console.warn('[agent-events] stream closed')
      }
    }

    return () => {
      es.close()
      esRef.current = null
    }
  }, [enabled, accessToken])
}
