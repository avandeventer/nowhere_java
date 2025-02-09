package client.nowhere.dao;

import client.nowhere.model.GameSession;
import client.nowhere.model.RitualStory;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;

@Component
public class RitualDAO {

    private final Firestore db;

    @Autowired
    public RitualDAO(Firestore db) {
        this.db = db;
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

}
