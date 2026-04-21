import { create } from 'zustand';
import { Conversation, Speaker, SavePromptData } from '@/types/transcript';

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

interface CallState {
  callId: number | null;
  callStartedAt: string | null;
  isCallActive: boolean;
  wsConnected: boolean;

  conversations: Conversation[];
  partialText: string;
  partialSpeaker: Speaker | null;

  protocols: ProtocolStep[];

  savePrompt: SavePromptData | null;

  startCall: (callId: number, timestamp: string) => void;
  endCall: () => void;
  resetCall: () => void;
  setWsConnected: (ok: boolean) => void;

  addFinalConversation: (msg: { text: string; speaker: Speaker; timestamp: string }) => void;
  setPartial: (text: string, speaker: Speaker) => void;
  clearPartial: () => void;
  clearConversations: () => void;

  toggleProtocol: (id: number) => void;
  resetProtocols: () => void;

  openSavePrompt: (data: SavePromptData) => void;
  closeSavePrompt: () => void;
}

export const useCallStore = create<CallState>((set) => ({
  callId: null,
  callStartedAt: null,
  isCallActive: false,
  wsConnected: false,

  conversations: [],
  partialText: '',
  partialSpeaker: null,

  protocols: DEFAULT_PROTOCOLS,

  savePrompt: null,

  startCall: (callId, timestamp) => set({
    callId,
    callStartedAt: timestamp,
    isCallActive: true,
    conversations: [],
    partialText: '',
    partialSpeaker: null,
    protocols: DEFAULT_PROTOCOLS.map(p => ({ ...p, completed: false })),
  }),
  endCall: () => set({ isCallActive: false, partialText: '', partialSpeaker: null }),
  resetCall: () => set({
    callId: null, callStartedAt: null, isCallActive: false,
    conversations: [], partialText: '', partialSpeaker: null,
  }),
  setWsConnected: (ok) => set({ wsConnected: ok }),

  addFinalConversation: (msg) => set((s) => ({
    conversations: [...s.conversations, {
      id: `${msg.timestamp}-${s.conversations.length}`,
      text: msg.text,
      speaker: msg.speaker,
      timestamp: msg.timestamp,
    }],
  })),
  setPartial: (text, speaker) => set({ partialText: text, partialSpeaker: speaker }),
  clearPartial: () => set({ partialText: '', partialSpeaker: null }),
  clearConversations: () => set({ conversations: [] }),

  toggleProtocol: (id) => set((s) => ({
    protocols: s.protocols.map(p => p.id === id ? { ...p, completed: !p.completed } : p),
  })),
  resetProtocols: () => set({
    protocols: DEFAULT_PROTOCOLS.map(p => ({ ...p, completed: false })),
  }),

  openSavePrompt: (data) => set({ savePrompt: data }),
  closeSavePrompt: () => set({ savePrompt: null }),
}));
