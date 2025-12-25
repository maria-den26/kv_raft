package org.example.raft.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class RequestVoteRequest {
    private final long term;
    private final String candidateId;
    private final int lastLogIndex;
    private final long lastLogTerm;

    @JsonCreator
    public RequestVoteRequest(@JsonProperty("term") long term,
                              @JsonProperty("candidateId") String candidateId,
                              @JsonProperty("lastLogIndex") int lastLogIndex,
                              @JsonProperty("lastLogTerm") long lastLogTerm) {
        this.term = term;
        this.candidateId = candidateId;
        this.lastLogIndex = lastLogIndex;
        this.lastLogTerm = lastLogTerm;
    }

    public long getTerm() {
        return term;
    }

    public String getCandidateId() {
        return candidateId;
    }

    public int getLastLogIndex() {
        return lastLogIndex;
    }

    public long getLastLogTerm() {
        return lastLogTerm;
    }
}






