package org.example.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.example.kv.KeyValueCommand;
import org.example.raft.NotLeaderException;
import org.example.raft.RaftNode;
import org.example.raft.protocol.AppendEntriesRequest;
import org.example.raft.protocol.AppendEntriesResponse;
import org.example.raft.protocol.RequestVoteRequest;
import org.example.raft.protocol.RequestVoteResponse;
import org.example.raft.util.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * HTTP-сервер, предоставляющий доступ к RPC и KV endpoints.
 */
public final class RaftHttpServer implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(RaftHttpServer.class);
    private static final long CLIENT_TIMEOUT_MILLIS = 5_000;

    private final Server server;
    private final RaftNode node;
    private final ObjectMapper mapper = Json.mapper();

    public RaftHttpServer(int port, RaftNode node) {
        this.node = node;
        this.server = new Server(port);
        server.setHandler(new RaftHandler());
    }

    public void start() throws Exception {
        server.start();
        LOGGER.info("HTTP server listening on {}", server.getURI());
    }

    @Override
    public void close() throws IOException {
        try {
            server.stop();
        } catch (Exception e) {
            throw new IOException("Failed to stop server", e);
        }
    }

    // Обработчик HTTP-запросов, маршрутизирующий их по путям
    private class RaftHandler extends AbstractHandler {
        @Override
        public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException {
            try {
                switch (target) {
                    // запрос на репликацию лога
                    case "/raft/append": {
                        AppendEntriesRequest req = readJson(request, AppendEntriesRequest.class);
                        AppendEntriesResponse resp = node.handleAppendEntries(req);
                        writeJson(response, 200, resp);
                        break;
                    }
                    // запрос на выбор нового лидера путем голосования за кандидата
                    case "/raft/vote": {
                        RequestVoteRequest req = readJson(request, RequestVoteRequest.class);
                        RequestVoteResponse resp = node.handleRequestVote(req);
                        writeJson(response, 200, resp);
                        break;
                    }
                    // запрос на запись значения
                    case "/kv/put": {
                        KeyValueCommand payload = readJson(request, KeyValueCommand.class);
                        KeyValueCommand command = new KeyValueCommand(KeyValueCommand.Type.PUT, payload.getKey(), payload.getValue());
                        handleWrite(response, command);
                        break;
                    }
                    // запрос на удаление значения
                    case "/kv/delete": {
                        KeyValueCommand payload = readJson(request, KeyValueCommand.class);
                        KeyValueCommand command = new KeyValueCommand(KeyValueCommand.Type.DELETE, payload.getKey(), payload.getValue());
                        handleWrite(response, command);
                        break;
                    }
                    // запрос на чтение значения
                    case "/kv/get": {
                        String key = extractKeyFromQuery(request.getQueryString());
                        KeyValueCommand command = new KeyValueCommand(KeyValueCommand.Type.GET, key, null);
                        handleRead(response, command);
                        break;
                    }
                    default:
                        response.setStatus(404);
                        break;
                }
                baseRequest.setHandled(true);
            } catch (Exception e) {
                LOGGER.error("Request handling failed", e);
                response.setStatus(500);
                baseRequest.setHandled(true);
            }
        }
    }

    private void handleWrite(HttpServletResponse response, KeyValueCommand command) throws IOException {
        try {
            CompletableFuture<byte[]> future = node.submitCommand(mapper.writeValueAsBytes(command));
            byte[] result = future.get(CLIENT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            writeBytes(response, 200, result);
        } catch (NotLeaderException nle) {
            Map<String, String> payload = new HashMap<>();
            payload.put("leader", nle.getLeaderHint());
            writeJson(response, 409, payload);
        } catch (Exception e) {
            LOGGER.error("write failed", e);
            writeBytes(response, 500, ("error:" + e.getMessage()).getBytes(StandardCharsets.UTF_8));
        }
    }

    private void handleRead(HttpServletResponse response, KeyValueCommand command) throws IOException {
        try {
            byte[] result = node.readFromStateMachine(mapper.writeValueAsBytes(command));
            writeBytes(response, 200, result);
        } catch (Exception e) {
            writeBytes(response, 500, ("error:" + e.getMessage()).getBytes(StandardCharsets.UTF_8));
        }
    }

    private <T> T readJson(HttpServletRequest request, Class<T> type) throws IOException {
        try (InputStream body = request.getInputStream()) {
            return mapper.readValue(body, type);
        }
    }

    private void writeJson(HttpServletResponse response, int status, Object payload) throws IOException {
        byte[] body = mapper.writeValueAsBytes(payload);
        writeBytes(response, status, body);
    }

    private void writeBytes(HttpServletResponse response, int status, byte[] body) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setContentLength(body.length);
        response.getOutputStream().write(body);
        response.getOutputStream().flush();
    }

    private String extractKeyFromQuery(String query) {
        if (query == null || query.isEmpty()) {
            return "";
        }
        // Извлекаем значение параметра key (ожидаем формат: key=value)
        String keyPrefix = "key=";
        int keyIndex = query.indexOf(keyPrefix);
        if (keyIndex == -1) {
            return "";
        }
        String encodedValue = query.substring(keyIndex + keyPrefix.length());
        return URLDecoder.decode(encodedValue, StandardCharsets.UTF_8);
    }

}


