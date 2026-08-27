package it.mazzoni.vis.config;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class K1DeploymentGuardTest {

    private final JobsProperties enabledJobs = new JobsProperties(true, List.of("NYSE"), Map.of(), true);

    @Test
    void k1AcceptsExactlyOneDeclaredInstance() {
        assertThatCode(() -> K1DeploymentGuard.validate(
                new DeploymentProperties("k1", 1), enabledJobs)).doesNotThrowAnyException();
    }

    @Test
    void k1RejectsMultipleInstancesWhileJobsAreInProcess() {
        assertThatThrownBy(() -> K1DeploymentGuard.validate(
                new DeploymentProperties("k1", 2), enabledJobs))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_DECLARED_MAX_INSTANCES=1");
    }

    @Test
    void nonK1ModeDoesNotApplyTemporaryScalingGuard() {
        assertThatCode(() -> K1DeploymentGuard.validate(
                new DeploymentProperties("local", 4), enabledJobs)).doesNotThrowAnyException();
    }

    @Test
    void k1MayScaleOnlyWhenInProcessJobsAreDisabled() {
        JobsProperties disabledJobs = new JobsProperties(false, List.of(), Map.of(), true);

        assertThatCode(() -> K1DeploymentGuard.validate(
                new DeploymentProperties("k1", 3), disabledJobs)).doesNotThrowAnyException();
    }
}
