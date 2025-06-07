package client.nowhere.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.util.Assert;

import static org.assertj.core.api.Assertions.fail;

public class StoryTest {

    @Test
    public void testRitualDeserialization () {
        ObjectMapper objectMapper = new ObjectMapper();
        String json = "{ \"gameCode\": \"6USV9R\", \"ritualOptions\": [ { \"optionType\": \"ritual\", \"optionId\": \"1\", \"selectedByPlayerId\": \"7d79f617-c56a-4e34-aad7-4b4a77eb2019\" } ] }";

        try {
            RitualStory story = objectMapper.readValue(json, RitualStory.class);
            Assert.isInstanceOf(RitualOption.class, story.getRitualOptions().get(0));
        } catch (JsonProcessingException exception) {
            fail("You should be able to deserialize this as a RitualStory", exception);
        }
    }
}
