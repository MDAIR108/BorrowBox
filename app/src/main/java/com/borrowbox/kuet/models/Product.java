package com.borrowbox.kuet.models;

import java.io.Serializable;

public class Product implements Serializable {
    private String id;
    private String name;
    private double pricePerDay;
    private boolean isFree;
    private boolean isAvailable;
    private String category;
    private String department;
    private String hallLocation;
    private String ownerName;
    private String ownerId;
    private String description;
    private String whatsappNumber;
    private String emoji;
    private String imageUrl;
    private int maxRentDays = 7;
    private String model = "";
    private String condition = "Good";
    private java.util.List<String> features = new java.util.ArrayList<>();

    public Product(String id, String name, double pricePerDay, boolean isFree, boolean isAvailable,
                   String category, String department, String hallLocation, String ownerName,
                   String description, String whatsappNumber, String emoji) {
        this(id, name, pricePerDay, isFree, isAvailable, category, department, hallLocation, ownerName, "owner_" + id, description, whatsappNumber, emoji, null);
    }

    public Product(String id, String name, double pricePerDay, boolean isFree, boolean isAvailable,
                   String category, String department, String hallLocation, String ownerName,
                   String ownerId, String description, String whatsappNumber, String emoji) {
        this(id, name, pricePerDay, isFree, isAvailable, category, department, hallLocation, ownerName, ownerId, description, whatsappNumber, emoji, null);
    }

    public Product(String id, String name, double pricePerDay, boolean isFree, boolean isAvailable,
                   String category, String department, String hallLocation, String ownerName,
                   String ownerId, String description, String whatsappNumber, String emoji, String imageUrl) {
        this.id = id;
        this.name = name;
        this.pricePerDay = pricePerDay;
        this.isFree = isFree;
        this.isAvailable = isAvailable;
        this.category = category;
        this.department = department;
        this.hallLocation = hallLocation;
        this.ownerName = ownerName;
        this.ownerId = ownerId != null ? ownerId : "owner_" + id;
        this.description = description;
        this.whatsappNumber = whatsappNumber;
        this.emoji = emoji;
        this.imageUrl = imageUrl;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public double getPricePerDay() { return pricePerDay; }
    public boolean isFree() { return isFree; }
    public boolean isAvailable() { return isAvailable; }
    public String getCategory() { return category; }
    public String getDepartment() { return department; }
    public String getHallLocation() { return hallLocation; }
    public String getOwnerName() { return ownerName; }
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public String getDescription() { return description; }
    public String getWhatsappNumber() { return whatsappNumber; }
    public String getEmoji() { return emoji; }
    public String getImageUrl() { return imageUrl != null ? imageUrl : ""; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public int getMaxRentDays() { return maxRentDays > 0 ? maxRentDays : 7; }
    public void setMaxRentDays(int days) { this.maxRentDays = days; }

    public String getModel() { return model != null ? model : ""; }
    public void setModel(String model) { this.model = model; }

    public String getCondition() { return condition != null ? condition : "Good"; }
    public void setCondition(String condition) { this.condition = condition; }

    public java.util.List<String> getFeatures() { return features != null ? features : new java.util.ArrayList<>(); }
    public void setFeatures(java.util.List<String> features) { this.features = features; }

    public void setAvailable(boolean available) { this.isAvailable = available; }
    public void setName(String name) { this.name = name; }
    public void setPricePerDay(double price) { this.pricePerDay = price; }
    public void setCategory(String category) { this.category = category; }
    public void setDescription(String desc) { this.description = desc; }
    public void setWhatsappNumber(String num) { this.whatsappNumber = num; }

    public String getFormattedPrice() {
        if (isFree || pricePerDay == 0) {
            return "FREE";
        }
        return "৳" + (int)pricePerDay + " / day";
    }

    public String getAvailabilityText() {
        return isAvailable ? "Available" : "Currently Rented";
    }
}
