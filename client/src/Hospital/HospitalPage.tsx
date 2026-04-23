import { useState, useMemo } from 'react'
import { authFetch } from '@/lib/authFetch'

interface Hospital {
  pid: string; name: string; location: string; call: string; time: string
}
interface HospitalDetails {
  emergencyRoomDefault: number; emergencyRoomAvailable: number
  emergencyRoomChildDefault: number; emergencyRoomChildAvailable: number
  operatingRoom: number; neurosurgeryRoom: number; neonatalRoom: number
  thoracicRoom: number; generalRoom: number
  ct: boolean; mri: boolean; angiography: boolean
  ventilator: boolean; crrt: boolean; ecmo: boolean
}

const DUMMY_HOSPITALS: Hospital[] = [
  { pid: 'H001', name: '서울대학교병원',       location: '서울특별시 종로구 대학로 101',         call: '02-2072-2114', time: '15분' },
  { pid: 'H002', name: '세브란스병원',         location: '서울특별시 서대문구 연세로 50-1',        call: '02-2228-1004', time: '22분' },
  { pid: 'H003', name: '삼성서울병원',         location: '서울특별시 강남구 일원로 81',            call: '02-3410-2114', time: '31분' },
  { pid: 'H004', name: '서울아산병원',         location: '서울특별시 송파구 올림픽로43길 43',       call: '02-3010-3114', time: '38분' },
  { pid: 'H005', name: '고려대학교 안암병원',   location: '서울특별시 성북구 고려대로 73',          call: '02-920-5114',  time: '20분' },
  { pid: 'H006', name: '한양대학교병원',       location: '서울특별시 성동구 왕십리로 222-1',        call: '02-2290-8114', time: '17분' },
]
const DUMMY_DETAILS: Record<string, HospitalDetails> = {
  H001: { emergencyRoomDefault:30, emergencyRoomAvailable:8,  emergencyRoomChildDefault:10, emergencyRoomChildAvailable:3,  operatingRoom:5, neurosurgeryRoom:2, neonatalRoom:4, thoracicRoom:1, generalRoom:120, ct:true,  mri:true,  angiography:true,  ventilator:true,  crrt:true,  ecmo:true  },
  H002: { emergencyRoomDefault:25, emergencyRoomAvailable:12, emergencyRoomChildDefault:8,  emergencyRoomChildAvailable:5,  operatingRoom:4, neurosurgeryRoom:1, neonatalRoom:6, thoracicRoom:2, generalRoom:95,  ct:true,  mri:true,  angiography:true,  ventilator:true,  crrt:true,  ecmo:false },
  H003: { emergencyRoomDefault:20, emergencyRoomAvailable:3,  emergencyRoomChildDefault:6,  emergencyRoomChildAvailable:0,  operatingRoom:3, neurosurgeryRoom:0, neonatalRoom:3, thoracicRoom:1, generalRoom:80,  ct:true,  mri:true,  angiography:false, ventilator:true,  crrt:false, ecmo:false },
  H004: { emergencyRoomDefault:35, emergencyRoomAvailable:15, emergencyRoomChildDefault:12, emergencyRoomChildAvailable:7,  operatingRoom:6, neurosurgeryRoom:3, neonatalRoom:8, thoracicRoom:2, generalRoom:150, ct:true,  mri:true,  angiography:true,  ventilator:true,  crrt:true,  ecmo:true  },
  H005: { emergencyRoomDefault:18, emergencyRoomAvailable:6,  emergencyRoomChildDefault:5,  emergencyRoomChildAvailable:2,  operatingRoom:2, neurosurgeryRoom:1, neonatalRoom:2, thoracicRoom:0, generalRoom:60,  ct:true,  mri:false, angiography:false, ventilator:true,  crrt:false, ecmo:false },
  H006: { emergencyRoomDefault:22, emergencyRoomAvailable:9,  emergencyRoomChildDefault:7,  emergencyRoomChildAvailable:4,  operatingRoom:3, neurosurgeryRoom:1, neonatalRoom:3, thoracicRoom:1, generalRoom:75,  ct:true,  mri:true,  angiography:true,  ventilator:true,  crrt:true,  ecmo:false },
}

const delay = (ms: number) => new Promise(r => setTimeout(r, ms))

function PanelHeader({ title, subtitle, trailing }: {
  title: string; subtitle: string; trailing?: React.ReactNode
}) {
  return (
    <div className="flex items-center px-3.5 py-3 border-b border-dispatch-lineSoft shrink-0">
      <div className="flex-1 min-w-0">
        <div className="text-[10px] font-semibold tracking-[1.6px] uppercase text-dispatch-textMuted font-mono">{subtitle}</div>
        <div className="text-sm font-semibold text-dispatch-text tracking-[-0.2px] mt-px">{title}</div>
      </div>
      {trailing}
    </div>
  )
}

