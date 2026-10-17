package com.borrowbox.kuet.models;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class Rental implements Serializable {
    private String id;
    private String requestId;
    private String productId;
    private String productName;
    private String productEmoji;
    private String ownerId;
    private String renterId;
    private long startDateMillis;
    private long returnDateMillis;
    private double totalCost;
    private String status; // "Active", "Returned", "Overdue"
    private long confirmedAt;
    private long returnedAt;

    public Rental() {
        this.status = "Active";
        this.confirmedAt = System.currentTimeMillis();
    }

    public Rental(String id, String requestId, String productId, String productName,
                  String productEmoji, String ownerId, String renterId,
                  long startDateMillis, long returnDateMillis, double totalCost) {
        this.id = id;
        this.requestId = requestId;
        this.productId = productId;
        this.productName = productName;
        this.productEmoji = productEmoji != null ? productEmoji : "📦";
        this.ownerId = ownerId;
        this.renterId = renterId;
        this.startDateMillis = startDateMillis;
        this.returnDateMillis = returnDateMillis;
        this.totalCost = totalCost;
        this.status = "Active";
        this.confirmedAt = System.currentTimeMillis();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public String getProductEmoji() { return productEmoji != null ? productEmoji : "📦"; }
    public void setProductEmoji(String productEmoji) { this.productEmoji = productEmoji; }

    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }

    public String getRenterId() { return renterId; }
    public void setRenterId(String renterId) { this.renterId = renterId; }

    public long getStartDateMillis() { return startDateMillis; }
    public void setStartDateMillis(long startDateMillis) { this.startDateMillis = startDateMillis; }

    public long getReturnDateMillis() { return returnDateMillis; }
    public void setReturnDateMillis(long returnDateMillis) { this.returnDateMillis = returnDateMillis; }

    public double getTotalCost() { return totalCost; }
    public void setTotalCost(double totalCost) { this.totalCost = totalCost; }

    public String getStatus() {
        if ("Active".equalsIgnoreCase(status) && System.currentTimeMillis() > returnDateMillis) {
            return "Overdue";
        }
        return status != null ? status : "Active";
    }
    public void setStatus(String status) { this.status = status; }

    public long getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(long confirmedAt) { this.confirmedAt = confirmedAt; }

    public long getReturnedAt() { return returnedAt; }
    public void setReturnedAt(long returnedAt) { this.returnedAt = returnedAt; }

    public String getFormattedReturnDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        return sdf.format(new Date(returnDateMillis));
    }

    public String getRemainingTimeText() {
        long now = System.currentTimeMillis();
        if (now > returnDateMillis) {
            long overdue = now - returnDateMillis;
            long days = overdue / (1000 * 60 * 60 * 24);
            return "Overdue by " + (days == 0 ? "hours" : days + " day(s)");
        }
        long diff = returnDateMillis - now;
        long days = diff / (1000 * 60 * 60 * 24);
        long hours = (diff % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60);
        return days + "d " + hours + "h remaining";
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("requestId", requestId);
        map.put("productId", productId);
        map.put("productName", productName);
        map.put("ownerId", ownerId);
        map.put("renterId", renterId);
        map.put("startDateMillis", startDateMillis);
        map.put("returnDateMillis", returnDateMillis);
        map.put("totalCost", totalCost);
        map.put("status", getStatus());
        map.put("confirmedAt", confirmedAt);
        map.put("returnedAt", returnedAt);
        return map;
    }
}
