package com.example.news.node.client;

import java.io.*;
import java.net.*;
import java.util.*;

public class ClientMain {

    private static String clientAddress;

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java ClientMain <nodeIp> <nodePort>");
            return;
        }

        String host = args[0];
        int port = Integer.parseInt(args[1]);

        clientAddress = getLocalAddress() + ":" + (5000 + new Random().nextInt(1000));

        // Start a thread to listen for notifications
        Thread listenerThread = new Thread(() -> listenForNotifications(clientAddress));
        listenerThread.setDaemon(true);
        listenerThread.start();

        // Start interaction with the node
        interactWithNode(host, port);
    }

    private static void interactWithNode(String host, int port) {
        try (Socket socket = new Socket(host, port);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader userInput = new BufferedReader(new InputStreamReader(System.in))) {

            System.out.println("Connected to node " + host + ":" + port);
            System.out.println("Type commands: 'subscribe <topic>', 'publish <topic>|<title>|<content>', 'pull <topic>', or 'exit'");

            String command;
            while (true) {
                System.out.print("Cmd> ");
                command = userInput.readLine();
                if ("exit".equalsIgnoreCase(command)) {
                    System.out.println("Exiting...");
                    break;
                }

                if (command.startsWith("subscribe")) {
                    handleSubscribe(command, out, in);
                } else if (command.startsWith("publish")) {
                    handlePublish(command, out, in);
                } else if (command.startsWith("pull")) {
                    handlePull(command, out, in);
                } else {
                    System.out.println("Unknown command. Use 'subscribe', 'publish', or 'pull'.");
                }
            }
        } catch (IOException e) {
            System.err.println("Error connecting to node: " + e.getMessage());
        }
    }



    private static void handlePublish(String cmd, PrintWriter out, BufferedReader in) {
        try {
            out.println(cmd);
            String response = in.readLine();
            if (response == null) {
                System.err.println("Server closed the connection unexpectedly.");
            } else {
                System.out.println("Server: " + response);
            }
        } catch (IOException e) {
            System.err.println("Error reading server response: " + e.getMessage());
        }
    }
    private static void handleSubscribe(String cmd, PrintWriter out, BufferedReader in) {
        String topic = cmd.substring("subscribe".length()).trim();

        if (topic.isEmpty()) {
            System.out.println("ERROR: Please specify a topic to subscribe.");
            return;
        }

        out.println("subscribe " + topic);

        try {
            String response = in.readLine();
            System.out.println("Server: " + response);
        } catch (IOException e) {
            System.err.println("Error reading server response for subscribe: " + e.getMessage());
        }
    }


    private static void handlePull(String cmd, PrintWriter out, BufferedReader in) {
        out.println(cmd);
        try {
            String response;
            while (!(response = in.readLine()).equals("END_PULL")) {
                System.out.println(response);
            }
        } catch (IOException e) {
            System.err.println("Error reading server response: " + e.getMessage());
        }
    }
    private static void listenForNotifications(String clientAddress) {
        String[] parts = clientAddress.split(":");
        int port = Integer.parseInt(parts[1]);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Listening for notifications on " + clientAddress + "...");

            while (true) {
                try (Socket clientSocket = serverSocket.accept();
                     BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))) {
                    String notification = in.readLine();
                    if (notification != null && notification.startsWith("NOTIFY")) {
                        System.out.println("Notification: " + notification.substring("NOTIFY".length()).trim());
                    }
                } catch (IOException e) {
                    System.err.println("[Notification Listener] Error receiving notification: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("[Notification Listener] Error setting up listener: " + e.getMessage());
        }
    }

    // Metodă pentru setarea adresei corecte a clientului
    private static void setClientAddress(String clientAddress) {
        System.out.println("Client address set to: " + clientAddress);
    }




    private static String getLocalAddress() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            return "127.0.0.1";
        }
    }
}
