package client.nowhere.helper;

import client.nowhere.model.Option;
import client.nowhere.model.OutcomeType;
import client.nowhere.model.Player;
import client.nowhere.model.Story;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class OutcomeTypeHelper {

    /**
     * Sorts stories by matching their authorId to the order of players in the sorted players list.
     * Stories with matching players are sorted by player index, stories without matching players
     * are placed at the end and sorted by createdAt.
     * @param sortedPlayers List of players sorted by joinedAt
     * @param stories List of stories to sort
     * @return List of stories sorted by player order
     */
    public List<Story> sortStoriesByPlayerOrder(List<Player> sortedPlayers, List<Story> stories) {
        // Create a map of player authorId to their index in the sorted list
        Map<String, Integer> playerIndexMap = new HashMap<>();
        for (int i = 0; i < sortedPlayers.size(); i++) {
            playerIndexMap.put(sortedPlayers.get(i).getAuthorId(), i);
        }

        // Sort stories by matching authorId to player order, then by createdAt for stories without matching players
        final Map<String, Integer> finalPlayerIndexMap = playerIndexMap;
        return stories.stream()
                .sorted(Comparator
                        .comparing((Story story) -> {
                            Integer playerIdx = finalPlayerIndexMap.get(story.getAuthorId());
                            // Stories with matching players come first, sorted by player index
                            // Stories without matching players come last, sorted by createdAt
                            return playerIdx != null ? playerIdx : Integer.MAX_VALUE;
                        }))
                .toList();
    }

    /**
     * Distributes stories to a player based on their index and submission count.
     * @param sortedStories List of stories sorted by createdAt
     * @param sortedPlayers List of players sorted by joinedAt
     * @param playerIndex The index of the player in the sorted players list
     * @param shouldReturnMultiple Whether to return multiple stories (player has 2+ submissions)
     * @return List of OutcomeType objects representing the assigned stories
     */
    List<OutcomeType> distributeStoriesToPlayer(List<Story> sortedStories, List<Player> sortedPlayers, int playerIndex, int offset, boolean shouldReturnMultiple) {
        int numPlayers = sortedPlayers.size();
        int numStories = sortedStories.size();
        List<OutcomeType> assignedStories = new ArrayList<>();

        // Calculate offset player index (wrapping if needed)
        int offsetPlayerIndex = (playerIndex + offset) % numPlayers;

        if (numStories <= numPlayers || !shouldReturnMultiple) {
            // Fewer stories than players, or player hasn't made 2+ submissions: return one story with wrapping
            int storyIndex = offsetPlayerIndex % numStories;
            Story assignedStory = sortedStories.get(storyIndex);
            OutcomeType outcomeType = new OutcomeType(assignedStory.getStoryId(), assignedStory.getPrompt());
            String prequelStoryId = assignedStory.getPrequelStoryId();
            if (prequelStoryId != null && !prequelStoryId.isEmpty()) {
                outcomeType.setClarifier(prequelStoryId);
            }

            if (assignedStory.getOptions() != null && !assignedStory.getOptions().isEmpty()) {
                for (Option option : assignedStory.getOptions()) {
                    outcomeType.getSubTypes().add(new OutcomeType(option.getOptionId(), option.getOptionText()));
                }
            }

            assignedStories.add(outcomeType);
        } else {
            // More stories than players AND player has 2+ submissions: return multiple stories
            for (int i = 0; i < sortedStories.size(); i++) {
                if (i % numPlayers == offsetPlayerIndex) {
                    Story story = sortedStories.get(i);
                    OutcomeType outcomeType = new OutcomeType(story.getStoryId(), story.getPrompt());
                    String prequelStoryId = story.getPrequelStoryId();
                    if (prequelStoryId != null && !prequelStoryId.isEmpty()) {
                        outcomeType.setClarifier(prequelStoryId);
                    }
                    assignedStories.add(outcomeType);
                }
            }
        }

        return assignedStories;
    }
}
