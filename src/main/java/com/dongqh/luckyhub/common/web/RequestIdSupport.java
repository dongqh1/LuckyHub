package com.dongqh.luckyhub.common.web;

import jakarta.servlet.http.HttpServletRequest;

public final class RequestIdSupport {

    public static final String HEADER_NAME = "X-Request-Id";
    public static final String REQUEST_ATTRIBUTE = RequestIdSupport.class.getName() + ".requestId";
    public static final String MDC_KEY = "requestId";

    private RequestIdSupport() {
    }

    public static String getRequestId(HttpServletRequest request) {
        Object requestId = request.getAttribute(REQUEST_ATTRIBUTE);
        return requestId instanceof String value ? value : "unknown";
    }
}
