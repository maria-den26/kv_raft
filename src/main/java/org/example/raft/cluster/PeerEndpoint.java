package org.example.raft.cluster;

// Сетевой адрес ноды
public final class PeerEndpoint {
    private final String id;
    private final String host;
    private final int port;

    public PeerEndpoint(String id, String host, int port) {
        this.id = id;
        this.host = host;
        this.port = port;
    }

    public String getId() {
        return id;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String asHttpBase() {
        return "http://" + host + ":" + port;
    }
}






