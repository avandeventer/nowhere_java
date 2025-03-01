package client.nowhere.controller;

import client.nowhere.helper.RitualHelper;
import client.nowhere.model.RitualOption;
import client.nowhere.model.RitualStory;
import client.nowhere.model.Story;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

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
    public Story get(@RequestParam String gameCode) {
        return ritualHelper.getRitualJobs(gameCode);
    }

    @PutMapping("/ritual")
    @ResponseBody
    public RitualOption update(@RequestBody RitualStory ritualStory) {
        return ritualHelper.update(ritualStory);
    }
}
