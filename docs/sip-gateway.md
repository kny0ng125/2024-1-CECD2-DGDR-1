# SIP 게이트웨이 연동 (Asterisk AudioSocket)

## 왜 이 경계인가

이 서버는 **SIP 스택을 갖지 않는다.** INVITE/SDP 협상, RTP/RTCP, jitter buffer,
DTMF, 코덱 협상은 전부 게이트웨이의 몫이다. 그 영역은 Asterisk·FreeSWITCH 같은
검증된 구현이 훨씬 잘하고, 직접 구현했다면 이 프로젝트의 나머지를 할 시간이 없었다.

서버가 필요로 하는 것은 **화자별로 분리된 순수 PCM 스트림** 하나뿐이다.
AudioSocket 은 정확히 그 경계에 있는 프로토콜이라 골랐다.

```
전화망 ──SIP/RTP──> Asterisk ──AudioSocket(TCP)──> 이 서버
                     └ SIP 스택 전담              └ PCM 만 받음
```

## 와이어 포맷

프레임 하나의 구조가 전부다.

```
+--------+------------------+------------------+
| type   | length (big-end) | payload          |
| 1 byte | 2 bytes          | length bytes     |
+--------+------------------+------------------+
```

| type | 이름 | payload |
|------|------|---------|
| `0x00` | TERMINATE | 없음 |
| `0x01` | UUID | 통화 UUID 16바이트 (또는 36자 ASCII) |
| `0x03` | DTMF | ASCII 1바이트 |
| `0x10` | AUDIO | signed linear 16bit LE · 8kHz 기본, 320B = 20ms |
| `0xFF` | ERROR | 오류 코드 1바이트 |

구현: `telephony/sip/AudioSocketCodec.java`, 검증: `AudioSocketCodecTest`
(TCP 분할 수신, 스트림 동기 손실, UUID 표현 편차까지 커버).

## 통화 메타데이터를 따로 등록하는 이유

AudioSocket 이 실어 나르는 통화 정보는 **UUID 하나뿐**이다. 발신번호도,
어느 요원에게 배정된 통화인지도 오지 않는다. 반면 코어는 leg 를 붙이려면
담당 요원을 알아야 한다(`LegDescriptor.agentUserId` — 없으면 소유자 없는
통화 기록이 생긴다).

그래서 게이트웨이가 **AudioSocket 연결 직전에** 메타데이터를 REST 로 등록하고,
오디오 연결은 UUID 로 그 티켓을 찾아간다.

```
Asterisk                          서버
   │                               │
   ├─ POST /api/v1/sip/registrations ─────>  티켓 보관 (TTL 60s)
   │    {audioSocketUuid, providerCallKey,
   │     role, agentPhone, callerPhone}
   │                               │
   ├─ AudioSocket TCP connect ────────────>  accept
   ├─ UUID 프레임 ────────────────────────>  티켓 조회 → attachLeg()
   ├─ AUDIO 프레임 ... ───────────────────>  브리지 + STT
   └─ TERMINATE / FIN ───────────────────>  leg.close() → 통화 종료
```

대안은 ARI(Asterisk REST Interface)로 서버가 채널 정보를 역조회하는 것이지만,
그러면 게이트웨이 종류에 결합된다. 등록 방식은 *AudioSocket 을 말할 줄 아는
게이트웨이면 무엇이든* 붙을 수 있다.

티켓은 **1회용**이다. 소비되면 즉시 제거하고, TTL 이 지나면 버린다.

## Asterisk 설정

### `pjsip.conf` (발췌)

```ini
[trunk]
type = endpoint
context = emergency-in
disallow = all
allow = alaw
allow = ulaw
```

### `extensions.conf` — ARI 모드 (권장, 수락/거절 지원)

요원이 수락 버튼을 누르기 전에는 **응답하지 않아야** 한다. 그러려면 채널을
Stasis 앱에 넣어 서버가 붙잡고 있어야 한다. `Answer()` 를 바로 하면 벨이
울리는 구간 자체가 없어져 수락 버튼이 의미를 잃는다.

```ini
[emergency-in]
exten => _X.,1,NoOp(=== 119 inbound: ${CALLERID(num)} -> ${EXTEN} ===)
 same => n,Progress()            ; 링백만 들려주고 응답은 보류
 same => n,Stasis(dgdr)          ; 서버(ARI)가 제어권을 가짐
 same => n,Hangup()

; 서버가 수락 시 여기로 continue 시킨다.
; AUDIOSOCKET_UUID 는 서버가 채널 변수로 심어 준다.
[dgdr-media]
exten => start,1,NoOp(=== media start ${AUDIOSOCKET_UUID} ===)
 same => n,Set(CHANNEL(audio_format)=slin)
 same => n,AudioSocket(${AUDIOSOCKET_UUID},127.0.0.1:9092)
 same => n,Hangup()
```

`ari.conf` 에 사용자를 만들고 `http.conf` 에서 HTTP 를 켜야 한다.

