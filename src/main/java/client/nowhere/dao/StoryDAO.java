package client.nowhere.dao;

import client.nowhere.exception.ResourceException;
import client.nowhere.model.Option;
import client.nowhere.model.Story;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Component
public class StoryDAO {

    private final Firestore db;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    public StoryDAO(Firestore db, ObjectMapper objectMapper) {
        this.db = db;
        this.objectMapper = objectMapper;
    }

    public Story createStory(Story story) {
        try {
            DocumentReference gameSessionRef = db.collection("gameSessions").document(story.getGameCode());
            ApiFuture<WriteResult> result = gameSessionRef.update("stories", FieldValue.arrayUnion(story));
            WriteResult asyncResponse = result.get();
            System.out.println("Update time : " + result.get().toString());
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            throw new ResourceException("There was an issue creating the game session", e);
        }
        return story;
    }

    public Story updateStory(Story story) {
        try {
            DocumentReference gameSessionRef = db.collection("gameSessions").document(story.getGameCode());
            DocumentSnapshot gameSession = FirestoreDAOUtil.getDocumentSnapshot(gameSessionRef);
            List<Story> stories = mapStories(gameSession);

            for (int i = 0; i < stories.size(); i++) {
                Story storyToUpdate = stories.get(i);
                if (storyToUpdate.getStoryId().equals(story.getStoryId())) {
                    if (!story.getPrompt().isEmpty()) {
                        storyToUpdate.setPrompt(story.getPrompt());
                    }

                    if (!story.getPlayerId().isEmpty()) {
                        storyToUpdate.setPlayerId(story.getPlayerId());
                    }

                    if (!story.getAuthorId().isEmpty()) {
                        storyToUpdate.setAuthorId(story.getAuthorId());
                    }

                    if (story.getLocation() != null
                            && story.getLocation() != null
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

                    if (story.getOptions() != null && !story.getOptions().isEmpty()) {
                        List<Option> optionsToUpdate = new ArrayList<>();

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
                                    optionsToUpdate.add(optionToUpdate);
                                }
                            }
                        }

                        if (!optionsToUpdate.isEmpty()) {
                            storyToUpdate.setOptions(optionsToUpdate);
                        }
                    }
                    story = storyToUpdate;
                    stories.set(i, storyToUpdate);
                    break;
                }
            }

