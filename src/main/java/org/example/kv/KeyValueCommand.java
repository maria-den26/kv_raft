package org.example.kv;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

// Команды (PUT/DELETE/GET)
public final class KeyValueCommand {
    public enum Type {
        PUT,
        DELETE,
        GET
    }

    private final Type type;
    private final String key;
    private final String value;

    @JsonCreator
    public KeyValueCommand(@JsonProperty("type") Type type,
                           @JsonProperty("key") String key,
                           @JsonProperty("value") String value) {
        this.type = type;
        this.key = key;
        this.value = value;
    }

    public Type getType() {
        return type;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }
}


