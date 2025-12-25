package org.example.raft.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;


public final class Json {
    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .findAndAddModules()
            .build();

    private Json() {
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }
}






