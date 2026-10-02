package com.capstone.dsaplatform.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * The Vite dev server (5173) and Spring Boot (8080) are different origins,
 * so the browser blocks API calls unless the backend explicitly allows the frontend.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    // Read from application.yml so a different frontend port doesn't need a code change.
    private final String allowedOrigin;

    public CorsConfig(@Value("${app.cors.allowed-origin}") String allowedOrigin) {
        this.allowedOrigin = allowedOrigin;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // Only /api/** and only the methods the contract uses: least privilege.
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigin)
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("*");
    }
}
