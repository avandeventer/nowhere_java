package client.nowhere.util;

import client.nowhere.model.GameSession;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.cloud.Timestamp;

import java.io.File;
import java.io.IOException;

/**
 * Utility class for loading test data from JSON files in test resources.
 */
public class TestJsonLoader {

    private static final ObjectMapper objectMapper;

    static {
        objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // Register custom deserializer for Google Cloud Timestamp
        SimpleModule module = new SimpleModule();
        module.addDeserializer(Timestamp.class, new TimestampDeserializer());
        objectMapper.registerModule(module);
    }

    /**
     * Custom deserializer for com.google.cloud.Timestamp.
     * Handles JSON objects with "seconds" and "nanos" fields.
     */
    private static class TimestampDeserializer extends JsonDeserializer<Timestamp> {
        @Override
        public Timestamp deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode node = p.getCodec().readValue(p, JsonNode.class);
            long seconds = node.has("seconds") ? node.get("seconds").asLong() : 0;
            int nanos = node.has("nanos") ? node.get("nanos").asInt() : 0;
            return Timestamp.ofTimeSecondsAndNanos(seconds, nanos);
        }
    }

    /**
     * Loads a GameSession from a JSON file in test resources.
     * @param fileName The name of the JSON file (without path)
     * @return The deserialized GameSession
     */
    public static GameSession loadGameSessionFromJson(String fileName) throws IOException {
        File jsonFile = new File("src/test/resources/" + fileName);
        return objectMapper.readValue(jsonFile, GameSession.class);
    }
}
