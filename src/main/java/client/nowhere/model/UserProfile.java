package client.nowhere.model;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class UserProfile {
    private String id;
    private String email;
    private String password;
    private Map<String, ProfileAdventureMap> maps; // Key: adventureId

    public UserProfile() {
        this.id = UUID.randomUUID().toString();
        this.email = "";
        this.password = "";
        this.maps = new HashMap<>();
    }

    public UserProfile(String email, String password) {
        this.id = UUID.randomUUID().toString();
        this.email = email;
        this.password = password;
        this.maps = new HashMap<>();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public Map<String, ProfileAdventureMap> getMaps() { return maps; }
    public void setMaps(Map<String, ProfileAdventureMap> maps) { this.maps = maps; }

    public void upsertProfileAdventureMap(ProfileAdventureMap updatedMap) {
        maps.put(updatedMap.getAdventureMap().getAdventureId(), updatedMap);
    }
}
