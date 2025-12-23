package org.example.raft.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class RequestVoteResponse {
    private final long term;
    private final boolean voteGranted;

    @JsonCreator
    public RequestVoteResponse(@JsonProperty("term") long term,
                               @JsonProperty("voteGranted") boolean voteGranted) {
        this.term = term;
        this.voteGranted = voteGranted;
    }

    public long getTerm() {
        return term;
    }

    public boolean isVoteGranted() {
        return voteGranted;
    }
}


