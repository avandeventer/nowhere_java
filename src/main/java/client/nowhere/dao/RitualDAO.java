package client.nowhere.dao;

import client.nowhere.exception.ResourceException;
import client.nowhere.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
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
    private ObjectMapper objectMapper;

    @Autowired
    public RitualDAO(Firestore db, ObjectMapper objectMapper) {
        this.db = db;
        this.objectMapper = objectMapper;
    }

    public RitualStory getRitualJobs(String gameCode) {
        RitualStory ritualStory = new RitualStory();
        try {
            DocumentReference gameSessionRef = db.collection("gameSessions").document(gameCode);
            DocumentSnapshot gameSessionSnapshot = FirestoreDAOUtil.getGameSession(gameSessionRef);
            GameSession gameSession = FirestoreDAOUtil.mapGameSession(gameSessionSnapshot);
            ritualStory = gameSession.getAdventureMap().getRitual();
        } catch (InterruptedException | ExecutionException e) {
            System.out.println("There was an issue retrieving the game session " + e.getMessage());
        }
        return ritualStory;
    }

    public RitualOption selectJob(RitualStory ritualStory) {
        try {
            DocumentReference gameSessionRef = db.collection("gameSessions").document(ritualStory.getGameCode());
            DocumentSnapshot gameSession = FirestoreDAOUtil.getGameSession(gameSessionRef);
            GameSession game = FirestoreDAOUtil.mapGameSession(gameSession);

            List<RitualOption> ritualOptions = game.getAdventureMap().getRitual().getRitualOptions();
            RitualOption selectedOption = ritualStory.getRitualOptions().get(0);

            Optional<RitualOption> existingOptionOptional = ritualOptions.stream()
                    .filter(option ->
                            option.getSelectedByPlayerId() == null
                            && option.getOptionId().equals(selectedOption.getOptionId())
                    ).findFirst();

            if (!existingOptionOptional.isPresent()) {
                throw new ResourceException("No matching ritual option found");
            }

            RitualOption existingOption = existingOptionOptional.get();

            if (selectedOption.getPointsRewarded() != null) {
                existingOption.setPointsRewarded(selectedOption.getPointsRewarded());
            }
            if (selectedOption.getSelectedByPlayerId() != null) {
                existingOption.setSelectedByPlayerId(selectedOption.getSelectedByPlayerId());
            }
            if (selectedOption.getPlayerSucceeded()) {
                existingOption.setPlayerSucceeded(selectedOption.getPlayerSucceeded());
            }

            List<RitualOption> updatedRitualOptions = ritualOptions.stream()
                    .map(option -> option.getOptionId().equals(existingOption.getOptionId()) ? existingOption : option)
                    .collect(Collectors.toList());

            game.getAdventureMap().getRitual().setRitualOptions(updatedRitualOptions);
            gameSessionRef.update("adventureMap", game.getAdventureMap());

            return existingOption;
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            throw new ResourceException("There was an issue updating the ritual", e);
        }
    }

}
