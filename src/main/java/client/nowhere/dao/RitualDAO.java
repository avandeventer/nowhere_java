package client.nowhere.dao;

import client.nowhere.exception.ResourceException;
import client.nowhere.model.*;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

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

    public Story getRitualJobs(String gameCode) {
        Story ritualStory = new Story();
        try {
            DocumentReference gameSessionRef = db.collection("gameSessions").document(gameCode);
            DocumentSnapshot gameSessionSnapshot = FirestoreDAOUtil.getDocumentSnapshot(gameSessionRef);
            GameSession gameSession = FirestoreDAOUtil.mapGameSession(gameSessionSnapshot);
            ritualStory = gameSession.getAdventureMap().getRitual();
        } catch (InterruptedException | ExecutionException e) {
            System.out.println("There was an issue retrieving the game session " + e.getMessage());
        }
        return ritualStory;
    }

    public Option getRitualJob(String gameCode, String playerId) {
        Story ritualJobs = getRitualJobs(gameCode);
        return ritualJobs.getOptions().stream().filter(ritualOption ->
                ritualOption.getSelectedByPlayerId() != null
                && ritualOption.getSelectedByPlayerId().equals(playerId))
                .findFirst().get();
    }

    public Option selectJob(Story ritualStory) {
        try {
            DocumentReference gameSessionRef = db.collection("gameSessions").document(ritualStory.getGameCode());
            DocumentSnapshot gameSession = FirestoreDAOUtil.getDocumentSnapshot(gameSessionRef);
            GameSession game = FirestoreDAOUtil.mapGameSession(gameSession);

            List<Option> ritualOptions = game.getAdventureMap().getRitual().getOptions();
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

            List<Option> updatedRitualOptions = ritualOptions.stream()
                    .map(option -> option.getOptionId().equals(existingOption.getOptionId()) ? existingOption : option)
                    .collect(Collectors.toList());

            game.getAdventureMap().getRitual().setOptions(updatedRitualOptions);
            gameSessionRef.update("adventureMap", game.getAdventureMap());

            return existingOption;
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            throw new ResourceException("There was an issue updating the ritual", e);
        }
    }


    public Story create(Story ritualStory) {
        try {
            DocumentReference gameSessionRef = db.collection("gameSessions").document(ritualStory.getGameCode());
            ApiFuture<WriteResult> result = gameSessionRef.update("rituals", FieldValue.arrayUnion(ritualStory));
            WriteResult asyncResponse = result.get();
            System.out.println("Update time : " + result.get().toString());
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            throw new ResourceException("There was an issue creating the game session", e);
        }
        return ritualStory;
    }
}
