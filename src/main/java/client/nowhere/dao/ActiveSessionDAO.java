package client.nowhere.dao;

import client.nowhere.exception.ResourceException;
import client.nowhere.model.ActivePlayerSession;
import client.nowhere.model.GameSession;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;

@Component
public class ActiveSessionDAO {

    private final Firestore db;

    @Autowired
    public ActiveSessionDAO(Firestore db) {
        this.db = db;
    }

    public ActivePlayerSession update(ActivePlayerSession activeSession) {
        DocumentReference gameSessionRef = db.collection("gameSessions").document(activeSession.getGameCode());

        ActivePlayerSession activeSessionToUpdate = new ActivePlayerSession();
        try {
            DocumentSnapshot gameSession = getGameSession(gameSessionRef);
            GameSession game = FirestoreDAOUtil.mapGameSession(gameSession);
            activeSessionToUpdate = game.getActivePlayerSession();
            activeSessionToUpdate.update(activeSession);

            ApiFuture<WriteResult> result = gameSessionRef.update("activePlayerSession", activeSessionToUpdate);
            WriteResult asyncResponse = result.get();
            System.out.println("Update time : " + result.get().toString());
            System.out.println("Object " + result.get().toString());
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            throw new ResourceException("There was an issue reading the game session", e);
        }
        return activeSessionToUpdate;

    }

    private DocumentSnapshot getGameSession(DocumentReference gameSessionRef) throws InterruptedException, ExecutionException {
        ApiFuture<DocumentSnapshot> future = gameSessionRef.get();
        DocumentSnapshot document = future.get();

        if (!document.exists()) {
            throw new ResourceException("Game session does not exist");
        }
        return document;
    }

}