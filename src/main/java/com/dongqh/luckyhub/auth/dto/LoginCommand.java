package com.dongqh.luckyhub.auth.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginCommand (

    @NotBlank(message = "用户名不能为空")
    @Size(max = 50, message = "用户名不能超过50个字符")
    String username,

    @NotBlank(message = "密码不能为空")
    String password
){

}
