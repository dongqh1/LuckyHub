package com.dongqh.luckyhub.common.enums;

import org.springframework.http.HttpStatus;

public interface ErrorCode {

    int code();

    String message();

    HttpStatus httpStatus();
}
