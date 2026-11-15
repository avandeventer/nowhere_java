package client.nowhere.model;

import com.google.cloud.Timestamp;

/**
 * Stored on the game session while a game state transition is running, so it runs exactly once however
 * many requests try to start it (the last players' done flags, the TV timer, the host's next
 * button), across Cloud Run instances. See ActiveSessionDAO.lockTransition.
 *
 * A lock counts only while fromState is still the game's state and it is younger than
 * ActiveSessionDAO.TRANSITION_LOCK_TTL, so a transition whose instance died mid-way stops
 * blocking PUT /game/next after a couple of minutes.
 */
public class TransitionLock {

    String lockId;
    String fromState;
    Timestamp lockedAt;
    // Set once a transition actually starts; a redelivered Cloud Task then finds it and does nothing
    Timestamp startedAt;

    public TransitionLock() { }

    public TransitionLock(String lockId, String fromState, Timestamp lockedAt, Timestamp startedAt) {
        this.lockId = lockId;
        this.fromState = fromState;
        this.lockedAt = lockedAt;
        this.startedAt = startedAt;
    }

    public String getLockId() {
        return lockId;
    }

    public void setLockId(String lockId) {
        this.lockId = lockId;
    }

    public String getFromState() {
        return fromState;
    }

    public void setFromState(String fromState) {
        this.fromState = fromState;
    }

    public Timestamp getLockedAt() {
        return lockedAt;
    }

    public void setLockedAt(Timestamp lockedAt) {
        this.lockedAt = lockedAt;
    }

    public Timestamp getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Timestamp startedAt) {
        this.startedAt = startedAt;
    }
}
