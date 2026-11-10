package com.borrowbox.kuet.notifications;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.borrowbox.kuet.databinding.ActivityNotificationsBinding;
import com.borrowbox.kuet.networking.ApiClient;
import com.borrowbox.kuet.networking.SessionManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class NotificationsActivity extends AppCompatActivity {

    private ActivityNotificationsBinding binding;
    private ApiClient apiClient;
    private SessionManager sessionManager;
    private NotificationAdapter adapter;
    private final List<JSONObject> notifications = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityNotificationsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        apiClient = ApiClient.getInstance(this);
        sessionManager = new SessionManager(this);

        binding.btnNotifBack.setOnClickListener(v -> finish());

        setupRecyclerView();
        loadNotifications();
    }

    private void setupRecyclerView() {
        adapter = new NotificationAdapter(notifications, this::handleNotificationClick);
        binding.rvNotifications.setLayoutManager(new LinearLayoutManager(this));
        binding.rvNotifications.setAdapter(adapter);
    }

    private void loadNotifications() {
        String token = sessionManager.getToken();
        binding.pbLoading.setVisibility(View.VISIBLE);

        apiClient.getNotifications(token, new ApiClient.ApiCallback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject response) {
                binding.pbLoading.setVisibility(View.GONE);
                notifications.clear();
                JSONArray arr = response.optJSONArray("notifications");
                if (arr != null && arr.length() > 0) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject obj = arr.optJSONObject(i);
                        if (obj != null) {
                            notifications.add(obj);
                        }
                    }
                }
                adapter.notifyDataSetChanged();

                if (notifications.isEmpty()) {
                    binding.layoutEmpty.setVisibility(View.VISIBLE);
                    binding.rvNotifications.setVisibility(View.GONE);
                } else {
                    binding.layoutEmpty.setVisibility(View.GONE);
                    binding.rvNotifications.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onError(String errorMessage) {
                binding.pbLoading.setVisibility(View.GONE);
                Toast.makeText(NotificationsActivity.this, "Failed to load notifications: " + errorMessage, Toast.LENGTH_SHORT).show();
                if (notifications.isEmpty()) {
                    binding.layoutEmpty.setVisibility(View.VISIBLE);
                    binding.rvNotifications.setVisibility(View.GONE);
                }
            }
        });
    }

    private void handleNotificationClick(JSONObject item) {
        String notifId = item.optString("id", "");
        String type = item.optString("type", "");
        String title = item.optString("title", "Notification");
        String message = item.optString("message", "");
        String rentalId = item.optString("rentalId", "");
        String token = sessionManager.getToken();

        // Mark as read in background
        if (!notifId.isEmpty()) {
            apiClient.markNotificationRead(notifId, token, new ApiClient.ApiCallback<JSONObject>() {
                @Override
                public void onSuccess(JSONObject result) {
                    try {
                        item.put("read", true);
                        adapter.notifyDataSetChanged();
                    } catch (Exception ignored) {}
                }

                @Override
                public void onError(String errorMessage) {}
            });
        }

        if (("rent_request".equals(type) || title.toLowerCase().contains("request")) && !rentalId.isEmpty()) {
            Intent intent = new Intent(this, com.borrowbox.kuet.rentals.RentalRequestDetailsActivity.class);
            intent.putExtra(com.borrowbox.kuet.rentals.RentalRequestDetailsActivity.EXTRA_RENTAL_ID, rentalId);
            intent.putExtra("rentalId", rentalId);
            startActivity(intent);
        } else {
            // Simple info dialog
            new MaterialAlertDialogBuilder(this)
                    .setTitle(title)
                    .setMessage(message)
                    .setPositiveButton("OK", null)
                    .show();
        }
    }

    private void respondToRental(String rentalId, boolean accept) {
        String token = sessionManager.getToken();
        binding.pbLoading.setVisibility(View.VISIBLE);

        ApiClient.ApiCallback<JSONObject> callback = new ApiClient.ApiCallback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject result) {
                binding.pbLoading.setVisibility(View.GONE);
                String msg = accept ? "Rental request accepted! ✅" : "Rental request declined. ❌";
                Toast.makeText(NotificationsActivity.this, msg, Toast.LENGTH_LONG).show();
                loadNotifications();
            }

            @Override
            public void onError(String errorMessage) {
                binding.pbLoading.setVisibility(View.GONE);
                Toast.makeText(NotificationsActivity.this, "Action failed: " + errorMessage, Toast.LENGTH_LONG).show();
            }
        };

        if (accept) {
            apiClient.acceptRental(rentalId, token, callback);
        } else {
            apiClient.rejectRental(rentalId, token, callback);
        }
    }
}
