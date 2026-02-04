package com.green.imagecore.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class StaticForwardController {

    private final ResourceLoader resourceLoader;

    public StaticForwardController(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    private static final String[] EXCLUDED = {
            "/api",
            "/actuator",
            "/login",
            "/logout",
            "/oauth2",
            "/saml2"
    };

    /**
     * Serves statically pre-rendered Nuxt pages generated via {@code nuxi generate}.
     * Only used in prod for static pages
    */
    @GetMapping({
            "/",
            "/**/{path:[^\\.]*}"
    })
    public ResponseEntity<Resource> servePrerendered(HttpServletRequest request) {
        String uri = request.getRequestURI();

        // normalize
        if (uri == null || uri.isBlank()) uri = "/";
        if (uri.endsWith("/")) uri = uri.substring(0, uri.length() - 1);
        if (uri.isBlank()) uri = "/";

        // exclude backend routes
        if (isExcluded(uri)) {
            return ResponseEntity.notFound().build();
        }

        // resolve file
        String path = uri.equals("/")
                ? "classpath:/static/index.html"
                : "classpath:/static" + uri + "/index.html";

        Resource resource = resourceLoader.getResource(path);

        if (resource.exists() && resource.isReadable()) {
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(resource);
        }

        // fallback
        Resource notFound = resourceLoader.getResource("classpath:/static/404.html");
        return ResponseEntity.status(404)
                .contentType(MediaType.TEXT_HTML)
                .body(notFound);
    }

    private boolean isExcluded(String uri) {
        for (String prefix : EXCLUDED) {
            if (uri.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
