package dgdr.server.manual;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 외부 의존 없이 도는 매뉴얼 검색기. 개발·데모용 대체 구현이다.
 *
 * <h2>이것이 무엇이 아닌지</h2>
 * <p><b>검색 모델이 아니다.</b> 미리 정의된 키워드 가중치 합으로 점수를 매기는
 * 규칙 기반 매칭이며, 학습도 임베딩도 없다. 실제 시스템의 검색 품질을
 * 대표하지 않으므로 성능 수치를 이것으로 산출해서는 안 된다.
 *
 * <h2>왜 고정 응답이 아닌가</h2>
 * <p>항상 같은 매뉴얼을 돌려주면 화면은 채워지지만 <b>아무것도 검증되지 않는다</b> —
 * 전사본이 실제로 검색기까지 전달되는지, 유사도 순 정렬이 동작하는지,
 * 통화 내용이 바뀌면 결과가 따라 바뀌는지 전부 확인할 수 없다.
 * 대화에 반응하게 만들면 그 경로들이 데모만으로 검증된다.
 *
 * <p>{@code manual.retriever=stub} 일 때(또는 설정이 없을 때) 활성화된다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "manual.retriever", havingValue = "stub", matchIfMissing = true)
public class StubManualRetriever implements ManualRetriever {

    /**
     * 매뉴얼 하나 + 그 매뉴얼을 지목하는 키워드.
     *
     * @param weight 키워드가 여러 매뉴얼에 걸릴 때의 변별력. 특이한 표현일수록 높다.
     */
    private record Entry(ManualCandidate manual, List<String> keywords, double weight) {}

