package client.nowhere.support;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * Points integration tests at the real deployed backend rather than a local Firestore emulator.
 * Reusing the real, already-authored adventure map (see FullGameScenario) avoids re-deriving
 * correct game content in test fixtures - see the investigation notes on GameFlowScenario tests
 * for why a from-scratch AdventureMap fixture was abandoned in favor of this.
 */
public final class LiveBackend {

    public static final String BASE_URL = "https://nowhere-java-556057816518.us-east4.run.app";

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
        RestTemplate restTemplate = new RestTemplateBuilder().rootUri(BASE_URL).build();
        List<HttpMessageConverter<?>> converters = restTemplate.getMessageConverters();
        converters.replaceAll(converter -> converter instanceof MappingJackson2HttpMessageConverter ? jsonConverter : converter);
        return restTemplate;
    }
}
