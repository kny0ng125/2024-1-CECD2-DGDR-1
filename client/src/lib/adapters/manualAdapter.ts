import { Manual, ManualApiResponse } from '@/types/manual';

export function adaptManualResponse(raw: ManualApiResponse): Manual[] {
  const entries = Object.entries(raw).filter(([, v]) => v !== undefined);

  return entries
    .map(([key, v]) => ({
      id: parseInt(key.replace('passage', ''), 10),
      title: v!['병명'],
      similarity: Math.max(0, Math.min(1, v!['유사도'])),
      clinicalFeatures: v!['임상적 특징'],
      patientAssessment: v!['환자평가 필수항목'],
    }))
    .sort((a, b) => {
      if (b.similarity !== a.similarity) return b.similarity - a.similarity;
      return a.id - b.id;
    });
}
