package com.xperience.hero.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Minimal CORS configuration for local development only, scoped to the API
 * paths and the two Vite dev-server ports this project actually uses.
 * Credentials are not enabled since the host-token/RSVP-token design does not
 * rely on cookies.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:5171", "http://localhost:5172")
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("Content-Type", "X-Host-Token")
                .allowCredentials(false);
    }
}
