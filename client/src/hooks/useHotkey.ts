import { useEffect } from 'react'

type Modifier = 'ctrl' | 'alt' | 'shift' | 'meta'

export function useHotkey(
  combo: string,
  callback: () => void,
  deps: unknown[] = []
) {
  useEffect(() => {
    const parts = combo.toLowerCase().split('+')
    const key = parts.pop()!
    const mods = new Set<Modifier>(parts as Modifier[])

    const handler = (e: KeyboardEvent) => {
      if (e.key.toLowerCase() !== key) return
      if (mods.has('ctrl') !== e.ctrlKey) return
      if (mods.has('alt') !== e.altKey) return
      if (mods.has('shift') !== e.shiftKey) return
      if (mods.has('meta') !== e.metaKey) return
      e.preventDefault()
      callback()
    }

    window.addEventListener('keydown', handler)
    return () => window.removeEventListener('keydown', handler)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [combo, ...deps])
}
