package org.example.raft.cluster;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;


public final class ClusterConfig {
    private final String localId;
    private final PeerEndpoint localEndpoint;
    private final Map<String, PeerEndpoint> peers;

    public ClusterConfig(String localId, PeerEndpoint localEndpoint, Collection<PeerEndpoint> peers) {
        this.localId = Objects.requireNonNull(localId, "localId");
        this.localEndpoint = Objects.requireNonNull(localEndpoint, "localEndpoint");
        this.peers = peers.stream()
                .filter(peer -> !peer.getId().equals(localId))
                .collect(Collectors.toUnmodifiableMap(PeerEndpoint::getId, peer -> peer));
    }

    public String getLocalId() {
        return localId;
    }

    public PeerEndpoint getLocalEndpoint() {
        return localEndpoint;
    }

    public Map<String, PeerEndpoint> getPeers() {
        return peers;
    }

    public int majority() {
        return (peers.size() + 1) / 2 + 1;
    }
}







