package client.nowhere.helper;

import client.nowhere.dao.*;
import client.nowhere.exception.GameStateException;
import client.nowhere.exception.ResourceException;
import client.nowhere.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class GameSessionHelper {

    private final GameSessionDAO gameSessionDAO;
    private final AdventureMapDAO adventureMapDAO;
    private final StoryDAO storyDAO;
    private final RitualDAO ritualDAO;
    private final EndingDAO endingDAO;

    @Autowired
    public GameSessionHelper(
            GameSessionDAO gameSessionDAO,
            AdventureMapDAO adventureMapDAO,
            StoryDAO storyDAO,
            RitualDAO ritualDAO,
            EndingDAO endingDAO
    ) {
        this.gameSessionDAO = gameSessionDAO;
        this.adventureMapDAO = adventureMapDAO;
        this.storyDAO = storyDAO;
        this.ritualDAO = ritualDAO;
        this.endingDAO = endingDAO;
    }

    public GameSession createGameSession(String userProfileId, String adventureId, String saveGameId, Integer storiesToWritePerRound, Integer storiesToPlayPerRound) {
        AdventureMap adventureMap = adventureMapDAO.get(userProfileId, adventureId);
        return gameSessionDAO.createGameSession(generateSessionCode(), userProfileId, adventureMap, saveGameId, storiesToWritePerRound, storiesToPlayPerRound);
    }

    public GameSession  updateToNextGameState(String gameCode) {
        GameSession gameSession = gameSessionDAO.getGame(gameCode);
        gameSession.setGameStateToNext();
        return updateGameSession(gameSession, false);
    }

    public GameSession updateGameSession(GameSession gameSession, boolean isTestMode) {
        GameSession existingSession = gameSessionDAO.getGame(gameSession.getGameCode());

        if(existingSession.getGameState().equals(gameSession.getGameState())) {
            return existingSession;
        }

        try {
            gameSessionDAO.updateGameSession(gameSession);
            List<Player> players = gameSessionDAO.getPlayers(gameSession.getGameCode());
            ActiveGameStateSession gameStateSession =
                    gameSession.getActiveGameStateSession();
            gameStateSession.resetPlayerDoneStatus(players);
            gameSession.setActiveGameStateSession(gameStateSession);

            switch (gameSession.getGameState()) {
                case START:
                case START_PHASE2:
                    assignStoryAuthors(gameSession, isTestMode, players);
                    gameSession.setGameStateToNext();
                    break;
                case GENERATE_WRITE_OPTION_AUTHORS:
                case GENERATE_WRITE_OPTION_AUTHORS_AGAIN:
                    Queue<String> playerAuthorQueue = new LinkedList<>(players.stream()
                            .map(Player::getAuthorId)
                            .collect(Collectors.toList()));

                    List<Story> stories = storyDAO.getStories(gameSession.getGameCode());
                    List<Story> storiesWithPrompts = stories.stream().filter(story ->
                            !story.getPlayerId().isEmpty()
                                    && story.getSelectedOptionId().isEmpty()).collect(Collectors.toList());
                    for (Story storyWithPrompt : storiesWithPrompts) {
                        String firstOptionAuthorPicked = "";
                        for (Option option : storyWithPrompt.getOptions()) {
                            String assignedAuthorId;

                            if (!option.getSuccessText().isEmpty() && !option.getFailureText().isEmpty()) {
                                continue;
                            }

                            do {
                                assignedAuthorId = playerAuthorQueue.poll();
                                playerAuthorQueue.offer(assignedAuthorId);
                            } while (Objects.equals(assignedAuthorId, storyWithPrompt.getPlayerId())
                                    || Objects.equals(assignedAuthorId, storyWithPrompt.getAuthorId())
                                    || Objects.equals(assignedAuthorId, firstOptionAuthorPicked));

                            option.setOutcomeAuthorId(assignedAuthorId);
                            firstOptionAuthorPicked = assignedAuthorId;
                        }
                        storyDAO.updateStory(storyWithPrompt);
                    }
                    gameSession.setGameStateToNext();
                    break;
                case GENERATE_ENDINGS:
                    List<Ending> authorEndings = new ArrayList<>();

                    int totalPlayerFavor = players.stream()
                            .mapToInt(Player::getFavor)
                            .sum();

                    int totalRitualFavor = existingSession
                            .getAdventureMap()
                            .getRitual().getOptions()
                            .stream()
                            .mapToInt(Option::getPointsRewarded)
                            .sum();

                    boolean didWeSucceed = totalPlayerFavor + totalRitualFavor > (12 * players.size());
                    gameSession.setDidWeSucceed(didWeSucceed);

                    //Per turn per player possible total:
                    // 1 per turn for location = 4
                    // player stat start = 4
                    // 2 per turn max for stories = 8
                    // 2 x Each Success for Ending Ritual (4) = 8
                    // Max = 24, Min = 0
                    // Average = 2 for location + 4 player stat + 8 for ritual = 14 each player

                    for (int authorIndex = 0; authorIndex < players.size(); authorIndex++) {
                        int playerIndex = authorIndex + 1 >= players.size() ? 0 : authorIndex + 1;
                        String authorId = players.get(authorIndex).getAuthorId();
                        String playerId = players.get(playerIndex).getAuthorId();

                        Ending authorEnding = new Ending(authorId, playerId);
                        authorEnding.setPlayerUsername(players.get(playerIndex).getUserName());
                        List<Story> storiesPlayedByPlayer
                                = storyDAO.getPlayedStories(gameSession.getGameCode(), false, playerId);

                        //Filter this out so it only contains the option that the player picked
                        storiesPlayedByPlayer.forEach(story ->
                                story.setOptions(
                                        story.getOptions().stream()
                                                .filter(option -> option.getOptionId().equals(story.getSelectedOptionId()))
                                                .collect(Collectors.toList())
                                )
                        );

                        authorEnding.setAssociatedStories(storiesPlayedByPlayer);
                        Option ritualOption = ritualDAO.getRitualJob(gameSession.getGameCode(), playerId);
                        authorEnding.setAssociatedRitualOption(ritualOption);
                        authorEnding.setDidWeSucceed(gameSession.getDidWeSucceed());
                        authorEndings.add(authorEnding);

                        endingDAO.createEnding(gameSession.getGameCode(), authorEnding);
                    }
                    gameSession.setGameStateToNext();
                    break;
            }
        } catch (Exception e) {
            System.out.println("Story creation stopped");
            throw new ResourceException("Story creation failure", e);
        }

        return gameSessionDAO.updateGameSession(gameSession);
    }

    private void assignStoryAuthors(
            GameSession gameSession,
            boolean isTestMode,
            List<Player> players
    ) {
        Set<Story> remainingUnwrittenStories = gameSession.getStories().stream()
                .filter(story -> story.getAuthorId().isEmpty()).collect(Collectors.toSet());

        Map<String, Integer> authorStoryCount = new HashMap<>();
        Map<String, Integer> authorOutcomeCount = new HashMap<>();
        for (Player player : players) {
            authorStoryCount.put(player.getAuthorId(), 0);
            authorOutcomeCount.put(player.getAuthorId(), 0);
        }

        while (!remainingUnwrittenStories.isEmpty()) {
            for (Story unwrittenStory : new ArrayList<>(remainingUnwrittenStories)) {
                String playerId = unwrittenStory.getPlayerId();

                // Build eligible authors dynamically based on current state
                List<Player> eligibleAuthors = players.stream()
                        .filter(p -> !p.getAuthorId().equals(playerId))
                        .filter(p -> remainingUnwrittenStories.stream().anyMatch(
                                s -> !s.getPlayerId().equals(p.getAuthorId())
                        ))
                        .sorted(Comparator
                                .comparingInt((Player p) -> authorStoryCount.get(p.getAuthorId())) // FEWEST authored stories first
                                .thenComparingInt(p -> (int) remainingUnwrittenStories.stream()
                                        .filter(s -> !s.getPlayerId().equals(p.getAuthorId()))
                                        .count()) // THEN fewest eligible stories
                        )
                        .collect(Collectors.toList());

                if (eligibleAuthors.isEmpty()) {
                    continue;
                }

                Player selectedAuthor = eligibleAuthors.get(0);
                unwrittenStory.setAuthorId(selectedAuthor.getAuthorId());

                if (isTestMode) {
                    unwrittenStory.setPrompt(UUID.randomUUID().toString());
                    unwrittenStory.getOptions().get(0).setAttemptText(UUID.randomUUID().toString());
                    unwrittenStory.getOptions().get(0).setOptionText(UUID.randomUUID().toString());

                    unwrittenStory.getOptions().get(1).setAttemptText(UUID.randomUUID().toString());
                    unwrittenStory.getOptions().get(1).setOptionText(UUID.randomUUID().toString());
                }

                storyDAO.updateStory(unwrittenStory);
                authorStoryCount.put(selectedAuthor.getAuthorId(), authorStoryCount.get(selectedAuthor.getAuthorId()) + 1);
                remainingUnwrittenStories.remove(unwrittenStory);
            }
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
        return stringBuilder.toString();
    }

    public Player joinPlayer(Player player) {
        player.setGameCode(player.getGameCode().toUpperCase(Locale.ROOT));

        GameSession gameSession =
                this.gameSessionDAO.getGame(player.getGameCode());

        List<Player> alreadyJoinedPlayers = gameSession.getPlayers();

        Optional<Player> alreadyJoined = alreadyJoinedPlayers.stream().filter(alreadyJoinedPlayer ->
                alreadyJoinedPlayer.getUserName().equals(player.getUserName())).collect(Collectors.toList()).stream().findAny();

        if(alreadyJoined.isPresent()) {
            return alreadyJoined.get();
        } else if (gameSession.isAfterGameState(GameState.WRITE_PROMPTS)) {
            throw new GameStateException("New players cannot join after locations have been selected.");
        }

        if(alreadyJoinedPlayers.size() == 0) {
            player.setFirstPlayer(true);
        }

        player.setBasePlayerStats(gameSession.getAdventureMap().getStatTypes(), 4);

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

    public Player getPlayer(String gameCode, String authorId) {
        return this.gameSessionDAO.getPlayer(gameCode, authorId);
    }
}
