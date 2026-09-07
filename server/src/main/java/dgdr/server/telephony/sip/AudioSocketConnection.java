package dgdr.server.telephony.sip;

import dgdr.server.telephony.core.AudioFormat;
import dgdr.server.telephony.core.CallControl;
import dgdr.server.telephony.core.CallDescriptor;
import dgdr.server.telephony.core.CallOrchestrator;
import dgdr.server.telephony.core.LegHandle;
import dgdr.server.telephony.core.MediaSink;
import dgdr.server.user.infra.UserRepository;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Optional;

/**
 * AudioSocket TCP 연결 하나 = 통화 leg 하나.
 *
 * <p>연결 수명 동안 프레임 루프를 돈다. 첫 UUID 프레임에서 티켓을 조회해
 * 코어에 leg 를 붙이고, 이후 AUDIO 프레임을 코어로 흘린다.
 * 브리지된 오디오는 {@link AudioSocketMediaSink} 를 통해 같은 소켓으로 나간다.
 */
@Slf4j
final class AudioSocketConnection implements Runnable {

    private final Socket socket;
    private final AudioSocketProperties properties;
    private final SipCallRegistry registry;
    private final CallOrchestrator orchestrator;
    private final UserRepository userRepository;
    private final Runnable onFinished;

    private LegHandle leg;

    AudioSocketConnection(Socket socket,
                          AudioSocketProperties properties,
                          SipCallRegistry registry,
                          CallOrchestrator orchestrator,
                          UserRepository userRepository,
                          Runnable onFinished) {
        this.socket = socket;
        this.properties = properties;
        this.registry = registry;
        this.orchestrator = orchestrator;
        this.userRepository = userRepository;
        this.onFinished = onFinished;
    }

    @Override
    public void run() {
        String peer = socket.getRemoteSocketAddress().toString();
        try (Socket s = socket;
             DataInputStream in = new DataInputStream(new BufferedInputStream(s.getInputStream()));
             OutputStream out = new BufferedOutputStream(s.getOutputStream())) {

            // Nagle 알고리즘은 작은 오디오 프레임을 뭉쳐 지연을 만든다.
            // 실시간 음성에서는 처리량보다 지연이 중요하다.
            s.setTcpNoDelay(true);

            // UUID 프레임이 올 때까지만 타임아웃을 건다. 오디오가 흐르기 시작한
            // 뒤에는 무음 구간에도 프레임이 계속 오므로 타임아웃을 풀 필요는 없지만,
            // 게이트웨이가 조용히 사라지는 경우를 잡기 위해 유지한다.
            s.setSoTimeout((int) properties.getAudiosocket().getHandshakeTimeout().toMillis());

            AudioFormat format = properties.getAudiosocket().resolveFormat();
            log.info("[audiosocket] connection from {} (format={})", peer, format);

            loop(in, out, format, peer);

        } catch (SocketTimeoutException e) {
            log.warn("[audiosocket] {} timed out waiting for frames", peer);
        } catch (IOException e) {
            log.debug("[audiosocket] {} io ended: {}", peer, e.toString());
        } catch (RuntimeException e) {
            log.error("[audiosocket] {} failed: {}", peer, e.toString(), e);
        } finally {
            closeLeg();
            onFinished.run();
            log.info("[audiosocket] connection closed: {}", peer);
        }
    }

