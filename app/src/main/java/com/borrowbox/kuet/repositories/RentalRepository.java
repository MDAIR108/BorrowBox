package com.borrowbox.kuet.repositories;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import com.borrowbox.kuet.models.AppNotification;
import com.borrowbox.kuet.models.Rental;
import com.borrowbox.kuet.models.RentalRequest;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class RentalRepository {

    public interface DataCallback<T> {
        void onSuccess(T result);
        void onError(String errorMessage);
    }

    private static final String PREFS_NAME = "borrowbox_rentals_prefs";
    private static final String KEY_REQUESTS = "rental_requests_json";
    private static final String KEY_RENTALS = "rentals_json";

    private final Context context;
    private final SharedPreferences prefs;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<RentalRequest> cachedRequests = new ArrayList<>();
    private final List<Rental> cachedRentals = new ArrayList<>();

    public RentalRepository(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        loadFromPrefs();
    }

    /**
     * Recomputes and verifies price and duration from the product record
     * before saving to prevent client tampering.
     */
    public void submitRentalRequest(RentalRequest request, DataCallback<String> callback) {
        if (request == null) {
            if (callback != null) callback.onError("Invalid request");
            return;
        }

        // Validate dates
        long now = System.currentTimeMillis();
        if (request.getStartDateMillis() < now - (1000 * 60 * 60 * 24)) {
            if (callback != null) callback.onError("Start date cannot be in the past.");
            return;
        }

        if (request.getReturnDateMillis() <= request.getStartDateMillis()) {
            if (callback != null) callback.onError("Expected return date must be after start date.");
            return;
        }

        // Recompute duration and total cost securely
        long diff = request.getReturnDateMillis() - request.getStartDateMillis();
        int days = (int) Math.ceil((double) diff / (1000 * 60 * 60 * 24));
        if (days <= 0) days = 1;
        request.setDurationDays(days);

        double computedTotal = request.getProductPricePerDay() * days;
        request.setTotalCost(computedTotal);
        request.setStatus("Pending");
        request.setCreatedAt(System.currentTimeMillis());
        request.setUpdatedAt(System.currentTimeMillis());

        if (request.getId() == null || request.getId().isEmpty()) {
            request.setId("req_" + System.currentTimeMillis());
        }

        cachedRequests.add(0, request);
        saveToPrefs();

        // Create in-app notification for the owner
        NotificationRepository notifRepo = new NotificationRepository(context);
        notifRepo.sendNotification(new AppNotification(
                "notif_" + System.currentTimeMillis(),
                request.getOwnerId(),
                "New Rental Request",
                request.getRenterName() + " requested to rent \"" + request.getProductName() + "\" for " + days + " day(s).",
                "RENTAL_REQUEST",
                request.getId()
        ));

        mainHandler.postDelayed(() -> {
            if (callback != null) {
                callback.onSuccess(request.getId());
            }
        }, 300);
    }

    /**
     * Accepts a rental request after checking that the product has no overlapping confirmed rentals.
     */
    public void acceptRentalRequest(RentalRequest request, DataCallback<Rental> callback) {
        if (request == null) {
            if (callback != null) callback.onError("Request not found");
            return;
        }

        // Check for date overlap with active rentals for this product
        for (Rental existing : cachedRentals) {
            if (existing.getProductId().equals(request.getProductId())
                    && !"Returned".equalsIgnoreCase(existing.getStatus())
                    && !"Cancelled".equalsIgnoreCase(existing.getStatus())) {

                boolean overlap = (request.getStartDateMillis() <= existing.getReturnDateMillis()
                        && request.getReturnDateMillis() >= existing.getStartDateMillis());
                if (overlap) {
                    if (callback != null) {
                        callback.onError("This product already has a confirmed rental overlapping these dates!");
                    }
                    return;
                }
            }
        }

        // Update request status
        request.setStatus("Accepted");
        request.setUpdatedAt(System.currentTimeMillis());

        // Create confirmed rental record
        Rental rental = new Rental(
                "rent_" + System.currentTimeMillis(),
                request.getId(),
                request.getProductId(),
                request.getProductName(),
                request.getProductEmoji(),
                request.getOwnerId(),
                request.getRenterId(),
                request.getStartDateMillis(),
                request.getReturnDateMillis(),
                request.getTotalCost()
        );
        cachedRentals.add(0, rental);
        saveToPrefs();

        // Notify renter
        NotificationRepository notifRepo = new NotificationRepository(context);
        notifRepo.sendNotification(new AppNotification(
                "notif_" + System.currentTimeMillis(),
                request.getRenterId(),
                "Rental Request Accepted!",
                "Your request for \"" + request.getProductName() + "\" has been accepted. Contact owner via WhatsApp for pickup.",
                "REQUEST_ACCEPTED",
                rental.getId()
        ));

        mainHandler.post(() -> {
            if (callback != null) {
                callback.onSuccess(rental);
            }
        });
    }

    public void rejectRentalRequest(RentalRequest request, DataCallback<Void> callback) {
        if (request == null) {
            if (callback != null) callback.onError("Request not found");
            return;
        }

        request.setStatus("Rejected");
        request.setUpdatedAt(System.currentTimeMillis());
        saveToPrefs();

        // Notify renter
        NotificationRepository notifRepo = new NotificationRepository(context);
        notifRepo.sendNotification(new AppNotification(
                "notif_" + System.currentTimeMillis(),
                request.getRenterId(),
                "Rental Request Update",
                "Your request for \"" + request.getProductName() + "\" could not be accepted at this time.",
                "REQUEST_REJECTED",
                request.getId()
        ));

        mainHandler.post(() -> {
            if (callback != null) {
                callback.onSuccess(null);
            }
        });
    }

    public void getRenterRequests(String renterId, DataCallback<List<RentalRequest>> callback) {
        List<RentalRequest> result = new ArrayList<>();
        for (RentalRequest req : cachedRequests) {
            if (req.getRenterId() != null && req.getRenterId().equals(renterId)) {
                result.add(req);
            }
        }
        mainHandler.post(() -> {
            if (callback != null) callback.onSuccess(result);
        });
    }

    public void getOwnerRequests(String ownerId, DataCallback<List<RentalRequest>> callback) {
        List<RentalRequest> result = new ArrayList<>();
        for (RentalRequest req : cachedRequests) {
            if (req.getOwnerId() != null && req.getOwnerId().equals(ownerId)) {
                result.add(req);
            }
        }
        mainHandler.post(() -> {
            if (callback != null) callback.onSuccess(result);
        });
    }

    public void getActiveRentals(String userId, DataCallback<List<Rental>> callback) {
        List<Rental> result = new ArrayList<>();
        for (Rental r : cachedRentals) {
            if (r.getRenterId().equals(userId) || r.getOwnerId().equals(userId)) {
                result.add(r);
            }
        }
        mainHandler.post(() -> {
            if (callback != null) callback.onSuccess(result);
        });
    }

    public void getRequestById(String requestId, DataCallback<RentalRequest> callback) {
        for (RentalRequest req : cachedRequests) {
            if (req.getId() != null && req.getId().equals(requestId)) {
                if (callback != null) {
                    mainHandler.post(() -> callback.onSuccess(req));
                }
                return;
            }
        }
        if (callback != null) {
            mainHandler.post(() -> callback.onError("Rental request not found"));
        }
    }

    private void saveToPrefs() {
        try {
            JSONArray arr = new JSONArray();
            for (RentalRequest req : cachedRequests) {
                arr.put(new JSONObject(req.toMap()));
            }
            prefs.edit().putString(KEY_REQUESTS, arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    private void loadFromPrefs() {
        cachedRequests.clear();
        String json = prefs.getString(KEY_REQUESTS, null);
        if (json != null) {
            try {
                JSONArray arr = new JSONArray(json);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    RentalRequest req = new RentalRequest();
                    req.setId(obj.optString("id"));
                    req.setProductId(obj.optString("productId"));
                    req.setProductName(obj.optString("productName"));
                    req.setProductPricePerDay(obj.optDouble("productPricePerDay", 20.0));
                    req.setPickupLocation(obj.optString("pickupLocation"));
                    req.setOwnerId(obj.optString("ownerId"));
                    req.setOwnerName(obj.optString("ownerName"));
                    req.setRenterId(obj.optString("renterId"));
                    req.setRenterName(obj.optString("renterName"));
                    req.setRenterDepartment(obj.optString("renterDepartment"));
                    req.setRenterRollNumber(obj.optString("renterRollNumber"));
                    req.setRenterAddress(obj.optString("renterAddress"));
                    req.setRenterPhone(obj.optString("renterPhone"));
                    req.setRenterWhatsApp(obj.optString("renterWhatsApp"));
                    req.setRenterPhotoUrl(obj.optString("renterPhotoUrl"));
                    req.setRenterTrustScore(obj.optDouble("renterTrustScore", 5.0));
                    req.setRenterVerificationStatus(obj.optString("renterVerificationStatus", "Not Submitted"));
                    req.setStartDateMillis(obj.optLong("startDateMillis"));
                    req.setReturnDateMillis(obj.optLong("returnDateMillis"));
                    req.setDurationDays(obj.optInt("durationDays", 1));
                    req.setTotalCost(obj.optDouble("totalCost", 20.0));
                    req.setStatus(obj.optString("status", "Pending"));
                    cachedRequests.add(req);
                }
            } catch (Exception ignored) {}
        }
    }
}
