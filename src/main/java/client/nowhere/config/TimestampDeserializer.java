package client.nowhere.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.cloud.Timestamp;

import java.io.IOException;

public class TimestampDeserializer extends JsonDeserializer<Timestamp> {

    @Override
    public Timestamp deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonNode node = parser.getCodec().readTree(parser);
        long seconds = node.has("seconds") ? node.get("seconds").asLong() : 0;
        int nanos = node.has("nanos") ? node.get("nanos").asInt() : 0;
        return Timestamp.ofTimeSecondsAndNanos(seconds, nanos);
    }
}