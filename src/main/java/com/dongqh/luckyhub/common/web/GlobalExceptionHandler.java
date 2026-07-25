package com.dongqh.luckyhub.common.web;

import com.dongqh.luckyhub.common.enums.CommonErrorCode;
import com.dongqh.luckyhub.common.enums.ErrorCode;
import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.common.result.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Comparator;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .sorted(Comparator.comparing(error -> error.getField()))
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .findFirst()
                .orElse(CommonErrorCode.INVALID_PARAMETER.message());
        return build(CommonErrorCode.INVALID_PARAMETER, message, request);
    }

    @ExceptionHandler({ConstraintViolationException.class, HandlerMethodValidationException.class})
    public ResponseEntity<ErrorResponse> handleConstraintViolation(Exception exception, HttpServletRequest request) {
        return build(CommonErrorCode.INVALID_PARAMETER, CommonErrorCode.INVALID_PARAMETER.message(), request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedJson(HttpMessageNotReadableException exception, HttpServletRequest request) {
        return build(CommonErrorCode.MALFORMED_JSON, CommonErrorCode.MALFORMED_JSON.message(), request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException exception, HttpServletRequest request) {
        return build(CommonErrorCode.RESOURCE_NOT_FOUND, CommonErrorCode.RESOURCE_NOT_FOUND.message(), request);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException exception, HttpServletRequest request) {
        log.warn("Business exception, requestId={}, code={}, message={}",
                RequestIdSupport.getRequestId(request), exception.getErrorCode().code(), exception.getMessage());
        return build(exception.getErrorCode(), exception.getMessage(), request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataConflict(DataIntegrityViolationException exception, HttpServletRequest request) {
        log.warn("Data conflict, requestId={}", RequestIdSupport.getRequestId(request));
        return build(CommonErrorCode.DATA_CONFLICT, CommonErrorCode.DATA_CONFLICT.message(), request);
    }

    @ExceptionHandler(RedisConnectionFailureException.class)
    public ResponseEntity<ErrorResponse> handleRedisFailure(RedisConnectionFailureException exception, HttpServletRequest request) {
        log.error("Redis unavailable, requestId={}", RequestIdSupport.getRequestId(request), exception);
        return build(CommonErrorCode.SERVICE_UNAVAILABLE, CommonErrorCode.SERVICE_UNAVAILABLE.message(), request);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorResponse> handleDataAccessFailure(DataAccessException exception, HttpServletRequest request) {
        log.error("Database operation failed, requestId={}", RequestIdSupport.getRequestId(request), exception);
        return build(CommonErrorCode.SYSTEM_ERROR, CommonErrorCode.SYSTEM_ERROR.message(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnknownException(Exception exception, HttpServletRequest request) {
        log.error("Unhandled exception, requestId={}", RequestIdSupport.getRequestId(request), exception);
        return build(CommonErrorCode.SYSTEM_ERROR, CommonErrorCode.SYSTEM_ERROR.message(), request);
    }

    private ResponseEntity<ErrorResponse> build(ErrorCode errorCode, String message, HttpServletRequest request) {
        ErrorResponse body = ErrorResponse.of(
                errorCode,
                message,
                RequestIdSupport.getRequestId(request),
                System.currentTimeMillis()
        );
        return ResponseEntity.status(errorCode.httpStatus()).body(body);
    }
}
