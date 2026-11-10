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

    public static final String RENT_REQUESTS_CHANNEL_ID = "rent_requests_v2";

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
                        "Rental Requests & Updates",
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
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .build();
                channel.setSound(soundUri, audioAttributes);

                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private void initFirebaseSafely() {
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseOptions options = new FirebaseOptions.Builder()
                        .setApplicationId(getPackageName())
                        .setApiKey("AIzaSyBorrowBoxKuetDemoKeyPlaceholder00")
                        .setProjectId("borrowbox-kuet")
                        .setStorageBucket("borrowbox-kuet.appspot.com")
                        .build();
                FirebaseApp.initializeApp(this, options);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
