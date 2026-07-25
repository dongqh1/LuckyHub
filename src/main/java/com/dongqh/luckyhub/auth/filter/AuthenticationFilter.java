package com.dongqh.luckyhub.auth.filter;

import com.dongqh.luckyhub.auth.context.LoginContext;
import com.dongqh.luckyhub.auth.enums.AuthErrorCode;
import com.dongqh.luckyhub.auth.model.JwtPayload;
import com.dongqh.luckyhub.auth.model.LoginPrincipal;
import com.dongqh.luckyhub.auth.security.JwtService;
import com.dongqh.luckyhub.auth.security.SessionService;
import com.dongqh.luckyhub.common.enums.ErrorCode;
import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.common.result.ErrorResponse;
import com.dongqh.luckyhub.common.web.RequestIdSupport;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class AuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final SessionService sessionService;
    private final ObjectMapper objectMapper;

    public AuthenticationFilter(
            JwtService jwtService,
            SessionService sessionService,
            ObjectMapper objectMapper
    ) {
        this.jwtService = jwtService;
        this.sessionService = sessionService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            String token = extractToken(request);
            JwtPayload payload = jwtService.parse(token);

            boolean sessionValid = sessionService.isValid(
                    payload.sessionId(),
                    payload.userId()
            );

            if (!sessionValid) {
                throw new BusinessException(
                        AuthErrorCode.INVALID_TOKEN
                );
            }

            LoginPrincipal principal = new LoginPrincipal(
                    payload.userId(),
                    payload.username(),
                    payload.sessionId()
            );

            LoginContext.set(principal);

            filterChain.doFilter(request, response);
        } catch (BusinessException exception) {
            writeError(
                    request,
                    response,
                    exception.getErrorCode(),
                    exception.getMessage()
            );
        } finally {
            LoginContext.clear();
        }
    }

    private String extractToken(HttpServletRequest request) {
        String authorization =
                request.getHeader(AUTHORIZATION);

        if (authorization == null
                || !authorization.startsWith(BEARER_PREFIX)) {
            throw new BusinessException(
                    AuthErrorCode.INVALID_TOKEN
            );
        }

        String token = authorization
                .substring(BEARER_PREFIX.length())
                .trim();

        if (token.isBlank()) {
            throw new BusinessException(
                    AuthErrorCode.INVALID_TOKEN
            );
        }

        return token;
    }

    private void writeError(
            HttpServletRequest request,
            HttpServletResponse response,
            ErrorCode errorCode,
            String message
    ) throws IOException {
        ErrorResponse body = ErrorResponse.of(
                errorCode,
                message,
                RequestIdSupport.getRequestId(request),
                System.currentTimeMillis()
        );

        response.setStatus(errorCode.httpStatus().value());
        response.setCharacterEncoding(
                StandardCharsets.UTF_8.name()
        );
        response.setContentType(
                MediaType.APPLICATION_JSON_VALUE
        );

        objectMapper.writeValue(
                response.getOutputStream(),
                body
        );
    }
}
