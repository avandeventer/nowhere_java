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
import java.util.stream.Collectors;

@Component
public class GameSessionDAO {

    private final Firestore db;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    public GameSessionDAO(Firestore db) {
        this.db = db;
    }

    public GameSession createGameSession(String sessionCode) {
        List<Player> list = new ArrayList<>();
        DocumentReference docRef = db.collection("gameSessions").document(sessionCode);
        Map<String, Object> data = new HashMap<>();
        data.put("code", sessionCode);
        data.put("players", list);
        data.put("gameState", GameState.INIT);

        try {
            ApiFuture<WriteResult> result = docRef.set(data);
            WriteResult asyncResponse = result.get();
            System.out.println("Update time : " + result.get().toString());
            System.out.println("Object " + result.get().toString());
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            throw new ResourceException("There was an issue creating the game session", e);
        }
        GameSession gameSession = new GameSession(sessionCode);
        gameSession.setGameState(GameState.INIT);
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
            players = mapPlayers(gameSession);
        } catch (InterruptedException | ExecutionException e) {
            System.out.println("There was an issue retrieving the players for this session. " + e.getMessage());
        }
        return players;
    }

    private List<Player> mapPlayers(DocumentSnapshot document) {
        List<Map<String, Object>> rawPlayers = (List<Map<String, Object>>) document.get("players");

        if (rawPlayers == null) {
            throw new ResourceException("No players found in the game session");
        }

        List<Player> players = rawPlayers.stream()
                .map(rawStory -> objectMapper.convertValue(rawStory, Player.class))
                .collect(Collectors.toList());

        return players;
    }

    private DocumentSnapshot getGameSession(DocumentReference gameSessionRef) throws InterruptedException, ExecutionException {
        // Get the game session data
        ApiFuture<DocumentSnapshot> future = gameSessionRef.get();
        DocumentSnapshot document = future.get();

        if (!document.exists()) {
            throw new ResourceException("Game session does not exist");
        }
        return document;
    }

}
