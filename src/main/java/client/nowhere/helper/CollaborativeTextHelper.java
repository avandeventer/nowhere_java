package client.nowhere.helper;

import client.nowhere.dao.AdventureMapDAO;
import client.nowhere.dao.CollaborativeTextDAO;
import client.nowhere.dao.GameSessionDAO;
import client.nowhere.exception.ValidationException;
import client.nowhere.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.cloud.Timestamp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Component
public class CollaborativeTextHelper {

    private final GameSessionDAO gameSessionDAO;
    private final CollaborativeTextDAO collaborativeTextDAO;
    private final AdventureMapDAO adventureMapDAO;

    @Autowired
    public CollaborativeTextHelper(GameSessionDAO gameSessionDAO, CollaborativeTextDAO collaborativeTextDAO, AdventureMapDAO adventureMapDAO) {
        this.gameSessionDAO = gameSessionDAO;
        this.collaborativeTextDAO = collaborativeTextDAO;
        this.adventureMapDAO = adventureMapDAO;
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

    public TextSubmission calculateWinningSubmission(String gameCode) {
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

        TextSubmission winningSubmission = calculateWinnerFromVotes(phase);
        
        // Store the winning submission in GameSessionDisplay
        if (winningSubmission != null) {
            updateGameSessionDisplayWithWinningSubmission(gameCode, phaseId, winningSubmission);
        }
        
        return winningSubmission;
    }

    public List<TextSubmission> getAvailableSubmissionsForPlayer(String gameCode, String playerId, int requestedCount) {
        GameSession gameSession = getGameSession(gameCode);
        String phaseId = getPhaseIdForGameState(gameSession.getGameState());

        if (phaseId == null) {
            throw new ValidationException("Current game state does not support collaborative text: " + gameSession.getGameState());
        }
        
        // Use transactional method to get submissions and record views atomically
        return collaborativeTextDAO.getAvailableSubmissionsForPlayerAtomically(gameCode, phaseId, playerId, requestedCount);
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
        newSubmission.setCreatedAt(Timestamp.now());
        newSubmission.setLastModified(Timestamp.now());
        
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
        newSubmission.setCreatedAt(Timestamp.now());
        newSubmission.setLastModified(Timestamp.now());
        
        // Add the new addition to the new submission
        newSubmission.setAdditions(parentSubmission.getAdditions());
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
            case WHERE_ARE_WE, WHERE_ARE_WE_VOTE, WHERE_ARE_WE_VOTE_WINNER -> "WHERE_ARE_WE";
            case WHAT_DO_WE_FEAR, WHAT_DO_WE_FEAR_VOTE, WHAT_DO_WE_FEAR_VOTE_WINNER -> "WHAT_DO_WE_FEAR";
            case WHO_ARE_WE, WHO_ARE_WE_VOTE, WHO_ARE_WE_VOTE_WINNER -> "WHO_ARE_WE";
            case WHAT_IS_OUR_GOAL, WHAT_IS_OUR_GOAL_VOTE, WHAT_IS_OUR_GOAL_VOTE_WINNER -> "WHAT_IS_OUR_GOAL";
            case WHAT_ARE_WE_CAPABLE_OF, WHAT_ARE_WE_CAPABLE_OF_VOTE, WHAT_ARE_WE_CAPABLE_OF_VOTE_WINNERS -> "WHAT_ARE_WE_CAPABLE_OF";
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
            case WHAT_DO_WE_FEAR, WHAT_DO_WE_FEAR_VOTE -> "What do we fear?";
            case WHO_ARE_WE, WHO_ARE_WE_VOTE -> "Who are we?";
            case WHAT_IS_OUR_GOAL, WHAT_IS_OUR_GOAL_VOTE -> "What is our goal?";
            case WHAT_ARE_WE_CAPABLE_OF, WHAT_ARE_WE_CAPABLE_OF_VOTE -> "What are we capable of?";
            default -> "Unknown question";
        };
    }

    private boolean isVotingPhase(GameState gameState) {
        return gameState == GameState.WHERE_ARE_WE_VOTE ||
               gameState == GameState.WHAT_DO_WE_FEAR_VOTE ||
               gameState == GameState.WHO_ARE_WE_VOTE ||
               gameState == GameState.WHAT_IS_OUR_GOAL_VOTE ||
               gameState == GameState.WHAT_ARE_WE_CAPABLE_OF_VOTE;
    }

