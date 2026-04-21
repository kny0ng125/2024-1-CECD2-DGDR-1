import { create } from 'zustand'

interface CallState {
  callId: number | null
  setCallId: (id: number | null) => void
}

export const useCallStore = create<CallState>((set) => ({
  callId: null,
  setCallId: (id) => set({ callId: id }),
}))
