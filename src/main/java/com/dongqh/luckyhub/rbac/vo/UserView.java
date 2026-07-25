package com.dongqh.luckyhub.rbac.vo;

import com.dongqh.luckyhub.rbac.entity.SysUser;

import java.time.LocalDateTime;

public record UserView(
        Long id,
        String username,
        String nickname,
        Integer status,
        LocalDateTime createdAt
) {
    public static UserView from(SysUser user) {
        return new UserView(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getStatus(),
                user.getCreatedAt()
        );
    }
}
