package com.green.imagecore.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Slf4j
@Configuration
public class RequestLogFilter extends OncePerRequestFilter {

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
            if (path.startsWith(prefix)) return true;
        }

        for (String ext : STATIC_EXTENSIONS) {
            if (path.endsWith(ext)) return true;
        }

        return false;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String username = resolveUsername();

        // Put user into MDC so every log line can use it
        MDC.put("user", username);

        try {
            log.info(">>> {} {} from {}",
                    request.getMethod(),
                    request.getRequestURI(),
                    request.getRemoteAddr());

            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("user");
        }
    }

    private String resolveUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated()) return "\\";
        if ("anonymousUser".equals(auth.getPrincipal())) return "\\";

        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken().getSubject();
        }

        return auth.getName();
    }
}
