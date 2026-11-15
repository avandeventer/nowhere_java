package client.nowhere.support;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;

/**
 * Points integration tests at the real deployed backend rather than a local Firestore emulator.
 * Reusing the real, already-authored adventure map (see FullGameScenario) avoids re-deriving
 * correct game content in test fixtures - see the investigation notes on GameFlowScenario tests
 * for why a from-scratch AdventureMap fixture was abandoned in favor of this.
 */
public final class LiveBackend {

    public static final String BASE_URL = "https://nowhere-java-556057816518.us-east4.run.app";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(60);

    private LiveBackend() { }

    /**
     * Model classes are deserialized directly (see GameFlowClient) rather than as generic Maps,
     * so the client-side ObjectMapper needs the same com.google.cloud.Timestamp support the
     * server has - see TimestampModule.
     */
    public static RestTemplate restTemplate() {
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new TimestampModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        MappingJackson2HttpMessageConverter jsonConverter = new MappingJackson2HttpMessageConverter(objectMapper);
        // Without a read timeout, a request that stalls in the network (never reaching Cloud Run)
        // hung a run for ~37 minutes until the connection reset. 60s is well above the slowest
        // real call LatencyLog has seen (~16s PUT /game/next near the Firestore size limit).
        RestTemplate restTemplate = new RestTemplateBuilder()
                .rootUri(BASE_URL)
                .setConnectTimeout(CONNECT_TIMEOUT)
                .setReadTimeout(READ_TIMEOUT)
                .build();
        List<HttpMessageConverter<?>> converters = restTemplate.getMessageConverters();
        converters.replaceAll(converter -> converter instanceof MappingJackson2HttpMessageConverter ? jsonConverter : converter);
        return restTemplate;
    }
}
