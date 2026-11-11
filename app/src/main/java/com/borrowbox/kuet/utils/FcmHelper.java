package com.borrowbox.kuet.utils;

import android.content.Context;
import android.util.Log;

import com.borrowbox.kuet.networking.ApiClient;
import com.borrowbox.kuet.networking.SessionManager;
import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONObject;

public class FcmHelper {
    private static final String TAG = "FcmHelper";

    public static void registerFcmToken(Context context) {
        if (context == null) return;
        SessionManager sessionManager = new SessionManager(context.getApplicationContext());
        if (!sessionManager.isLoggedIn() || sessionManager.isGuest()) {
            return;
        }
        String authToken = sessionManager.getToken();
        registerFcmToken(context, authToken);
    }

    public static void registerFcmToken(Context context, String authToken) {
        if (context == null || authToken == null || authToken.trim().isEmpty()) return;
        Context appContext = context.getApplicationContext();
        SessionManager sessionManager = new SessionManager(appContext);
        ApiClient apiClient = ApiClient.getInstance(appContext);

        try {
            FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
                if (!task.isSuccessful() || task.getResult() == null) {
                    Log.w(TAG, "Fetching FCM registration token failed", task.getException());
                    return;
                }
                String token = task.getResult();
                sessionManager.saveFcmToken(token);
                Log.d(TAG, "FCM token retrieved: " + token);

                try {
                    JSONObject body = new JSONObject();
                    body.put("token", token);
                    body.put("fcmToken", token);
                    apiClient.postJson("/users/fcm-token", body, authToken, new ApiClient.ApiCallback<JSONObject>() {
                        @Override
                        public void onSuccess(JSONObject result) {
                            Log.d(TAG, "FCM token successfully registered on backend");
                        }

                        @Override
                        public void onError(String errorMessage) {
                            Log.w(TAG, "Failed to register FCM token on backend: " + errorMessage);
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Error building FCM registration payload", e);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error accessing FirebaseMessaging: " + e.getMessage());
        }
    }
}