    /**
     * 신고 접수 빈도가 높은 응급 상황 위주로 추린 소규모 매뉴얼 집합.
     *
     * <p>내용은 일반적인 응급처치 지침을 요약한 것이며, 실제 119 표준 매뉴얼의
     * 전재가 아니다. 개발용 자료다.
     */
    private static final List<Entry> MANUALS = List.of(
            new Entry(new ManualCandidate(
                    "심정지",
                    "의식 없음, 호흡 없음 또는 비정상 호흡(헐떡임), 맥박 촉지 불가.",
                    "반응 확인 → 호흡 확인(10초 이내) → 즉시 가슴압박 시작. "
                            + "압박 위치는 가슴 중앙, 속도 분당 100~120회, 깊이 약 5cm. "
                            + "자동심장충격기(AED) 확보 여부 확인.",
                    0),
                    List.of("쓰러졌", "의식이 없", "의식은 없", "숨을 안", "숨소리도", "호흡이 없",
                            "심장", "가슴을 부여잡", "반응이 없", "심폐소생", "가슴압박"), 1.0),

            new Entry(new ManualCandidate(
                    "화재 및 연기 흡입",
                    "연기·유독가스 흡입에 의한 기침, 호흡곤란, 그을음, 화상 동반 가능.",
                    "현장 이탈 가능 여부 확인 → 대피 경로 안내(낮은 자세, 젖은 수건으로 코와 입) → "
                            + "고립 시 문틈 밀폐 후 위치 고지. 재실자 수와 층수를 반드시 확보.",
                    0),
                    List.of("불이 났", "화재", "연기", "타는 냄새", "불길", "매캐", "대피"), 1.0),

            new Entry(new ManualCandidate(
                    "급성 심근경색",
                    "지속되는 흉통(압박감·조이는 느낌), 식은땀, 왼팔·턱으로 뻗치는 통증, 구역감.",
                    "통증 시작 시각과 지속 시간 확인 → 안정된 자세로 눕히고 움직임 최소화 → "
                            + "복용 중인 심장약 여부 확인. 환자를 걷게 하지 말 것.",
                    0),
                    List.of("가슴이 답답", "흉통", "가슴이 아프", "식은땀", "쥐어짜", "가슴 통증",
                            "숨이 차"), 1.0),

            new Entry(new ManualCandidate(
                    "뇌졸중",
                    "편측 마비, 안면 비대칭, 언어 장애(어눌함), 갑작스러운 시야 이상.",
                    "증상 발생 시각을 정확히 확인(혈전 용해 가능 시간 판단의 핵심) → "
                            + "얼굴·팔·언어 3가지 신속 평가 → 음식·물 제공 금지(흡인 위험).",
                    0),
                    List.of("한쪽", "마비", "말이 어눌", "발음이", "입이 돌아", "팔이 안 올라",
                            "쓰러지면서 말"), 1.0),

            new Entry(new ManualCandidate(
                    "외상 및 골절",
                    "변형, 부종, 압통, 개방창 및 출혈 동반 가능.",
                    "출혈 여부와 양 확인 → 직접 압박 지혈 → 손상 부위 고정, 임의 정복 금지 → "
                            + "손상 기전(높이·속도) 확인.",
                    0),
                    List.of("넘어져", "다쳤", "부러진", "골절", "피가", "출혈", "떨어졌", "찢어졌",
                            "꺾여"), 1.0),

            new Entry(new ManualCandidate(
                    "기도 이물 폐쇄",
                    "갑작스러운 기침, 말을 못 함, 목을 움켜쥐는 동작(만국 공통 질식 신호), 청색증.",
                    "기침 가능 여부 확인 → 기침 가능하면 계속 유도, 불가능하면 복부 밀어내기(하임리히) → "
                            + "의식 소실 시 즉시 심폐소생술 전환.",
                    0),
                    List.of("목에 걸", "삼켰", "질식", "숨을 못 쉬", "켁켁", "떡", "사탕"), 1.0),

            new Entry(new ManualCandidate(
                    "경련 및 발작",
                    "전신 강직-간대 운동, 의식 소실, 안구 편위, 실금 동반 가능.",
                    "발작 지속 시간 측정 → 주변 위험물 제거, 억제하지 말 것 → "
                            + "입에 아무것도 넣지 말 것 → 종료 후 회복 자세.",
                    0),
                    List.of("경련", "발작", "떨고 있", "간질", "몸이 뻣뻣", "거품"), 1.0),

            new Entry(new ManualCandidate(
                    "저혈당",
                    "식은땀, 떨림, 창백, 혼돈, 심하면 의식 저하. 당뇨 병력에서 흔함.",
                    "당뇨 병력·인슐린 투여 여부 확인 → 의식 있으면 당분 섭취 유도 → "
                            + "의식 저하 시 경구 투여 금지.",
                    0),
                    List.of("당뇨", "인슐린", "혈당", "저혈당", "축 처", "횡설수설"), 1.0)
    );

    @Override
    public String name() {
        return "stub-keyword";
    }

    @Override
    public List<ManualCandidate> retrieve(String conversationText, int topK) {
        if (conversationText == null || conversationText.isBlank()) return List.of();

        String haystack = conversationText.toLowerCase(Locale.KOREAN);

        List<ManualCandidate> scored = new ArrayList<>();
        for (Entry entry : MANUALS) {
            double hits = entry.keywords().stream()
                    .filter(k -> haystack.contains(k.toLowerCase(Locale.KOREAN)))
                    .count();
            if (hits == 0) continue;

            // 매칭된 키워드 비율을 0~1 로 정규화한다. 실제 유사도가 아니라
            // 순위를 만들기 위한 값이라는 점을 이름과 문서로 분명히 해 둔다.
            double score = Math.min(1.0, (hits * entry.weight()) / Math.max(1, entry.keywords().size() * 0.4));
            scored.add(new ManualCandidate(
                    entry.manual().disease(),
                    entry.manual().clinicalFeatures(),
                    entry.manual().patientAssessment(),
                    Math.round(score * 1000) / 1000.0));
        }

        List<ManualCandidate> result = scored.stream()
                .sorted(Comparator.comparingDouble(ManualCandidate::similarity).reversed())
                .limit(topK)
                .toList();

        log.debug("[manual/stub] {} candidate(s) for {} chars", result.size(), conversationText.length());
        return result;
    }
}
