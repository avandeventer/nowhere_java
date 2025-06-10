package client.nowhere.helper;

import client.nowhere.dao.GameSessionDAO;
import client.nowhere.dao.RitualDAO;
import client.nowhere.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Component
public class RitualHelper {

    private final RitualDAO ritualDAO;
    private final GameSessionDAO gameSessionDAO;

    @Autowired
    public RitualHelper(RitualDAO ritualDAO, GameSessionDAO gameSessionDAO) {
        this.ritualDAO = ritualDAO;
        this.gameSessionDAO = gameSessionDAO;
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

    public RitualOption update(RitualStory ritualStory) {

        if (ritualStory.getRitualOptions().size() == 1) {
            Optional<RitualOption> ritualOptionOptional = ritualStory.getRitualOptions()
                    .stream()
                    .filter(option -> !option.getSelectedByPlayerId().isEmpty())
                    .findFirst();

            if(ritualOptionOptional.isPresent()) {
                RitualOption chosenRitualOption = ritualOptionOptional.get();

                RitualStory existingRitualStories = ritualDAO.getRitualJobs(ritualStory.getGameCode());
                List<PlayerStat> chosenRitualSuccessRequirements = existingRitualStories
                        .getRitualOptions().stream().filter(ritualOption ->
                                ritualOption.getOptionId().equals(chosenRitualOption.getOptionId()))
                        .findFirst().get().getPlayerStatDCs();

                Player chosenPlayer = gameSessionDAO.getPlayer(
                        ritualStory.getGameCode(),
                        chosenRitualOption.getSelectedByPlayerId()
                );

                RitualOption updatedOption = determineWhetherPlayerSucceeded(chosenRitualOption, chosenPlayer, chosenRitualSuccessRequirements);

                ritualStory.setRitualOptions(Arrays.asList(updatedOption));
            }
        }

        return this.ritualDAO.selectJob(ritualStory);
    }

    private RitualOption determineWhetherPlayerSucceeded(RitualOption chosenRitualOption, Player chosenPlayer, List<PlayerStat> ritualDCs) {
        RitualOption updatedRitualOption = chosenRitualOption;
        for (PlayerStat ritualDC : ritualDCs) {
            int playerStatValue = chosenPlayer.getPlayerStats().stream().filter(playerStat
                    -> playerStat.getStatType().getId().equals(ritualDC.getStatType().getId())).findFirst()
                    .get().getValue();

            if (playerStatValue >= ritualDC.getValue()) {
                updatedRitualOption.setPointsRewarded(updatedRitualOption.getPointsRewarded() + 2);
            }
        }

        if (updatedRitualOption.getPointsRewarded() > 2) {
            updatedRitualOption.setPlayerSucceeded(true);
        }

        switch (updatedRitualOption.getPointsRewarded()) {
            case 0:
                updatedRitualOption.setSuccessMarginText("Your efforts were in vain");
                break;
            case 2:
                updatedRitualOption.setSuccessMarginText("You feel you didn't help much");
                break;
            case 4:
                updatedRitualOption.setSuccessMarginText("You feel like you helped!");
                break;
            case 6:
                updatedRitualOption.setSuccessMarginText("You helped a good bit!");
                break;
            case 8:
                updatedRitualOption.setSuccessMarginText("You helped a great deal!");
                break;
            default:
                break;
        }

        return updatedRitualOption;
    }
}