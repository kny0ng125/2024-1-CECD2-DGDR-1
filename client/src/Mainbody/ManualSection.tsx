import { useState } from 'react'
import { useManualStore } from '@/stores/useManualStore'
import { useCallStore } from '@/stores/useCallStore'
import { authFetch } from '@/lib/authFetch'
import type { Manual } from '@/types/manual'
import { T } from '@/lib/theme'

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
  const barColor = isTop ? T.accentBlue : sim >= 50 ? '#64748b' : '#475569'

  return (
    <button
      onClick={() => onClick(manual.id)}
      style={{
        textAlign: 'left', cursor: 'pointer', fontFamily: T.ui,
        background: isSelected ? T.accentBlueSoft : T.bgCard,
        border: 'none',
        boxShadow: isSelected
          ? `inset 0 0 0 1.5px ${T.accentBlue}`
          : isTop
          ? `inset 0 0 0 1px ${T.accentBlueEdge}`
          : `inset 0 0 0 1px ${T.line}`,
        borderRadius: 6,
        padding: '10px 12px',
        color: T.text,
        display: 'flex', flexDirection: 'column', gap: 6,
        transition: 'background .15s',
        position: 'relative',
        minHeight: 70,
      }}>
      {isTop && (
        <div style={{
          position: 'absolute', top: -7, right: 8,
          fontFamily: T.mono, fontSize: 8.5, fontWeight: 700, letterSpacing: 1.2,
          background: T.accentBlue, color: '#fff',
          padding: '2px 5px', borderRadius: 3,
        }}>TOP</div>
      )}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', gap: 6 }}>
        <div style={{
          fontSize: 13, fontWeight: 600, letterSpacing: -0.2,
          overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
        }}>{manual.title}</div>
        <div style={{
          fontFamily: T.mono, fontSize: 12, fontWeight: 600,
          color: isSelected || isTop ? '#93c5fd' : T.textDim, flexShrink: 0,
        }}>
          {sim}<span style={{ fontSize: 8, marginLeft: 1 }}>%</span>
        </div>
      </div>
      <div style={{ height: 3, background: T.lineSoft, borderRadius: 2, overflow: 'hidden', marginTop: 'auto' }}>
        <div style={{ width: `${sim}%`, height: '100%', background: barColor, borderRadius: 2 }} />
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
    <div style={{
      background: T.bgElev, borderRadius: 6,
      boxShadow: `inset 0 0 0 1px ${T.line}`,
      display: 'flex', flexDirection: 'column',
      height: '100%', minHeight: 0,
    }}>
      {/* Title bar */}
      <div style={{
        padding: '16px 24px 14px',
        borderBottom: `1px solid ${T.lineSoft}`,
        display: 'flex', alignItems: 'center', gap: 16,
        flexShrink: 0,
      }}>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ fontFamily: T.mono, fontSize: 10, color: T.textMuted, letterSpacing: 1.5, marginBottom: 4 }}>
            {selectedManual ? 'SELECTED MANUAL · 선택된 매뉴얼' : 'ASSISTED DIAGNOSIS · 유사도 분석'}
          </div>
          <div style={{ fontSize: 20, fontWeight: 600, letterSpacing: -0.5, color: T.text }}>
            {selectedManual ? selectedManual.title : '통화 내용 기반 유사 매뉴얼 추천'}
          </div>
        </div>
        {selectedManual && (
          <button onClick={() => selectManual(null)}
            style={{
              fontFamily: T.ui, cursor: 'pointer', border: 'none',
              background: 'transparent', color: T.textDim, fontSize: 12,
              padding: '6px 10px', borderRadius: 4,
            }}>
            × 선택 해제
          </button>
        )}
      </div>

      {/* Detail area */}
      <div className="dispatch-scroll" style={{ flex: 1, minHeight: 0, overflowY: 'auto', padding: '18px 24px' }}>
        {!selectedManual ? (
          <div style={{
            height: '100%', minHeight: 200,
            display: 'flex', flexDirection: 'column',
            alignItems: 'center', justifyContent: 'center',
            gap: 14, color: T.textMuted,
            background: T.bgCard,
            boxShadow: `inset 0 0 0 1px ${T.lineSoft}`,
            borderRadius: 6, padding: 32,
          }}>
            <div style={{
              width: 54, height: 54, borderRadius: 27,
              background: T.bgElev,
              boxShadow: `inset 0 0 0 1px ${T.line}`,
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              fontFamily: T.mono, fontSize: 20, color: T.textMuted,
            }}>§</div>
            <div style={{ fontSize: 13, color: T.textDim }}>아직 불러온 매뉴얼이 없습니다.</div>
            <div style={{ fontFamily: T.mono, fontSize: 10, color: T.textMuted, letterSpacing: 1 }}>
              ↓ 아래 매뉴얼 카드를 선택해 주세요
            </div>
          </div>
        ) : (
          <>
            <div style={{
              background: T.bgCard, borderRadius: 6,
              boxShadow: `inset 0 0 0 1px ${T.line}`,
              padding: '16px 20px', marginBottom: 12,
            }}>
              <div style={{ fontFamily: T.mono, fontSize: 10, color: T.textMuted, letterSpacing: 1.4, marginBottom: 8 }}>
                §1 · 임상적 특징
              </div>
              <div style={{ fontSize: 13.5, lineHeight: 1.75, color: '#cbd5e1' }}>
                {selectedManual.clinicalFeatures}
              </div>
            </div>
            <div style={{
              background: T.bgCard, borderRadius: 6,
              boxShadow: `inset 0 0 0 1px ${T.line}`,
              padding: '16px 20px',
            }}>
              <div style={{ fontFamily: T.mono, fontSize: 10, color: T.textMuted, letterSpacing: 1.4, marginBottom: 10 }}>
                §2 · 환자평가 필수항목
              </div>
              <div style={{ fontSize: 13.5, lineHeight: 1.8, color: '#cbd5e1', whiteSpace: 'pre-wrap' }}>
                {selectedManual.patientAssessment}
              </div>
            </div>
          </>
        )}
      </div>

      {/* CTA */}
      <div style={{
        borderTop: `1px solid ${T.lineSoft}`,
        padding: '12px 24px',
        display: 'flex', alignItems: 'center', gap: 12,
        background: T.bgCard,
        flexShrink: 0,
      }}>
        <div style={{ flex: 1, fontFamily: T.mono, fontSize: 10, color: T.textMuted, letterSpacing: 1 }}>
          {selectedManual
            ? `MATCH · ${selectedManual.similarity}% · 매뉴얼 선택됨`
            : 'WAITING · 매뉴얼을 선택하면 상세가 위에 표시됩니다'}
        </div>
        <button
          id="manual-check-button"
          onClick={fetchManuals}
          style={{
            cursor: 'pointer', fontFamily: T.ui, border: 'none',
            background: T.accentBlue, color: '#fff',
            fontSize: 13, fontWeight: 600,
            padding: '8px 16px', borderRadius: 4,
            display: 'flex', alignItems: 'center', gap: 8,
          }}>
          매뉴얼 확인하기
          <span style={{
            fontFamily: T.mono, fontSize: 10, opacity: 0.7,
            background: 'rgba(255,255,255,0.15)', padding: '1px 5px', borderRadius: 2,
          }}>Ctrl+1</span>
        </button>
      </div>

      {/* Cards rail */}
      {cards.length > 0 && (
        <div style={{
          borderTop: `1px solid ${T.line}`,
          background: T.bg,
          padding: '12px 14px 14px',
          flexShrink: 0,
        }}>
          <div style={{ display: 'flex', alignItems: 'baseline', gap: 10, padding: '0 2px 8px' }}>
            <div style={{ fontFamily: T.mono, fontSize: 10, color: T.textMuted, letterSpacing: 1.4 }}>
              SUGGESTED · 유사 매뉴얼 Top 6
            </div>
            <div style={{ flex: 1, height: 1, background: T.lineSoft }} />
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(6, 1fr)', gap: 8 }}>
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
