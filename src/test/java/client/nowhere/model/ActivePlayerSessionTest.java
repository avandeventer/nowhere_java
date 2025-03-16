package client.nowhere.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.util.Assert;

import static org.assertj.core.api.Assertions.fail;

public class ActivePlayerSessionTest {

    @Test
    public void testRitualDeserialization () {
        ObjectMapper objectMapper = new ObjectMapper();
        String json = "{\n" +
                "  \"playerId\": \"406df369-8c63-4962-ae19-a2e120f1c9d6\",\n" +
                "  \"playerChoiceOptionId\": \"4\",\n" +
                "  \"ritualStory\": {\n" +
                "    \"gameCode\": \"UW6M41\",\n" +
                "    \"ritualOptions\": [\n" +
                "      {\n" +
                "        \"optionId\": \"4\",\n" +
                "        \"selectedByPlayerId\": \"406df369-8c63-4962-ae19-a2e120f1c9d6\"\n" +
                "      }\n" +
                "    ]\n" +
                "  },\n" +
                "  \"selectedLocationOptionId\": \"\",\n" +
                "  \"setNextPlayerTurn\": false,\n" +
                "  \"gameCode\": \"UW6M41\",\n" +
                "  \"outcomeDisplay\": [],\n" +
                "  \"locationOutcomeDisplay\": []\n" +
                "}";

        try {
            ActivePlayerSession activePlayerSession = objectMapper.readValue(json, ActivePlayerSession.class);
            Assert.isInstanceOf(RitualStory.class, activePlayerSession.getRitualStory());
            Assert.isInstanceOf(RitualOption.class, activePlayerSession.getRitualStory().getRitualOptions().get(0));
        } catch (JsonProcessingException exception) {
            fail("You should be able to deserialize this as a RitualStory", exception);
        }
    }

}
