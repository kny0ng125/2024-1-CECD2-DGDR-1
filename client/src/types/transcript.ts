export type Speaker = 'caller' | 'agent'

/**
 * [data 채널] `GET /api/v1/call/{callId}/transcript/stream` 의
 * `transcript` 이벤트 페이로드. 서버의 `TranscriptEntry` 와 1:1 대응한다.
 *
 * 주의: `speakerPhone` 은 서버가 DB 저장용으로 들고 있는 값이며
 * 화면에서는 쓰지 않는다. 최소수집 원칙상 SSE 전용 DTO 로 분리해
 * 내보내지 않는 것이 맞고, 여기서는 실제 응답 형태를 정확히 기술하기 위해
 * optional 로만 선언한다.
 */
export interface TranscriptEvent {
  speaker: Speaker
  speakerPhone?: string | null
  text: string
  isFinal: boolean
  timestamp: string
}

/**
 * [control 채널] `GET /api/v1/agent/events` 의 이벤트들.
 * SSE 이벤트 이름이 곧 종류이므로 페이로드에는 타입 필드가 없다.
 */
export interface CallOfferedEvent {
  callId: number
  callerPhone: string | null
  offeredAt: string
}

export interface CallAnsweredEvent {
  callId: number
  answeredAt: string
}

export interface CallStartedEvent {
  callId: number
  callerPhone: string | null
  startedAt: string
}

export interface CallEndedEvent {
  callId: number
  /** 수락되지 않은 채 끝난 통화 */
  missed: boolean
}

/** 통화 생명주기. 서버 `CallState` 와 동일. */
export type CallState = 'OFFERED' | 'ANSWERED' | 'ENDED'

export interface Conversation {
  id: string
  text: string
  speaker: Speaker
  timestamp: string
}
