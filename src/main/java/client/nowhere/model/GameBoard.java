package client.nowhere.model;

import java.util.HashMap;
import java.util.Map;

public class GameBoard {
    PlayerCoordinates playerCoordinates;
    private Map<String, Encounter> dungeonGrid = new HashMap<>();

    public GameBoard() {
        this.dungeonGrid = new HashMap<>();
    }

    public GameBoard(Map<String, Encounter> dungeonGrid) {
        this.dungeonGrid = dungeonGrid != null ? dungeonGrid : new HashMap<>();
    }

    private String key(int x, int y) {
        return x + "," + y;
    }
    
    public void setEncounter(int x, int y, Encounter encounter) {
        dungeonGrid.put(key(x, y), encounter);
    }
    
    public Encounter getEncounter(int x, int y) {
        return dungeonGrid.get(key(x, y));
    }
    
    public Map<String, Encounter> getDungeonGrid() {
        return dungeonGrid;
    }
    
    public void setDungeonGrid(Map<String, Encounter> dungeonGrid) {
        this.dungeonGrid = dungeonGrid != null ? dungeonGrid : new HashMap<>();
    }

    public PlayerCoordinates getPlayerCoordinates() {
        return playerCoordinates;
    }

    public void setPlayerCoordinates(PlayerCoordinates playerCoordinates) {
        this.playerCoordinates = playerCoordinates;
    }

    public Encounter getEncounterAtPlayerCoordinates() {
        if (playerCoordinates == null) {
            System.err.println("Player coordinates not found for game.");
            return null;
        }

        Encounter encounter = this.getEncounter(
                playerCoordinates.getxCoordinate(),
                playerCoordinates.getyCoordinate()
        );

        if (encounter == null) {
            System.err.println("Encounter not found at coordinates (" + playerCoordinates.getxCoordinate() + ", " + playerCoordinates.getyCoordinate() + ")");
            return null;
        }

        return encounter;
    }

}

