package client.nowhere.dao;

import client.nowhere.exception.ResourceException;
import client.nowhere.model.Ending;
import client.nowhere.model.GameSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

@Component
public class EndingDAO {

    private final Firestore db;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    public EndingDAO(Firestore db, ObjectMapper objectMapper) {
        this.db = db;
        this.objectMapper = objectMapper;
    }

    public Ending createEnding(String gameCode, Ending ending) {
        try {
            DocumentReference gameSessionRef = db.collection("gameSessions").document(gameCode);
            ApiFuture<WriteResult> result = gameSessionRef.update("endings", FieldValue.arrayUnion(ending));
            result.get();
            System.out.println("Update time : " + result.get().toString());
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            throw new ResourceException("There was an issue updating the ending", e);
        }
        return ending;
    }

    public Ending getAuthorEnding(String gameCode, String authorId) {
        GameSession game = getGame(gameCode);

        if(game.getEndings() == null) {
            throw new ResourceException("Game "
                    + gameCode
                    + " has no endings");
        }

        Optional<Ending> endingOptional = game.getEndings().stream()
                .filter(ending -> ending.getAuthorId().equals(authorId))
                .findFirst();

        if(!endingOptional.isPresent()) {
            throw new ResourceException("No ending with authorId "
                    + authorId
                    + " found");
        }

        return endingOptional.get();
    }


    public Ending getPlayerEnding(String gameCode, String playerId) {
        GameSession game = getGame(gameCode);

        if(game.getEndings() == null) {
            throw new ResourceException("Game "
                    + gameCode
                    + " has no endings");
        }

        Optional<Ending> endingOptional = game.getEndings().stream()
                .filter(ending -> ending.getPlayerId().equals(playerId))
                .findFirst();

        if(!endingOptional.isPresent()) {
            throw new ResourceException("No ending with playerId "
                    + playerId
                    + " found");
        }

        return endingOptional.get();
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

    public Ending updateEnding(String gameCode, Ending ending) {
        try {
            DocumentReference gameSessionRef = db.collection("gameSessions").document(gameCode);
            DocumentSnapshot gameSession = FirestoreDAOUtil.getDocumentSnapshot(gameSessionRef);
            List<Ending> endings = (List<Ending>) FirestoreDAOUtil.mapDocument(objectMapper, gameSession, "endings", Ending.class);

            endings.stream()
                    .filter(existingEnding -> existingEnding
                            .getAuthorId().equals(ending.getAuthorId()))
                    .forEach(existingEnding -> {
                        existingEnding.updateEnding(ending);
                    });

            ApiFuture<WriteResult> result = gameSessionRef.update("endings", endings);
            WriteResult asyncResponse = result.get();
            System.out.println("Update time : " + result.get().toString() + " " + asyncResponse.toString());
            return ending;
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            throw new ResourceException("There was an issue creating the game session", e);
        }
    }
}
