import { describe, it, expect } from 'vitest';
import { adaptManualResponse } from '@/lib/adapters/manualAdapter';
import { ManualApiResponse } from '@/types/manual';

describe('adaptManualResponse', () => {
  it('정상 케이스: 전체 passage 유사도 내림차순 정렬', () => {
    const raw: ManualApiResponse = {
      passage0: { '병명': '심근경색', '임상적 특징': '흉통', '환자평가 필수항목': '혈압', '유사도': 0.9 },
      passage1: { '병명': '뇌졸중', '임상적 특징': '편측마비', '환자평가 필수항목': '의식', '유사도': 0.7 },
      passage2: { '병명': '저혈당', '임상적 특징': '식은땀', '환자평가 필수항목': '혈당', '유사도': 0.8 },
    };

    const result = adaptManualResponse(raw);

    expect(result).toHaveLength(3);
    expect(result[0].title).toBe('심근경색');
    expect(result[0].similarity).toBe(0.9);
    expect(result[1].title).toBe('저혈당');
    expect(result[1].similarity).toBe(0.8);
    expect(result[2].title).toBe('뇌졸중');
    expect(result[2].similarity).toBe(0.7);
  });

  it('누락된 passage 건너뜀', () => {
    const raw: ManualApiResponse = {
      passage0: { '병명': '심근경색', '임상적 특징': '흉통', '환자평가 필수항목': '혈압', '유사도': 0.9 },
      passage2: { '병명': '저혈당', '임상적 특징': '식은땀', '환자평가 필수항목': '혈당', '유사도': 0.8 },
      // passage1, 3, 4, 5 누락
    };

    const result = adaptManualResponse(raw);

    expect(result).toHaveLength(2);
    expect(result.map(r => r.id)).toEqual([0, 2]);
  });

  it('유사도 동점 시 원래 id 순으로 정렬', () => {
    const raw: ManualApiResponse = {
      passage3: { '병명': '폐렴', '임상적 특징': '기침', '환자평가 필수항목': '산소포화도', '유사도': 0.75 },
      passage1: { '병명': '뇌졸중', '임상적 특징': '편측마비', '환자평가 필수항목': '의식', '유사도': 0.75 },
      passage5: { '병명': '패혈증', '임상적 특징': '고열', '환자평가 필수항목': '체온', '유사도': 0.75 },
    };

    const result = adaptManualResponse(raw);

    expect(result).toHaveLength(3);
    expect(result[0].id).toBe(1);
    expect(result[1].id).toBe(3);
    expect(result[2].id).toBe(5);
  });

  it('유사도가 범위(0~1) 밖이면 clamp 처리', () => {
    const raw: ManualApiResponse = {
      passage0: { '병명': '과호흡', '임상적 특징': '호흡곤란', '환자평가 필수항목': '호흡수', '유사도': 1.5 },
      passage1: { '병명': '저혈압', '임상적 특징': '어지럼', '환자평가 필수항목': '혈압', '유사도': -0.2 },
    };

    const result = adaptManualResponse(raw);

    expect(result.find(r => r.id === 0)?.similarity).toBe(1);
    expect(result.find(r => r.id === 1)?.similarity).toBe(0);
  });

  it('빈 응답은 빈 배열 반환', () => {
    const result = adaptManualResponse({});
    expect(result).toEqual([]);
  });
});