```ini
; http.conf
[general]
enabled = yes
bindaddr = 127.0.0.1
bindport = 8088

; ari.conf
[general]
enabled = yes
[dgdr]
type = user
read_only = no
password = <shared secret>
```

이 모드에서는 **서버가 AudioSocket UUID 를 만들고 티켓도 직접 등록**한다.
dialplan 이 `CURL()` 로 등록할 필요가 없다.

### `extensions.conf` — ARI 없이 (수락 흐름 없음)

`_X.` 로 들어온 통화를 요원 번호로 라우팅하면서, 같은 통화의 두 leg 에
**같은 `providerCallKey`(SIP Call-ID)** 를 부여하는 것이 핵심이다.
이 값이 같아야 코어가 한 통화로 묶는다.

```ini
[emergency-in]
exten => _X.,1,NoOp(=== 119 inbound: ${CALLERID(num)} -> ${EXTEN} ===)
 same => n,Answer()
 same => n,Set(CALLKEY=${CHANNEL(pjsip,call-id)})
 same => n,Set(LEG_UUID=${UUID()})

 ; AudioSocket 을 열기 전에 통화 메타데이터를 등록한다.
 ; 실패하면 서버가 leg 를 거절하므로 그대로 끊는다.
 same => n,Set(REG=${CURL(http://127.0.0.1:8080/api/v1/sip/registrations,\
{"audioSocketUuid":"${LEG_UUID}","providerCallKey":"${CALLKEY}",\
"role":"CALLER","agentPhone":"${EXTEN}","callerPhone":"${CALLERID(num)}"})})
 same => n,GotoIf($["${REG}" = ""]?failed)

 ; slin = 8kHz. slin16 을 쓰려면 application.yml 의 codec 도 함께 바꿀 것.
 same => n,Set(CHANNEL(audio_format)=slin)
 same => n,AudioSocket(${LEG_UUID},127.0.0.1:9092)
 same => n,Hangup()

 same => n(failed),NoOp(registration failed)
 same => n,Hangup(38)
```

`CURL()` 은 `func_curl.so` 가 필요하다. 헤더를 붙이려면
`CURLOPT(httpheader)` 로 `X-Sip-Gateway-Secret` 을 설정한다.

```ini
 same => n,Set(CURLOPT(httpheader)=X-Sip-Gateway-Secret: ${SIP_GATEWAY_SECRET})
```

## 서버 설정

```yaml
sip:
  audiosocket:
    enabled: true
    port: 9092
    bind-address: 127.0.0.1   # ⚠ 공인 IP 금지 (아래 참고)
    codec: slin               # Asterisk 쪽 audio_format 과 일치시킬 것
  gateway:
    shared-secret: <생성한 값>
```

### 보안상 주의

**AudioSocket 프로토콜에는 인증이 없다.** 이 포트에 TCP 로 붙을 수 있는 자는
임의의 통화를 열고 오디오를 주입할 수 있다. 그래서

- `bind-address` 기본값이 `127.0.0.1` 이다. 게이트웨이가 다른 호스트라면
  사설망 주소로 바꾸고 방화벽으로 출처를 제한한다.
- 그마저도 **티켓이 없으면 leg 를 거절한다.** 등록 API 를 통과하지 못한
  연결은 UUID 조회에서 실패하고 즉시 끊긴다.
- 등록 API 는 공유 비밀을 **상수 시간 비교**한다. `String.equals` 는 첫
  불일치에서 빠져나오므로 응답 시간 차이로 비밀을 한 바이트씩 알아낼 수 있다.
- 공유 비밀이 설정되지 않으면 등록 API 는 모든 요청을 `503` 으로 거절한다.
  설정 누락이 곧 무인증 개방이 되는 사고를 막기 위함이다.

## 샘플레이트

Asterisk 기본 `slin` 은 8kHz, CLOVA Speech Nest 는 16kHz 만 받는다.
게이트웨이 설정에 의존하지 않도록 **서버가 스스로 맞춘다** —
`LegDescriptor.format` 과 `TranscriptionEngine.requiredFormat()` 이 다르면
`PcmResampler` 가 끼어든다. 8kHz SIP leg 와 16kHz Vonage leg 가 한 통화에
섞여도 브리지 시점에 서로의 포맷으로 변환된다.

## 검증

Asterisk 없이도 프로토콜 계층은 전부 테스트로 검증된다.

```
./gradlew test --tests '*AudioSocketCodecTest'
./gradlew test --tests '*PcmResamplerTest'
./gradlew test --tests '*DefaultCallOrchestratorTest'
```

코덱을 소켓 I/O 에서 분리해 둔 이유가 이것이다. 프레임 파싱은 한 바이트만
어긋나도 이후 전부가 쓰레기가 되는 코드인데, 실제 TCP 연결을 띄워야만
검증할 수 있다면 아무도 검증하지 않게 된다.

실제 게이트웨이까지 포함한 확인은 Docker 로 Asterisk 를 띄워서 한다.

```bash
docker run -d --name asterisk --network host \
  -v $PWD/docs/asterisk:/etc/asterisk \
  andrius/asterisk:latest
```
