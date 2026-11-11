package com.borrowbox.kuet.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.borrowbox.kuet.R;

public class PhotoUploadForegroundService extends Service {

    public static final String CHANNEL_ID = "photo_upload_channel";
    public static final int NOTIFICATION_ID = 2001;

    public static final String ACTION_START = "com.borrowbox.kuet.action.START_UPLOAD";
    public static final String ACTION_PROGRESS = "com.borrowbox.kuet.action.UPDATE_PROGRESS";
    public static final String ACTION_COMPLETE = "com.borrowbox.kuet.action.COMPLETE_UPLOAD";
    public static final String ACTION_FAIL = "com.borrowbox.kuet.action.FAIL_UPLOAD";

    public static final String EXTRA_TASK_NAME = "extra_task_name";
    public static final String EXTRA_PROGRESS = "extra_progress";
    public static final String EXTRA_MESSAGE = "extra_message";

    private NotificationManager notificationManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static void startUpload(Context context, String taskName) {
        Intent intent = new Intent(context, PhotoUploadForegroundService.class);
        intent.setAction(ACTION_START);
        intent.putExtra(EXTRA_TASK_NAME, taskName);
        ContextCompat.startForegroundService(context, intent);
    }

    public static void updateProgress(Context context, int progress, String message) {
        Intent intent = new Intent(context, PhotoUploadForegroundService.class);
        intent.setAction(ACTION_PROGRESS);
        intent.putExtra(EXTRA_PROGRESS, progress);
        intent.putExtra(EXTRA_MESSAGE, message);
        context.startService(intent);
    }

    public static void completeUpload(Context context, String message) {
        Intent intent = new Intent(context, PhotoUploadForegroundService.class);
        intent.setAction(ACTION_COMPLETE);
        intent.putExtra(EXTRA_MESSAGE, message);
        context.startService(intent);
    }

    public static void failUpload(Context context, String errorMessage) {
        Intent intent = new Intent(context, PhotoUploadForegroundService.class);
        intent.setAction(ACTION_FAIL);
        intent.putExtra(EXTRA_MESSAGE, errorMessage);
        context.startService(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Upload Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Shows real-time progress while uploading photos and media");
            channel.enableLights(false);
            channel.enableVibration(false);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        String action = intent.getAction();

        if (ACTION_START.equals(action)) {
            String taskName = intent.getStringExtra(EXTRA_TASK_NAME);
            if (taskName == null || taskName.isEmpty()) taskName = "Uploading photo...";

            Notification notification = buildProgressNotification(taskName, "Preparing and optimizing image...", 15);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        } else if (ACTION_PROGRESS.equals(action)) {
            int progress = intent.getIntExtra(EXTRA_PROGRESS, 50);
            String message = intent.getStringExtra(EXTRA_MESSAGE);
            if (message == null) message = "Uploading data...";

            Notification notification = buildProgressNotification("Upload in progress", message, progress);
            if (notificationManager != null) {
                notificationManager.notify(NOTIFICATION_ID, notification);
            }
        } else if (ACTION_COMPLETE.equals(action)) {
            String message = intent.getStringExtra(EXTRA_MESSAGE);
            if (message == null) message = "Upload complete!";

            Notification notification = buildFinishedNotification("Upload Complete", message, true);
            if (notificationManager != null) {
                notificationManager.notify(NOTIFICATION_ID, notification);
            }
            mainHandler.postDelayed(() -> {
                stopForeground(STOP_FOREGROUND_DETACH);
                stopSelf();
            }, 1500);
        } else if (ACTION_FAIL.equals(action)) {
            String message = intent.getStringExtra(EXTRA_MESSAGE);
            if (message == null) message = "Upload failed.";

            Notification notification = buildFinishedNotification("Upload Failed", message, false);
            if (notificationManager != null) {
                notificationManager.notify(NOTIFICATION_ID, notification);
            }
            mainHandler.postDelayed(() -> {
                stopForeground(STOP_FOREGROUND_DETACH);
                stopSelf();
            }, 2500);
        }

        return START_NOT_STICKY;
    }

    private Notification buildProgressNotification(String title, String message, int progress) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_box)
                .setColor(Color.parseColor("#F0C38E"))
                .setContentTitle(title)
                .setContentText(message)
                .setProgress(100, progress, progress <= 0)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private Notification buildFinishedNotification(String title, String message, boolean success) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_box)
                .setColor(success ? Color.parseColor("#68D391") : Color.parseColor("#F1A9A9"))
                .setContentTitle(title)
                .setContentText(message)
                .setProgress(0, 0, false)
                .setOngoing(false)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
