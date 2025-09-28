package client.nowhere.helper;

import client.nowhere.dao.CollaborativeTextDAO;
import client.nowhere.dao.GameSessionDAO;
import client.nowhere.exception.ValidationException;
import client.nowhere.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Component
public class CollaborativeTextHelper {

    private final GameSessionDAO gameSessionDAO;
    private final CollaborativeTextDAO collaborativeTextDAO;

    @Autowired
    public CollaborativeTextHelper(GameSessionDAO gameSessionDAO, CollaborativeTextDAO collaborativeTextDAO) {
        this.gameSessionDAO = gameSessionDAO;
        this.collaborativeTextDAO = collaborativeTextDAO;
    }

    // ===== PUBLIC API METHODS =====

    /**
     * Unified method for text submissions - creates new submission or branches from existing one
     * Uses atomic Firestore transactions to prevent race conditions
     * @param gameCode The game code
     * @param textAddition The text addition (required)
     * @return The updated collaborative text phase
     */
    public CollaborativeTextPhase submitTextAddition(String gameCode, TextAddition textAddition) {
        // Validate input
        if (textAddition == null) {
            throw new ValidationException("Text addition cannot be null");
        }
        if (textAddition.getAuthorId() == null || textAddition.getAuthorId().trim().isEmpty()) {
            throw new ValidationException("Author ID cannot be null or empty");
        }
        if (textAddition.getAddedText() == null || textAddition.getAddedText().trim().isEmpty()) {
            throw new ValidationException("Added text cannot be null or empty");
        }

        // Get game session to determine phase ID
        GameSession gameSession = getGameSession(gameCode);
        String phaseId = getPhaseIdForGameState(gameSession.getGameState());
        if (phaseId == null) {
            throw new ValidationException("Current game state does not support collaborative text: " + gameSession.getGameState());
        }

        // Create the appropriate TextSubmission
        TextSubmission newSubmission;
        if (textAddition.getSubmissionId() != null && !textAddition.getSubmissionId().trim().isEmpty()) {
            // Branching: Create new submission based on parent + addition
            newSubmission = createBranchedSubmission(gameSession, textAddition, phaseId);
        } else {
            // New submission: Create new submission with empty originalText
            newSubmission = createNewSubmission(textAddition);
        }

        // Add submission atomically using Firestore transactions
        return collaborativeTextDAO.addSubmissionAtomically(gameCode, phaseId, newSubmission);
    }

    public CollaborativeTextPhase getCollaborativeTextPhase(String gameCode) {
        GameSession gameSession = getGameSession(gameCode);
        String phaseId = getPhaseIdForGameState(gameSession.getGameState());
        if (phaseId == null) {
            throw new ValidationException("Current game state does not support collaborative text: " + gameSession.getGameState());
        }

        // Get phase from DAO
        CollaborativeTextPhase phase = collaborativeTextDAO.getCollaborativeTextPhase(gameCode, phaseId);
        if (phase == null) {
            // Create a new phase if it doesn't exist
            phase = createCollaborativeTextPhaseForGameState(gameSession.getGameState());
            // Note: We would need to add a method to create phases atomically if needed
            // For now, this will be handled by the first submission
            throw new ValidationException("Collaborative text phase not found. Please submit text first.");
        }

        return phase;
    }

    public CollaborativeTextPhase submitPlayerVote(String gameCode, PlayerVote playerVote) {
        // Validate input
        if (playerVote == null) {
            throw new ValidationException("Player vote cannot be null");
        }
        if (playerVote.getPlayerId() == null || playerVote.getPlayerId().trim().isEmpty()) {
            throw new ValidationException("Player ID cannot be null or empty");
        }
        if (playerVote.getSubmissionId() == null || playerVote.getSubmissionId().trim().isEmpty()) {
            throw new ValidationException("Submission ID cannot be null or empty");
        }
        if (playerVote.getRanking() < 1 || playerVote.getRanking() > 3) {
            throw new ValidationException("Ranking must be between 1 and 3");
        }

        // Get game session to determine phase ID
        GameSession gameSession = getGameSession(gameCode);
        String phaseId = getPhaseIdForGameState(gameSession.getGameState());
        if (phaseId == null) {
            throw new ValidationException("Current game state does not support voting: " + gameSession.getGameState());
        }

        // Add vote atomically using Firestore transactions
        return collaborativeTextDAO.addVoteAtomically(gameCode, phaseId, playerVote);
    }

