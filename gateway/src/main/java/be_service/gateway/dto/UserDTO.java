package be_service.gateway.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * DTO nhận thông tin user từ AUTH-SERVICE sau khi validate JWT token thành công.
 * Dùng @JsonIgnoreProperties để không bị lỗi nếu auth service trả thêm field mới.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserDTO {
    private Long userId;
    private String userName;
    private String nickName;
    private String email;
    private String phone;
    private String avatar;
    private List<String> roles;
}
