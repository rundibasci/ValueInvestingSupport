package it.mazzoni.vis.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class ObservabilitySupport {

    public static final String OUTCOME_SUCCESS = "success";
    public static final String OUTCOME_ERROR = "error";

    private final MeterRegistry meterRegistry;

    public ObservabilitySupport(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @FunctionalInterface
    public interface ThrowingCallable<T> {
        T call() throws Throwable;
    }

    public <T> T time(String metricName, Iterable<Tag> baseTags, Callable<T> callable) throws Exception {
        try {
            return timeThrowing(metricName, baseTags, callable::call);
        } catch (Exception e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    public <T> T timeThrowing(String metricName, Iterable<Tag> baseTags, ThrowingCallable<T> callable) throws Throwable {
        long start = System.nanoTime();
        String outcome = OUTCOME_SUCCESS;
        String error = "none";
        try {
            return callable.call();
        } catch (Throwable e) {
            outcome = OUTCOME_ERROR;
            error = safeError(e);
            throw e;
        } finally {
            recordTime(metricName, baseTags, outcome, error, System.nanoTime() - start);
        }
    }

    public void recordTime(String metricName, Iterable<Tag> baseTags, String outcome, String error, long durationNanos) {
        Timer.builder(metricName)
                .tags(withOutcome(baseTags, outcome, error))
                .register(meterRegistry)
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }

    public void count(String metricName, Iterable<Tag> tags) {
        Counter.builder(metricName)
                .tags(tags)
                .register(meterRegistry)
                .increment();
    }

    public Iterable<Tag> tags(String... keyValues) {
        List<Tag> tags = new ArrayList<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            tags.add(Tag.of(keyValues[i], safeValue(keyValues[i + 1])));
        }
        return tags;
    }

    public static String safeError(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getClass().getSimpleName().contains("InvocationTarget")) {
            current = current.getCause();
        }
        return current.getClass().getSimpleName();
    }

    public static String safeValue(String value) {
        if (value == null || value.isBlank()) {
            return "none";
        }
        String normalized = value.toLowerCase().replaceAll("[^a-z0-9._-]", "-");
        return normalized.length() > 64 ? normalized.substring(0, 64) : normalized;
    }

    private Iterable<Tag> withOutcome(Iterable<Tag> baseTags, String outcome, String error) {
        List<Tag> tags = new ArrayList<>();
        baseTags.forEach(tags::add);
        tags.add(Tag.of("outcome", outcome));
        tags.add(Tag.of("error", safeValue(error)));
        return tags;
    }

    public <T> Supplier<T> timedSupplier(String metricName, Iterable<Tag> tags, Supplier<T> supplier) {
        return () -> {
            try {
                return time(metricName, tags, supplier::get);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        };
    }
}
