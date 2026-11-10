package com.borrowbox.kuet.models;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class RentalDetail implements Serializable {
    private String id;
    private String productId;
    private String productName;
    private String productImage;
    private double rentPerDay;
    private int days;
    private double totalPrice;
    private String status;
    private String createdAt;
    private String ownerName;
    private String renterName;
    private String ownerId;
    private String renterId;
    private String model;
    private String description;
    private List<String> features;
    private String category;
    private String condition;

    public RentalDetail() {
        this.features = new ArrayList<>();
        this.status = "Pending";
    }

    public static RentalDetail fromJson(JSONObject json) {
        RentalDetail r = new RentalDetail();
        if (json == null) return r;

        // Document ID
        r.id = json.optString("id", json.optString("_id", ""));

        // Product info
        r.productId = json.optString("productId", json.optString("product_id", ""));
        r.productName = json.optString("productName", json.optString("name", json.optString("title", "Product")));
        r.productImage = json.optString("productImage", json.optString("imageUrl", json.optString("image_url", json.optString("photoUrl", ""))));

        // Rental terms
        r.days = json.optInt("days", 1);
        if (r.days <= 0) r.days = 1;

        r.rentPerDay = json.optDouble("rentPerDay", json.optDouble("pricePerDay", json.optDouble("price_per_day", 0.0)));
        r.totalPrice = json.optDouble("totalPrice", json.optDouble("total_price", r.rentPerDay * r.days));

        if (r.rentPerDay == 0.0 && r.totalPrice > 0.0 && r.days > 0) {
            r.rentPerDay = r.totalPrice / r.days;
        }

        // Status & Dates
        r.status = json.optString("status", "Pending");
        r.createdAt = json.optString("createdAt", json.optString("created_at", ""));

        // Names & IDs
        r.ownerName = json.optString("ownerName", json.optString("owner_name", "BorrowBox Member"));
        r.renterName = json.optString("renterName", json.optString("renter_name", "User"));
        r.ownerId = json.optString("ownerId", json.optString("owner_id", ""));
        r.renterId = json.optString("renterId", json.optString("renter_id", ""));

        // Product extra details
        r.model = json.optString("model", "");
        r.description = json.optString("description", "");
        r.category = json.optString("category", "General");
        r.condition = json.optString("condition", "Good");

        // Features list
        r.features = new ArrayList<>();
        JSONArray featArr = json.optJSONArray("features");
        if (featArr != null) {
            for (int i = 0; i < featArr.length(); i++) {
                String f = featArr.optString(i, "").trim();
                if (!f.isEmpty()) {
                    r.features.add(f);
                }
            }
        } else {
            String rawFeat = json.optString("features", "");
            if (!rawFeat.isEmpty()) {
                String[] parts = rawFeat.split("[,;]");
                for (String p : parts) {
                    String trimmed = p.trim();
                    if (!trimmed.isEmpty()) {
                        r.features.add(trimmed);
                    }
                }
            }
        }

        return r;
    }

    public String getId() { return id != null ? id : ""; }
    public void setId(String id) { this.id = id; }

    public String getProductId() { return productId != null ? productId : ""; }
    public void setProductId(String productId) { this.productId = productId; }

    public String getProductName() { return (productName != null && !productName.trim().isEmpty()) ? productName : "Product"; }
    public void setProductName(String productName) { this.productName = productName; }

    public String getProductImage() { return productImage != null ? productImage : ""; }
    public void setProductImage(String productImage) { this.productImage = productImage; }

    public double getRentPerDay() { return rentPerDay; }
    public void setRentPerDay(double rentPerDay) { this.rentPerDay = rentPerDay; }

    public int getDays() { return days > 0 ? days : 1; }
    public void setDays(int days) { this.days = days; }

    public double getTotalPrice() { return totalPrice; }
    public void setTotalPrice(double totalPrice) { this.totalPrice = totalPrice; }

    public String getStatus() { return status != null ? status : "Pending"; }
    public void setStatus(String status) { this.status = status; }

    public String getCreatedAt() { return createdAt != null ? createdAt : ""; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getOwnerName() { return (ownerName != null && !ownerName.trim().isEmpty()) ? ownerName : "BorrowBox Member"; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }

    public String getRenterName() { return (renterName != null && !renterName.trim().isEmpty()) ? renterName : "User"; }
    public void setRenterName(String renterName) { this.renterName = renterName; }

    public String getOwnerId() { return ownerId != null ? ownerId : ""; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }

    public String getRenterId() { return renterId != null ? renterId : ""; }
    public void setRenterId(String renterId) { this.renterId = renterId; }

    public String getModel() { return model != null ? model : ""; }
    public void setModel(String model) { this.model = model; }

    public String getDescription() { return description != null ? description : ""; }
    public void setDescription(String description) { this.description = description; }

    public List<String> getFeatures() { return features != null ? features : new ArrayList<>(); }
    public void setFeatures(List<String> features) { this.features = features; }

    public String getCategory() { return (category != null && !category.trim().isEmpty()) ? category : "General"; }
    public void setCategory(String category) { this.category = category; }

    public String getCondition() { return (condition != null && !condition.trim().isEmpty()) ? condition : "Good"; }
    public void setCondition(String condition) { this.condition = condition; }

    /**
     * Helper: Format rent per day (e.g. "৳20 / day" or "Free")
     */
    public String getFormattedRentPerDay() {
        if (rentPerDay <= 0) return "Free";
        if (rentPerDay == (long) rentPerDay) {
            return "৳" + (long) rentPerDay + " / day";
        }
        return String.format(Locale.US, "৳%.2f / day", rentPerDay);
    }

    /**
     * Helper: Format days (e.g. "1 day" or "3 days")
     */
    public String getFormattedDays() {
        int d = getDays();
        return d + (d == 1 ? " day" : " days");
    }

    /**
     * Helper: Format total price (e.g. "৳60" or "FREE")
     */
    public String getFormattedTotalPrice() {
        if (totalPrice <= 0) return "FREE";
        if (totalPrice == (long) totalPrice) {
            return "৳" + (long) totalPrice;
        }
        return String.format(Locale.US, "৳%.2f", totalPrice);
    }

    /**
     * Helper: Format request date safely into human readable string
     */
    public String getFormattedRequestDate() {
        if (createdAt == null || createdAt.trim().isEmpty()) {
            return "N/A";
        }
        String str = createdAt.trim();

        // Check if numeric millis
        try {
            long millis = Long.parseLong(str);
            if (millis > 0) {
                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
                return sdf.format(new Date(millis));
            }
        } catch (NumberFormatException ignored) {}

        // ISO date parsers
        String[] patterns = {
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd'T'HH:mm:ss.SSS",
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd"
        };
        for (String pattern : patterns) {
            try {
                SimpleDateFormat parser = new SimpleDateFormat(pattern, Locale.US);
                parser.setTimeZone(TimeZone.getTimeZone("UTC"));
                Date date = parser.parse(str);
                if (date != null) {
                    SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
                    return sdf.format(date);
                }
            } catch (Exception ignored) {}
        }

        return str;
    }

    /**
     * Returns canonical status: "Pending", "Accepted", "Rejected", or "Cancelled"
     * evaluated case-insensitively.
     */
    public String getCanonicalStatus() {
        if (status == null) return "Pending";
        String s = status.trim().toLowerCase(Locale.ROOT);
        if (s.equals("accepted") || s.equals("active") || s.equals("completed") || s.equals("returned")) {
            return "Accepted";
        } else if (s.equals("rejected") || s.equals("declined")) {
            return "Rejected";
        } else if (s.equals("cancelled") || s.equals("canceled")) {
            return "Cancelled";
        }
        return "Pending";
    }
}
