package client.nowhere.helper;

import client.nowhere.dao.*;
import client.nowhere.model.AdventureMap;
import client.nowhere.model.ProfileAdventureMap;
import client.nowhere.model.UserProfile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;

@Component
public class UserProfileHelper {

    UserProfileDAO userProfileDAO;

    @Autowired
    public UserProfileHelper(UserProfileDAO userProfileDAO) {
        this.userProfileDAO = userProfileDAO;
    }

    public UserProfile create(UserProfile userProfile) {
        ProfileAdventureMap profileAdventureMap = new ProfileAdventureMap(new AdventureMap());
        HashMap<String, ProfileAdventureMap> profileAdventureHashMap = new HashMap<>();
        profileAdventureHashMap.put(profileAdventureMap.getAdventureMap().getAdventureId(), profileAdventureMap);
        userProfile.setMaps(profileAdventureHashMap);
        return this.userProfileDAO.create(userProfile);
    }

    public UserProfile get(String email, String password) {
        return new UserProfile(email, password);
    }
}
