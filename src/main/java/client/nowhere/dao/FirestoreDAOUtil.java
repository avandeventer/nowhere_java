package client.nowhere.dao;

import client.nowhere.exception.ResourceException;
import client.nowhere.model.GameSession;
import client.nowhere.model.UserProfile;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public class FirestoreDAOUtil {

    public static <T> List<?> mapDocument(ObjectMapper objectMapper, DocumentSnapshot document, String documentPath, Class<T> targetType) {
        if(documentPath.equals("")) {
            document.getData();
        }

        List<Map<String, Object>> rawDocumentMap = (List<Map<String, Object>>) document.get(documentPath);

        if (rawDocumentMap == null) {
            throw new ResourceException("No documents found in the game session " + documentPath);
        }

        List<?> documents = rawDocumentMap.stream()
                .map(rawStory -> objectMapper.convertValue(rawStory, targetType))
                .collect(Collectors.toList());

        return documents;
    }

    public static <T> GameSession mapGameSession(DocumentSnapshot document) {
        if (document.exists()) {
            return document.toObject(GameSession.class);
        } else {
            throw new ResourceException("No data found for the document");
        }
    }

    static DocumentSnapshot getGameSession(DocumentReference gameSessionRef) throws InterruptedException, ExecutionException {
        ApiFuture<DocumentSnapshot> future = gameSessionRef.get();
        DocumentSnapshot document = future.get();

        if (!document.exists()) {
            throw new ResourceException("Game session does not exist");
        }
        return document;
    }

    public static <T> UserProfile mapUserProfile(DocumentSnapshot document) {
        if (document.exists()) {
            return document.toObject(UserProfile.class);
        } else {
            throw new ResourceException("No data found for the document");
        }
    }

    public static DocumentSnapshot getDocumentSnapshot(DocumentReference documentReference) throws InterruptedException, ExecutionException {
        ApiFuture<DocumentSnapshot> future = documentReference.get();
        DocumentSnapshot document = future.get();

        if (!document.exists()) {
            throw new ResourceException("Document does not exist");
        }
        return document;
    }

}
