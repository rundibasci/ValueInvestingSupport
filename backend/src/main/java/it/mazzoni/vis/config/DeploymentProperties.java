package it.mazzoni.vis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.deployment")
public record DeploymentProperties(String mode, int declaredMaxInstances) {

    public boolean isK1() {
        return "k1".equalsIgnoreCase(mode);
    }

    /**
     * K2 Cloud Run API service: background work runs exclusively through
     * Cloud Run Jobs (see {@code CloudRunJobEntryPoint}), never in-process,
     * so this mode is not subject to K1's single-instance constraint.
     */
    public boolean isK2() {
        return "k2".equalsIgnoreCase(mode);
    }
}