    public String calculateWinningSubmission(String gameCode) {
        GameSession gameSession = getGameSession(gameCode);
        String phaseId = getPhaseIdForGameState(gameSession.getGameState());
        if (phaseId == null) {
            throw new ValidationException("Current game state does not support collaborative text: " + gameSession.getGameState());
        }

        // Get phase from DAO
        CollaborativeTextPhase phase = collaborativeTextDAO.getCollaborativeTextPhase(gameCode, phaseId);
        if (phase == null) {
            throw new ValidationException("Collaborative text phase not found for game state: " + gameSession.getGameState());
        }

        return calculateWinnerFromVotes(phase);
    }

    public List<TextSubmission> getAvailableSubmissionsForPlayer(String gameCode, String playerId) {
        GameSession gameSession = getGameSession(gameCode);
        String phaseId = getPhaseIdForGameState(gameSession.getGameState());

        if (phaseId == null) {
            throw new ValidationException("Current game state does not support collaborative text: " + gameSession.getGameState());
        }
        
        // Retrieve the phase (non-atomically for reads)
        CollaborativeTextPhase phase = collaborativeTextDAO.getCollaborativeTextPhase(gameCode, phaseId);
        if (phase == null) {
            throw new ValidationException("Collaborative text phase not found for game state: " + gameSession.getGameState());
        }

        // Get available submissions for the player (max 2 views per submission)
        int maxViews = 2;
        return phase.getAvailableSubmissionsForPlayer(playerId, maxViews);
    }

    public void recordSubmissionView(String gameCode, String playerId, String submissionId) {
        GameSession gameSession = getGameSession(gameCode);
        String phaseId = getPhaseIdForGameState(gameSession.getGameState());

        if (phaseId == null) {
            throw new ValidationException("Current game state does not support collaborative text: " + gameSession.getGameState());
        }
        
        // Retrieve the phase (non-atomically for reads)
        CollaborativeTextPhase phase = collaborativeTextDAO.getCollaborativeTextPhase(gameCode, phaseId);
        if (phase == null) {
            throw new ValidationException("Collaborative text phase not found for game state: " + gameSession.getGameState());
        }

        // Record the view (max 2 views per submission)
        int maxViews = 2;
        phase.recordSubmissionView(playerId, submissionId, maxViews);
        
        // Update the phase in the database
        collaborativeTextDAO.updateCollaborativeTextPhaseAtomically(gameCode, phaseId, phase);
    }

    // ===== SUBMISSION CREATION LOGIC =====

    /**
     * Creates a new submission with empty originalText
     */
    private TextSubmission createNewSubmission(TextAddition textAddition) {
        String newSubmissionId = UUID.randomUUID().toString();
        
        TextSubmission newSubmission = new TextSubmission();
        newSubmission.setSubmissionId(newSubmissionId);
        newSubmission.setAuthorId(textAddition.getAuthorId());
        newSubmission.setOriginalText(""); // Empty as requested
        newSubmission.setCurrentText(textAddition.getAddedText());
        newSubmission.setCreatedAt(LocalDateTime.now());
        newSubmission.setLastModified(LocalDateTime.now());
        
        // Add the text addition to the new submission
        newSubmission.addTextAddition(textAddition);
        
        return newSubmission;
    }

    /**
     * Creates a new submission that branches from an existing parent submission
     * The new submission contains the parent's current text plus the new addition
     */
    private TextSubmission createBranchedSubmission(GameSession gameSession, TextAddition textAddition, String phaseId) {
        // Get the current phase to find the parent submission
        CollaborativeTextPhase currentPhase = collaborativeTextDAO.getCollaborativeTextPhase(gameSession.getGameCode(), phaseId);
        if (currentPhase == null) {
            throw new ValidationException("Collaborative text phase not found. Cannot branch from non-existent phase.");
        }

        // Find the parent submission and validate it exists
        TextSubmission parentSubmission = currentPhase.getSubmissionById(textAddition.getSubmissionId());
        if (parentSubmission == null) {
            throw new ValidationException("Parent submission not found: " + textAddition.getSubmissionId());
        }

        // Create new submission ID
        String newSubmissionId = UUID.randomUUID().toString();
        
        // Create new submission with parent's current text + new addition
        String newCurrentText = parentSubmission.getCurrentText() + " " + textAddition.getAddedText();
        
        TextSubmission newSubmission = new TextSubmission();
        newSubmission.setSubmissionId(newSubmissionId);
        newSubmission.setAuthorId(textAddition.getAuthorId()); // Use the new author's ID
        newSubmission.setOriginalText(parentSubmission.getCurrentText()); // Base text from parent
        newSubmission.setCurrentText(newCurrentText);
        newSubmission.setCreatedAt(LocalDateTime.now());
        newSubmission.setLastModified(LocalDateTime.now());
        
        // Add the new addition to the new submission
        newSubmission.addTextAddition(textAddition);
        
        return newSubmission;
    }