    // ===== VOTE CALCULATION METHODS =====

    private TextSubmission calculateWinnerFromVotes(CollaborativeTextPhase phase) {
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
            .orElse(null);
    }

    /**
     * Gets submissions for voting phase (excludes player's own submissions)
     * @param gameCode The game code
     * @param playerId The player requesting submissions
     * @return List of submissions ordered by phase
     */
    public List<TextSubmission> getVotingSubmissionsForPlayer(String gameCode, String playerId) {
        GameSession gameSession = getGameSession(gameCode);
        String phaseId = getPhaseIdForGameState(gameSession.getGameState());

        if (phaseId == null) {
            throw new ValidationException("Current game state does not support voting: " + gameSession.getGameState());
        }
        
        // Retrieve the phase
        CollaborativeTextPhase phase = collaborativeTextDAO.getCollaborativeTextPhase(gameCode, phaseId);
        if (phase == null) {
            throw new ValidationException("Collaborative text phase not found for game state: " + gameSession.getGameState());
        }

        // Return top 5 submissions except player's own, ordered by most additions first, then by creation time
        // For WHAT_DO_WE_FEAR, return all submissions; for WHAT_ARE_WE_CAPABLE_OF, return top 6; for others, return top 5
        boolean isWhatDoWeFear = phaseId.equals("WHAT_DO_WE_FEAR");
        boolean isWhatAreWeCapableOf = phaseId.equals("WHAT_ARE_WE_CAPABLE_OF");
        int limit = isWhatDoWeFear ? Integer.MAX_VALUE : (isWhatAreWeCapableOf ? 6 : 5);
        
        return phase.getSubmissions().stream()
                .filter(submission -> !submission.getAuthorId().equals(playerId))
                .sorted((s1, s2) -> {
                    // First sort by number of additions (descending - most additions first)
                    int additionsComparison = Integer.compare(s2.getAdditions().size(), s1.getAdditions().size());
                    if (additionsComparison != 0) {
                        return additionsComparison;
                    }
                    // If additions are equal, sort by creation time (descending - newest first)
                    return s2.getCreatedAt().compareTo(s1.getCreatedAt());
                })
                .limit(limit)
                .toList();
    }

    /**
     * Submits multiple player votes for voting phase
     * @param gameCode The game code
     * @param playerVotes List of player votes with rankings
     * @return Updated collaborative text phase
     */
    public CollaborativeTextPhase submitPlayerVotes(String gameCode, List<PlayerVote> playerVotes) {
        if (playerVotes == null || playerVotes.isEmpty()) {
            throw new ValidationException("Player votes cannot be null or empty");
        }

        GameSession gameSession = getGameSession(gameCode);
        String phaseId = getPhaseIdForGameState(gameSession.getGameState());

        if (phaseId == null) {
            throw new ValidationException("Current game state does not support voting: " + gameSession.getGameState());
        }

        // Submit each vote atomically
        for (PlayerVote vote : playerVotes) {
            collaborativeTextDAO.addVoteAtomically(gameCode, phaseId, vote);
        }

        // Return updated phase
        return collaborativeTextDAO.getCollaborativeTextPhase(gameCode, phaseId);
    }

    /**
     * Updates the GameSessionDisplay with the winning submission for the given phase
     */
    private void updateGameSessionDisplayWithWinningSubmission(String gameCode, String phaseId, TextSubmission winningSubmission) {
        try {
            // Get the current GameSessionDisplay
            GameSessionDisplay display = adventureMapDAO.getGameSessionDisplay(gameCode);
            if (display == null) {
                display = new GameSessionDisplay();
            }

            // Update the appropriate field based on the phase
            switch (phaseId) {
                case "WHERE_ARE_WE" -> {
                    display.setMapDescription(winningSubmission.getCurrentText());
                    display.setWhereAreWeSubmission(winningSubmission);
                }
                case "WHAT_DO_WE_FEAR" -> {
                    // Create a PlayerStat entry for WHAT_DO_WE_FEAR
                    createPlayerStatForFearPhase(gameCode, winningSubmission);
                    System.out.println("WHAT_DO_WE_FEAR winning submission: " + winningSubmission.getCurrentText());
                }
                case "WHO_ARE_WE" -> {
                    display.setPlayerDescription(winningSubmission.getCurrentText());
                    display.setWhoAreWeSubmission(winningSubmission);
                }
                case "WHAT_IS_OUR_GOAL" -> {
                    display.setGoalDescription(winningSubmission.getCurrentText());
                    display.setWhatIsOurGoalSubmission(winningSubmission);
                }
                case "WHAT_ARE_WE_CAPABLE_OF" -> {
                    // Create PlayerStat entries for WHAT_ARE_WE_CAPABLE_OF (top 6)
                    createPlayerStatsForCapablePhase(gameCode, phaseId);
                }
            }

            // Update the GameSessionDisplay in the database
            adventureMapDAO.updateGameSessionDisplay(gameCode, display);
        } catch (Exception e) {
            // Log error but don't fail the main operation
            System.err.println("Failed to update GameSessionDisplay with winning submission: " + e.getMessage());
        }
    }

