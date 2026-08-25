package it.mazzoni.vis.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class K1DeploymentGuard implements ApplicationRunner {

    private final DeploymentProperties deployment;
    private final JobsProperties jobs;

    public K1DeploymentGuard(DeploymentProperties deployment, JobsProperties jobs) {
        this.deployment = deployment;
        this.jobs = jobs;
    }

    @Override
    public void run(ApplicationArguments args) {
        validate(deployment, jobs);
    }

    static void validate(DeploymentProperties deployment, JobsProperties jobs) {
        if (deployment.isK1() && jobs.enabled() && deployment.declaredMaxInstances() != 1) {
            throw new IllegalStateException(
                    "K1 deployment rejected: in-process scheduled jobs require APP_DECLARED_MAX_INSTANCES=1");
        }
    }
}
