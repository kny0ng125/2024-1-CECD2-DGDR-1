package dgdr.server.user.domain;


import com.fasterxml.jackson.annotation.JsonIgnore;
import dgdr.server.call.Call;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name="users")
public class User {
    @Id
    @Column(name = "user_id", length = 50)
    private String userId;

    @Column(name = "name", length = 50)
    private String name;

    /**
     * BCrypt 해시(60자 고정).
     *
     * <p>「개인정보의 안전성 확보조치 기준」은 비밀번호를 복호화되지 않도록
     * 일방향 암호화하여 저장하도록 규정한다. 평문 저장은 위반이다.
     */
    @JsonIgnore
    @Column(name = "password", length = 60)
    private String password;

    @Column(name = "phone", length = 20)
    private String phone;

    @JsonIgnore
    @OneToMany(mappedBy = "user")
    private List<Call> calls;
}
