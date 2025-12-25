package org.example.raft;


public interface StateMachine {

    /**
     * Применяет сериализованную команду к state machine.
     *
     * @param command serialized command payload
     * @return serialized response
     */
    byte[] apply(byte[] command);
}







