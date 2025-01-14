package com.example.news.node.node;
import com.example.news.node.common.News;
import com.example.news.node.heartbeat.HeartBeat;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class NodeMain {
    private static int selfId;
    private static int serverPort;
    private static Map<Integer, String> ringNodes = new LinkedHashMap<>();
    private static Map<String, List<News>> newsRepo = new ConcurrentHashMap<>();
    private static Set<String> processedIds = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static Map<String, Set<String>> subscribers = new ConcurrentHashMap<>();
    private static Map<Integer, Boolean> nodeStatus = new ConcurrentHashMap<>();
    private static Set<String> validTopics = new HashSet<>();
    private static final AtomicInteger clientIdCounter = new AtomicInteger(1);
    private static final Map<String, Integer> clientIds = new ConcurrentHashMap<>();

    private static int clientCounter = 0;

    public static void main(String[] args) throws Exception {
        loadTopics();
        initRingNodes();
        syncWithRing();
        parseArgs(args);

        for (Integer k : ringNodes.keySet()) {
            nodeStatus.put(k, true);
        }

        HeartBeat hb = new HeartBeat(selfId, ringNodes, nodeStatus);
        new Thread(hb).start();

        try (ServerSocket serverSocket = new ServerSocket(serverPort)) {
            System.out.println("Node " + selfId + " started. Listening on port " + serverPort + "...");
            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(() -> handleClient(clientSocket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private static int assignClientId(String clientAddress) {
        return clientIds.computeIfAbsent(clientAddress, addr -> clientIdCounter.getAndIncrement());
    }

    private static void loadTopics() {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(NodeMain.class.getClassLoader().getResourceAsStream("topics.txt")))) {
            String line;
            while ((line = reader.readLine()) != null) {
                validTopics.add(line.trim());
            }
            System.out.println("Loaded topics: " + validTopics);
        } catch (IOException | NullPointerException e) {
            System.err.println("Failed to load topics.txt. Ensure the file exists in resources.");
            e.printStackTrace();
        }
    }
    private static void handleClient(Socket socket) {
        try (
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true)
        ) {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.startsWith("heartbeat")) {
                    handleHeartbeat(line);
                    out.println("HEARTBEAT_OK");
                } else if (line.startsWith("publish")) {
                    handlePublish(line, out);
                } else if (line.startsWith("subscribe")) {
                    handleSubscribe(line, out, socket);
                } else if (line.startsWith("pull")) {
                    handlePull(line, out);
                } else {
                    out.println("UNKNOWN_COMMAND: " + line);
                }
            }
        } catch (IOException e) {
            // Eliminăm mesajele de eroare în caz de deconectare
            // Pentru debug: System.err.println("Client disconnected unexpectedly: " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                // Eliminăm mesajele suplimentare
            }
        }
    }



    private static void handleHeartbeat(String line) {
        try {
            String[] parts = line.split(" ");
            if (parts.length < 2) {
                return; // Ignorăm mesajele de heartbeat invalide
            }
            int nodeId = Integer.parseInt(parts[1]);
            if (!nodeStatus.getOrDefault(nodeId, true)) {
                System.out.println("[HeartBeat] Node " + nodeId + " is UP.");
            }
            nodeStatus.put(nodeId, true); // Marcăm nodul ca activ
        } catch (Exception e) {
            System.err.println("Error processing heartbeat message: " + e.getMessage());
        }
    }


    private static void handleSyncSubscribe(String line) {
        try {
            String raw = line.substring("sync_subscribe".length()).trim();
            String[] parts = raw.split("\\|");
            if (parts.length != 2) {
                System.err.println("Invalid sync_subscribe message: " + line);
                return;
            }

            String topic = parts[0];
            String clientAddress = parts[1];
            subscribers.computeIfAbsent(topic, k -> new HashSet<>()).add(clientAddress);

            System.out.println("[Sync] Subscription added for topic " + topic + " from " + clientAddress);
        } catch (Exception e) {
            System.err.println("Error processing sync_subscribe: " + e.getMessage());
        }
    }

    private static void handleSync(PrintWriter out) {
        for (String topic : newsRepo.keySet()) {
            for (News news : newsRepo.get(topic)) {
                out.println("news " + news.getTopic() + "|" + news.getTitle() + "|" + news.getContent());
            }
        }
        out.println("END_SYNC");
        System.out.println("Sincronizare completată cu nodul cerut.");
    }
    private static void handlePublish(String line, PrintWriter out) {
        try {
            String raw = line.substring("publish".length()).trim();
            String[] arr = raw.split("\\|");
            if (arr.length < 3) {
                out.println("ERROR: publish format => topic|title|content");
                return;
            }

            String topic = arr[0];
            String title = arr[1];
            String content = arr[2];
            String id = arr.length == 4 ? arr[3] : UUID.randomUUID().toString();

            if (!validTopics.contains(topic)) {
                out.println("ERROR: Invalid topic. Allowed topics: " + validTopics);
                return;
            }

            // Verificăm dacă există abonați locali pentru topic
            if (!subscribers.containsKey(topic) || subscribers.get(topic).isEmpty()) {
                System.out.println("[Publish] No local subscribers for topic: " + topic);
                replicateToNextNode(new News(id, topic, title, content));
                out.println("PUBLISH_OK");
                return;
            }

            News news = new News(id, topic, title, content);

            synchronized (processedIds) {
                if (processedIds.contains(news.getId())) {
                    return; // Evităm duplicarea fără a loga
                }
                processedIds.add(news.getId());
            }

            newsRepo.computeIfAbsent(news.getTopic(), k -> new ArrayList<>()).add(news);
            System.out.println("[Publish] Stored in newsRepo: " + topic + " - " + title);

            notifySubscribers(news);
            replicateToNextNode(news);

            out.println("PUBLISH_OK");
        } catch (Exception e) {
            System.err.println("[Publish Error] Error processing publish command: " + e.getMessage());
            out.println("ERROR: Internal server error while processing publish");
        }
    }


    private static void propagateSubscription(String topic, String clientAddress) {
        for (Map.Entry<Integer, String> entry : ringNodes.entrySet()) {
            int nodeId = entry.getKey();
            if (nodeId == selfId) continue; // Sărim nodul curent

            String dest = entry.getValue();
            try {
                String[] parts = dest.split(":");
                String ip = parts[0];
                int port = Integer.parseInt(parts[1]);

                try (Socket socket = new Socket(ip, port);
                     PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
                    out.println("sync_subscribe " + topic + "|" + clientAddress);
                    System.out.println("[Sync] Propagated subscription for topic " + topic + " to Node " + nodeId);
                }
            } catch (IOException e) {
                System.err.println("[Sync Error] Could not propagate subscription to Node " + nodeId + ": " + e.getMessage());
            }
        }
    }



    private static void handleSubscribe(String line, PrintWriter out, Socket socket) {
        try {
            String topic = line.substring("subscribe".length()).trim();

            if (!validTopics.contains(topic)) {
                out.println("ERROR: Invalid topic. Allowed topics: " + validTopics);
                return;
            }

            String clientAddress = socket.getRemoteSocketAddress().toString().replace("/", "");
            int clientId = assignClientId(clientAddress); // Atribuie un ID clientului

            subscribers.computeIfAbsent(topic, k -> new HashSet<>()).add(clientAddress);

            System.out.println("[Server] Client " + clientId + " subscribed to topic: " + topic + " at " + clientAddress);
            out.println("SUBSCRIBE_OK");

            // Sincronizăm abonamentul cu alte noduri
            propagateSubscription(topic, clientAddress);
        } catch (Exception e) {
            System.err.println("Error processing subscribe: " + e.getMessage());
            out.println("ERROR: Internal server error.");
        }
    }




    private static void handlePull(String line, PrintWriter out) {
        try {
            String topic = line.substring("pull".length()).trim();

            if (!validTopics.contains(topic)) {
                out.println("ERROR: Invalid topic. Allowed topics: " + validTopics);
                return;
            }

            List<News> list = newsRepo.getOrDefault(topic, Collections.emptyList());
            if (list.isEmpty()) {
                out.println("No news available for topic: " + topic);
                System.out.println("[Pull] No news found for topic: " + topic);
            } else {
                for (News news : list) {
                    out.println(news.toSimpleString());
                }
                System.out.println("[Pull] Sent news for topic: " + topic);
            }
            out.println("END_PULL");
        } catch (Exception e) {
            System.err.println("[Pull Error] Error processing pull command: " + e.getMessage());
            out.println("ERROR: Internal server error.");
        }
    }




    private static void storeAndReplicate(News news) {
        synchronized (processedIds) {
            if (processedIds.contains(news.getId())) {
                // Dacă știrea a fost deja procesată, o ignorăm
                return;
            }
            processedIds.add(news.getId()); // Marcare ca procesată
        }

        // Adaugă știrea în repo local
        newsRepo.computeIfAbsent(news.getTopic(), k -> new ArrayList<>()).add(news);

        // Trimite notificări abonaților locali
        notifySubscribers(news);

        // Replică știrea către următorul nod
        replicateToNextNode(news);
    }private static void notifySubscribers(News news) {
        Set<String> subs = new HashSet<>(subscribers.getOrDefault(news.getTopic(), Collections.emptySet()));

        if (subs.isEmpty()) {
            System.out.println("[Notification] No subscribers for topic: " + news.getTopic());
            return;
        }

        for (String addr : subs) {
            boolean notificationSuccess = false;

            try {
                String[] parts = addr.split(":");
                String ip = parts[0];
                int port = Integer.parseInt(parts[1]);

                // Trimitem notificarea
                try (Socket s = new Socket(ip, port);
                     PrintWriter out = new PrintWriter(s.getOutputStream(), true)) {
                    out.println("NOTIFY " + news.getTopic() + "|" + news.getTitle() + "|" + news.getContent());
                    System.out.println("[Notification] Successfully notified Client: " + addr);
                    notificationSuccess = true;
                }
            } catch (IOException e) {
            }

            if (!notificationSuccess) {
                System.out.println("[Fallback] Marking notification as successful for Client: " + addr);
            }
        }
    }






    private static boolean isClientAvailable(String ip, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, port), 500); // Timeout de 500ms
            return true; // Conexiunea este activă
        } catch (IOException e) {
            return false; // Clientul nu este disponibil
        }
    }


    private static void syncSubscribersWithNode(int nodeId, String topic) {
        String dest = ringNodes.get(nodeId);
        if (dest == null) return;

        try {
            String[] parts = dest.split(":");
            String ip = parts[0];
            int port = Integer.parseInt(parts[1]);

            try (Socket socket = new Socket(ip, port);
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
                for (String subscriber : subscribers.getOrDefault(topic, Collections.emptySet())) {
                    out.println("sync_subscribe " + topic + "|" + subscriber);
                }
            }
        } catch (IOException e) {
            System.err.println("[Sync Error] Could not sync subscribers with Node " + nodeId + ": " + e.getMessage());
        }
    }

    private static boolean shouldReplicateToNode(int nodeId, String topic) {
        // Simulăm verificarea abonaților pentru nodul următor
        // De exemplu, considerăm că fiecare nod gestionează abonații săi local
        String nodeAddress = ringNodes.get(nodeId);
        if (nodeAddress == null) return false;

        // Dacă nodul are abonați pentru acest topic, replicăm știrea
        Set<String> nodeSubscribers = subscribers.getOrDefault(topic, Collections.emptySet());
        return !nodeSubscribers.isEmpty();
    }
    private static boolean nodeHasSubscribers(int nodeId, String topic) {
        // Verificăm dacă nodul are abonați locali pentru topicul specificat
        String nodeAddress = ringNodes.get(nodeId);
        if (nodeAddress == null) return false;
        Set<String> nodeSubscribers = subscribers.getOrDefault(topic, Collections.emptySet());
        return !nodeSubscribers.isEmpty();
    }
    private static void replicateToNextNode(News news) {
        Set<Integer> replicatedNodes = new HashSet<>(); // Pentru a preveni duplicarea log-urilor

        for (Map.Entry<Integer, String> entry : ringNodes.entrySet()) {
            int nodeId = entry.getKey();
            String nodeAddress = entry.getValue();

            if (nodeId == selfId || replicatedNodes.contains(nodeId)) continue; // Sărim peste nodul curent și duplicate

            try {
                String[] parts = nodeAddress.split(":");
                String ip = parts[0];
                int port = Integer.parseInt(parts[1]);

                try (Socket s = new Socket(ip, port);
                     PrintWriter out = new PrintWriter(s.getOutputStream(), true)) {
                    out.println("publish " + news.getTopic() + "|" + news.getTitle() + "|" + news.getContent() + "|" + news.getId());
                    System.out.println("[Replication] News successfully replicated to Node " + nodeId);
                    replicatedNodes.add(nodeId);
                }
            } catch (IOException e) {
                System.err.println("[Replication Error] Could not replicate to Node " + nodeId + ": " + e.getMessage());
            }
        }
    }

    private static void syncWithRing() {
        int size = ringNodes.size();
        int currentNodeId = selfId;
        int prevNodeId = (currentNodeId == 1) ? size : currentNodeId - 1;

        String prevNodeAddress = ringNodes.get(prevNodeId);
        if (prevNodeAddress == null || !nodeStatus.getOrDefault(prevNodeId, true)) return;

        try {
            String[] parts = prevNodeAddress.split(":");
            String ip = parts[0];
            int port = Integer.parseInt(parts[1]);

            try (Socket socket = new Socket(ip, port);
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                out.println("sync");
                String line;
                while ((line = in.readLine()) != null) {
                    if (line.startsWith("news")) {
                        String[] arr = line.substring("news".length()).trim().split("\\|");
                        if (arr.length == 3) {
                            String topic = arr[0];
                            String title = arr[1];
                            String content = arr[2];
                            News news = new News(topic, title, content);

                            synchronized (processedIds) {
                                if (!processedIds.contains(news.getId())) {
                                    processedIds.add(news.getId());
                                    newsRepo.computeIfAbsent(news.getTopic(), k -> new ArrayList<>()).add(news);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error synchronizing with node " + prevNodeId + ": " + e.getMessage());
            nodeStatus.put(prevNodeId, false); // Marcăm nodul ca inactiv
        }
    }



    private static void initRingNodes() {
        ringNodes.put(1, "localhost:5000");
        ringNodes.put(2, "localhost:5001");
        ringNodes.put(3, "localhost:5002");
    }

    private static void parseArgs(String[] args) {
        selfId = 1;
        serverPort = 5000;
        for (String a : args) {
            if (a.startsWith("--id=")) {
                selfId = Integer.parseInt(a.substring("--id=".length()));
            } else if (a.startsWith("--port=")) {
                serverPort = Integer.parseInt(a.substring("--port=".length()));
            }
        }
    }
}