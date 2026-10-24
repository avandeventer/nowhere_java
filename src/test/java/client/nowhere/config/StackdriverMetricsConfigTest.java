package client.nowhere.config;

import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.CountAtBucket;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.stackdriver.StackdriverConfig;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StackdriverMetricsConfigTest {

    private static final Duration STEP = Duration.ofMinutes(1);

    private final MockClock clock = new MockClock();

    private final StackdriverConfig config = new StackdriverConfig() {
        @Override
        public String get(String key) {
            return null;
        }

        @Override
        public String projectId() {
            return "test-project";
        }

        @Override
        public boolean enabled() {
            return false;
        }

        @Override
        public Duration step() {
            return STEP;
        }
    };

    @Test
    void histogramBucketsCoverTheSameStepAsTheCount() {
        StackdriverMetricsConfig.StepAlignedStackdriverMeterRegistry registry =
                new StackdriverMetricsConfig.StepAlignedStackdriverMeterRegistry(config, clock);
        Timer timer = Timer.builder("firestore.dao")
                .serviceLevelObjectives(Duration.ofMillis(50), Duration.ofMillis(250))
                .register(registry);

        // Mirrors the registry: at each step boundary it rolls meters over by calling count(), and
        // it publishes a little later in the step. Calls made between the boundary and the publish
        // belong to the new step and must not leak into the published buckets.
        timer.record(Duration.ofMillis(10));
        timer.record(Duration.ofMillis(100));
        timer.record(Duration.ofMillis(400));
        clock.add(STEP);
        timer.count();
        timer.record(Duration.ofMillis(30));
        clock.add(Duration.ofSeconds(5));
        assertSnapshot(timer.takeSnapshot(), 3, 1, 2);

        clock.add(STEP.minusSeconds(5));
        timer.count();
        clock.add(Duration.ofSeconds(5));
        assertSnapshot(timer.takeSnapshot(), 1, 1, 1);
    }

    private static void assertSnapshot(HistogramSnapshot snapshot, long count, double atMost50ms, double atMost250ms) {
        CountAtBucket[] buckets = snapshot.histogramCounts();
        assertEquals(count, snapshot.count());
        assertEquals(atMost50ms, buckets[0].count(), "calls <= 50ms in the last step");
        assertEquals(atMost250ms, buckets[1].count(), "calls <= 250ms in the last step (cumulative)");
    }
}
