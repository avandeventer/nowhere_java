package client.nowhere.model;

// ===== STORY DISTRIBUTION HELPERS =====

import java.util.List;

public record StoryDistributionContext(
        List<Story> sortedStories,
        List<Player> sortedPlayers,
        int playerIndex
) {}