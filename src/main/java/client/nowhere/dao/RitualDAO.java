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
import java.util.Map;
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

    public RitualOption selectJob(String gameCode,
                                  String playerId,
                                  String optionId
    ) {
        try {
            DocumentReference gameSessionRef = db.collection("gameSessions").document(gameCode);
            DocumentSnapshot gameSession = FirestoreDAOUtil.getGameSession(gameSessionRef);
            GameSession game = FirestoreDAOUtil.mapGameSession(gameSession);

            List<RitualOption> ritualOptions = game.getAdventureMap().getRitual().getRitualOptions();
            Optional<RitualOption> selectedOption = ritualOptions.stream()
                    .filter(option ->
                            option.getSelectedByPlayerId() == null
                            && option.getOptionId().equals(optionId)
                    ).findFirst();

            selectedOption.ifPresent(option -> option.setSelectedByPlayerId(playerId));

            if (!selectedOption.isPresent()) {
                throw new ResourceException("No available ritual option found");
            }

            RitualOption ritualOption = selectedOption.get();
            ritualOption.setSelectedByPlayerId(playerId);

            game.getAdventureMap().getRitual().setRitualOptions(ritualOptions);
            gameSessionRef.update("adventureMap", game.getAdventureMap());

            return ritualOption;
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            throw new ResourceException("There was an issue updating the ritual", e);
        }
    }

}
