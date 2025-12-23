package org.example.kv;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class KeyValueStateMachineTest {

    @Test
    void putAndGet() {
        KeyValueStateMachine machine = new KeyValueStateMachine();
        KeyValueCommand put = new KeyValueCommand(KeyValueCommand.Type.PUT, "a", "1");
        byte[] putResult = machine.apply(JsonSupport.toBytes(put));
        KeyValueCommand get = new KeyValueCommand(KeyValueCommand.Type.GET, "a", null);
        byte[] getResult = machine.apply(JsonSupport.toBytes(get));
        assertEquals(new String(putResult), new String(getResult));
    }

    private static final class JsonSupport {
        private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER = org.example.raft.util.Json.mapper();

        static byte[] toBytes(Object obj) {
            try {
                return MAPPER.writeValueAsBytes(obj);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}


