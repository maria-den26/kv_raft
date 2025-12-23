package org.example.kv;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

// Результаты операций
public final class KeyValueResult {
    private final boolean success;
    private final String value;
    private final String message;

    @JsonCreator
    public KeyValueResult(@JsonProperty("success") boolean success,
                          @JsonProperty("value") String value,
                          @JsonProperty("message") String message) {
        this.success = success;
        this.value = value;
        this.message = message;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getValue() {
        return value;
    }

    public String getMessage() {
        return message;
    }
}


