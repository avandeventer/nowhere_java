package client.nowhere.controller;

import client.nowhere.helper.AdventureMapHelper;
import client.nowhere.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(maxAge = 3600)
@RestController
public class AdventureMapController {

    private final AdventureMapHelper adventureMapHelper;

    @Autowired
    public AdventureMapController(AdventureMapHelper adventureMapHelper) {
        this.adventureMapHelper = adventureMapHelper;
    }

    @GetMapping("/location")
    @ResponseBody
    public List<Location> getLocation(
            @RequestParam String gameCode,
            @RequestParam(required = false) String authorId,
            @RequestParam(required = false) String outcomeAuthorId) {
        if (authorId != null) {
            return this.adventureMapHelper.getLocationByAuthor(gameCode, authorId);
        } else if (outcomeAuthorId != null) {
            return this.adventureMapHelper.getLocationByOutcomeAuthor(gameCode, outcomeAuthorId);
        } else {
            return this.adventureMapHelper.getGameLocations(gameCode);
        }
    }

    @PostMapping("/location")
    @ResponseBody
    public List<Location> addLocation(@RequestParam String gameCode, @RequestBody Location location) {
        return this.adventureMapHelper.addLocation(gameCode, location);
    }

    @GetMapping("/display")
    @ResponseBody
    public GameSessionDisplay getGameSessionDisplay(@RequestParam String gameCode) {
        return this.adventureMapHelper.getGameSessionDisplay(gameCode);
    }

    @PostMapping("/adventure-map")
    @ResponseBody
    public AdventureMap post(@RequestBody AdventureMap adventureMap) {
        return this.adventureMapHelper.createGlobal(adventureMap);
    }

    @PutMapping("/adventure-map")
    @ResponseBody
    public AdventureMap update(@RequestBody AdventureMap adventureMap) {
        return this.adventureMapHelper.updateGlobal(adventureMap);
    }

    @GetMapping("/adventure-map")
    @ResponseBody
    public AdventureMap get(@RequestParam String adventureId) {
        return this.adventureMapHelper.getGlobal(adventureId);
    }
}
