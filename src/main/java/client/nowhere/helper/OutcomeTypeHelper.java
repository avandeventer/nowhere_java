package client.nowhere.helper;

import client.nowhere.model.*;
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
     * @return List of OutcomeType objects representing the assigned stories
     */
    List<OutcomeType> distributeStoriesToPlayer(List<Story> sortedStories, List<Player> sortedPlayers, int playerIndex, int offset) {
        int numPlayers = sortedPlayers.size();

        // Calculate offset player index (wrapping if needed)
        int offsetPlayerIndex = getOffsetPlayerIndex(playerIndex, offset, numPlayers);

        List<OutcomeType> assignedStoryOutcomeTypes = new ArrayList<>();
        Player assignedPlayer = sortedPlayers.get(offsetPlayerIndex);

        List<Story> assignedStories = sortedStories.stream().filter(story -> story.getAuthorId().equals(assignedPlayer.getAuthorId())).toList();
        if (assignedStories.isEmpty()) {
            return new ArrayList<>();
        }

        for (Story assignedStory : assignedStories) {
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
            assignedStoryOutcomeTypes.add(outcomeType);
        }

        return assignedStoryOutcomeTypes;
    }

    public static int getOffsetPlayerIndex(int playerIndex, int offset, int numPlayers) {
        int offsetPlayerIndex = (playerIndex + offset) % numPlayers;
        if (offsetPlayerIndex < 0) {
            offsetPlayerIndex = offsetPlayerIndex + numPlayers;
        }
        return offsetPlayerIndex;
    }

    /**
     * Common setup for story distribution: filters stories, sorts players by joinedAt,
     * and sorts stories by player order.
     * @param gameSession The game session
     * @param playerId The player ID to find index for
     * @param visited Whether to filter for visited (true) or unvisited (false) stories
     * @return StoryDistributionContext with sorted stories, players, and player index, or null if any step yields empty results
     */
    public StoryDistributionContext getStoryDistributionContext(GameSession gameSession, String playerId, boolean visited) {
        List<Story> stories = gameSession.getStories().stream()
                .filter(story -> story.isVisited() == visited)
                .toList();

        if (stories.isEmpty()) {
            return null;
        }

        PlayerSortResult playerResult = gameSession.getSortedPlayersAndIndex(playerId);
        if (playerResult == null) {
            return null;
        }

        List<Player> sortedPlayers = playerResult.getSortedPlayers();
        int playerIndex = playerResult.getPlayerIndex();

        List<Story> sortedStories = sortStoriesByPlayerOrder(sortedPlayers, stories);

        if (sortedStories.isEmpty()) {
            return null;
        }

        return new StoryDistributionContext(sortedStories, sortedPlayers, playerIndex);
    }
}