            ApiFuture<WriteResult> result = gameSessionRef.update("stories", stories);
            WriteResult asyncResponse = result.get();
            System.out.println("Update time : " + asyncResponse.toString());
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            throw new ResourceException("There was an issue updating the story", e);
        }
        return story;
    }

    private List<Story> mapStories(DocumentSnapshot document) {
        List<Map<String, Object>> rawStories = (List<Map<String, Object>>) document.get("stories");

        if (rawStories == null) {
            throw new ResourceException("No stories found in the game session");
        }

        List<Story> stories = rawStories.stream()
                .map(rawStory -> objectMapper.convertValue(rawStory, Story.class))
                .collect(Collectors.toList());

        return stories;
    }

    public List<Story> getAuthorStories(String gameCode, String authorId) {
        List<Story> authorStories;
        try {
            DocumentReference gameSessionRef = db.collection("gameSessions").document(gameCode);
            DocumentSnapshot gameSession = FirestoreDAOUtil.getDocumentSnapshot(gameSessionRef);
            List<Story> stories = mapStories(gameSession);
            authorStories = stories.stream()
                    .filter(story -> story.getAuthorId().equals(authorId)
                            && story.getPrompt().isEmpty())
                    .collect(Collectors.toList());
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            throw new ResourceException("There was an issue updating the story", e);
        }
        return authorStories;
    }

    public List<Story> getAuthorStoriesByOutcomeAuthorId(String gameCode, String outcomeAuthorId) {
        List<Story> outcomeAuthorStories;
        try {
            DocumentReference gameSessionRef = db.collection("gameSessions").document(gameCode);
            DocumentSnapshot gameSession = FirestoreDAOUtil.getDocumentSnapshot(gameSessionRef);
            List<Story> stories = mapStories(gameSession);
            outcomeAuthorStories = stories.stream()
                    .filter(story -> story.getSelectedOptionId().isEmpty() && story.getOptions().stream()
                            .anyMatch(option -> option.getOutcomeAuthorId().equals(outcomeAuthorId)
                                    && option.getFailureText().isEmpty()
                                    && option.getSuccessText().isEmpty()))
                    .collect(Collectors.toList());
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            throw new ResourceException("There was an issue updating the story", e);
        }
        return outcomeAuthorStories;
    }

    public List<Story> getPlayerStories(String gameCode, String playerId, String locationId) {
        List<Story> playerStories;
        try {
            DocumentReference gameSessionRef = db.collection("gameSessions").document(gameCode);
            DocumentSnapshot gameSession = FirestoreDAOUtil.getDocumentSnapshot(gameSessionRef);
            List<Story> stories = mapStories(gameSession);
            playerStories = stories.stream()
                    .filter(story -> wasNotWrittenByPlayer(playerId, locationId, story))
                    .collect(Collectors.toList());
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            throw new ResourceException("There was an issue updating the story", e);
        }
        return playerStories;
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
        List<Story> authorStories;
        try {
            DocumentReference gameSessionRef = db.collection("gameSessions").document(gameCode);
            DocumentSnapshot gameSession = FirestoreDAOUtil.getDocumentSnapshot(gameSessionRef);
            List<Story> stories = mapStories(gameSession);
            authorStories = stories.stream()
                    .filter(story -> isTestMode || (!story.getPlayerId().isEmpty() && !story.getSelectedOptionId().isEmpty()))
                    .collect(Collectors.toList());
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            throw new ResourceException("There was an issue updating the story", e);
        }
        return authorStories;
    }

    public List<Story> getPlayedStories(String gameCode, boolean isTestMode, String playerId) {
        List<Story> allPlayedStories = getPlayedStories(gameCode, isTestMode);
        return allPlayedStories.stream()
                .filter(playedStory -> playedStory.getPlayerId().equals(playerId)).collect(Collectors.toList());
    }

    public List<Story> getPlayedStoriesForAdventure(String gameCode, String playerId) {
        List<Story> authorStories;
        try {
            DocumentReference gameSessionRef = db.collection("gameSessions").document(gameCode);
            DocumentSnapshot gameSession = FirestoreDAOUtil.getDocumentSnapshot(gameSessionRef);
            List<Story> stories = mapStories(gameSession);
            authorStories = stories.stream()
                    .filter(story ->
                            story.getPlayerId().equals(playerId) &&
                            story.getSelectedOptionId().isEmpty())
                    .collect(Collectors.toList());
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            throw new ResourceException("There was an issue updating the story", e);
        }
        return authorStories;
    }

    public List<Story> getStories(String gameCode) {
        try {
            DocumentReference gameSessionRef = db.collection("gameSessions").document(gameCode);
            DocumentSnapshot gameSession = FirestoreDAOUtil.getDocumentSnapshot(gameSessionRef);
            List<Story> stories = mapStories(gameSession);
            return stories;
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            throw new ResourceException("There was an issue updating the story", e);
        }
    }

    public List<Story> getAuthorStoriesByStoryId(String gameCode, String storyId) {
        List<Story> playerStories;
        try {
            DocumentReference gameSessionRef = db.collection("gameSessions").document(gameCode);
            DocumentSnapshot gameSession = FirestoreDAOUtil.getDocumentSnapshot(gameSessionRef);
            List<Story> stories = mapStories(gameSession);
            playerStories = stories.stream()
                    .filter(story -> story.getStoryId().equals(storyId))
                    .collect(Collectors.toList());
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            throw new ResourceException("There was an issue updating the story", e);
        }
        return playerStories;

    }
}