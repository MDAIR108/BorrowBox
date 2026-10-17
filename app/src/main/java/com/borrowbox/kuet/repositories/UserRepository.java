package com.borrowbox.kuet.repositories;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.borrowbox.kuet.models.UserProfile;

public class UserRepository {

    public interface DataCallback<T> {
        void onSuccess(T result);
        void onError(String errorMessage);
    }

    private static final String PREFS_NAME = "borrowbox_user_prefs";
    private static final String KEY_UID = "uid";
    private static final String KEY_NAME = "full_name";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_PHONE = "phone";
    private static final String KEY_WHATSAPP = "whatsapp";
    private static final String KEY_ADDRESS = "address";
    private static final String KEY_DEPT = "department";
    private static final String KEY_ROLL = "roll";
    private static final String KEY_CR = "cr_name";
    private static final String KEY_PHOTO = "photo_url";
    private static final String KEY_STATUS = "verification_status";

    private final Context context;
    private final SharedPreferences prefs;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public UserRepository(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public String getCurrentUserId() {
        String uid = prefs.getString(KEY_UID, null);
        if (uid == null) {
            uid = "kuet_user_1907001";
            prefs.edit().putString(KEY_UID, uid).apply();
        }
        return uid;
    }

    public void getUserProfile(String uid, DataCallback<UserProfile> callback) {
        // Load from local storage immediately, ensuring no null or frozen states
        UserProfile profile = loadFromLocalPrefs();
        if (profile == null) {
            // Provide default verified student profile for testing
            profile = new UserProfile(
                    uid,
                    "Tanvir Ahmed",
                    "tanvir1907001@stud.kuet.ac.bd",
                    "+8801712345678",
                    "+8801712345678",
                    "Room 312, Amar Ekushey Hall, KUET",
                    "Computer Science & Engineering (CSE)",
                    "1907001",
                    "Md. Rakibul Islam",
                    "",
                    "Approved" // Verified status
            );
            saveToLocalPrefs(profile);
        }

        final UserProfile finalProfile = profile;
        mainHandler.post(() -> {
            if (callback != null) {
                callback.onSuccess(finalProfile);
            }
        });
    }

    public void saveUserProfile(UserProfile profile, Uri newPhotoUri, DataCallback<Void> callback) {
        if (profile == null) {
            if (callback != null) callback.onError("Invalid profile data");
            return;
        }

        if (newPhotoUri != null) {
            profile.setProfilePictureUrl(newPhotoUri.toString());
        }
        profile.setUpdatedAt(System.currentTimeMillis());

        // Save locally
        saveToLocalPrefs(profile);

        mainHandler.postDelayed(() -> {
            if (callback != null) {
                callback.onSuccess(null);
            }
        }, 300);
    }

    private UserProfile loadFromLocalPrefs() {
        if (!prefs.contains(KEY_NAME)) {
            return null;
        }
        UserProfile p = new UserProfile();
        p.setUid(prefs.getString(KEY_UID, getCurrentUserId()));
        p.setFullName(prefs.getString(KEY_NAME, ""));
        p.setEmail(prefs.getString(KEY_EMAIL, ""));
        p.setPhoneNumber(prefs.getString(KEY_PHONE, ""));
        p.setWhatsappNumber(prefs.getString(KEY_WHATSAPP, ""));
        p.setPresentAddress(prefs.getString(KEY_ADDRESS, ""));
        p.setDepartment(prefs.getString(KEY_DEPT, ""));
        p.setRollNumber(prefs.getString(KEY_ROLL, ""));
        p.setCrName(prefs.getString(KEY_CR, ""));
        p.setProfilePictureUrl(prefs.getString(KEY_PHOTO, ""));
        p.setVerificationStatus(prefs.getString(KEY_STATUS, "Not Submitted"));
        return p;
    }

    private void saveToLocalPrefs(UserProfile p) {
        prefs.edit()
                .putString(KEY_UID, p.getUid())
                .putString(KEY_NAME, p.getFullName())
                .putString(KEY_EMAIL, p.getEmail())
                .putString(KEY_PHONE, p.getPhoneNumber())
                .putString(KEY_WHATSAPP, p.getWhatsappNumber())
                .putString(KEY_ADDRESS, p.getPresentAddress())
                .putString(KEY_DEPT, p.getDepartment())
                .putString(KEY_ROLL, p.getRollNumber())
                .putString(KEY_CR, p.getCrName())
                .putString(KEY_PHOTO, p.getProfilePictureUrl())
                .putString(KEY_STATUS, p.getVerificationStatus())
                .apply();
    }
}
