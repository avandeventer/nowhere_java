package client.nowhere.model;

import lombok.Getter;

import java.util.List;

/**
 * Helper class to hold sorted players and player index
 */
public class PlayerSortResult {
    @Getter
    final List<Player> sortedPlayers;

    @Getter
    final int playerIndex;

    @Getter
    final Player assignedAuthor;

    @Getter
    final Player player;

    public PlayerSortResult(List<Player> sortedPlayers, int playerIndex, Player assignedAuthor, Player player) {
        this.sortedPlayers = sortedPlayers;
        this.playerIndex = playerIndex;
        this.assignedAuthor = assignedAuthor;
        this.player = player;
    }
}