-- =====================================================================
--  개발용 시드 데이터
--
--  ⚠ 여기에는 실제 119 신고 내용이나 실제 개인정보를 절대 넣지 말 것.
--    모든 값은 가공된 합성 데이터이며, 전화번호는 방송통신위원회가
--    창작물용으로 지정한 010-0000-XXXX 대역만 사용한다.
--
--  로그인 계정: test01 / test02   비밀번호: Test1234!
-- =====================================================================

-- ---------------------------------------------------------------------
-- 요원 계정 (비밀번호는 BCrypt 해시. 평문 'Test1234!')
-- ---------------------------------------------------------------------
INSERT INTO users (user_id, name, password, phone) VALUES
  ('test01', '홍길동', '$2a$10$YnYpf6NlTf90pyv6Xlu7xubmtsg0dQyfXzI/z.owYWE5U7mRTEQIq', '010-0000-0001'),
  ('test02', '김수보', '$2a$10$YnYpf6NlTf90pyv6Xlu7xubmtsg0dQyfXzI/z.owYWE5U7mRTEQIq', '010-0000-0002');

-- ---------------------------------------------------------------------
-- 통화 세션
--   start_time  = 벨이 울리기 시작한 시각
--   answer_time = 요원이 수락한 시각 (미응답이면 NULL)
--   둘의 차이가 접수 지연이다. 시드에도 몇 초씩 차이를 둬서
--   지표 계산이 실제로 동작하는지 볼 수 있게 한다.
-- ---------------------------------------------------------------------
INSERT INTO calls (id, start_time, answer_time, end_time, state, user_id) VALUES
  -- 3초 만에 수락
  (1, DATE_SUB(NOW(), INTERVAL 7200 SECOND), DATE_SUB(NOW(), INTERVAL 7197 SECOND),
      DATE_SUB(NOW(), INTERVAL 115 MINUTE),  'ENDED', 'test01'),
  -- 8초 만에 수락
  (2, DATE_SUB(NOW(), INTERVAL 86400 SECOND), DATE_SUB(NOW(), INTERVAL 86392 SECOND),
      DATE_SUB(NOW(), INTERVAL 1435 MINUTE),  'ENDED', 'test01'),
  -- 2초 만에 수락
  (3, DATE_SUB(NOW(), INTERVAL 259200 SECOND), DATE_SUB(NOW(), INTERVAL 259198 SECOND),
      DATE_SUB(NOW(), INTERVAL 4315 MINUTE),   'ENDED', 'test02'),
  -- 미응답 통화. 수락되지 않아 전사 기록이 없지만 행은 남는다 —
  -- 놓친 신고가 기록에서 사라지면 안 된다.
  (4, DATE_SUB(NOW(), INTERVAL 30 MINUTE), NULL,
      DATE_SUB(NOW(), INTERVAL 29 MINUTE), 'ENDED', 'test01');

-- ---------------------------------------------------------------------
-- 전사 기록 (합성 시나리오)
--   retention_expires_at = 발화 시각 + 3개월
-- ---------------------------------------------------------------------
INSERT INTO call_record
  (call_id, speaker, speaker_phone_number, transcription, time, retention_expires_at)
VALUES
  (1, 'agent',  NULL,            '119 상황실입니다. 어디십니까?',
      DATE_SUB(NOW(), INTERVAL 120 MINUTE), DATE_ADD(DATE_SUB(NOW(), INTERVAL 120 MINUTE), INTERVAL 3 MONTH)),
  (1, 'caller', '010-0000-1001', '여기 사무실인데요, 동료가 갑자기 쓰러졌어요.',
      DATE_SUB(NOW(), INTERVAL 119 MINUTE), DATE_ADD(DATE_SUB(NOW(), INTERVAL 119 MINUTE), INTERVAL 3 MONTH)),
  (1, 'agent',  NULL,            '의식이 있습니까? 말을 걸어보시고 반응이 있는지 알려주세요.',
      DATE_SUB(NOW(), INTERVAL 118 MINUTE), DATE_ADD(DATE_SUB(NOW(), INTERVAL 118 MINUTE), INTERVAL 3 MONTH)),
  (1, 'caller', '010-0000-1001', '불러도 대답이 없어요. 숨은 쉬는 것 같은데 잘 모르겠어요.',
      DATE_SUB(NOW(), INTERVAL 117 MINUTE), DATE_ADD(DATE_SUB(NOW(), INTERVAL 117 MINUTE), INTERVAL 3 MONTH)),
  (1, 'agent',  NULL,            '지금 바로 구급대가 출동합니다. 제가 안내하는 대로 따라 해주세요.',
      DATE_SUB(NOW(), INTERVAL 116 MINUTE), DATE_ADD(DATE_SUB(NOW(), INTERVAL 116 MINUTE), INTERVAL 3 MONTH)),

  (2, 'agent',  NULL,            '119 상황실입니다. 무슨 일이십니까?',
      DATE_SUB(NOW(), INTERVAL 1440 MINUTE), DATE_ADD(DATE_SUB(NOW(), INTERVAL 1440 MINUTE), INTERVAL 3 MONTH)),
  (2, 'caller', '010-0000-1002', '아이가 계단에서 넘어져서 팔을 크게 다쳤어요.',
      DATE_SUB(NOW(), INTERVAL 1439 MINUTE), DATE_ADD(DATE_SUB(NOW(), INTERVAL 1439 MINUTE), INTERVAL 3 MONTH)),
  (2, 'agent',  NULL,            '출혈이 있습니까? 팔이 이상한 방향으로 꺾여 있나요?',
      DATE_SUB(NOW(), INTERVAL 1438 MINUTE), DATE_ADD(DATE_SUB(NOW(), INTERVAL 1438 MINUTE), INTERVAL 3 MONTH)),

  (3, 'agent',  NULL,            '119 상황실입니다.',
      DATE_SUB(NOW(), INTERVAL 4320 MINUTE), DATE_ADD(DATE_SUB(NOW(), INTERVAL 4320 MINUTE), INTERVAL 3 MONTH)),
  (3, 'caller', '010-0000-1003', '아버지가 가슴이 답답하다고 하시면서 식은땀을 흘리세요.',
      DATE_SUB(NOW(), INTERVAL 4319 MINUTE), DATE_ADD(DATE_SUB(NOW(), INTERVAL 4319 MINUTE), INTERVAL 3 MONTH));
