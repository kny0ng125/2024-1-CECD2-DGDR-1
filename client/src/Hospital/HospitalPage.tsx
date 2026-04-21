import { useState } from 'react'
import { Loader2 } from 'lucide-react'
import { authFetch } from '@/lib/authFetch'

interface Hospital {
  pid: string
  name: string
  location: string
  call: string
  time: string
}

interface HospitalDetails {
  emergencyRoomDefault: number
  emergencyRoomAvailable: number
  emergencyRoomChildDefault: number
  emergencyRoomChildAvailable: number
  operatingRoom: number
  neurosurgeryRoom: number
  neonatalRoom: number
  thoracicRoom: number
  generalRoom: number
  ct: boolean
  mri: boolean
  angiography: boolean
  ventilator: boolean
  crrt: boolean
  ecmo: boolean
}

const HospitalPage = () => {
  const [location, setLocation] = useState('')
  const [hospitals, setHospitals] = useState<Hospital[]>([])
  const [selected, setSelected] = useState<Hospital | null>(null)
  const [details, setDetails] = useState<HospitalDetails | null>(null)
  const [listLoading, setListLoading] = useState(false)
  const [detailLoading, setDetailLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const search = async () => {
    if (!location.trim()) return
    setListLoading(true)
    setError(null)
    try {
      const res = await authFetch('/api/v1/hospital/search', {
        method: 'POST',
        body: JSON.stringify({ location }),
      })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      const data = await res.json()
      setHospitals(
        data
          .filter((i: any) => i.location.includes('서울'))
          .map((i: any, idx: number) => ({
            pid: i.pid,
            name: i.name,
            location: i.location,
            call: i.call,
            time: `${(idx + 2) * 15}m`,
          }))
      )
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setListLoading(false)
    }
  }

  const loadDetails = async (hospital: Hospital) => {
    setSelected(hospital)
    setDetailLoading(true)
    try {
      const res = await authFetch('/api/v1/hospital/emergency')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      const data = await res.json()
      const found = data.find((x: any) => x.id === 1) // TODO: pid 기반 조회로 변경
      setDetails(found ?? null)
    } catch (e) {
      console.error(e)
      setDetails(null)
    } finally {
      setDetailLoading(false)
    }
  }

  return (
    <div className="flex gap-5 p-5 h-[calc(100vh-60px)] bg-[#f0f0f0]">
      {/* 좌: 병원 검색 + 목록 */}
      <div className="flex-1 bg-white p-5 rounded-[10px] shadow-[0_4px_10px_rgba(0,0,0,0.1)] overflow-y-auto">
        <h2 className="text-[1.6rem] font-bold mb-5">이송병원 현황</h2>
        <div className="flex gap-3 mb-5">
          <input
            type="text"
            value={location}
            onChange={(e) => setLocation(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && search()}
            placeholder="예: 서울특별시 중구 필동로1길 30"
            className="flex-1 text-[1.1rem] p-3 border-0 border-b-2 border-[#007bff] focus:outline-none"
          />
          <button onClick={search} className="bg-[#007bff] text-white rounded-[6px] px-6">
            검색
          </button>
        </div>
        {listLoading && (
          <div className="flex justify-center py-10">
            <Loader2 className="animate-spin w-8 h-8" />
          </div>
        )}
        {error && <p className="text-[#d32f2f]">서버 오류: {error}</p>}
        {!listLoading && !error && hospitals.length === 0 && (
          <p className="text-[#888]">병원을 검색해주세요.</p>
        )}
        {hospitals.map((h) => (
          <div
            key={h.pid}
            onClick={() => loadDetails(h)}
            className={`flex justify-between items-center py-[15px] px-3 border-b border-[#ddd] cursor-pointer transition-colors ${
              selected?.pid === h.pid ? 'bg-[#d0e0ff]' : 'hover:bg-[#f7f7f7]'
            }`}
          >
            <div className="w-[300px] text-[1.2rem] font-bold">{h.name}</div>
            <div className="text-[0.9rem] text-[#555] flex-grow-[3]">
              <span>위치: {h.location}</span>
              <br />
              <span>전화번호: {h.call}</span>
            </div>
          </div>
        ))}
      </div>

      {/* 우: 상세 정보 */}
      <div className="flex-1 bg-white p-5 rounded-[10px] shadow-[0_4px_10px_rgba(0,0,0,0.1)] overflow-y-auto">
        {!selected ? (
          <div className="text-center text-[#888] mt-20">병원을 선택하세요.</div>
        ) : detailLoading ? (
          <div className="flex justify-center py-10">
            <Loader2 className="animate-spin w-8 h-8" />
          </div>
        ) : !details ? (
          <p className="text-[#888]">병원 데이터를 찾을 수 없습니다.</p>
        ) : (
          <>
            <h3 className="text-[1.4rem] font-bold mb-5">{selected.name}</h3>
            <h4 className="mt-5 mb-3 text-[#007bff] font-bold">병상 현황</h4>
            <ul className="list-none p-0 m-0">
              {[
                `응급실 성인 병상: 총 ${details.emergencyRoomDefault} 병상 중 ${details.emergencyRoomAvailable} 병상 이용 가능`,
                `응급실 소아 병상: 총 ${details.emergencyRoomChildDefault} 병상 중 ${details.emergencyRoomChildAvailable} 병상 이용 가능`,
                `일반 수술실: ${details.operatingRoom} 병상 이용 가능`,
                `신경외과 수술실: ${details.neurosurgeryRoom} 병상 이용 가능`,
                `신생아 중환자실: ${details.neonatalRoom} 병상 이용 가능`,
                `흉부 외과 중환자실: ${details.thoracicRoom} 병상 이용 가능`,
                `일반 병실: ${details.generalRoom} 병상 이용 가능`,
              ].map((t, i) => (
                <li
                  key={i}
                  className="mb-[10px] p-[10px_15px] bg-[#f9f9f9] rounded-[5px] border border-[#ddd]"
                >
                  {t}
                </li>
              ))}
            </ul>
            <h4 className="mt-5 mb-3 text-[#007bff] font-bold">장비 현황</h4>
            <ul className="list-none p-0 m-0">
              {(
                [
                  ['CT', details.ct],
                  ['MRI', details.mri],
                  ['혈관촬영기', details.angiography],
                  ['인공호흡기', details.ventilator],
                  ['CRRT', details.crrt],
                  ['ECMO', details.ecmo],
                ] as [string, boolean][]
              ).map(([name, ok]) => (
                <li
                  key={name}
                  className="mb-[10px] p-[10px_15px] bg-[#f9f9f9] rounded-[5px] border border-[#ddd]"
                >
                  {name}: {ok ? '사용 가능' : '사용 불가'}
                </li>
              ))}
            </ul>
          </>
        )}
      </div>
    </div>
  )
}

export default HospitalPage
