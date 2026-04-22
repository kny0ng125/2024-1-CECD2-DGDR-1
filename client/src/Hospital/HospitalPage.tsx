import { useState, useMemo } from 'react'
import { authFetch } from '@/lib/authFetch'
import { T } from '@/lib/theme'

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
    <div style={{ display: 'flex', alignItems: 'center', padding: '12px 14px', borderBottom: `1px solid ${T.lineSoft}`, flexShrink: 0 }}>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ fontSize: 10, fontWeight: 600, letterSpacing: 1.6, textTransform: 'uppercase' as const, color: T.textMuted, fontFamily: T.mono }}>{subtitle}</div>
        <div style={{ fontSize: 14, fontWeight: 600, color: T.text, letterSpacing: -0.2, marginTop: 1 }}>{title}</div>
      </div>
      {trailing}
    </div>
  )
}

function BedRow({ label, subLabel, available, total }: {
  label: string; subLabel: string; available: number; total: number | null
}) {
  const state = available === 0 ? 'red' : available <= 3 ? 'amber' : 'green'
  const cfg = {
    red:   { bg: T.redSoft,   edge: T.redEdge,                      tag: '없음',     tagBg: T.red,   num: '#fca5a5' },
    amber: { bg: T.amberSoft, edge: 'rgba(245,158,11,0.42)',         tag: '여유 적음', tagBg: T.amber, num: '#fcd34d' },
    green: { bg: T.greenSoft, edge: T.greenEdge,                     tag: '여유',     tagBg: T.green, num: '#86efac' },
  }[state]
  return (
    <div style={{
      display: 'flex', alignItems: 'center', padding: '12px 14px',
      background: cfg.bg, boxShadow: `inset 0 0 0 1px ${cfg.edge}`,
      borderRadius: 5, gap: 12,
    }}>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ fontSize: 13, fontWeight: 600, color: T.text, letterSpacing: -0.2 }}>{label}</div>
        <div style={{ fontFamily: T.mono, fontSize: 10, color: T.textMuted, letterSpacing: 0.8, marginTop: 2 }}>{subLabel}</div>
      </div>
      <div style={{ display: 'flex', alignItems: 'baseline', gap: 4, fontFamily: T.mono, fontVariantNumeric: 'tabular-nums' }}>
        <span style={{ fontSize: 22, fontWeight: 700, color: cfg.num, letterSpacing: -0.5 }}>{available}</span>
        {total != null && <span style={{ fontSize: 12, color: T.textMuted }}>/ {total}</span>}
      </div>
      <div style={{
        fontFamily: T.mono, fontSize: 9, fontWeight: 700, letterSpacing: 1,
        background: cfg.tagBg, color: '#0b0d10',
        padding: '3px 7px', borderRadius: 3, minWidth: 62, textAlign: 'center',
      }}>{cfg.tag}</div>
    </div>
  )
}

