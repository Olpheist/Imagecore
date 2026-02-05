package com.green.imagecore.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

@Profile("prod")
@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) {
                        try {
                            // exact file match first (js/css/images)
                            Resource exact = location.createRelative(resourcePath);
                            if (exact.exists() && exact.isReadable()) return exact;

                            // normalize
                            String p = resourcePath;
                            if (p == null || p.isBlank()) p = "index.html";

                            // forward correct routes for production
                            if (!p.contains(".") || p.endsWith("/")) {
                                Resource index = location.createRelative(p + "/index.html");
                                if (index.exists() && index.isReadable()) return index;
                            }

                            // custom 404 fallback from nuxt
                            Resource notFound = location.createRelative("404.html");
                            if (notFound.exists() && notFound.isReadable()) return notFound;

                            return null;
                        } catch (Exception e) {
                            return null;
                        }
                    }
                });
    }
}
