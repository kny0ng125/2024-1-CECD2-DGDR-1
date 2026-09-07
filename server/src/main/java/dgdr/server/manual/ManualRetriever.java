package dgdr.server.manual;

import java.util.List;

/**
 * 통화 내용으로 응급처치 매뉴얼을 검색하는 백엔드.
 *
 * <p>{@code TranscriptionEngine}·{@code CallControl} 과 같은 자리의 포트다.
 * 통화 소스와 STT 를 갈아 끼울 수 있게 만든 것과 같은 이유로 검색기도 분리한다 —
 * 이 프로젝트에서 검색 방식은 <b>실제로 여러 번 바뀌었고</b>, 비교 실험
 * (KorDPR / BM25 / OpenAI 임베딩)의 결론이 아직 열려 있다.
 *
 * <h2>구현체</h2>
 * <ul>
 *   <li>{@link SageMakerManualRetriever} — 학습한 KorDPR 을 SageMaker 엔드포인트로 서빙</li>
 *   <li>{@link StubManualRetriever} — 외부 의존 없이 도는 로컬 대체. 개발·데모용</li>
 * </ul>
 * 선택은 {@code manual.retriever} 설정으로 한다.
 */
public interface ManualRetriever {

    /** 로그·진단용 이름. 어느 검색기가 답했는지 응답에도 실린다. */
    String name();

    /**
     * 통화 내용을 질의로 삼아 매뉴얼을 검색한다.
     *
     * @param conversationText {@code "상황실: ...\n신고자: ..."} 형식으로 합쳐진 전사본
     * @param topK             돌려줄 최대 개수
     * @return 유사도 내림차순 후보. 결과가 없으면 빈 목록(예외 아님) —
     *         매뉴얼이 안 뜨는 것은 통화를 중단할 이유가 되지 않는다.
     */
    List<ManualCandidate> retrieve(String conversationText, int topK);
}
