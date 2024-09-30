package client.nowhere.dao;

import client.nowhere.exception.ResourceException;
import client.nowhere.model.GameSession;
import client.nowhere.model.GameState;
import client.nowhere.model.Player;
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
}
