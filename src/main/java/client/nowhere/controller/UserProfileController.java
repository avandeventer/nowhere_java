package client.nowhere.controller;

import client.nowhere.exception.ValidationException;
import client.nowhere.helper.AdventureMapHelper;
import client.nowhere.helper.UserProfileHelper;
import client.nowhere.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(maxAge = 3600)
@RestController
public class UserProfileController {

    UserProfileHelper userProfileHelper;
    AdventureMapHelper adventureMapHelper;

    @Autowired
    public UserProfileController(UserProfileHelper userProfileHelper, AdventureMapHelper adventureMapHelper) {
        this.userProfileHelper = userProfileHelper;
        this.adventureMapHelper = adventureMapHelper;
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

    @GetMapping("/save-game")
    @ResponseBody
    public SaveGame get(@RequestParam String email,
                        @RequestParam String password,
                        @RequestParam String adventureId,
                        @RequestParam String saveGameId) {
        return this.userProfileHelper.getSaveGame(email, password, adventureId, saveGameId);
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

    @PostMapping(value = "/save-game/adventure-map")
    @ResponseBody
    public String saveGameSessionAdventureMapToUserProfile(
            @RequestParam String gameCode
    ) {
        return this.userProfileHelper.saveGameSessionAdventureMapToUserProfile(gameCode);
    }

    @DeleteMapping(value = "/save-game", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public void deleteSaveGame(@RequestParam String userProfileId,
                                   @RequestParam String adventureId,
                                   @RequestParam String saveGameId) {
        if (saveGameId.isEmpty() || userProfileId.isEmpty() || adventureId.isEmpty()) {
            throw new ValidationException("User Profile ID, Adventure ID, and Save Game Name are required");
        }

        this.userProfileHelper.deleteSaveGame(userProfileId, adventureId, saveGameId);
    }



    @PostMapping("/user-profile/{userProfileId}/adventure-map")
    @ResponseBody
    public AdventureMap createAdventureMap(
            @PathVariable String userProfileId,
            @RequestBody AdventureMap adventureMap
    ) {
        return this.adventureMapHelper.create(userProfileId, adventureMap);
    }

    @DeleteMapping("/user-profile/adventure-map")
    @ResponseBody
    public void deleteAdventureMap(
            @RequestParam String userProfileId,
            @RequestParam String adventureId
    ) {
        this.adventureMapHelper.delete(userProfileId, adventureId);
    }

    @PutMapping("/user-profile/{userProfileId}/adventure-map")
    @ResponseBody
    public AdventureMap updateAdventureMap(
            @PathVariable String userProfileId,
            @RequestBody AdventureMap adventureMap
    ) {
        return this.adventureMapHelper.updateAdventureMap(userProfileId, adventureMap);
    }

    @PutMapping("/user-profile/{userProfileId}/adventure-map/{adventureId}/location")
    @ResponseBody
    public AdventureMap addLocation(
            @PathVariable String userProfileId,
            @PathVariable String adventureId,
            @RequestBody Location location
    ) {
        return this.adventureMapHelper.addLocation(userProfileId, adventureId, location);
    }

    @PutMapping("/user-profile/{userProfileId}/adventure-map/{adventureId}/stat-type")
    @ResponseBody
    public AdventureMap addStatType(
            @PathVariable String userProfileId,
            @PathVariable String adventureId,
            @RequestBody StatType statType
    ) {
        return this.adventureMapHelper.addStatType(userProfileId, adventureId, statType);
    }

    @PutMapping("/user-profile/{userProfileId}/adventure-map/{adventureId}/ritual-option")
    @ResponseBody
    public AdventureMap addRitualOption(
            @PathVariable String userProfileId,
            @PathVariable String adventureId,
            @RequestBody Option option
    ) {
        return this.adventureMapHelper.addRitualOption(userProfileId, adventureId, option);
    }

    @GetMapping("/user-profile/{userProfileId}/adventure-map/{adventureId}")
    @ResponseBody
    public AdventureMap getAdventureMap(
            @PathVariable String userProfileId,
            @PathVariable String adventureId
    ) {
        return this.adventureMapHelper.get(userProfileId, adventureId);
    }
}
