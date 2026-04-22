import { create } from 'zustand';
import { Manual } from '@/types/manual';

interface ManualState {
  manuals: Manual[];
  selectedManual: Manual | null;
  savedManuals: Manual[];
  checklistState: Record<string, Record<number, boolean>>;

  setManuals: (list: Manual[]) => void;
  selectManual: (m: Manual | null) => void;
  saveManual: (m: Manual) => void;
  removeManual: (id: number) => void;
  toggleCheck: (title: string, lineIndex: number) => void;
}

export const useManualStore = create<ManualState>((set, get) => ({
  manuals: [],
  selectedManual: null,
  savedManuals: [],
  checklistState: {},

  setManuals: (list) => set({ manuals: list }),
  selectManual: (m) => set({ selectedManual: m }),
  saveManual: (m) => {
    if (get().savedManuals.some(x => x.title === m.title)) return;
    set((s) => ({ savedManuals: [...s.savedManuals, m] }));
  },
  removeManual: (id) => set((s) => ({
    savedManuals: s.savedManuals.filter(x => x.id !== id),
    selectedManual: s.selectedManual?.id === id ? null : s.selectedManual,
  })),
  toggleCheck: (title, lineIndex) => set((s) => ({
    checklistState: {
      ...s.checklistState,
      [title]: {
        ...(s.checklistState[title] ?? {}),
        [lineIndex]: !s.checklistState[title]?.[lineIndex],
      },
    },
  })),
}));
