package org.example;

import org.example.kv.KeyValueStateMachine;
import org.example.raft.RaftNode;
import org.example.raft.cluster.ClusterConfig;
import org.example.raft.cluster.PeerEndpoint;
import org.example.raft.transport.HttpRaftTransport;
import org.example.server.RaftHttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Entry point that wires Raft node, transport and HTTP server.
 */
public final class Main {
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        Map<String, String> params = parseArgs(args);
        String nodeId = params.getOrDefault("id", "node1");
        String host = params.getOrDefault("host", "127.0.0.1");
        int port = Integer.parseInt(params.getOrDefault("port", "9001"));
        List<PeerEndpoint> peers = parsePeers(params.getOrDefault("peers", ""));

        PeerEndpoint local = new PeerEndpoint(nodeId, host, port);
        ClusterConfig config = new ClusterConfig(nodeId, local, peers);
        KeyValueStateMachine stateMachine = new KeyValueStateMachine();
        HttpRaftTransport transport = new HttpRaftTransport(config.getPeers(), Duration.ofSeconds(2));
        RaftNode node = new RaftNode(config, transport, stateMachine);
        RaftHttpServer server = new RaftHttpServer(port, node);

        node.start();
        server.start();
        LOGGER.info("Node {} ready", nodeId);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                server.close();
                node.close();
                transport.close();
            } catch (Exception e) {
                LOGGER.error("shutdown failed", e);
            }
        }));
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> params = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("--") && i + 1 < args.length) {
                params.put(args[i].substring(2), args[i + 1]);
                i++;
            }
        }
        return params;
    }

    private static List<PeerEndpoint> parsePeers(String value) {
        List<PeerEndpoint> peers = new ArrayList<>();
        if (value == null || value.isEmpty()) {
            return peers;
        }
        String[] peerItems = value.split(",");
        for (String item : peerItems) {
            String[] parts = item.split("=");
            if (parts.length != 2) {
                continue;
            }
            String id = parts[0];
            String[] hostPort = parts[1].split(":");
            if (hostPort.length != 2) {
                continue;
            }
            peers.add(new PeerEndpoint(id, hostPort[0], Integer.parseInt(hostPort[1])));
        }
        return peers;
    }
}

