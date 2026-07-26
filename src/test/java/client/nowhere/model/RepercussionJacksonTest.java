package client.nowhere.model;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RepercussionJacksonTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    void directRepercussionDeserialization_preservesId() throws Exception {
        String json = "{\"repercussionId\":\"7b31b0fc-46ce-497f-b015-729a21db09a5\",\"repercussionType\":\"Trait\",\"repercussionSubmission\":\"DIGIDESTINED\",\"color\":\"#0288d1\"}";
        Repercussion repercussion = objectMapper.readValue(json, Repercussion.class);
        assertEquals("7b31b0fc-46ce-497f-b015-729a21db09a5", repercussion.getRepercussionId());
    }

    @Test
    void repercussionNestedInOutcomeFork_preservesId() throws Exception {
        String json = "{\"textSubmission\":{},\"repercussions\":[{\"repercussionId\":\"7b31b0fc-46ce-497f-b015-729a21db09a5\",\"repercussionType\":\"Trait\",\"repercussionSubmission\":\"DIGIDESTINED\",\"color\":\"#0288d1\"}]}";
        OutcomeFork fork = objectMapper.readValue(json, OutcomeFork.class);
        assertEquals("7b31b0fc-46ce-497f-b015-729a21db09a5", fork.getRepercussions().get(0).getRepercussionId());
    }

    @Test
    void repercussionNestedInOption_preservesId() throws Exception {
        String json = "{\"optionId\":\"0b973710-ee11-4fb3-9156-b6d65587fe7a\",\"outcomeForks\":[{\"textSubmission\":{},\"repercussions\":[{\"repercussionId\":\"7b31b0fc-46ce-497f-b015-729a21db09a5\",\"repercussionType\":\"Trait\",\"repercussionSubmission\":\"DIGIDESTINED\",\"color\":\"#0288d1\"}]}]}";
        Option option = objectMapper.readValue(json, Option.class);
        assertEquals("7b31b0fc-46ce-497f-b015-729a21db09a5", option.getOutcomeForks().get(0).getRepercussions().get(0).getRepercussionId());
    }
}