package client.nowhere.dao;

import client.nowhere.exception.ResourceException;
import client.nowhere.model.*;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Component
public class GameSessionDAO {

    private final Firestore db;

    @Autowired
    public GameSessionDAO(Firestore db) {
        this.db = db;
    }

    public GameSession createGameSession(String sessionCode, String userProfileId, AdventureMap adventureMap, String saveGameId, Integer storiesToWritePerRound, Integer storiesToPlayPerRound) {
        DocumentReference docRef = db.collection("gameSessions").document(sessionCode);

        GameSession gameSession = new GameSession(sessionCode);
        gameSession.setGameCode(sessionCode);
        gameSession.setGameState(GameState.INIT);
        gameSession.setUserProfileId(userProfileId);
        gameSession.setSaveGameId(saveGameId);
        gameSession.setStoriesToWritePerRound(storiesToWritePerRound);
        gameSession.setStoriesToPlayPerRound(storiesToPlayPerRound);
        gameSession.setAdventureMap(adventureMap);

        try {
            ApiFuture<WriteResult> result = docRef.set(gameSession);
            WriteResult asyncResponse = result.get();
            System.out.println("Update time : " + result.get().toString());
            System.out.println("Object " + result.get().toString());
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            throw new ResourceException("There was an issue creating the game session", e);
        }
        return gameSession;
    }

    public Player joinGameSession(Player player) {
        try {
            DocumentReference gameSessionRef = db.collection("gameSessions").document(player.getGameCode());
            ApiFuture<WriteResult> result = gameSessionRef.update("players", FieldValue.arrayUnion(player));
            WriteResult asyncResponse = result.get();
            System.out.println("Update time : " + result.get().toString());
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            throw new ResourceException("There was an issue creating the game session", e);
        }
        return player;
    }

    public GameSession updateGameSession(GameSession gameSession) {
        DocumentReference gameSessionRef = db.collection("gameSessions").document(gameSession.getGameCode());
        Map<String, Object> updates = new HashMap<>();
        updates.put("gameState", gameSession.getGameState());
        updates.put("saveGameId", gameSession.getSaveGameId());
        updates.put("activeGameStateSession", gameSession.getActiveGameStateSession());
        updates.put("activePlayerSession", gameSession.getActivePlayerSession());
        updates.put("totalPointsTowardsVictory", gameSession.getTotalPointsTowardsVictory());

        try {
            ApiFuture<WriteResult> result = gameSessionRef.update(updates);
            WriteResult asyncResponse = result.get();
            System.out.println("Update time : " + result.get().toString());
            System.out.println("Object " + result.get().toString());
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            throw new ResourceException("There was an issue creating the game session", e);
        }
        return gameSession;
    }

    public List<Player> getPlayers(String gameCode) {
        GameSession game = getGame(gameCode);
        return game.getPlayers();
    }

    public GameSession getGame(String gameCode) {
        GameSession gameSession = new GameSession();
        try {
            DocumentReference gameSessionRef = db.collection("gameSessions").document(gameCode);
            DocumentSnapshot gameSessionSnapshot = FirestoreDAOUtil.getDocumentSnapshot(gameSessionRef);
            gameSession = FirestoreDAOUtil.mapGameSession(gameSessionSnapshot);
        } catch (InterruptedException | ExecutionException e) {
            System.out.println("There was an issue retrieving the game session " + e.getMessage());
        }
        return gameSession;
    }

    public Player updatePlayer(Player player) {
        try {
            GameSession gameSession = getGame(player.getGameCode());
            List<Player> players = gameSession.getPlayers();
            players.stream()
                    .filter(existingPlayer -> existingPlayer.getAuthorId().equals(player.getAuthorId()))
                    .forEach(existingPlayer -> existingPlayer.updatePlayer(player));
            DocumentReference gameSessionRef = db.collection("gameSessions").document(player.getGameCode());
            ApiFuture<WriteResult> result = gameSessionRef.update("players", players);
            WriteResult asyncResponse = result.get();
            System.out.println("Update time : " + asyncResponse.toString());
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            throw new ResourceException("There was an issue updating the player", e);
        }

        return player;
    }

    public Player getPlayer(String gameCode, String authorId) {
        List<Player> players = this.getPlayers(gameCode);
        return players.stream().filter(player -> player.getAuthorId().equals(authorId)).findFirst().get();
    }

    public DocumentReference getGameRef(String gameCode) {
        return db.collection("gameSessions").document(gameCode);
    }

    public GameSession getGameInTransaction(String gameCode, Transaction txn) {
        try {
            DocumentReference ref = getGameRef(gameCode);
            DocumentSnapshot snapshot = txn.get(ref).get();
            if (!snapshot.exists()) {
                throw new ResourceException("GameSession " + gameCode + " not found");
            }
            return snapshot.toObject(GameSession.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get GameSession in transaction", e);
        }
    }

    public void updateStoriesInTransaction(String gameCode, List<Story> stories, Transaction txn) {
        DocumentReference ref = getGameRef(gameCode);
        txn.update(ref, "stories", stories);
    }

    public <T> T runInTransaction(Transaction.Function<T> txnLogic) {
        try {
            return db.runTransaction(txnLogic).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Transaction interrupted", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Transaction failed", e.getCause());
        }
    }
}
