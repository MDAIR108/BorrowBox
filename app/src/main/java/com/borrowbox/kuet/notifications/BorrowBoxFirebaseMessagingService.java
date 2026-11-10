package com.borrowbox.kuet.notifications;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.borrowbox.kuet.R;
import com.borrowbox.kuet.networking.ApiClient;
import com.borrowbox.kuet.networking.SessionManager;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.json.JSONObject;

import java.util.Map;

public class BorrowBoxFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "BorrowBoxFCM";
    public static final String CHANNEL_ID = "rent_requests_v2";
    public static final String CHANNEL_NAME = "Rental Requests & Updates";

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "Refreshed FCM token: " + token);

        SessionManager sessionManager = new SessionManager(getApplicationContext());
        if (sessionManager.isLoggedIn() && !sessionManager.isGuest()) {
            sendTokenToServer(token, sessionManager.getToken());
        }
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        Log.d(TAG, "From: " + remoteMessage.getFrom());

        String title = "BorrowBox";
        String body = "";

        if (remoteMessage.getNotification() != null) {
            title = remoteMessage.getNotification().getTitle() != null
                    ? remoteMessage.getNotification().getTitle() : title;
            body = remoteMessage.getNotification().getBody() != null
                    ? remoteMessage.getNotification().getBody() : "";
        }

        Map<String, String> data = remoteMessage.getData();
        if (data != null) {
            if (data.containsKey("title")) {
                title = data.get("title");
            }
            if (data.containsKey("body")) {
                body = data.get("body");
            }
        }

        showNotification(title, body, data);
    }

    private void showNotification(String title, String body, Map<String, String> data) {
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        // High importance notification channel for Android 8.0+ (heads-up, sound, vibration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifications for rental requests and rental status updates");
            channel.enableLights(true);
            channel.setLightColor(Color.parseColor("#F0C38E")); // Peach accent
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 250, 150, 250});
            channel.setLockscreenVisibility(NotificationCompat.VISIBILITY_PUBLIC);

            android.media.AudioAttributes audioAttributes = new android.media.AudioAttributes.Builder()
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .build();
            channel.setSound(defaultSoundUri, audioAttributes);

            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }

        Intent intent;
        String type = data != null ? data.get("type") : null;
        String rentalId = data != null ? data.get("rentalId") : null;
        if (rentalId == null && data != null) {
            rentalId = data.get("rental_id");
        }

        if ("rent_request".equals(type) && rentalId != null && !rentalId.trim().isEmpty()) {
            intent = new Intent(this, com.borrowbox.kuet.rentals.RentalRequestDetailsActivity.class);
            intent.putExtra(com.borrowbox.kuet.rentals.RentalRequestDetailsActivity.EXTRA_RENTAL_ID, rentalId);
            intent.putExtra("rentalId", rentalId);
            intent.putExtra("rental_id", rentalId);
        } else {
            intent = new Intent(this, NotificationsActivity.class);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (data != null) {
            for (Map.Entry<String, String> entry : data.entrySet()) {
                intent.putExtra(entry.getKey(), entry.getValue());
            }
        }

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                (int) System.currentTimeMillis(),
                intent,
                flags
        );

        int themeColor = Color.parseColor("#F0C38E"); // Brand peach accent

        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_box)
                        .setColor(themeColor)
                        .setContentTitle(title)
                        .setContentText(body)
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                        .setAutoCancel(true)
                        .setSound(defaultSoundUri)
                        .setVibrate(new long[]{0, 250, 150, 250})
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setDefaults(NotificationCompat.DEFAULT_ALL)
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                        .setContentIntent(pendingIntent);

        if (notificationManager != null) {
            int notificationId = (int) (System.currentTimeMillis() & 0xfffffff);
            notificationManager.notify(notificationId, notificationBuilder.build());
        }
    }

    private void sendTokenToServer(String fcmToken, String authToken) {
        ApiClient apiClient = ApiClient.getInstance(getApplicationContext());
        try {
            JSONObject body = new JSONObject();
            body.put("token", fcmToken);
            body.put("fcmToken", fcmToken);
            apiClient.postJson("/users/fcm-token", body, authToken, new ApiClient.ApiCallback<JSONObject>() {
                @Override
                public void onSuccess(JSONObject result) {
                    Log.d(TAG, "FCM token synced to server successfully");
                }

                @Override
                public void onError(String errorMessage) {
                    Log.w(TAG, "Failed to sync FCM token to server: " + errorMessage);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error building FCM token payload", e);
        }
    }
}