function EquipChip({ name, available }: { name: string; available: boolean }) {
  return (
    <div style={{
      padding: '11px 14px',
      background: available ? T.greenSoft : 'rgba(148,163,184,0.08)',
      boxShadow: `inset 0 0 0 1px ${available ? T.greenEdge : 'rgba(148,163,184,0.2)'}`,
      borderRadius: 5, display: 'flex', alignItems: 'center', gap: 10,
    }}>
      <div style={{
        width: 22, height: 22, borderRadius: 11,
        background: available ? T.green : 'rgba(148,163,184,0.3)',
        color: '#0b0d10', fontSize: 11, fontWeight: 800,
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        fontFamily: T.mono, flexShrink: 0,
      }}>{available ? '✓' : '×'}</div>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ fontSize: 13, fontWeight: 600, color: available ? T.text : T.textDim }}>{name}</div>
        <div style={{ fontFamily: T.mono, fontSize: 9.5, color: available ? '#4ade80' : T.textMuted, letterSpacing: 1, marginTop: 1 }}>
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
    <div style={{
      height: 'calc(100vh - 52px)',
      background: T.bg,
      display: 'grid', gridTemplateColumns: '40% 60%',
      gap: 10, padding: 10,
      fontFamily: T.ui, color: T.text, boxSizing: 'border-box',
    }}>
      {/* LEFT — Search + list */}
      <div style={{ background: T.bgElev, borderRadius: 6, boxShadow: `inset 0 0 0 1px ${T.line}`, display: 'flex', flexDirection: 'column', minHeight: 0 }}>
        <PanelHeader
          title="이송병원 현황"
          subtitle="TRANSPORT DESTINATIONS"
          trailing={import.meta.env.DEV && (
            <span style={{
              fontFamily: T.mono, fontSize: 9, fontWeight: 700, letterSpacing: 1,
              background: T.amberSoft, color: T.amber,
              padding: '3px 6px', borderRadius: 3,
              boxShadow: `inset 0 0 0 1px rgba(245,158,11,0.35)`,
            }}>🔧 DEV</span>
          )}
        />

        <div style={{ padding: 12, display: 'flex', gap: 6, borderBottom: `1px solid ${T.lineSoft}`, flexShrink: 0 }}>
          <input
            type="text" value={query}
            onChange={e => setQuery(e.target.value)}
            onKeyDown={e => e.key === 'Enter' && search()}
            placeholder="주소를 입력하세요 (예: 서울 마포구)"
            style={{
              flex: 1, fontFamily: T.ui, fontSize: 13, padding: '9px 12px',
              background: T.bgCard, color: T.text, border: 'none', borderRadius: 4,
              boxShadow: `inset 0 0 0 1px ${T.line}`, outline: 'none',
            }}
          />
          <button onClick={search}
            style={{
              cursor: 'pointer', fontFamily: T.ui, border: 'none',
              background: T.accentBlue, color: '#fff',
              fontSize: 13, fontWeight: 600, padding: '0 18px', borderRadius: 4,
            }}>검색</button>
        </div>

        <div className="dispatch-scroll" style={{ flex: 1, minHeight: 0, overflowY: 'auto', padding: 8 }}>
          {listLoading ? (
            <div style={{ display: 'flex', justifyContent: 'center', padding: 40 }}>
              <div style={{ width: 20, height: 20, borderRadius: '50%', border: `2px solid ${T.line}`, borderTopColor: T.accentBlue, animation: 'dispatchSpin 0.9s linear infinite' }} />
            </div>
          ) : error ? (
            <div style={{ padding: 16, color: '#fca5a5', fontSize: 12 }}>서버 오류: {error}</div>
          ) : hospitals.map(h => {
            const active = h.pid === selectedPid
            const mins   = parseInt(h.time, 10) || 0
            const tc     = mins <= 10 ? T.green : mins <= 20 ? T.amber : T.red
            return (
              <button key={h.pid}
                onClick={() => loadDetails(h.pid)}
                style={{
                  width: '100%', display: 'block', textAlign: 'left',
                  border: 'none', cursor: 'pointer', fontFamily: T.ui,
                  background: active ? T.accentBlueSoft : 'transparent',
                  padding: '12px 12px 12px 14px',
                  borderRadius: 4, marginBottom: 2, position: 'relative', color: T.text,
                }}>
                {active && <span style={{ position: 'absolute', left: 0, top: 8, bottom: 8, width: 3, background: T.accentBlue, borderRadius: 2 }} />}
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 10, marginBottom: 6 }}>
                  <span style={{ fontSize: 14, fontWeight: 600, color: active ? '#dbeafe' : T.text, letterSpacing: -0.2 }}>{h.name}</span>
                  <span style={{ fontFamily: T.mono, fontSize: 11, fontWeight: 700, color: tc, padding: '2px 8px', borderRadius: 3, background: `${tc}1a`, boxShadow: `inset 0 0 0 1px ${tc}55`, flexShrink: 0 }}>{h.time}</span>
                </div>
                <div style={{ fontSize: 11, color: T.textDim, marginBottom: 3 }}>{h.location}</div>
                <div style={{ fontFamily: T.mono, fontSize: 10.5, color: T.textMuted, letterSpacing: 0.5 }}>☎ {h.call}</div>
              </button>
            )
          })}
        </div>
      </div>

      {/* RIGHT — Detail */}
      <div style={{ background: T.bgElev, borderRadius: 6, boxShadow: `inset 0 0 0 1px ${T.line}`, display: 'flex', flexDirection: 'column', minHeight: 0 }}>
        {!selected ? (
          <div style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', color: T.textMuted, gap: 16 }}>
            <div style={{ width: 64, height: 64, borderRadius: 32, background: T.bgCard, boxShadow: `inset 0 0 0 1px ${T.line}`, display: 'flex', alignItems: 'center', justifyContent: 'center', fontFamily: T.mono, fontSize: 22 }}>🛏</div>
            <div style={{ fontSize: 14 }}>이송병원을 선택해 주세요</div>
            <div style={{ fontFamily: T.mono, fontSize: 10, letterSpacing: 1 }}>← SELECT A HOSPITAL</div>
          </div>
        ) : (
          <>
            <div style={{ padding: '20px 24px', borderBottom: `1px solid ${T.lineSoft}`, flexShrink: 0 }}>
              <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 16 }}>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ fontFamily: T.mono, fontSize: 10, color: T.textMuted, letterSpacing: 1.4, marginBottom: 4 }}>HOSPITAL · {selected.pid}</div>
                  <h2 style={{ margin: 0, fontSize: 22, fontWeight: 600, letterSpacing: -0.5, color: T.text }}>{selected.name}</h2>
                  <div style={{ marginTop: 8, display: 'flex', gap: 18, flexWrap: 'wrap' as const, fontSize: 12, color: T.textDim }}>
                    <span>📍 {selected.location}</span>
                    <span style={{ fontFamily: T.mono }}>☎ {selected.call}</span>
                  </div>
                </div>
                <div style={{ textAlign: 'right', flexShrink: 0 }}>
                  <div style={{ fontFamily: T.mono, fontSize: 10, color: T.textMuted, letterSpacing: 1.3 }}>ETA · 예상 이송</div>
                  <div style={{ fontFamily: T.mono, fontSize: 28, fontWeight: 700, color: '#93c5fd', letterSpacing: -1 }}>{selected.time}</div>
                </div>
              </div>
            </div>

            <div className="dispatch-scroll" style={{ flex: 1, minHeight: 0, overflowY: 'auto', padding: '20px 24px' }}>
              {detailLoading ? (
                <div style={{ display: 'flex', justifyContent: 'center', padding: 40 }}>
                  <div style={{ width: 24, height: 24, borderRadius: '50%', border: `2px solid ${T.line}`, borderTopColor: T.accentBlue, animation: 'dispatchSpin 0.9s linear infinite' }} />
                </div>
              ) : !details ? (
                <div style={{ padding: 20, color: T.textMuted, fontSize: 13 }}>병원 데이터를 찾을 수 없습니다.</div>
              ) : (
                <>
                  <div style={{ marginBottom: 24 }}>
                    <div style={{ display: 'flex', alignItems: 'baseline', gap: 10, marginBottom: 12 }}>
                      <div style={{ fontFamily: T.mono, fontSize: 10, color: T.textMuted, letterSpacing: 1.4 }}>§1</div>
                      <div style={{ fontSize: 15, fontWeight: 600, color: T.text }}>🛏 병상 현황</div>
                      <div style={{ flex: 1, height: 1, background: T.lineSoft }} />
                      <div style={{ fontFamily: T.mono, fontSize: 9.5, color: T.textMuted, letterSpacing: 1 }}>REALTIME · EDT</div>
                    </div>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                      {beds.map(b => <BedRow key={b.label} {...b} />)}
                    </div>
                  </div>

                  <div>
                    <div style={{ display: 'flex', alignItems: 'baseline', gap: 10, marginBottom: 12 }}>
                      <div style={{ fontFamily: T.mono, fontSize: 10, color: T.textMuted, letterSpacing: 1.4 }}>§2</div>
                      <div style={{ fontSize: 15, fontWeight: 600, color: T.text }}>🔬 장비 현황</div>
                      <div style={{ flex: 1, height: 1, background: T.lineSoft }} />
                      <div style={{ fontFamily: T.mono, fontSize: 9.5, color: T.textMuted, letterSpacing: 1 }}>
                        {equipment.filter(e => e.available).length} / {equipment.length} · ONLINE
                      </div>
                    </div>
                    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 8 }}>
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
