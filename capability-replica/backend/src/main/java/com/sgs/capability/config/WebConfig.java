package com.sgs.capability.config;

import com.sgs.capability.security.AuthorizationInterceptor;
import com.sgs.capability.security.AuditLogInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Keeps local frontend calls close to the original CORS setup. */
@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final AuthorizationInterceptor authorizationInterceptor;
    private final AuditLogInterceptor auditLogInterceptor;
    private final String serverRootAddress;
    private final String corsAllowedOriginPatterns;

    public WebConfig(AuthorizationInterceptor authorizationInterceptor,
                     AuditLogInterceptor auditLogInterceptor,
                     @Value("${app.server-root-address:}") String serverRootAddress,
                     @Value("${app.cors.allowed-origin-patterns:http://localhost:*,http://127.0.0.1:*}") String corsAllowedOriginPatterns) {
        this.authorizationInterceptor = authorizationInterceptor;
        this.auditLogInterceptor = auditLogInterceptor;
        this.serverRootAddress = serverRootAddress;
        this.corsAllowedOriginPatterns = corsAllowedOriginPatterns;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(allowedOriginPatterns())
                .allowedMethods("*")
                .allowedHeaders("*")
                .allowCredentials(true);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(auditLogInterceptor);
        registry.addInterceptor(authorizationInterceptor);
    }

    private String[] allowedOriginPatterns() {
        List<String> patterns = new ArrayList<>();
        Arrays.stream(corsAllowedOriginPatterns.split(","))
                .map(String::trim)
                .filter(pattern -> !pattern.isEmpty())
                .forEach(patterns::add);
        serverRootOrigin().ifPresent(origin -> {
            if (!patterns.contains(origin)) {
                patterns.add(origin);
            }
        });
        return patterns.toArray(String[]::new);
    }

    private java.util.Optional<String> serverRootOrigin() {
        if (serverRootAddress == null || serverRootAddress.isBlank()) {
            return java.util.Optional.empty();
        }
        try {
            URI uri = URI.create(serverRootAddress);
            if (uri.getScheme() == null || uri.getHost() == null) {
                return java.util.Optional.empty();
            }
            int port = uri.getPort();
            String origin = uri.getScheme() + "://" + uri.getHost()
                    + (port > -1 ? ":" + port : "");
            return java.util.Optional.of(origin);
        } catch (IllegalArgumentException ex) {
            return java.util.Optional.empty();
        }
    }
}
