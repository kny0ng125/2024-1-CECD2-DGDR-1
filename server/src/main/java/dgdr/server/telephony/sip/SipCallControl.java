package dgdr.server.telephony.sip;

import dgdr.server.telephony.core.CallControl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Asterisk 채널 하나에 대한 {@link CallControl} 구현.
 *
 * <p>수락은 ARI 로 채널에 응답한 뒤 dialplan 으로 되돌려 보내는 두 단계다.
 * Stasis 앱에 머무는 동안에는 dialplan 이 진행되지 않으므로, AudioSocket 을
 * 실행시키려면 명시적으로 {@code continue} 를 해 줘야 한다. 그 지점이
 * 미디어가 붙는 순간이고, 그래서 <b>수락하기 전에는 오디오 연결 자체가
 * 존재하지 않는다.</b>
 *
 * <p>{@code answer()} 안에서 AudioSocket 티켓을 먼저 등록하는 순서가 중요하다.
 * dialplan 이 재개되면 곧바로 AudioSocket 이 연결을 시도하는데, 그때 티켓이
 * 없으면 서버가 자기 자신의 연결을 거절한다.
 */
@Slf4j
@RequiredArgsConstructor
public class SipCallControl implements CallControl {

    private final String channelId;
    private final String audioSocketUuid;
    private final SipCallRegistry.Ticket ticket;
    private final AriClient ari;
    private final SipCallRegistry registry;
    private final AudioSocketProperties properties;

    @Override
    public void answer() {
        // 1) 미디어가 붙을 자리를 먼저 마련한다. dialplan 재개 직후 AudioSocket 이
        //    바로 연결되므로, 티켓 등록이 늦으면 자기 연결을 거절하게 된다.
        registry.register(ticket);

        // 2) SIP 200 OK. 이 시점부터 발신자에게 통화가 연결된 것으로 보인다.
        ari.answer(channelId);

        // 3) dialplan 으로 돌려보내 AudioSocket 을 실행시킨다.
        AudioSocketProperties.Ari cfg = properties.getAri();
        ari.setChannelVar(channelId, "AUDIOSOCKET_UUID", audioSocketUuid);
        ari.continueInDialplan(channelId, cfg.getMediaContext(),
                cfg.getMediaExtension(), 1);

        log.info("[sip] answered channel {} → dialplan {}@{}",
                channelId, cfg.getMediaExtension(), cfg.getMediaContext());
    }

    @Override
    public void hangup(HangupCause cause) {
        // 등록만 되고 쓰이지 않은 티켓을 남겨 두지 않는다.
        registry.consume(audioSocketUuid);
        ari.hangup(channelId, toAriReason(cause));
    }

    @Override
    public boolean supportsAnswer() {
        return true;
    }

    /**
     * 종료 사유를 ARI 의 reason 문자열로 옮긴다.
     *
     * <p>발신자 단말에 다르게 표시되므로 구분이 의미를 가진다 —
     * 거절은 "통화 거절", 정상 종료는 그냥 통화 종료로 보인다.
     */
    private static String toAriReason(HangupCause cause) {
        return switch (cause) {
            case REJECTED -> "decline";     // SIP 603
            case BUSY     -> "busy";        // SIP 486
            case TIMEOUT  -> "no_answer";   // SIP 408
            case NORMAL   -> "normal";      // BYE
        };
    }
}
