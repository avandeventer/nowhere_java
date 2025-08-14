package client.nowhere.dao;

import client.nowhere.exception.ResourceException;
import client.nowhere.model.*;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Component
public class RitualDAO {

    private final Firestore db;

    @Autowired
    public RitualDAO(Firestore db) {
        this.db = db;
    }

    public List<Story> getRitualJobs(String gameCode) {
        List<Story> ritualArtifacts = new ArrayList<>();
        try {
            DocumentReference gameSessionRef = db.collection("gameSessions").document(gameCode);
            DocumentSnapshot gameSessionSnapshot = FirestoreDAOUtil.getDocumentSnapshot(gameSessionRef);
            GameSession gameSession = FirestoreDAOUtil.mapGameSession(gameSessionSnapshot);
            ritualArtifacts = gameSession.getRituals();
        } catch (InterruptedException | ExecutionException e) {
            System.out.println("There was an issue retrieving the game session " + e.getMessage());
        }
        return ritualArtifacts;
    }

    public Option getRitualJob(String gameCode, String playerId) {
        List<Story> rituals = getRitualJobs(gameCode);
        List<Option> ritualOptions = rituals.stream().flatMap(story -> story.getOptions().stream()).collect(Collectors.toList());
        return ritualOptions.stream().filter(ritual ->
                ritual.getSelectedByPlayerId() != null
                && ritual.getSelectedByPlayerId().equals(playerId))
                .findFirst().get();
    }

    public Option selectJob(Story ritualStory) {
        try {
            DocumentReference gameSessionRef = db.collection("gameSessions").document(ritualStory.getGameCode());
            DocumentSnapshot gameSession = FirestoreDAOUtil.getDocumentSnapshot(gameSessionRef);
            GameSession game = FirestoreDAOUtil.mapGameSession(gameSession);

            List<Option> ritualOptions = game.getRituals().stream()
                    .flatMap(story -> story.getOptions().stream())
                    .collect(Collectors.toList());

            Option selectedOption = ritualStory.getOptions().get(0);

            Optional<Option> existingOptionOptional = ritualOptions.stream()
                    .filter(option ->
                            option.getSelectedByPlayerId() == null
                            && option.getOptionId().equals(selectedOption.getOptionId())
                    ).findFirst();

            if (!existingOptionOptional.isPresent()) {
                throw new ResourceException("No matching ritual option found");
            }

            Option existingOption = existingOptionOptional.get();

            if (selectedOption.getPointsRewarded() != null) {
                existingOption.setPointsRewarded(selectedOption.getPointsRewarded());
            }
            if (selectedOption.getSelectedByPlayerId() != null) {
                existingOption.setSelectedByPlayerId(selectedOption.getSelectedByPlayerId());
            }
            if (selectedOption.isPlayerSucceeded()) {
                existingOption.setPlayerSucceeded(selectedOption.isPlayerSucceeded());
            }

            if (!selectedOption.getSuccessMarginText().isEmpty()) {
                existingOption.setSuccessMarginText(selectedOption.getSuccessMarginText());
            }

            List<Story> rituals = game.getRituals();

            for (Story ritual : rituals) {
                for (Option opt : ritual.getOptions()) {
                    if (opt.getOptionId().equals(existingOption.getOptionId())) {
                        List<Option> updatedOptions = ritual.getOptions().stream()
                                .map(o -> o.getOptionId()
                                        .equals(existingOption.getOptionId())
                                        ? existingOption : o)
                                .collect(Collectors.toList());

                        ritual.setOptions(updatedOptions);
                        break;
                    }
                }
            }

            gameSessionRef.update("rituals", game.getRituals());

            return existingOption;
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            throw new ResourceException("There was an issue updating the ritual", e);
        }
    }


    public Story create(Story ritualStory) {
        try {
            List<Story> ritualStories = getRitualJobs(ritualStory.getGameCode());

            ritualStories = ritualStories.stream()
                    .filter(s -> !s.getStoryId().equals(ritualStory.getStoryId()))
                    .collect(Collectors.toList());

            ritualStories.add(ritualStory);

            DocumentReference gameSessionRef = db.collection("gameSessions")
                    .document(ritualStory.getGameCode());

            ApiFuture<WriteResult> result = gameSessionRef.update("rituals", ritualStories);
            WriteResult asyncResponse = result.get();
            System.out.println("Update time: " + asyncResponse);
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            throw new ResourceException("There was an issue creating the game session", e);
        }
        return ritualStory;
    }
}
