package client.nowhere.helper;

import client.nowhere.dao.CollaborativeTextDAO;
import client.nowhere.dao.GameSessionDAO;
import client.nowhere.exception.ValidationException;
import client.nowhere.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
public class VotingHelper {

    private final GameSessionDAO gameSessionDAO;
    private final CollaborativeTextDAO collaborativeTextDAO;
    private final ActiveSessionHelper activeSessionHelper;
    private final OutcomeTypeHelper outcomeTypeHelper;

    @Autowired
    public VotingHelper(GameSessionDAO gameSessionDAO, CollaborativeTextDAO collaborativeTextDAO, ActiveSessionHelper activeSessionHelper, OutcomeTypeHelper outcomeTypeHelper) {
        this.gameSessionDAO = gameSessionDAO;
        this.collaborativeTextDAO = collaborativeTextDAO;
        this.activeSessionHelper = activeSessionHelper;
        this.outcomeTypeHelper = outcomeTypeHelper;
    }

    /**
     * Gets submissions for voting phase (excludes player's own submissions)
     * @param gameCode The game code
     * @param playerId The player requesting submissions
     * @return List of submissions ordered by phase
     */
    public List<TextSubmission> getVotingSubmissionsForPlayer(String gameCode, String playerId) {
        GameSession gameSession = gameSessionDAO.getGame(gameCode);
        GameState phaseIdState = gameSession.getGameState().getPhaseId();
        if (phaseIdState == null) {
            throw new ValidationException("Current game state does not support voting: " + gameSession.getGameState());
        }
        String phaseId = phaseIdState.name();

        if (phaseIdState == GameState.MAKE_OUTCOME_CHOICE_VOTING) {
            Story currentStory = gameSession.getStoryAtCurrentPlayerCoordinates();
            if (currentStory != null && currentStory.getPlayerIds().contains(playerId)) {
                return new ArrayList<>();
            }

            // Get submissions from HOW_DOES_THIS_RESOLVE and HOW_DOES_THIS_RESOLVE_AGAIN phases
            // filtered by currentStory.selectedOptionId
            if (currentStory != null && !currentStory.getSelectedOptionId().isEmpty()) {
                String selectedOptionId = currentStory.getSelectedOptionId();
                Option selectedOption = currentStory.getOptions().stream().filter(option -> option.getOptionId().equals(selectedOptionId))
                        .findFirst().orElse(null);
                if (selectedOption == null || selectedOption.getOutcomeForks() == null) {
                    return null;
                }
                return selectedOption.getOutcomeForks().stream().map(OutcomeFork::getTextSubmission).toList();
            }
        }

        // Retrieve the phase
        CollaborativeTextPhase phase = collaborativeTextDAO.getCollaborativeTextPhase(gameCode, phaseId);
        List<OutcomeType> outcomeTypes = getMakeChoiceStoryOutcomes(gameCode, playerId);

        OutcomeType outcomeType = outcomeTypes.isEmpty() ? null : outcomeTypes.getFirst();
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

        else if (outcomeType != null
                && outcomeType.getId() != null
                && !outcomeType.getId().isEmpty()) {
            if (outcomeType.getId().equals(submission.getOutcomeTypeWithLabel().getId())
                    || outcomeType.getId().equals(submission.getOutcomeType())) {
                return true;
            }

            if (outcomeType.getSubTypes() != null && !outcomeType.getSubTypes().isEmpty()) {
                for (OutcomeType subType : outcomeType.getSubTypes()) {
                    if (subType.getId().equals(submission.getOutcomeTypeWithLabel().getId()) || subType.getId().equals(submission.getOutcomeType())) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Sets the outcomeTypeWithLabel on a submission if the outcomeType matches and is not null or empty.
     * @param submission The submission to update
     * @param outcomeType The outcome type to set if it matches
     */
    private void setOutcomeTypeWithLabelIfMatch(TextSubmission submission, OutcomeType outcomeType) {
        if (outcomeType != null
                && outcomeType.getId() != null
                && !outcomeType.getId().isEmpty()) {

            if (outcomeType.getId().equals(submission.getOutcomeTypeWithLabel().getId())
                    || outcomeType.getId().equals(submission.getOutcomeType())) {
                submission.setOutcomeTypeWithLabel(outcomeType);
            }

            if (outcomeType.getSubTypes() != null && !outcomeType.getSubTypes().isEmpty()) {
                for (OutcomeType subType : outcomeType.getSubTypes()) {
                    if (subType.getId().equals(submission.getOutcomeTypeWithLabel().getId()) || subType.getId().equals(submission.getOutcomeType())) {
                        submission.getOutcomeTypeWithLabel().getSubTypes().add(subType);
                    }
                }
            }
        }
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

        GameSession gameSession = gameSessionDAO.getGame(gameCode);
        GameState phaseIdState = gameSession.getGameState().getPhaseId();
        if (phaseIdState == null) {
            throw new ValidationException("Current game state does not support voting: " + gameSession.getGameState());
        }
        String phaseId = phaseIdState.name();

        // Submit each vote atomically
        for (PlayerVote vote : playerVotes) {
            collaborativeTextDAO.addVoteAtomically(gameCode, phaseId, vote);
        }

        if (phaseIdState == GameState.MAKE_CHOICE_VOTING) {
            setNonActivePlayersToDone(gameSession);
        }

        if (phaseIdState == GameState.MAKE_OUTCOME_CHOICE_VOTING) {
            setActivePlayersToDone(gameSession);
        }

        // Return updated phase
        return collaborativeTextDAO.getCollaborativeTextPhase(gameCode, phaseId);
    }

    private void setNonActivePlayersToDone(GameSession gameSession) {
        for (Player player : gameSession.getPlayers()) {
            List<OutcomeType> outcomeTypes = getMakeChoiceStoryOutcomes(gameSession.getGameCode(), player.getAuthorId());

            if (outcomeTypes.isEmpty()) {
                activeSessionHelper.update(gameSession.getGameCode(), gameSession.getGameState(), player.getAuthorId(), true);
            }
        }
    }

    private void setActivePlayersToDone(GameSession gameSession) {
        for (Player player : gameSession.getPlayers()) {
            Story story = gameSession.getStoryAtCurrentPlayerCoordinates();

            if (story.getPlayerIds().contains(player.getAuthorId())) {
                activeSessionHelper.update(gameSession.getGameCode(), gameSession.getGameState(), player.getAuthorId(), true);
            }
        }
    }

    public List<OutcomeType> getMakeChoiceStoryOutcomes(String gameCode, String playerId) {
        GameSession gameSession = gameSessionDAO.getGame(gameCode);
        GameState phaseId = gameSession.getGameState().getPhaseId();

        if (phaseId != GameState.MAKE_CHOICE_VOTING) {
            return new ArrayList<>();
        }

        List<Story> allUnvisitedStories = gameSession.getStories().stream().filter(story -> !story.isVisited()).toList();

        if (allUnvisitedStories.isEmpty()) {
            return new ArrayList<>();
        }

        PlayerSortResult playerResult = gameSession.getSortedPlayersAndIndex(playerId);
        if (playerResult == null) {
            return new ArrayList<>();
        }

        List<Player> sortedPlayers = playerResult.getSortedPlayers();
        int playerIndex = playerResult.getPlayerIndex();

        // Sort stories by matching authorId to player order
        List<Story> sortedStories = outcomeTypeHelper.sortStoriesByPlayerOrder(sortedPlayers, allUnvisitedStories);

        if (sortedStories.isEmpty()) {
            return new ArrayList<>();
        }

        // Get assigned stories with offset of 3
        List<OutcomeType> assignedPlayerStories = outcomeTypeHelper.distributeStoriesToPlayer(sortedStories, sortedPlayers, playerIndex, -1, false);
        List<String> storyIds = assignedPlayerStories.stream().map(OutcomeType::getId).toList();

        Encounter encounter = gameSession.getGameBoard().getEncounterAtPlayerCoordinates();

        if (!storyIds.isEmpty()
                && encounter != null
                && storyIds.contains(encounter.getStoryId())) {
            return assignedPlayerStories;
        } else {
            return new ArrayList<>();
        }
    }
}
