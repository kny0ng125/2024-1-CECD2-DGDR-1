package dgdr.server.telephony.sip;

import dgdr.server.telephony.core.LegRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * AudioSocket 통화 티켓 보관소.
 *
 * <h2>왜 필요한가</h2>
 * <p>AudioSocket 프로토콜이 실어 나르는 통화 정보는 UUID 하나뿐이다.
 * 발신번호도, 착신번호도, 어느 요원에게 배정된 통화인지도 오지 않는다.
 * 반면 코어는 leg 를 붙이려면 담당 요원을 알아야 한다
 * ({@code LegDescriptor.agentUserId}).
 *
 * <p>그래서 게이트웨이가 <b>AudioSocket 연결 직전에</b> 통화 메타데이터를
 * REST 로 먼저 등록하고, 오디오 연결은 UUID 로 그 티켓을 찾아간다.
 * Asterisk dialplan 기준:
 * <pre>
 *   same =&gt; n,Set(CALLUUID=${UUID})
 *   same =&gt; n,Set(CURL_RESULT=${CURL(http://server:8080/api/v1/sip/registrations,...)})
 *   same =&gt; n,AudioSocket(${CALLUUID},127.0.0.1:9092)
 * </pre>
 *
 * <p>대안은 ARI(Asterisk REST Interface)로 서버가 채널 정보를 역조회하는
 * 것이지만, 게이트웨이 종류에 종속되는 결합이 생긴다. 등록 방식은
 * "AudioSocket 을 말할 줄 아는 게이트웨이면 무엇이든" 붙을 수 있다.
 *
 * <p>인메모리인 이유: 티켓의 수명이 수십 초이고 소비되면 사라진다.
 * 서버 인스턴스가 여럿이면 게이트웨이가 등록한 인스턴스와 AudioSocket 이
 * 붙는 인스턴스가 달라질 수 있으므로, 그때는 공유 저장소(Redis)가 필요하다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SipCallRegistry {

    private final AudioSocketProperties properties;

    /**
     * 게이트웨이가 등록하는 통화 leg 정보.
     *
     * @param audioSocketUuid AudioSocket UUID 프레임으로 올 값 (조회 키)
     * @param providerCallKey 통화 식별자. 같은 통화의 두 leg 가 같은 값을 가진다.
     *                        보통 SIP {@code Call-ID}. leg 마다 UUID 는 다르지만
     *                        이 값은 같아야 코어가 한 통화로 묶는다.
     * @param role            이 leg 가 요원인지 신고자인지
     * @param agentPhone      요원 측 번호 (계정 해석에 사용)
     * @param callerPhone     신고자 번호
     */
    public record Ticket(
            String audioSocketUuid,
            String providerCallKey,
            LegRole role,
            String agentPhone,
            String callerPhone,
            Instant registeredAt
    ) {
        boolean isExpired(Instant now, java.time.Duration ttl) {
            return registeredAt.plus(ttl).isBefore(now);
        }
    }

    private final ConcurrentMap<String, Ticket> tickets = new ConcurrentHashMap<>();

    public void register(Ticket ticket) {
        purgeExpired();
        tickets.put(ticket.audioSocketUuid(), ticket);
        log.info("[sip] registered ticket uuid={} call={} role={}",
                ticket.audioSocketUuid(), ticket.providerCallKey(), ticket.role());
    }

    /**
     * 티켓을 꺼내며 <b>제거</b>한다. 티켓은 1회용이다 — 같은 UUID 로 두 번
     * 연결되는 것은 정상 흐름이 아니고, 남겨 두면 재사용 공격의 여지가 된다.
     */
    public Optional<Ticket> consume(String audioSocketUuid) {
        Ticket ticket = tickets.remove(audioSocketUuid);
        if (ticket == null) return Optional.empty();
        if (ticket.isExpired(Instant.now(), properties.getGateway().getRegistrationTtl())) {
            log.warn("[sip] ticket expired uuid={}", audioSocketUuid);
            return Optional.empty();
        }
        return Optional.of(ticket);
    }

    /** 등록만 되고 소비되지 않은 티켓 정리. 등록 시점마다 훑는다. */
    private void purgeExpired() {
        Instant now = Instant.now();
        java.time.Duration ttl = properties.getGateway().getRegistrationTtl();
        tickets.values().removeIf(t -> t.isExpired(now, ttl));
    }

    /** 진단용. */
    public int pendingCount() {
        return tickets.size();
    }
}
