package org.example.kv;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.raft.StateMachine;
import org.example.raft.util.Json;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

// Реализация StateMachine для KV
public final class KeyValueStateMachine implements StateMachine {
    private final ConcurrentMap<String, String> store = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = Json.mapper();

    @Override
    public byte[] apply(byte[] command) {
        if (command == null || command.length == 0) {
            return new byte[0];
        }
        try {
            KeyValueCommand request = mapper.readValue(command, KeyValueCommand.class); // Десериализация JSON в команду
            KeyValueResult result; // Результат выполнения команды
            switch (request.getType()) {
                case PUT:
                    Objects.requireNonNull(request.getValue(), "value"); // Проверяем, что кладем не пустое значение
                    store.put(request.getKey(), request.getValue()); // Кладем значение по ключу. Если ключ уже был — значение перезаписывается.
                    result = new KeyValueResult(true, request.getValue(), "PUT applied");
                    break;
                case DELETE:
                    String removed = store.remove(request.getKey()); // Удаляем значение по ключу
                    result = new KeyValueResult(removed != null, removed, removed != null ? "DELETE applied" : "Key missing");
                    break;
                case GET:
                default:
                    String value = store.get(request.getKey()); // Берем значение по ключу
                    result = new KeyValueResult(value != null, value, value != null ? "OK" : "Key missing");
                    break;
            }
            return mapper.writeValueAsBytes(result); // Сериализация результата выполнения команды в JSON
        } catch (Exception e) {
            String message = e.getMessage();
            return ("error:" + (message != null ? message : "null")).getBytes(StandardCharsets.UTF_8);
        }
    }
}


