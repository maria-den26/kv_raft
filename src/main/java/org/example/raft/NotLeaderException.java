package org.example.raft;

/**
 * Indicates that a client request hit a follower and should be retried on leader.
 */
public final class NotLeaderException extends RuntimeException {
    private final String leaderHint;

    public NotLeaderException(String leaderHint) {
        super("Not a leader. Try " + leaderHint);
        this.leaderHint = leaderHint;
    }

    public String getLeaderHint() {
        return leaderHint;
    }
}


