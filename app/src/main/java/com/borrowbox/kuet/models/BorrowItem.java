package com.borrowbox.kuet.models;

import java.io.Serializable;

public class BorrowItem implements Serializable {
    private String id;
    private String title;
    private String category;
    private double dailyPrice;
    private boolean isFree;
    private String department;
    private String hallLocation;
    private String ownerName;
    private double rating;
    private String emoji;

    public BorrowItem(String id, String title, String category, double dailyPrice, boolean isFree,
                      String department, String hallLocation, String ownerName, double rating, String emoji) {
        this.id = id;
        this.title = title;
        this.category = category;
        this.dailyPrice = dailyPrice;
        this.isFree = isFree;
        this.department = department;
        this.hallLocation = hallLocation;
        this.ownerName = ownerName;
        this.rating = rating;
        this.emoji = emoji;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getCategory() { return category; }
    public double getDailyPrice() { return dailyPrice; }
    public boolean isFree() { return isFree; }
    public String getDepartment() { return department; }
    public String getHallLocation() { return hallLocation; }
    public String getOwnerName() { return ownerName; }
    public double getRating() { return rating; }
    public String getEmoji() { return emoji; }

    public String getPriceFormatted() {
        if (isFree || dailyPrice == 0) {
            return "FREE";
        }
        return "৳" + (int)dailyPrice + "/day";
    }
}
