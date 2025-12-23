package org.example.raft.transport;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.raft.cluster.PeerEndpoint;
import org.example.raft.protocol.AppendEntriesRequest;
import org.example.raft.protocol.AppendEntriesResponse;
import org.example.raft.protocol.RequestVoteRequest;
import org.example.raft.protocol.RequestVoteResponse;
import org.example.raft.util.Json;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;


public final class HttpRaftTransport implements RaftTransport, Closeable {

    private final ObjectMapper mapper; // общий JSON‑сериализатор/десериализатор для всех запросов и ответов
    private final Map<String, URI> appendUris; // "таблица маршрутизации" для RPC appendEntries
    private final Map<String, URI> voteUris; // "таблица маршрутизации" для RPC requestVote
    private final int timeoutMillis;

    public HttpRaftTransport(Map<String, PeerEndpoint> peers, Duration requestTimeout) {
        this.mapper = Json.mapper();
        this.timeoutMillis = Math.toIntExact(Math.min(Integer.MAX_VALUE, Math.max(1, requestTimeout.toMillis())));
        this.appendUris = java.util.Collections.unmodifiableMap(
                peers.entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey,
                                e -> URI.create(e.getValue().asHttpBase() + "/raft/append"))));
        this.voteUris = java.util.Collections.unmodifiableMap(
                peers.entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey,
                                e -> URI.create(e.getValue().asHttpBase() + "/raft/vote"))));
    }

    @Override
    public CompletableFuture<AppendEntriesResponse> appendEntries(String targetNodeId, AppendEntriesRequest request) {
        return send(targetNodeId, request, appendUris, new TypeReference<AppendEntriesResponse>(){});
    }

    @Override
    public CompletableFuture<RequestVoteResponse> requestVote(String targetNodeId, RequestVoteRequest request) {
        return send(targetNodeId, request, voteUris, new TypeReference<RequestVoteResponse>(){});
    }

    private <T> CompletableFuture<T> send(String targetNodeId,
                                          Object body,
                                          Map<String, URI> endpoints,
                                          TypeReference<T> responseType) {
        URI uri = endpoints.get(targetNodeId);
        if (uri == null) {
            CompletableFuture<T> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IllegalArgumentException("Unknown peer " + targetNodeId));
            return failed;
        }
        byte[] payload;
        try {
            payload = mapper.writeValueAsBytes(body);
        } catch (IOException e) {
            CompletableFuture<T> failed = new CompletableFuture<>();
            failed.completeExceptionally(e);
            return failed;
        }

        return CompletableFuture.supplyAsync(() -> execute(uri, payload, responseType, targetNodeId));
    }

    private <T> T execute(URI uri, byte[] payload, TypeReference<T> responseType, String targetNodeId) {
        HttpURLConnection connection = null;
        try {
            URL url = uri.toURL();
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(timeoutMillis);
            connection.setReadTimeout(timeoutMillis);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            try (OutputStream output = connection.getOutputStream()) {
                output.write(payload);
            }
            int status = connection.getResponseCode();
            InputStream responseStream = status >= 200 && status < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            byte[] responseBody = readAll(responseStream);
            if (status >= 200 && status < 300) {
                return mapper.readValue(responseBody, responseType);
            }
            throw new IllegalStateException("HTTP " + status + " from " + targetNodeId);
        } catch (IOException e) {
            throw new RuntimeException("Transport to " + targetNodeId + " failed", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static byte[] readAll(InputStream stream) throws IOException {
        if (stream == null) {
            return new byte[0];
        }
        try (InputStream input = stream;
             java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream()) {
            byte[] data = new byte[4096];
            int read;
            while ((read = input.read(data)) != -1) {
                buffer.write(data, 0, read);
            }
            return buffer.toByteArray();
        }
    }

    @Override
    public void close() {}
}

