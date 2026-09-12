package client.nowhere.dao;

import client.nowhere.exception.ResourceException;
import client.nowhere.model.ActiveGameStateSession;
import client.nowhere.model.ActivePlayerSession;
import client.nowhere.model.GameSession;
import client.nowhere.model.GameState;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteResult;
import com.google.common.util.concurrent.MoreExecutors;
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

    /**
     * Blind, fire-and-forget write of just the nextGameStateLoading flag. Unlike
     * updateActivePlayerSession, this does not read the document first - it targets the
     * nested field directly so the loading indicator can flip for clients without waiting
     * on a full GameSession fetch/deserialize.
     */
    public void setNextGameStateLoading(String gameCode, boolean isLoading) {
        DocumentReference gameSessionRef = db.collection("gameSessions").document(gameCode);
        ApiFuture<WriteResult> result = gameSessionRef.update("activePlayerSession.nextGameStateLoading", isLoading);
        ApiFutures.addCallback(result, new ApiFutureCallback<WriteResult>() {
            @Override
            public void onFailure(Throwable throwable) {
                System.out.println("Failed to set nextGameStateLoading for game " + gameCode + ": " + throwable.getMessage());
            }

            @Override
            public void onSuccess(WriteResult writeResult) {
                // No-op: fire-and-forget, nothing downstream depends on this write completing.
            }
        }, MoreExecutors.directExecutor());
    }

    public ActivePlayerSession updateActivePlayerSession(String gameCode, ActivePlayerSession activeSession) {
        DocumentReference gameSessionRef = db.collection("gameSessions").document(gameCode);

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

    public boolean updateActiveGameStateSession(String gameCode, GameState gamePhase, String authorId, boolean isDone) {
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
                        return game.areAllPlayersDone();
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
                    if (game.areAllPlayersDone()) {
                        System.out.println("All players are done - game progression needed");
                        // Don't progress here - just mark that progression is needed
                        // The helper layer will handle the actual progression
                    }
                }
                
                // Update only the active game state session
                transaction.update(gameSessionRef, "activeGameStateSession", activeSessionToUpdate);
                return game.areAllPlayersDone(); // Return true if progression needed, false otherwise
            }).get();
            
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            throw new ResourceException("Failed to update game state session", e);
        }
    }
}
