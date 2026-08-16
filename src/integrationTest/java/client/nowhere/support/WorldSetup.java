package client.nowhere.support;

import client.nowhere.model.AdventureMap;
import client.nowhere.model.GameSessionDisplay;
import client.nowhere.model.Location;
import client.nowhere.model.Option;
import client.nowhere.model.OutcomeFork;
import client.nowhere.model.StatType;
import client.nowhere.model.Story;
import client.nowhere.model.TextSubmission;

import java.util.List;
import java.util.UUID;

/**
 * Builds the minimal world content a DUNGEON_MODE game needs to exist before it can be created:
 * an AdventureMap with one startingLocation carrying startingStories, which round zero
 * (GameSessionHelper.handleRoundZeroBoard) reads to build the tutorial board.
 */
public class WorldSetup {

    public static AdventureMap buildAdventureMap() {
        AdventureMap adventureMap = new AdventureMap();
        adventureMap.setName("Integration Test World");
        adventureMap.setStatTypes(List.of(
                new StatType("strength", "Strength"),
                new StatType("dexterity", "Dexterity")
        ));
        adventureMap.setLocations(List.of(buildStartingLocation()));
        adventureMap.setGameSessionDisplay(buildGameSessionDisplay());
        return adventureMap;
    }

    private static GameSessionDisplay buildGameSessionDisplay() {
        GameSessionDisplay display = new GameSessionDisplay(
                "A small world built for an integration test.",
                "Survive the encounters and reach the end.",
                "The test world settles into its ending."
        );
        display.setPlayerTitle("The Testers");
        display.setPlayerDescription("A handful of travelers exploring a world built for one purpose.");
        display.setMidwayDescription("The journey continues.");
        display.setSuccessText("Everything worked as expected.");
        display.setNeutralText("Everything worked, more or less.");
        display.setFailureText("Something did not work as expected.");
        display.setEntity("the test world");
        return display;
    }

    private static Location buildStartingLocation() {
        Location location = new Location();
        location.setLabel("The Old Mill");
        location.setDescription("A crumbling mill at the edge of town, where every story begins.");
        location.setStartingLocation(true);
        location.setStartingStories(List.of(
                buildStartingStory("A wolf slips out of the treeline and watches you."),
                buildStartingStory("The mill's wheel groans, though there is no wind.")
        ));
        return location;
    }

    private static Story buildStartingStory(String prompt) {
        Story story = new Story();
        story.setPrompt(prompt);
        story.setOptions(List.of(
                simpleOption("Approach carefully"),
                simpleOption("Turn and run")
        ));
        return story;
    }

    // A single OutcomeFork auto-resolves (CollaborativeTextHelper.handleMakeChoice), matching the
    // round-zero tutorial design where DESTINY is awarded without a real MAKE_OUTCOME_CHOICE_VOTING.
    private static Option simpleOption(String optionText) {
        Option option = new Option();
        option.setOptionText(optionText);
        option.setSuccessText(optionText + " - and it works out.");

        TextSubmission resolution = new TextSubmission();
        resolution.setSubmissionId(UUID.randomUUID().toString());
        resolution.setCurrentText(optionText + " - and it works out.");
        resolution.setOriginalText(optionText + " - and it works out.");

        option.setOutcomeForks(List.of(new OutcomeFork(resolution)));
        return option;
    }
}
