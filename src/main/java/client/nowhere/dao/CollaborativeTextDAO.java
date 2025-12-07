package client.nowhere.dao;

import client.nowhere.exception.ResourceException;
import client.nowhere.model.CollaborativeTextPhase;
import client.nowhere.model.GameSession;
import client.nowhere.model.PlayerVote;
import client.nowhere.model.TextSubmission;
import com.google.cloud.firestore.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Component
public class CollaborativeTextDAO {

    private final Firestore db;

    @Autowired
    public CollaborativeTextDAO(Firestore db) {
        this.db = db;
    }

    /**
     * Retrieves a CollaborativeTextPhase from Firestore within a transaction
     * @param gameCode The game code
     * @param phaseId The phase ID (e.g., "WHERE_ARE_WE")
     * @param txn The Firestore transaction
     * @return The CollaborativeTextPhase or null if not found
     */
    public CollaborativeTextPhase getCollaborativeTextPhaseInTransaction(String gameCode, String phaseId, Transaction txn) {
        try {
            DocumentReference gameSessionRef = db.collection("gameSessions").document(gameCode);
            DocumentSnapshot gameSessionSnapshot = txn.get(gameSessionRef).get();
            
            if (!gameSessionSnapshot.exists()) {
                return null;
            }
            
            // Convert to GameSession object using the same pattern as GameSessionDAO
            GameSession gameSession = gameSessionSnapshot.toObject(GameSession.class);
            if (gameSession == null) {
                return null;
            }
            
            // Get the collaborativeTextPhases from the GameSession object
            Map<String, CollaborativeTextPhase> collaborativeTextPhases = gameSession.getCollaborativeTextPhases();
            if (collaborativeTextPhases == null || !collaborativeTextPhases.containsKey(phaseId)) {
                return null;
            }
            
            return collaborativeTextPhases.get(phaseId);
            
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new ResourceException("Failed to retrieve collaborative text phase in transaction", e);
        }
    }

    /**
     * Updates a CollaborativeTextPhase in Firestore within a transaction
     * This method adds new submissions without overwriting existing ones
     * @param gameCode The game code
     * @param phaseId The phase ID
     * @param updatedPhase The updated CollaborativeTextPhase
     * @param txn The Firestore transaction
     */
    public void updateCollaborativeTextPhaseInTransaction(String gameCode, String phaseId, 
                                                        CollaborativeTextPhase updatedPhase, Transaction txn) {
        try {
            DocumentReference gameSessionRef = db.collection("gameSessions").document(gameCode);
            
            // Update the specific phase within the collaborativeTextPhases map
            txn.update(gameSessionRef, "collaborativeTextPhases." + phaseId, updatedPhase);
            
        } catch (Exception e) {
            throw new ResourceException("Failed to update collaborative text phase in transaction", e);
        }
    }

    /**
     * Creates a new CollaborativeTextPhase in Firestore within a transaction
     * @param gameCode The game code
     * @param phaseId The phase ID
     * @param newPhase The new CollaborativeTextPhase
     * @param txn The Firestore transaction
     */
    public void createCollaborativeTextPhaseInTransaction(String gameCode, String phaseId, 
                                                        CollaborativeTextPhase newPhase, Transaction txn) {
        try {
            DocumentReference gameSessionRef = db.collection("gameSessions").document(gameCode);
            
            // Create the collaborativeTextPhases map if it doesn't exist and add the new phase
            txn.update(gameSessionRef, "collaborativeTextPhases." + phaseId, newPhase);
            
        } catch (Exception e) {
            throw new ResourceException("Failed to create collaborative text phase in transaction", e);
        }
    }

    /**
     * Adds a new TextSubmission to an existing CollaborativeTextPhase atomically
     * This prevents race conditions when multiple players submit simultaneously
     * @param gameCode The game code
     * @param phaseId The phase ID
     * @param newSubmission The new TextSubmission to add
     * @return The updated CollaborativeTextPhase
     */
    public CollaborativeTextPhase addSubmissionAtomically(String gameCode, String phaseId, TextSubmission newSubmission) {
        try {
            return db.runTransaction(transaction -> {
                // Get the current phase
                CollaborativeTextPhase currentPhase = getCollaborativeTextPhaseInTransaction(gameCode, phaseId, transaction);
                
                if (currentPhase == null) {
                    // Create new phase if it doesn't exist
                    currentPhase = new CollaborativeTextPhase();
                    currentPhase.setPhaseId(phaseId);
                    createCollaborativeTextPhaseInTransaction(gameCode, phaseId, currentPhase, transaction);
                }
                
                // Add the new submission to the existing phase
                currentPhase.addSubmission(newSubmission);
                
                // Update the phase in Firestore
                updateCollaborativeTextPhaseInTransaction(gameCode, phaseId, currentPhase, transaction);
                
                return currentPhase;
            }).get();
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new ResourceException("Failed to add submission atomically", e);
        }
    }

