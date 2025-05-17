package client.nowhere.controller;

import client.nowhere.exception.ValidationException;
import client.nowhere.helper.UserProfileHelper;
import client.nowhere.model.SaveGame;
import client.nowhere.model.UserProfile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(maxAge = 3600)
@RestController
public class UserProfileController {

    UserProfileHelper userProfileHelper;

    @Autowired
    public UserProfileController(UserProfileHelper userProfileHelper) {
        this.userProfileHelper = userProfileHelper;
    }

    @GetMapping("/user-profile")
    @ResponseBody
    public UserProfile get(@RequestParam String email, @RequestParam String password) {
        return this.userProfileHelper.get(email, password);
    }

    @PostMapping(value = "/user-profile", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public UserProfile create(@RequestBody UserProfile userProfile) {
        if (userProfile.getEmail().isEmpty() || userProfile.getPassword().isEmpty()) {
            throw new ValidationException("Email and password are required");
        }

        return this.userProfileHelper.create(userProfile);
    }

    @PostMapping(value = "/save-game", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public SaveGame upsertSaveGame(@RequestParam String userProfileId,
                                      @RequestParam String adventureId,
                                      @RequestBody SaveGame saveGame) {
        if (saveGame.getName().isEmpty() || userProfileId.isEmpty() || adventureId.isEmpty()) {
            throw new ValidationException("User Profile ID, Adventure ID, and Save Game Name are required");
        }

        return this.userProfileHelper.upsertSaveGame(userProfileId, adventureId, saveGame);
    }
}
