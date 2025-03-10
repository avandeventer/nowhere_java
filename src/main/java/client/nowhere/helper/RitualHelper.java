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
                List<StatRequirement> chosenRitualSuccessRequirements = existingRitualStories
                        .getRitualOptions().stream().filter(ritualOption ->
                                ritualOption.getOptionId().equals(chosenRitualOption.getOptionId()))
                        .findFirst().get().getStatRequirements();

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

    private RitualOption determineWhetherPlayerSucceeded(RitualOption chosenRitualOption, Player chosenPlayer, List<StatRequirement> chosenRitualStatRequirements) {
        RitualOption updatedRitualOption = chosenRitualOption;
        for (StatRequirement statRequirement : chosenRitualStatRequirements) {
            int playerStat = chosenPlayer.getStatByEnum(statRequirement.getDcStat());
            if (playerStat >= statRequirement.getDcValue()) {
                updatedRitualOption.setPointsRewarded(updatedRitualOption.getPointsRewarded() + 2);
                updatedRitualOption.setPlayerSucceeded(true);
            }
        }

        switch (updatedRitualOption.getPointsRewarded()) {
            case 0:
                updatedRitualOption.setSuccessMarginText("You try to convince yourself that you feel a divine presence as you go about your task, but deep down you know you feel you've contributed nothing.");
                break;
            case 2:
                updatedRitualOption.setSuccessMarginText("You feel small glimmers of Erysus around you, but you feel her eye drift away to other people. You didn't help the ritual much.");
                break;
            case 4:
                updatedRitualOption.setSuccessMarginText("As the night continues you feel the presence of Erysus more and more. She looks upon you positively. You've helped a good bit!");
                break;
            case 6:
                updatedRitualOption.setSuccessMarginText("You feel completely enveloped by the love of your god as you complete your duties. As the warmth of the faith in your heart grows you become sure you're of the most important parts of the ritual.");
                break;
            case 8:
                updatedRitualOption.setSuccessMarginText("Divine rapture. Your contributions to the ceremony are noticed by everyone, including and especially Erysus. A liquid warmth spreads in your chest and daggers of ice-y jealousy are shot in your direction from all over town. You are certain you have become one of Erysus' chosen.");
                break;
            default:
                break;
        }

        return updatedRitualOption;
    }
}