# 작업 로그 — 통화 파이프라인 리팩터링

> 목적: 포트폴리오용으로 아키텍처를 정리하고, 통화 소스를 교체 가능하게 만드는 것.
> 이 문서는 세션 간 인수인계용이다.

## 밀기로 한 4가지 (포폴 축)

1. **통화기록 보존/파기 설계** — 법령 근거를 코드에 명시한 보존기간·파기 배치·접속기록
2. **SSE·gRPC 기반 실시간 채널** — control/data 채널 분리, callId 기반 동시 발화 처리
3. **추상화를 통한 설계** — 통화 소스·STT·호 제어를 각각 포트로 분리
4. **골든타임 UI/UX** — 핫키, 프로토콜 가시화, 발화 시작/확정 상태 표시

---

## 완료 (2026-09-06 ~ 09-07)

### 구조
- 루트 패키지 `dgdr.server.vonage` → `dgdr.server` (provider명이 코어 경로에 섞이지 않게)
- `telephony/core` 신설 — 통화 소스에 무지한 도메인 코어
  - `CallOrchestrator` / `DefaultCallOrchestrator` — 유일한 진입점
  - `LiveCall` — 통화 단위로 leg 를 묶고 그 안에서만 오디오 브리지
  - `OrchestratedLeg` — leg 당 STT 스트림 + 화자 태깅
  - `CallDescriptor`(통화 단위) / `LegDescriptor`(leg 단위) 분리
  - `MediaSink` — 코어→소스 오디오 출구 (소스가 구현)
  - `CallControl` — 코어→소스 호 제어 (소스가 구현). **미디어와 별도 인터페이스**
  - `CallState` — OFFERED → ANSWERED → ENDED
  - `PcmResampler` — 8kHz ↔ 16kHz
- `stt/TranscriptionEngine` 포트 + `ClovaTranscriptionEngine` (프로덕션 구현 1개, 테스트 더블이 2번째)
- `manual/ManualRetriever` 포트 + `SageMakerManualRetriever` / `StubManualRetriever`

### 통화 소스 3종이 동등해짐
| 소스 | 패키지 | 통화 키 | 입력 | 호 제어 |
|---|---|---|---|---|
| Vonage | `telephony/vonage` | conversation-uuid | WS binary → `writeAudio` | 없음 |
| SIP | `telephony/sip` | SIP Call-ID | AudioSocket TCP → `writeAudio` | ARI |
| 시나리오 | `telephony/scenario` | 생성 UUID | 대본 → `writeTranscript` | 있음(테스트용) |

### SIP 게이트웨이
- Asterisk **AudioSocket** 프로토콜 구현 (`AudioSocketCodec`, `AudioSocketServer`)
- **ARI** 클라이언트 — `Progress()` → `Stasis()` 로 붙잡았다가 수락 시 `answer` + `continue`
- `SipCallRegistry` — 통화 메타데이터 티켓 (1회용, TTL)
- 문서: `docs/sip-gateway.md`

### 호 제어
- `POST /api/v1/call/{callId}/answer | reject | hangup` (소유권 검증 + 접속기록)
- `AgentEventChannel` 이벤트: `call_offered` / `call_answered` / `call_started` / `call_ended`
- 프런트: `useAgentEvents` 훅, `IncomingCallBar` (수락 `Enter` / 거절 `Esc`)

### 리팩터링 중 발견해 고친 실제 버그
1. **동시 통화 오디오 혼선** — 브리지가 "열린 모든 WS 세션"을 순회 → 다른 신고 음성이 섞임
2. **Vonage 경로에서 callId 미전달** — `call_started` 를 push 하지 않아 프런트가 자막 구독 불가
3. **컨트롤 채널 미구독** — `/api/v1/agent/events` 를 프런트가 아무도 구독하지 않고 있었음
4. **`useHotkey` stale closure** — 콜백이 첫 렌더 상태를 붙든 채 굳음
5. **매뉴얼 JSON 손조립** — 전사본에 따옴표·개행이 있으면 요청이 깨짐
6. **`response.json` 디스크 쓰기** — 신고 전사본이 평문으로 리포에 커밋돼 있었음(삭제)
7. **`/manual/{callId}` 소유권 검증 누락** — 외부 추론 서버로 전사본을 보내는 경로인데 무방비
8. **UTF-8 인코딩** — `javac` 와 `sql.init` 이 플랫폼 기본(MS949)으로 읽어 한글이 깨짐
9. **시나리오 재생 스레드 풀 2개** — 겹치는 발화가 밀려 동시 발화가 순차 재생으로 바뀜

