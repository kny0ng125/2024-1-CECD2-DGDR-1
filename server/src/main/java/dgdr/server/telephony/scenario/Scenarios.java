package dgdr.server.telephony.scenario;

import java.util.List;

/**
 * 재생 가능한 통화 시나리오 목록.
 *
 * <p>전화 없이 전 구간(통화 개설 → 화자별 발화 → 종료)을 재현하기 위한
 * 대본이다. 실제 신고 내용은 사용하지 않으며 전화번호도 창작물 전용
 * 대역(010-0000-XXXX)만 쓴다.
 */
public final class Scenarios {

    private Scenarios() {}

    /**
     * 발화 하나.
     *
     * @param speaker    {@code "agent"} | {@code "caller"}
     * @param text       발화 전체 텍스트 (partial 은 이걸 잘라서 만든다)
     * @param startMs    통화 시작 기준 절대 시각(ms)
     * @param durationMs 이 발화가 이어지는 길이(ms). partial 들이 이 구간에 걸쳐 방출된다.
     *
     * <p>서로 다른 화자의 {@code [startMs, startMs+durationMs)} 구간이 겹치면
     * 두 화자의 chunk 가 동시에 방출된다 — 동시 발화 처리 검증용.
     */
    public record Line(String speaker, String text, long startMs, long durationMs) {}

    public record Scenario(
            String id,
            String agentPhone,
            String callerPhone,
            List<Line> lines
    ) {
        /** 마지막 발화가 끝나는 시각. 재생 종료 판단에 쓴다. */
        public long totalDurationMs() {
            return lines.stream()
                    .mapToLong(l -> l.startMs() + l.durationMs())
                    .max()
                    .orElse(0L);
        }
    }

    /** 순차 진행 — 겹침 없음. 기본 데모용. */
    public static final Scenario EMERGENCY_119 = new Scenario(
            "emergency-119",
            "+821000001111",
            "+821000002222",
            List.of(
                    new Line("agent",  "네, 119 상황실입니다. 어떤 일이시죠?",              0L,     1800L),
                    new Line("caller", "남편이 갑자기 가슴을 부여잡고 쓰러졌어요!",          2000L,  1800L),
                    new Line("agent",  "지금 계신 위치가 어디세요?",                        4000L,  1500L),
                    new Line("caller", "서울시 중구 을지로 1가 한빛아파트 302호요.",         5800L,  2000L),
                    new Line("agent",  "의식은 있으신가요? 숨은 쉬고 계세요?",              8000L,  1800L),
                    new Line("caller", "의식은 없고 숨소리도 거의 안 들려요.",             10000L,  1800L),
                    new Line("agent",  "구급차 바로 출동시켰습니다. 심폐소생술 안내드릴게요.", 12000L, 2000L),
                    new Line("caller", "네 알려주세요 빨리요!",                          14200L,  1200L),
                    new Line("agent",  "먼저 환자를 단단한 바닥에 눕혀주세요.",            15600L,  1800L),
                    new Line("caller", "눕혔어요.",                                    17600L,  1000L),
                    new Line("agent",  "양손을 겹쳐 가슴 중앙을 분당 100회로 눌러주세요.",   19000L,  2200L),
                    new Line("caller", "시작했어요, 계속 할게요.",                       21400L,  1800L)
            )
    );

    /**
     * 동시 발화 — 두 화자의 chunk 가 겹쳐 방출된다.
     *
     * <p>실제 신고 전화에서 흔한 상황이고(신고자는 흥분해서 요원 말을 끊는다),
     * 자막 UI 가 화자별 partial 을 따로 유지하지 못하면 여기서 깨진다.
     */
    public static final Scenario OVERLAP_TEST = new Scenario(
            "overlap-test",
            "+821000001111",
            "+821000002222",
            List.of(
                    // caller 0~3000 / agent 1000~3000  → 1000~3000 겹침
                    new Line("caller", "불이 났어요 집 안에 사람이 있어요",  0L,    3000L),
                    new Line("agent",  "침착하세요 지금 위치가 어디죠",      1000L, 2000L),
                    // caller 2500~5000 / agent 3500~5500 → 3500~5000 겹침
                    new Line("caller", "3층 302호요 연기가 너무 심해요",     2500L, 2500L),
                    new Line("agent",  "네 소방차 바로 출동했습니다",        3500L, 2000L)
            )
    );

    /**
     * 극단적 동시 발화 — 신고자가 요원 말을 계속 끊는 상황.
     *
     * <p>실제 119 신고에서 드물지 않다. 신고자는 흥분 상태라 접수 요원의
     * 질문이 끝나기를 기다리지 않는다. 이 구간에서 자막 UI 가 화자별 partial 을
     * 따로 유지하지 못하면 두 사람 말이 한 버블에 뒤섞이거나 서로를 덮어쓴다.
     *
     * <p>{@code overlap-test} 보다 겹침 구간을 길게 잡아 육안으로 확인되게 했다.
     */
    public static final Scenario OVERLAP_HEAVY = new Scenario(
            "overlap-heavy",
            "+821000001111",
            "+821000002222",
            List.of(
                    // 0~4000 내내 신고자가 말하는 동안 요원이 두 번 끼어든다
                    new Line("caller", "아버지가 갑자기 가슴을 움켜쥐고 쓰러지셨어요 숨을 제대로 못 쉬세요",
                            0L, 4000L),
                    new Line("agent",  "선생님 진정하시고 지금 계신 위치부터 말씀해 주세요",
                            800L, 2600L),
                    new Line("agent",  "의식이 있으신지 확인해 주세요",
                            2600L, 2000L),
                    // 3200~7000 신고자, 4000~6500 요원 → 3300ms 겹침
                    new Line("caller", "여기 강남구 테헤란로인데 지금 불러도 대답을 안 하세요 어떡해요",
                            3200L, 3800L),
                    new Line("agent",  "구급차 출동했습니다 제가 심폐소생술 안내드릴게요",
                            4000L, 2500L),
                    // 마지막은 셋이 아니라 둘이 거의 완전히 겹친다
                    new Line("caller", "빨리요 제발 빨리 와주세요",
                            7000L, 2200L),
                    new Line("agent",  "지금 바로 가슴 중앙을 세게 눌러주세요",
                            7100L, 2400L)
            )
    );

    private static final List<Scenario> ALL =
            List.of(EMERGENCY_119, OVERLAP_TEST, OVERLAP_HEAVY);

    public static List<Scenario> all() {
        return ALL;
    }

    public static Scenario byId(String id) {
        if (id == null || id.isBlank()) return EMERGENCY_119;
        return ALL.stream()
                .filter(s -> s.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown scenarioId: " + id));
    }
}
