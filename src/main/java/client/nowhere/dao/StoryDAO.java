package client.nowhere.dao;

import client.nowhere.exception.ResourceException;
import client.nowhere.helper.PhasePaths;
import client.nowhere.model.Option;
import client.nowhere.model.Story;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * Stores a game's stories one document each, next to the round's phases (see PhasePaths):
 *
 * gameSessions/{code}/rounds/{round}/stories/{storyId}
 *
 * {round} is the game's roundNumber when the story is created, kept on Story.roundNumber so later
 * updates can find the document. Reads return every round's stories in creation order
 * (round, then insertOrder), which is the order the old GameSession.stories array had.
 *
 * The top-level "stories" collection (createGlobalStory and friends) is the save-game library,
 * not a game's stories.
 */
@Component
public class StoryDAO {

    private static final int MAX_BATCH_WRITES = 500;

    private final Firestore db;

    @Autowired
    public StoryDAO(Firestore db) {
        this.db = db;
    }

    public Story createStory(Story story) {
        int roundNumber = story.getRoundNumber() != null && story.getRoundNumber() >= 0
                ? story.getRoundNumber()
                : currentRound(story.getGameCode());
        story.setRoundNumber(roundNumber);
        story.setInsertOrder(nextInsertOrder());
        await(storyRef(story.getGameCode(), roundNumber, story.getStoryId()).set(story));
        return story;
    }

    /** Writes stories that arrive with a whole game (the debug game endpoint), keeping their order. */
    public void createStories(String gameCode, List<Story> stories, int defaultRoundNumber) {
        long baseOrder = nextInsertOrder();
        List<WriteBatch> batches = new ArrayList<>();
        for (int i = 0; i < stories.size(); i++) {
            if (i % MAX_BATCH_WRITES == 0) {
                batches.add(db.batch());
            }
            Story story = stories.get(i);
            if (story.getRoundNumber() == null || story.getRoundNumber() < 0) {
                story.setRoundNumber(defaultRoundNumber);
            }
            story.setInsertOrder(baseOrder + i);
            batches.getLast().set(storyRef(gameCode, story.getRoundNumber(), story.getStoryId()), story);
        }
        batches.forEach(batch -> await(batch.commit()));
    }

    /**
     * Merges the non-empty fields of story into the stored story, in a transaction on that one
     * story's document. Returns the merged story, or the input unchanged if no such story exists.
     */
    public Story updateStory(Story story) {
        DocumentReference storyRef = findStoryRef(story);
        if (storyRef == null) {
            System.out.println("Story " + story.getStoryId() + " not found in game " + story.getGameCode() + ", nothing updated");
            return story;
        }
        return await(db.runTransaction(transaction -> {
            Story storyToUpdate = transaction.get(storyRef).get().toObject(Story.class);
            applyUpdate(storyToUpdate, story);
            transaction.set(storyRef, storyToUpdate);
            return storyToUpdate;
        }));
    }

