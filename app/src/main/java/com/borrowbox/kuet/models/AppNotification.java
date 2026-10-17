package com.borrowbox.kuet.models;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class AppNotification implements Serializable {
    private String id;
    private String recipientId;
    private String title;
    private String message;
    private String type;
    private String relatedId;
    private long timestamp;
    private boolean isRead;

    public AppNotification() {
        this.timestamp = System.currentTimeMillis();
        this.isRead = false;
    }

    public AppNotification(String id, String recipientId, String title, String message, String type, String relatedId) {
        this.id = id;
        this.recipientId = recipientId;
        this.title = title;
        this.message = message;
        this.type = type;
        this.relatedId = relatedId;
        this.timestamp = System.currentTimeMillis();
        this.isRead = false;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getRecipientId() { return recipientId; }
    public void setRecipientId(String recipientId) { this.recipientId = recipientId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getRelatedId() { return relatedId; }
    public void setRelatedId(String relatedId) { this.relatedId = relatedId; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { isRead = read; }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("recipientId", recipientId);
        map.put("title", title);
        map.put("message", message);
        map.put("type", type);
        map.put("relatedId", relatedId);
        map.put("timestamp", timestamp);
        map.put("isRead", isRead);
        return map;
    }
}
