package com.example.news.node.client;

import java.io.*;
import java.net.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Mini-server local la client. Primește notificări "NOTIFY <topic>|<title>|<content>|<id>"
 * de la nodul la care s-a abonat.
 */
public class ClientNotificationServer implements Runnable {
    private int listenPort;
    private AtomicBoolean running = new AtomicBoolean(true);

    public ClientNotificationServer(int listenPort) {
        this.listenPort = listenPort;
    }

    @Override
    public void run() {
        System.out.println("[ClientNotificationServer] Listening on port " + listenPort);
        try(ServerSocket ss = new ServerSocket(listenPort)) {
            while(running.get()) {
                Socket s = ss.accept();
                new Thread(() -> handleNotify(s)).start();
            }
        } catch(IOException e) {
            e.printStackTrace();
        }
    }

    private void handleNotify(Socket s) {
        try(BufferedReader in = new BufferedReader(
                new InputStreamReader(s.getInputStream()))) {
            String line = in.readLine();
            if(line != null && line.startsWith("NOTIFY ")) {
                String raw = line.substring("NOTIFY ".length());
                // Format: <topic>|<title>|<content>|<id>
                String[] arr = raw.split("\\|");
                if(arr.length>=3) {
                    String topic   = arr[0];
                    String title   = arr[1];
                    String content = arr[2];
                    String id      = (arr.length>3)? arr[3] : "???";
                    System.out.println("** Notification ** : "
                            + "["+topic+"] " + title + " - " + content + " (id="+id+")");
                }
            }
        } catch(IOException ex) {
            ex.printStackTrace();
        }
    }

    public void stopServer() {
        running.set(false);
        // Ca să-l scoți din accept(), poți deschide un socket la acest port
        // ...
    }
}