    private void loop(DataInputStream in, OutputStream out, AudioFormat format, String peer)
            throws IOException {
        while (!Thread.currentThread().isInterrupted()) {
            AudioSocketFrame frame;
            try {
                frame = AudioSocketCodec.read(in);
            } catch (IllegalArgumentException badType) {
                // 알 수 없는 타입은 스트림 동기가 깨졌다는 뜻이다. 이 지점부터
                // 읽는 모든 바이트는 무의미하므로 이어가지 않고 연결을 끊는다.
                log.error("[audiosocket] {} frame desync: {}", peer, badType.getMessage());
                return;
            }
            if (frame == null) return;   // EOF — 게이트웨이가 닫음

            switch (frame.type()) {
                case UUID -> {
                    if (leg != null) {
                        log.warn("[audiosocket] {} duplicate UUID frame; ignored", peer);
                        break;
                    }
                    if (!attach(AudioSocketCodec.decodeUuid(frame.payload()), out, format, peer)) {
                        return;   // 티켓 없음 → 통화를 열 수 없다
                    }
                }
                case AUDIO -> {
                    if (leg == null) {
                        // UUID 보다 오디오가 먼저 오는 것은 규약 위반이다.
                        log.warn("[audiosocket] {} audio before UUID; dropping", peer);
                        break;
                    }
                    leg.writeAudio(frame.payload());
                }
                case DTMF -> log.debug("[audiosocket] {} dtmf '{}'", peer,
                        new String(frame.payload(), java.nio.charset.StandardCharsets.US_ASCII));
                case ERROR -> {
                    log.error("[audiosocket] {} gateway reported error code={}", peer,
                            frame.payload().length > 0 ? frame.payload()[0] : -1);
                    return;
                }
                case TERMINATE -> {
                    log.info("[audiosocket] {} terminate frame", peer);
                    return;
                }
            }
        }
    }

    /** 티켓을 조회해 코어에 leg 를 붙인다. @return 성공 여부 */
    private boolean attach(String uuid, OutputStream out, AudioFormat format, String peer) {
        Optional<SipCallRegistry.Ticket> found = registry.consume(uuid);
        if (found.isEmpty()) {
            // 게이트웨이가 등록을 건너뛰었거나 TTL 이 지났다. 담당 요원을 모르는
            // 통화를 임의로 열면 소유자 없는 기록이 생기므로 거절한다.
            log.error("[audiosocket] {} no registration for uuid={}", peer, uuid);
            return false;
        }
        SipCallRegistry.Ticket ticket = found.get();

        String agentUserId = userRepository.findByPhone(ticket.agentPhone())
                .map(u -> u.getUserId())
                .orElse(null);
        if (agentUserId == null) {
            log.error("[audiosocket] {} unknown agent phone '{}'", peer, ticket.agentPhone());
            return false;
        }

        CallDescriptor callDesc = new CallDescriptor(
                ticket.providerCallKey(), agentUserId,
                ticket.agentPhone(), ticket.callerPhone());

        // 여기서 CallControl.UNSUPPORTED 를 넘기는 것이 옳다. ARI 모드에서는
        // 이 통화가 이미 열려 있고(StasisStart 에서 SipCallControl 과 함께 개설됨)
        // 이 leg 는 합류만 하므로 control 인자가 무시된다. ARI 없이 dialplan 이
        // 직접 AudioSocket 을 부르는 배포에서는 제어 수단이 실제로 없다 —
        // dialplan 이 이미 Answer() 를 마친 뒤이기 때문이다.
        this.leg = orchestrator.attachLeg(
                callDesc.leg(ticket.role(), format),
                new AudioSocketMediaSink(out, socket),
                CallControl.UNSUPPORTED);

        log.info("[audiosocket] {} leg attached: callId={} role={} call={}",
                peer, leg.callId(), ticket.role(), ticket.providerCallKey());
        return true;
    }

    private void closeLeg() {
        if (leg == null) return;
        try { leg.close(); }
        catch (RuntimeException e) { log.warn("[audiosocket] leg close failed: {}", e.toString()); }
    }

    /**
     * 브리지된 오디오를 AudioSocket 으로 되돌려 보내는 sink.
     *
     * <p>{@code synchronized} 인 이유: 브리지는 상대 leg 의 수신 스레드에서
     * 호출되므로, 이 연결의 읽기 스레드와 다른 스레드가 같은
     * {@link OutputStream} 에 쓴다. 프레임 단위 원자성이 깨지면 게이트웨이
     * 쪽 파서의 동기가 무너진다.
     */
    private record AudioSocketMediaSink(OutputStream out, Socket socket) implements MediaSink {

        @Override
        public void send(byte[] pcm) {
            if (!isOpen() || pcm == null || pcm.length == 0) return;
            synchronized (out) {
                try {
                    AudioSocketCodec.write(out, AudioSocketFrame.audio(pcm));
                } catch (IOException e) {
                    log.debug("[audiosocket] send failed: {}", e.toString());
                }
            }
        }

        @Override
        public boolean isOpen() {
            return !socket.isClosed() && socket.isConnected();
        }
    }
}
