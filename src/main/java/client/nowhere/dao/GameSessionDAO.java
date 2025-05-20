package client.nowhere.dao;

import client.nowhere.exception.ResourceException;
import client.nowhere.model.GameSession;
import client.nowhere.model.GameState;
import client.nowhere.model.Player;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Component
public class GameSessionDAO {

    private final Firestore db;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    public GameSessionDAO(Firestore db, ObjectMapper objectMapper) {
        this.db = db;
        this.objectMapper = objectMapper;
    }

    public GameSession createGameSession(String sessionCode, String userProfileId, String saveGameId, Integer storiesToWritePerRound, Integer storiesToPlayPerRound) {
        DocumentReference docRef = db.collection("gameSessions").document(sessionCode);

        GameSession gameSession = new GameSession(sessionCode);
        gameSession.setGameCode(sessionCode);
        gameSession.setGameState(GameState.INIT);
        gameSession.setUserProfileId(userProfileId);
        gameSession.setSaveGameId(saveGameId);
        gameSession.setStoriesToWritePerRound(storiesToWritePerRound);
        gameSession.setStoriesToPlayPerRound(storiesToPlayPerRound);

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
        updates.put("activeGameStateSession", gameSession.getActiveGameStateSession());

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
        List<Player> players = new ArrayList<>();
        try {
            DocumentReference gameSessionRef = db.collection("gameSessions").document(gameCode);
            DocumentSnapshot gameSession = FirestoreDAOUtil.getDocumentSnapshot(gameSessionRef);
            players = (List<Player>) FirestoreDAOUtil.mapDocument(objectMapper, gameSession, "players", Player.class);
        } catch (InterruptedException | ExecutionException e) {
            System.out.println("There was an issue retrieving the players for this session. " + e.getMessage());
        }
        return players;
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
            DocumentReference gameSessionRef = db.collection("gameSessions").document(player.getGameCode());
            DocumentSnapshot gameSession = FirestoreDAOUtil.getDocumentSnapshot(gameSessionRef);
            List<Player> players = (List<Player>) FirestoreDAOUtil.mapDocument(objectMapper, gameSession, "players", Player.class);
            players.stream()
                    .filter(existingPlayer -> existingPlayer.getAuthorId().equals(player.getAuthorId()))
                    .forEach(existingPlayer -> {
                        existingPlayer.updatePlayer(player);
                    });

            ApiFuture<WriteResult> result = gameSessionRef.update("players", players);
            WriteResult asyncResponse = result.get();
            System.out.println("Update time : " + result.get().toString());
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            throw new ResourceException("There was an issue creating the game session", e);
        }
        return player;
    }

    public Player getPlayer(String gameCode, String authorId) {
        List<Player> players = this.getPlayers(gameCode);
        return players.stream().filter(player -> player.getAuthorId().equals(authorId)).findFirst().get();
    }
}
