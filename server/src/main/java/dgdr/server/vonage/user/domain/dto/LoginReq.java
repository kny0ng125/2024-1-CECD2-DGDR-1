package dgdr.server.vonage.user.domain.dto;

public record LoginReq
(
    String id,
    String password
) {
    /** 로그에 평문 비밀번호가 남지 않도록 마스킹한다. */
    @Override
    public String toString() {
        return "LoginReq[id=%s, password=****]".formatted(id);
    }
}
