package client.nowhere.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TraitTypeJacksonTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void traitTypeSerializesAsObjectWithNameAndColor() throws Exception {
        Trait trait = new Trait("hello", TraitType.TITLE);

        String json = objectMapper.writeValueAsString(trait);

        assertEquals(
                "{\"traitId\":\"" + trait.getTraitId() + "\",\"traitLabel\":\"hello\",\"traitType\":{\"name\":\"Title\",\"color\":\"#7b1fa2\"},\"textSubmission\":null}",
                json
        );
    }

    @Test
    void legacyPlainStringTraitTypeStillDeserializes() throws Exception {
        String legacyJson = "{\"traitId\":\"x\",\"traitLabel\":\"y\",\"traitType\":\"STANDARD\"}";

        Trait trait = objectMapper.readValue(legacyJson, Trait.class);

        assertEquals(TraitType.STANDARD, trait.getTraitType());
    }

    @Test
    void objectShapedTraitTypeRoundTrips() throws Exception {
        Trait original = new Trait("hello", TraitType.DESTINY);

        String json = objectMapper.writeValueAsString(original);
        Trait roundTripped = objectMapper.readValue(json, Trait.class);

        assertEquals(TraitType.DESTINY, roundTripped.getTraitType());
    }

    @Test
    void traitTypeValuesArraySerializesAsObjects() throws Exception {
        String json = objectMapper.writeValueAsString(TraitType.values());

        assertEquals(
                "[{\"name\":\"Trait\",\"color\":\"#0288d1\"},{\"name\":\"Title\",\"color\":\"#7b1fa2\"},{\"name\":\"Companion\",\"color\":\"#E60000\"},{\"name\":\"Relationship\",\"color\":\"#E981AE\"},{\"name\":\"Destiny\",\"color\":\"#FFD700\"}]",
                json
        );
    }
}
