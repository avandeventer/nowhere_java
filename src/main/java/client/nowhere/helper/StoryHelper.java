package client.nowhere.helper;

import client.nowhere.constants.AuthorConstants;
import client.nowhere.dao.GameSessionDAO;
import client.nowhere.dao.StoryDAO;
import client.nowhere.dao.UserProfileDAO;
import client.nowhere.exception.ResourceException;
import client.nowhere.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class StoryHelper {

    private final StoryDAO storyDAO;
    private final GameSessionDAO gameSessionDAO;
    private final UserProfileDAO userProfileDAO;

    @Autowired
    public StoryHelper(StoryDAO storyDAO, GameSessionDAO gameSessionDAO, UserProfileDAO userProfileDAO) {
        this.storyDAO = storyDAO;
        this.gameSessionDAO = gameSessionDAO;
        this.userProfileDAO = userProfileDAO;
    }

    public Story createStory(Story story) {
        GameSession gameSession = gameSessionDAO.getGame(story.getGameCode());
        story.randomizeNewStory(gameSession.getAdventureMap().getStatTypes());
        return storyDAO.createStory(story);
    }

    public Story updateStory(Story story) {
        return storyDAO.updateStory(story);
    }

    public List<Story> getAuthorStories(String gameCode, String authorId) {
        return storyDAO.getAuthorStories(gameCode, authorId);
    }

    public List<Story> getAuthorStoriesByOutcomeAuthorId(String gameCode, String outcomeAuthorId) {
        return storyDAO.getAuthorStoriesByOutcomeAuthorId(gameCode, outcomeAuthorId);
    }

    public List<Story> getAuthorStoriesByStoryId(String gameCode, String storyId) {
        return storyDAO.getAuthorStoriesByStoryId(gameCode, storyId);
    }

    public Story storePlayerStory(String gameCode, String playerId, String locationId) {
        return gameSessionDAO.runInTransaction(txn -> {
            Story playerStory = null;
            GameSession gameSession = gameSessionDAO.getGameInTransaction(gameCode, txn);
            List<Story> stories = gameSession.getStories() != null ? new ArrayList<>(gameSession.getStories()) : new ArrayList<>();

            List<Story> existingUnwrittenPlayerStories = gameSession.getStories() == null || gameSession.getStories().isEmpty()
                    ? new ArrayList<>()
                    : gameSession.getStories().stream()
                    .filter(story ->
                            story.getPlayerId().equals(playerId)
                                    && story.getSelectedOptionId().isBlank()
                                    && story.getAuthorId().isBlank()
                    ).collect(Collectors.toList());

            if (existingUnwrittenPlayerStories.size() >= gameSession.getStoriesToWritePerRound()) {
                playerStory = getSaveGameStoryForPlayer(gameCode, playerId, locationId, gameSession);
            }

            if (playerStory == null) {
                playerStory = createNewPlayerStory(gameCode, locationId, gameSession, playerId);
            }

            playerStory.setPlayerId(playerId);
            playerStory.setVisited(true);
            stories.add(playerStory);
            gameSessionDAO.updateStoriesInTransaction(gameCode, stories, txn);
            return playerStory;
        });
    }

    private Story createNewPlayerStory(String gameCode, String locationId, GameSession gameSession, String playerId) {
        Optional<Location> location = gameSession.getAdventureMap().getLocations().stream().filter(
                gameSessionLocation ->
                        gameSessionLocation.getId().equals(locationId)).findFirst();

        if (location.isEmpty()) {
            throw new ResourceException("A location at " + locationId + " does not exist.");
        }

        List<StatType> playerStats = gameSession.getAdventureMap().getStatTypes();
        Story playerStory = new Story(gameCode, location.get(), playerStats);

        List<Story> stories = gameSession.getStories() != null ? gameSession.getStories() : Collections.emptyList();

        List<Story> playedStories = new ArrayList<>();
        if (!stories.isEmpty()) {
            playedStories = getPlayedStories(gameSession, stories);
        }

        if (!playedStories.isEmpty()) {
            long numberOfFavorStories = stories.stream().filter(Story::isMainPlotStory).count();
            int requiredFavorStories = (gameSession.getPlayers().size() + 1) / 2;
            if (numberOfFavorStories < requiredFavorStories && !playerStory.isMainPlotStory()) {
                playerStory.setMainPlotStory(true);
                Optional<StatType> favorStatOptional = playerStats.stream().filter(StatType::isFavorType).findFirst();
                favorStatOptional.ifPresent(statType -> playerStory.getOptions().forEach(option ->
                    option.randomizeFavorOutcomes(
                        playerStats.stream().filter(stat -> !stat.isFavorType()).collect(Collectors.toList()),
                        statType
                    )
                ));
            }

            Optional<Story> prequelStory = getPrequelStory(locationId, playerId, playedStories);

            prequelStory.ifPresent(foundPrequelStory -> {
                playerStory.makeSequel(foundPrequelStory.getStoryId(), foundPrequelStory.isPlayerSucceeded(), foundPrequelStory.getSelectedOptionId());

                if (foundPrequelStory.getPlayerId().equals(playerId)
                        && foundPrequelStory.getLocation().getId().equals(locationId)) {
                    playerStory.setPrequelStoryPlayerId(foundPrequelStory.getPlayerId());
                }
            });
        }

        return playerStory;
    }

    private Optional<Story> getPrequelStory(String locationId, String playerId, List<Story> playedStories) {
        Random rand = new Random();
        int coinFlip = rand.nextInt(3);
        boolean getSequel = coinFlip > 0;

        Optional<Story> prequelStory = Optional.empty();

        if (getSequel) {
            prequelStory = findLocationMatch(playedStories, locationId)
                    .or(() -> findPlayerMatch(playedStories, playerId));
        }

        return prequelStory;
    }

    private List<Story> getPlayedStories(GameSession gameSession, List<Story> stories) {
        List<String> existingSequelIds = stories.stream()
                .map(Story::getPrequelStoryId)
                .collect(Collectors.toList());

        List<Story> playedStories = stories.stream()
                .filter(story ->
                        !story.getPlayerId().isEmpty()
                                && !story.getSelectedOptionId().isEmpty()
                                && !existingSequelIds.contains(story.getStoryId()))
                .collect(Collectors.toList());

        return playedStories.isEmpty()
                ? playedStories : filterOutExistingUserProfileSequels(gameSession, playedStories);
    }

    private Optional<Story> findPlayerMatch(List<Story> stories, String playerId) {
        return stories.stream()
                .filter(story -> story.getPlayerId().equals(playerId))
                .findFirst();
    }

    private Optional<Story> findLocationMatch(List<Story> stories, String locationId) {
        return stories.stream()
                .filter(story -> story.getLocation().getId().equals(locationId))
                .findFirst();
    }

    public Story getSaveGameStoryForPlayer(String gameCode, String playerId, String locationId, GameSession gameSession) {
        Story selectedStory = getSequelSaveGameStory(gameSession, playerId, locationId);

        if (selectedStory == null) {
            selectedStory = getRegularSaveGameStory(gameCode, locationId, gameSession);
        }

        if (selectedStory != null) {
            selectedStory.setSaveGameStory(true);
            if (selectedStory.isAFavorStory()) {
                selectedStory.setMainPlotStory(true);
            }
        }

        return selectedStory;
    }

    private Story getRegularSaveGameStory(String gameCode, String locationId, GameSession gameSession) {
        List<Story> saveGameStories = userProfileDAO.getRegularSaveGameStories(gameSession, locationId);

        Story selectedSaveGameStory = null;
        if (!saveGameStories.isEmpty()) {
            Random randomGenerator = new Random();
            int randomSequelStoryIndex = randomGenerator.nextInt(saveGameStories.size());
            selectedSaveGameStory = saveGameStories.get(randomSequelStoryIndex);
            selectedSaveGameStory.setGameCode(gameCode);
        }
        return selectedSaveGameStory;
    }

    public Story getSequelSaveGameStory(GameSession gameSession, String playerId, String locationId) {
        String userProfileId = gameSession.getUserProfileId();
        String adventureId = gameSession.getAdventureMap().getAdventureId();
        String saveGameId = gameSession.getSaveGameId();

        List<Story> saveGameSequelStories = userProfileDAO.getSaveGameSequelStories(
                userProfileId,
                adventureId,
                saveGameId,
                gameSession.getStories()
        );

        if (saveGameSequelStories.size() == 0) {
            return null;
        }

        List<String> allGameSessionStoryIds = gameSession.getStories().stream()
                .map(Story::getStoryId)
                .collect(Collectors.toList());

        Story selectedSequelStory = getSaveGamePlayerSequelStory(gameSession, playerId, saveGameSequelStories, allGameSessionStoryIds, locationId);

        if (selectedSequelStory == null) {
            selectedSequelStory = getSaveGameLocationSequelStory(locationId, saveGameSequelStories, allGameSessionStoryIds);
        }

        if (selectedSequelStory != null) {
            selectedSequelStory.setSequelStory(true);
        }

        return selectedSequelStory;
    }

    private Story getSaveGameLocationSequelStory(String locationId, List<Story> saveGameSequelStories, List<String> allGameSessionStoryIds) {
        Story selectedSequelStory = null;
        List<Story> locationSequels = saveGameSequelStories.stream()
                .filter(saveGameSequelStory -> isLocationSequelRelevantToThisPlayer(locationId, saveGameSequelStory, allGameSessionStoryIds))
                .collect(Collectors.toList());

        if (locationSequels.size() > 0) {
            Random randomGenerator = new Random();
            int randomSequelStoryIndex = randomGenerator.nextInt(locationSequels.size());
            selectedSequelStory = locationSequels.get(randomSequelStoryIndex);
        }

        return selectedSequelStory;
    }

    private Story getSaveGamePlayerSequelStory(GameSession gameSession, String playerId, List<Story> saveGameSequelStories, List<String> allGameSessionStoryIds, String locationId) {
        List<String> playerVisitedStoryIds = gameSession.getStories().stream()
                .filter(gameSessionStory -> isVisitedByPlayer(playerId, gameSessionStory))
                .map(Story::getStoryId)
                .collect(Collectors.toList());

        List<Story> sequels = saveGameSequelStories.stream()
                .filter(saveGameSequelStory -> isPlayerSequelRelevantToThisPlayer(saveGameSequelStory, playerVisitedStoryIds, allGameSessionStoryIds))
                .collect(Collectors.toList());

        Story selectedSequelStory = sequels.size() > 0 ? sequels.get(0) : null;
        if (selectedSequelStory != null) {
            Optional<Location> locationOptional = gameSession.getAdventureMap()
                    .getLocations().stream().filter(existingLocation -> existingLocation.getId().equals(locationId))
                    .findFirst();

            locationOptional.ifPresent(selectedSequelStory::setLocation);
            selectedSequelStory.setPrequelStoryPlayerId(playerId);
        }

        return selectedSequelStory;
    }

    private List<Story> filterOutExistingUserProfileSequels(GameSession gameSession, List<Story> playedStories) {
        List<Story> saveGameSequelStories = userProfileDAO.getSaveGameSequelStories(
                gameSession.getUserProfileId(),
                gameSession.getAdventureMap().getAdventureId(),
                gameSession.getSaveGameId(),
                playedStories
        );

        Set<SequelKey> existingSaveGameSequelOutcomes = saveGameSequelStories.stream()
                .filter(story -> !story.getPrequelStorySelectedOptionId().isEmpty())
                .map(Story::getPrequelKey)
                .collect(Collectors.toSet());

        return playedStories.stream()
                .filter(story -> !existingSaveGameSequelOutcomes.contains(story.getSequelKey()))
                .collect(Collectors.toList());

    }

    private boolean isVisitedByPlayer(String playerId, Story gameSessionStory) {
        return gameSessionStory.getPlayerId().equals(playerId)
                && !gameSessionStory.getSelectedOptionId().isEmpty();
    }

    private boolean isPlayerSequelRelevantToThisPlayer(Story saveGameSequelStory, List<String> storyIdsPlayedByPlayer, List<String> existingGameSessionStoryIds) {
        if (existingGameSessionStoryIds.contains(saveGameSequelStory.getStoryId())) {
            return false;
        }

        boolean isASequelForThisPlayer = saveGameSequelStory.getPrequelStoryPlayerId().equals(AuthorConstants.GLOBAL_PLAYER_SEQUEL)
                && storyIdsPlayedByPlayer.contains(saveGameSequelStory.getPrequelStoryId());

        return isASequelForThisPlayer;
    }

    private boolean isLocationSequelRelevantToThisPlayer(String locationId, Story saveGameSequelStory, List<String> existingGameSessionStoryIds) {
        if (existingGameSessionStoryIds.contains(saveGameSequelStory.getStoryId())) {
            return false;
        }

        boolean isASequelForThisLocation = saveGameSequelStory.getLocation() != null
                && saveGameSequelStory.getLocation().getId() != null
                && saveGameSequelStory.getLocation().getId().equals(locationId);
        return isASequelForThisLocation;
    }

    public Story createGlobalStory(Story story) {
        story.setAuthorId(AuthorConstants.GAME_DEV);
        story.setOutcomeAuthorId(AuthorConstants.GAME_DEV);

        if(story.getStoryId().isEmpty()) {
            String storyId = UUID.randomUUID().toString();
            story.setStoryId(storyId);
        }

        for (Option option : story.getOptions()) {
            if(option.getOptionId().isEmpty()) {
                String optionId = UUID.randomUUID().toString();
                option.setOptionId(optionId);
            }
        }

        return storyDAO.createGlobalStory(story);
    }

    public List<Story> getPlayedStoriesForAdventure(String gameCode, String playerId) {
        return storyDAO.getPlayedStoriesForAdventure(gameCode, playerId);
    }

    public List<Story> saveAllGameSessionStories(String gameCode) {
        GameSession gameSession = gameSessionDAO.getGame(gameCode);
        String adventureId = gameSession.getAdventureMap().getAdventureId();
        String userProfileId = gameSession.getUserProfileId();
        String saveGameId = gameSession.getSaveGameId();

        gameSession.getStories()
            .stream()
            .forEach(Story::resetPlayerVariables);

        List<Story> gameSessionStories = gameSession.getStories()
                .stream()
                .filter(gameSessionStory -> !gameSessionStory.getAuthorId().equals(AuthorConstants.DEFAULT))
                .collect(Collectors.toList());

        return userProfileDAO.saveGameToUserProfile(gameSessionStories, userProfileId, adventureId, saveGameId);
    }

    public RepercussionOutput triggerRepercussions(Story story) {
        Story updatedStory = updateStory(story);
        RepercussionOutput repercussionOutput = new RepercussionOutput();

        if (updatedStory.isAFavorStory()) {
            List<Integer> statGradient = Arrays.asList(9, 6);
            Story endingRitualOptions = new Story();
            endingRitualOptions.setGameCode(story.getGameCode());
            endingRitualOptions.setAuthorId(updatedStory.getPlayerId());
            endingRitualOptions.makeSequel(story.getStoryId(), story.isPlayerSucceeded(), story.getSelectedOptionId());
            Option chosenOption = updatedStory.getOptions().stream()
                    .filter(option ->
                            option.getOptionId().equals(updatedStory.getSelectedOptionId())).findFirst().get();
            StatType chosenOptionStatDC = chosenOption.getPlayerStatDCs().get(0).getStatType();

            StatType outcomeStat = getOutcomeStat(chosenOption, updatedStory.getLocation(), story.isPlayerSucceeded());

            Option unchosenStoryOption = updatedStory.getOptions().stream()
                    .filter(option ->
                            !option.getOptionId().equals(updatedStory.getSelectedOptionId())).findFirst().get();
            StatType unchosenOptionStatDC = unchosenStoryOption.getPlayerStatDCs().get(0).getStatType();

            List<Option> ritualOptions = Arrays.asList(
                    new Option(
                            Arrays.asList(chosenOptionStatDC, outcomeStat), statGradient
                    ),
                    new Option(
                            Arrays.asList(chosenOptionStatDC, unchosenOptionStatDC), statGradient
                    )
            );

            for (Option ritualOption : ritualOptions) {
                ritualOption.setSuccessText("You can feel your body swell with pride as you realize you are doing well. Your friends cheer!");
                ritualOption.setFailureText("You try your best, but you just don't have the skills to wield your sacred artifact properly");
            }

            endingRitualOptions.setOptions(ritualOptions);
            repercussionOutput.setEnding(endingRitualOptions);
        }
        return repercussionOutput;
    }

    private StatType getOutcomeStat(Option chosenOption, Location location, boolean playerSucceeded) {
        List<OutcomeStat> statResults = chosenOption.getFailureResults();
        if (playerSucceeded) {
            statResults = chosenOption.getSuccessResults();
        }

        Optional<OutcomeStat> statResult = statResults.stream().filter(outcomeStat -> !outcomeStat.getPlayerStat().getStatType().isFavorType())
                .findFirst();

        if (statResult.isPresent()) {
            return statResult.get().getPlayerStat().getStatType();
        }

        return location.getPrimaryStat();
    }
}
