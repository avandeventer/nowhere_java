package client.nowhere.helper;

import client.nowhere.dao.GameSessionDAO;
import client.nowhere.dao.StoryDAO;
import client.nowhere.exception.ResourceException;
import client.nowhere.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Component
public class GameSessionHelper {

    private final GameSessionDAO gameSessionDAO;
    private final StoryDAO storyDAO;
    private final StoryHelper storyHelper;

    @Autowired
    public GameSessionHelper(GameSessionDAO gameSessionDAO, StoryHelper storyHelper, StoryDAO storyDAO) {
        this.gameSessionDAO = gameSessionDAO;
        this.storyHelper = storyHelper;
        this.storyDAO = storyDAO;
    }

    public GameSession createGameSession() {
        return gameSessionDAO.createGameSession(generateSessionCode());
    }

    public GameSession updateGameSession(GameSession gameSession, boolean isTestMode) {

        try {
            switch (gameSession.getGameState()) {
                case START:
                    List<Player> players = gameSessionDAO.getPlayers(gameSession.getGameCode());
                    generateStoriesToWrite(gameSession, isTestMode, players, new ArrayList<>(), false);
                    gameSession.setGameState(GameState.WRITE_PROMPTS);
                    break;
                case START_PHASE2:
                    players = gameSessionDAO.getPlayers(gameSession.getGameCode());
                    List<Story> playedStories = storyDAO.getPlayedStories(gameSession.getGameCode(), isTestMode);
                    generateStoriesToWrite(gameSession, isTestMode, players, playedStories, true);
                    gameSession.setGameState(GameState.WRITE_PROMPTS_AGAIN);
                    break;

            }
        } catch (Exception e) {
            System.out.println("Story creation stopped");
            throw new ResourceException("Story creation failure", e);
        }

        return gameSessionDAO.updateGameSession(gameSession);
    }

