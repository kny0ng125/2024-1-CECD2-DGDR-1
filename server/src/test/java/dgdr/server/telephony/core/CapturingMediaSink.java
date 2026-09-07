package dgdr.server.telephony.core;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** 브리지로 흘러들어온 오디오를 그대로 모아 두는 {@link MediaSink}. */
public final class CapturingMediaSink implements MediaSink {

    private final List<byte[]> received = new CopyOnWriteArrayList<>();
    private volatile boolean open = true;

    @Override
    public void send(byte[] pcm) {
        received.add(pcm);
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    public void closeSink() {
        open = false;
    }

    public List<byte[]> received() {
        return received;
    }

    public int frameCount() {
        return received.size();
    }

    public int totalBytes() {
        return received.stream().mapToInt(b -> b.length).sum();
    }
}
