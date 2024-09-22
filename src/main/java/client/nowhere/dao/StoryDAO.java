package client.nowhere.dao;

import client.nowhere.exception.ResourceException;
import client.nowhere.model.Story;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;

@Component
public class StoryDAO {

    private final Firestore db;

    @Autowired
    public StoryDAO(Firestore db) {
        this.db = db;
    }

    public Story createStory(Story story) {

        try {
            DocumentReference gameSessionRef = db.collection("gameSessions").document(story.getGameCode());
            ApiFuture<WriteResult> result = gameSessionRef.update("stories", FieldValue.arrayUnion(story));
            WriteResult asyncResponse = result.get();
            System.out.println("Update time : " + result.get().toString());
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            throw new ResourceException("There was an issue creating the game session", e);
        }
        return story;
    }
}