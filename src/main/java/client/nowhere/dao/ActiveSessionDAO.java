package client.nowhere.dao;

import client.nowhere.exception.ResourceException;
import client.nowhere.model.ActiveGameStateSession;
import client.nowhere.model.ActivePlayerSession;
import client.nowhere.model.GameSession;
import client.nowhere.model.GameState;

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
            DocumentSnapshot gameSession = FirestoreDAOUtil.getGameSession(gameSessionRef);
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

    public boolean update(String gameCode, GameState gamePhase, String authorId, boolean isDone) {
        DocumentReference gameSessionRef = db.collection("gameSessions").document(gameCode);

        try {
            // Use Firestore transaction to ensure atomicity and prevent race conditions
            return db.runTransaction(transaction -> {
                DocumentSnapshot gameSession = transaction.get(gameSessionRef).get();
                GameSession game = FirestoreDAOUtil.mapGameSession(gameSession);
                ActiveGameStateSession activeSessionToUpdate = game.getActiveGameStateSession();
                
                // Early return if player is already done (idempotency check)
                Boolean currentDoneStatus = activeSessionToUpdate.getIsPlayerDone().get(authorId);
                if ((currentDoneStatus != null && currentDoneStatus) || !gamePhase.equals(game.getGameState())) {
                    // Player already done or game phase has changed, return false (no progression needed)
                    if (currentDoneStatus != null && currentDoneStatus) {
                        System.out.println("Player " + authorId + " already marked as done, skipping duplicate update");
                    } else {
                        System.out.println("Player " + authorId + " update skipped - game phase mismatch. Expected: " + gamePhase + ", Current: " + game.getGameState());
                    }
                    return false; // No progression needed
                }
                
                // Process the update only if player is not already done
                if (isDone) {
                    activeSessionToUpdate.getIsPlayerDone().put(authorId, true);
                    System.out.println("Player " + authorId + " marked as done");
                    
                    // Check if all players are done - if so, we'll need to progress the game
                    if (areAllPlayersDone(game, activeSessionToUpdate)) {
                        System.out.println("All players are done - game progression needed");
                        // Don't progress here - just mark that progression is needed
                        // The helper layer will handle the actual progression
                    }
                }
                
                // Update only the active game state session
                transaction.update(gameSessionRef, "activeGameStateSession", activeSessionToUpdate);
                return areAllPlayersDone(game, activeSessionToUpdate); // Return true if progression needed, false otherwise
                
            }).get();
            
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            throw new ResourceException("Failed to update game state session", e);
        }
    }

    /**
     * Check if all players in the game have completed the current phase
     */
    public boolean areAllPlayersDone(GameSession game, ActiveGameStateSession activeSession) {
        return game.getPlayers().stream()
            .allMatch(player -> Boolean.TRUE.equals(activeSession.getIsPlayerDone().get(player.getAuthorId())));
    }
}
