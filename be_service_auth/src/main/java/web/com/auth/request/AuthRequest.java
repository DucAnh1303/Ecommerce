package web.com.auth.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AuthRequest {

    @NotBlank(message = "User Name is not blank")
    private String userName;
    @NotBlank(message = "password is not empty")
    private String password;
}
