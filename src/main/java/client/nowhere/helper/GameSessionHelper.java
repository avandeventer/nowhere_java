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
            List<Player> players = gameSessionDAO.getPlayers(gameSession.getGameCode());
            ActiveGameStateSession gameStateSession =
                    gameSession.getActiveGameStateSession();
            gameStateSession.resetPlayerDoneStatus(players);
            gameSession.setActiveGameStateSession(gameStateSession);

            switch (gameSession.getGameState()) {
                case START:
                    generateStoriesToWrite(gameSession, isTestMode, players, new ArrayList<>(), false);
                    gameSession.setGameState(GameState.WRITE_PROMPTS);
                    break;
                case GENERATE_WRITE_OPTION_AUTHORS:
                case GENERATE_WRITE_OPTION_AUTHORS_AGAIN:
                    Queue<String> playerAuthorQueue = new LinkedList<>(players.stream()
                            .map(Player::getAuthorId)
                            .collect(Collectors.toList()));

                    List<Story> stories = storyDAO.getStories(gameSession.getGameCode());
                    List<Story> storiesWithPrompts = stories.stream().filter(story -> !story.getPlayerId().isEmpty()
                            && story.isVisited()
                            && story.getSelectedOptionId().isEmpty()).collect(Collectors.toList());
                    for (Story storyWithPrompt : storiesWithPrompts) {
                        for (Option option : storyWithPrompt.getOptions()) {
                            String assignedAuthorId;
                            do {
                                assignedAuthorId = playerAuthorQueue.poll();
                                playerAuthorQueue.offer(assignedAuthorId);
                            } while (assignedAuthorId.equals(storyWithPrompt.getPlayerId())
                                    || assignedAuthorId.equals(storyWithPrompt.getAuthorId()));

                            if(!option.getSuccessText().isEmpty() && !option.getFailureText().isEmpty()) {
                                continue;
                            }

                            option.setOutcomeAuthorId(assignedAuthorId);
                        }
                        storyDAO.updateStory(storyWithPrompt);
                    }
                    gameSession.setGameStateToNext();
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

        int sequelStoryIndex = 0;
        for (int i = 0; i < players.size(); i++) {
            locationIndex = locationIndex >= DefaultLocation.values().length ? 0 : locationIndex;

            Player player = players.get(i);

            Random rand = new Random();

            int coinFlip = rand.nextInt(3);
            boolean shouldGenerateSequelStory = coinFlip == 2 && isPhaseTwo;
            boolean shouldGenerateSequelPlayerStory = coinFlip == 1 && isPhaseTwo;
            Story storyOne = new Story(
                    gameSession.getGameCode(),
                    adventureMap.getLocations().get(locationIndex),
                    "",
                    player.getAuthorId(),
                    shouldGenerateSequelStory ? playedStories.get(sequelStoryIndex).getStoryId() : "",
                    shouldGenerateSequelPlayerStory ? playedStories.get(sequelStoryIndex).getPlayerId() : "",
                    shouldGenerateSequelStory && playedStories.get(sequelStoryIndex).isPlayerSucceeded()
            );
            sequelStoryIndex++;

            locationIndex++;
            locationIndex = locationIndex >= DefaultLocation.values().length ? 0 : locationIndex;

            coinFlip = rand.nextInt(8);
            shouldGenerateSequelStory = coinFlip == 3 && isPhaseTwo;
            shouldGenerateSequelPlayerStory = coinFlip == 4 && isPhaseTwo;
            Story storyTwo = new Story(
                    gameSession.getGameCode(),
                    adventureMap.getLocations().get(locationIndex),
                    "",
                    player.getAuthorId(),
                    shouldGenerateSequelStory ? playedStories.get(sequelStoryIndex).getStoryId() : "",
                    shouldGenerateSequelPlayerStory ? playedStories.get(sequelStoryIndex).getPlayerId() : "",
                    shouldGenerateSequelStory && playedStories.get(sequelStoryIndex).isPlayerSucceeded()
            );

            if(isTestMode) {
                storyOne.setPrompt(UUID.randomUUID().toString());
                storyOne.getOptions().get(0).setAttemptText(UUID.randomUUID().toString());
                storyOne.getOptions().get(0).setOptionText(UUID.randomUUID().toString());

                storyOne.getOptions().get(1).setAttemptText(UUID.randomUUID().toString());
                storyOne.getOptions().get(1).setOptionText(UUID.randomUUID().toString());

                storyTwo.setPrompt(UUID.randomUUID().toString());
                storyTwo.getOptions().get(0).setAttemptText(UUID.randomUUID().toString());
                storyTwo.getOptions().get(0).setOptionText(UUID.randomUUID().toString());

                storyTwo.getOptions().get(1).setAttemptText(UUID.randomUUID().toString());
                storyTwo.getOptions().get(1).setOptionText(UUID.randomUUID().toString());
            }

            storyHelper.createStory(storyOne);
            storyHelper.createStory(storyTwo);

            locationIndex++;
            // locationIndexTwo++;
            // story1OutcomeAuthorIdIndex++;
            // story2OutcomeAuthorIdIndex++;
        }
    }

    private String generateSessionCode() {
        String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ123456789";
        StringBuilder stringBuilder = new StringBuilder();
        Random rnd = new Random();
        while (stringBuilder.length() < 6) {
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
