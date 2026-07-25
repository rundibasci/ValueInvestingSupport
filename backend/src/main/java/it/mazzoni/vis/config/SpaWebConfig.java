package it.mazzoni.vis.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Keeps client-side React routes usable when the frontend is bundled into the
 * Spring Boot container. API, Actuator, and static-resource paths are excluded
 * by only forwarding extensionless application routes.
 */
@Configuration
public class SpaWebConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        for (String route : new String[]{
                "/login",
                "/auth/oauth2/callback",
                "/account",
                "/audit",
                "/checklists",
                "/screener",
                "/portfolio",
                "/watchlist",
                "/seed",
                "/universe-curation",
                "/admin/seed",
                "/admin/jobs",
                "/admin/fallbacks",
                "/admin/users",
                "/securities/{symbol}",
                "/securities/{symbol}/review"
        }) {
            registry.addViewController(route).setViewName("forward:/index.html");
        }
    }
}
