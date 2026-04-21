import { useState, useEffect } from 'react'
import { useManualStore } from '@/stores/useManualStore'
import { useCallStore } from '@/stores/useCallStore'
import { useHotkey } from '@/hooks/useHotkey'
import { authFetch } from '@/lib/authFetch'
import type { Manual } from '@/types/manual'

interface CardItem {
  id: number
  passage: string
  clinicalFeatures: string
  patientAssessment: string
  similarity: number
}

const ManualSection = () => {
  const { selectedManual, saveManual, selectManual } = useManualStore()
  const { callId } = useCallStore()
  const [cards, setCards] = useState<CardItem[]>([])
  const [selectedCardId, setSelectedCardId] = useState<number | null>(null)

  useEffect(() => {
    if (selectedManual) setSelectedCardId(null)
  }, [selectedManual])

  const fetchAndProcessData = async () => {
    if (!callId) return
    try {
      const response = await authFetch(`/manual/${callId}`)
      if (!response.ok) throw new Error('Failed to fetch')
      const data = await response.json()
      // 유사도 내림차순 정렬 (02 PR에서 어댑터 정식 구현)
      const processed: CardItem[] = Object.values(data).map((passageData: any, index) => ({
        id: index + 1,
        passage: passageData['병명'],
        clinicalFeatures: passageData['임상적 특징'],
        patientAssessment: passageData['환자평가 필수항목'],
        similarity: passageData['유사도'] ?? 0,
      }))
      setCards(processed)
    } catch (err) {
      console.error('Fetch error:', err)
    }
  }

  const handleCardClick = (card: CardItem) => {
    const selected: Manual = {
      id: card.id,
      title: card.passage,
      similarity: card.similarity,
      clinicalFeatures: card.clinicalFeatures,
      patientAssessment: card.patientAssessment,
    }
    selectManual(selected)
    setSelectedCardId(card.id)
    saveManual(selected)
  }

  const formatWithLineBreaks = (text: string) =>
    text.split('\n').map((line, i) => (
      <span key={i}>
        {line}
        <br />
      </span>
    ))

  useHotkey('ctrl+alt+m', fetchAndProcessData)

  return (
    <div className="bg-[#f7f7f7] p-5 rounded-[10px] h-[92vh] box-border flex flex-col justify-between gap-5 shadow-[0_4px_10px_rgba(0,0,0,0.1)] mx-5">
      {/* 헤더 */}
      <div className="flex justify-between items-center mt-[15px] mb-[15px]">
        <h2 className="text-[1.3em] text-center text-[#333] font-[1000] w-full">
          {selectedManual ? selectedManual.title : '아직 불러온 매뉴얼이 없습니다.'}
        </h2>
      </div>

      {/* 매뉴얼 본문 */}
      {selectedManual ? (
        <div className="flex-1 max-h-[1000px] overflow-y-auto p-5 bg-white rounded-[10px] shadow-[inset_0_2px_4px_rgba(0,0,0,0.05)]">
          <h4 className="mt-[30px] mb-[30px] font-bold text-[1.7em]">임상적 특징</h4>
          <p className="text-[1.3em] leading-[1.8] mt-[10px] text-[#333]">
            {formatWithLineBreaks(selectedManual.clinicalFeatures)}
          </p>
          <div className="mt-[80px]" />
          <h4 className="mt-[30px] mb-[30px] font-bold text-[1.7em]">환자평가 필수항목</h4>
          <p className="text-[1.3em] leading-[1.8] mt-[10px] text-[#333]">
            {formatWithLineBreaks(selectedManual.patientAssessment)}
          </p>
        </div>
      ) : (
        <div className="flex-1 p-5 bg-[#d1ecf1] text-[#0c5460] border border-[#bee5eb] rounded-[10px]">
          아직 불러온 매뉴얼이 없습니다.
        </div>
      )}

      {/* 버튼 */}
      <div className="flex justify-start gap-[10px] mt-5">
        <button
          id="manual-check-button"
          onClick={fetchAndProcessData}
          className="text-[1em] px-5 py-[10px] rounded-[5px] shadow-[0_2px_5px_rgba(0,0,0,0.1)] border-none cursor-pointer bg-[#007bff] text-white hover:opacity-90 transition-opacity"
        >
          매뉴얼 확인하기
        </button>
      </div>

      {/* 카드 목록 */}
      <div className="flex gap-5 flex-wrap justify-between">
        {cards.map((card) => (
          <div
            key={card.id}
            onClick={() => handleCardClick(card)}
            className={`p-[15px] rounded-[10px] w-[350px] h-[150px] flex flex-col justify-center items-center cursor-pointer transition-all text-center hover:scale-105 ${
              card.id === selectedCardId ? 'bg-[#b0c4ff]' : 'bg-[#e0e0ff]'
            }`}
          >
            <span className="text-[#000069] font-bold text-[1.7em]">{card.passage}</span>
          </div>
        ))}
      </div>
    </div>
  )
}

export default ManualSection
