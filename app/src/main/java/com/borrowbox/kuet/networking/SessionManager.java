package com.borrowbox.kuet.networking;

import android.content.Context;
import android.content.SharedPreferences;

import com.borrowbox.kuet.models.UserProfile;

public class SessionManager {
    private static final String PREF_NAME = "borrowbox_session_pref";
    private static final String KEY_TOKEN = "jwt_token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USER_NAME = "user_name";
    private static final String KEY_USER_ROLL = "user_roll";
    private static final String KEY_USER_EMAIL = "user_email";
    private static final String KEY_USER_PHONE = "user_phone";
    private static final String KEY_USER_DEPT = "user_dept";
    private static final String KEY_USER_HALL = "user_hall";
    private static final String KEY_IS_EMAIL_VERIFIED = "is_email_verified";
    private static final String KEY_IS_ROLL_VERIFIED = "is_roll_verified";
    private static final String KEY_IS_GUEST = "is_guest";
    private static final String KEY_SERVER_URL = "server_url";

    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void saveSession(String token, String id, String name, String roll, String email,
                            String phone, String dept, String hall, boolean isEmailVerified, boolean isRollVerified) {
        prefs.edit()
                .putString(KEY_TOKEN, token)
                .putString(KEY_USER_ID, id)
                .putString(KEY_USER_NAME, name)
                .putString(KEY_USER_ROLL, roll)
                .putString(KEY_USER_EMAIL, email)
                .putString(KEY_USER_PHONE, phone)
                .putString(KEY_USER_DEPT, dept)
                .putString(KEY_USER_HALL, hall)
                .putBoolean(KEY_IS_EMAIL_VERIFIED, isEmailVerified)
                .putBoolean(KEY_IS_ROLL_VERIFIED, isRollVerified)
                .putBoolean(KEY_IS_GUEST, false)
                .apply();
    }

    public void setGuest(boolean isGuest) {
        prefs.edit().putBoolean(KEY_IS_GUEST, isGuest).apply();
    }

    public boolean isGuest() {
        return prefs.getBoolean(KEY_IS_GUEST, false);
    }

    public boolean isLoggedIn() {
        String token = getToken();
        return token != null && !token.trim().isEmpty() && !isGuest();
    }

    public String getToken() {
        return prefs.getString(KEY_TOKEN, null);
    }

    public String getUserId() {
        return prefs.getString(KEY_USER_ID, "");
    }

    public String getUserName() {
        return prefs.getString(KEY_USER_NAME, "KUETian");
    }

    public String getUserRoll() {
        return prefs.getString(KEY_USER_ROLL, "");
    }

    public String getUserEmail() {
        return prefs.getString(KEY_USER_EMAIL, "");
    }

    public String getUserPhone() {
        return prefs.getString(KEY_USER_PHONE, "");
    }

    public String getUserDept() {
        return prefs.getString(KEY_USER_DEPT, "KUET");
    }

    public String getUserHall() {
        return prefs.getString(KEY_USER_HALL, "Campus");
    }

    public boolean isEmailVerified() {
        return prefs.getBoolean(KEY_IS_EMAIL_VERIFIED, false);
    }

    public void setEmailVerified(boolean verified) {
        prefs.edit().putBoolean(KEY_IS_EMAIL_VERIFIED, verified).apply();
    }

    public boolean isRollVerified() {
        return prefs.getBoolean(KEY_IS_ROLL_VERIFIED, false);
    }

    public UserProfile getUserProfile() {
        UserProfile p = new UserProfile();
        p.setUid(getUserId());
        p.setFullName(getUserName());
        p.setRollNumber(getUserRoll());
        p.setEmail(getUserEmail());
        p.setPhoneNumber(getUserPhone());
        p.setDepartment(getUserDept());
        p.setHallName(getUserHall());
        p.setVerified(isEmailVerified());
        p.setVerificationStatus(isEmailVerified() ? "Approved" : "Pending");
        return p;
    }

    public void logout() {
        // Keep server URL across logouts so user doesn't lose custom IP
        String savedServerUrl = getServerUrl();
        prefs.edit().clear().apply();
        if (savedServerUrl != null) {
            setServerUrl(savedServerUrl);
        }
    }

    public String getServerUrl() {
        return prefs.getString(KEY_SERVER_URL, null);
    }

    public void setServerUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            prefs.edit().remove(KEY_SERVER_URL).apply();
            return;
        }
        String cleanUrl = url.trim();
        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            cleanUrl = "http://" + cleanUrl;
        }
        if (cleanUrl.endsWith("/")) {
            cleanUrl = cleanUrl.substring(0, cleanUrl.length() - 1);
        }
        prefs.edit().putString(KEY_SERVER_URL, cleanUrl).apply();
    }
}
