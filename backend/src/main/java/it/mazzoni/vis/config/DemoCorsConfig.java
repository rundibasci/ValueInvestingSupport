package it.mazzoni.vis.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@Profile("demo")
public class DemoCorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // file:// pages send Origin: null — allowedOriginPatterns("*") matches it
        registry.addMapping("/demo/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET");
    }
}
