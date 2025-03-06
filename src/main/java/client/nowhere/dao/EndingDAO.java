package client.nowhere.dao;

import client.nowhere.exception.ResourceException;
import client.nowhere.model.Ending;
import client.nowhere.model.GameSession;
import client.nowhere.model.Story;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ExecutionException;

@Component
public class EndingDAO {

    private final Firestore db;

    @Autowired
    public EndingDAO(Firestore db) {
        this.db = db;
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
            DocumentSnapshot gameSessionSnapshot = FirestoreDAOUtil.getGameSession(gameSessionRef);
            gameSession = FirestoreDAOUtil.mapGameSession(gameSessionSnapshot);
        } catch (InterruptedException | ExecutionException e) {
            System.out.println("There was an issue retrieving the game session " + e.getMessage());
        }
        return gameSession;
    }
}
