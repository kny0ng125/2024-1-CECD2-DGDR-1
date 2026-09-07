package dgdr.server.telephony.core;

import dgdr.server.stt.TranscriptionEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * {@link LegHandle} 의 코어측 구현.
 *
 * <p>leg 하나가 소유하는 것: 자기 STT 스트림, 자기 {@link MediaSink},
 * 자기 화자 역할. 이 셋이 leg 단위로 묶여 있어야 화자 분리가 성립한다.
 * (두 화자의 오디오를 한 STT 스트림에 섞으면 누가 말했는지 알 수 없다.)
 *
 * <h2>STT 스트림이 지연 개설되는 이유</h2>
 * <p>스트림은 leg 가 붙을 때가 아니라 통화가 {@link CallState#ANSWERED} 로
 * 넘어갈 때 열린다. OFFERED 구간에서 열면 링백 톤과 무음을 인식하게 되고,
 * 인식 과금도 통화가 성립하지 않은 구간에 대해 발생한다.
 */
final class OrchestratedLeg implements LegHandle {

    private static final Logger log = LoggerFactory.getLogger(OrchestratedLeg.class);

    private final String legId = UUID.randomUUID().toString();
    private final LiveCall call;
    private final LegDescriptor desc;
    private final MediaSink sink;

    /** 이 leg 전용 STT 스트림. 통화 수락 전에는 null. */
    private final AtomicReference<TranscriptionEngine.Stream> stt = new AtomicReference<>();

    /** STT 스트림을 여는 팩토리. 수락 시점에 호출된다. */
    private final SttOpener sttOpener;

    /** STT 엔진이 요구하는 포맷. leg 포맷과 다르면 write 때 변환한다. */
    private final AudioFormat sttFormat;

    /** 전사 결과 수신자 (코어가 캐시에 넣는 콜백). */
    private final Consumer<TranscriptChunk> onTranscript;

    /** leg 종료 처리를 한 번만 하기 위한 래치. */
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /** 코어가 leg 를 실제로 떼어낼 때 부르는 훅 (통화 종료 판단 포함). */
    private final Consumer<OrchestratedLeg> onClose;

    /** STT 스트림 개설을 코어에 위임하는 함수형 인터페이스. */
    @FunctionalInterface
    interface SttOpener {
        /** @return 열린 스트림, 실패했으면 null */
        TranscriptionEngine.Stream open(OrchestratedLeg leg);
    }

    OrchestratedLeg(LiveCall call, LegDescriptor desc, MediaSink sink,
                    SttOpener sttOpener, AudioFormat sttFormat,
                    Consumer<TranscriptChunk> onTranscript,
                    Consumer<OrchestratedLeg> onClose) {
        this.call = call;
        this.desc = desc;
        this.sink = sink;
        this.sttOpener = sttOpener;
        this.sttFormat = sttFormat;
        this.onTranscript = onTranscript;
        this.onClose = onClose;
    }

    // ── 코어 내부용 접근자 ──────────────────────────────────────

    String legId()          { return legId; }
    MediaSink sink()        { return sink; }
    AudioFormat format()    { return desc.format(); }
    LiveCall call()         { return call; }
    String phoneNumber()    { return desc.phoneNumber(); }

    void logRelayFailure(RuntimeException e) {
        log.debug("[leg {}] relay failed: {}", legId, e.toString());
    }

    /**
     * 통화가 수락되어 이제 전사를 시작해도 될 때 코어가 호출한다.
     * 여러 번 불려도 스트림은 하나만 열린다.
     */
    void startTranscription() {
        if (closed.get() || stt.get() != null) return;
        TranscriptionEngine.Stream opened = sttOpener.open(this);
        if (opened == null) return;                  // 개설 실패 — 통화는 계속한다
        if (!stt.compareAndSet(null, opened)) {
            opened.close();                          // 경합에서 졌다. 방금 연 것을 정리.
        }
    }

    // ── LegHandle ────────────────────────────────────────────

    @Override public Long callId()             { return call.callId(); }
    @Override public String providerCallKey()  { return call.providerCallKey(); }
    @Override public LegRole role()            { return desc.role(); }

    @Override
    public void writeAudio(byte[] pcm) {
        if (closed.get() || pcm == null || pcm.length == 0) return;

        // (1) 같은 통화의 다른 leg 로 릴레이 — 이게 있어야 통화가 성립한다.
        call.bridgeFrom(legId, pcm, desc.format());

        // (2) 이 leg 전용 STT 스트림으로 전달.
        //     수락 전이면 스트림이 없다. 링백 구간의 오디오는 버린다.
        TranscriptionEngine.Stream stream = stt.get();
        if (stream == null) return;
        try {
            byte[] forStt = desc.format().equals(sttFormat)
                    ? pcm
                    : PcmResampler.convert(pcm, desc.format(), sttFormat);
            stream.write(forStt);
        } catch (RuntimeException e) {
            // STT 가 죽어도 통화(브리지)는 계속되어야 한다.
            log.warn("[leg {}] stt write failed: {}", legId, e.toString());
        }
    }

    @Override
    public void writeTranscript(String text, boolean isFinal) {
        if (closed.get() || text == null || text.isBlank()) return;
        if (call.state() == CallState.OFFERED) return;   // 수락 전 발화는 없다
        onTranscript.accept(new TranscriptChunk(this, text, isFinal));
    }

    /** STT 엔진 콜백에서 들어온 결과를 같은 경로로 흘려보낸다. */
    void emitFromStt(String text, boolean isFinal) {
        if (text == null || text.isBlank()) return;
        onTranscript.accept(new TranscriptChunk(this, text, isFinal));
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;   // 멱등
        TranscriptionEngine.Stream stream = stt.getAndSet(null);
        if (stream != null) {
            try { stream.close(); }
            catch (RuntimeException e) { log.debug("[leg {}] stt close failed: {}", legId, e.toString()); }
        }
        onClose.accept(this);
    }

    /** 전사 한 조각 + 그 출처 leg. */
    record TranscriptChunk(OrchestratedLeg leg, String text, boolean isFinal) {}
}
