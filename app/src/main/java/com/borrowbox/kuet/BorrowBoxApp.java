package com.borrowbox.kuet;

import android.app.Application;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;

/**
 * Base Application class for BorrowBox.
 * Ensures FirebaseApp is safely initialized even before google-services.json
 * is configured, preventing IllegalStateException crashes on app startup.
 */
public class BorrowBoxApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        com.borrowbox.kuet.utils.AppConfig.init(this);
        initFirebaseSafely();
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