    private static void applyUpdate(Story storyToUpdate, Story story) {
        if (!story.getPrompt().isEmpty()) {
            storyToUpdate.setPrompt(story.getPrompt());
        }

        if (!story.getPlayerId().isEmpty()) {
            storyToUpdate.setPlayerId(story.getPlayerId());
        }

        if (story.getPlayerIds() != null && !story.getPlayerIds().isEmpty()) {
            storyToUpdate.setPlayerIds(story.getPlayerIds());
        }

        if (story.getPartnerIds() != null && !story.getPartnerIds().isEmpty()) {
            storyToUpdate.setPartnerIds(story.getPartnerIds());
        }

        if (!story.getAuthorId().isEmpty()) {
            storyToUpdate.setAuthorId(story.getAuthorId());
        }

        if (story.getRepercussions() != null && !story.getRepercussions().isEmpty()) {
            storyToUpdate.setRepercussions(story.getRepercussions());
        }

        if (story.getLocation() != null
                && !story.getLocation().getLabel().isEmpty()) {
            storyToUpdate.setLocation(story.getLocation());
        }

        if (!story.getSelectedOptionId().isEmpty()) {
            storyToUpdate.setSelectedOptionId(story.getSelectedOptionId());
        }

        if(story.isVisited()) {
            storyToUpdate.setVisited(story.isVisited());
        }

        if(story.isPlayerSucceeded()) {
            storyToUpdate.setPlayerSucceeded(story.isPlayerSucceeded());
        }

        if(story.isSequelStory()) {
            storyToUpdate.setSequelStory(story.isSequelStory());
        }

        if(story.isSaveGameStory()) {
            storyToUpdate.setSaveGameStory(story.isSaveGameStory());
        }

        if(!story.getPrequelStoryId().isEmpty()) {
            storyToUpdate.setPrequelStoryId(story.getPrequelStoryId());
        }

        if(!story.getPrequelStorySelectedOptionId().isEmpty()) {
            storyToUpdate.setPrequelStorySelectedOptionId(story.getPrequelStorySelectedOptionId());
        }

        if(story.isPrequelStorySucceeded()) {
            storyToUpdate.setPrequelStorySucceeded(story.isPrequelStorySucceeded());
        }

        if(!story.getPrequelStoryPlayerId().isEmpty()) {
            storyToUpdate.setPrequelStoryPlayerId(story.getPrequelStoryPlayerId());
        }

        if(story.isMainPlotStory()) {
            storyToUpdate.setMainPlotStory(story.isMainPlotStory());
        }

        if (story.getOptions() != null && !story.getOptions().isEmpty()) {
            List<Option> optionsToUpdate = storyToUpdate.getOptions() != null
                    ? updateOptions(story, storyToUpdate) : new ArrayList<>();

            List<String> existingOptionIds = optionsToUpdate.stream()
                    .map(Option::getOptionId)
                    .toList();

            for (Option inputOption : story.getOptions()) {
                if (!existingOptionIds.contains(inputOption.getOptionId())) {
                    optionsToUpdate.add(inputOption);
                }
            }

            storyToUpdate.setOptions(optionsToUpdate);
        }
    }

    private static List<Option> updateOptions(Story story, Story storyToUpdate) {
        List<Option> optionsToUpdate = new ArrayList<>();

        // Update existing options
        for (Option resultOption : storyToUpdate.getOptions()) {
            for (Option inputOption : story.getOptions()) {
                if (resultOption.getOptionId().equals(inputOption.getOptionId())) {
                    Option optionToUpdate = new Option(
                            resultOption.getOptionId(),
                            !inputOption.getOptionText().isEmpty() ?
                                    inputOption.getOptionText() :
                                    resultOption.getOptionText(),
                            !inputOption.getOptionText().isEmpty() ?
                                    inputOption.getOptionText() :
                                    resultOption.getOptionText(),
                            !inputOption.getSuccessText().isEmpty() ?
                                    inputOption.getSuccessText() :
                                    resultOption.getSuccessText(),
                            resultOption.getSuccessResults(),
                            !inputOption.getFailureText().isEmpty() ?
                                    inputOption.getFailureText() :
                                    resultOption.getFailureText(),
                            resultOption.getFailureResults(),
                            !inputOption.getOutcomeAuthorId().isEmpty() ?
                                    inputOption.getOutcomeAuthorId() :
                                    resultOption.getOutcomeAuthorId(),
                            resultOption.getPlayerStatDCs()
                    );

                    if(!inputOption.getSelectedForkId().isEmpty()) {
                        optionToUpdate.setSelectedForkId(inputOption.getSelectedForkId());
                    }

                    if (inputOption.getOutcomeForks() != null && !inputOption.getOutcomeForks().isEmpty()) {
                        optionToUpdate.setOutcomeForks(inputOption.getOutcomeForks());
                    }
                    optionsToUpdate.add(optionToUpdate);
                    break;
                }
            }
        }
        return optionsToUpdate;
    }

