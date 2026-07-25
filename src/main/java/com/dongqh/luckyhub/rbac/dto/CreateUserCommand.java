package com.dongqh.luckyhub.rbac.dto;

import com.dongqh.luckyhub.common.validation.ValidPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "password")
public class CreateUserCommand {
    @NotBlank(message = "用户名不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9_]{4,20}$",
            message = "用户名只能包含字母、数字、下划线，长度为4-20个字符")
    private String username;

    @ValidPassword
    private String password;

    @Size(max = 50, message = "昵称不能超过50个字符")
    private String nickname;
}
