package com.sgs.capability.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgs.capability.dto.AbpResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Enforces copied ABP permission names on annotated controller actions. */
@Component
public class AuthorizationInterceptor implements HandlerInterceptor {
    public static final String AUTH_CONTEXT = "authContext";

    private final AuthService auth;
    private final ObjectMapper objectMapper;

    public AuthorizationInterceptor(AuthService auth, ObjectMapper objectMapper) {
        this.auth = auth;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        if (allowAnonymous(method)) {
            return true;
        }
        RequirePermission permission = permission(method);
        if (permission == null) {
            return true;
        }
        AuthContext context = auth.currentUser(request.getHeader("Authorization")).orElse(null);
        if (context == null) {
            String message = auth.delegationValidationError(request.getHeader("Authorization"))
                    .orElse("未登录或登录已过期");
            writeDenied(response, HttpServletResponse.SC_UNAUTHORIZED, message);
            return false;
        }
        request.setAttribute(AUTH_CONTEXT, context);
        if (!auth.hasPermission(context, permission.value())) {
            writeDenied(response, HttpServletResponse.SC_FORBIDDEN, "没有权限：" + permission.value());
            return false;
        }
        return true;
    }

    private RequirePermission permission(HandlerMethod method) {
        RequirePermission action = method.getMethodAnnotation(RequirePermission.class);
        return action == null ? method.getBeanType().getAnnotation(RequirePermission.class) : action;
    }

    private boolean allowAnonymous(HandlerMethod method) {
        return method.hasMethodAnnotation(AllowAnonymous.class)
                || method.getBeanType().isAnnotationPresent(AllowAnonymous.class);
    }

    private void writeDenied(HttpServletResponse response, int status, String message) throws IOException {
        AbpResponse<Void> body = AbpResponse.denied(message);
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), body);
    }
}