    public List<Story> getAuthorStories(String gameCode, String authorId) {
        return getStories(gameCode).stream()
                .filter(story -> story.getAuthorId().equals(authorId)
                        && story.getPrompt().isEmpty())
                .collect(Collectors.toList());
    }

    public List<Story> getAuthorStoriesByOutcomeAuthorId(String gameCode, String outcomeAuthorId) {
        return getStories(gameCode).stream()
                .filter(story -> story.getSelectedOptionId().isEmpty() && story.getOptions().stream()
                        .anyMatch(option -> option.getOutcomeAuthorId().equals(outcomeAuthorId)
                                && option.getFailureText().isEmpty()
                                && option.getSuccessText().isEmpty()))
                .collect(Collectors.toList());
    }

    public List<Story> getPlayerStories(String gameCode, String playerId, String locationId) {
        return getStories(gameCode).stream()
                .filter(story -> wasNotWrittenByPlayer(playerId, locationId, story))
                .collect(Collectors.toList());
    }

    private boolean wasNotWrittenByPlayer(String playerId, String locationId, Story story) {
        return !story.getAuthorId().equals(playerId)
                && !story.getOutcomeAuthorId().equals(playerId)
                && (story.getLocation() != null
                && story.getLocation().getId().equals(locationId))
                && story.getPlayerId().isEmpty()
                && story.getPrequelStoryPlayerId().isEmpty();
    }

    public Story createGlobalStory(Story story) {
        try {
            DocumentReference globalStoryRef = db.collection("stories").document(story.getStoryId());
            ApiFuture<WriteResult> result = globalStoryRef.set(story);
            WriteResult asyncResponse = result.get();
            System.out.println("Update time : " + result.get().toString());
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            throw new ResourceException("There was an issue creating the game session", e);
        }
        return story;
    }

    public List<Story> getGlobalStories(int locationId) {
        try {
            QuerySnapshot querySnapshot = db.collection("stories")
                    .whereEqualTo("location.locationId", locationId)
                    .get()
                    .get();

            List<Story> authorStories = querySnapshot.getDocuments().stream()
                    .map(document -> document.toObject(Story.class))
                    .collect(Collectors.toList());

            return authorStories;
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            throw new ResourceException("There was an issue retrieving stories by locationId", e);
        }
    }

    public List<Story> getGlobalSequelPlayerStories(List<String> storyIds) {
        try {
            QuerySnapshot querySnapshot = db.collection("stories")
                    .whereIn("prequelStoryId", storyIds)
                    .get()
                    .get();

            List<Story> authorStories = querySnapshot.getDocuments().stream()
                    .map(document -> document.toObject(Story.class))
                    .collect(Collectors.toList());

            return authorStories;
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            throw new ResourceException("There was an issue retrieving stories by locationId", e);
        }
    }

    public List<Story> getPlayedStories(String gameCode, boolean isTestMode) {
        return getStories(gameCode).stream()
                .filter(story -> isTestMode || (!story.getPlayerId().isEmpty() && !story.getSelectedOptionId().isEmpty()))
                .collect(Collectors.toList());
    }

    public List<Story> getPlayedStories(String gameCode, boolean isTestMode, String playerId) {
        List<Story> allPlayedStories = getPlayedStories(gameCode, isTestMode);
        return allPlayedStories.stream()
                .filter(playedStory -> playedStory.getPlayerId().equals(playerId)).collect(Collectors.toList());
    }

    public List<Story> getPlayedStoriesForAdventure(String gameCode, String playerId) {
        return getStories(gameCode).stream()
                .filter(story ->
                        story.getPlayerId().equals(playerId) &&
                        story.getSelectedOptionId().isEmpty())
                .collect(Collectors.toList());
    }

    public List<Story> getAuthorStoriesByStoryId(String gameCode, String storyId) {
        try {
            return getStories(gameCode).stream()
                    .filter(story -> story.getStoryId().equals(storyId))
                    .collect(Collectors.toList());
        } catch (ResourceException e) {
            return new ArrayList<>();
        }
    }

