package client.nowhere.config;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.Histogram;
import io.micrometer.core.instrument.distribution.NoopHistogram;
import io.micrometer.core.instrument.distribution.StepBucketHistogram;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.step.StepTimer;
import io.micrometer.stackdriver.StackdriverConfig;
import io.micrometer.stackdriver.StackdriverMeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Replaces Spring Boot's StackdriverMeterRegistry so timer histograms (the SLO buckets on
 * http.server.requests and firestore.dao) cover the same window as the timer's count.
 *
 * Micrometer 1.12's Stackdriver timers count per publish step but fill buckets on a rolling
 * ~2 minute window, so a point can report count=6 with buckets summing to 20, and Cloud Monitoring
 * rejects it ("bucket_counts fields that sum to 20 which does not equal the count"). Here each
 * timer uses a StepBucketHistogram that rolls over together with the count. Micrometer 1.15 fixes
 * this upstream; this class can go once Spring Boot brings in that version.
 */
@Configuration
@ConditionalOnProperty(name = "management.stackdriver.metrics.export.enabled", havingValue = "true")
public class StackdriverMetricsConfig {

    @Bean
    public StackdriverMeterRegistry stackdriverMeterRegistry(StackdriverConfig config, Clock clock) {
        return new StepAlignedStackdriverMeterRegistry(config, clock);
    }

    static class StepAlignedStackdriverMeterRegistry extends StackdriverMeterRegistry {

        private final long stepMillis;

        StepAlignedStackdriverMeterRegistry(StackdriverConfig config, Clock clock) {
            super(config, clock);
            this.stepMillis = config.step().toMillis();
        }

        @Override
        protected Timer newTimer(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig,
                PauseDetector pauseDetector) {
            Histogram histogram = distributionStatisticConfig.isPublishingHistogram()
                    ? new StepBucketHistogram(clock, stepMillis, distributionStatisticConfig, false, true)
                    : NoopHistogram.INSTANCE;
            return new StepAlignedTimer(id, clock, distributionStatisticConfig, pauseDetector, getBaseTimeUnit(),
                    stepMillis, histogram);
        }
    }

    static class StepAlignedTimer extends StepTimer {

        private final StepBucketHistogram stepHistogram;

        StepAlignedTimer(Id id, Clock clock, DistributionStatisticConfig distributionStatisticConfig,
                PauseDetector pauseDetector, TimeUnit baseTimeUnit, long stepMillis, Histogram histogram) {
            super(id, clock, distributionStatisticConfig, pauseDetector, baseTimeUnit, stepMillis, histogram);
            this.stepHistogram = histogram instanceof StepBucketHistogram ? (StepBucketHistogram) histogram : null;
        }

        /**
         * The registry rolls step meters over at each step boundary by calling count(), which only
         * rolls the count. Rolling the histogram at the same moment keeps calls made between the
         * boundary and the publish out of the previous step's buckets.
         */
        @Override
        public long count() {
            if (stepHistogram != null) {
                stepHistogram.poll();
            }
            return super.count();
        }
    }
}
