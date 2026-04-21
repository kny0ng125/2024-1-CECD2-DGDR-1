import { create } from 'zustand'

interface ManualContent {
  clinicalFeatures: string
  patientAssessment: string
}

interface Manual {
  title: string
  content: ManualContent
}

interface ManualState {
  savedManuals: Manual[]
  selectedManual: Manual | null
  saveManual: (manual: Manual) => void
  selectManual: (manual: Manual) => void
}

export const useManualStore = create<ManualState>((set, get) => ({
  savedManuals: [],
  selectedManual: null,
  saveManual: (manual) => {
    if (get().savedManuals.some((m) => m.title === manual.title)) return
    set({ savedManuals: [...get().savedManuals, manual] })
  },
  selectManual: (manual) => set({ selectedManual: manual }),
}))