### 테스트 (CLOVA·Asterisk·네트워크·Spring 컨텍스트 없이 돎)
- `DefaultCallOrchestratorTest` — 통화 식별, 브리지 격리, 화자 태깅, 호 제어, 종료 (~30 케이스)
- `AudioSocketCodecTest` — TCP 분할 수신, 스트림 동기 손실, UUID 표현 편차
- `PcmResamplerTest`

### 검증 상태
- `./gradlew build` — **통과** (컨텍스트 로딩 포함)
- `npm run build` — **미확인**
- 코어 테스트 리포트 육안 확인 — **미확인** (`server/build/reports/tests/test/index.html`)
- 시나리오 데모 (착신 → 수락 → 자막) — **확인 중**

---

## 내일 할 일

### A. 남은 검증
- [ ] `npm run build` 통과시키기 (스토어 스키마 변경분)
- [ ] 코어 테스트 전부 green 확인
- [ ] `overlap-heavy` 시나리오로 동시 발화 UI 육안 확인

### B. 추가 리팩터링 후보
- [ ] **`speakerPhone` SSE 노출 제거** — DB 저장용 필드가 자막 청크마다 브라우저로 나감.
      SSE 전용 DTO 분리. 최소수집 원칙을 코드로 보여주는 소재.
- [ ] `HospitalPage` 더미 데이터 — 실제 API 연동 or "미구현" 명시
- [ ] `.ipynb_checkpoints`, `__pycache__` 리포에서 제거 + gitignore
- [ ] 루트 `README.md` — **머지 컨플릭트 마커(`<<<<<<< HEAD`)가 남아 있음.**
      실행 절차(Docker → application.yml → dev 프로파일)를 넣어 클론 후 5분 안에 뜨게
- [ ] `server/README.md` 제목 한 줄뿐

### C. 포폴 문서
- 형식: 마크다운, 신입 취업 지원용
- 본인 기여 범위: git 기준 31커밋, **최근 25커밋 전부 본인**
  (TS/Vite 마이그레이션, SSE 전환, gRPC/CLOVA, privacy 패키지, dev 하네스, 이번 리팩터링)
- AI 파트(KorDPR 학습/SageMaker 배포/비교실험)는 `leadawon` 담당 — 명확히 구분할 것

---

## 과장하면 안 되는 것 (면접 대비)

- **KorDPR 실제 성능**: Top-1 0.16 / Top-6 0.56. 동일 조건 BM25(0.29/0.53)를 넘지 못함.
  OpenAI `text-embedding-3-large` 가 0.46/0.84 로 크게 앞섬.
  → "비교 실험으로 한계를 확인했다"로 쓰면 오히려 좋은 소재. 성능 자랑은 금물.
- `ai/kordpr/retrieval_accuracy.png` 는 **upstream 오픈소스의 KorQuAD 벤치마크**이지
  이 시스템 성능이 아니다.
- `StubManualRetriever` 는 **검색 모델이 아니다.** 규칙 기반 키워드 매칭이며
  성능 수치를 여기서 산출하면 안 된다.
- **콜 컨트롤은 수락/종료만** 구현했다. 보류·전환·큐잉·스크린팝은 범위 밖.
  숨기지 말고 "범위 설정"으로 명시할 것.
- **브라우저폰은 폐기했다.** 실전 PSAP 이 1자 제어(상담원 PC 가 오디오 소유)를
  피하는 이유 — PC 장애 시 전사까지 죽고, 감사 가능성이 사라진다 — 를 근거로.
- 한국 119종합상황실의 구체적 CTI 인터페이스·교환기 구성은 **공개 자료로 확인 불가**.
  "한국 119는 ~를 씁니다"라고 단정하지 말 것.

## 참고: 실전 컨택센터 조사 결론

- 콜 컨트롤(CSTA/TAPI/JTAPI)과 미디어(RTP)는 **다른 계층**. Cisco Finesse 는
  오디오를 다루지 않고 IP Phone 이 담당한다.
- 실시간 전사용 오디오는 **상담원 PC 가 아니라 망 경계(SBC)** 에서 포크한다.
  표준은 SIPREC(RFC 7866), 벤더는 CUBE/AudioHook/KVS/Media Streams.
- 프로덕션은 전부 **채널 분리 스트림**. 믹스 모노 + diarization 은 선택지가 아니다.
  → 이 프로젝트의 leg 당 STT 스트림 설계가 여기 부합한다. (강한 소재)
- 소방청 **차세대 119통합시스템** 진행 중 (3년 2,598억, 2029 전국 운영,
  2026-03 KT 컨소시엄 ISMP 수주). AI 음성인식 기반 접수가 핵심.
  → 지원서에 맥락으로 쓸 것. 단 "AI 가 접수를 대체"가 아니라 **"요원 보조"** 로.
