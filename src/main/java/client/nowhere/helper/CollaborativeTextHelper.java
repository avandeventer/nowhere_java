package client.nowhere.helper;

import java.util.*;
import java.util.stream.Collectors;

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
    private final FeatureFlagHelper featureFlagHelper;

    @Autowired
    public CollaborativeTextHelper(GameSessionDAO gameSessionDAO, CollaborativeTextDAO collaborativeTextDAO, AdventureMapDAO adventureMapDAO, AdventureMapHelper adventureMapHelper, StoryDAO storyDAO, FeatureFlagHelper featureFlagHelper) {
        this.gameSessionDAO = gameSessionDAO;
        this.collaborativeTextDAO = collaborativeTextDAO;
        this.adventureMapDAO = adventureMapDAO;
        this.adventureMapHelper = adventureMapHelper;
        this.storyDAO = storyDAO;
        this.featureFlagHelper = featureFlagHelper;
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
            
//            if (phaseId.equals(GameState.HOW_DOES_THIS_RESOLVE.name())) {
//                createOptionFromOutcomeType(gameCode, textAddition);
//            }
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

        boolean streamlinedMode = featureFlagHelper.getFlagValue("streamlinedCollaborativeStories");
        List<TextSubmission> winningSubmissions = streamlinedMode && !phaseId.equals(GameState.MAKE_CHOICE_VOTING.name())
                ? calculateWinnersFromAdditions(phase, gameSession.getGameState(), gameSession.getRoundNumber())
                : calculateWinnersFromVotes(phase, gameSession.getGameState());

        // Store the winning submissions in GameSessionDisplay
        if (!winningSubmissions.isEmpty()) {
            updateGameSessionWithWinningSubmissions(gameCode, phaseId, winningSubmissions, gameSession.getRoundNumber());
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

        boolean streamlinedMode = featureFlagHelper.getFlagValue("streamlinedCollaborativeStories");

        if ((outcomeTypeId == null || outcomeTypeId.trim().isEmpty()) && !streamlinedMode) {
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
        
        String outcomeType = textAddition.getOutcomeType();
        if (outcomeType != null && !outcomeType.isEmpty()) {
            newSubmission.setOutcomeType(outcomeType);
        }

        if (textAddition.getOutcomeTypeWithLabel() != null
            && !textAddition.getOutcomeTypeWithLabel().getLabel().trim().isEmpty()
            && !textAddition.getOutcomeTypeWithLabel().getId().isEmpty()
        ) {
            newSubmission.setOutcomeTypeWithLabel(textAddition.getOutcomeTypeWithLabel());
            
            // If outcomeTypeWithLabel has subTypes, use the first subType's id as outcomeType
            // Otherwise, use the parent id
            if (textAddition.getOutcomeTypeWithLabel().getSubTypes() != null
                    && !textAddition.getOutcomeTypeWithLabel().getSubTypes().isEmpty()) {
                OutcomeType firstSubType = textAddition.getOutcomeTypeWithLabel().getSubTypes().get(0);
                if (firstSubType != null && firstSubType.getId() != null && !firstSubType.getId().isEmpty()) {
                    newSubmission.setOutcomeType(firstSubType.getId());
                } else {
                    newSubmission.setOutcomeType(textAddition.getOutcomeTypeWithLabel().getId());
                }
            } else {
                newSubmission.setOutcomeType(textAddition.getOutcomeTypeWithLabel().getId());
            }
        }

        return newSubmission;
    }

    /**
     * Creates a new option in the story at the current encounter if the outcomeTypeWithLabel
     * doesn't already exist as an option. This is used for HOW_DOES_THIS_RESOLVE phase
     * when players submit with an outcomeTypeWithLabel.
     */
    private void createOptionFromOutcomeType(String gameCode, TextAddition textAddition) {
        if (textAddition.getOutcomeTypeWithLabel() == null
                || textAddition.getOutcomeTypeWithLabel().getLabel() == null
                || textAddition.getOutcomeTypeWithLabel().getLabel().isEmpty()) {
            return;
        }

        Story storyAtEncounter = getStoryAtCurrentEncounter(gameCode);
        if (storyAtEncounter == null) {
            return;
        }

        String labelText = textAddition.getOutcomeTypeWithLabel().getLabel();

        boolean optionExists = storyAtEncounter.getOptions() != null && storyAtEncounter.getOptions().stream()
                .anyMatch(option -> labelText.equals(option.getOptionText()));

        if (!optionExists) {
            Option newOption = new Option();
            newOption.setOptionText(labelText);
            newOption.setOptionId(textAddition.getOutcomeTypeWithLabel().getId());

            if (storyAtEncounter.getOptions() == null) {
                storyAtEncounter.setOptions(new ArrayList<>());
            }
            storyAtEncounter.getOptions().add(newOption);
            storyAtEncounter.setGameCode(gameCode);
            storyDAO.updateStory(storyAtEncounter);
        }
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

        if (parentSubmission.getOutcomeTypeWithLabel() != null
            && !parentSubmission.getOutcomeTypeWithLabel().getLabel().trim().isEmpty()
        ) {
            newSubmission.setOutcomeTypeWithLabel(parentSubmission.getOutcomeTypeWithLabel());
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
        // Get the story at current encounter
        Story story = getStoryAtCurrentEncounter(gameSession.getGameCode());
        if (story == null) {
            throw new ValidationException("Story not found at player coordinates");
        }
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

    /**
     * Calculates winners based on number of additions (streamlined mode).
     * For phases with outcomeTypes, ranks by most submissions per outcomeType.
     */
    private List<TextSubmission> calculateWinnersFromAdditions(CollaborativeTextPhase phase, GameState gameState, int roundNumber) {
        for (TextSubmission submission : phase.getSubmissions()) {
            int additionCount = submission.getAdditions() != null ? submission.getAdditions().size() : 0;
            submission.setAverageRanking(additionCount);
        }

        // Comparator is ascending (lower values first, higher values last)
        // For additions: higher = better, so we use .max() to find highest, then sort descending
        Comparator<TextSubmission> rankingComparator = Comparator
                .comparingDouble(TextSubmission::getAverageRanking);

        if (gameState == GameState.HOW_DOES_THIS_RESOLVE_WINNERS
                || gameState == GameState.HOW_DOES_THIS_RESOLVE_WINNERS_AGAIN) {
            // Rank by most submissions PER outcomeType
            List<String> uniqueOutcomeTypes = phase.getSubmissions().stream()
                    .map(TextSubmission::getOutcomeType)
                    .filter(outcomeType -> outcomeType != null && !outcomeType.isEmpty())
                    .distinct()
                    .toList();

            // For each outcomeType, find the submission with most additions
            // .max() with ascending comparator returns the highest value
            List<TextSubmission> winners = new ArrayList<>();
            for (String outcomeType : uniqueOutcomeTypes) {
                TextSubmission winner = phase.getSubmissions().stream()
                        .filter(submission -> outcomeType.equals(submission.getOutcomeType()))
                        .max(rankingComparator)
                        .orElse(null);
                if (winner != null) {
                    winners.add(winner);
                }
            }

            // Sort descending so highest additions come first
            return winners.stream().sorted(rankingComparator.reversed()).toList();
        } else {
            // For other phases (like CAMPFIRE), return all submissions sorted by most additions first
            // Filter out submissions that have been iterated on (referenced in other submissions' additions)
            phase.filterOutParentSubmissions();
            
            // Sort descending so highest additions come first
            return phase.getSubmissions().stream()
                    .sorted(rankingComparator.reversed())
                    .toList();
        }
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
        } else if (gameState == GameState.WHAT_WILL_BECOME_OF_US_VOTE_WINNER || gameState == GameState.HOW_DOES_THIS_RESOLVE_WINNERS) {
            List<String> uniqueOutcomeTypes = submissionsWithVotes.stream()
                    .map(TextSubmission::getOutcomeType)
                    .filter(outcomeType -> outcomeType != null && !outcomeType.isEmpty())
                    .distinct()
                    .toList();

            List<TextSubmission> winners = new ArrayList<>();
            for (String outcomeType : uniqueOutcomeTypes) {
                TextSubmission winner = submissionsWithVotes.stream()
                        .filter(submission -> outcomeType.equals(submission.getOutcomeType()))
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
     * Checks if a submission is available for voting by a player.
     * A submission is available if:
     * - It has no outcomeType (null or empty) and is not the player's own submission, OR
     * - It matches the player's outcomeType
     * @param submission The submission to check
     * @param playerId The player requesting submissions
     * @param outcomeType The player's outcome type
     * @return true if the submission is available for voting
     */
    private boolean isSubmissionAvailableForVoting(TextSubmission submission, String playerId, OutcomeType outcomeType) {
        if (submission.getOutcomeType() == null || submission.getOutcomeType().isEmpty()) {
            return !submission.getAuthorId().equals(playerId);
        }
        return outcomeType.getId().equals(submission.getOutcomeType());
    }

    /**
     * Sets the outcomeTypeWithLabel on a submission if the outcomeType matches and is not null or empty.
     * @param submission The submission to update
     * @param outcomeType The outcome type to set if it matches
     */
    private void setOutcomeTypeWithLabelIfMatch(TextSubmission submission, OutcomeType outcomeType) {
        if (outcomeType != null 
                && outcomeType.getId() != null 
                && !outcomeType.getId().isEmpty()
                && outcomeType.getId().equals(submission.getOutcomeType())) {
            submission.setOutcomeTypeWithLabel(outcomeType);
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
        OutcomeType outcomeType = getOutcomeTypeForPlayer(gameCode, playerId);
        if (phase == null) {
            throw new ValidationException("Collaborative text phase not found for game state: " + gameSession.getGameState());
        }

        // Return top 5 submissions except player's own, ordered by most additions first, then by creation time
        // For WHAT_DO_WE_FEAR, return all submissions; for WHAT_ARE_WE_CAPABLE_OF, return top 6; for others, return top 5
        // For WHAT_WILL_BECOME_OF_US, include player's own submissions (they'll be filtered by outcomeType on frontend)
        boolean isWhatDoWeFear = phaseIdState == GameState.WHAT_DO_WE_FEAR;
        boolean isWhatAreWeCapableOf = phaseIdState == GameState.WHAT_ARE_WE_CAPABLE_OF;
        int limit = isWhatDoWeFear ? Integer.MAX_VALUE : (isWhatAreWeCapableOf ? 6 : 5);

        return phase.getSubmissions().stream()
                .filter(textSubmission -> isSubmissionAvailableForVoting(textSubmission, playerId, outcomeType))
                .peek(textSubmission -> setOutcomeTypeWithLabelIfMatch(textSubmission, outcomeType))
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
    private void updateGameSessionWithWinningSubmissions(String gameCode, String phaseId, List<TextSubmission> winningSubmissions, int roundNumber) {
        try {
            boolean streamlinedMode = featureFlagHelper.getFlagValue("streamlinedCollaborativeStories");
            
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
                    if (streamlinedMode) {
                        addAllSubmissionsToAdventureMap(gameCode, winningSubmissions);
                    } else {
                        initializeDungeonGridWithEncounters(gameCode, phaseId, winningSubmissions, display);
                    }
                }
                case "WHAT_HAPPENS_HERE" -> {
                    if (streamlinedMode) {
                        handleWhatHappensHereStreamlined(gameCode, winningSubmissions);
                    } else {
                        handleWhatHappensHere(gameCode, winningSubmissions);
                    }
                }
                case "WHAT_CAN_WE_TRY" -> {
                    if (!streamlinedMode) {
                        handleWhatCanWeTry(gameCode, winningSubmissions);
                    }
//                    else {
//                        handleWhatCanWeTryStreamlined(gameCode, winningSubmissions);
//                    }
                }
                case "HOW_DOES_THIS_RESOLVE", "HOW_DOES_THIS_RESOLVE_AGAIN" -> {
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
     * Adds all submissions to AdventureMap as EncounterLabels (streamlined mode for SET_ENCOUNTERS)
     */
    private void addAllSubmissionsToAdventureMap(String gameCode, List<TextSubmission> winningSubmissions) {
        try {
            if (winningSubmissions.isEmpty()) {
                return;
            }

            GameSession gameSession = getGameSession(gameCode);
            AdventureMap adventureMap = gameSession.getAdventureMap();
            if (adventureMap == null) {
                adventureMap = new AdventureMap();
                gameSession.setAdventureMap(adventureMap);
            }

            if (adventureMap.getEncounterLabels() == null) {
                adventureMap.setEncounterLabels(new ArrayList<>());
            }

            // Create EncounterLabels for all submissions
            List<EncounterLabel> encounterLabels = new ArrayList<>();
            for (TextSubmission submission : winningSubmissions) {
                EncounterLabel label = new EncounterLabel(
                    submission.getCurrentText(),
                    submission
                );
                encounterLabels.add(label);
            }
            adventureMap.getEncounterLabels().addAll(encounterLabels);

            // Update AdventureMap in Firestore
            gameSession.setAdventureMap(adventureMap);
            gameSession.setGameBoard(new GameBoard());
            adventureMapDAO.updateGameSessionAdventureMap(gameSession.getGameCode(), adventureMap);
        } catch (Exception e) {
            System.err.println("Failed to add submissions to AdventureMap: " + e.getMessage());
            e.printStackTrace();
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
     * Handles WHAT_HAPPENS_HERE phase in streamlined mode: Creates stories for each winning submission
     * using encounterLabels from AdventureMap and places them on the game board
     */
    private void handleWhatHappensHereStreamlined(String gameCode, List<TextSubmission> winningSubmissions) {
        try {
            if (winningSubmissions.isEmpty()) {
                return;
            }

            GameSession gameSession = getGameSession(gameCode);
            GameBoard gameBoard = gameSession.getGameBoard();
            if (gameBoard == null) {
                gameBoard = new GameBoard();
                gameSession.setGameBoard(gameBoard);
            }

            PlayerCoordinates playerCoords = gameBoard.getPlayerCoordinates();
            if (playerCoords == null) {
                playerCoords = new PlayerCoordinates(0, 0);
                gameBoard.setPlayerCoordinates(playerCoords);
            }

            AdventureMap adventureMap = gameSession.getAdventureMap();
            if (adventureMap == null || adventureMap.getEncounterLabels() == null) {
                System.err.println("AdventureMap or encounterLabels not found");
                return;
            }

            int currentX = playerCoords.getxCoordinate();
            int y = playerCoords.getyCoordinate();

            for (int i = 0; i < winningSubmissions.size(); i++) {
                TextSubmission submission = winningSubmissions.get(i);
                String encounterLabelId = submission.getOutcomeType();

                if (encounterLabelId == null || encounterLabelId.isEmpty()) {
                    continue;
                }

                // Find the corresponding EncounterLabel from AdventureMap
                EncounterLabel encounterLabel = adventureMap.getEncounterLabels().stream()
                        .filter(label -> encounterLabelId.equals(label.getEncounterId()))
                        .findFirst()
                        .orElse(null);

                if (encounterLabel == null) {
                    System.err.println("EncounterLabel not found for id: " + encounterLabelId);
                    continue;
                }

                // Create a new story with the encounterLabel and submission text
                Story story = new Story();
                story.setPrompt(submission.getCurrentText());
                story.setPlayerId(AuthorConstants.DUNGEON_PLAYER);
                story.setGameCode(gameCode);
                story.setEncounterLabel(encounterLabel);
                story.setCreatedAt(Timestamp.now());
                
                // Set authorId to the original author from the first addition (for branched submissions)
                // or use the submission's authorId (for new submissions)
                String originalAuthorId = submission.getAuthorId();
                if (submission.getAdditions() != null && !submission.getAdditions().isEmpty()) {
                    TextAddition firstAddition = submission.getAdditions().get(0);
                    if (firstAddition.getAuthorId() != null && !firstAddition.getAuthorId().isEmpty()) {
                        originalAuthorId = firstAddition.getAuthorId();
                    }
                }
                story.setAuthorId(originalAuthorId);
                storyDAO.createStory(story);

                // Create encounter with the story
                Encounter encounter = new Encounter(
                    encounterLabel,
                    EncounterType.NORMAL,
                    story.getStoryId(),
                    story.getPrompt()
                );

                // Place first submission at player coordinates, subsequent ones at x+1, x+2, etc.
                int x = currentX + i;
                gameBoard.setEncounter(x, y, encounter);
            }

            // Update the game board in Firestore
            gameSessionDAO.updateDungeonGrid(gameCode, gameBoard);
        } catch (Exception e) {
            System.err.println("Failed to handle WHAT_HAPPENS_HERE (streamlined): " + e.getMessage());
            e.printStackTrace();
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

            Story story = getStoryAtCurrentEncounter(gameCode);

            //Create or update the existing story
            if (story == null) {
                story = new Story();
                story.setPrompt(winningSubmission.getCurrentText());
                story.setPlayerId(AuthorConstants.DUNGEON_PLAYER);
                story.setGameCode(gameCode);
                story.setEncounterLabel(encounter.getEncounterLabel());
                storyDAO.createStory(story);
            } else {
                story.setPrompt(winningSubmission.getCurrentText());
                storyDAO.updateStory(story);
            }

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
     * Handles WHAT_CAN_WE_TRY phase in streamlined mode: Adds all winning submissions as options to the story
     */
    private void handleWhatCanWeTryStreamlined(String gameCode, List<TextSubmission> winningSubmissions) {
        try {
            if (winningSubmissions.isEmpty()) {
                return;
            }

            Story story = getStoryAtCurrentEncounter(gameCode);
            if (story == null) {
                return;
            }

            // Get current options or initialize empty list
            List<Option> currentOptions = story.getOptions();
            if (currentOptions == null) {
                currentOptions = new ArrayList<>();
            }

            // Add all winning submissions as options
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
            story.setGameCode(gameCode);
            storyDAO.updateStory(story);
        } catch (Exception e) {
            System.err.println("Failed to handle WHAT_CAN_WE_TRY (streamlined): " + e.getMessage());
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

            Story story = getStoryAtCurrentEncounter(gameCode);
            if (story == null) {
                return;
            }

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
     * If a submission has outcomeTypeWithLabel with subTypes, adds a new option to the matching story
     * and removes the corresponding submission from WHAT_CAN_WE_TRY phase
     */
    private void handleHowDoesThisResolve(String gameCode, List<TextSubmission> winningSubmissions) {
        try {
            if (winningSubmissions.isEmpty()) {
                return;
            }

            List<Story> stories = storyDAO.getStories(gameCode);

            if (stories == null || stories.isEmpty()) {
                return;
            }

            // Get WHAT_CAN_WE_TRY phase to remove submissions
            CollaborativeTextPhase whatCanWeTryPhase = collaborativeTextDAO.getCollaborativeTextPhase(gameCode, GameState.WHAT_CAN_WE_TRY.name());

            // Process each winning submission
            for (TextSubmission winner : winningSubmissions) {
                OutcomeType outcomeTypeWithLabel = winner.getOutcomeTypeWithLabel();
                
                // Check if this submission has subTypes (new structure)
                if (outcomeTypeWithLabel != null 
                        && outcomeTypeWithLabel.getSubTypes() != null 
                        && !outcomeTypeWithLabel.getSubTypes().isEmpty()) {
                    
                    // Find the story where storyId matches the parent outcomeTypeWithLabel.id
                    String parentStoryId = outcomeTypeWithLabel.getId();
                    Story matchingStory = stories.stream()
                            .filter(story -> story.getStoryId().equals(parentStoryId))
                            .findFirst()
                            .orElse(null);
                    
                    if (matchingStory != null) {
                        // Process each subType
                        for (OutcomeType subType : outcomeTypeWithLabel.getSubTypes()) {
                            String subTypeId = subType.getId();
                            String subTypeLabel = subType.getLabel();
                            
                            if (subTypeId != null && !subTypeId.isEmpty() 
                                    && subTypeLabel != null && !subTypeLabel.isEmpty()) {
                                
                                // Initialize options list if null
                                if (matchingStory.getOptions() == null) {
                                    matchingStory.setOptions(new ArrayList<>());
                                }
                                
                                // Check if option with this ID already exists
                                boolean optionExists = matchingStory.getOptions().stream()
                                        .anyMatch(opt -> opt.getOptionId().equals(subTypeId));
                                
                                if (!optionExists) {
                                    // Add new option with subType.id as optionId and subType.label as successText
                                    Option newOption = new Option();
                                    newOption.setOptionText(subTypeLabel);
                                    newOption.setOptionId(subTypeId);
                                    newOption.setSuccessText(winner.getCurrentText());
                                    String originalAuthorId = winner.getAuthorId();
                                    if (winner.getAdditions() != null && !winner.getAdditions().isEmpty()) {
                                        TextAddition firstAddition = winner.getAdditions().get(0);
                                        if (firstAddition.getAuthorId() != null && !firstAddition.getAuthorId().isEmpty()) {
                                            originalAuthorId = firstAddition.getAuthorId();
                                        }
                                    }
                                    newOption.setOutcomeAuthorId(originalAuthorId);
                                    matchingStory.getOptions().add(newOption);
                                    
                                    // Update the story
                                    matchingStory.setGameCode(gameCode);
                                    storyDAO.updateStory(matchingStory);
                                }

                                // Remove the submission from WHAT_CAN_WE_TRY phase that matches subType.id
                                if (whatCanWeTryPhase != null) {
                                    boolean removed = whatCanWeTryPhase.removeSubmissionById(subTypeId);
                                    if (removed) {
                                        // Update the phase in Firestore
                                        collaborativeTextDAO.updateCollaborativeTextPhaseAtomically(gameCode, GameState.WHAT_CAN_WE_TRY.name(), whatCanWeTryPhase);
                                    }
                                }
                            }
                        }
                    }
                }
            }

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
     * Handles NAVIGATE_VOTING phase in streamlined mode: Moves player to next encounter (x++)
     */
    public boolean handleNavigationStreamlined(String gameCode) {
        try {
            // Get current game session and player coordinates
            GameSession gameSession = getGameSession(gameCode);
            GameBoard gameBoard = gameSession.getGameBoard();
            if (gameBoard == null) {
                System.err.println("Game board not found for game: " + gameCode);
                return false;
            }

            PlayerCoordinates currentCoords = gameBoard.getPlayerCoordinates();
            if (currentCoords == null) {
                System.err.println("Player coordinates not found for game: " + gameCode);
                return false;
            }

            // Increment x coordinate to move to next encounter
            int newX = currentCoords.getxCoordinate() + 1;
            int newY = currentCoords.getyCoordinate();

            // Create new player coordinates
            PlayerCoordinates newCoordinates = new PlayerCoordinates(newX, newY);

            gameSessionDAO.updatePlayerCoordinates(gameCode, newCoordinates);
            clearStoryWritingPhases(gameCode);

            Encounter encounter = gameBoard.getEncounter(newX, newY);
            return encounter != null;
        } catch (Exception e) {
            System.err.println("Failed to handle NAVIGATE_VOTING (streamlined): " + e.getMessage());
            e.printStackTrace();
            return false;
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
            collaborativeTextDAO.clearPhase(gameCode, GameState.WHAT_HAPPENS_HERE.name(), true);
            collaborativeTextDAO.clearPhase(gameCode, GameState.WHAT_CAN_WE_TRY.name(), true);
            collaborativeTextDAO.clearPhase(gameCode, GameState.HOW_DOES_THIS_RESOLVE.name(), true);
            collaborativeTextDAO.clearPhase(gameCode, GameState.HOW_DOES_THIS_RESOLVE_AGAIN.name(), true);
            collaborativeTextDAO.clearPhase(gameCode, GameState.MAKE_CHOICE_VOTING.name(), false);
            collaborativeTextDAO.clearPhase(gameCode, GameState.NAVIGATE_VOTING.name(), false);
            collaborativeTextDAO.clearPhase(gameCode, GameState.CAMPFIRE.name(), true);
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

        PhaseBaseInfo baseInfo = gameState.getPhaseBaseInfo(entityName, gameSession.getRoundNumber());
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

    /**
     * Gets the story at the current encounter.
     * @param gameCode The game code
     * @return Story at current encounter, or null if not found
     */
    public Story getStoryAtCurrentEncounter(String gameCode) {
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
                } else if (gameState == GameState.WHAT_ARE_WE_CAPABLE_OF_VOTE_WINNERS) {
                    yield "The winning submissions are...";
                } else if (gameState == GameState.HOW_DOES_THIS_RESOLVE_WINNERS){
                    yield "Now our heroes must make their choice. Choose from the options on your device and our resolution will be revealed.";
                } else if (gameState == GameState.WHAT_CAN_WE_TRY_WINNERS || gameState == GameState.WHAT_HAPPENS_HERE_WINNER) {
                    yield "The story so far...";
                } else if (gameState == GameState.SET_ENCOUNTERS_WINNERS) {
                    yield "These things have become part of this world";
                } else if (gameState == GameState.CAMPFIRE_WINNERS) {
                    yield "We talk long into the night...";
                } else if (gameState == GameState.NAVIGATE_WINNER) {
                    yield "We see the encounters ahead";
                } else {
                    yield "The winning submission is...";
                }
            }
        };
    }

    /**
     * Gets collaborative mode instructions, reusing logic based on collaborative mode and phase type
     */
    private String getCollaborativeModeInstructions(
            CollaborativeMode collaborativeMode,
            PhaseType phaseType,
            GameState gameState
        ) {
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

    /**
     * Gets all encounterLabels from the AdventureMap for streamlined mode.
     * @param gameCode The game code
     * @return List of encounterLabels
     */
    public List<EncounterLabel> getEncounterLabels(String gameCode) {
        try {
            GameSession gameSession = getGameSession(gameCode);
            AdventureMap adventureMap = gameSession.getAdventureMap();
            if (adventureMap == null || adventureMap.getEncounterLabels() == null) {
                return new ArrayList<>();
            }
            return adventureMap.getEncounterLabels();
        } catch (Exception e) {
            System.err.println("Failed to get encounterLabels: " + e.getMessage());
            return new ArrayList<>();
        }
    }


    /**
     * Gets outcome types based on the current game phase.
     * For WHAT_HAPPENS_HERE phase, returns encounterLabels as OutcomeType objects.
     * For HOW_DOES_THIS_RESOLVE phase, returns options as OutcomeType objects.
     * For WHAT_CAN_WE_TRY phase, returns a story distributed to the player based on their index.
     * @param gameCode The game code
     * @param playerId The player ID for distribution logic
     * @return List of OutcomeType objects
     */
    public List<OutcomeType> getOutcomeTypes(String gameCode, String playerId) {
        try {
            GameSession gameSession = getGameSession(gameCode);
            GameState phaseId = gameSession.getGameState().getPhaseId();

            if (phaseId == GameState.WHAT_HAPPENS_HERE) {
                if (gameSession.getRoundNumber() == 0) {
                    List<EncounterLabel> encounterLabels = getEncounterLabels(gameCode);

                    return encounterLabels.stream()
                            .map(label -> new OutcomeType(label.getEncounterId(), label.getEncounterLabel()))
                            .toList();
                } else {
                    // Round > 0: Distribute visited stories and filter encounters
                    List<Story> allVisitedStories = gameSession.getStories().stream().filter(Story::isVisited).toList();
                    
                    if (allVisitedStories.isEmpty()) {
                        return new ArrayList<>();
                    }

                    PlayerSortResult playerResult = getSortedPlayersAndIndex(gameSession.getPlayers(), playerId);
                    if (playerResult == null) {
                        return new ArrayList<>();
                    }
                    
                    List<Player> sortedPlayers = playerResult.sortedPlayers;
                    int playerIndex = playerResult.playerIndex;
                    
                    // Calculate offset: 3 for 4 players, 4 for more than 4 players
                    int offsetValue = sortedPlayers.size() == 4 ? 1 : 2;
                    int offsetPlayerIndex = (playerIndex + offsetValue) % sortedPlayers.size();
                    
                    // Sort visited stories by player order
                    List<Story> sortedVisitedStories = sortStoriesByPlayerOrder(sortedPlayers, allVisitedStories);
                    
                    // Distribute stories to player
                    List<OutcomeType> assignedStories = distributeStoriesToPlayer(sortedVisitedStories, sortedPlayers, offsetPlayerIndex, false);
                    
                    if (assignedStories.isEmpty()) {
                        return new ArrayList<>();
                    }
                    
                    // Get the assigned story's encounter label ID
                    String assignedStoryId = assignedStories.getFirst().getId();
                    Story assignedStory = sortedVisitedStories.stream()
                            .filter(story -> story.getStoryId().equals(assignedStoryId))
                            .findFirst()
                            .orElse(null);
                    
                    final String assignedEncounterLabelId = (assignedStory != null && assignedStory.getEncounterLabel() != null)
                            ? assignedStory.getEncounterLabel().getEncounterId()
                            : null;
                    
                    // Get all encounter labels
                    List<EncounterLabel> allEncounterLabels = getEncounterLabels(gameCode);
                    
                    // Get encounter label IDs used in stories (except the assigned one)
                    Set<String> usedEncounterLabelIds = allVisitedStories.stream()
                            .filter(story -> story.getEncounterLabel() != null)
                            .map(story -> story.getEncounterLabel().getEncounterId())
                            .filter(id -> !id.equals(assignedEncounterLabelId)) // Keep the assigned one
                            .collect(Collectors.toSet());
                    
                    // Filter encounters: remove used ones, but keep the assigned one
                    List<EncounterLabel> availableEncounterLabels = allEncounterLabels.stream()
                            .filter(label -> {
                                String labelId = label.getEncounterId();
                                return !usedEncounterLabelIds.contains(labelId);
                            })
                            .toList();
                    
                    // Build OutcomeType list from filtered encounters
                    return availableEncounterLabels.stream()
                            .map(label -> {
                                if (assignedStory != null
                                        && label.getEncounterId().equals(assignedStory.getEncounterLabel().getEncounterId())) {
                                    return new OutcomeType(label.getEncounterId(), label.getEncounterLabel(), assignedStoryId);
                                } else {
                                    return new OutcomeType(label.getEncounterId(), label.getEncounterLabel());
                                }
                            })
                            .toList();
                }
            } else if (phaseId == GameState.WHAT_CAN_WE_TRY) {
                List<Story> allUnvisitedStories = gameSession.getStories().stream().filter(story -> !story.isVisited()).toList();
                
                if (allUnvisitedStories.isEmpty()) {
                    return new ArrayList<>();
                }
                
                PlayerSortResult playerResult = getSortedPlayersAndIndex(gameSession.getPlayers(), playerId);
                if (playerResult == null) {
                    return new ArrayList<>();
                }
                
                List<Player> sortedPlayers = playerResult.sortedPlayers;
                int playerIndex = playerResult.playerIndex;

                // Sort stories by matching authorId to player order
                List<Story> sortedStories = sortStoriesByPlayerOrder(sortedPlayers, allUnvisitedStories);
                
                if (sortedStories.isEmpty()) {
                    return new ArrayList<>();
                }
                
                // Check how many submissions the player has made for this phase
                long playerSubmissionCount = getPlayerSubmissionCountForPhase(gameCode, playerId, GameState.WHAT_CAN_WE_TRY);
                
                // Only return multiple stories if player has made 2+ submissions
                boolean shouldReturnMultiple = playerSubmissionCount >= 2;

                // Offset player index by one (wrapping if needed)
                int offsetPlayerIndex = (playerIndex + 1) % sortedPlayers.size();
                List<OutcomeType> nextPlayersStories = distributeStoriesToPlayer(sortedStories, sortedPlayers, offsetPlayerIndex, shouldReturnMultiple);

                int nextNextIndex = sortedPlayers.size() > 4 ? offsetPlayerIndex + 1 : playerIndex;
                List<OutcomeType> nextNextPlayerStories = distributeStoriesToPlayer(sortedStories, sortedPlayers, nextNextIndex, shouldReturnMultiple);
                nextPlayersStories.addAll(nextNextPlayerStories);
                return nextPlayersStories;
            } else if (phaseId == GameState.HOW_DOES_THIS_RESOLVE || phaseId == GameState.HOW_DOES_THIS_RESOLVE_AGAIN) {
                // Get stories and players for distribution
                List<Story> allUnvisitedStories = gameSession.getStories().stream().filter(story -> !story.isVisited()).toList();
                
                if (allUnvisitedStories.isEmpty()) {
                    return new ArrayList<>();
                }
                
                PlayerSortResult playerResult = getSortedPlayersAndIndex(gameSession.getPlayers(), playerId);
                if (playerResult == null) {
                    return new ArrayList<>();
                }
                
                List<Player> sortedPlayers = playerResult.sortedPlayers;
                int playerIndex = playerResult.playerIndex;
                
                // Get WHAT_CAN_WE_TRY submissions to count related submissions per story
                CollaborativeTextPhase whatCanWeTry = collaborativeTextDAO.getCollaborativeTextPhase(gameCode, GameState.WHAT_CAN_WE_TRY.name());
                List<TextSubmission> whatCanWeTrySubmissions = (whatCanWeTry != null && whatCanWeTry.getSubmissions() != null) 
                        ? whatCanWeTry.getSubmissions() 
                        : new ArrayList<>();

                // Sort stories by matching authorId to player order
                List<Story> sortedStories = sortStoriesByPlayerOrder(sortedPlayers, allUnvisitedStories);
                
                if (sortedStories.isEmpty()) {
                    return new ArrayList<>();
                }
                
                int numStories = sortedStories.size();
                
                // Offset player index by one (wrapping if needed)
                int offsetValue = 3;
                if (phaseId == GameState.HOW_DOES_THIS_RESOLVE_AGAIN) {
                    offsetValue = sortedPlayers.size() > 4 ? 4 : 2;
                }

                int offsetPlayerIndex = (playerIndex + offsetValue) % sortedPlayers.size();
                
                // Get assigned story (only one story per player for this phase)
                List<OutcomeType> assignedStories = distributeStoriesToPlayer(sortedStories, sortedPlayers, offsetPlayerIndex, false);
                
                if (assignedStories.isEmpty()) {
                    return new ArrayList<>();
                }
                
                // Get the assigned story ID and prompt (first story from the list)
                String assignedStoryId = assignedStories.getFirst().getId();
                
                // Find the assigned story to get its prompt
                final String storyPrompt = sortedStories.stream()
                        .filter(story -> story.getStoryId().equals(assignedStoryId))
                        .findFirst()
                        .map(story -> story.getPrompt() != null ? story.getPrompt() : "")
                        .orElse("");
                
                // Get submissions related to the assigned story
                List<TextSubmission> relatedSubmissions = whatCanWeTrySubmissions.stream()
                        .filter(submission -> {
                            if (submission.getOutcomeTypeWithLabel() == null) {
                                return false;
                            }
                            String outcomeTypeId = submission.getOutcomeTypeWithLabel().getId();
                            return outcomeTypeId != null && outcomeTypeId.equals(assignedStoryId);
                        })
                        .sorted(Comparator.comparing(TextSubmission::getCreatedAt))
                        .toList();
                
                if (relatedSubmissions.isEmpty()) {
                    return createPreCannedOptions(assignedStoryId, storyPrompt);
                }
                
                int numPlayers = sortedPlayers.size();
                // Check if player index is shared (wraps around when numPlayers > numStories)
                // A player shares if: offsetPlayerIndex >= numStories OR offsetPlayerIndex < (numPlayers - numStories)
                boolean isSharedIndex = numPlayers > numStories && 
                        (offsetPlayerIndex >= numStories || offsetPlayerIndex < (numPlayers - numStories));
                
                if (isSharedIndex) {
                    // Multiple players share this story - distribute submissions evenly using modulus
                    // Find all players that share this story (same story index after modulus)
                    int storyIndex = offsetPlayerIndex % numStories;
                    List<Integer> sharingPlayerIndices = new ArrayList<>();
                    for (int i = 0; i < numPlayers; i++) {
                        if ((i % numStories) == storyIndex) {
                            sharingPlayerIndices.add(i);
                        }
                    }
                    
                    // Find this player's position among the sharing players (sorted by player index)
                    sharingPlayerIndices.sort(Integer::compareTo);
                    int playerPositionInSharing = sharingPlayerIndices.indexOf(offsetPlayerIndex);
                    int numSharingPlayers = sharingPlayerIndices.size();
                    
                    // Sort submissions by createdAt and distribute using modulus
                    List<TextSubmission> sortedRelatedSubmissions = relatedSubmissions.stream()
                            .sorted(Comparator.comparing(TextSubmission::getCreatedAt))
                            .toList();
                    
                    // Create subTypes for distributed submissions
                    List<OutcomeType> distributedSubTypes = new ArrayList<>();
                    for (int i = 0; i < sortedRelatedSubmissions.size(); i++) {
                        if (i % numSharingPlayers == playerPositionInSharing) {
                            TextSubmission submission = sortedRelatedSubmissions.get(i);
                            distributedSubTypes.add(new OutcomeType(
                                submission.getSubmissionId(),
                                submission.getCurrentText()
                            ));
                        }
                    }
                    
                    // Return single OutcomeType with story info and subTypes array
                    OutcomeType storyOutcomeType = new OutcomeType(assignedStoryId, storyPrompt);
                    storyOutcomeType.setSubTypes(distributedSubTypes);
                    return List.of(storyOutcomeType);
                } else {
                    // Player has unique story assignment - return all related submissions as subTypes
                    List<OutcomeType> allSubTypes = relatedSubmissions.stream()
                            .map(submission -> new OutcomeType(
                                submission.getSubmissionId(),
                                submission.getCurrentText()
                            ))
                            .toList();
                    
                    // Return single OutcomeType with story info and subTypes array
                    OutcomeType storyOutcomeType = new OutcomeType(assignedStoryId, storyPrompt);
                    storyOutcomeType.setSubTypes(allSubTypes);
                    return List.of(storyOutcomeType);
                }
            } else {
                return new ArrayList<>();
            }
        } catch (Exception e) {
            System.err.println("Failed to get outcome types: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Gets the count of submissions a player has made for a specific collaborative text phase.
     * @param gameCode The game code
     * @param playerId The player ID
     * @param phase The game state phase to check
     * @return The count of submissions made by the player for this phase
     */
    private long getPlayerSubmissionCountForPhase(String gameCode, String playerId, GameState phase) {
        CollaborativeTextPhase phaseData = collaborativeTextDAO.getCollaborativeTextPhase(gameCode, phase.name());
        if (phaseData == null || phaseData.getSubmissions() == null) {
            return 0;
        }
        return phaseData.getSubmissions().stream()
                .filter(submission -> playerId.equals(submission.getAuthorId()))
                .count();
    }

    /**
     * Helper class to hold sorted players and player index
     */
    private static class PlayerSortResult {
        final List<Player> sortedPlayers;
        final int playerIndex;
        
        PlayerSortResult(List<Player> sortedPlayers, int playerIndex) {
            this.sortedPlayers = sortedPlayers;
            this.playerIndex = playerIndex;
        }
    }

    /**
     * Sorts players by joinedAt and finds the index of the specified player.
     * @param players List of players to sort
     * @param playerId The ID of the player to find
     * @return PlayerSortResult containing sorted players and player index, or null if player not found or invalid input
     */
    private PlayerSortResult getSortedPlayersAndIndex(List<Player> players, String playerId) {
        if (players == null || players.isEmpty() || playerId == null || playerId.isEmpty()) {
            return null;
        }
        
        List<Player> sortedPlayers = players.stream()
                .filter(player -> player.getJoinedAt() != null)
                .sorted(Comparator.comparing(Player::getJoinedAt))
                .toList();
        
        int playerIndex = -1;
        for (int i = 0; i < sortedPlayers.size(); i++) {
            if (sortedPlayers.get(i).getAuthorId().equals(playerId)) {
                playerIndex = i;
                break;
            }
        }
        
        if (playerIndex == -1) {
            return null;
        }
        
        return new PlayerSortResult(sortedPlayers, playerIndex);
    }

    /**
     * Sorts stories by matching their authorId to the order of players in the sorted players list.
     * Stories with matching players are sorted by player index, stories without matching players
     * are placed at the end and sorted by createdAt.
     * @param sortedPlayers List of players sorted by joinedAt
     * @param stories List of stories to sort
     * @return List of stories sorted by player order
     */
    private List<Story> sortStoriesByPlayerOrder(List<Player> sortedPlayers, List<Story> stories) {
        // Create a map of player authorId to their index in the sorted list
        Map<String, Integer> playerIndexMap = new HashMap<>();
        for (int i = 0; i < sortedPlayers.size(); i++) {
            playerIndexMap.put(sortedPlayers.get(i).getAuthorId(), i);
        }
        
        // Sort stories by matching authorId to player order, then by createdAt for stories without matching players
        final Map<String, Integer> finalPlayerIndexMap = playerIndexMap;
        return stories.stream()
                .sorted(Comparator
                        .comparing((Story story) -> {
                            Integer playerIdx = finalPlayerIndexMap.get(story.getAuthorId());
                            // Stories with matching players come first, sorted by player index
                            // Stories without matching players come last, sorted by createdAt
                            return playerIdx != null ? playerIdx : Integer.MAX_VALUE;
                        })
                        .thenComparing(Story::getCreatedAt))
                .toList();
    }

    /**
     * Creates pre-canned options when there are no submissions for a story.
     * Randomly selects 3 options from a predefined list.
     * @param assignedStoryId The ID of the assigned story
     * @param storyPrompt The prompt text of the story
     * @return List containing a single OutcomeType with pre-canned subTypes
     */
    private List<OutcomeType> createPreCannedOptions(String assignedStoryId, String storyPrompt) {
        List<String> preCannedOptions = List.of(
            "Use your secret vegetables",
            "Ask for help",
            "Ignore them",
            "Call for the king",
            "Use your tongue",
            "Trust your instincts",
            "Set a fire",
            "Snacks!",
            "Become wet",
            "Consult with the stars",
            "Phone a friend",
            "Use 'smooch'",
            "Run away",
            "Yell loudly!",
            "Attack",
            "Defend",
            "Examine",
            "Use your feet",
            "Try to be brave"
        );
        
        // Randomly select 3 options
        List<String> shuffled = new ArrayList<>(preCannedOptions);
        Collections.shuffle(shuffled);
        List<String> selectedOptions = shuffled.subList(0, Math.min(3, shuffled.size()));
        
        // Create subTypes from pre-canned options
        List<OutcomeType> preCannedSubTypes = new ArrayList<>();
        for (String optionText : selectedOptions) {
            String optionId = UUID.randomUUID().toString();
            preCannedSubTypes.add(new OutcomeType(optionId, optionText));
        }
        
        // Return single OutcomeType with story info and pre-canned subTypes
        OutcomeType storyOutcomeType = new OutcomeType(assignedStoryId, storyPrompt);
        storyOutcomeType.setSubTypes(preCannedSubTypes);
        return List.of(storyOutcomeType);
    }

    /**
     * Distributes stories to a player based on their index and submission count.
     * @param sortedStories List of stories sorted by createdAt
     * @param sortedPlayers List of players sorted by joinedAt
     * @param playerIndex The index of the player in the sorted players list
     * @param shouldReturnMultiple Whether to return multiple stories (player has 2+ submissions)
     * @return List of OutcomeType objects representing the assigned stories
     */
    private List<OutcomeType> distributeStoriesToPlayer(List<Story> sortedStories, List<Player> sortedPlayers, int playerIndex, boolean shouldReturnMultiple) {
        int numPlayers = sortedPlayers.size();
        int numStories = sortedStories.size();
        List<OutcomeType> assignedStories = new ArrayList<>();

        if (numStories <= numPlayers || !shouldReturnMultiple) {
            // Fewer stories than players, or player hasn't made 2+ submissions: return one story with wrapping
            int storyIndex = playerIndex % numStories;
            Story assignedStory = sortedStories.get(storyIndex);
            assignedStories.add(new OutcomeType(assignedStory.getStoryId(), assignedStory.getPrompt()));
        } else {
            // More stories than players AND player has 2+ submissions: return multiple stories
            for (int i = 0; i < sortedStories.size(); i++) {
                if (i % numPlayers == playerIndex) {
                    Story story = sortedStories.get(i);
                    assignedStories.add(new OutcomeType(story.getStoryId(), story.getPrompt()));
                }
            }
        }

        return assignedStories;
    }
}