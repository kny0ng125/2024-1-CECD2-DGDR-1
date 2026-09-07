package dgdr.server.telephony.sip;

import dgdr.server.telephony.core.CallOrchestrator;
import dgdr.server.user.infra.UserRepository;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SIP 게이트웨이용 AudioSocket TCP 리스너.
 *
 * <p>Asterisk 등 게이트웨이가 SIP/RTP 를 처리하고, 통화 leg 하나당 TCP 연결
 * 하나로 순수 PCM 을 넘겨준다. 서버는 SIP 스택을 전혀 갖지 않는다 —
 * 그 부분은 검증된 게이트웨이에 맡기고, 이쪽은 미디어 경계만 구현한다.
 *
 * <h2>스레드 모델</h2>
 * <p>연결당 스레드다. 통화 leg 수가 수십 규모이고 각 스레드는 대부분
 * 소켓 read 에서 블록되므로 실용적이다. 수백 이상으로 가면 Netty 기반
 * 이벤트 루프로 바꿔야 한다 — 그때도 교체 범위는 이 클래스와
 * {@link AudioSocketConnection} 안으로 갇힌다. 코어는 영향받지 않는다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "sip.audiosocket.enabled", havingValue = "true")
@RequiredArgsConstructor
public class AudioSocketServer {

    private final AudioSocketProperties properties;
    private final SipCallRegistry registry;
    private final CallOrchestrator orchestrator;
    private final UserRepository userRepository;

    private final ExecutorService connectionPool =
            Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "audiosocket-conn");
                t.setDaemon(true);
                return t;
            });

    private final AtomicInteger openConnections = new AtomicInteger();

    private volatile ServerSocket serverSocket;
    private volatile Thread acceptThread;
    private volatile boolean running;

    /**
     * 컨텍스트가 완전히 뜬 뒤에 리스너를 연다.
     *
     * <p>{@code @PostConstruct} 가 아닌 이유: 통화가 붙는 즉시 코어가
     * DB 에 Call 을 쓰고 SSE 를 push 한다. 아직 초기화 중인 컨텍스트에
     * 통화를 받아들이면 그 경로들이 준비되지 않았을 수 있다.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void start() throws IOException {
        AudioSocketProperties.AudioSocket cfg = properties.getAudiosocket();

        // 설정이 틀렸으면 여기서 터뜨린다. 잘못된 코덱으로 조용히 뜨면
        // 통화가 붙은 뒤에야 알게 되고, 그때는 실제 신고 중일 수 있다.
        cfg.resolveFormat();

        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(
                InetAddress.getByName(cfg.getBindAddress()), cfg.getPort()));

        running = true;
        acceptThread = new Thread(this::acceptLoop, "audiosocket-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();

        log.info("[audiosocket] listening on {}:{} (codec={}, max={})",
                cfg.getBindAddress(), cfg.getPort(), cfg.getCodec(), cfg.getMaxConnections());
    }

    private void acceptLoop() {
        while (running && !serverSocket.isClosed()) {
            Socket socket;
            try {
                socket = serverSocket.accept();
            } catch (IOException e) {
                if (running) log.error("[audiosocket] accept failed: {}", e.toString());
                continue;
            }

            if (openConnections.get() >= properties.getAudiosocket().getMaxConnections()) {
                log.error("[audiosocket] connection limit reached ({}); rejecting {}",
                        properties.getAudiosocket().getMaxConnections(),
                        socket.getRemoteSocketAddress());
                closeQuietly(socket);
                continue;
            }

            openConnections.incrementAndGet();
            connectionPool.submit(new AudioSocketConnection(
                    socket, properties, registry, orchestrator, userRepository,
                    openConnections::decrementAndGet));
        }
        log.info("[audiosocket] accept loop stopped");
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (serverSocket != null) closeQuietly(serverSocket);
        if (acceptThread != null) acceptThread.interrupt();
        connectionPool.shutdownNow();
        log.info("[audiosocket] stopped");
    }

    /** 진단용. */
    public int openConnectionCount() {
        return openConnections.get();
    }

    private static void closeQuietly(java.io.Closeable c) {
        try { c.close(); } catch (IOException ignored) { }
    }
}
