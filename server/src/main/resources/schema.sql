-- =====================================================================
--  119 수보 지원 시스템 스키마 (MySQL 8.0)
--
--  주의: 이 파일은 spring.sql-init 에 의해 서버 기동 시마다 실행된다.
--        DROP 이 포함되어 있으므로 로컬 개발 프로파일에서만 사용할 것.
--        (application.yml 의 sql.init.mode 를 never 로 두면 실행되지 않는다)
--
--  개인정보 관련 설계 근거
--   - call_record 는 119 신고 통화의 전사(轉寫)본으로, 신고자의 건강 상태가
--     포함되어 개인정보 보호법 제23조의 '민감정보'에 해당한다.
--   - 보존기간은 신고 접수 녹취에 준하여 3개월로 두고, retention_expires_at
--     경과분은 파기 배치(RetentionPolicyScheduler)가 삭제한다.
--     ※ 구급활동일지(119구조·구급에 관한 법률 시행규칙, 3년 보관)는
--       본 시스템의 관리 대상이 아니며 별도 체계에서 관리된다.
--   - access_log 는 개인정보 보호법 제29조 및 '개인정보의 안전성 확보조치
--     기준'이 요구하는 접속기록으로, 민감정보를 취급하므로 2년간 보관한다.
-- =====================================================================

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS access_log;
DROP TABLE IF EXISTS call_record;
DROP TABLE IF EXISTS calls;
DROP TABLE IF EXISTS users;
SET FOREIGN_KEY_CHECKS = 1;

-- ---------------------------------------------------------------------
-- 상황실 요원 계정
-- ---------------------------------------------------------------------
CREATE TABLE users (
    user_id   VARCHAR(50)  NOT NULL COMMENT '로그인 아이디(자연키)',
    name      VARCHAR(50)  NOT NULL COMMENT '요원 성명',
    -- 개인정보의 안전성 확보조치 기준: 비밀번호는 복호화되지 않도록
    -- 일방향 암호화하여 저장해야 한다. BCrypt 해시는 60자 고정.
    password  VARCHAR(60)  NOT NULL COMMENT 'BCrypt 해시',
    phone     VARCHAR(20)  NULL     COMMENT '연락처',
    PRIMARY KEY (user_id),
    UNIQUE KEY uk_users_phone (phone)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '상황실 요원 계정';

-- ---------------------------------------------------------------------
-- 통화 세션
--   테이블명 주의: CALL 은 MySQL 예약어이므로 calls 를 사용한다.
--   이 테이블 자체에는 신고 내용이 없고 담당 요원과 시각만 보관하므로
--   전사본과 별도의 수명을 가진다.
-- ---------------------------------------------------------------------
CREATE TABLE calls (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    start_time  DATETIME    NOT NULL COMMENT '통화 도달 시각(벨 울리기 시작)',
    -- start_time 과 answer_time 을 나눠 두는 이유: 둘의 차이가 접수 지연이며
    -- 상황실 운영의 핵심 지표다. 하나로 합치면 산출 자체가 불가능해진다.
    -- 미응답으로 끝난 통화는 이 값이 NULL 로 남아 그 자체로 구분된다.
    answer_time DATETIME    NULL     COMMENT '요원 수락 시각(미응답이면 NULL)',
    end_time    DATETIME    NULL     COMMENT '통화 종료 시각(진행 중이면 NULL)',
    state       VARCHAR(20) NOT NULL DEFAULT 'OFFERED'
                            COMMENT 'OFFERED | ANSWERED | ENDED',
    user_id     VARCHAR(50) NULL     COMMENT '수보 담당 요원',
    PRIMARY KEY (id),
    KEY idx_calls_user_start (user_id, start_time),
    KEY idx_calls_start (start_time),
    KEY idx_calls_state (state),
    CONSTRAINT fk_calls_user
        FOREIGN KEY (user_id) REFERENCES users (user_id)
        ON DELETE SET NULL
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '통화 세션';

-- ---------------------------------------------------------------------
-- 통화 전사 기록 (민감정보)
-- ---------------------------------------------------------------------
CREATE TABLE call_record (
    id                   BIGINT      NOT NULL AUTO_INCREMENT,
    call_id              BIGINT      NOT NULL,
    speaker              VARCHAR(20) NOT NULL COMMENT 'agent | caller',
    speaker_phone_number VARCHAR(20) NULL     COMMENT '발화자 전화번호(개인정보)',
    -- STT 결과는 한 발화가 수백 자를 넘을 수 있어 VARCHAR(255) 로는 잘린다.
    -- 전사본 자체는 법령상 암호화 의무 대상(비밀번호·고유식별정보·바이오정보)이
    -- 아니므로 평문으로 두되, 운영 반영 시 컬럼 암호화를 재검토할 것.
    transcription        TEXT        NULL     COMMENT '전사 내용(민감정보)',
    time                 DATETIME    NOT NULL COMMENT '발화 시각',
    retention_expires_at DATETIME    NOT NULL COMMENT '보존기간 만료 시각(생성 +3개월)',
    PRIMARY KEY (id),
    KEY idx_call_record_call (call_id),
    KEY idx_call_record_retention (retention_expires_at),
    CONSTRAINT fk_call_record_call
        FOREIGN KEY (call_id) REFERENCES calls (id)
        ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '통화 전사 기록(민감정보, 보존 3개월)';

-- ---------------------------------------------------------------------
-- 접속기록
--   개인정보 보호법 제29조 / 개인정보의 안전성 확보조치 기준.
--   민감정보를 처리하므로 2년 이상 보관하고, 위·변조를 막기 위해
--   애플리케이션에서 UPDATE/DELETE 하지 않는다(파기 배치 제외).
-- ---------------------------------------------------------------------
CREATE TABLE access_log (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    actor_user_id VARCHAR(50)  NULL     COMMENT '수행자 계정(탈퇴 시에도 기록 유지)',
    action        VARCHAR(40)  NOT NULL COMMENT 'VIEW_CALL_LIST | VIEW_CALL_RECORD | ...',
    target_type   VARCHAR(40)  NULL     COMMENT '대상 자원 유형',
    target_id     VARCHAR(64)  NULL     COMMENT '대상 자원 식별자',
    ip_address    VARCHAR(45)  NULL     COMMENT '요청 IP (IPv6 고려 45자)',
    accessed_at   DATETIME     NOT NULL COMMENT '수행 시각',
    PRIMARY KEY (id),
    KEY idx_access_log_actor (actor_user_id, accessed_at),
    KEY idx_access_log_time (accessed_at),
    KEY idx_access_log_target (target_type, target_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '개인정보 접속기록(보관 2년)';
