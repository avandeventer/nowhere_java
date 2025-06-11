package client.nowhere.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.util.Assert;

import java.util.List;

import static org.assertj.core.api.Assertions.fail;

public class StoryTest {

    @Test
    public void testRitualDeserializationWithRitualOnly() throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        String json = """
        {
            "gameCode": "6USV9R",
            "ritualOptions": [
                { "optionId": "1", "selectedByPlayerId": "7d79f617-c56a-4e34-aad7-4b4a77eb2019" }
            ]
        }
        """;

        Story story = objectMapper.readValue(json, Story.class);

        List<Option> options = story.getOptions();
        Assert.notNull(options, "Options should not be null");
        Assert.notEmpty(options, "Options should not be empty");
        Assert.isTrue("1".equals(options.get(0).getOptionId()), "Option ID should be 1");
    }
}
