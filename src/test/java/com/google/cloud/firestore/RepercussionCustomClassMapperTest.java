package com.google.cloud.firestore;

import client.nowhere.model.Option;
import client.nowhere.model.OutcomeFork;
import client.nowhere.model.Repercussion;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Lives in com.google.cloud.firestore on purpose: CustomClassMapper and its
 * serialize()/convertToCustomClass() methods are package-private, and this is the only way to
 * invoke them directly without standing up a live Firestore connection or emulator.
 */
public class RepercussionCustomClassMapperTest {

    @Test
    void directRepercussionRoundTrip_preservesId() {
        Repercussion repercussion = new Repercussion();
        repercussion.setRepercussionId("7b31b0fc-46ce-497f-b015-729a21db09a5");
        repercussion.setRepercussionType("Trait");
        repercussion.setRepercussionSubmission("DIGIDESTINED");

        Object serialized = CustomClassMapper.serialize(repercussion);
        System.out.println("Serialized map: " + serialized);

        Repercussion roundTripped = CustomClassMapper.convertToCustomClass(serialized, Repercussion.class, null);
        assertEquals("7b31b0fc-46ce-497f-b015-729a21db09a5", roundTripped.getRepercussionId());
    }

    @Test
    void repercussionNestedInOutcomeFork_roundTrip_preservesId() {
        Repercussion repercussion = new Repercussion();
        repercussion.setRepercussionId("7b31b0fc-46ce-497f-b015-729a21db09a5");
        repercussion.setRepercussionType("Trait");
        repercussion.setRepercussionSubmission("DIGIDESTINED");

        OutcomeFork fork = new OutcomeFork();
        fork.setRepercussions(java.util.List.of(repercussion));

        Object serialized = CustomClassMapper.serialize(fork);
        System.out.println("Serialized fork map: " + serialized);

        OutcomeFork roundTripped = CustomClassMapper.convertToCustomClass(serialized, OutcomeFork.class, null);
        assertEquals("7b31b0fc-46ce-497f-b015-729a21db09a5", roundTripped.getRepercussions().get(0).getRepercussionId());
    }

    @Test
    void repercussionNestedInOption_roundTrip_preservesId() {
        Repercussion repercussion = new Repercussion();
        repercussion.setRepercussionId("7b31b0fc-46ce-497f-b015-729a21db09a5");
        repercussion.setRepercussionType("Trait");
        repercussion.setRepercussionSubmission("DIGIDESTINED");

        OutcomeFork fork = new OutcomeFork();
        fork.setRepercussions(java.util.List.of(repercussion));

        Option option = new Option();
        option.setOptionId("0b973710-ee11-4fb3-9156-b6d65587fe7a");
        option.setOutcomeForks(java.util.List.of(fork));

        Object serialized = CustomClassMapper.serialize(option);
        System.out.println("Serialized option map: " + serialized);

        Option roundTripped = CustomClassMapper.convertToCustomClass(serialized, Option.class, null);
        assertEquals("7b31b0fc-46ce-497f-b015-729a21db09a5",
                roundTripped.getOutcomeForks().get(0).getRepercussions().get(0).getRepercussionId());
    }
}