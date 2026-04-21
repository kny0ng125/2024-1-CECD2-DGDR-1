import { useState, useEffect } from 'react'
import { useManualStore } from '@/stores/useManualStore'

interface Protocol {
  id: number
  text: string
  completed: boolean
}

const Sidebar = () => {
  const { savedManuals, selectManual } = useManualStore()
  const [protocols, setProtocols] = useState<Protocol[]>([
    { id: 1, text: '환자 상태 확인', completed: false },
    { id: 2, text: '현장 조건 파악', completed: false },
    { id: 3, text: '초동조치 지도', completed: false },
    { id: 4, text: '출동 확인 완료', completed: false },
  ])

  const toggleProtocol = (id: number) => {
    setProtocols((prev) =>
      prev.map((p) => (p.id === id ? { ...p, completed: !p.completed } : p))
    )
  }

  useEffect(() => {
    const handleKeydown = (e: KeyboardEvent) => {
      if (e.altKey) {
        const num = parseInt(e.key)
        if (num >= 1 && num <= protocols.length) {
          toggleProtocol(num)
        }
      }
    }
    window.addEventListener('keydown', handleKeydown)
    return () => window.removeEventListener('keydown', handleKeydown)
  }, [protocols])

  return (
    <div className="bg-[#f0f0f0] p-5 m-5 rounded-[10px] h-[92vh] box-border flex flex-col justify-between gap-5 shadow-[0_4px_10px_rgba(0,0,0,0.1)]">
      {/* 열람 매뉴얼 목록 */}
      <div className="flex-[5] bg-white p-5 rounded-[10px] mb-5 overflow-y-auto shadow-[inset_0_2px_4px_rgba(0,0,0,0.05)]">
        <h3 className="font-bold mb-[30px]">열람 매뉴얼 목록</h3>
        {savedManuals.map((manual, index) => (
          <div
            key={index}
            onClick={() => selectManual(manual)}
            className="text-[1.3em] bg-[#f0f8ff] text-[#007bff] p-[10px] rounded-[10px] mb-[10px] text-center cursor-pointer"
          >
            {manual.title}
          </div>
        ))}
      </div>

      {/* 수보 프로토콜 목록 */}
      <div className="flex-[4] bg-white p-5 rounded-[10px] overflow-y-auto shadow-[inset_0_2px_4px_rgba(0,0,0,0.05)]">
        <h3 className="font-bold mb-[30px]">수보 프로토콜 목록</h3>
        {protocols.map((protocol) => (
          <div
            key={protocol.id}
            onClick={() => toggleProtocol(protocol.id)}
            className={`text-[1.3em] p-[15px] mb-5 rounded-[5px] cursor-pointer shadow-[0_2px_5px_rgba(0,0,0,0.1)] ${
              protocol.completed
                ? 'bg-[#d4edda] text-[#155724] border border-[#c3e6cb]'
                : 'bg-[#f8d7da] text-[#721c24] border border-[#f5c6cb]'
            }`}
          >
            {protocol.text}
          </div>
        ))}
      </div>
    </div>
  )
}

export default Sidebar
