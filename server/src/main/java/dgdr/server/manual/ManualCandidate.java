package dgdr.server.manual;

/**
 * 검색된 응급처치 매뉴얼 후보 하나.
 *
 * <p>도메인 타입으로 따로 두는 이유: 예전 구현은 SageMaker 응답 JSON 의
 * {@code passage0..5} 구조를 그대로 {@code Map<String, Object>} 로 들고 다니다
 * 컨트롤러까지 흘려보냈다. 그러면 추론 백엔드를 바꾸는 순간 프런트 계약이
 * 함께 깨지고, 응답 키가 몇 개인지 같은 세부사항이 서비스 계층에 스며든다.
 *
 * @param disease           병명 (매뉴얼 제목)
 * @param clinicalFeatures  임상적 특징
 * @param patientAssessment 환자평가 필수항목
 * @param similarity        질의와의 유사도. 스케일은 리트리버마다 다르므로
 *                          <b>절대값이 아니라 순위로만</b> 해석해야 한다.
 *                          (KorDPR 내적 ~114, BM25 ~5.7, 코사인 0~1)
 */
public record ManualCandidate(
        String disease,
        String clinicalFeatures,
        String patientAssessment,
        double similarity
) {
    public ManualCandidate {
        if (disease == null || disease.isBlank()) {
            throw new IllegalArgumentException("disease is required");
        }
        if (clinicalFeatures == null) clinicalFeatures = "";
        if (patientAssessment == null) patientAssessment = "";
    }
}
