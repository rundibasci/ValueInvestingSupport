package it.mazzoni.vis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.deployment")
public record DeploymentProperties(String mode, int declaredMaxInstances) {

    public boolean isK1() {
        return "k1".equalsIgnoreCase(mode);
    }
}
