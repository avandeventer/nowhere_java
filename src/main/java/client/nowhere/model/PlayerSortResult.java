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

    PlayerSortResult(List<Player> sortedPlayers, int playerIndex) {
        this.sortedPlayers = sortedPlayers;
        this.playerIndex = playerIndex;
    }
}