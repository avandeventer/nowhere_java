package client.nowhere.helper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import client.nowhere.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import client.nowhere.dao.AdventureMapDAO;
import client.nowhere.dao.EndingDAO;
import client.nowhere.dao.GameSessionDAO;
import client.nowhere.dao.StoryDAO;
import client.nowhere.exception.GameStateException;
import client.nowhere.exception.ResourceException;
import io.netty.util.internal.StringUtil;

@Component
public class GameSessionHelper {

    private final GameSessionDAO gameSessionDAO;
    private final AdventureMapDAO adventureMapDAO;
    private final StoryDAO storyDAO;
    private final EndingDAO endingDAO;
    private final UserProfileHelper userProfileHelper;

    @Autowired
    public GameSessionHelper(
            GameSessionDAO gameSessionDAO,
            AdventureMapDAO adventureMapDAO,
            StoryDAO storyDAO,
            EndingDAO endingDAO,
            UserProfileHelper userProfileHelper
    ) {
        this.gameSessionDAO = gameSessionDAO;
        this.adventureMapDAO = adventureMapDAO;
        this.storyDAO = storyDAO;
        this.endingDAO = endingDAO;
        this.userProfileHelper = userProfileHelper;
    }

    public GameSession createGameSession(String userProfileId, String adventureId, String saveGameId, Integer storiesToWritePerRound, Integer storiesToPlayPerRound) {
        AdventureMap adventureMap = StringUtil.isNullOrEmpty(adventureId) ? null : adventureMapDAO.get(userProfileId, adventureId);
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
            List<Player> players = existingSession.getPlayers();
            ActiveGameStateSession gameStateSession =
                    gameSession.getActiveGameStateSession();
            gameStateSession.resetPlayerDoneStatus(players);
            gameSession.setActiveGameStateSession(gameStateSession);

            ActivePlayerSession activePlayerSession = new ActivePlayerSession();
            activePlayerSession.resetPlayerDoneWithTurn(players);
            activePlayerSession.resetActivePlayerSession();
            gameSession.setActivePlayerSession(activePlayerSession);

            switch (gameSession.getGameState()) {
                case WHERE_ARE_WE:
                    if (gameSession.getAdventureMap() != null && !gameSession.getAdventureMap().getAdventureId().isEmpty()) {
                        gameSession.skipAdventureMapCreateMode();
                    }
                    break;
                case GENERATE_LOCATION_AUTHORS:    
                    generateLocationAuthors(gameSession, players);
                    gameSession.setGameStateToNext();
                    break;
                case GENERATE_OCCUPATION_AUTHORS:
                    players.forEach(player -> {
                        player.setBasePlayerStats(
                                gameSession.getAdventureMap().getStatTypes(), 4
                        );
                        gameSessionDAO.updatePlayer(player);
                    });
                    gameSession.setPlayers(players);
                    assignLocationOptionAuthors(gameSession, players);
                    gameSession.setGameStateToNext();
                    break;
                case PREAMBLE:
                    if (
                        gameSession.getAdventureMap() != null &&
                        gameSession.getUserProfileId() != null && 
                        !gameSession.getUserProfileId().isEmpty()
                    ) {
                        String saveGameId = userProfileHelper.saveGameSessionAdventureMapToUserProfile(gameSession);

                        if (!saveGameId.isEmpty()) {
                            gameSession.setSaveGameId(saveGameId);
                        }
                    }
                    break;
                case GENERATE_WRITE_PROMPT_AUTHORS:
                case GENERATE_WRITE_PROMPT_AUTHORS_AGAIN:
                    assignStoryAuthors(gameSession, isTestMode, players);
                    gameSession.setGameStateToNext();
                    break;
                case GENERATE_WRITE_OPTION_AUTHORS:
                case GENERATE_WRITE_OPTION_AUTHORS_AGAIN:
                    assignStoryOptionAuthors(gameSession, players);
                    gameSession.setGameStateToNext();
                    break;
                case ROUND1:
                case ROUND2:
                case RITUAL:
                case ENDING:
                    activePlayerSession.setFirstPlayerTurn(players);
                    break;
                case GENERATE_ENDINGS:
                    List<Ending> authorEndings = new ArrayList<>();

                    int totalPlayerFavor = players.stream()
                            .mapToInt(Player::getFavor)
                            .sum();

                    int totalRitualFavor = gameSession.getTotalPointsTowardsVictory();
                    gameSession.setTotalPointsTowardsVictory(totalRitualFavor + totalPlayerFavor);

                    boolean didWeSucceed = gameSession.getTotalPointsTowardsVictory() > (12 * players.size());
                    gameSession.setDidWeSucceed(didWeSucceed);

                    //Per turn per player possible total:
                    // 1 per turn for location = 4
                    // player stat start = 4
                    // 2 per turn max for stories = 8
                    // 2 x Each Success for Ending Ritual (4) = 8
                    // Max = 24, Min = 0
                    // Average = 2 for location + 4 player stat + 8 for ritual = 14 each player

                    //Per turn per player possible total (with single turn and negative values):
                    // 1 per turn for location = 2
                    // player stat start = 4
                    // 1 per turn max for stories = 4
                    // 1-10 for rituals (one high and one low) = average = 5
                    //Average =

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
                        // Option ritual = ritualDAO.getRitualJob(gameSession.getGameCode(), playerId);
                        // authorEnding.setAssociatedRitualOption(ritual);
                        authorEnding.setDidWeSucceed(gameSession.getDidWeSucceed());
                        authorEndings.add(authorEnding);

                        endingDAO.createEnding(gameSession.getGameCode(), authorEnding);
                    }
                    gameSession.setGameStateToNext();
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            System.out.println("Game State transition stopped");
            throw new ResourceException("Game State update failure", e);
        }

