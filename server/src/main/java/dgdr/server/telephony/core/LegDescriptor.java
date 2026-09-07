package dgdr.server.telephony.core;

/**
 * 통화 소스가 leg 를 열 때 코어에 넘기는 정보.
 *
 * <p>통화 단위 정보는 {@link CallDescriptor} 가 들고 있고, 여기에는
 * <b>이 leg 만의</b> 속성인 역할과 오디오 포맷만 남는다.
 *
 * @param call   이 leg 가 속한 통화
 * @param role   요원 leg 인지 신고자 leg 인지
 * @param format 이 leg 가 실어 나르는 PCM 포맷. 소스마다 다르다 —
 *               Vonage 는 16kHz, Asterisk 기본 slin 은 8kHz. 코어가
 *               STT 요구 포맷과 비교해 필요하면 리샘플링한다.
 */
public record LegDescriptor(
        CallDescriptor call,
        LegRole role,
        AudioFormat format
) {
    public LegDescriptor {
        if (call == null) throw new IllegalArgumentException("call descriptor is required");
        if (role == null) throw new IllegalArgumentException("role is required");
        if (format == null) format = AudioFormat.PCM16_16K_MONO;
    }

    public String providerCallKey() { return call.providerCallKey(); }
    public String agentUserId()     { return call.agentUserId(); }
    public String callerPhone()     { return call.callerPhone(); }

    /**
     * 이 leg 화자의 전화번호. 전사 레코드의 {@code speakerPhoneNumber} 로 저장된다.
     * 역할에서 파생되므로 별도 필드를 두지 않는다.
     */
    public String phoneNumber() {
        return role == LegRole.AGENT ? call.agentPhone() : call.callerPhone();
    }
}
