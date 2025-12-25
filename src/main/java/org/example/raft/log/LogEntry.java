package org.example.raft.log;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Реплицируемая команда
 */
public final class LogEntry {
    private final long term;
    private final int index;
    private final byte[] command;

    @JsonCreator
    public LogEntry(@JsonProperty("term") long term,
                    @JsonProperty("index") int index,
                    @JsonProperty("command") byte[] command) {
        this.term = term;
        this.index = index;
        this.command = command;
    }

    public long getTerm() {
        return term;
    }

    public int getIndex() {
        return index;
    }

    public byte[] getCommand() {
        return command;
    }
}







