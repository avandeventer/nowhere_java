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
    public StoryDAO(Firestore db) {
        this.db = db;
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
            DocumentSnapshot gameSession = getGameSession(gameSessionRef);
            List<Story> stories = mapStories(gameSession);

            for (int i = 0; i < stories.size(); i++) {
                Story storyToUpdate = stories.get(i);
                if (storyToUpdate.getStoryId().equals(story.getStoryId())) {
                    if(!story.getPrompt().isEmpty()) {
                        storyToUpdate.setPrompt(story.getPrompt());
                    }

                    storyToUpdate.setVisited(story.isVisited());

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
                                        resultOption.getStatRequirement(),
                                        resultOption.getStatDC(),
                                        !inputOption.getSuccessText().isEmpty() ?
                                                inputOption.getSuccessText() :
                                                resultOption.getSuccessText(),
                                        resultOption.getSuccessResults(),
                                        !inputOption.getFailureText().isEmpty() ?
                                                inputOption.getFailureText() :
                                                resultOption.getFailureText(),
                                        resultOption.getFailureResults()
                                );
                                optionsToUpdate.add(optionToUpdate);
                            }
                        }
                    }
                    storyToUpdate.setOptions(optionsToUpdate);
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

    private DocumentSnapshot getGameSession(DocumentReference gameSessionRef) throws InterruptedException, ExecutionException {
        ApiFuture<DocumentSnapshot> future = gameSessionRef.get();
        DocumentSnapshot document = future.get();

        if (!document.exists()) {
            throw new ResourceException("Game session does not exist");
        }
        return document;
    }

    public List<Story> getAuthorStories(String gameCode, String authorId) {
        List<Story> playerStories;
        try {
            DocumentReference gameSessionRef = db.collection("gameSessions").document(gameCode);
            DocumentSnapshot gameSession = getGameSession(gameSessionRef);
            List<Story> stories = mapStories(gameSession);
            playerStories = stories.stream()
                    .filter(story -> story.getAuthorId().equals(authorId))
                    .collect(Collectors.toList());
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            throw new ResourceException("There was an issue updating the story", e);
        }
        return playerStories;
    }

    public List<Story> getAuthorStoriesByOutcomeAuthorId(String gameCode, String outcomeAuthorId) {
        List<Story> playerStories;
        try {
            DocumentReference gameSessionRef = db.collection("gameSessions").document(gameCode);
            DocumentSnapshot gameSession = getGameSession(gameSessionRef);
            List<Story> stories = mapStories(gameSession);
            playerStories = stories.stream()
                    .filter(story -> story.getOutcomeAuthorId().equals(outcomeAuthorId))
                    .collect(Collectors.toList());
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            throw new ResourceException("There was an issue updating the story", e);
        }
        return playerStories;
    }

    public List<Story> getPlayerStories(String gameCode, String playerId, int locationId) {
        List<Story> playerStories;
        try {
            DocumentReference gameSessionRef = db.collection("gameSessions").document(gameCode);
            DocumentSnapshot gameSession = getGameSession(gameSessionRef);
            List<Story> stories = mapStories(gameSession);
            playerStories = stories.stream()
                    .filter(story -> !story.getOutcomeAuthorId().equals(playerId)
                            && (story.getLocation() != null)
                            && story.getLocation().getLocationId() == locationId
                            && !story.isVisited()
                    )
                    .collect(Collectors.toList());
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            throw new ResourceException("There was an issue updating the story", e);
        }
        return playerStories;
    }
}