package client.nowhere.config;

import com.google.api.core.ApiFutures;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "management.tracing.enabled=true",
        "management.tracing.sampling.probability=1.0"
})
@AutoConfigureMockMvc
@AutoConfigureObservability
@Import({TestFirestoreConfig.class, FirestoreTracingTest.InMemoryExporterConfig.class})
@ActiveProfiles("test")
class FirestoreTracingTest {

    // What Cloud Run's front end sends: a W3C parent that is marked "not sampled" (flags 00).
    private static final String CLOUD_RUN_TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String CLOUD_RUN_TRACEPARENT = "00-" + CLOUD_RUN_TRACE_ID + "-00f067aa0ba902b7-00";

    @TestConfiguration
    static class InMemoryExporterConfig {
        @Bean
        InMemorySpanExporter inMemorySpanExporter() {
            return InMemorySpanExporter.create();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Firestore firestore;

    @Autowired
    private InMemorySpanExporter spanExporter;

    @Autowired
    private SdkTracerProvider tracerProvider;

    @Test
    void daoSpanNestsUnderRequestSpanAndIgnoresCloudRunParent() throws Exception {
        CollectionReference collection = mock(CollectionReference.class);
        DocumentReference document = mock(DocumentReference.class);
        DocumentSnapshot snapshot = mock(DocumentSnapshot.class);
        when(firestore.collection(anyString())).thenReturn(collection);
        when(collection.document(anyString())).thenReturn(document);
        when(document.get()).thenReturn(ApiFutures.immediateFuture(snapshot));
        when(snapshot.exists()).thenReturn(false);

        mockMvc.perform(get("/featureFlags/value")
                        .param("flagName", "anyFlag")
                        .header("traceparent", CLOUD_RUN_TRACEPARENT))
                .andExpect(status().isOk());

        tracerProvider.forceFlush().join(5, TimeUnit.SECONDS);
        List<SpanData> spans = spanExporter.getFinishedSpanItems();

        SpanData daoSpan = spans.stream()
                .filter(span -> span.getName().equals("FeatureFlagDAO.getFlagValue"))
                .findFirst().orElseThrow();
        SpanData requestSpan = spans.stream()
                .filter(span -> span.getSpanId().equals(daoSpan.getParentSpanId()))
                .findFirst().orElseThrow();

        assertEquals("/featureFlags/value", requestSpan.getAttributes().asMap().entrySet().stream()
                .filter(entry -> entry.getKey().getKey().equals("uri"))
                .map(entry -> entry.getValue().toString())
                .findFirst().orElseThrow());
        assertEquals(requestSpan.getTraceId(), daoSpan.getTraceId());
        assertNotEquals(CLOUD_RUN_TRACE_ID, requestSpan.getTraceId());
        assertEquals(false, requestSpan.getParentSpanContext().isValid());
    }
}
