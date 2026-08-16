package client.nowhere.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/**
 * Points the real Firestore client at a locally running Firestore emulator instead of prod,
 * so DAOs exercise genuine read-after-write persistence across a full HTTP-driven game.
 * See src/integrationTest/README.md for how to start the emulator.
 */
@TestConfiguration
@Profile("integration")
public class FirestoreEmulatorConfig {

    public static final String DEFAULT_EMULATOR_HOST = "localhost:8080";

    @Bean
    @Primary
    public Firestore firestore() {
        String emulatorHost = System.getenv().getOrDefault("FIRESTORE_EMULATOR_HOST", DEFAULT_EMULATOR_HOST);
        FirestoreOptions options = FirestoreOptions.newBuilder()
                .setProjectId("nowhere-integration-test")
                .setEmulatorHost(emulatorHost)
                .build();
        return options.getService();
    }

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        SimpleModule module = new SimpleModule();
        module.addDeserializer(Timestamp.class, new TimestampDeserializer());
        objectMapper.registerModule(module);
        return objectMapper;
    }
}
