package client.nowhere.helper;

import client.nowhere.model.*;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class OutcomeTypeHelper {

    /**
     * Distributes stories to a player based on their index and submission count.
     * @param relevantStories List of stories sorted by createdAt
     * @return List of OutcomeType objects representing the assigned stories
     */
    List<OutcomeType> distributeStoriesToPlayer(List<Story> relevantStories, String assignedAuthorId) {
        List<OutcomeType> assignedStoryOutcomeTypes = new ArrayList<>();

        List<Story> assignedStories = relevantStories.stream().filter(story -> story.getAuthorId().equals(assignedAuthorId)).toList();
        if (assignedStories.isEmpty()) {
            return new ArrayList<>();
        }

        for (Story assignedStory : assignedStories) {
            OutcomeType outcomeType = new OutcomeType(assignedStory.getStoryId(), assignedStory.getPrompt());
            String prequelStoryId = assignedStory.getPrequelStoryId();
            if (prequelStoryId != null && !prequelStoryId.isEmpty()) {
                outcomeType.setClarifier(prequelStoryId);
            }

            outcomeType.setHeader(getHeader(assignedStory));

            if (assignedStory.getOptions() != null && !assignedStory.getOptions().isEmpty()) {
                for (Option option : assignedStory.getOptions()) {
                    outcomeType.getSubTypes().add(new OutcomeType(option.getOptionId(), option.getOptionText()));
                }
            }
            assignedStoryOutcomeTypes.add(outcomeType);
        }

        return assignedStoryOutcomeTypes;
    }

    private static String getHeader(Story assignedStory) {
        String header = "";
        if (assignedStory.getEncounterLabel() != null && !assignedStory.getEncounterLabel().getEncounterLabel().isEmpty()) {
            header = assignedStory.getEncounterLabel().getEncounterLabel();
            if (assignedStory.getLocation() != null && !assignedStory.getLocation().getLabel().isEmpty()) {
                header = header + " (at " + assignedStory.getLocation().getLabel() + ")";
            }
        }
        return header;
    }

    public static int getOffsetPlayerIndex(int playerIndex, int offset, int numPlayers) {
        int offsetPlayerIndex = (playerIndex + offset) % numPlayers;
        if (offsetPlayerIndex < 0) {
            offsetPlayerIndex = offsetPlayerIndex + numPlayers;
        }
        return offsetPlayerIndex;
    }

    /**
     * Convenience overload that resolves the StoryDistributionContext internally.
     * Use this when you don't need the context for any post-distribution logic.
     * Returns an empty list if the context resolves to null.
     */
    StoryDistributionContext distributeStoriesToPlayer(GameSession gameSession, String playerId, int offset) {
        List<Story> stories = gameSession.getStories().stream()
                .filter(story -> !story.isVisited())
                .toList();

        if (stories.isEmpty()) {
            return null;
        }

        PlayerSortResult playerAssignment = OutcomeTypeHelper.getPlayerAssignment(gameSession, playerId, offset);

        List<OutcomeType> assignedStories = distributeStoriesToPlayer(stories, playerAssignment.getAssignedAuthor().getAuthorId());
        return new StoryDistributionContext(stories, playerAssignment.getSortedPlayers(), playerAssignment.getPlayerIndex(), assignedStories, playerAssignment.getAssignedAuthor());
    }

    public static @NonNull PlayerSortResult getPlayerAssignment(GameSession gameSession, String playerId, int offset) {
        PlayerSortResult playerResult = gameSession.getSortedPlayersAndIndex(playerId);
        int numPlayers = playerResult.getSortedPlayers().size();

        int playerIndex = playerResult.getPlayerIndex();
        int offsetPlayerIndex = getOffsetPlayerIndex(playerIndex, offset, numPlayers);

        List<Player> sortedPlayers = playerResult.getSortedPlayers();
        Player player = sortedPlayers.get(playerIndex);
        Player assignedPlayer = sortedPlayers.get(offsetPlayerIndex);
        return new PlayerSortResult(sortedPlayers, playerIndex, assignedPlayer, player);
    }
}
