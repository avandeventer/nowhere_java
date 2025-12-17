package client.nowhere.helper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import client.nowhere.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.cloud.Timestamp;

import client.nowhere.dao.AdventureMapDAO;
import client.nowhere.dao.CollaborativeTextDAO;
import client.nowhere.dao.GameSessionDAO;
import client.nowhere.dao.StoryDAO;
import client.nowhere.exception.ValidationException;
import client.nowhere.constants.AuthorConstants;

@Component
public class CollaborativeTextHelper {

    private final GameSessionDAO gameSessionDAO;
    private final CollaborativeTextDAO collaborativeTextDAO;
    private final AdventureMapDAO adventureMapDAO;
    private final AdventureMapHelper adventureMapHelper;
    private final StoryDAO storyDAO;

    @Autowired
    public CollaborativeTextHelper(GameSessionDAO gameSessionDAO, CollaborativeTextDAO collaborativeTextDAO, AdventureMapDAO adventureMapDAO, AdventureMapHelper adventureMapHelper, StoryDAO storyDAO) {
        this.gameSessionDAO = gameSessionDAO;
        this.collaborativeTextDAO = collaborativeTextDAO;
        this.adventureMapDAO = adventureMapDAO;
        this.adventureMapHelper = adventureMapHelper;
        this.storyDAO = storyDAO;
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
            throw new ValidationException("Collaborative text phase not found. Please submit text first.");
        }

