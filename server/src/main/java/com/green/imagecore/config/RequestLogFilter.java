package com.green.imagecore.config;

import com.green.imagecore.entities.Log;
import com.green.imagecore.service.LogService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class RequestLogFilter extends OncePerRequestFilter {

    private final LogService logService;

    private static final Set<String> STATIC_PREFIXES = Set.of(
            "/_nuxt/",
            "/favicon.ico",
            "/.well-known/",
            "/assets/",
            "/images/",
            "/css/",
            "/js/"
    );

    private static final Set<String> STATIC_EXTENSIONS = Set.of(
            ".js", ".css", ".map",
            ".png", ".jpg", ".jpeg", ".gif", ".webp", ".svg", ".ico",
            ".woff", ".woff2", ".ttf", ".eot"
    );

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();

        for (String prefix : STATIC_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }

        for (String ext : STATIC_EXTENSIONS) {
            if (path.endsWith(ext)) {
                return true;
            }
        }

        return false;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        long start = System.currentTimeMillis();
        String username = resolveUsername();
        MDC.put("user", username);

        try {
            filterChain.doFilter(request, response);
        } finally {
            int status = response.getStatus();
            String method = request.getMethod();
            String path = request.getRequestURI();
            long duration = System.currentTimeMillis() - start;

            boolean skipHealthCheck =
                    path.equals("/actuator/health")
                            && method.equals("GET")
                            && status >= 200
                            && status < 300;

            if (!skipHealthCheck) {
                String logLevel = resolveLogLevel(status);
                Map<String, String> queryParams = flattenQueryParams(request);

                String message = String.format(
                        "[%d] %s %s (%dms)",
                        status,
                        method,
                        path,
                        duration
                );

                writeApplicationLog(logLevel, message);

                logService.save(
                        Log.builder()
                                .logLevel(logLevel)
                                .username(username)
                                .method(method)
                                .path(path)
                                .status(status)
                                .durationMs((int) duration)
                                .queryParams(queryParams)
                                .build()
                );
            }

            MDC.remove("user");
        }
    }

    private Map<String, String> flattenQueryParams(HttpServletRequest request) {
        return request.getParameterMap().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> String.join(",", Arrays.asList(entry.getValue()))
                ));
    }

    private String resolveLogLevel(int status) {
        if (status >= 500) {
            return "ERROR";
        }

        if (status >= 400) {
            return "WARN";
        }

        return "INFO";
    }

    private void writeApplicationLog(String logLevel, String message) {
        switch (logLevel) {
            case "ERROR":
                log.error(message);
                break;
            case "WARN":
                log.warn(message);
                break;
            default:
                log.info(message);
                break;
        }
    }

    public static String resolveUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated()) {
            return "\\";
        }

        if ("anonymousUser".equals(auth.getPrincipal())) {
            return "\\";
        }

        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken().getSubject();
        }

        return auth.getName();
    }
}