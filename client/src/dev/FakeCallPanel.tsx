import { useState, useEffect } from 'react'
import { authFetch } from '@/lib/authFetch'
import { useCallStore } from '@/stores/useCallStore'
import { useAuthStore } from '@/stores/useAuthStore'

interface PlaybackStatus {
  callId: number
  scenarioId: string
  state: 'OFFERED' | 'ANSWERED'
  startedAt: string
}

interface ScenarioSummary {
  id: string
  lineCount: number
  durationMs: number
}

/**
 * 개발용 시나리오 재생 패널.
 *
 * <p>화면 상태를 직접 조작하지 않는다. 서버에 재생을 요청하면 통화 상태는
 * <b>컨트롤 채널(SSE)로 돌아온다</b> — 실제 전화가 걸려올 때와 완전히 같은
 * 경로다. 예전 구현은 여기서 {@code startCall()} 을 직접 불러 화면을 켰기
 * 때문에, 컨트롤 채널이 아예 배선되지 않았다는 사실이 데모에서 드러나지
 * 않았다.
 */
const FakeCallPanel = () => {
  const { callId, lifecycle } = useCallStore()
  const accessToken = useAuthStore((s) => s.accessToken)

  const [scenarios, setScenarios] = useState<ScenarioSummary[]>([])
  const [scenarioId, setScenarioId] = useState('emergency-119')
  const [speed, setSpeed] = useState(1.0)
  const [ring, setRing] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!accessToken) return
    authFetch('/api/v1/dev/fake-call/scenarios')
      .then((r) => (r.ok ? r.json() : []))
      .then((list: ScenarioSummary[]) => {
        setScenarios(list)
        if (list.length > 0) setScenarioId(list[0].id)
      })
      .catch(() => {})
  }, [accessToken])

  const handleStart = async () => {
    setBusy(true)
    setError(null)
    try {
      const res = await authFetch('/api/v1/dev/fake-call/start', {
        method: 'POST',
        body: JSON.stringify({ scenarioId, speedMultiplier: speed, ring }),
      })
      if (!res.ok) throw new Error(`${res.status} ${await res.text()}`)
      // 응답의 callId 로 화면을 켜지 않는다. 컨트롤 채널이 call_offered /
      // call_started 를 보내 줄 것이고, 그 경로가 실제 통화와 같아야 한다.
      await res.json() as PlaybackStatus
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setBusy(false)
    }
  }

  const handleStop = async () => {
    if (!callId) return
    setBusy(true)
    try {
      await authFetch(`/api/v1/dev/fake-call/stop/${callId}`, { method: 'POST' })
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setBusy(false)
    }
  }

  if (!accessToken) return null

  const active = lifecycle === 'OFFERED' || lifecycle === 'ANSWERED'

  return (
    <div className="fixed bottom-4 right-4 z-50 bg-white border border-gray-300 rounded-lg shadow-lg p-4 w-72 text-sm">
      <div className="flex items-center justify-between mb-2">
        <span className="font-bold text-gray-700">DEV: 시나리오 재생</span>
        <span
          className={`w-2 h-2 rounded-full ${
            lifecycle === 'ANSWERED' ? 'bg-green-500'
            : lifecycle === 'OFFERED' ? 'bg-amber-500'
            : 'bg-gray-300'
          }`}
        />
      </div>

      <div className="flex flex-col gap-2">
        <label className="flex flex-col gap-1">
          <span className="text-xs text-gray-500">시나리오</span>
          <select
            className="border rounded px-2 py-1"
            value={scenarioId}
            onChange={(e) => setScenarioId(e.target.value)}
            disabled={active || busy}
          >
            {scenarios.map((s) => (
              <option key={s.id} value={s.id}>
                {s.id} ({s.lineCount}발화 · {Math.round(s.durationMs / 1000)}초)
              </option>
            ))}
          </select>
        </label>

        <label className="flex flex-col gap-1">
          <span className="text-xs text-gray-500">배속 ×{speed.toFixed(1)}</span>
          <input
            type="range" min={0.5} max={5} step={0.5}
            value={speed}
            onChange={(e) => setSpeed(parseFloat(e.target.value))}
            disabled={active || busy}
          />
        </label>

        <label className="flex items-center gap-2 text-xs text-gray-600">
          <input
            type="checkbox"
            checked={ring}
            onChange={(e) => setRing(e.target.checked)}
            disabled={active || busy}
          />
          벨 울림부터 시작 (수락/거절 테스트)
        </label>

        {active ? (
          <>
            <div className="text-xs text-gray-600">
              callId <code>{callId}</code> · {lifecycle}
            </div>
            <button
              onClick={handleStop}
              disabled={busy}
              className="bg-red-500 hover:bg-red-600 text-white rounded py-1 disabled:opacity-50"
            >
              중단
            </button>
          </>
        ) : (
          <button
            onClick={handleStart}
            disabled={busy}
            className="bg-blue-600 hover:bg-blue-700 text-white rounded py-1 disabled:opacity-50"
          >
            {busy ? '시작 중…' : '시나리오 시작'}
          </button>
        )}

        {error && <div className="text-xs text-red-600 break-all">{error}</div>}
      </div>
    </div>
  )
}

export default FakeCallPanel
