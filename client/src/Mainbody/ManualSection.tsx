import { useState } from 'react'
import { useManualStore } from '@/stores/useManualStore'
import { useCallStore } from '@/stores/useCallStore'
import { authFetch } from '@/lib/authFetch'
import type { Manual } from '@/types/manual'

interface CardData {
  id: number
  title: string
  similarity: number
  clinicalFeatures: string
  patientAssessment: string
}

function ManualCard({ manual, isTop, isSelected, onClick }: {
  manual: CardData
  isTop: boolean
  isSelected: boolean
  onClick: (id: number) => void
}) {
  const sim      = manual.similarity
  const barColor = isTop ? '#3b82f6' : sim >= 50 ? '#64748b' : '#475569'

  const ringClass = isSelected
    ? 'ring-[1.5px] ring-inset ring-dispatch-blue'
    : isTop
    ? 'ring-1 ring-inset ring-dispatch-blue-edge'
    : 'ring-1 ring-inset ring-dispatch-line'

  return (
    <button
      onClick={() => onClick(manual.id)}
      className={`text-left cursor-pointer font-ui border-0 rounded-md py-2.5 px-3 text-dispatch-text flex flex-col gap-1.5 transition-colors duration-150 relative min-h-[70px] ${
        isSelected ? 'bg-dispatch-blue-soft' : 'bg-dispatch-card'
      } ${ringClass}`}
    >
      {isTop && (
        <div className="absolute -top-[7px] right-2 font-mono text-[8.5px] font-bold tracking-[1.2px] bg-dispatch-blue text-white py-0.5 px-[5px] rounded-[3px]">
          TOP
        </div>
      )}
      <div className="flex justify-between items-baseline gap-1.5">
        <div className="text-[13px] font-semibold tracking-[-0.2px] overflow-hidden text-ellipsis whitespace-nowrap">
          {manual.title}
        </div>
        <div className={`font-mono text-xs font-semibold shrink-0 ${isSelected || isTop ? 'text-[#93c5fd]' : 'text-dispatch-textDim'}`}>
          {sim}<span className="text-[8px] ml-px">%</span>
        </div>
      </div>
      <div className="h-[3px] bg-dispatch-lineSoft rounded-sm overflow-hidden mt-auto">
        <div
          className="h-full rounded-sm"
          style={{ width: `${sim}%`, background: barColor }}
        />
      </div>
    </button>
  )
}

