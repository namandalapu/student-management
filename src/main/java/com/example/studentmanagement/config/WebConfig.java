package com.example.studentmanagement.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * Web configuration for the single-project setup.
 *
 * <p>The Angular UI is built into {@code classpath:/static} and served by this same Spring Boot
 * application, so the UI and the REST API share one origin (http://localhost:8080).
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private static final String UI_LOCATION = "classpath:/static/";
    private static final String UI_INDEX = "static/index.html";

    private final String[] allowedOrigins;

    public WebConfig(@Value("${app.cors.allowed-origins}") String[] allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    /** CORS is only needed when the UI runs on the Angular dev server (ng serve on port 4200). */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS");
    }

    /**
     * Serves the built Angular files, and falls back to index.html for client-side routes such as
     * /students/3/edit so that refreshing the page or opening a deep link works. Requests under
     * /api and requests for missing files (anything with a file extension) still return 404.
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations(UI_LOCATION)
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        if (resourcePath.equals("api") || resourcePath.startsWith("api/")
                                || resourcePath.contains(".")) {
                            return null;
                        }
                        Resource index = new ClassPathResource(UI_INDEX);
                        return index.exists() ? index : null;
                    }
                });
    }
}
