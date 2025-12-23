package org.example.raft.transport;

import java.util.concurrent.CompletableFuture;

import org.example.raft.protocol.AppendEntriesRequest;
import org.example.raft.protocol.AppendEntriesResponse;
import org.example.raft.protocol.RequestVoteRequest;
import org.example.raft.protocol.RequestVoteResponse;


public interface RaftTransport {
    CompletableFuture<AppendEntriesResponse> appendEntries(String targetNodeId, AppendEntriesRequest request);

    CompletableFuture<RequestVoteResponse> requestVote(String targetNodeId, RequestVoteRequest request);
}




