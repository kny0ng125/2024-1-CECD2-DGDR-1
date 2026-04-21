import { useState } from 'react'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import DetailInfoModal from './DetailInfoModal'
import { Loader2 } from 'lucide-react'
import { HOSPITAL_API_BASE_URL } from '@/lib/config'

interface Hospital {
  pid: string
  name: string
  location: string
  call: string
  time: string
}

interface HospitalListModalProps {
  onClose: () => void
}

const HospitalListModal = ({ onClose }: HospitalListModalProps) => {
  const [hospitals, setHospitals] = useState<Hospital[]>([])
  const [selectedHospital, setSelectedHospital] = useState<Hospital | null>(null)
  const [loading, setLoading] = useState(false)
  const [showErrorModal, setShowErrorModal] = useState(false)
  const [location, setLocation] = useState('')

  const fetchHospitals = async (inputLocation: string) => {
    setLoading(true)
    try {
      const response = await fetch(`${HOSPITAL_API_BASE_URL}/hospital/search`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ location: inputLocation }),
      })
      if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`)
      const data = await response.json()
      setHospitals(
        data
          .filter((item: any) => item.location.includes('서울'))
          .map((item: any, index: number) => ({
            pid: item.pid,
            name: item.name,
            location: item.location,
            call: item.call,
            time: `${(index + 2) * 15}m`,
          }))
      )
    } catch {
      setShowErrorModal(true)
    } finally {
      setLoading(false)
    }
  }

  if (loading) {
    return (
      <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
        <Loader2 className="animate-spin w-8 h-8 text-white" />
      </div>
    )
  }

  if (showErrorModal) {
    return (
      <Dialog open onOpenChange={() => { setShowErrorModal(false); onClose() }}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>서버 오류</DialogTitle>
          </DialogHeader>
          <p>서버로부터 응답이 없습니다.</p>
          <div className="flex justify-end">
            <button
              onClick={() => { setShowErrorModal(false); onClose() }}
              className="bg-[#dc3545] text-white px-4 py-2 rounded"
            >
              확인
            </button>
          </div>
        </DialogContent>
      </Dialog>
    )
  }

  return (
    <Dialog open onOpenChange={onClose}>
      <DialogContent className="max-w-[1200px] h-[700px] max-h-[700px] overflow-y-auto">
        <DialogHeader>
          <DialogTitle className="text-[#000000] text-[1.6rem] font-bold text-center">
            이송병원 현황
          </DialogTitle>
        </DialogHeader>
        <div>
          <div className="flex mb-3 items-center">
            <input
              type="text"
              value={location}
              onChange={(e) => setLocation(e.target.value)}
              placeholder="예: 서울특별시 중구 필동로1길 30"
              className="flex-1 text-[1.2rem] p-3 border-0 border-b-2 border-[#007bff] focus:outline-none w-[85%]"
            />
            <button
              onClick={() => location.trim() && fetchHospitals(location)}
              className="bg-[#007bff] text-white rounded-[6px] ml-[170px] px-4 py-[10px] min-w-[100px]"
            >
              검색
            </button>
          </div>
          <div>
            {hospitals.length > 0 ? (
              hospitals.map((hospital) => (
                <div
                  key={hospital.pid}
                  className="flex justify-between items-center py-[15px] border-b border-[#ddd] hover:bg-[#f7f7f7] transition-colors"
                >
                  <div className="flex justify-between items-center w-full">
                    <div className="w-[300px] text-[1.2rem] font-bold">{hospital.name}</div>
                    <div className="text-[0.9rem] text-[#555] flex-grow-[3]">
                      <span>위치: {hospital.location}</span>
                      <br />
                      <span>전화번호: {hospital.call}</span>
                    </div>
                    <button
                      onClick={() => setSelectedHospital(hospital)}
                      className="bg-[#17a2b8] text-white p-2 text-[1.1rem] rounded"
                    >
                      ℹ️
                    </button>
                  </div>
                </div>
              ))
            ) : (
              <p>병원을 검색해주세요.</p>
            )}
          </div>
        </div>
        {selectedHospital && <DetailInfoModal onClose={() => setSelectedHospital(null)} />}
      </DialogContent>
    </Dialog>
  )
}

export default HospitalListModal
