package dgdr.server.vonage.clova;

import java.time.LocalDateTime;

public record TranscriptEntry(
        String speaker,          // "caller" or "agent"
        String speakerPhone,     // DB 저장용
        String text,
        boolean isFinal,
        LocalDateTime timestamp
) {}
