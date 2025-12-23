package client.nowhere.dao;

import client.nowhere.exception.ResourceException;
import com.google.cloud.firestore.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;

@Component
public class FeatureFlagDAO {

    private final Firestore db;
    private static final String COLLECTION_NAME = "featureFlags";
    private static final String DOCUMENT_ID = "flags";

    @Autowired
    public FeatureFlagDAO(Firestore db) {
        this.db = db;
    }

    /**
     * Gets the value of a feature flag by its name.
     * @param flagName The name of the feature flag
     * @return The boolean value of the flag, or false if not found
     */
    public Boolean getFlagValue(String flagName) {
        try {
            DocumentReference flagsRef = db.collection(COLLECTION_NAME).document(DOCUMENT_ID);
            DocumentSnapshot flagsSnapshot = flagsRef.get().get();
            
            if (!flagsSnapshot.exists()) {
                return false;
            }
            
            Object flagValue = flagsSnapshot.get(flagName);
            if (flagValue == null) {
                return false;
            }
            
            // Handle both Boolean and boolean types
            if (flagValue instanceof Boolean) {
                return (Boolean) flagValue;
            }
            
            return false;
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new ResourceException("Failed to retrieve feature flag: " + flagName, e);
        }
    }
}

