package it.mazzoni.vis.config;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class K2SchedulingGuardTest {

    private final JobsProperties schedulingOn = new JobsProperties(true, List.of("NYSE"), Map.of(), true);
    private final JobsProperties schedulingOff = new JobsProperties(true, List.of("NYSE"), Map.of(), false);

    @Test
    void k2RejectsInProcessSchedulingStillEnabled() {
        assertThatThrownBy(() -> K2SchedulingGuard.validate(
                new DeploymentProperties("k2", 3), schedulingOn))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_JOBS_SCHEDULING_ENABLED=false");
    }

    @Test
    void k2AcceptsInProcessSchedulingDisabled() {
        assertThatCode(() -> K2SchedulingGuard.validate(
                new DeploymentProperties("k2", 3), schedulingOff)).doesNotThrowAnyException();
    }

    @Test
    void k2MayScaleBeyondOneInstanceOnceSchedulingIsDisabled() {
        assertThatCode(() -> K2SchedulingGuard.validate(
                new DeploymentProperties("k2", 10), schedulingOff)).doesNotThrowAnyException();
    }

    @Test
    void nonK2ModeDoesNotApplyThisGuard() {
        assertThatCode(() -> K2SchedulingGuard.validate(
                new DeploymentProperties("k1", 1), schedulingOn)).doesNotThrowAnyException();
        assertThatCode(() -> K2SchedulingGuard.validate(
                new DeploymentProperties("local", 1), schedulingOn)).doesNotThrowAnyException();
    }

    @Test
    void jobsEnabledFlagIsIndependentOfSchedulingAndDoesNotAffectThisGuard() {
        JobsProperties schedulingOffButBodyDisabled = new JobsProperties(false, List.of(), Map.of(), false);
        assertThatCode(() -> K2SchedulingGuard.validate(
                new DeploymentProperties("k2", 3), schedulingOffButBodyDisabled)).doesNotThrowAnyException();
    }
}
