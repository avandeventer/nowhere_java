package client.nowhere.model;

import java.util.HashMap;
import java.util.Map;

public class GameBoard {
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
}

