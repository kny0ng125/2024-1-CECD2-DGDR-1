package dgdr.server.telephony.core;

/**
 * 통화 한 건에 참여하는 다리(leg)의 역할.
 *
 * <p>화자 구분(speaker tagging)의 근거이자, 오디오 브리지에서
 * "누구에게 되돌려 보낼지"를 정하는 기준이다.
 *
 * <p>기존 코드는 화자를 {@code "agent"} / {@code "caller"} 문자열로 다뤘고
 * 이 값이 STT 콜백, 캐시, DB 컬럼, 프런트까지 그대로 흘렀다.
 * 저장·전송 포맷은 문자열로 유지하되({@link #wireName()}) 도메인 내부에서는
 * 열거형으로 다뤄 오타로 인한 무성증상 버그를 막는다.
 */
public enum LegRole {

    /** 수보요원 (상황실) */
    AGENT("agent"),

    /** 신고자 */
    CALLER("caller");

    private final String wireName;

    LegRole(String wireName) {
        this.wireName = wireName;
    }

    /** DB·SSE·프런트가 쓰는 문자열 표현. 기존 데이터와의 호환을 위해 고정. */
    public String wireName() {
        return wireName;
    }

    public static LegRole fromWireName(String value) {
        for (LegRole role : values()) {
            if (role.wireName.equalsIgnoreCase(value)) return role;
        }
        throw new IllegalArgumentException("Unknown leg role: " + value);
    }
}