    private void generateStoriesToWrite(
            GameSession gameSession,
            boolean isTestMode,
            List<Player> players,
            List<Story> playedStories,
            boolean isPhaseTwo
    ) {
        int locationIndex = ThreadLocalRandom.current().nextInt(0, DefaultLocation.values().length);
        AdventureMap adventureMap = new AdventureMap();

        int story1OutcomeAuthorIdIndex = ThreadLocalRandom.current().nextInt(1, players.size());
        int story2OutcomeAuthorIdIndex = ThreadLocalRandom.current().nextInt(1, players.size());
        if (story2OutcomeAuthorIdIndex == story1OutcomeAuthorIdIndex) {
            story2OutcomeAuthorIdIndex++;
        }

        int sequelStoryIndex = 0;
        for (int i = 0; i < players.size(); i++) {
            locationIndex = locationIndex >= DefaultLocation.values().length ? 0 : locationIndex;
            story1OutcomeAuthorIdIndex = story1OutcomeAuthorIdIndex >= players.size() ? 0 : story1OutcomeAuthorIdIndex;
            story2OutcomeAuthorIdIndex = story2OutcomeAuthorIdIndex >= players.size() ? 0 : story2OutcomeAuthorIdIndex;

            Player player = players.get(i);

            Random rand = new Random();

            int coinFlip = rand.nextInt(8);
            boolean shouldGenerateSequelStory = coinFlip == 3 && isPhaseTwo;
            boolean shouldGenerateSequelPlayerStory = coinFlip == 4 && isPhaseTwo;
            Story storyOne = new Story(
                    gameSession.getGameCode(),
                    adventureMap.getLocations().get(locationIndex),
                    players.get(story1OutcomeAuthorIdIndex).getAuthorId(),
                    player.getAuthorId(),
                    shouldGenerateSequelStory ? playedStories.get(sequelStoryIndex).getStoryId() : "",
                    shouldGenerateSequelPlayerStory ? playedStories.get(sequelStoryIndex).getPlayerId() : "",
                    shouldGenerateSequelStory ? playedStories.get(sequelStoryIndex).isPlayerSucceeded() : false
            );
            sequelStoryIndex++;

            int locationIndexTwo = (locationIndex + 1 >= adventureMap.getLocations().size()) ? 0 : locationIndex + 1;

            coinFlip = rand.nextInt(8);
            shouldGenerateSequelStory = coinFlip == 3 && isPhaseTwo;
            shouldGenerateSequelPlayerStory = coinFlip == 4 && isPhaseTwo;
            Story storyTwo = new Story(
                    gameSession.getGameCode(),
                    adventureMap.getLocations().get(locationIndexTwo),
                    players.get(story2OutcomeAuthorIdIndex).getAuthorId(),
                    player.getAuthorId(),
                    shouldGenerateSequelStory ? playedStories.get(sequelStoryIndex).getStoryId() : "",
                    shouldGenerateSequelPlayerStory ? playedStories.get(sequelStoryIndex).getPlayerId() : "",
                    shouldGenerateSequelStory ? playedStories.get(sequelStoryIndex).isPlayerSucceeded() : false
            );

            if(isTestMode) {
                storyOne.setPrompt(UUID.randomUUID().toString());
                storyOne.getOptions().get(0).setAttemptText(UUID.randomUUID().toString());
                storyOne.getOptions().get(0).setOptionText(UUID.randomUUID().toString());
                storyOne.getOptions().get(0).setSuccessText(UUID.randomUUID().toString());
                storyOne.getOptions().get(0).setFailureText(UUID.randomUUID().toString());

                storyOne.getOptions().get(1).setAttemptText(UUID.randomUUID().toString());
                storyOne.getOptions().get(1).setOptionText(UUID.randomUUID().toString());
                storyOne.getOptions().get(1).setSuccessText(UUID.randomUUID().toString());
                storyOne.getOptions().get(1).setFailureText(UUID.randomUUID().toString());

                storyTwo.setPrompt(UUID.randomUUID().toString());
                storyTwo.getOptions().get(0).setAttemptText(UUID.randomUUID().toString());
                storyTwo.getOptions().get(0).setOptionText(UUID.randomUUID().toString());
                storyTwo.getOptions().get(0).setSuccessText(UUID.randomUUID().toString());
                storyTwo.getOptions().get(0).setFailureText(UUID.randomUUID().toString());

                storyTwo.getOptions().get(1).setAttemptText(UUID.randomUUID().toString());
                storyTwo.getOptions().get(1).setOptionText(UUID.randomUUID().toString());
                storyTwo.getOptions().get(1).setSuccessText(UUID.randomUUID().toString());
                storyTwo.getOptions().get(1).setFailureText(UUID.randomUUID().toString());
            }

            storyHelper.createStory(storyOne);
            storyHelper.createStory(storyTwo);

            locationIndex++;
            story1OutcomeAuthorIdIndex++;
            story2OutcomeAuthorIdIndex++;
        }
    }

    private String generateSessionCode() {
        String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890";
        StringBuilder stringBuilder = new StringBuilder();
        Random rnd = new Random();
        while (stringBuilder.length() < 5) {
            int index = (int) (rnd.nextFloat() * CHARS.length());
            stringBuilder.append(CHARS.charAt(index));
        }
        String sessionCode = stringBuilder.toString();
        return sessionCode;
    }

    public Player joinPlayer(Player player) {
        List<Player> alreadyJoinedPlayers =
                this.gameSessionDAO.getPlayers(player.getGameCode());

        Optional<Player> alreadyJoined = alreadyJoinedPlayers.stream().filter(alreadyJoinedPlayer ->
                alreadyJoinedPlayer.getUserName().equals(player.getUserName())).collect(Collectors.toList()).stream().findAny();

        if(alreadyJoined.isPresent()) {
            return alreadyJoined.get();
        }

        player.createRandomAuthorId();
        return this.gameSessionDAO.joinGameSession(player);
    }

    public List<Player> getPlayers(String gameCode) {
        return this.gameSessionDAO.getPlayers(gameCode);
    }

    public GameSession getGame(String gameCode) {
        return this.gameSessionDAO.getGame(gameCode);
    }

    public Player updatePlayer(Player player) {
        return this.gameSessionDAO.updatePlayer(player);
    }
}
