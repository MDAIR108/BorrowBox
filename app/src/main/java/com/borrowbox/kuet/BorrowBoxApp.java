package com.borrowbox.kuet;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;

/**
 * Base Application class for BorrowBox.
 * Ensures FirebaseApp is safely initialized and notification channels are created.
 */
public class BorrowBoxApp extends Application {

    public static final String RENT_REQUESTS_CHANNEL_ID = "rent_requests_v3";

    @Override
    public void onCreate() {
        super.onCreate();
        com.borrowbox.kuet.utils.AppConfig.init(this);
        initFirebaseSafely();
        createNotificationChannels(this);
    }

    public static void createNotificationChannels(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                NotificationChannel channel = new NotificationChannel(
                        RENT_REQUESTS_CHANNEL_ID,
                        "Rental requests",
                        NotificationManager.IMPORTANCE_HIGH
                );
                channel.setDescription("High-priority alerts for new rental requests and status updates");
                channel.enableLights(true);
                channel.setLightColor(Color.parseColor("#F0C38E"));
                channel.enableVibration(true);
                channel.setVibrationPattern(new long[]{0, 250, 150, 250});
                channel.setLockscreenVisibility(NotificationCompat.VISIBILITY_PUBLIC);

                Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                AudioAttributes audioAttributes = new AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .build();
                channel.setSound(soundUri, audioAttributes);

                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    public static void setupUserPresence(String userId) {
        if (userId == null || userId.trim().isEmpty()) return;
        try {
            com.google.firebase.database.FirebaseDatabase rtdb = com.google.firebase.database.FirebaseDatabase.getInstance();
            com.google.firebase.database.DatabaseReference presenceRef = rtdb.getReference("presence").child(userId);
            com.google.firebase.database.DatabaseReference connectedRef = rtdb.getReference(".info/connected");
            connectedRef.addValueEventListener(new com.google.firebase.database.ValueEventListener() {
                @Override
                public void onDataChange(com.google.firebase.database.DataSnapshot snapshot) {
                    Boolean connected = snapshot.getValue(Boolean.class);
                    if (Boolean.TRUE.equals(connected)) {
                        presenceRef.setValue(true);
                        presenceRef.onDisconnect().setValue(false);
                    }
                }

                @Override
                public void onCancelled(com.google.firebase.database.DatabaseError error) {
                    android.util.Log.w("BorrowBoxApp", "Presence cancelled: " + error.getMessage());
                }
            });
        } catch (Exception e) {
            android.util.Log.w("BorrowBoxApp", "RTDB presence not active: " + e.getMessage());
        }
    }

    private void initFirebaseSafely() {
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this);
            }
        } catch (Exception e) {
            android.util.Log.e("BorrowBoxApp", "FirebaseApp init error: " + e.getMessage());
        }
    }
}
