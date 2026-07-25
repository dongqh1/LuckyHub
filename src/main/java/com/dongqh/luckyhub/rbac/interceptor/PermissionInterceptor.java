package com.dongqh.luckyhub.rbac.interceptor;

import com.dongqh.luckyhub.auth.context.LoginContext;
import com.dongqh.luckyhub.auth.model.LoginPrincipal;
import com.dongqh.luckyhub.common.exception.ForbiddenException;
import com.dongqh.luckyhub.rbac.annotation.RequirePermission;
import com.dongqh.luckyhub.rbac.service.UserPermissionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

@Component
public class PermissionInterceptor
        implements HandlerInterceptor {

    private static final Logger log =
            LoggerFactory.getLogger(
                    PermissionInterceptor.class
            );

    private final UserPermissionService
            userPermissionService;

    public PermissionInterceptor(
            UserPermissionService userPermissionService
    ) {
        this.userPermissionService =
                userPermissionService;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) {
        /*
         * 静态资源或不存在的 API 可能不是 HandlerMethod。
         * 这里继续执行，让 Spring 最终返回正常的 404。
         * 真正的后台 Controller 一定是 HandlerMethod。
         */
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RequirePermission requirement =
                findRequirement(handlerMethod);
        //找到注解权限

        /*
         * /api/admin/** 下只要匹配到 Controller，
         * 却没有配置权限注解，就默认拒绝。
         */
        if (requirement == null
                || requirement.value().isBlank()) {
            log.error(
                    "Admin endpoint has no permission annotation: "
                            + "{}#{}",
                    handlerMethod.getBeanType().getName(),
                    handlerMethod.getMethod().getName()
            );

            throw new ForbiddenException(
                    "后台接口未配置访问权限"
            );
        }

        LoginPrincipal principal =
                LoginContext.require();

        Set<String> permissionCodes =
                userPermissionService.findPermissionCodes(
                        principal.userId()
                );

        String requiredPermission =
                requirement.value();

        if (!permissionCodes.contains(
                requiredPermission
        )) {
            log.warn(
                    "Permission denied, userId={}, "
                            + "username={}, permission={}, "
                            + "handler={}#{}",
                    principal.userId(),
                    principal.username(),
                    requiredPermission,
                    handlerMethod.getBeanType().getName(),
                    handlerMethod.getMethod().getName()
            );

            throw new ForbiddenException(
                    "没有访问该接口的权限"
            );
        }

        return true;
    }

    private RequirePermission findRequirement(
            HandlerMethod handlerMethod
    ) {
        RequirePermission methodRequirement =
                AnnotatedElementUtils.findMergedAnnotation(
                        handlerMethod.getMethod(),
                        RequirePermission.class
                );

        if (methodRequirement != null) {
            return methodRequirement;
        }

        return AnnotatedElementUtils.findMergedAnnotation(
                handlerMethod.getBeanType(),
                RequirePermission.class
        );
    }
}
