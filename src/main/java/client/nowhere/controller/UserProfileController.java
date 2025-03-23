package client.nowhere.controller;

import client.nowhere.helper.UserProfileHelper;
import client.nowhere.model.GameSession;
import client.nowhere.model.UserProfile;
import org.apache.catalina.User;
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
        return this.userProfileHelper.create(userProfile);
    }
}