function BedRow({ label, subLabel, available, total }: {
  label: string; subLabel: string; available: number; total: number | null
}) {
  const state = available === 0 ? 'red' : available <= 3 ? 'amber' : 'green'
  const styles = {
    red: {
      wrap:   'bg-dispatch-red-soft ring-1 ring-inset ring-dispatch-red-edge',
      number: 'text-[#fca5a5]',
      tag:    'bg-dispatch-red',
      label:  '없음',
    },
    amber: {
      wrap:   'bg-dispatch-amber-soft ring-1 ring-inset ring-[rgba(245,158,11,0.42)]',
      number: 'text-[#fcd34d]',
      tag:    'bg-dispatch-amber',
      label:  '여유 적음',
    },
    green: {
      wrap:   'bg-dispatch-green-soft ring-1 ring-inset ring-dispatch-green-edge',
      number: 'text-[#86efac]',
      tag:    'bg-dispatch-green',
      label:  '여유',
    },
  }[state]
  return (
    <div className={`flex items-center py-3 px-3.5 rounded-[5px] gap-3 ${styles.wrap}`}>
      <div className="flex-1 min-w-0">
        <div className="text-[13px] font-semibold text-dispatch-text tracking-[-0.2px]">{label}</div>
        <div className="font-mono text-[10px] text-dispatch-textMuted tracking-[0.8px] mt-0.5">{subLabel}</div>
      </div>
      <div className="flex items-baseline gap-1 font-mono tabular-nums">
        <span className={`text-[22px] font-bold tracking-[-0.5px] ${styles.number}`}>{available}</span>
        {total != null && <span className="text-xs text-dispatch-textMuted">/ {total}</span>}
      </div>
      <div className={`font-mono text-[9px] font-bold tracking-[1px] text-[#0b0d10] py-[3px] px-[7px] rounded-[3px] min-w-[62px] text-center ${styles.tag}`}>
        {styles.label}
      </div>
    </div>
  )
}

function EquipChip({ name, available }: { name: string; available: boolean }) {
  return (
    <div
      className={`py-[11px] px-3.5 rounded-[5px] flex items-center gap-2.5 ring-1 ring-inset ${
        available
          ? 'bg-dispatch-green-soft ring-dispatch-green-edge'
          : 'bg-[rgba(148,163,184,0.08)] ring-[rgba(148,163,184,0.2)]'
      }`}
    >
      <div
        className={`w-[22px] h-[22px] rounded-full text-[#0b0d10] text-[11px] font-extrabold flex items-center justify-center font-mono shrink-0 ${
          available ? 'bg-dispatch-green' : 'bg-[rgba(148,163,184,0.3)]'
        }`}
      >
        {available ? '✓' : '×'}
      </div>
      <div className="flex-1 min-w-0">
        <div className={`text-[13px] font-semibold ${available ? 'text-dispatch-text' : 'text-dispatch-textDim'}`}>{name}</div>
        <div className={`font-mono text-[9.5px] tracking-[1px] mt-px ${available ? 'text-[#4ade80]' : 'text-dispatch-textMuted'}`}>
          {available ? 'AVAILABLE' : 'UNAVAILABLE'}
        </div>
      </div>
    </div>
  )
}

