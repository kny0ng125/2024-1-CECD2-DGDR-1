package dgdr.server.vonage.user.domain.dto;

import dgdr.server.vonage.user.domain.User;

public record UserSignUpReq (
    String id,
    String name,
    String password,
    String phone
){
    /**
     * 해시된 비밀번호를 받아 엔티티를 만든다.
     *
     * <p>평문 비밀번호를 그대로 담는 변환 메서드를 두지 않는 이유:
     * 호출부에서 해시를 빠뜨려도 컴파일이 통과해버리기 때문이다.
     * 해싱은 {@code UserAuthService} 가 책임진다.
     */
    public User toEntity(String encodedPassword) {
        return User.builder()
                .userId(id)
                .name(name)
                .password(encodedPassword)
                .phone(phone)
                .build();
    }

    /** 로그에 평문 비밀번호가 남지 않도록 마스킹한다. */
    @Override
    public String toString() {
        return "UserSignUpReq[id=%s, name=%s, phone=%s, password=****]"
                .formatted(id, name, phone);
    }
}
