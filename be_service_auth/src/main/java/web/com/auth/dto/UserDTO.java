package web.com.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import web.com.auth.entity.RoleEntity;
import web.com.auth.entity.RoleEnum;
import web.com.auth.entity.UserEntity;

import java.util.List;

@Builder
@AllArgsConstructor
@Getter
@Setter
public class UserDTO {
    private Long userId;
    private String userName;
    private String nickName;
    private String email;
    private String phone;
    private String avatar;
    private List<RoleEnum> roles;

    public static UserDTO from(UserEntity user, List<RoleEnum> roles) {
        return UserDTO.builder()
                .userId(user.getId())
                .userName(user.getUserName())
                .nickName(user.getNickname())
                .email(user.getEmail())
                .phone(user.getPhone())
                .avatar(user.getAvatar())
                .roles(roles)
                .build();
    }
}