    // ===== GENERALIZED VALIDATION METHODS =====

    private GameSession getGameSession(String gameCode) {
        GameSession gameSession = gameSessionDAO.getGame(gameCode);
        if (gameSession == null) {
            throw new ValidationException("Game session not found: " + gameCode);
        }
        return gameSession;
    }

    // ===== GAME STATE HELPER METHODS =====

    private String getPhaseIdForGameState(GameState gameState) {
        return switch (gameState) {
            case WHERE_ARE_WE, WHERE_ARE_WE_VOTE -> "WHERE_ARE_WE";
            case WHO_ARE_WE, WHO_ARE_WE_VOTE -> "WHO_ARE_WE";
            case WHAT_IS_OUR_GOAL, WHAT_IS_OUR_GOAL_VOTE -> "WHAT_IS_OUR_GOAL";
            case WHAT_ARE_WE_CAPABLE_OF, WHAT_ARE_WE_CAPABLE_OF_VOTE -> "WHAT_ARE_WE_CAPABLE_OF";
            default -> null;
        };
    }

    private CollaborativeTextPhase createCollaborativeTextPhaseForGameState(GameState gameState) {
        String phaseId = getPhaseIdForGameState(gameState);
        String question = getQuestionForGameState(gameState);
        CollaborativeTextPhase.PhaseType phaseType = isVotingPhase(gameState) ? 
            CollaborativeTextPhase.PhaseType.VOTING : CollaborativeTextPhase.PhaseType.SUBMISSION;
        
        return new CollaborativeTextPhase(phaseId, question, phaseType);
    }

    private String getQuestionForGameState(GameState gameState) {
        return switch (gameState) {
            case WHERE_ARE_WE, WHERE_ARE_WE_VOTE -> "Where are we?";
            case WHO_ARE_WE, WHO_ARE_WE_VOTE -> "Who are we?";
            case WHAT_IS_OUR_GOAL, WHAT_IS_OUR_GOAL_VOTE -> "What is our goal?";
            case WHAT_ARE_WE_CAPABLE_OF, WHAT_ARE_WE_CAPABLE_OF_VOTE -> "What are we capable of?";
            default -> "Unknown question";
        };
    }

    private boolean isVotingPhase(GameState gameState) {
        return gameState == GameState.WHERE_ARE_WE_VOTE ||
               gameState == GameState.WHO_ARE_WE_VOTE ||
               gameState == GameState.WHAT_IS_OUR_GOAL_VOTE ||
               gameState == GameState.WHAT_ARE_WE_CAPABLE_OF_VOTE;
    }

    // ===== VOTE CALCULATION METHODS =====

    private String calculateWinnerFromVotes(CollaborativeTextPhase phase) {
        // Calculate average ranking for each submission
        for (TextSubmission submission : phase.getSubmissions()) {
            List<PlayerVote> votesForSubmission = phase.getPlayerVotes().values().stream()
                .flatMap(List::stream)
                .filter(vote -> vote.getSubmissionId().equals(submission.getSubmissionId()))
                .toList();

            if (!votesForSubmission.isEmpty()) {
                double averageRanking = votesForSubmission.stream()
                    .mapToInt(PlayerVote::getRanking)
                    .average()
                    .orElse(0.0);
                submission.setAverageRanking(averageRanking);
            }
        }

        // Find submission with the lowest average ranking (best ranking)
        return phase.getSubmissions().stream()
            .filter(submission -> submission.getAverageRanking() > 0)
            .min((s1, s2) -> Double.compare(s1.getAverageRanking(), s2.getAverageRanking()))
            .map(TextSubmission::getCurrentText)
            .orElse(null);
    }
}