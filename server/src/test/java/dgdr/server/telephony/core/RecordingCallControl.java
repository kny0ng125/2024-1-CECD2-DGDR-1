package dgdr.server.telephony.core;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 호 제어 호출을 기록하는 테스트 더블.
 *
 * <p>{@link CallControl} 을 인터페이스로 둔 값이 여기서 회수된다.
 * Asterisk 없이도 "수락하면 게이트웨이에 응답을 지시하는가",
 * "게이트웨이가 실패하면 상태를 되돌리는가"를 검증할 수 있다.
 */
public final class RecordingCallControl implements CallControl {

    private final boolean supportsAnswer;
    private final AtomicInteger answerCount = new AtomicInteger();
    private final List<HangupCause> hangups = new CopyOnWriteArrayList<>();

    /** true 면 answer() 가 예외를 던진다 (게이트웨이 응답 실패 재현). */
    private volatile boolean failOnAnswer;

    public RecordingCallControl() {
        this(true);
    }

    public RecordingCallControl(boolean supportsAnswer) {
        this.supportsAnswer = supportsAnswer;
    }

    public static RecordingCallControl failing() {
        RecordingCallControl c = new RecordingCallControl();
        c.failOnAnswer = true;
        return c;
    }

    @Override
    public void answer() {
        answerCount.incrementAndGet();
        if (failOnAnswer) {
            throw new IllegalStateException("gateway refused to answer");
        }
    }

    @Override
    public void hangup(HangupCause cause) {
        hangups.add(cause);
    }

    @Override
    public boolean supportsAnswer() {
        return supportsAnswer;
    }

    public int answerCount()          { return answerCount.get(); }
    public List<HangupCause> hangups() { return hangups; }
}
