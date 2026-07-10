package dgdr.server.vonage.stt;

public record SttResult(
        String text,
        boolean isFinal
) {}
