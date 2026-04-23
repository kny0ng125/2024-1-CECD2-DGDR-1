package dgdr.server.vonage;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class CallRecordDto {
    private final Long id;
    private final String speaker;
    private final String speakerPhoneNumber;
    private final String transcription;
    private final LocalDateTime time;

    @Builder
    public CallRecordDto(Long id, String speaker, String speakerPhoneNumber, String transcription, LocalDateTime time) {
        this.id = id;
        this.speaker = speaker;
        this.speakerPhoneNumber = speakerPhoneNumber;
        this.transcription = transcription;
        this.time = time;
    }
}
