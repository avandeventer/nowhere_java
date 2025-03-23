package client.nowhere.model;

import org.checkerframework.checker.units.qual.A;

import java.util.HashMap;
import java.util.Map;

public class ProfileAdventureMap {
    private AdventureMap adventureMap;
    private Map<String, SaveGame> saveGames;

    public ProfileAdventureMap() {
        this.adventureMap = new AdventureMap();
        this.saveGames = new HashMap<>();
    }

    public ProfileAdventureMap(AdventureMap adventureMap) {
        this.adventureMap = adventureMap;
        this.saveGames = new HashMap<>();
        SaveGame saveGame = new SaveGame();
        this.saveGames.put(saveGame.getId(), saveGame);
    }

    public AdventureMap getAdventureMap() { return adventureMap; }
    public void setAdventureMap(AdventureMap adventureMap) { this.adventureMap = adventureMap; }

    public Map<String, SaveGame> getSaveGames() { return saveGames; }
    public void setSaveGames(Map<String, SaveGame> saveGames) { this.saveGames = saveGames; }

    public void upsertSaveGame(SaveGame updatedSaveGame) {
        saveGames.put(updatedSaveGame.getId(), updatedSaveGame);
    }

    public SaveGame getSaveGameById(String saveGameId) {
        return saveGames.get(saveGameId);
    }
}
