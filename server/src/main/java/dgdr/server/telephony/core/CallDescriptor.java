package dgdr.server.telephony.core;

/**
 * 통화 <b>한 건</b>의 신원. leg 가 아니라 통화의 속성만 담는다.
 *
 * <h2>{@link LegDescriptor} 와 분리한 이유</h2>
 * <p>통화가 leg 보다 먼저 존재하는 순간이 있다. SIP 게이트웨이에서 벨이
 * 울리는 동안에는 아직 오디오 연결이 없다 — 요원이 수락해야 미디어가
 * 붙는다. 그 구간에도 통화는 존재해야 하고(callId 가 있어야 요원 화면에
 * 벨을 띄운다), 따라서 "통화를 여는 데 필요한 정보"가 "leg 를 붙이는 데
 * 필요한 정보"와 별도로 있어야 한다.
 *
 * <p>합쳐 두면 leg 마다 통화 단위 정보를 중복해서 들고 다니게 되고,
 * 두 leg 가 서로 다른 값을 주면 어느 쪽이 맞는지 알 수 없게 된다.
 *
 * @param providerCallKey 통화 소스가 부여한 통화 식별자. Vonage 의
 *                        {@code conversation_uuid}, SIP 의 {@code Call-ID},
 *                        AudioSocket 의 UUID 가 모두 이 자리로 정규화된다.
 *                        같은 값을 가진 leg 들이 한 통화로 묶인다. 코어는
 *                        내용을 해석하지 않고 동등성만 본다.
 * @param agentUserId     담당 요원의 User ID. <b>필수</b> — 코어가 Call 의
 *                        소유자를 정하고 알림을 보낼 대상을 찾는 데 쓴다.
 *                        소스는 통화를 열기 전에 번호→계정 해석을 끝내야 한다.
 * @param agentPhone      요원 측 번호(E.164). 없을 수 있다.
 * @param callerPhone     신고자 번호(E.164). 발신번호 표시제한이면 없다.
 */
public record CallDescriptor(
        String providerCallKey,
        String agentUserId,
        String agentPhone,
        String callerPhone
) {
    public CallDescriptor {
        if (providerCallKey == null || providerCallKey.isBlank()) {
            throw new IllegalArgumentException("providerCallKey is required");
        }
        if (agentUserId == null || agentUserId.isBlank()) {
            // 담당 요원을 모르면 통화를 열 수 없다. 소스가 번호→계정 해석에
            // 실패했다는 뜻이므로 거절하는 편이 낫다. 예전 코드는 이 경우
            // Call 생성을 조용히 건너뛰어, 통화는 이어지는데 기록은 남지 않았다.
            throw new IllegalArgumentException("agentUserId is required to open a call");
        }
    }

    /** 이 통화에 leg 를 하나 만든다. */
    public LegDescriptor leg(LegRole role, AudioFormat format) {
        return new LegDescriptor(this, role, format);
    }

    /** 오디오를 주고받지 않는 leg (시나리오 재생, 외부 STT 게이트웨이). */
    public LegDescriptor textOnlyLeg(LegRole role) {
        return new LegDescriptor(this, role, AudioFormat.PCM16_16K_MONO);
    }
}
