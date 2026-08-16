package client.nowhere.support;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.cloud.Timestamp;

import java.io.IOException;

/**
 * Model classes carry several com.google.cloud.Timestamp fields that aren't @JsonIgnore'd
 * (e.g. PlayerVote.votedAt). Timestamp has no default constructor Jackson can use, so a vanilla
 * ObjectMapper fails to deserialize any response containing one. The server's own default bean
 * serialization emits it as {"seconds": <long>, "nanos": <int>} (Timestamp's only "getX"-shaped
 * getters), so this module reads/writes that same minimal shape.
 */
public class TimestampModule extends SimpleModule {

    public TimestampModule() {
        addSerializer(Timestamp.class, new JsonSerializer<>() {
            @Override
            public void serialize(Timestamp value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                gen.writeStartObject();
                gen.writeNumberField("seconds", value.getSeconds());
                gen.writeNumberField("nanos", value.getNanos());
                gen.writeEndObject();
            }
        });
        addDeserializer(Timestamp.class, new JsonDeserializer<>() {
            @Override
            public Timestamp deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                JsonNode node = p.getCodec().readTree(p);
                long seconds = node.path("seconds").asLong(0);
                int nanos = node.path("nanos").asInt(0);
                return Timestamp.ofTimeSecondsAndNanos(seconds, nanos);
            }
        });
    }
}
