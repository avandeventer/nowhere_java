package client.nowhere.config;

import com.google.cloud.opentelemetry.trace.TraceConfiguration;
import com.google.cloud.opentelemetry.trace.TraceExporter;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Sends spans to Google Cloud Trace using the service's own credentials (Application Default
 * Credentials), so no collector or API key is needed. Spring Boot picks up any SpanExporter bean
 * and batches spans into it.
 */
@Configuration
@Profile("!test & !integration")
@ConditionalOnProperty(name = "management.tracing.enabled", havingValue = "true")
public class CloudTraceConfig {

    @Bean
    public SpanExporter cloudTraceSpanExporter(@Value("${gcp.project-id}") String projectId) {
        return TraceExporter.createWithConfiguration(
                TraceConfiguration.builder().setProjectId(projectId).build());
    }
}
