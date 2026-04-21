import { useEffect, useState } from 'react'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog'
import { HOSPITAL_API_BASE_URL } from '@/lib/config'

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

interface DetailInfoModalProps {
  onClose: () => void
}

const DetailInfoModal = ({ onClose }: DetailInfoModalProps) => {
  const [hospitalDetails, setHospitalDetails] = useState<HospitalDetails | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const fetchHospitalDetails = async () => {
      try {
        const response = await fetch(`${HOSPITAL_API_BASE_URL}/hospital/emergency`, {
          headers: { 'Content-Type': 'application/json' },
        })
        if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`)
        const data = await response.json()
        const hospitalData = data.find((item: any) => item.id === 1)
        setHospitalDetails(hospitalData)
      } catch (err) {
        setError((err as Error).message)
      } finally {
        setLoading(false)
      }
    }
    fetchHospitalDetails()
  }, [])

  if (loading) return <p>Loading...</p>
  if (error) return <p>Error: {error}</p>
  if (!hospitalDetails) return <p>병원 데이터를 찾을 수 없습니다.</p>

  const bedInfo = [
    `응급실 성인 병상: 총 ${hospitalDetails.emergencyRoomDefault} 병상 중 ${hospitalDetails.emergencyRoomAvailable} 병상 이용 가능`,
    `응급실 소아 병상: 총 ${hospitalDetails.emergencyRoomChildDefault} 병상 중 ${hospitalDetails.emergencyRoomChildAvailable} 병상 이용 가능`,
    `일반 수술실: ${hospitalDetails.operatingRoom} 병상 이용 가능`,
    `신경외과 수술실: ${hospitalDetails.neurosurgeryRoom} 병상 이용 가능`,
    `신생아 중환자실: ${hospitalDetails.neonatalRoom} 병상 이용 가능`,
    `흉부 외과 중환자실: ${hospitalDetails.thoracicRoom} 병상 이용 가능`,
    `일반 병실: ${hospitalDetails.generalRoom} 병상 이용 가능`,
  ]

  const equipmentInfo = [
    `CT: ${hospitalDetails.ct ? '사용 가능' : '사용 불가'}`,
    `MRI: ${hospitalDetails.mri ? '사용 가능' : '사용 불가'}`,
    `혈관촬영기: ${hospitalDetails.angiography ? '사용 가능' : '사용 불가'}`,
    `인공호흡기: ${hospitalDetails.ventilator ? '사용 가능' : '사용 불가'}`,
    `CRRT: ${hospitalDetails.crrt ? '사용 가능' : '사용 불가'}`,
    `ECMO: ${hospitalDetails.ecmo ? '사용 가능' : '사용 불가'}`,
  ]

  const listItemClass = 'mb-[10px] p-[10px_15px] bg-[#f9f9f9] rounded-[5px] border border-[#ddd] w-full'
  const sectionTitleClass = 'mt-5 mb-5 text-[#007bff] font-bold'

  return (
    <Dialog open onOpenChange={onClose}>
      <DialogContent className="max-w-[800px] max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>병원 상세 정보</DialogTitle>
        </DialogHeader>
        <div>
          <h5 className={sectionTitleClass}>병상 현황</h5>
          <ul className="list-none p-0 m-0 w-full">
            {bedInfo.map((item, index) => (
              <li key={index} className={listItemClass}>{item}</li>
            ))}
          </ul>
          <h5 className={sectionTitleClass}>장비 현황</h5>
          <ul className="list-none p-0 m-0 w-full">
            {equipmentInfo.map((item, index) => (
              <li key={index} className={listItemClass}>{item}</li>
            ))}
          </ul>
        </div>
        <DialogFooter>
          <button
            onClick={onClose}
            className="bg-[#6c757d] text-white px-4 py-2 rounded"
          >
            닫기
          </button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

export default DetailInfoModal
