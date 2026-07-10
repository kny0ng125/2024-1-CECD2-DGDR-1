package dgdr.server.vonage.dev;

import java.util.List;

public final class FakeCallScenarios {
    private FakeCallScenarios() {}

    /**
     * 한 발화(utterance).
     * @param speaker    "agent" | "caller"
     * @param text       발화 전체 텍스트 (partial은 이걸 잘라서 생성)
     * @param startMs    통화 시작 기준 절대 시작 시각(ms)
     * @param durationMs 이 발화가 이어지는 길이(ms) — partial들이 이 구간에 걸쳐 방출됨
     *
     * startMs 구간이 서로 겹치면 → 두 화자 chunk가 동시에 방출됨(동시성 테스트).
     */
    public record Line(String speaker, String text, long startMs, long durationMs) {}

    public record Scenario(
            String id,
            String agentPhone,
            String callerPhone,
            List<Line> lines
    ) {}

    // ── 순차 시나리오 (겹침 없음) ─────────────────────────────
    public static final Scenario EMERGENCY_119 = new Scenario(
            "emergency-119",
            "+821012345678",
            "+821099990000",
            List.of(
                    new Line("agent",  "네, 119 상황실입니다. 어떤 일이시죠?",            0L,     1800L),
                    new Line("caller", "남편이 갑자기 가슴을 부여잡고 쓰러졌어요!",        2000L,  1800L),
                    new Line("agent",  "지금 계신 위치가 어디세요?",                      4000L,  1500L),
                    new Line("caller", "서울시 중구 을지로 1가 한빛아파트 302호요.",       5800L,  2000L),
                    new Line("agent",  "의식은 있으신가요? 숨은 쉬고 계세요?",            8000L,  1800L),
                    new Line("caller", "의식은 없고 숨소리도 거의 안 들려요.",           10000L,  1800L),
                    new Line("agent",  "구급차 바로 출동시켰습니다. 심폐소생술 안내드릴게요.", 12000L, 2000L),
                    new Line("caller", "네 알려주세요 빨리요!",                        14200L,  1200L),
                    new Line("agent",  "먼저 환자를 단단한 바닥에 눕혀주세요.",          15600L,  1800L),
                    new Line("caller", "눕혔어요.",                                  17600L,  1000L),
                    new Line("agent",  "양손을 겹쳐 가슴 중앙을 분당 100회로 눌러주세요.", 19000L, 2200L),
                    new Line("caller", "시작했어요, 계속 할게요.",                     21400L,  1800L)
            )
    );

    // ── 동시 발화 시나리오 (겹침) — 두 화자 chunk 동시 방출 테스트 ──
    public static final Scenario OVERLAP_TEST = new Scenario(
            "overlap-test",
            "+821012345678",
            "+821099990000",
            List.of(
                    // caller 0~3000 / agent 1000~3000  → 1000~3000 구간 겹침
                    new Line("caller", "불이 났어요 집 안에 사람이 있어요",  0L,    3000L),
                    new Line("agent",  "침착하세요 지금 위치가 어디죠",      1000L, 2000L),
                    // caller 2500~5000 / agent 3500~5500 → 3500~5000 구간 겹침
                    new Line("caller", "3층 302호요 연기가 너무 심해요",     2500L, 2500L),
                    new Line("agent",  "네 소방차 바로 출동했습니다",        3500L, 2000L)
            )
    );

    public static Scenario byId(String id) {
        if (id == null || "emergency-119".equals(id)) return EMERGENCY_119;
        if ("overlap-test".equals(id)) return OVERLAP_TEST;
        throw new IllegalArgumentException("Unknown scenarioId: " + id);
    }
}