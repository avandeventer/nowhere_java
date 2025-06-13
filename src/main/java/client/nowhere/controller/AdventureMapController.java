package client.nowhere.controller;

import client.nowhere.helper.AdventureMapHelper;
import client.nowhere.model.AdventureMap;
import client.nowhere.model.GameSessionDisplay;
import client.nowhere.model.Location;
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
    public List<Location> get(@RequestParam String gameCode) {
        return this.adventureMapHelper.getGameLocations(gameCode);
    }

    @GetMapping("/display")
    @ResponseBody
    public GameSessionDisplay getGameSessionDisplay(@RequestParam String gameCode) {
        return this.adventureMapHelper.getGameSessionDisplay(gameCode);
    }

    @PostMapping("/adventure-map")
    @ResponseBody
    public AdventureMap post(@RequestBody AdventureMap adventureMap) {
        return this.adventureMapHelper.create(adventureMap);
    }
}
