package org.example.raft.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.example.raft.log.LogEntry;

import java.util.List;

public final class AppendEntriesRequest {
    private final long term;
    private final String leaderId;
    private final int prevLogIndex;
    private final long prevLogTerm;
    private final List<LogEntry> entries;
    private final int leaderCommit;

    @JsonCreator
    public AppendEntriesRequest(@JsonProperty("term") long term,
                                @JsonProperty("leaderId") String leaderId,
                                @JsonProperty("prevLogIndex") int prevLogIndex,
                                @JsonProperty("prevLogTerm") long prevLogTerm,
                                @JsonProperty("entries") List<LogEntry> entries,
                                @JsonProperty("leaderCommit") int leaderCommit) {
        this.term = term;
        this.leaderId = leaderId;
        this.prevLogIndex = prevLogIndex;
        this.prevLogTerm = prevLogTerm;
        this.entries = entries;
        this.leaderCommit = leaderCommit;
    }

    public long getTerm() {
        return term;
    }

    public String getLeaderId() {
        return leaderId;
    }

    public int getPrevLogIndex() {
        return prevLogIndex;
    }

    public long getPrevLogTerm() {
        return prevLogTerm;
    }

    public List<LogEntry> getEntries() {
        return entries;
    }

    public int getLeaderCommit() {
        return leaderCommit;
    }
}






