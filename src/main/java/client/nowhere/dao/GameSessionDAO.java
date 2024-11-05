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
import java.util.List;
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

    public GameSession createGameSession(String sessionCode) {
        DocumentReference docRef = db.collection("gameSessions").document(sessionCode);

        GameSession gameSession = new GameSession(sessionCode);
        gameSession.setGameCode(sessionCode);
        gameSession.setGameState(GameState.INIT);

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

        try {
            ApiFuture<WriteResult> result = gameSessionRef.update("gameState", gameSession.getGameState());
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
            DocumentSnapshot gameSession = getGameSession(gameSessionRef);
            players = (List<Player>) FirestoreDAOUtil.mapDocument(objectMapper, gameSession, "players", Player.class);
        } catch (InterruptedException | ExecutionException e) {
            System.out.println("There was an issue retrieving the players for this session. " + e.getMessage());
        }
        return players;
    }

    private DocumentSnapshot getGameSession(DocumentReference gameSessionRef) throws InterruptedException, ExecutionException {
        ApiFuture<DocumentSnapshot> future = gameSessionRef.get();
        DocumentSnapshot document = future.get();

        if (!document.exists()) {
            throw new ResourceException("Game session does not exist");
        }
        return document;
    }

    public GameSession getGame(String gameCode) {
        GameSession gameSession = new GameSession();
        try {
            DocumentReference gameSessionRef = db.collection("gameSessions").document(gameCode);
            DocumentSnapshot gameSessionSnapshot = getGameSession(gameSessionRef);
            gameSession = FirestoreDAOUtil.mapGameSession(gameSessionSnapshot);
        } catch (InterruptedException | ExecutionException e) {
            System.out.println("There was an issue retrieving the game session " + e.getMessage());
        }
        return gameSession;
    }

    public Player updatePlayer(Player player) {
        try {
            DocumentReference gameSessionRef = db.collection("gameSessions").document(player.getGameCode());
            DocumentSnapshot gameSession = getGameSession(gameSessionRef);
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
}
