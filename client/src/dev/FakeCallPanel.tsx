import { useState, useEffect } from 'react'
import { authFetch } from '@/lib/authFetch'
import { useCallStore } from '@/stores/useCallStore'
import { useAuthStore } from '@/stores/useAuthStore'

interface FakeCallStatus {
  callId: number
  scenarioId: string
  startedAt: string
}

const SCENARIOS = [{ id: 'emergency-119', label: '119 응급 신고' }]

const FakeCallPanel = () => {
  const { callId, startCall, resetCall } = useCallStore()
  const accessToken = useAuthStore((s) => s.accessToken)
  const [scenarioId, setScenarioId] = useState(SCENARIOS[0].id)
  const [speed, setSpeed] = useState(1.0)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!accessToken) return
    authFetch('/api/v1/dev/fake-call/active')
      .then((r) => (r.ok ? r.json() : []))
      .then((list: FakeCallStatus[]) => {
        if (list.length > 0) startCall(list[0].callId, list[0].startedAt)
      })
      .catch(() => {})
  }, [accessToken])

  const handleStart = async () => {
    setBusy(true)
    setError(null)
    try {
      const res = await authFetch('/api/v1/dev/fake-call/start', {
        method: 'POST',
        body: JSON.stringify({ scenarioId, speedMultiplier: speed }),
      })
      if (!res.ok) throw new Error(`${res.status} ${await res.text()}`)
      const status: FakeCallStatus = await res.json()
      startCall(status.callId, status.startedAt)
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
      resetCall()
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setBusy(false)
    }
  }

  if (!accessToken) return null

  return (
    <div className="fixed bottom-4 right-4 z-50 bg-white border border-gray-300 rounded-lg shadow-lg p-4 w-72 text-sm">
      <div className="flex items-center justify-between mb-2">
        <span className="font-bold text-gray-700">DEV: Fake Call</span>
        <span className={`w-2 h-2 rounded-full ${callId ? 'bg-green-500' : 'bg-gray-300'}`} />
      </div>

      <div className="flex flex-col gap-2">
        <label className="flex flex-col gap-1">
          <span className="text-xs text-gray-500">Scenario</span>
          <select
            className="border rounded px-2 py-1"
            value={scenarioId}
            onChange={(e) => setScenarioId(e.target.value)}
            disabled={!!callId || busy}
          >
            {SCENARIOS.map((s) => (
              <option key={s.id} value={s.id}>
                {s.label}
              </option>
            ))}
          </select>
        </label>

        <label className="flex flex-col gap-1">
          <span className="text-xs text-gray-500">Speed ×{speed.toFixed(1)}</span>
          <input
            type="range"
            min={0.5}
            max={5}
            step={0.5}
            value={speed}
            onChange={(e) => setSpeed(parseFloat(e.target.value))}
            disabled={!!callId || busy}
          />
        </label>

        {callId ? (
          <>
            <div className="text-xs text-gray-600">
              callId: <code>{callId}</code>
            </div>
            <button
              onClick={handleStop}
              disabled={busy}
              className="bg-red-500 hover:bg-red-600 text-white rounded py-1 disabled:opacity-50"
            >
              Stop
            </button>
          </>
        ) : (
          <button
            onClick={handleStart}
            disabled={busy}
            className="bg-blue-600 hover:bg-blue-700 text-white rounded py-1 disabled:opacity-50"
          >
            {busy ? 'Starting…' : 'Start Fake Call'}
          </button>
        )}

        {error && <div className="text-xs text-red-600 break-all">{error}</div>}
      </div>
    </div>
  )
}

export default FakeCallPanel
