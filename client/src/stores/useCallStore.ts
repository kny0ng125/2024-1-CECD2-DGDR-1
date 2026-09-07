import { create } from 'zustand';
import { Conversation, Speaker, CallState as CallLifecycle } from '@/types/transcript';

interface ProtocolStep {
  id: number;
  text: string;
  completed: boolean;
}

const DEFAULT_PROTOCOLS: ProtocolStep[] = [
  { id: 1, text: '환자 상태 확인', completed: false },
  { id: 2, text: '현장 조건 파악', completed: false },
  { id: 3, text: '초동조치 지도', completed: false },
  { id: 4, text: '출동 확인 완료', completed: false },
];

interface CallStore {
  /** 서버가 발급한 통화 식별자. 컨트롤 채널로 받는다. */
  callId: number | null;
  /** OFFERED = 벨 울림(수락 대기), ANSWERED = 통화 중, null = 통화 없음 */
  lifecycle: CallLifecycle | null;
  callerPhone: string | null;
  /** 벨이 울리기 시작한 시각(OFFERED) 또는 수락 시각(ANSWERED) */
  callStartedAt: string | null;
  /** 수락되지 않고 끝난 직전 통화 — 놓친 신고 배지용 */
  lastCallMissed: boolean;

  conversations: Conversation[];
  protocols: ProtocolStep[];

  /** 벨 울림 시작 */
  offerCall: (callId: number, callerPhone: string | null, at: string) => void;
  /** 수락됨 — 이 시점부터 자막이 흐른다 */
  answerCall: (callId: number, at: string) => void;
  /** 수락 절차 없이 곧바로 통화 중으로 시작(제어 불가 소스) */
  startCall: (callId: number, at: string, callerPhone?: string | null) => void;
  endCall: (missed?: boolean) => void;
  resetCall: () => void;

  addFinalConversation: (msg: { text: string; speaker: Speaker; timestamp: string }) => void;
  clearConversations: () => void;

  toggleProtocol: (id: number) => void;
  resetProtocols: () => void;
}

const freshProtocols = () => DEFAULT_PROTOCOLS.map(p => ({ ...p, completed: false }));

export const useCallStore = create<CallStore>((set) => ({
  callId: null,
  lifecycle: null,
  callerPhone: null,
  callStartedAt: null,
  lastCallMissed: false,

  conversations: [],
  protocols: freshProtocols(),

  offerCall: (callId, callerPhone, at) => set({
    callId,
    lifecycle: 'OFFERED',
    callerPhone,
    callStartedAt: at,
    lastCallMissed: false,
    // 벨 시점에 미리 비워 둔다. 수락 후 이전 통화 자막이 남아 있으면
    // 요원이 지금 신고와 직전 신고를 섞어 읽게 된다.
    conversations: [],
    protocols: freshProtocols(),
  }),

  answerCall: (callId, at) => set({
    callId,
    lifecycle: 'ANSWERED',
    callStartedAt: at,
  }),

  startCall: (callId, at, callerPhone = null) => set({
    callId,
    lifecycle: 'ANSWERED',
    callerPhone,
    callStartedAt: at,
    lastCallMissed: false,
    conversations: [],
    protocols: freshProtocols(),
  }),

  endCall: (missed = false) => set({
    lifecycle: 'ENDED',
    lastCallMissed: missed,
  }),

  resetCall: () => set({
    callId: null,
    lifecycle: null,
    callerPhone: null,
    callStartedAt: null,
    conversations: [],
  }),

  addFinalConversation: (msg) => set((s) => ({
    conversations: [...s.conversations, {
      id: `${msg.timestamp}-${s.conversations.length}`,
      text: msg.text,
      speaker: msg.speaker,
      timestamp: msg.timestamp,
    }],
  })),

  clearConversations: () => set({ conversations: [] }),

  toggleProtocol: (id) => set((s) => ({
    protocols: s.protocols.map(p => p.id === id ? { ...p, completed: !p.completed } : p),
  })),

  resetProtocols: () => set({ protocols: freshProtocols() }),
}));

/** 파생 상태 — 통화가 실제로 진행 중인가. 여러 컴포넌트가 같은 판단을 쓴다. */
export const useIsCallActive = () =>
  useCallStore((s) => s.lifecycle === 'ANSWERED');

export const useIsRinging = () =>
  useCallStore((s) => s.lifecycle === 'OFFERED');
