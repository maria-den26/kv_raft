package org.example.raft.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class AppendEntriesResponse {
    private final long term;
    private final boolean success;
    private final int matchIndex;

    @JsonCreator
    public AppendEntriesResponse(@JsonProperty("term") long term,
                                 @JsonProperty("success") boolean success,
                                 @JsonProperty("matchIndex") int matchIndex) {
        this.term = term;
        this.success = success;
        this.matchIndex = matchIndex;
    }

    public long getTerm() {
        return term;
    }

    public boolean isSuccess() {
        return success;
    }

    public int getMatchIndex() {
        return matchIndex;
    }
}






