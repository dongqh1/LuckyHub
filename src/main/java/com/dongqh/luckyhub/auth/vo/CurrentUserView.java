package com.dongqh.luckyhub.auth.vo;

import java.util.List;

public record CurrentUserView(Long userId,
                              String username,
                              String nickname,
                              Integer status,
                              List<String> roles,
                              List<String> permission

) {
}
