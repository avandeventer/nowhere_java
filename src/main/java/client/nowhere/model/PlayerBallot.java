package client.nowhere.model;

import java.util.ArrayList;
import java.util.List;

/**
 * One player's ranked votes for one vote (see PhasePaths.voteScope), stored as its own document at
 * {phasePath}/votes/{voteScope}_{playerId}. Each player only ever writes
 * their own ballot, so votes need no transaction.
 */
public class PlayerBallot {
    private String playerId;
    private String voteScope;
    private List<PlayerVote> votes;

    public PlayerBallot() {
        this.votes = new ArrayList<>();
    }

    public PlayerBallot(String playerId, String voteScope, List<PlayerVote> votes) {
        this.playerId = playerId;
        this.voteScope = voteScope;
        this.votes = votes;
    }

    public String getPlayerId() { return playerId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }

    public String getVoteScope() { return voteScope; }
    public void setVoteScope(String voteScope) { this.voteScope = voteScope; }

    public List<PlayerVote> getVotes() { return votes; }
    public void setVotes(List<PlayerVote> votes) { this.votes = votes; }
}
