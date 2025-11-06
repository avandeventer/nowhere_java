package client.nowhere.helper;

import client.nowhere.dao.GameSessionDAO;
import client.nowhere.dao.RitualDAO;
import client.nowhere.dao.EndingDAO;
import client.nowhere.dao.StoryDAO;
import client.nowhere.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class RitualHelper {

    private final RitualDAO ritualDAO;
    private final EndingDAO endingDAO;
    private final StoryDAO storyDAO;
    private final GameSessionDAO gameSessionDAO;

    @Autowired
    public RitualHelper(RitualDAO ritualDAO, GameSessionDAO gameSessionDAO, EndingDAO endingDAO, StoryDAO storyDAO) {
        this.ritualDAO = ritualDAO;
        this.gameSessionDAO = gameSessionDAO;
        this.endingDAO = endingDAO;
        this.storyDAO = storyDAO;
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

                Option chosenRitual = rituals
                        .stream().filter(ritualOption ->
                                ritualOption.getOptionId().equals(chosenRitualOption.getOptionId()))
                        .findFirst().get();

                Player chosenPlayer = gameSessionDAO.getPlayer(
                        ritualStory.getGameCode(),
                        chosenRitualOption.getSelectedByPlayerId()
                );

                Option updatedOption = determineWhetherPlayerSucceeded(chosenRitual, chosenPlayer);
                GameSession gameSession = gameSessionDAO.getGame(ritualStory.getGameCode());
                gameSession.setTotalPointsTowardsVictory(gameSession.getTotalPointsTowardsVictory() + updatedOption.getPointsRewarded());
                gameSessionDAO.updateGameSession(gameSession);

                ritualStory.setOptions(Collections.singletonList(updatedOption));
            }
        }


        return ritualStory.getOptions().get(0); //this.ritualDAO.selectJob(ritualStory);
    }

    private Option determineWhetherPlayerSucceeded(Option chosenRitualOption, Player chosenPlayer) {
        for (PlayerStat ritualDC : chosenRitualOption.getPlayerStatDCs()) {
            int playerStatValue = chosenPlayer.getPlayerStats().stream().filter(playerStat
                    -> playerStat.getStatType().getId().equals(ritualDC.getStatType().getId())).findFirst()
                    .get().getValue();

            if (playerStatValue < ritualDC.getValue()) {
                chosenRitualOption.setPointsRewarded(0);
            }
        }

        if (chosenRitualOption.getPointsRewarded() > 2) {
            chosenRitualOption.setPlayerSucceeded(true);
        }

        switch (chosenRitualOption.getPointsRewarded()) {
            case 0 -> chosenRitualOption.setSuccessMarginText("Your efforts were in vain");
            case 1, 2, -1, -2 -> chosenRitualOption.setSuccessMarginText("You feel you didn't help much");
            case 3, 4, -3, -4 -> chosenRitualOption.setSuccessMarginText("You feel like you helped!");
            case 5, 6, -5, -6 -> chosenRitualOption.setSuccessMarginText("You helped a good bit!");
            case 7, 8, -7, -8 -> chosenRitualOption.setSuccessMarginText("You helped a great deal!");
            case 9, 10, -9, -10 -> chosenRitualOption.setSuccessMarginText("You helped a tremendous amount!");
            default -> chosenRitualOption.setSuccessMarginText("You did something unexpected!");
        }

        return chosenRitualOption;
    }

    public Story create(Story ritualStory) {
        return this.ritualDAO.create(ritualStory);
    }

    public WinState getVictory(String gameCode) {
        GameSession gameSession = gameSessionDAO.getGame(gameCode);
        List<Player> players = gameSession.getPlayers();
        int totalPlayerFavor = players.stream()
        .mapToInt(Player::getFavor)
        .sum();

        int totalRitualFavor = gameSession.getTotalPointsTowardsVictory();
        gameSession.setTotalPointsTowardsVictory(totalRitualFavor + totalPlayerFavor);

        int gameSizeAdjustment = players.size() * gameSession.getStoriesToPlayPerRound();
        boolean didWeSucceed = gameSession.getTotalPointsTowardsVictory() > (6 * gameSizeAdjustment);
        boolean didWeDestroy = gameSession.getTotalPointsTowardsVictory() < (gameSizeAdjustment) * -1;

        GameSessionDisplay gameSessionDisplay = gameSession.getAdventureMap().getGameSessionDisplay();

        if (didWeSucceed) {
            return new WinState(gameSessionDisplay.getSuccessText(), "https://storage.googleapis.com/nowhere_images/location_icons/Heart.png", "SUCCESS");
        } else if (didWeDestroy) {
            return new WinState(gameSessionDisplay.getFailureText(), "https://storage.googleapis.com/nowhere_images/location_icons/Heart.png", "DESTROYED");
        } else {
            return new WinState(gameSessionDisplay.getNeutralText(), "https://storage.googleapis.com/nowhere_images/location_icons/Skull.png", "FAILED");
        }
    }
}