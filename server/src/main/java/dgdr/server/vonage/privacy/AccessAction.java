package dgdr.server.vonage.privacy;

/**
 * 접속기록에 남길 행위 유형.
 *
 * <p>「개인정보의 안전성 확보조치 기준」은 접속기록에 처리한 정보주체 정보와
 * 수행업무를 남기도록 한다. 개인정보를 실제로 열람·변경하는 경로만 기록하며,
 * 개인정보가 포함되지 않는 조회(예: 병원 병상 현황)는 대상이 아니다.
 */
public enum AccessAction {

    /** 통화 목록 조회. 날짜 범위로 본인 담당 통화를 나열. */
    VIEW_CALL_LIST,

    /** 특정 통화의 전사 기록 열람. 민감정보 접근. */
    VIEW_CALL_RECORD,

    /** 통화 전사 실시간 구독(SSE). */
    STREAM_CALL_RECORD,

    /** 보존기간 만료에 따른 파기. */
    PURGE_EXPIRED,

    /** 로그인 성공. */
    LOGIN_SUCCESS,

    /** 로그인 실패. 계정 도용 시도 탐지에 쓰인다. */
    LOGIN_FAILURE
}