    /**
     * Adds a new PlayerVote to an existing CollaborativeTextPhase atomically
     * @param gameCode The game code
     * @param phaseId The phase ID
     * @param playerVote The new PlayerVote to add
     * @return The updated CollaborativeTextPhase
     */
    public CollaborativeTextPhase addVoteAtomically(String gameCode, String phaseId, 
                                                  PlayerVote playerVote) {
        try {
            return db.runTransaction(transaction -> {
                // Get the current phase
                CollaborativeTextPhase currentPhase = getCollaborativeTextPhaseInTransaction(gameCode, phaseId, transaction);
                
                if (currentPhase == null) {
                    throw new ResourceException("Collaborative text phase not found: " + phaseId);
                }
                
                // Add the new vote to the existing phase
                currentPhase.addPlayerVote(playerVote);
                
                // Update the phase in Firestore
                updateCollaborativeTextPhaseInTransaction(gameCode, phaseId, currentPhase, transaction);
                
                return currentPhase;
            }).get();
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new ResourceException("Failed to add vote atomically", e);
        }
    }

    /**
     * Retrieves a CollaborativeTextPhase outside of a transaction (for read operations)
     * @param gameCode The game code
     * @param phaseId The phase ID
     * @return The CollaborativeTextPhase or null if not found
     */
    public CollaborativeTextPhase getCollaborativeTextPhase(String gameCode, String phaseId) {
        try {
            DocumentReference gameSessionRef = db.collection("gameSessions").document(gameCode);
            DocumentSnapshot gameSessionSnapshot = gameSessionRef.get().get();
            
            if (!gameSessionSnapshot.exists()) {
                return null;
            }
            
            // Convert to GameSession object using the same pattern as GameSessionDAO
            GameSession gameSession = gameSessionSnapshot.toObject(GameSession.class);
            if (gameSession == null) {
                return null;
            }
            
            // Get the collaborativeTextPhases from the GameSession object
            Map<String, CollaborativeTextPhase> collaborativeTextPhases = gameSession.getCollaborativeTextPhases();
            if (collaborativeTextPhases == null || !collaborativeTextPhases.containsKey(phaseId)) {
                return null;
            }
            
            return collaborativeTextPhases.get(phaseId);
            
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new ResourceException("Failed to retrieve collaborative text phase", e);
        }
    }

    /**
     * Updates an existing CollaborativeTextPhase atomically.
     * @param gameCode The game code
     * @param phaseId The phase ID
     * @param phase The CollaborativeTextPhase object with updated data
     * @return The updated CollaborativeTextPhase
     */
    public CollaborativeTextPhase updateCollaborativeTextPhaseAtomically(String gameCode, String phaseId, CollaborativeTextPhase phase) {
        try {
            return db.runTransaction(transaction -> {
                // Update the phase in Firestore
                updateCollaborativeTextPhaseInTransaction(gameCode, phaseId, phase, transaction);
                return phase;
            }).get();
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new ResourceException("Failed to update collaborative text phase atomically", e);
        }
    }

    /**
     * Gets available submissions for a player and records views atomically
     * @param gameCode The game code
     * @param phaseId The phase ID
     * @param playerId The player requesting submissions
     * @param requestedCount Number of submissions requested
     * @param outcomeTypeId Optional outcome type ID to filter by (for WHAT_WILL_BECOME_OF_US phase)
     * @return List of available submissions
     */
    public List<TextSubmission> getAvailableSubmissionsForPlayerAtomically(String gameCode, String phaseId, String playerId, int requestedCount, String outcomeTypeId) {
        try {
            return db.runTransaction((Transaction.Function<List<TextSubmission>>) transaction -> {
                // Get the current phase
                CollaborativeTextPhase phase = getCollaborativeTextPhaseInTransaction(gameCode, phaseId, transaction);
                if (phase == null) {
                    return new ArrayList<>();
                }
                
                // Get available submissions
                List<TextSubmission> availableSubmissions = phase.getAvailableSubmissionsForPlayer(playerId, requestedCount, outcomeTypeId);
                
                // Record views for each submission returned
                for (TextSubmission submission : availableSubmissions) {
                    phase.recordSubmissionView(submission.getSubmissionId(), playerId);
                }
                
                // Update the phase with new view records
                updateCollaborativeTextPhaseInTransaction(gameCode, phaseId, phase, transaction);
                
                return availableSubmissions;
            }).get();
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new ResourceException("Failed to get available submissions atomically", e);
        }
    }

    /**
     * Clears all submissions and player votes from a CollaborativeTextPhase atomically
     * This is useful when transitioning between game phases to reset the phase state
     * @param gameCode The game code
     * @param phaseId The phase ID to clear
     */
    public void clearPhaseSubmissionsAndVotes(String gameCode, String phaseId) {
        try {
            db.runTransaction(transaction -> {
                // Get the current phase
                CollaborativeTextPhase phase = getCollaborativeTextPhaseInTransaction(gameCode, phaseId, transaction);
                if (phase == null) {
                    // Phase doesn't exist, nothing to clear
                    return null;
                }
                
                // Clear all submissions and votes
                phase.setSubmissions(new ArrayList<>());
                phase.setPlayerVotes(new HashMap<>());
                phase.setPlayersWhoSubmitted(new ArrayList<>());
                phase.setPlayersWhoVoted(new ArrayList<>());
                phase.setSubmissionViews(new HashMap<>());
                
                // Update the phase in Firestore
                updateCollaborativeTextPhaseInTransaction(gameCode, phaseId, phase, transaction);
                
                return null;
            }).get();
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new ResourceException("Failed to clear phase submissions and votes", e);
        }
    }
}
