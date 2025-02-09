package client.nowhere.helper;

import client.nowhere.dao.RitualDAO;
import client.nowhere.model.*;
import org.checkerframework.checker.units.qual.A;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RitualHelper {

    private final RitualDAO ritualDAO;

    @Autowired
    public RitualHelper(RitualDAO ritualDAO) {
        this.ritualDAO = ritualDAO;
    }

    public RitualStory getRitualJobs(String gameCode) {
        RitualStory ritualStory = this.ritualDAO.getRitualJobs(gameCode);

        if (ritualStory == null) {
            AdventureMap adventureMap = new AdventureMap();
            adventureMap.generateDefaultRitual();
            ritualStory = adventureMap.getRitual();
        }

        return ritualStory;
    }
}