const HospitalPage = () => {
  const [query, setQuery]           = useState('')
  const [hospitals, setHospitals]   = useState<Hospital[]>(DUMMY_HOSPITALS)
  const [selectedPid, setSelectedPid] = useState<string | null>('H004')
  const [details, setDetails]       = useState<HospitalDetails | null>(DUMMY_DETAILS['H004'] ?? null)
  const [listLoading, setListLoading] = useState(false)
  const [detailLoading, setDetailLoading] = useState(false)
  const [error, setError]           = useState<string | null>(null)

  const selected = hospitals.find(h => h.pid === selectedPid) ?? null

  const search = async () => {
    if (!query.trim()) return
    setListLoading(true); setError(null)
    try {
      if (import.meta.env.DEV) {
        await delay(600)
        setHospitals(DUMMY_HOSPITALS)
      } else {
        const res = await authFetch('/api/v1/hospital/search', { method: 'POST', body: JSON.stringify({ location: query }) })
        if (!res.ok) throw new Error(`HTTP ${res.status}`)
        const data = await res.json()
        setHospitals(
          data.filter((i: any) => i.location.includes('서울'))
            .map((i: any, idx: number) => ({ pid: i.pid, name: i.name, location: i.location, call: i.call, time: `${(idx + 2) * 10}분` }))
        )
      }
    } catch (e) { setError((e as Error).message) }
    finally { setListLoading(false) }
  }

  const loadDetails = async (pid: string) => {
    setSelectedPid(pid); setDetailLoading(true)
    try {
      if (import.meta.env.DEV) {
        await delay(400)
        setDetails(DUMMY_DETAILS[pid] ?? null)
      } else {
        const res = await authFetch('/api/v1/hospital/emergency')
        if (!res.ok) throw new Error(`HTTP ${res.status}`)
        const data = await res.json()
        setDetails(data.find((x: any) => x.id === 1) ?? null)
      }
    } catch { setDetails(null) }
    finally { setDetailLoading(false) }
  }

  const beds = useMemo(() => !details ? [] : [
    { label: '응급실',    subLabel: 'EMERGENCY ROOM',  available: details.emergencyRoomAvailable,      total: details.emergencyRoomDefault },
    { label: '소아 응급실', subLabel: 'PEDIATRIC ER',    available: details.emergencyRoomChildAvailable,  total: details.emergencyRoomChildDefault },
    { label: '수술실',    subLabel: 'OPERATING ROOM',   available: details.operatingRoom,               total: null },
    { label: '신경외과',   subLabel: 'NEUROSURGERY',     available: details.neurosurgeryRoom,             total: null },
    { label: '신생아실',   subLabel: 'NEONATAL',         available: details.neonatalRoom,                 total: null },
    { label: '흉부외과',   subLabel: 'THORACIC',         available: details.thoracicRoom,                 total: null },
    { label: '일반 병동',  subLabel: 'GENERAL WARD',     available: details.generalRoom,                  total: null },
  ], [details])

  const equipment = useMemo(() => !details ? [] : [
    { name: 'CT',        available: details.ct },
    { name: 'MRI',       available: details.mri },
    { name: '혈관촬영기', available: details.angiography },
    { name: '인공호흡기', available: details.ventilator },
    { name: 'CRRT',      available: details.crrt },
    { name: 'ECMO',      available: details.ecmo },
  ], [details])

  return (
    <div
      className="bg-dispatch-bg grid gap-2.5 p-2.5 font-ui text-dispatch-text box-border"
      style={{ height: 'calc(100vh - 52px)', gridTemplateColumns: '40% 60%' }}
    >
      {/* LEFT — Search + list */}
      <div className="bg-dispatch-elev rounded-md ring-1 ring-inset ring-dispatch-line flex flex-col min-h-0">
        <PanelHeader
          title="이송병원 현황"
          subtitle="TRANSPORT DESTINATIONS"
          trailing={import.meta.env.DEV && (
            <span className="font-mono text-[9px] font-bold tracking-[1px] bg-dispatch-amber-soft text-dispatch-amber py-[3px] px-1.5 rounded-[3px] ring-1 ring-inset ring-[rgba(245,158,11,0.35)]">
              🔧 DEV
            </span>
          )}
        />

        <div className="p-3 flex gap-1.5 border-b border-dispatch-lineSoft shrink-0">
          <input
            type="text" value={query}
            onChange={e => setQuery(e.target.value)}
            onKeyDown={e => e.key === 'Enter' && search()}
            placeholder="주소를 입력하세요 (예: 서울 마포구)"
            className="flex-1 font-ui text-[13px] py-[9px] px-3 bg-dispatch-card text-dispatch-text border-0 rounded ring-1 ring-inset ring-dispatch-line outline-none"
          />
          <button
            onClick={search}
            className="cursor-pointer font-ui border-0 bg-dispatch-blue text-white text-[13px] font-semibold px-[18px] rounded"
          >검색</button>
        </div>

        <div className="dispatch-scroll flex-1 min-h-0 overflow-y-auto p-2">
          {listLoading ? (
            <div className="flex justify-center py-10">
              <div
                className="w-5 h-5 rounded-full border-2 border-dispatch-line"
                style={{ borderTopColor: '#3b82f6', animation: 'dispatchSpin 0.9s linear infinite' }}
              />
            </div>
          ) : error ? (
            <div className="p-4 text-[#fca5a5] text-xs">서버 오류: {error}</div>
          ) : hospitals.map(h => {
            const active = h.pid === selectedPid
            const mins   = parseInt(h.time, 10) || 0
            const timeColor = mins <= 10 ? '#22c55e' : mins <= 20 ? '#f59e0b' : '#ef4444'
            return (
              <button
                key={h.pid}
                onClick={() => loadDetails(h.pid)}
                className={`w-full block text-left border-0 cursor-pointer font-ui py-3 pr-3 pl-3.5 rounded mb-0.5 relative text-dispatch-text ${
                  active ? 'bg-dispatch-blue-soft' : 'bg-transparent'
                }`}
              >
                {active && <span className="absolute left-0 top-2 bottom-2 w-[3px] bg-dispatch-blue rounded-sm" />}
                <div className="flex justify-between items-center gap-2.5 mb-1.5">
                  <span className={`text-sm font-semibold tracking-[-0.2px] ${active ? 'text-[#dbeafe]' : 'text-dispatch-text'}`}>{h.name}</span>
                  <span
                    className="font-mono text-[11px] font-bold py-0.5 px-2 rounded-[3px] shrink-0"
                    style={{
                      color: timeColor,
                      background: `${timeColor}1a`,
                      boxShadow: `inset 0 0 0 1px ${timeColor}55`,
                    }}
                  >{h.time}</span>
                </div>
                <div className="text-[11px] text-dispatch-textDim mb-[3px]">{h.location}</div>
                <div className="font-mono text-[10.5px] text-dispatch-textMuted tracking-[0.5px]">☎ {h.call}</div>
              </button>
            )
          })}
        </div>
      </div>

      {/* RIGHT — Detail */}
      <div className="bg-dispatch-elev rounded-md ring-1 ring-inset ring-dispatch-line flex flex-col min-h-0">
        {!selected ? (
          <div className="flex-1 flex flex-col items-center justify-center text-dispatch-textMuted gap-4">
            <div className="w-16 h-16 rounded-full bg-dispatch-card ring-1 ring-inset ring-dispatch-line flex items-center justify-center font-mono text-[22px]">
              🛏
            </div>
            <div className="text-sm">이송병원을 선택해 주세요</div>
            <div className="font-mono text-[10px] tracking-[1px]">← SELECT A HOSPITAL</div>
          </div>
        ) : (
          <>
            <div className="py-5 px-6 border-b border-dispatch-lineSoft shrink-0">
              <div className="flex items-start justify-between gap-4">
                <div className="flex-1 min-w-0">
                  <div className="font-mono text-[10px] text-dispatch-textMuted tracking-[1.4px] mb-1">HOSPITAL · {selected.pid}</div>
                  <h2 className="m-0 text-[22px] font-semibold tracking-[-0.5px] text-dispatch-text">{selected.name}</h2>
                  <div className="mt-2 flex gap-[18px] flex-wrap text-xs text-dispatch-textDim">
                    <span>📍 {selected.location}</span>
                    <span className="font-mono">☎ {selected.call}</span>
                  </div>
                </div>
                <div className="text-right shrink-0">
                  <div className="font-mono text-[10px] text-dispatch-textMuted tracking-[1.3px]">ETA · 예상 이송</div>
                  <div className="font-mono text-[28px] font-bold text-[#93c5fd] tracking-[-1px]">{selected.time}</div>
                </div>
              </div>
            </div>

            <div className="dispatch-scroll flex-1 min-h-0 overflow-y-auto py-5 px-6">
              {detailLoading ? (
                <div className="flex justify-center py-10">
                  <div
                    className="w-6 h-6 rounded-full border-2 border-dispatch-line"
                    style={{ borderTopColor: '#3b82f6', animation: 'dispatchSpin 0.9s linear infinite' }}
                  />
                </div>
              ) : !details ? (
                <div className="p-5 text-dispatch-textMuted text-[13px]">병원 데이터를 찾을 수 없습니다.</div>
              ) : (
                <>
                  <div className="mb-6">
                    <div className="flex items-baseline gap-2.5 mb-3">
                      <div className="font-mono text-[10px] text-dispatch-textMuted tracking-[1.4px]">§1</div>
                      <div className="text-[15px] font-semibold text-dispatch-text">🛏 병상 현황</div>
                      <div className="flex-1 h-px bg-dispatch-lineSoft" />
                      <div className="font-mono text-[9.5px] text-dispatch-textMuted tracking-[1px]">REALTIME · EDT</div>
                    </div>
                    <div className="flex flex-col gap-1.5">
                      {beds.map(b => <BedRow key={b.label} {...b} />)}
                    </div>
                  </div>

                  <div>
                    <div className="flex items-baseline gap-2.5 mb-3">
                      <div className="font-mono text-[10px] text-dispatch-textMuted tracking-[1.4px]">§2</div>
                      <div className="text-[15px] font-semibold text-dispatch-text">🔬 장비 현황</div>
                      <div className="flex-1 h-px bg-dispatch-lineSoft" />
                      <div className="font-mono text-[9.5px] text-dispatch-textMuted tracking-[1px]">
                        {equipment.filter(e => e.available).length} / {equipment.length} · ONLINE
                      </div>
                    </div>
                    <div className="grid grid-cols-2 gap-2">
                      {equipment.map(e => <EquipChip key={e.name} {...e} />)}
                    </div>
                  </div>
                </>
              )}
            </div>
          </>
        )}
      </div>
    </div>
  )
}

export default HospitalPage
