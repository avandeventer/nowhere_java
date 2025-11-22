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
    private final AdventureMapHelper adventureMapHelper;

    @Autowired
    public CollaborativeTextHelper(GameSessionDAO gameSessionDAO, CollaborativeTextDAO collaborativeTextDAO, AdventureMapDAO adventureMapDAO, AdventureMapHelper adventureMapHelper) {
        this.gameSessionDAO = gameSessionDAO;
        this.collaborativeTextDAO = collaborativeTextDAO;
        this.adventureMapDAO = adventureMapDAO;
        this.adventureMapHelper = adventureMapHelper;
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
        if (textAddition == null) {
            throw new ValidationException("Text addition cannot be null");
        }
        if (textAddition.getAuthorId() == null || textAddition.getAuthorId().trim().isEmpty()) {
            throw new ValidationException("Author ID cannot be null or empty");
        }
        if (textAddition.getAddedText() == null || textAddition.getAddedText().trim().isEmpty()) {
            throw new ValidationException("Added text cannot be null or empty");
        }

        GameSession gameSession = getGameSession(gameCode);
        GameState phaseIdState = gameSession.getGameState().getPhaseId();
        if (phaseIdState == null) {
            throw new ValidationException("Current game state does not support collaborative text: " + gameSession.getGameState());
        }
        String phaseId = phaseIdState.name();

        TextSubmission newSubmission;
        if (textAddition.getSubmissionId() != null && !textAddition.getSubmissionId().trim().isEmpty()) {
            newSubmission = createBranchedSubmission(gameSession, textAddition, phaseId);
        } else {
            newSubmission = createNewSubmission(textAddition, gameSession);
        }

        return collaborativeTextDAO.addSubmissionAtomically(gameCode, phaseId, newSubmission);
    }

    public CollaborativeTextPhase getCollaborativeTextPhase(String gameCode) {
        GameSession gameSession = getGameSession(gameCode);
        GameState phaseIdState = gameSession.getGameState().getPhaseId();
        if (phaseIdState == null) {
            throw new ValidationException("Current game state does not support collaborative text: " + gameSession.getGameState());
        }
        String phaseId = phaseIdState.name();

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
        GameState phaseIdState = gameSession.getGameState().getPhaseId();
        if (phaseIdState == null) {
            throw new ValidationException("Current game state does not support voting: " + gameSession.getGameState());
        }
        String phaseId = phaseIdState.name();

        // Add vote atomically using Firestore transactions
        return collaborativeTextDAO.addVoteAtomically(gameCode, phaseId, playerVote);
    }

    public List<TextSubmission> calculateWinningSubmission(String gameCode) {
        GameSession gameSession = getGameSession(gameCode);
        GameState phaseIdState = gameSession.getGameState().getPhaseId();
        if (phaseIdState == null) {
            throw new ValidationException("Current game state does not support collaborative text: " + gameSession.getGameState());
        }
        String phaseId = phaseIdState.name();

        // Get phase from DAO
        CollaborativeTextPhase phase = collaborativeTextDAO.getCollaborativeTextPhase(gameCode, phaseId);
        if (phase == null) {
            throw new ValidationException("Collaborative text phase not found for game state: " + gameSession.getGameState());
        }

        List<TextSubmission> winningSubmissions = calculateWinnersFromVotes(phase, gameSession.getGameState());
        
        // Store the winning submissions in GameSessionDisplay
        if (!winningSubmissions.isEmpty()) {
            updateGameSessionDisplayWithWinningSubmissions(gameCode, phaseId, winningSubmissions);
        }
        
        return winningSubmissions;
    }

    public List<TextSubmission> getAvailableSubmissionsForPlayer(String gameCode, String playerId, int requestedCount) {
        GameSession gameSession = getGameSession(gameCode);
        GameState phaseIdState = gameSession.getGameState().getPhaseId();
        if (phaseIdState == null) {
            throw new ValidationException("Current game state does not support collaborative text: " + gameSession.getGameState());
        }
        String phaseId = phaseIdState.name();
        
        // Use transactional method to get submissions and record views atomically
        return collaborativeTextDAO.getAvailableSubmissionsForPlayerAtomically(gameCode, phaseId, playerId, requestedCount);
    }


    // ===== SUBMISSION CREATION LOGIC =====

    /**
     * Creates a new submission with empty originalText
     */
    private TextSubmission createNewSubmission(TextAddition textAddition, GameSession gameSession) {
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
        
        // Assign outcome type for WHAT_WILL_BECOME_OF_US phase
        // Use outcomeType from TextAddition if provided, otherwise calculate based on player order
        if (gameSession.getGameState() == GameState.WHAT_WILL_BECOME_OF_US) {
            String outcomeType = textAddition.getOutcomeType();
            newSubmission.setOutcomeType(outcomeType);
        }
        
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
        
        // Preserve the parent's outcome type if it exists
        if (parentSubmission.getOutcomeType() != null && !parentSubmission.getOutcomeType().trim().isEmpty()) {
            newSubmission.setOutcomeType(parentSubmission.getOutcomeType());
        }
        
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

    /**
     * Gets the outcome type assigned to a player based on their order in the game (joinedAt)
     * Players are sorted by joinedAt, then assigned outcome types in a round-robin fashion:
     * index % 3 = 0 -> "success"
     * index % 3 = 1 -> "neutral"
     * index % 3 = 2 -> "failure"
     * @param gameCode The game code
     * @param playerId The player's ID
     * @return The assigned outcome type ("success", "neutral", or "failure")
     */
    public String getOutcomeTypeForPlayer(String gameCode, String playerId) {
        GameSession gameSession = getGameSession(gameCode);
        return assignOutcomeTypeToPlayer(gameSession, playerId);
    }

    /**
     * Assigns an outcome type to a player based on their order in the game (joinedAt)
     * Players are sorted by joinedAt, then assigned outcome types in a round-robin fashion:
     * index % 3 = 0 -> "success"
     * index % 3 = 1 -> "neutral"
     * index % 3 = 2 -> "failure"
     * @param gameSession The game session
     * @param playerId The player's ID
     * @return The assigned outcome type ("success", "neutral", or "failure")
     */
    private String assignOutcomeTypeToPlayer(GameSession gameSession, String playerId) {
        if (gameSession.getPlayers() == null || gameSession.getPlayers().isEmpty()) {
            throw new ValidationException("No players found in game session");
        }

        // Sort players by joinedAt timestamp
        List<Player> sortedPlayers = gameSession.getPlayers().stream()
                .filter(player -> player.getJoinedAt() != null)
                .sorted(Comparator.comparing(Player::getJoinedAt))
                .toList();

        // Find the player's index in the sorted list
        int playerIndex = -1;
        for (int i = 0; i < sortedPlayers.size(); i++) {
            if (sortedPlayers.get(i).getAuthorId().equals(playerId)) {
                playerIndex = i;
                break;
            }
        }

        if (playerIndex == -1) {
            throw new ValidationException("Player not found in game session: " + playerId);
        }

        // Assign outcome type based on index modulo 3
        int outcomeIndex = playerIndex % 3;
        return switch (outcomeIndex) {
            case 0 -> "success";
            case 1 -> "neutral";
            case 2 -> "failure";
            default -> "neutral"; // Should never reach here, but provide a default
        };
    }

    // ===== GAME STATE HELPER METHODS =====

    private CollaborativeTextPhase createCollaborativeTextPhaseForGameState(GameState gameState) {
        GameState phaseIdState = gameState.getPhaseId();
        String phaseId = phaseIdState != null ? phaseIdState.name() : null;
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
            case WHAT_IS_COMING, WHAT_IS_COMING_VOTE -> "What is coming?";
            case WHAT_ARE_WE_CAPABLE_OF, WHAT_ARE_WE_CAPABLE_OF_VOTE -> "What are we capable of?";
            case WHAT_WILL_BECOME_OF_US, WHAT_WILL_BECOME_OF_US_VOTE -> "What will become of us?";
            default -> "Unknown question";
        };
    }

    private boolean isVotingPhase(GameState gameState) {
        return gameState == GameState.WHERE_ARE_WE_VOTE ||
               gameState == GameState.WHAT_DO_WE_FEAR_VOTE ||
               gameState == GameState.WHO_ARE_WE_VOTE ||
               gameState == GameState.WHAT_IS_COMING_VOTE ||
               gameState == GameState.WHAT_ARE_WE_CAPABLE_OF_VOTE ||
               gameState == GameState.WHAT_WILL_BECOME_OF_US_VOTE;
    }

    // ===== VOTE CALCULATION METHODS =====

    private List<TextSubmission> calculateWinnersFromVotes(CollaborativeTextPhase phase, GameState gameState) {
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

        // For WHAT_ARE_WE_CAPABLE_OF, return top 6 submissions
        if (gameState == GameState.WHAT_ARE_WE_CAPABLE_OF_VOTE_WINNERS) {
            return phase.getSubmissions().stream()
                .filter(submission -> submission.getAverageRanking() > 0)
                .sorted(Comparator.comparingDouble(TextSubmission::getAverageRanking))
                .limit(6)
                .toList();
        } else if (gameState == GameState.WHAT_WILL_BECOME_OF_US_VOTE_WINNER) {
            // For WHAT_WILL_BECOME_OF_US, return the best submission for each outcome type (success, neutral, failure)
            List<TextSubmission> winners = new ArrayList<>();
            for (String outcomeType : List.of("success", "neutral", "failure")) {
                TextSubmission winner = phase.getSubmissions().stream()
                    .filter(submission -> submission.getAverageRanking() > 0)
                    .filter(submission -> outcomeType.equals(submission.getOutcomeType()))
                    .min((s1, s2) -> Double.compare(s1.getAverageRanking(), s2.getAverageRanking()))
                    .orElse(null);
                if (winner != null) {
                    winners.add(winner);
                }
            }
            return winners;
        } else {
            // For other phases, return the single best submission
            TextSubmission winner = phase.getSubmissions().stream()
                .filter(submission -> submission.getAverageRanking() > 0)
                .min((s1, s2) -> Double.compare(s1.getAverageRanking(), s2.getAverageRanking()))
                .orElse(null);
            
            return winner != null ? List.of(winner) : List.of();
        }
    }

    /**
     * Gets submissions for voting phase (excludes player's own submissions)
     * @param gameCode The game code
     * @param playerId The player requesting submissions
     * @return List of submissions ordered by phase
     */
    public List<TextSubmission> getVotingSubmissionsForPlayer(String gameCode, String playerId) {
        GameSession gameSession = getGameSession(gameCode);
        GameState phaseIdState = gameSession.getGameState().getPhaseId();
        if (phaseIdState == null) {
            throw new ValidationException("Current game state does not support voting: " + gameSession.getGameState());
        }
        String phaseId = phaseIdState.name();
        
        // Retrieve the phase
        CollaborativeTextPhase phase = collaborativeTextDAO.getCollaborativeTextPhase(gameCode, phaseId);
        if (phase == null) {
            throw new ValidationException("Collaborative text phase not found for game state: " + gameSession.getGameState());
        }

        // Return top 5 submissions except player's own, ordered by most additions first, then by creation time
        // For WHAT_DO_WE_FEAR, return all submissions; for WHAT_ARE_WE_CAPABLE_OF, return top 6; for others, return top 5
        // For WHAT_WILL_BECOME_OF_US, include player's own submissions (they'll be filtered by outcomeType on frontend)
        boolean isWhatDoWeFear = phaseIdState == GameState.WHAT_DO_WE_FEAR;
        boolean isWhatAreWeCapableOf = phaseIdState == GameState.WHAT_ARE_WE_CAPABLE_OF;
        boolean isWhatWillBecomeOfUs = phaseIdState == GameState.WHAT_WILL_BECOME_OF_US;
        int limit = isWhatDoWeFear ? Integer.MAX_VALUE : (isWhatAreWeCapableOf ? 6 : 5);
        
        return phase.getSubmissions().stream()
                .filter(submission -> isWhatWillBecomeOfUs || !submission.getAuthorId().equals(playerId))
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
        GameState phaseIdState = gameSession.getGameState().getPhaseId();
        if (phaseIdState == null) {
            throw new ValidationException("Current game state does not support voting: " + gameSession.getGameState());
        }
        String phaseId = phaseIdState.name();

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
    private void updateGameSessionDisplayWithWinningSubmissions(String gameCode, String phaseId, List<TextSubmission> winningSubmissions) {
        try {
            // Get the current GameSessionDisplay
            GameSessionDisplay display = adventureMapDAO.getGameSessionDisplay(gameCode);
            if (display == null) {
                display = new GameSessionDisplay();
            }

            // Update the appropriate field based on the phase
            switch (phaseId) {
                case "WHERE_ARE_WE" -> {
                    if (!winningSubmissions.isEmpty()) {
                        TextSubmission winningSubmission = winningSubmissions.getFirst();
                        display.setMapDescription(winningSubmission.getCurrentText());
                        display.setWhereAreWeSubmission(winningSubmission);
                    }
                }
                case "WHAT_DO_WE_FEAR" -> {
                    if (!winningSubmissions.isEmpty()) {
                        // Create a PlayerStat entry for WHAT_DO_WE_FEAR
                        createPlayerStatForFearPhase(gameCode, winningSubmissions.getFirst());
                        display.setEntity(winningSubmissions.getFirst().getCurrentText());
                        System.out.println("WHAT_DO_WE_FEAR winning submission: " + winningSubmissions.getFirst().getCurrentText());
                    }
                }
                case "WHO_ARE_WE" -> {
                    if (!winningSubmissions.isEmpty()) {
                        TextSubmission winningSubmission = winningSubmissions.getFirst();
                        display.setPlayerDescription(winningSubmission.getCurrentText());
                        display.setWhoAreWeSubmission(winningSubmission);
                    }
                }
                case "WHAT_IS_COMING" -> {
                    if (!winningSubmissions.isEmpty()) {
                        TextSubmission winningSubmission = winningSubmissions.getFirst();
                        display.setGoalDescription(winningSubmission.getCurrentText());
                        display.setWhatIsOurGoalSubmission(winningSubmission);
                    }
                }
                case "WHAT_ARE_WE_CAPABLE_OF" -> {
                    // Create PlayerStat entries for WHAT_ARE_WE_CAPABLE_OF (top 6)
                    createPlayerStatsForCapablePhase(gameCode, phaseId, winningSubmissions);
                }
                case "WHAT_WILL_BECOME_OF_US" -> {
                    // Set successText, neutralText, and failureText based on outcomeType
                    for (TextSubmission winningSubmission : winningSubmissions) {
                        String outcomeType = winningSubmission.getOutcomeType();
                        if (outcomeType != null && !outcomeType.trim().isEmpty()) {
                            switch (outcomeType) {
                                case "success" -> display.setSuccessText(winningSubmission.getCurrentText());
                                case "neutral" -> display.setNeutralText(winningSubmission.getCurrentText());
                                case "failure" -> display.setFailureText(winningSubmission.getCurrentText());
                            }
                        }
                    }
                }
            }

            // Update the GameSessionDisplay in the database
            adventureMapDAO.updateGameSessionDisplay(gameCode, display);
        } catch (Exception e) {
            // Log error but don't fail the main operation
            System.err.println("Failed to update GameSessionDisplay with winning submissions: " + e.getMessage());
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
    private void createPlayerStatsForCapablePhase(String gameCode, String phaseId, List<TextSubmission> winningSubmissions) {
        try {
            // Get the game session to access the adventure map
            GameSession gameSession = getGameSession(gameCode);
            if (gameSession.getAdventureMap() == null) {
                gameSession.setAdventureMap(new AdventureMap());
            }

            // Create PlayerStat entries for each winning submission
            if (gameSession.getAdventureMap().getStatTypes() == null) {
                gameSession.getAdventureMap().setStatTypes(new ArrayList<>());
            }

            List<StatType> statTypes = new ArrayList<>();
            List<String> existingStatTypeLabels = gameSession.getAdventureMap().getStatTypes().stream().map(StatType::getLabel).toList();
            for (TextSubmission submission : winningSubmissions) {
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

    /**
     * Gets the phase information for collaborative text based on the current game state
     * @param gameCode The game code
     * @return CollaborativeTextPhaseInfo with phase question, instructions, mode, and mode instructions
     */
    public CollaborativeTextPhaseInfo getCollaborativeTextPhaseInfo(String gameCode) {
        GameSession gameSession = getGameSession(gameCode);
        GameState gameState = gameSession.getGameState();
        GameSessionDisplay gameSessionDisplay = adventureMapHelper.getGameSessionDisplay(gameCode);
        String entityName = gameSessionDisplay.getEntity() != null && !gameSessionDisplay.getEntity().isEmpty() 
            ? gameSessionDisplay.getEntity() 
            : "the Entity";

        PhaseBaseInfo baseInfo = gameState.getPhaseBaseInfo(entityName);
        PhaseMode phaseMode = determinePhaseMode(gameState, baseInfo);

        String phaseInstructions = getPhaseInstructionsForMode(gameState, phaseMode, baseInfo);
        
        String collaborativeModeInstructions = getCollaborativeModeInstructions(
            baseInfo.collaborativeMode(), 
            phaseMode, 
            gameState
        );

        return new CollaborativeTextPhaseInfo(
            baseInfo.phaseQuestion(),
            phaseInstructions,
            baseInfo.collaborativeMode(),
            collaborativeModeInstructions,
            phaseMode
        );
    }

    /**
     * Determines the phase mode by checking which game state in PhaseBaseInfo matches the current game state
     */
    private PhaseMode determinePhaseMode(GameState gameState, PhaseBaseInfo baseInfo) {
        if (gameState == baseInfo.collaboratingState()) {
            return PhaseMode.COLLABORATING;
        } else if (gameState == baseInfo.votingState()) {
            return PhaseMode.VOTING;
        } else if (gameState == baseInfo.winningState()) {
            return PhaseMode.WINNING;
        }
        // Default fallback
        return PhaseMode.COLLABORATING;
    }

    /**
     * Gets phase instructions based on the phase mode
     */
    private String getPhaseInstructionsForMode(GameState gameState, PhaseMode phaseMode, PhaseBaseInfo baseInfo) {
        return switch (phaseMode) {
            case COLLABORATING -> baseInfo.baseInstructions();
            case VOTING -> "The time has come to solidify our fate. Rank the descriptions on your device starting with your favorite first.";
            case WINNING -> {
                if (gameState == GameState.WHAT_WILL_BECOME_OF_US_VOTE_WINNER) {
                    yield "The threads before us have now been sealed. Only our choices ahead can reveal them to us. Now we must build this place.";
                } else if (gameState == GameState.WHAT_ARE_WE_CAPABLE_OF_VOTE_WINNERS) {
                    yield "The winning submissions are...";
                } else {
                    yield "The winning submission is...";
                }
            }
        };
    }

    /**
     * Gets collaborative mode instructions, reusing logic based on collaborative mode and phase mode
     */
    private String getCollaborativeModeInstructions(CollaborativeMode collaborativeMode, PhaseMode phaseMode, GameState gameState) {
        // For COLLABORATING phase, return mode-specific instructions
        if (phaseMode == PhaseMode.COLLABORATING) {
            if (collaborativeMode == CollaborativeMode.RAPID_FIRE) {
                return "Submit as many ideas as you can from your device!";
            } else {
                // SHARE_TEXT mode
                if (gameState == GameState.WHAT_WILL_BECOME_OF_US) {
                    return "Your friends will help, but each of us starts with a different prompt for this one!";
                } else {
                    return "Look to your device and don't worry about thinking too hard about what you say. Your friends will help!";
                }
            }
        }
        
        // For VOTING and WINNING phases, return empty string (instructions are in phaseInstructions)
        return "";
    }
}