package com.borrowbox.kuet.models;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class RentalRequest implements Serializable {
    private String id;
    private String productId;
    private String productName;
    private double productPricePerDay;
    private String productEmoji;
    private String pickupLocation;

    private String ownerId;
    private String ownerName;

    private String renterId;
    private String renterName;
    private String renterDepartment;
    private String renterRollNumber;
    private String renterAddress;
    private String renterPhone;
    private String renterWhatsApp;
    private String renterPhotoUrl;
    private double renterTrustScore = 5.0;
    private String renterVerificationStatus = "Not Submitted";

    private long startDateMillis;
    private long returnDateMillis;
    private int durationDays;
    private double totalCost;

    private String status; // "Pending", "Accepted", "Rejected", "Active", "Returned", "Cancelled", "Overdue"
    private long createdAt;
    private long updatedAt;

    public RentalRequest() {
        this.status = "Pending";
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public double getProductPricePerDay() { return productPricePerDay; }
    public void setProductPricePerDay(double productPricePerDay) { this.productPricePerDay = productPricePerDay; }

    public String getProductEmoji() { return productEmoji != null ? productEmoji : "📦"; }
    public void setProductEmoji(String productEmoji) { this.productEmoji = productEmoji; }

    public String getPickupLocation() { return pickupLocation; }
    public void setPickupLocation(String pickupLocation) { this.pickupLocation = pickupLocation; }

    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }

    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }

    public String getRenterId() { return renterId; }
    public void setRenterId(String renterId) { this.renterId = renterId; }

    public String getRenterName() { return renterName; }
    public void setRenterName(String renterName) { this.renterName = renterName; }

    public String getRenterDepartment() { return renterDepartment; }
    public void setRenterDepartment(String renterDepartment) { this.renterDepartment = renterDepartment; }

    public String getRenterRollNumber() { return renterRollNumber; }
    public void setRenterRollNumber(String renterRollNumber) { this.renterRollNumber = renterRollNumber; }

    public String getRenterPhone() { return renterPhone; }
    public void setRenterPhone(String renterPhone) { this.renterPhone = renterPhone; }

    public String getRenterWhatsApp() { return renterWhatsApp; }
    public void setRenterWhatsApp(String renterWhatsApp) { this.renterWhatsApp = renterWhatsApp; }

    public String getRenterPhotoUrl() { return renterPhotoUrl; }
    public void setRenterPhotoUrl(String renterPhotoUrl) { this.renterPhotoUrl = renterPhotoUrl; }

    public String getRenterAddress() { return renterAddress != null ? renterAddress : ""; }
    public void setRenterAddress(String renterAddress) { this.renterAddress = renterAddress; }

    public double getRenterTrustScore() { return renterTrustScore; }
    public void setRenterTrustScore(double renterTrustScore) { this.renterTrustScore = renterTrustScore; }

    public String getRenterVerificationStatus() { return renterVerificationStatus != null ? renterVerificationStatus : "Not Submitted"; }
    public void setRenterVerificationStatus(String renterVerificationStatus) { this.renterVerificationStatus = renterVerificationStatus; }

    public boolean isRenterVerified() {
        return "Approved".equalsIgnoreCase(renterVerificationStatus);
    }

    public long getStartDateMillis() { return startDateMillis; }
    public void setStartDateMillis(long startDateMillis) { this.startDateMillis = startDateMillis; }

    public long getReturnDateMillis() { return returnDateMillis; }
    public void setReturnDateMillis(long returnDateMillis) { this.returnDateMillis = returnDateMillis; }

    public int getDurationDays() { return durationDays; }
    public void setDurationDays(int durationDays) { this.durationDays = durationDays; }

    public double getTotalCost() { return totalCost; }
    public void setTotalCost(double totalCost) { this.totalCost = totalCost; }

    public String getStatus() { return status != null ? status : "Pending"; }
    public void setStatus(String status) { this.status = status; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }

    public String getFormattedStartDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        return sdf.format(new Date(startDateMillis));
    }

    public String getFormattedReturnDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        return sdf.format(new Date(returnDateMillis));
    }

    /**
     * Rental countdown computed strictly from stored timestamps,
     * never relying on a live app-bound timer.
     */
    public String getCountdownText() {
        long now = System.currentTimeMillis();
        if (now > returnDateMillis) {
            long overdueMillis = now - returnDateMillis;
            long days = overdueMillis / (1000 * 60 * 60 * 24);
            return "Overdue by " + (days == 0 ? "a few hours" : days + " day(s)");
        }

        long diff = returnDateMillis - now;
        long days = diff / (1000 * 60 * 60 * 24);
        long hours = (diff % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60);

        if (days > 0) {
            return days + " day(s) " + hours + " hr(s) remaining";
        } else {
            return hours + " hour(s) remaining";
        }
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("productId", productId);
        map.put("productName", productName);
        map.put("productPricePerDay", productPricePerDay);
        map.put("pickupLocation", pickupLocation);
        map.put("ownerId", ownerId);
        map.put("ownerName", ownerName);
        map.put("renterId", renterId);
        map.put("renterName", renterName);
        map.put("renterDepartment", renterDepartment);
        map.put("renterRollNumber", renterRollNumber);
        map.put("renterAddress", renterAddress);
        map.put("renterPhone", renterPhone);
        map.put("renterWhatsApp", renterWhatsApp);
        map.put("renterPhotoUrl", renterPhotoUrl);
        map.put("renterTrustScore", renterTrustScore);
        map.put("renterVerificationStatus", renterVerificationStatus);
        map.put("startDateMillis", startDateMillis);
        map.put("returnDateMillis", returnDateMillis);
        map.put("durationDays", durationDays);
        map.put("totalCost", totalCost);
        map.put("status", status);
        map.put("createdAt", createdAt);
        map.put("updatedAt", updatedAt);
        return map;
    }
}
