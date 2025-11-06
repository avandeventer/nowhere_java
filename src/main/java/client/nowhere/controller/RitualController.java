package client.nowhere.controller;

import client.nowhere.helper.RitualHelper;
import client.nowhere.model.Option;
import client.nowhere.model.Story;
import client.nowhere.model.WinState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(maxAge = 3600)
@RestController
public class RitualController {

    private final RitualHelper ritualHelper;

    @Autowired
    public RitualController(RitualHelper ritualHelper) {
        this.ritualHelper = ritualHelper;
    }

    @GetMapping("/ritual")
    @ResponseBody
    public List<Story> get(@RequestParam String gameCode) {
        return ritualHelper.getRitual(gameCode);
    }

    @PostMapping("/ritual")
    @ResponseBody
    public Story create(@RequestBody Story ritualStory) {
        return ritualHelper.create(ritualStory);
    }

    @PutMapping("/ritual")
    @ResponseBody
    public Option update(@RequestBody Story ritualStory) {
        return ritualHelper.update(ritualStory);
    }

    @GetMapping("/victory")
    @ResponseBody
    public WinState getVictory(@RequestParam String gameCode) {
        return ritualHelper.getVictory(gameCode);
    }
}
