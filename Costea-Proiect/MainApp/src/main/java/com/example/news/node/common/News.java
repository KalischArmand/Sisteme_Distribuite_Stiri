package com.example.news.node.common;

import java.io.Serializable;
import java.util.UUID;

/**
 * Model de știre, conține ID (unic), topic, titlu, conținut și timestamp.
 */
public class News implements Serializable {
    private String id;
    private String topic;
    private String title;
    private String content;
    private long timestamp; // momentul publicării

    // Constructor care generează ID automat
    public News(String topic, String title, String content) {
        this.id = UUID.randomUUID().toString();
        this.topic = topic;
        this.title = title;
        this.content = content;
        this.timestamp = System.currentTimeMillis();
    }

    // Constructor cu ID - folosit la replicare dacă ID-ul vine deja
    public News(String id, String topic, String title, String content) {
        this.id = id; // ID-ul este reutilizat pentru replicare
        this.topic = topic;
        this.title = title;
        this.content = content;
        this.timestamp = System.currentTimeMillis();
    }

    // Getter pentru toate câmpurile
    public String getId() { return id; }
    public String getTopic() { return topic; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public long getTimestamp() { return timestamp; }

    // Metodă pentru afișare simplificată (pentru comanda `pull`)
    public String toSimpleString() {
        return "[" + topic + "] " + title + " - " + content;
    }

    // Metodă pentru debug (detalii complete)
    @Override
    public String toString() {
        return "[" + topic + "] " + title + " - " + content
                + " (time=" + timestamp + ", id=" + id + ")";
    }
}
