package client.nowhere.helper;

import client.nowhere.dao.CollaborativeTextDAO;
import client.nowhere.dao.GameSessionDAO;
import client.nowhere.dao.StoryDAO;
import client.nowhere.exception.ValidationException;
import client.nowhere.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class VotingHelper {

    private final GameSessionDAO gameSessionDAO;
    private final CollaborativeTextDAO collaborativeTextDAO;
    private final ActiveSessionHelper activeSessionHelper;
    private final OutcomeTypeHelper outcomeTypeHelper;
    private final StoryDAO storyDAO;

    @Autowired
    public VotingHelper(GameSessionDAO gameSessionDAO,
                        CollaborativeTextDAO collaborativeTextDAO,
                        ActiveSessionHelper activeSessionHelper,
                        OutcomeTypeHelper outcomeTypeHelper,
                        StoryDAO storyDAO
    ) {
        this.gameSessionDAO = gameSessionDAO;
        this.collaborativeTextDAO = collaborativeTextDAO;
        this.activeSessionHelper = activeSessionHelper;
        this.outcomeTypeHelper = outcomeTypeHelper;
        this.storyDAO = storyDAO;
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
        String phasePath = PhasePaths.phasePath(gameSession, phaseIdState);

        switch (phaseIdState) {
            case LOCATION_VOTING: {
                List<TextSubmission> submissions = collaborativeTextDAO.getSubmissions(phasePath);
                if (submissions.isEmpty()) {
                    throw new ValidationException("LOCATION_VOTING phase not found for game: " + gameCode);
                }
                // Round 1's pool covers rounds 1 and 2, so round 1 votes on its first half. Round 2's
                // phase holds only the second half (copied by initializeLocationVoting).
                if (gameSession.getRoundNumber() == 1) {
                    return submissions.subList(0, (submissions.size() + 1) / 2);
                }
                return submissions;
            }
            case MAKE_PARTNER_CHOICE_VOTING: {
                Story currentStory = gameSession.getStoryAtCurrentPlayerCoordinates();
                if (currentStory == null || !playerId.equals(currentStory.getPlayerId())) {
                    activeSessionHelper.update(gameCode, gameSession.getGameState(), playerId, true);
                    return new ArrayList<>();
                }
                Player storyPlayer = gameSession.getPlayers().stream()
                        .filter(p -> p.getAuthorId().equals(playerId))
                        .findFirst().orElse(null);
                if (storyPlayer == null || storyPlayer.getSelectedLocationId() == null) {
                    activeSessionHelper.update(gameCode, gameSession.getGameState(), playerId, true);
                    return new ArrayList<>();
                }
                String myLocationId = storyPlayer.getSelectedLocationId();
                List<String> sharedLocationPlayerIds = GameSessionHelper.getAvailablePartnersAtLocation(
                        gameSession, myLocationId, playerId)
                        .stream()
                        .map(Player::getAuthorId)
                        .toList();
                if (sharedLocationPlayerIds.isEmpty()) {
                    activeSessionHelper.update(gameCode, gameSession.getGameState(), playerId, true);
                    return new ArrayList<>();
                }
                return collaborativeTextDAO.getSubmissions(phasePath).stream()
                        .filter(s -> sharedLocationPlayerIds.contains(s.getSubmissionId()))
                        .toList();
            }
            case LOCATION_OPTION_MAKE_CHOICE_VOTING: {
                Story currentStory = gameSession.getStoryAtCurrentPlayerCoordinates();
                if (currentStory == null || !playerId.equals(currentStory.getPlayerId())) {
                    activeSessionHelper.update(gameCode, gameSession.getGameState(), playerId, true);
                    return new ArrayList<>();
                }
                String locationId = currentStory.getLocation() != null ? currentStory.getLocation().getId() : null;
                return collaborativeTextDAO.getSubmissions(phasePath).stream()
                        .filter(s -> locationId != null && locationId.equals(s.getOutcomeType()))
                        .toList();
            }
            case ACCEPT_PARTNER_CHOICE_VOTING: {
                CollaborativeTextPhase makePartnerPhase = collaborativeTextDAO.loadPhase(
                        PhasePaths.phasePath(gameSession, GameState.MAKE_PARTNER_CHOICE_VOTING),
                        GameState.MAKE_PARTNER_CHOICE_VOTING.name(),
                        PhasePaths.voteScope(gameSession));
                boolean wasVotedFor = makePartnerPhase.getPlayerVotes().values().stream()
                        .flatMap(List::stream)
                        .anyMatch(v -> playerId.equals(v.getSubmissionId()));
                if (!wasVotedFor) {
                    activeSessionHelper.update(gameCode, gameSession.getGameState(), playerId, true);
                    return new ArrayList<>();
                }
                return makePartnerPhase.getSubmissions().stream()
                        .filter(s -> playerId.equals(s.getSubmissionId()))
                        .toList();
            }
            case MAKE_OUTCOME_CHOICE_VOTING: {
                Story currentStory = gameSession.getStoryAtCurrentPlayerCoordinates();
                if (currentStory == null) {
                    return new ArrayList<>();
                }

                boolean isPartner = currentStory.getPartnerIds().contains(playerId);
                boolean allPlayersInStory = gameSession.getPlayers().stream()
                        .allMatch(p -> currentStory.getPlayerIds().contains(p.getAuthorId()));
                boolean excludePlayerFromVote = !isPartner && (allPlayersInStory
                        ? !currentStory.getPlayerId().equals(playerId)
                        : currentStory.getPlayerIds().contains(playerId));
                if (excludePlayerFromVote) {
                    activeSessionHelper.update(gameCode, gameSession.getGameState(), playerId, true);
                    return new ArrayList<>();
                }

                Option selectedOption = currentStory.getSelectedOption();

                if (selectedOption == null || selectedOption.getOutcomeForks() == null) {
                    return new ArrayList<>();
                }

                return selectedOption.getOutcomeForks().stream().map(OutcomeFork::getTextSubmission).toList();
            }
            default: {
                List<TextSubmission> submissions = collaborativeTextDAO.getSubmissions(phasePath);
                List<OutcomeType> outcomeTypes = getMakeChoiceStoryOutcomes(gameSession, playerId);

                OutcomeType outcomeType = outcomeTypes.isEmpty() ? null : outcomeTypes.getFirst();

                // Return top 5 submissions except player's own, ordered by most additions first, then by creation time
                // For WHAT_DO_WE_FEAR, return all submissions; for WHAT_ARE_WE_CAPABLE_OF, return top 6; for others, return top 5
                // For WHAT_WILL_BECOME_OF_US, include player's own submissions (they'll be filtered by outcomeType on frontend)
                boolean isWhatDoWeFear = phaseIdState == GameState.WHAT_DO_WE_FEAR;
                boolean isWhatAreWeCapableOf = phaseIdState == GameState.WHAT_ARE_WE_CAPABLE_OF;
                int limit = isWhatDoWeFear ? Integer.MAX_VALUE : (isWhatAreWeCapableOf ? 6 : 5);

                List<TextSubmission> submissionsPlayerCanVoteOn = submissions.stream()
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

                if (submissionsPlayerCanVoteOn.isEmpty() && phaseIdState == GameState.MAKE_CHOICE_VOTING) {
                    activeSessionHelper.update(gameCode, gameSession.getGameState(), playerId, true);
                }

                return submissionsPlayerCanVoteOn;
            }
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
        if (playerVotes.stream().anyMatch(vote -> vote.getPlayerId() == null || vote.getPlayerId().isEmpty())) {
            throw new ValidationException("Every player vote needs a playerId");
        }

        GameSession gameSession = gameSessionDAO.getGame(gameCode);
        GameState phaseIdState = gameSession.getGameState().getPhaseId();
        if (phaseIdState == null) {
            throw new ValidationException("Current game state does not support voting: " + gameSession.getGameState());
        }
        String phasePath = PhasePaths.phasePath(gameSession, phaseIdState);
        String voteScope = PhasePaths.voteScope(gameSession);

        // One blind write of each voter's whole ballot (normally a single player per request)
        Map<String, List<PlayerVote>> votesByPlayer = playerVotes.stream()
                .collect(Collectors.groupingBy(PlayerVote::getPlayerId, LinkedHashMap::new, Collectors.toList()));
        votesByPlayer.forEach((votingPlayerId, ballot) ->
                collaborativeTextDAO.putVotes(phasePath, voteScope, votingPlayerId, ballot));

        if (phaseIdState == GameState.LOCATION_VOTING) {
            String votingPlayerId = playerVotes.getFirst().getPlayerId();
            if (votingPlayerId != null && !votingPlayerId.isEmpty()) {
                activeSessionHelper.update(gameCode, gameSession.getGameState(), votingPlayerId, true);
            }
        }

        switch (phaseIdState) {
            case ACCEPT_PARTNER_CHOICE_VOTING, MAKE_PARTNER_CHOICE_VOTING ->
                setNonVotingPlayersToDone(gameSession, playerVotes);
            case MAKE_CHOICE_VOTING ->
                setNonActivePlayersToDone(gameSession);
            case MAKE_OUTCOME_CHOICE_VOTING ->
                setActivePlayersToDone(gameSession);
        }

        // The player client ignores this response, so don't re-read the phase to build it.
        CollaborativeTextPhase response = new CollaborativeTextPhase();
        response.setPhaseId(phaseIdState.name());
        response.setPlayerVotes(votesByPlayer);
        response.setPlayersWhoVoted(new ArrayList<>(votesByPlayer.keySet()));
        return response;
    }

    private void setNonVotingPlayersToDone(GameSession gameSession, List<PlayerVote> playerVotes) {
        String votingPlayerId = playerVotes.getFirst().getPlayerId();

        for (Player player : gameSession.getPlayers()) {
                if (!votingPlayerId.equals(player.getAuthorId())) {
                    activeSessionHelper.update(gameSession.getGameCode(), gameSession.getGameState(), player.getAuthorId(), true);
                }
        }
    }

    private void setNonActivePlayersToDone(GameSession gameSession) {
        for (Player player : gameSession.getPlayers()) {
            if (!gameSession.getActiveGameStateSession().getIsPlayerDone().get(player.getAuthorId())) {
                List<OutcomeType> outcomeTypes = getMakeChoiceStoryOutcomes(gameSession, player.getAuthorId());

                if (outcomeTypes.isEmpty()) {
                    activeSessionHelper.update(gameSession.getGameCode(), gameSession.getGameState(), player.getAuthorId(), true);
                }
            }
        }
    }

    private void setActivePlayersToDone(GameSession gameSession) {
        for (Player player : gameSession.getPlayers()) {
            if (!gameSession.getActiveGameStateSession().getIsPlayerDone().get(player.getAuthorId())) {
                Story story = gameSession.getStoryAtCurrentPlayerCoordinates();

                if (story.getPlayerIds().contains(player.getAuthorId())) {
                    activeSessionHelper.update(gameSession.getGameCode(), gameSession.getGameState(), player.getAuthorId(), true);
                }
            }
        }
    }

    public List<OutcomeType> getMakeChoiceStoryOutcomes(GameSession gameSession, String playerId) {
        GameState phaseId = gameSession.getGameState().getPhaseId();

        if (phaseId != GameState.MAKE_CHOICE_VOTING && phaseId != GameState.MAKE_PARTNER_CHOICE_VOTING) {
            return new ArrayList<>();
        }

        Story story = gameSession.getStoryAtCurrentPlayerCoordinates();

        if (story.getPlayerIds().contains(playerId) || story.getPartnerIds().contains(playerId)) {
            return outcomeTypeHelper.distributeStoriesToPlayer(List.of(story), story.getAuthorId());
        } else {
            return new ArrayList<>();
        }
    }
}
