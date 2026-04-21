export type ManualApiResponse = Partial<Record<
  `passage${0|1|2|3|4|5}`,
  {
    '병명': string;
    '임상적 특징': string;
    '환자평가 필수항목': string;
    '유사도': number;
  }
>>;

export interface Manual {
  id: number;
  title: string;
  similarity: number;
  clinicalFeatures: string;
  patientAssessment: string;
}
