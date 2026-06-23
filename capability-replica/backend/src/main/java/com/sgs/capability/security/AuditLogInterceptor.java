package com.sgs.capability.security;

import com.sgs.capability.model.AuditLog;
import com.sgs.capability.service.CapabilityStore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Locale;

/** Writes current replica operations into the same audit table imported from production. */
@Component
public class AuditLogInterceptor implements HandlerInterceptor {
    private static final String START_TIME = AuditLogInterceptor.class.getName() + ".startTime";

    private final AuthService auth;
    private final CapabilityStore store;

    public AuditLogInterceptor(AuthService auth, CapabilityStore store) {
        this.auth = auth;
        this.store = store;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(START_TIME, System.nanoTime());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        if (!request.getRequestURI().startsWith("/api/")) {
            return;
        }
        try {
            AuthContext context = currentContext(request);
            AuditLog log = new AuditLog();
            log.userId = context == null ? null : context.user().id;
            log.impersonatorTenantId = context == null ? null : context.impersonatorTenantId();
            log.impersonatorUserId = context == null ? null : context.impersonatorUserId();
            log.serviceName = serviceName(request, handler);
            log.methodName = methodName(request, handler);
            log.parameters = requestParameters(request);
            log.executionTime = LocalDateTime.now().toString();
            log.time = log.executionTime;
            log.executionDuration = executionDuration(request);
            log.clientIpAddress = clientIp(request);
            log.clientName = request.getHeader("X-Forwarded-Host");
            log.browserInfo = truncate(request.getHeader("User-Agent"), 512);
            log.exception = exceptionText(response, ex);
            log.customData = "HTTP " + response.getStatus();
            log.result = log.exception == null ? "成功" : "异常";
            store.appendAuditLog(log, context == null ? 1 : context.tenantId());
        } catch (RuntimeException ignored) {
            // Audit logging must never break the business request.
        }
    }

    private AuthContext currentContext(HttpServletRequest request) {
        Object value = request.getAttribute(AuthorizationInterceptor.AUTH_CONTEXT);
        if (value instanceof AuthContext context) {
            return context;
        }
        return auth.currentUser(request.getHeader("Authorization")).orElse(null);
    }

    private int executionDuration(HttpServletRequest request) {
        Object value = request.getAttribute(START_TIME);
        if (!(value instanceof Long startTime)) {
            return 0;
        }
        long elapsedMillis = (System.nanoTime() - startTime) / 1_000_000;
        return elapsedMillis > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(elapsedMillis, 0);
    }

    private String serviceName(HttpServletRequest request, Object handler) {
        String path = request.getRequestURI();
        if (path.startsWith("/api/services/app/")) {
            String[] parts = path.substring("/api/services/app/".length()).split("/");
            if (parts.length > 0 && !parts[0].isBlank()) {
                return "SgsMineral.CapabilityTable." + parts[0] + "AppService";
            }
        }
        if (path.startsWith("/api/")) {
            String[] parts = path.substring("/api/".length()).split("/");
            if (parts.length > 0 && !parts[0].isBlank()) {
                return "SgsMineral.CapabilityTable.Web.Controllers." + parts[0] + "Controller";
            }
        }
        if (handler instanceof HandlerMethod method) {
            return method.getBeanType().getName();
        }
        return "UnknownService";
    }

    private String methodName(HttpServletRequest request, Object handler) {
        String path = request.getRequestURI();
        String[] parts = path.split("/");
        for (int index = parts.length - 1; index >= 0; index--) {
            if (!parts[index].isBlank()) {
                return parts[index];
            }
        }
        if (handler instanceof HandlerMethod method) {
            return method.getMethod().getName();
        }
        return request.getMethod();
    }

    private String requestParameters(HttpServletRequest request) {
        String body = requestBody(request);
        String value = body.isBlank()
                ? request.getQueryString()
                : body;
        if (value == null || value.isBlank()) {
            value = "{}";
        }
        return truncate(redact(value), 1024);
    }

    private String requestBody(HttpServletRequest request) {
        if (!(request instanceof ContentCachingRequestWrapper wrapper)) {
            return "";
        }
        byte[] content = wrapper.getContentAsByteArray();
        if (content.length == 0) {
            return "";
        }
        Charset charset = wrapper.getCharacterEncoding() == null
                ? StandardCharsets.UTF_8
                : Charset.forName(wrapper.getCharacterEncoding());
        return new String(content, charset);
    }

    private String redact(String value) {
        String redacted = value.replaceAll("(?i)(\"(?:password|accessToken|refreshToken|encryptedAccessToken|token)\"\\s*:\\s*\")[^\"]*(\")", "$1***$2");
        redacted = redacted.replaceAll("(?i)((?:password|accessToken|refreshToken|encryptedAccessToken|token)=)[^&]*", "$1***");
        return redacted;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return truncate(forwarded.split(",")[0].trim(), 64);
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return truncate(realIp, 64);
        }
        return truncate(request.getRemoteAddr(), 64);
    }

    private String exceptionText(HttpServletResponse response, Exception ex) {
        if (ex != null) {
            return truncate(ex.getClass().getSimpleName() + ": " + ex.getMessage(), 2000);
        }
        if (response.getStatus() >= 400) {
            return "HTTP " + response.getStatus();
        }
        return null;
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        return text.length() <= maxLength ? text : text.substring(0, Math.max(0, maxLength - 3)) + "...";
    }
}
