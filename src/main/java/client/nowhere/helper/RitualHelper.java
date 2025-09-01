package client.nowhere.helper;

import client.nowhere.dao.GameSessionDAO;
import client.nowhere.dao.RitualDAO;
import client.nowhere.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class RitualHelper {

    private final RitualDAO ritualDAO;
    private final GameSessionDAO gameSessionDAO;

    @Autowired
    public RitualHelper(RitualDAO ritualDAO, GameSessionDAO gameSessionDAO) {
        this.ritualDAO = ritualDAO;
        this.gameSessionDAO = gameSessionDAO;
    }

    public List<Story> getRitual(String gameCode) {
        return this.ritualDAO.getRitualJobs(gameCode);
    }

    public Option update(Story ritualStory) {

        if (ritualStory.getOptions().size() == 1) {
            Optional<Option> ritualOptionOptional = ritualStory.getOptions()
                    .stream()
                    .filter(option -> !option.getSelectedByPlayerId().isEmpty())
                    .findFirst();

            if(ritualOptionOptional.isPresent()) {
                Option chosenRitualOption = ritualOptionOptional.get();

                List<Story> existingRitualStories = ritualDAO.getRitualJobs(ritualStory.getGameCode());
                List<Option> rituals = existingRitualStories
                        .stream().flatMap(story -> story.getOptions().stream())
                        .collect(Collectors.toList());

                List<PlayerStat> chosenRitualSuccessRequirements = rituals
                        .stream().filter(ritualOption ->
                                ritualOption.getOptionId().equals(chosenRitualOption.getOptionId()))
                        .findFirst().get().getPlayerStatDCs();

                Player chosenPlayer = gameSessionDAO.getPlayer(
                        ritualStory.getGameCode(),
                        chosenRitualOption.getSelectedByPlayerId()
                );

                Option updatedOption = determineWhetherPlayerSucceeded(chosenRitualOption, chosenPlayer, chosenRitualSuccessRequirements);

                updatedOption.setAttemptText(attemptText);

                ritualStory.setOptions(Collections.singletonList(updatedOption));
            }
        }

        return this.ritualDAO.selectJob(ritualStory);
    }

    private Option determineWhetherPlayerSucceeded(Option chosenRitualOption, Player chosenPlayer, List<PlayerStat> ritualDCs) {
        Option updatedRitualOption = chosenRitualOption;
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

    public Story create(Story ritualStory) {
        return this.ritualDAO.create(ritualStory);
    }
}