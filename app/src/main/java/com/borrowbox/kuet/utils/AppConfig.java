package com.borrowbox.kuet.utils;

import android.content.Context;

import com.borrowbox.kuet.BuildConfig;
import com.borrowbox.kuet.networking.SessionManager;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * App-wide constants & KUET configuration.
 * Centralised here so nothing is hardcoded in logic files.
 */
public final class AppConfig {

    private AppConfig() { /* no instances */ }

    /**
     * Default Backend API Base URL:
     * Reads from BuildConfig.DEFAULT_API_BASE_URL (defaults to your PC Wi-Fi LAN IP).
     * Can be overridden at runtime via developer settings without rebuilding.
     */
    public static final String DEFAULT_API_BASE_URL = BuildConfig.DEFAULT_API_BASE_URL;

    /** Currently active API base URL used for all network requests */
    public static String API_BASE_URL = DEFAULT_API_BASE_URL;

    /**
     * Initializes the API base URL from persistent storage (if configured),
     * otherwise falls back to DEFAULT_API_BASE_URL.
     */
    public static void init(Context context) {
        if (context == null) return;
        SessionManager session = new SessionManager(context);
        String saved = session.getServerUrl();
        if (saved != null && !saved.trim().isEmpty()) {
            API_BASE_URL = saved.trim();
        } else {
            API_BASE_URL = DEFAULT_API_BASE_URL;
        }
    }

    /**
     * Updates the API base URL persistently in SessionManager.
     */
    public static void setServerUrl(String url, Context context) {
        if (url == null || url.trim().isEmpty()) {
            url = DEFAULT_API_BASE_URL;
        }
        String cleanUrl = url.trim();
        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            cleanUrl = "http://" + cleanUrl;
        }
        if (cleanUrl.endsWith("/")) {
            cleanUrl = cleanUrl.substring(0, cleanUrl.length() - 1);
        }
        API_BASE_URL = cleanUrl;
        if (context != null) {
            new SessionManager(context).setServerUrl(cleanUrl);
        }
    }

    /** Only emails ending in this domain are accepted at registration. */
    public static final String KUET_EMAIL_DOMAIN = "stud.kuet.ac.bd";

    // KUET Roll Number Validation: 7 digits = 2-digit batch + 2-digit dept + 3-digit serial (e.g. 2307108)
    public static final Pattern KUET_ROLL_PATTERN = Pattern.compile("^([0-9]{2})([0-9]{2})([0-9]{3})$");

    public static final Map<String, String> KUET_DEPT_CODES = new HashMap<>();
    static {
        KUET_DEPT_CODES.put("01", "Civil Engineering (CE)");
        KUET_DEPT_CODES.put("03", "Electrical & Electronic Engineering (EEE)");
        KUET_DEPT_CODES.put("05", "Mechanical Engineering (ME)");
        KUET_DEPT_CODES.put("07", "Computer Science & Engineering (CSE)");
        KUET_DEPT_CODES.put("09", "Chemical Engineering (ChE)");
        KUET_DEPT_CODES.put("11", "Industrial & Production Engineering (IPE)");
        KUET_DEPT_CODES.put("13", "Glass & Ceramic Engineering (GCE)");
        KUET_DEPT_CODES.put("15", "Urban & Regional Planning (URP)");
        KUET_DEPT_CODES.put("17", "Biomedical Engineering (BME)");
        KUET_DEPT_CODES.put("19", "Architecture (Arch)");
        KUET_DEPT_CODES.put("21", "Materials Science & Engineering (MSE)");
        KUET_DEPT_CODES.put("23", "Energy Science & Engineering (ESE)");
        KUET_DEPT_CODES.put("25", "Mechatronics Engineering (MTE)");
        KUET_DEPT_CODES.put("27", "Textile Engineering (TE)");
    }

    public static class RollValidationResult {
        public final boolean isValid;
        public final String errorMessage;
        public final String departmentName;

        public RollValidationResult(boolean isValid, String errorMessage, String departmentName) {
            this.isValid = isValid;
            this.errorMessage = errorMessage;
            this.departmentName = departmentName;
        }
    }

    public static RollValidationResult validateKuetRoll(String roll) {
        if (roll == null || roll.trim().isEmpty()) {
            return new RollValidationResult(false, "Roll number is required.", null);
        }
        String clean = roll.trim();
        Matcher matcher = KUET_ROLL_PATTERN.matcher(clean);
        if (!matcher.matches()) {
            return new RollValidationResult(false,
                    "Invalid KUET roll format. Must be exactly 7 digits (e.g. 2307108: 2-digit batch, 2-digit dept, 3-digit serial).",
                    null);
        }
        String deptCode = matcher.group(2);
        if (!KUET_DEPT_CODES.containsKey(deptCode)) {
            return new RollValidationResult(false,
                    "Invalid department code (" + deptCode + ") in roll number. Check your roll number.",
                    null);
        }
        return new RollValidationResult(true, null, KUET_DEPT_CODES.get(deptCode));
    }

    /** Firestore collection names – kept for legacy compatibility */
    public static final String COL_USERS               = "users";
    public static final String COL_ROLL_LOOKUP         = "rollLookup";
    public static final String COL_STUDENT_VERIF       = "studentVerifications";
    public static final String COL_PRODUCTS            = "products";
    public static final String COL_RENTAL_REQUESTS     = "rentalRequests";
    public static final String COL_RENTALS             = "rentals";
    public static final String COL_REPORTS             = "reports";
    public static final String COL_ADMIN_LOGS          = "adminLogs";
    public static final String COL_NOTIFICATIONS       = "notifications";

    /** Verification status values */
    public static final String VERIF_PENDING  = "Pending";
    public static final String VERIF_APPROVED = "Approved";
    public static final String VERIF_REJECTED = "Rejected";

    /** Rental status values */
    public static final String RENTAL_PENDING   = "Pending";
    public static final String RENTAL_ACCEPTED  = "Accepted";
    public static final String RENTAL_REJECTED  = "Rejected";
    public static final String RENTAL_ACTIVE    = "Active";
    public static final String RENTAL_RETURNED  = "Returned";
    public static final String RENTAL_CANCELLED = "Cancelled";
    public static final String RENTAL_OVERDUE   = "Overdue";

    /** Extras used across Intents */
    public static final String EXTRA_IS_GUEST  = "extra_is_guest";
    public static final String EXTRA_PRODUCT   = "extra_product";
    public static final String EXTRA_PRODUCT_ID = "extra_product_id";
    public static final String EXTRA_USER_UID  = "extra_user_uid";
    public static final String EXTRA_AUTH_TAB  = "extra_auth_tab"; // "login" or "register"
}