        return gameSessionDAO.updateGameSession(gameSession);
    }

    private void generateLocationAuthors(GameSession gameSession, List<Player> players) {
        List<StatType> statTypes = gameSession.getAdventureMap().getStatTypes();
        if (statTypes == null || statTypes.isEmpty()) {
            System.out.println("No StatTypes available for location generation");
            return;
        }

        List<Location> newLocations = new ArrayList<>();
        
        for (int i = 0; i < players.size(); i++) {
            Player player = players.get(i);
            Location location = new Location();
            location.setAuthorId(player.getAuthorId());
            
            List<Option> options = new ArrayList<>();
            
            StatType primaryStatType = statTypes.get(i % statTypes.size());
            StatType secondaryStatType1 = statTypes.get((i + 1) % statTypes.size());
            StatType secondaryStatType2 = statTypes.get((i + 2) % statTypes.size());
            
            Option option1 = createLocationOption(primaryStatType, secondaryStatType1);
            Option option2 = createLocationOption(primaryStatType, secondaryStatType2);
                         
            options.add(option1);
            options.add(option2);
            location.setOptions(options);
            
            newLocations.add(location);
        }
        
        for (Location location : newLocations) {
            gameSession.getAdventureMap().getLocations().add(location);
            adventureMapDAO.addLocation(gameSession.getGameCode(), location);
        }
    }

    private void assignLocationOptionAuthors(GameSession gameSession, List<Player> players) {
        Map<String, Integer> outcomeAuthorCount = players.stream()
                .collect(Collectors.toMap(Player::getAuthorId, p -> 0));

        List<Location> locations = adventureMapDAO.getLocations(gameSession.getGameCode());
        List<Location> locationsWithOptions = locations.stream()
                .filter(location -> location.getOptions() != null && !location.getOptions().isEmpty())
                .toList();
        
        // Randomize order for fair distribution
        List<Location> shuffledLocations = new ArrayList<>(locationsWithOptions);
        Collections.shuffle(shuffledLocations, new Random(shuffledLocations.size()));
        
        for (Location location : shuffledLocations) {
            // Get options that need authors
            List<Option> options = location.getOptions().stream()
                    .filter(option -> option.getOutcomeAuthorId().isEmpty())
                    .toList();
            
            if (options.isEmpty()) {
                continue;
            }

            // Find eligible authors (not the location author)
            Optional<Player> selectedAuthor = findAuthorWithFewestAssignments(
                    players, 
                    List.of(location.getAuthorId()), 
                    outcomeAuthorCount
            );

            if (selectedAuthor.isPresent()) {
                String authorId = selectedAuthor.get().getAuthorId();
                options.forEach(option -> option.setOutcomeAuthorId(authorId));
                outcomeAuthorCount.put(authorId, outcomeAuthorCount.get(authorId) + options.size());
            }
            
            adventureMapDAO.updateLocation(gameSession.getGameCode(), location);
        }
    }

    private void assignStoryOptionAuthors(GameSession gameSession, List<Player> players) {
        Map<String, Integer> outcomeAuthorCount = players.stream()
                .collect(Collectors.toMap(Player::getAuthorId, p -> 0));

        List<Story> storiesWithPrompts = gameSession.getStories().stream()
                .filter(story -> !story.getPlayerId().isEmpty() && story.getSelectedOptionId().isEmpty())
                .toList();
        
        // Randomize order for fair distribution
        List<Story> shuffledStories = new ArrayList<>(storiesWithPrompts);
        Collections.shuffle(shuffledStories, new Random(shuffledStories.size()));
        
        for (Story story : shuffledStories) {
            // Get options that need authors
            List<Option> options = story.getOptions().stream()
                    .filter(option -> option.getOutcomeAuthorId().isEmpty())
                    .toList();
            
            if (options.isEmpty()) {
                continue;
            }

            // Find eligible authors (not the story's player or author)
            Optional<Player> selectedAuthor = findAuthorWithFewestAssignments(
                    players, 
                    List.of(story.getPlayerId(), story.getAuthorId()), 
                    outcomeAuthorCount
            );

            if (selectedAuthor.isPresent()) {
                String authorId = selectedAuthor.get().getAuthorId();
                options.forEach(option -> option.setOutcomeAuthorId(authorId));
                outcomeAuthorCount.put(authorId, outcomeAuthorCount.get(authorId) + options.size());
            }
            
            storyDAO.updateStory(story);
        }
    }
    
    private Optional<Player> findAuthorWithFewestAssignments(
            List<Player> players, 
            List<String> excludedAuthorIds, 
            Map<String, Integer> outcomeAuthorCount
    ) {
        return players.stream()
                .filter(p -> !excludedAuthorIds.contains(p.getAuthorId()))
                .min(Comparator.comparingInt(p -> outcomeAuthorCount.get(p.getAuthorId())));
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

                Player selectedAuthor = eligibleAuthors.getFirst();
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

    /**
     * Creates a location option with primary and secondary StatTypes
     */
    private Option createLocationOption(StatType primaryStatType, StatType secondaryStatType) {
        Option option = new Option();
        option.setSuccessResults(createSuccessResults(primaryStatType, secondaryStatType));
        return option;
    }

    /**
     * Creates success results with primary and secondary StatTypes
     */
    private ArrayList<OutcomeStat> createSuccessResults(StatType primaryStatType, StatType secondaryStatType) {
        ArrayList<OutcomeStat> successResults = new ArrayList<>();
        
        // Add primary StatType
        OutcomeStat primaryOutcome = new OutcomeStat();
        primaryOutcome.setPlayerStat(new PlayerStat(primaryStatType, 1));
        successResults.add(primaryOutcome);
        
        // Add secondary StatType
        OutcomeStat secondaryOutcome = new OutcomeStat();
        secondaryOutcome.setPlayerStat(new PlayerStat(secondaryStatType, 1));
        successResults.add(secondaryOutcome);
        
        return successResults;
    }

    private String generateSessionCode() {
        String CHARS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
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

        if(alreadyJoinedPlayers.isEmpty()) {
            player.setFirstPlayer(true);
        }

        if (gameSession.getAdventureMap() != null) {
            player.setBasePlayerStats(gameSession.getAdventureMap().getStatTypes(), 4);
        }

        player.createRandomAuthorId();
        player.setJoinedAt(new Date());
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
