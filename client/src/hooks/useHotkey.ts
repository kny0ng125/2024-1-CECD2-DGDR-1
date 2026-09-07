import { useEffect, useRef } from 'react'

type Modifier = 'ctrl' | 'alt' | 'shift' | 'meta'

interface HotkeyOptions {
  /** false 면 리스너를 붙이지 않는다. 상황에 따라 켜고 끄는 키에 쓴다. */
  enabled?: boolean
  /**
   * 입력 요소에 포커스가 있을 때도 동작시킬지. 기본 false.
   *
   * <p>기본값이 false 인 이유: Enter·Escape 같은 단일 키를 전역에서 가로채면
   * 로그인 폼이나 검색창에서 타이핑이 망가진다. 수식어 없는 키를 쓰는 순간
   * 반드시 필요해지는 방어다.
   */
  allowInInput?: boolean
}

const EDITABLE = new Set(['INPUT', 'TEXTAREA', 'SELECT'])

function isEditable(target: EventTarget | null): boolean {
  const el = target as HTMLElement | null
  if (!el) return false
  return EDITABLE.has(el.tagName) || el.isContentEditable
}

/**
 * 전역 단축키.
 *
 * <p>상황실 화면에서 마우스를 집어 커서를 옮기고 조준해 클릭하는 동작은
 * 1~2초를 먹는다. 골든타임에서 그 시간은 그냥 손실이므로, 자주 쓰는 조작은
 * 전부 키보드에서 손을 떼지 않고 되어야 한다.
 *
 * <p>콜백을 ref 로 유지하는 이유: 예전 구현은 콜백을 effect 안에 가둬 두고
 * deps 에서 빼놨기 때문에, 클로저가 처음 렌더의 상태를 붙든 채 굳었다.
 * 통화 상태에 따라 다르게 동작해야 하는 키(수락/거절)에서는 그대로 버그가 된다.
 */
export function useHotkey(
  combo: string,
  callback: () => void,
  options: HotkeyOptions = {}
) {
  const { enabled = true, allowInInput = false } = options

  // 매 렌더의 최신 콜백을 가리키게 한다. 리스너는 다시 붙이지 않는다.
  const callbackRef = useRef(callback)
  callbackRef.current = callback

  useEffect(() => {
    if (!enabled) return

    const parts = combo.toLowerCase().split('+')
    const key = parts.pop()!
    const mods = new Set<Modifier>(parts as Modifier[])

    const handler = (e: KeyboardEvent) => {
      if (e.key.toLowerCase() !== key) return
      if (mods.has('ctrl') !== e.ctrlKey) return
      if (mods.has('alt') !== e.altKey) return
      if (mods.has('shift') !== e.shiftKey) return
      if (mods.has('meta') !== e.metaKey) return
      if (!allowInInput && isEditable(e.target)) return
      // IME 조합 중에는 무시한다. 한글 입력 중 Enter 는 조합 확정이지
      // 단축키가 아니다.
      if (e.isComposing) return

      e.preventDefault()
      callbackRef.current()
    }

    window.addEventListener('keydown', handler)
    return () => window.removeEventListener('keydown', handler)
  }, [combo, enabled, allowInInput])
}