    /**
     * Creates a PlayerStat entry for WHAT_DO_WE_FEAR phase
     */
    private void createPlayerStatForFearPhase(String gameCode, TextSubmission winningSubmission) {
        try {
            // Get the game session to access the adventure map
            GameSession gameSession = getGameSession(gameCode);
            if (gameSession.getAdventureMap() == null) {
                gameSession.setAdventureMap(new AdventureMap());
            }

            // Create a StatType for the fear entity
            StatType fearStatType = new StatType();
            fearStatType.setLabel("favor");
            fearStatType.setFavorType(true);
            fearStatType.setFavorEntity(winningSubmission.getCurrentText());

            // Add to the adventure map's player stats
            if (gameSession.getAdventureMap().getStatTypes() == null) {
                gameSession.getAdventureMap().setStatTypes(new ArrayList<>());
            }

            List<String> existingStatTypeLabels = gameSession.getAdventureMap().getStatTypes().stream().map(StatType::getFavorEntity).toList();
            if (existingStatTypeLabels.contains(fearStatType.getFavorEntity())) {
                return;
            }

            adventureMapDAO.addStatTypes(gameSession.getGameCode(), List.of(fearStatType));
        } catch (Exception e) {
            System.err.println("Failed to create PlayerStat for WHAT_DO_WE_FEAR: " + e.getMessage());
        }
    }

    /**
     * Creates PlayerStat entries for WHAT_ARE_WE_CAPABLE_OF phase (top 6)
     */
    private void createPlayerStatsForCapablePhase(String gameCode, String phaseId) {
        try {
            // Get the collaborative text phase to get all submissions
            CollaborativeTextPhase phase = collaborativeTextDAO.getCollaborativeTextPhase(gameCode, phaseId);
            if (phase == null || phase.getSubmissions().isEmpty()) {
                return;
            }

            // Get the game session to access the adventure map
            GameSession gameSession = getGameSession(gameCode);
            if (gameSession.getAdventureMap() == null) {
                gameSession.setAdventureMap(new AdventureMap());
            }

            // Calculate rankings for all submissions
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

            // Get top 6 submissions (lowest average ranking = best)
            List<TextSubmission> topSubmissions = phase.getSubmissions().stream()
                .filter(submission -> submission.getAverageRanking() > 0)
                .sorted(Comparator.comparingDouble(TextSubmission::getAverageRanking))
                .limit(6)
                .toList();

            // Create PlayerStat entries for each winning submission
            if (gameSession.getAdventureMap().getStatTypes() == null) {
                gameSession.getAdventureMap().setStatTypes(new ArrayList<>());
            }

            List<StatType> statTypes = new ArrayList<>();
            List<String> existingStatTypeLabels = gameSession.getAdventureMap().getStatTypes().stream().map(StatType::getLabel).toList();
            for (TextSubmission submission : topSubmissions) {
                StatType capableStatType = new StatType();
                capableStatType.setLabel(submission.getCurrentText());
                capableStatType.setFavorType(false);
                capableStatType.setFavorEntity("");
                if (!existingStatTypeLabels.contains(submission.getCurrentText())) {
                    statTypes.add(capableStatType);
                }
            }

            // Update the game session
            adventureMapDAO.addStatTypes(gameSession.getGameCode(), statTypes);
        } catch (Exception e) {
            System.err.println("Failed to create PlayerStats for WHAT_ARE_WE_CAPABLE_OF: " + e.getMessage());
        }
    }
}