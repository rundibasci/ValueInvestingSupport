package it.mazzoni.vis.jobs;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CloudRunJobEntryPointTest {

    private static CloudRunJob fakeJob(String key, AtomicInteger runs) {
        return new CloudRunJob() {
            @Override
            public String jobKey() {
                return key;
            }

            @Override
            public void run() {
                runs.incrementAndGet();
            }
        };
    }

    @Test
    void invokesExactlyTheRequestedJob() {
        AtomicInteger quoteRuns = new AtomicInteger();
        AtomicInteger alertRuns = new AtomicInteger();
        CloudRunJobEntryPoint entryPoint = new CloudRunJobEntryPoint(
                List.of(fakeJob("quote-refresh", quoteRuns), fakeJob("alert-detection", alertRuns)));

        entryPoint.run(new DefaultApplicationArguments("--job=quote-refresh"));

        org.assertj.core.api.Assertions.assertThat(quoteRuns.get()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(alertRuns.get()).isZero();
    }

    @Test
    void rejectsAnUnknownJobKey() {
        CloudRunJobEntryPoint entryPoint = new CloudRunJobEntryPoint(
                List.of(fakeJob("quote-refresh", new AtomicInteger())));

        assertThatThrownBy(() -> entryPoint.run(new DefaultApplicationArguments("--job=does-not-exist")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does-not-exist");
    }

    @Test
    void isANoOpWithoutAJobArgument() {
        AtomicInteger runs = new AtomicInteger();
        CloudRunJobEntryPoint entryPoint = new CloudRunJobEntryPoint(List.of(fakeJob("quote-refresh", runs)));

        assertThatCode(() -> entryPoint.run(new DefaultApplicationArguments())).doesNotThrowAnyException();
        org.assertj.core.api.Assertions.assertThat(runs.get()).isZero();
    }
}