    /** Every round's stories, in creation order (empty, never null, before any story exists). */
    public List<Story> getStories(String gameCode) {
        return loadStories(gameCode, currentRound(gameCode));
    }

    /** Stories of rounds 0..currentRound, read in parallel; for callers that already know the round. */
    public List<Story> loadStories(String gameCode, int currentRound) {
        List<ApiFuture<QuerySnapshot>> rounds = new ArrayList<>();
        for (int round = 0; round <= currentRound; round++) {
            rounds.add(db.collection(PhasePaths.storiesPath(gameCode, round)).orderBy("insertOrder").get());
        }
        List<Story> stories = new ArrayList<>();
        for (ApiFuture<QuerySnapshot> round : rounds) {
            await(round).getDocuments().forEach(document -> stories.add(document.toObject(Story.class)));
        }
        return stories;
    }

    /** Inside a transaction: every round's stories, for read-then-write flows like storePlayerStory. */
    public List<Story> loadStoriesInTransaction(Transaction transaction, String gameCode, int currentRound) throws ExecutionException, InterruptedException {
        List<Story> stories = new ArrayList<>();
        for (int round = 0; round <= currentRound; round++) {
            Query roundStories = db.collection(PhasePaths.storiesPath(gameCode, round)).orderBy("insertOrder");
            transaction.get(roundStories).get().getDocuments().forEach(document -> stories.add(document.toObject(Story.class)));
        }
        return stories;
    }

    /** Inside a transaction: adds a new story document (see createStory). */
    public void createStoryInTransaction(Transaction transaction, Story story, int currentRound) {
        if (story.getRoundNumber() == null || story.getRoundNumber() < 0) {
            story.setRoundNumber(currentRound);
        }
        story.setInsertOrder(nextInsertOrder());
        transaction.set(storyRef(story.getGameCode(), story.getRoundNumber(), story.getStoryId()), story);
    }

    private DocumentReference storyRef(String gameCode, int roundNumber, String storyId) {
        return db.collection(PhasePaths.storiesPath(gameCode, roundNumber)).document(storyId);
    }

    // Stories read through getGame carry their roundNumber; a partial Story (e.g. a PUT /story body)
    // may not, so look the id up in every round so far.
    private DocumentReference findStoryRef(Story story) {
        if (story.getRoundNumber() != null && story.getRoundNumber() >= 0) {
            return storyRef(story.getGameCode(), story.getRoundNumber(), story.getStoryId());
        }
        int currentRound = currentRound(story.getGameCode());
        List<DocumentReference> candidates = new ArrayList<>();
        for (int round = 0; round <= currentRound; round++) {
            candidates.add(storyRef(story.getGameCode(), round, story.getStoryId()));
        }
        return await(db.getAll(candidates.toArray(new DocumentReference[0]))).stream()
                .filter(DocumentSnapshot::exists)
                .map(DocumentSnapshot::getReference)
                .findFirst()
                .orElse(null);
    }

    private int currentRound(String gameCode) {
        DocumentReference gameSessionRef = db.collection("gameSessions").document(gameCode);
        DocumentSnapshot gameSession = await(db.getAll(new DocumentReference[]{gameSessionRef}, FieldMask.of("roundNumber"))).getFirst();
        if (!gameSession.exists()) {
            throw new ResourceException("Game session " + gameCode + " does not exist");
        }
        Long roundNumber = gameSession.getLong("roundNumber");
        return roundNumber == null ? 0 : roundNumber.intValue();
    }

    /** Microseconds since epoch; leaves room for a batch's per-item offset. */
    private static long nextInsertOrder() {
        return System.currentTimeMillis() * 1000;
    }

    private static <T> T await(ApiFuture<T> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResourceException("Interrupted while accessing stories", e);
        } catch (ExecutionException e) {
            throw new ResourceException("There was an issue accessing stories", e);
        }
    }
}
