package com.example.news.node.bully;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Descrie procesele / nodurile care participă la algoritmul Bully.
 */
public class NodeProcess {
    private int id;
    private volatile int coordinator = -1; // ID-ul coordonatorului actual (sau -1 dacă nu există)
    private Map<Integer, String> nodes;    // id -> ip:port
    private int localPort;                // port pe care acest nod ascultă pt. mesaje de bully

    public NodeProcess(int id, Map<Integer, String> nodes, int localPort) {
        this.id = id;
        this.nodes = nodes;
        this.localPort = localPort;
    }

    // ===== GETTERS / SETTERS =====
    public int getId() {
        return id;
    }
    public int getCoordinator() {
        return coordinator;
    }
    public void setCoordinator(int coord) {
        this.coordinator = coord;
    }

    /**
     * Returnează lista ID-urilor nodurilor care au ID mai mare decât mine și (opțional) sunt active.
     */
    public List<Integer> getNodesWithIdHigher() {
        return nodes.keySet()
                .stream()
                .filter(other -> other > id)
                .collect(Collectors.toList());
    }

    /**
     * Trimite un mesaj "ELECTION" la nodul cu ID-ul dat.
     * @return true dacă am putut trimite, false dacă nodul e down.
     */
    public boolean sendElectionMessageTo(int otherId) {
        String address = nodes.get(otherId);
        if(address == null) return false;
        // ex: "192.168.0.102:6000"
        String[] parts = address.split(":");
        String ip = parts[0];
        int port = Integer.parseInt(parts[1]);
        try(Socket s = new Socket(ip, port);
            PrintWriter out = new PrintWriter(s.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()))) {
            out.println("ELECTION " + id);
            // așteptăm "OK"?
            String resp = in.readLine();
            if(resp != null && resp.startsWith("OK")) {
                System.out.println("[NodeProcess] Node " + otherId + " responded OK");
            }
            return true;
        } catch(IOException e) {
            System.out.println("[NodeProcess] Node " + otherId + " is down or not reachable.");
            return false;
        }
    }

    /**
     * Trimite un mesaj COORDINATOR tuturor nodurilor (cu ID != mine).
     */
    public void announceCoordinatorToAll() {
        for(int otherId : nodes.keySet()) {
            if(otherId == id) continue;
            sendCoordinatorMessageTo(otherId);
        }
    }

    private void sendCoordinatorMessageTo(int otherId) {
        String address = nodes.get(otherId);
        if(address == null) return;
        String[] parts = address.split(":");
        String ip = parts[0];
        int port = Integer.parseInt(parts[1]);
        try(Socket s = new Socket(ip, port);
            PrintWriter out = new PrintWriter(s.getOutputStream(), true)) {
            out.println("COORDINATOR " + id);
        } catch(IOException e) {
            System.out.println("[NodeProcess] Could not send COORDINATOR msg to Node " + otherId);
        }
    }

    /**
     * Metodă care pornește un ServerSocket pt. a asculta mesaje (ELECTION, COORDINATOR etc.)
     */
    public void startBullyListener() {
        new Thread(() -> {
            try(ServerSocket serverSocket = new ServerSocket(localPort)) {
                System.out.println("[NodeProcess] Node " + id + " Bully listener started on port " + localPort);
                while(true) {
                    Socket client = serverSocket.accept();
                    new Thread(() -> handleBullyMessage(client)).start();
                }
            } catch(IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    /**
     * Tratarea mesajelor: ELECTION, COORDINATOR.
     */
    private void handleBullyMessage(Socket client) {
        try (
                BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                PrintWriter out = new PrintWriter(client.getOutputStream(), true)
        ) {
            String line = in.readLine();
            if(line == null) return;

            if(line.startsWith("ELECTION")) {
                // "ELECTION <senderId>"
                String[] arr = line.split(" ");
                int senderId = Integer.parseInt(arr[1]);
                System.out.println("[NodeProcess] Node " + id + " => Received ELECTION from " + senderId);
                // 1. Trimit "OK" înapoi la sender
                out.println("OK from node " + id);
                // 2. Pornesc propria alegere => BullyAlgorithm.startElection(this)
                //    ... DAR, conform Bully, de obicei așteptăm puțin,
                //    însă standard e să pornim și noi ELECTION.
                new Thread(() -> {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    BullyAlgorithm.startElection(this);
                }).start();
            }
            else if(line.startsWith("COORDINATOR")) {
                // "COORDINATOR <senderId>"
                String[] arr = line.split(" ");
                int coordId = Integer.parseInt(arr[1]);
                System.out.println("[NodeProcess] Node " + id + " => Received COORDINATOR from " + coordId);
                setCoordinator(coordId);
            }
            else {
                // alt tip de mesaj
                System.out.println("[NodeProcess] Node " + id + " => Unknown Bully msg: " + line);
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
}