const ManualSection = () => {
  const { selectedManual, saveManual, selectManual } = useManualStore()
  const { callId } = useCallStore()
  const [cards, setCards] = useState<CardData[]>([])

  const fetchManuals = async () => {
    if (!callId) return
    try {
      const res = await authFetch(`/manual/${callId}`)
      if (!res.ok) throw new Error('Failed to fetch')
      const data = await res.json()
      const processed: CardData[] = Object.values(data).map((d: any, i) => ({
        id: i + 1,
        title: d['병명'] ?? d.title ?? '',
        similarity: Math.round((d['유사도'] ?? d.similarity ?? 0) * 100),
        clinicalFeatures: d['임상적 특징'] ?? d.clinicalFeatures ?? '',
        patientAssessment: d['환자평가 필수항목'] ?? d.patientAssessment ?? '',
      }))
      processed.sort((a, b) => b.similarity - a.similarity)
      setCards(processed)
    } catch (err) {
      console.error('Manual fetch error:', err)
    }
  }

  const handleCardClick = (id: number) => {
    const card = cards.find(c => c.id === id)
    if (!card) return
    if (selectedManual?.id === id) { selectManual(null); return }
    const m: Manual = {
      id: card.id, title: card.title, similarity: card.similarity,
      clinicalFeatures: card.clinicalFeatures, patientAssessment: card.patientAssessment,
    }
    selectManual(m)
    saveManual(m)
  }

  const sortedCards = [...cards].sort((a, b) => b.similarity - a.similarity)
  const topId = sortedCards[0]?.id

  return (
    <div className="bg-dispatch-elev rounded-md ring-1 ring-inset ring-dispatch-line flex flex-col h-full min-h-0">
      {/* Title bar */}
      <div className="pt-4 pb-3.5 px-6 border-b border-dispatch-lineSoft flex items-center gap-4 shrink-0">
        <div className="flex-1 min-w-0">
          <div className="font-mono text-[10px] text-dispatch-textMuted tracking-[1.5px] mb-1">
            {selectedManual ? 'SELECTED MANUAL · 선택된 매뉴얼' : 'ASSISTED DIAGNOSIS · 유사도 분석'}
          </div>
          <div className="text-xl font-semibold tracking-[-0.5px] text-dispatch-text">
            {selectedManual ? selectedManual.title : '통화 내용 기반 유사 매뉴얼 추천'}
          </div>
        </div>
        {selectedManual && (
          <button
            onClick={() => selectManual(null)}
            className="font-ui cursor-pointer border-0 bg-transparent text-dispatch-textDim text-xs py-1.5 px-2.5 rounded"
          >
            × 선택 해제
          </button>
        )}
      </div>

      {/* Detail area */}
      <div className="dispatch-scroll flex-1 min-h-0 overflow-y-auto py-[18px] px-6">
        {!selectedManual ? (
          <div className="h-full min-h-[200px] flex flex-col items-center justify-center gap-3.5 text-dispatch-textMuted bg-dispatch-card ring-1 ring-inset ring-dispatch-lineSoft rounded-md p-8">
            <div className="w-[54px] h-[54px] rounded-[27px] bg-dispatch-elev ring-1 ring-inset ring-dispatch-line flex items-center justify-center font-mono text-xl text-dispatch-textMuted">
              §
            </div>
            <div className="text-[13px] text-dispatch-textDim">아직 불러온 매뉴얼이 없습니다.</div>
            <div className="font-mono text-[10px] text-dispatch-textMuted tracking-[1px]">
              ↓ 아래 매뉴얼 카드를 선택해 주세요
            </div>
          </div>
        ) : (
          <>
            <div className="bg-dispatch-card rounded-md ring-1 ring-inset ring-dispatch-line pt-4 pb-4 px-5 mb-3">
              <div className="font-mono text-[10px] text-dispatch-textMuted tracking-[1.4px] mb-2">
                §1 · 임상적 특징
              </div>
              <div className="text-[13.5px] leading-[1.75] text-[#cbd5e1]">
                {selectedManual.clinicalFeatures}
              </div>
            </div>
            <div className="bg-dispatch-card rounded-md ring-1 ring-inset ring-dispatch-line pt-4 pb-4 px-5">
              <div className="font-mono text-[10px] text-dispatch-textMuted tracking-[1.4px] mb-2.5">
                §2 · 환자평가 필수항목
              </div>
              <div className="text-[13.5px] leading-[1.8] text-[#cbd5e1] whitespace-pre-wrap">
                {selectedManual.patientAssessment}
              </div>
            </div>
          </>
        )}
      </div>

      {/* CTA */}
      <div className="border-t border-dispatch-lineSoft py-3 px-6 flex items-center gap-3 bg-dispatch-card shrink-0">
        <div className="flex-1 font-mono text-[10px] text-dispatch-textMuted tracking-[1px]">
          {selectedManual
            ? `MATCH · ${selectedManual.similarity}% · 매뉴얼 선택됨`
            : 'WAITING · 매뉴얼을 선택하면 상세가 위에 표시됩니다'}
        </div>
        <button
          id="manual-check-button"
          onClick={fetchManuals}
          className="cursor-pointer font-ui border-0 bg-dispatch-blue text-white text-[13px] font-semibold py-2 px-4 rounded flex items-center gap-2"
        >
          매뉴얼 확인하기
          <span className="font-mono text-[10px] opacity-70 bg-white/15 py-px px-[5px] rounded-sm">Ctrl+1</span>
        </button>
      </div>

      {/* Cards rail */}
      {cards.length > 0 && (
        <div className="border-t border-dispatch-line bg-dispatch-bg pt-3 pb-3.5 px-3.5 shrink-0">
          <div className="flex items-baseline gap-2.5 px-0.5 pb-2">
            <div className="font-mono text-[10px] text-dispatch-textMuted tracking-[1.4px]">
              SUGGESTED · 유사 매뉴얼 Top 6
            </div>
            <div className="flex-1 h-px bg-dispatch-lineSoft" />
          </div>
          <div className="grid grid-cols-6 gap-2">
            {sortedCards.slice(0, 6).map(m => (
              <ManualCard key={m.id} manual={m}
                isTop={m.id === topId}
                isSelected={selectedManual?.id === m.id}
                onClick={handleCardClick}
              />
            ))}
          </div>
        </div>
      )}
    </div>
  )
}

export default ManualSection