        return phase;
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
            updateGameSessionWithWinningSubmissions(gameCode, phaseId, winningSubmissions);
        }

        return winningSubmissions;
    }

    public List<TextSubmission> getAvailableSubmissionsForPlayer(String gameCode, String playerId, int requestedCount, String outcomeTypeId) {
        GameSession gameSession = getGameSession(gameCode);
        GameState phaseIdState = gameSession.getGameState().getPhaseId();
        if (phaseIdState == null) {
            throw new ValidationException("Current game state does not support collaborative text: " + gameSession.getGameState());
        }
        String phaseId = phaseIdState.name();

        if (outcomeTypeId == null || outcomeTypeId.trim().isEmpty()) {
            OutcomeType outcomeType = getOutcomeTypeForPlayer(gameCode, playerId);
            if (outcomeType != null && outcomeType.getId() != null) {
                outcomeTypeId = outcomeType.getId();
            }
        }

        return collaborativeTextDAO.getAvailableSubmissionsForPlayerAtomically(gameCode, phaseId, playerId, requestedCount, outcomeTypeId);
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
        
        // Assign optionId as outcomeType for HOW_DOES_THIS_RESOLVE phase
        if (gameSession.getGameState() == GameState.HOW_DOES_THIS_RESOLVE) {
            String optionId = textAddition.getOutcomeType(); // outcomeType contains optionId for this phase
            if (optionId != null && !optionId.isEmpty()) {
                newSubmission.setOutcomeType(optionId);
            } else {
                // Fallback: assign optionId based on player order
                String assignedOptionId = assignOptionTextToPlayer(gameSession, textAddition.getAuthorId()).getId();
                newSubmission.setOutcomeType(assignedOptionId);
            }
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
     * Only returns an outcome type for phases that require filtering by outcome type.
     * - WHAT_WILL_BECOME_OF_US: Returns outcome type (success, neutral, or failure)
     * - HOW_DOES_THIS_RESOLVE: Returns outcome type (optionId)
     * - Other phases: Returns null (no filtering needed)
     * @param gameCode The game code
     * @param playerId The player's ID
     * @return The assigned outcome type, or null if not applicable for the current phase
     */
    public OutcomeType getOutcomeTypeForPlayer(String gameCode, String playerId) {
        GameSession gameSession = getGameSession(gameCode);
        GameState currentState = gameSession.getGameState();
        GameState phaseIdState = currentState.getPhaseId();

        if (phaseIdState == GameState.WHAT_WILL_BECOME_OF_US) {
            return assignOutcomeTypeToPlayer(gameSession, playerId);
        } else if (phaseIdState == GameState.HOW_DOES_THIS_RESOLVE) {
            return assignOptionTextToPlayer(gameSession, playerId);
        }
        
        return null;
    }

    /**
     * Assigns an optionId to a player based on their order in the game (joinedAt)
     * Players are sorted by joinedAt, then assigned optionIds in a round-robin fashion
     * based on the available options in the Story at player coordinates
     * @param gameSession The game session
     * @param playerId The player's ID
     * @return The assigned optionId
     */
    private OutcomeType assignOptionTextToPlayer(GameSession gameSession, String playerId) {
        // Get the encounter at player coordinates
        Encounter encounter = getEncounterAtPlayerCoordinates(gameSession.getGameCode());
        if (encounter == null) {
            throw new ValidationException("Encounter not found at player coordinates");
        }

        String storyId = encounter.getStoryId();
        if (storyId == null || storyId.isEmpty()) {
            throw new ValidationException("Story ID not found in encounter");
        }

        List<Story> stories = storyDAO.getAuthorStoriesByStoryId(gameSession.getGameCode(), storyId);
        if (stories.isEmpty()) {
            throw new ValidationException("Story not found with ID: " + storyId);
        }

        Story story = stories.getFirst();
        List<Option> options = story.getOptions();
        if (options == null || options.isEmpty()) {
            throw new ValidationException("No options found in story");
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

        // Assign optionId based on index modulo number of options
        int optionIndex = playerIndex % options.size();

        return new OutcomeType(
                options.get(optionIndex).getOptionId(),
                options.get(optionIndex).getOptionText()
        );
    }

    /**
     * Assigns an outcome type to a player based on their order in the game (joinedAt)
     * Players are sorted by joinedAt, then assigned outcome types in a round-robin fashion:
     * index % 3 = 0 -> "success"
     * index % 3 = 1 -> "neutral"
     * index % 3 = 2 -> "failure"
     * @param gameSession The game session
     * @param playerId The player's ID
     * @return The assigned outcome type with label message
     */
    private OutcomeType assignOutcomeTypeToPlayer(GameSession gameSession, String playerId) {
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

        // Get entity name from GameSessionDisplay
        String entityName = "the Entity";
        try {
            GameSessionDisplay display = adventureMapHelper.getGameSessionDisplay(gameSession.getGameCode());
            if (display.getEntity() != null && !display.getEntity().isEmpty()) {
                entityName = display.getEntity();
            }
        } catch (Exception e) {
            System.err.println("Failed to get entity name from GameSessionDisplay: " + e.getMessage());
        }

        // Assign outcome type based on index modulo 3
        int outcomeIndex = playerIndex % 3;
        return switch (outcomeIndex) {
            case 0 -> new OutcomeType("success", "What will happen if we impress " + entityName + " and survive?");
            case 1 -> new OutcomeType("neutral", "What will happen if we are fractured and fail in the final confrontation with " + entityName + "?");
            case 2 -> new OutcomeType("failure", "What will happen if we rise and destroy " + entityName + "?");
            default -> new OutcomeType("neutral", "What will happen if we are fractured and fail in the final confrontation with " + entityName + "?"); // Should never reach here, but provide a default
        };
    }

    private List<TextSubmission> calculateWinnersFromVotes(CollaborativeTextPhase phase, GameState gameState) {
        for (TextSubmission submission : phase.getSubmissions()) {
            List<PlayerVote> votesForSubmission = phase.getPlayerVotes().values().stream()
                .flatMap(List::stream)
                .filter(vote -> vote.getSubmissionId().equals(submission.getSubmissionId()))
                .toList();

            int numberOfVotes = votesForSubmission.size();
            submission.setTotalVotes(numberOfVotes);

            // Calculate total points: Rank 1 = 5 points, Rank 2 = 4, Rank 3 = 3, Rank 4 = 2, Rank 5+ = 1
            int totalPoints = votesForSubmission.stream()
                    .mapToInt(vote -> {
                        int rank = vote.getRanking();
                        if (rank <= 1) return 5;
                        if (rank == 2) return 4;
                        if (rank == 3) return 3;
                        if (rank == 4) return 2;
                        return 1; // Rank 5 or higher
                    })
                    .sum();

            submission.setAverageRanking(totalPoints);
        }

        List<TextSubmission> submissionsWithVotes = phase.getSubmissions().stream()
            .filter(submission -> submission.getTotalVotes() > 0)
            .toList();

        // Sort in descending order (higher points = better)
        Comparator<TextSubmission> rankingComparator = Comparator
                .comparingDouble(TextSubmission::getAverageRanking)
                .reversed();

        if (gameState == GameState.SET_ENCOUNTERS_WINNERS) {
            return submissionsWithVotes.stream()
                .sorted(rankingComparator)
                .toList();
        } else if (gameState == GameState.WHAT_CAN_WE_TRY_WINNERS) {
            return submissionsWithVotes.stream()
                .sorted(rankingComparator)
                .limit(2)
                .toList();
        } else if (gameState == GameState.WHAT_ARE_WE_CAPABLE_OF_VOTE_WINNERS) {
            return submissionsWithVotes.stream()
                .sorted(rankingComparator)
                .limit(6)
                .toList();
        } else if (gameState == GameState.WHAT_WILL_BECOME_OF_US_VOTE_WINNER) {
            // For WHAT_WILL_BECOME_OF_US, return the best submission for each outcome type (success, neutral, failure)
            List<TextSubmission> winners = new ArrayList<>();
            for (String outcomeType : List.of("success", "neutral", "failure")) {
                TextSubmission winner = submissionsWithVotes.stream()
                    .filter(submission -> outcomeType.equals(submission.getOutcomeType()))
                    .min(rankingComparator)
                    .orElse(null);
                if (winner != null) {
                    winners.add(winner);
                }
            }
            return winners;
        } else if (gameState == GameState.HOW_DOES_THIS_RESOLVE_WINNERS) {
            // For HOW_DOES_THIS_RESOLVE, return the best submission for each optionId (outcomeType)
            // First, get all unique optionIds from submissions
            List<String> optionIds = submissionsWithVotes.stream()
                .map(TextSubmission::getOutcomeType)
                .filter(optionId -> optionId != null && !optionId.isEmpty())
                .distinct()
                .toList();

            List<TextSubmission> winners = new ArrayList<>();
            for (String optionId : optionIds) {
                TextSubmission winner = submissionsWithVotes.stream()
                    .filter(submission -> optionId.equals(submission.getOutcomeType()))
                    .min(rankingComparator)
                    .orElse(null);
                if (winner != null) {
                    winners.add(winner);
                }
            }
            return winners;
        } else {
            // For other phases return the single best submission
            TextSubmission winner = submissionsWithVotes.stream()
                .min(rankingComparator)
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
    private void updateGameSessionWithWinningSubmissions(String gameCode, String phaseId, List<TextSubmission> winningSubmissions) {
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
                case "SET_ENCOUNTERS" -> {
                    initializeDungeonGridWithEncounters(gameCode, phaseId, winningSubmissions, display);
                }
                case "WHAT_HAPPENS_HERE" -> {
                    handleWhatHappensHere(gameCode, winningSubmissions);
                }
                case "WHAT_CAN_WE_TRY" -> {
                    handleWhatCanWeTry(gameCode, winningSubmissions);
                }
                case "HOW_DOES_THIS_RESOLVE" -> {
                    handleHowDoesThisResolve(gameCode, winningSubmissions);
                }
                case "MAKE_CHOICE_VOTING" -> {
                    handleMakeChoice(gameCode, winningSubmissions);
                }
                case "NAVIGATE_VOTING" -> {
                    handleNavigation(gameCode, winningSubmissions);
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
     * Initializes dungeon grid with winning submission at (0,0) and adjacent encounters
     */
    private void initializeDungeonGridWithEncounters(String gameCode, String phaseId, List<TextSubmission> winningSubmissions, GameSessionDisplay display) {
        try {
            if (winningSubmissions.isEmpty()) return;

            GameSession gameSession = getGameSession(gameCode);
            CollaborativeTextPhase phase = collaborativeTextDAO.getCollaborativeTextPhase(gameCode, phaseId);
            if (phase == null) return;

            AdventureMap adventureMap = gameSession.getAdventureMap();
            if (adventureMap == null) {
                adventureMap = new AdventureMap();
                gameSession.setAdventureMap(adventureMap);
            }

            if (adventureMap.getEncounterLabels() == null) {
                adventureMap.setEncounterLabels(new ArrayList<>());
            }

            // Create EncounterLabels for all submissions with votes
            List<EncounterLabel> encounterLabels = new ArrayList<>();
            for (TextSubmission submission : winningSubmissions) {
                EncounterLabel label = new EncounterLabel(
                    submission.getCurrentText(),
                    submission
                );
                encounterLabels.add(label);
            }
            adventureMap.getEncounterLabels().addAll(encounterLabels);

            EncounterLabel winningLabel = encounterLabels.get(0);
            GameBoard gameBoard = new GameBoard();
            
            // Place winning submission at (0, 0)
            gameBoard.setEncounter(0, 0, new Encounter(winningLabel, EncounterType.NORMAL, "", ""));

            int entityXPosition = Math.random() < 0.5 ? -1 : 1;
            int entityYPosition =  Math.random() < 0.5 ? -1 : 1;

            gameBoard.setEncounter(entityXPosition, entityYPosition, new Encounter(new EncounterLabel(display.getEntity(), new TextSubmission()), EncounterType.MAIN, "", ""));

            List<int[]> allPositions = new ArrayList<>();
            for (int y = -4; y <= 4; y++) {
                for (int x = -4; x <= 4; x++) {
                    // Exclude starting position (0, 0) and entity position
                    // Use OR because we want to exclude when BOTH coordinates match the excluded position
                    if ((x != 0 || y != 0) && (x != entityXPosition || y != entityYPosition)) {
                        allPositions.add(new int[]{x, y});
                    }
                }
            }
            Collections.shuffle(allPositions, new Random());

            // Create weighted list of EncounterLabels based on ranking (higher ranked = more occurrences)
            // Skip the first one (winning submission) as it's already placed at (0,0)
            List<EncounterLabel> weightedLabels = new ArrayList<>();
            for (int i = 1; i < encounterLabels.size(); i++) {
                EncounterLabel label = encounterLabels.get(i);
                // Higher ranked (lower index) get more placements
                // Rank 1 (index 1) gets 5 copies, rank 2 gets 4, rank 3 gets 3, etc.
                int copies = Math.max(1, 6 - i);
                for (int j = 0; j < copies; j++) {
                    weightedLabels.add(label);
                }
            }

            // If we don't have enough labels, repeat the higher ranked ones
            if (weightedLabels.isEmpty()) {
                if (encounterLabels.size() > 1) {
                    // If only one other submission, repeat it
                    EncounterLabel otherLabel = encounterLabels.get(1);
                    for (int i = 0; i < allPositions.size(); i++) {
                        weightedLabels.add(otherLabel);
                    }
                } else {
                    // Only winning submission exists, use it for all positions
                    for (int i = 0; i < allPositions.size(); i++) {
                        weightedLabels.add(winningLabel);
                    }
                }
            }

            // Place encounters at all positions
            for (int i = 0; i < allPositions.size(); i++) {
                int[] pos = allPositions.get(i);
                int x = pos[0];
                int y = pos[1];
                EncounterLabel label = weightedLabels.isEmpty()
                    ? winningLabel
                    : weightedLabels.get(i % weightedLabels.size());
                
                gameBoard.setEncounter(x, y, new Encounter(label, EncounterType.NORMAL, "", ""));
            }
            gameBoard.setPlayerCoordinates(new PlayerCoordinates(0, 0));
            // Update GameSession in Firestore via DAO
            gameSessionDAO.initializeDungeonGrid(
                gameCode,
                gameBoard,
                adventureMap.getEncounterLabels()
            );

            // Initialize NAVIGATE_VOTING phase with directional submissions
            initializeNavigateVotingPhase(gameCode);
        } catch (Exception e) {
            System.err.println("Failed to initialize dungeon grid: " + e.getMessage());
        }
    }

    /**
     * Initializes the NAVIGATE_VOTING phase with four directional submissions (NORTH, SOUTH, EAST, WEST)
     */
    private void initializeNavigateVotingPhase(String gameCode) {
        try {
            String phaseId = GameState.NAVIGATE_VOTING.name();
            String[] directions = {"NORTH", "SOUTH", "EAST", "WEST"};

            for (String direction : directions) {
                TextSubmission submission = new TextSubmission();
                submission.setSubmissionId(direction);
                submission.setAuthorId(AuthorConstants.DUNGEON_PLAYER);
                submission.setOriginalText("");
                submission.setCurrentText(direction);
                submission.setCreatedAt(Timestamp.now());
                submission.setLastModified(Timestamp.now());
                submission.setOutcomeType(direction);

                // Add the submission to the phase atomically
                collaborativeTextDAO.addSubmissionAtomically(gameCode, phaseId, submission);
            }
        } catch (Exception e) {
            System.err.println("Failed to initialize NAVIGATE_VOTING phase: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Gets the Encounter at the player's current coordinates
     * @return The Encounter at player coordinates, or null if not found
     */
    private Encounter getEncounterAtPlayerCoordinates(String gameCode) {
        try {
            GameSession gameSession = getGameSession(gameCode);
            PlayerCoordinates playerCoords = gameSession.getGameBoard().getPlayerCoordinates();
            if (playerCoords == null) {
                System.err.println("Player coordinates not found for game: " + gameCode);
                return null;
            }

            GameBoard gameBoard = gameSession.getGameBoard();
            if (gameBoard == null) {
                System.err.println("Game board not found for game: " + gameCode);
                return null;
            }

            Encounter encounter = gameBoard.getEncounter(
                playerCoords.getxCoordinate(), 
                playerCoords.getyCoordinate()
            );
            if (encounter == null) {
                System.err.println("Encounter not found at coordinates (" + playerCoords.getxCoordinate() + ", " + playerCoords.getyCoordinate() + ")");
                return null;
            }

            return encounter;
        } catch (Exception e) {
            System.err.println("Failed to get encounter at player coordinates: " + e.getMessage());
            return null;
        }
    }

    /**
     * Handles WHAT_HAPPENS_HERE phase: Creates a Story from the winning submission
     * and updates the Encounter at player coordinates
     */
    private void handleWhatHappensHere(String gameCode, List<TextSubmission> winningSubmissions) {
        try {
            if (winningSubmissions.isEmpty()) {
                return;
            }

            // Get the single winning submission
            TextSubmission winningSubmission = winningSubmissions.getFirst();

            // Get the encounter at player coordinates
            Encounter encounter = getEncounterAtPlayerCoordinates(gameCode);
            if (encounter == null) {
                return;
            }

            // Get GameSession to update dungeon grid
            GameSession gameSession = getGameSession(gameCode);
            GameBoard gameBoard = gameSession.getGameBoard();
            if (gameBoard == null) {
                gameBoard = new GameBoard();
                gameSession.setGameBoard(gameBoard);
            }

            // Create a new Story
            Story story = new Story();
            story.setPrompt(winningSubmission.getCurrentText());
            story.setPlayerId(AuthorConstants.DUNGEON_PLAYER);
            story.setGameCode(gameCode);
            story.setEncounterLabel(encounter.getEncounterLabel());

            // Store the Story in GameSession.stories
            storyDAO.createStory(story);

            // Update the Encounter at player coordinates
            encounter.setStoryId(story.getStoryId());
            encounter.setStoryPrompt(story.getPrompt());
            encounter.setVisited(true);

            // Update the encounter in the game board
            PlayerCoordinates playerCoords = gameSession.getGameBoard().getPlayerCoordinates();
            gameBoard.setEncounter(
                playerCoords.getxCoordinate(), 
                playerCoords.getyCoordinate(), 
                encounter
            );

            // Update the game board in Firestore
            gameSessionDAO.updateDungeonGrid(gameCode, gameBoard);
        } catch (Exception e) {
            System.err.println("Failed to handle WHAT_HAPPENS_HERE: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Handles WHAT_CAN_WE_TRY phase: Creates Options from winning submissions
     * and adds them to the Story at player coordinates
     */
    private void handleWhatCanWeTry(String gameCode, List<TextSubmission> winningSubmissions) {
        try {
            if (winningSubmissions.isEmpty()) {
                return;
            }

            Encounter encounter = getEncounterAtPlayerCoordinates(gameCode);
            if (encounter == null) {
                return;
            }

            String storyId = encounter.getStoryId();

            List<Story> stories = storyDAO.getAuthorStoriesByStoryId(gameCode, storyId);
            Story story = stories.getFirst();

            // Get current options or initialize empty list
            List<Option> currentOptions = story.getOptions();
            if (currentOptions == null) {
                currentOptions = new ArrayList<>();
            }

            for (TextSubmission submission : winningSubmissions) {
                if (currentOptions.stream().anyMatch(option -> option.getOptionText().equals(submission.getCurrentText()))) {
                    continue;
                }

                Option option = new Option();
                option.setOptionText(submission.getCurrentText());
                currentOptions.add(option);
            }

            // Update the story with new options
            story.setOptions(currentOptions);
            story.setGameCode(gameCode); // Ensure gameCode is set for updateStory
            
            // Use the DAO's updateStory method
            storyDAO.updateStory(story);
        } catch (Exception e) {
            System.err.println("Failed to handle WHAT_CAN_WE_TRY: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Handles HOW_DOES_THIS_RESOLVE phase: Updates Option.successText for each winning submission
     */
    private void handleHowDoesThisResolve(String gameCode, List<TextSubmission> winningSubmissions) {
        try {
            if (winningSubmissions.isEmpty()) {
                return;
            }

            Encounter encounter = getEncounterAtPlayerCoordinates(gameCode);
            if (encounter == null) {
                return;
            }

            String storyId = encounter.getStoryId();

            List<Story> stories = storyDAO.getAuthorStoriesByStoryId(gameCode, storyId);

            Story story = stories.getFirst();
            List<Option> options = story.getOptions();

            Map<String, TextSubmission> optionWinners = new HashMap<>();
            for (TextSubmission winner : winningSubmissions) {
                String optionId = winner.getOutcomeType();
                if (optionId != null && !optionId.isEmpty()) {
                    optionWinners.put(optionId, winner);
                }
            }

            for (Option option : options) {
                TextSubmission winner = optionWinners.get(option.getOptionId());
                if (winner != null) {
                    option.setSuccessText(winner.getCurrentText());
                }
            }

            story.setOptions(options);
            story.setGameCode(gameCode); // Ensure gameCode is set for updateStory
            
            // Use the DAO's updateStory method
            storyDAO.updateStory(story);

            // Initialize MAKE_CHOICE_VOTING phase with submissions for each option
            initializeMakeChoiceVotingPhase(gameCode, winningSubmissions);
        } catch (Exception e) {
            System.err.println("Failed to handle HOW_DOES_THIS_RESOLVE: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Handles MAKE_CHOICE_VOTING phase: Sets visited to true and selectedOptionId to the winning option
     */
    private void handleMakeChoice(String gameCode, List<TextSubmission> winningSubmissions) {
        try {
            if (winningSubmissions.isEmpty()) {
                return;
            }

            // Get the story at the current encounter
            Story story = getStoryAtCurrentEncounter(gameCode);
            if (story == null) {
                return;
            }

            // The winning submission's submissionId is the optionId that was voted for
            TextSubmission winningSubmission = winningSubmissions.getFirst();
            String selectedOptionId = winningSubmission.getSubmissionId();

            // Update the story
            story.setVisited(true);
            story.setSelectedOptionId(selectedOptionId);
            story.setGameCode(gameCode); // Ensure gameCode is set for updateStory

            // Use the DAO's updateStory method
            storyDAO.updateStory(story);
        } catch (Exception e) {
            System.err.println("Failed to handle MAKE_CHOICE_VOTING: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Handles NAVIGATE_VOTING phase: Updates player coordinates based on the winning direction
     */
    private void handleNavigation(String gameCode, List<TextSubmission> winningSubmissions) {
        try {
            if (winningSubmissions.isEmpty()) {
                return;
            }

            // Get the winning direction submission
            TextSubmission winningSubmission = winningSubmissions.getFirst();
            String direction = winningSubmission.getSubmissionId();

            // Get current game session and player coordinates
            GameSession gameSession = getGameSession(gameCode);
            GameBoard gameBoard = gameSession.getGameBoard();
            if (gameBoard == null) {
                System.err.println("Game board not found for game: " + gameCode);
                return;
            }

            PlayerCoordinates currentCoords = gameBoard.getPlayerCoordinates();
            if (currentCoords == null) {
                System.err.println("Player coordinates not found for game: " + gameCode);
                return;
            }

            // Calculate new coordinates based on direction
            int newX = currentCoords.getxCoordinate();
            int newY = currentCoords.getyCoordinate();

            switch (direction) {
                case "NORTH" -> newY += 1;  // Move north: y increases
                case "SOUTH" -> newY -= 1;  // Move south: y decreases
                case "EAST" -> newX += 1;   // Move east: x increases
                case "WEST" -> newX -= 1;    // Move west: x decreases
                default -> {
                    System.err.println("Unknown direction: " + direction);
                    return;
                }
            }

            // Create new player coordinates
            PlayerCoordinates newCoordinates = new PlayerCoordinates(newX, newY);

            // Update player coordinates via DAO
            gameSessionDAO.updatePlayerCoordinates(gameCode, newCoordinates);

            // Clear CollaborativeTextPhase objects for all story writing phases
            clearStoryWritingPhases(gameCode);
        } catch (Exception e) {
            System.err.println("Failed to handle NAVIGATE_VOTING: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Clears submissions and player votes from all story writing phases
     * Called after navigation to reset phases for the next encounter
     */
    private void clearStoryWritingPhases(String gameCode) {
        try {
            // Clear all story writing phases
            collaborativeTextDAO.clearPhaseSubmissionsAndVotes(gameCode, GameState.WHAT_HAPPENS_HERE.name());
            collaborativeTextDAO.clearPhaseSubmissionsAndVotes(gameCode, GameState.WHAT_CAN_WE_TRY.name());
            collaborativeTextDAO.clearPhaseSubmissionsAndVotes(gameCode, GameState.HOW_DOES_THIS_RESOLVE.name());
            collaborativeTextDAO.clearPhaseSubmissionsAndVotes(gameCode, GameState.MAKE_CHOICE_VOTING.name());
            collaborativeTextDAO.clearPhaseSubmissionsAndVotes(gameCode, GameState.NAVIGATE_VOTING.name());
        } catch (Exception e) {
            System.err.println("Failed to clear story writing phases: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Initializes the MAKE_CHOICE_VOTING phase with submissions based on winning submissions from HOW_DOES_THIS_RESOLVE
     * Each submission uses outcomeType (optionId) as submissionId and has new timestamps
     */
    private void initializeMakeChoiceVotingPhase(String gameCode, List<TextSubmission> winningSubmissions) {
        try {
            if (winningSubmissions == null || winningSubmissions.isEmpty()) {
                System.err.println("No winning submissions found for MAKE_CHOICE_VOTING initialization");
                return;
            }

            String phaseId = GameState.MAKE_CHOICE_VOTING.name();

            for (TextSubmission winningSubmission : winningSubmissions) {
                if (winningSubmission.getOutcomeType() == null || winningSubmission.getOutcomeType().isEmpty()) {
                    continue;
                }

                TextSubmission submission = new TextSubmission();
                submission.setSubmissionId(winningSubmission.getOutcomeType());
                submission.setAuthorId(winningSubmission.getAuthorId());
                submission.setOriginalText(winningSubmission.getOriginalText());
                submission.setCurrentText(winningSubmission.getCurrentText());
                submission.setCreatedAt(Timestamp.now());
                submission.setLastModified(Timestamp.now());
                submission.setOutcomeType(winningSubmission.getOutcomeType());
                
                if (winningSubmission.getAdditions() != null) {
                    submission.setAdditions(new ArrayList<>(winningSubmission.getAdditions()));
                }

                collaborativeTextDAO.addSubmissionAtomically(gameCode, phaseId, submission);
            }
        } catch (Exception e) {
            System.err.println("Failed to initialize MAKE_CHOICE_VOTING phase: " + e.getMessage());
            e.printStackTrace();
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
        PhaseType phaseType = determinePhaseType(gameState, baseInfo);

        String phaseInstructions = getPhaseInstructionsForMode(gameState, phaseType, baseInfo);
        
        String collaborativeModeInstructions = getCollaborativeModeInstructions(
            baseInfo.collaborativeMode(), 
            phaseType, 
            gameState
        );

        Story storyToIterateOn = getStoryAtCurrentEncounter(gameCode);

        return new CollaborativeTextPhaseInfo(
            gameState.getPhaseId(),
            baseInfo.phaseQuestion(),
            phaseInstructions,
            baseInfo.collaborativeMode(),
            collaborativeModeInstructions,
            storyToIterateOn,
            phaseType,
            baseInfo.showGameBoard()
        );
    }

    private Story getStoryAtCurrentEncounter(String gameCode) {
        Encounter encounter = getEncounterAtPlayerCoordinates(gameCode);

        if (encounter == null) {
            return null;
        }

        List<Story> stories = storyDAO.getAuthorStoriesByStoryId(gameCode, encounter.getStoryId());
        if (stories == null || stories.isEmpty()) {
            return null;
        }
        return stories.getFirst();
    }

    /**
     * Determines the phase type by checking which game state in PhaseBaseInfo matches the current game state
     */
    private PhaseType determinePhaseType(GameState gameState, PhaseBaseInfo baseInfo) {
        if (gameState == baseInfo.collaboratingState()) {
            return PhaseType.SUBMISSION;
        } else if (gameState == baseInfo.votingState()) {
            return PhaseType.VOTING;
        } else if (gameState == baseInfo.winningState()) {
            return PhaseType.WINNING;
        }
        // Default fallback
        return PhaseType.SUBMISSION;
    }

    /**
     * Gets phase instructions based on the phase type
     */
    private String getPhaseInstructionsForMode(GameState gameState, PhaseType phaseType, PhaseBaseInfo baseInfo) {
        return switch (phaseType) {
            case SUBMISSION -> baseInfo.baseInstructions();
            case VOTING -> "The time has come to solidify our fate. Rank the descriptions on your device starting with your favorite first.";
            case WINNING -> {
                if (gameState == GameState.WHAT_WILL_BECOME_OF_US_VOTE_WINNER) {
                    yield "The threads before us have now been sealed. Only our choices ahead can reveal them to us. Now we must build this place.";
                } else if (gameState == GameState.WHAT_ARE_WE_CAPABLE_OF_VOTE_WINNERS || gameState == GameState.WHAT_CAN_WE_TRY) {
                    yield "The winning submissions are...";
                } else if (gameState == GameState.HOW_DOES_THIS_RESOLVE_WINNERS){
                    yield "Now our heroes must make their choice. Choose from the options on your device and our resolution will be revealed.";
                } else {
                    yield "The winning submission is...";
                }
            }
        };
    }

    /**
     * Gets collaborative mode instructions, reusing logic based on collaborative mode and phase type
     */
    private String getCollaborativeModeInstructions(CollaborativeMode collaborativeMode, PhaseType phaseType, GameState gameState) {
        // For SUBMISSION phase, return mode-specific instructions
        if (phaseType == PhaseType.SUBMISSION) {
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