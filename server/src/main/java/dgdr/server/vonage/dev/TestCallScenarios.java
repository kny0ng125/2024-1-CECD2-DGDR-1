package dgdr.server.vonage.dev;

import java.util.List;

public final class TestCallScenarios {
    private TestCallScenarios() {}

    public record Line(String speaker, String text, long preDelayMs) {}

    public record Scenario(
            String id,
            String agentPhone,
            String callerPhone,
            long partialDelayMs,
            List<Line> lines
    ) {}

    public static final Scenario EMERGENCY_119 = new Scenario(
            "emergency-119",
            "+821012345678",
            "+821099990000",
            350L,
            List.of(
                    new Line("agent",  "네, 119 상황실입니다. 어떤 일이시죠?", 0L),
                    new Line("caller", "남편이 갑자기 가슴을 부여잡고 쓰러졌어요!", 1500L),
                    new Line("agent",  "지금 계신 위치가 어디세요?", 1500L),
                    new Line("caller", "서울시 중구 을지로 1가 한빛아파트 302호요.", 1800L),
                    new Line("agent",  "의식은 있으신가요? 숨은 쉬고 계세요?", 1500L),
                    new Line("caller", "의식은 없고 숨소리도 거의 안 들려요.", 1700L),
                    new Line("agent",  "구급차 바로 출동시켰습니다. 심폐소생술 안내드릴게요.", 1500L),
                    new Line("caller", "네 알려주세요 빨리요!", 1200L),
                    new Line("agent",  "먼저 환자를 단단한 바닥에 눕혀주세요.", 1500L),
                    new Line("caller", "눕혔어요.", 1500L),
                    new Line("agent",  "양손을 겹쳐 가슴 중앙을 분당 100회 속도로 눌러주세요.", 1600L),
                    new Line("caller", "시작했어요, 계속 할게요.", 1800L)
            )
    );

    public static Scenario byId(String id) {
        if (id == null || "emergency-119".equals(id)) return EMERGENCY_119;
        throw new IllegalArgumentException("Unknown scenarioId: " + id);
    }
}
