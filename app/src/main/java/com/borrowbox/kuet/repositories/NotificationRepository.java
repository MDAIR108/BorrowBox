package com.borrowbox.kuet.repositories;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import com.borrowbox.kuet.models.AppNotification;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class NotificationRepository {

    public interface DataCallback<T> {
        void onSuccess(T result);
        void onError(String errorMessage);
    }

    private static final String PREFS_NAME = "borrowbox_notifications_prefs";
    private static final String KEY_NOTIFICATIONS = "notifications_json";

    private final Context context;
    private final SharedPreferences prefs;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<AppNotification> cachedNotifications = new ArrayList<>();

    public NotificationRepository(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        loadFromPrefs();
    }

    public void sendNotification(AppNotification notification) {
        if (notification == null) return;
        if (notification.getId() == null || notification.getId().isEmpty()) {
            notification.setId("notif_" + System.currentTimeMillis());
        }
        cachedNotifications.add(0, notification);
        saveToPrefs();
    }

    public void getNotificationsForUser(String userId, DataCallback<List<AppNotification>> callback) {
        List<AppNotification> userNotifs = new ArrayList<>();
        for (AppNotification notif : cachedNotifications) {
            if (userId != null && userId.equals(notif.getRecipientId())) {
                userNotifs.add(notif);
            }
        }
        if (callback != null) {
            mainHandler.post(() -> callback.onSuccess(userNotifs));
        }
    }

    public void markAsRead(String notificationId) {
        for (AppNotification notif : cachedNotifications) {
            if (notif.getId() != null && notif.getId().equals(notificationId)) {
                notif.setRead(true);
                break;
            }
        }
        saveToPrefs();
    }

    private void loadFromPrefs() {
        cachedNotifications.clear();
        String json = prefs.getString(KEY_NOTIFICATIONS, null);
        if (json != null && !json.isEmpty()) {
            try {
                JSONArray arr = new JSONArray(json);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    AppNotification notif = new AppNotification();
                    notif.setId(obj.optString("id", ""));
                    notif.setRecipientId(obj.optString("recipientId", ""));
                    notif.setTitle(obj.optString("title", ""));
                    notif.setMessage(obj.optString("message", ""));
                    notif.setType(obj.optString("type", ""));
                    notif.setRelatedId(obj.optString("relatedId", ""));
                    notif.setTimestamp(obj.optLong("timestamp", System.currentTimeMillis()));
                    notif.setRead(obj.optBoolean("isRead", false));
                    cachedNotifications.add(notif);
                }
            } catch (Exception ignored) {}
        }
    }

    private void saveToPrefs() {
        try {
            JSONArray arr = new JSONArray();
            for (AppNotification n : cachedNotifications) {
                JSONObject obj = new JSONObject();
                obj.put("id", n.getId());
                obj.put("recipientId", n.getRecipientId());
                obj.put("title", n.getTitle());
                obj.put("message", n.getMessage());
                obj.put("type", n.getType());
                obj.put("relatedId", n.getRelatedId());
                obj.put("timestamp", n.getTimestamp());
                obj.put("isRead", n.isRead());
                arr.put(obj);
            }
            prefs.edit().putString(KEY_NOTIFICATIONS, arr.toString()).apply();
        } catch (Exception ignored) {}
    }
}
