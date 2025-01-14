package com.example.news.node.heartbeat;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Map;
public class HeartBeat implements Runnable {
    private final int selfId;
    private final Map<Integer, String> ringNodes;
    private final Map<Integer, Boolean> nodeStatus;

    public HeartBeat(int selfId, Map<Integer, String> ringNodes, Map<Integer, Boolean> nodeStatus) {
        this.selfId = selfId;
        this.ringNodes = ringNodes;
        this.nodeStatus = nodeStatus;
    }

    @Override
    public void run() {
        while (true) {
            for (Map.Entry<Integer, String> entry : ringNodes.entrySet()) {
                int nodeId = entry.getKey();
                if (nodeId == selfId) continue; // Sărim propriul nod

                String address = entry.getValue();
                try {
                    String[] parts = address.split(":");
                    String ip = parts[0];
                    int port = Integer.parseInt(parts[1]);

                    try (Socket socket = new Socket()) {
                        socket.connect(new InetSocketAddress(ip, port), 1000); // Timeout de 1 secundă
                        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                        out.println("heartbeat " + selfId);
                    }

                    if (!nodeStatus.getOrDefault(nodeId, true)) {
                        System.out.println("[HeartBeat] Node " + nodeId + " is UP.");
                    }
                    nodeStatus.put(nodeId, true); // Marcăm nodul ca activ
                } catch (IOException e) {
                    if (nodeStatus.getOrDefault(nodeId, true)) {
                        System.out.println("[HeartBeat] Node " + nodeId + " might be DOWN!");
                    }
                    nodeStatus.put(nodeId, false); // Marcăm nodul ca inactiv
                }
            }

            try {
                Thread.sleep(3000); // Interval de 3 secunde
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}
