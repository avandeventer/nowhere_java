package client.nowhere.controller;

import client.nowhere.helper.LocationHelper;
import client.nowhere.model.Location;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(maxAge = 3600)
@RestController
public class LocationController {

    private final LocationHelper locationHelper;

    @Autowired
    public LocationController(LocationHelper locationHelper) {
        this.locationHelper = locationHelper;
    }

    @GetMapping("/location")
    @ResponseBody
    public List<Location> get(@RequestParam String gameCode) {
        return this.locationHelper.getGameLocations(gameCode);
    }
}
