package dgdr.server.vonage.user.domain;


import com.fasterxml.jackson.annotation.JsonIgnore;
import dgdr.server.vonage.Call;
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
    private String userId;

    private String name;

    @JsonIgnore
    private String password;

    private String phone;

    @JsonIgnore
    @OneToMany(mappedBy = "user")
    private List<Call> calls;
}
