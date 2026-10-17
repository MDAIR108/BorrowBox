package com.borrowbox.kuet.utils;

/**
 * App-wide constants. Centralised here so nothing is hardcoded in logic files.
 */
public final class AppConfig {

    private AppConfig() { /* no instances */ }

    /** Only emails ending in this domain are accepted at registration. */
    public static final String KUET_EMAIL_DOMAIN = "stud.kuet.ac.bd";

    /** Firestore collection names – matches the spec. */
    public static final String COL_USERS               = "users";
    public static final String COL_ROLL_LOOKUP         = "rollLookup";
    public static final String COL_STUDENT_VERIF       = "studentVerifications";
    public static final String COL_PRODUCTS            = "products";
    public static final String COL_RENTAL_REQUESTS     = "rentalRequests";
    public static final String COL_RENTALS             = "rentals";
    public static final String COL_REPORTS             = "reports";
    public static final String COL_ADMIN_LOGS          = "adminLogs";
    public static final String COL_NOTIFICATIONS       = "notifications";

    /** Verification status values (must match Firestore rules). */
    public static final String VERIF_PENDING  = "Pending";
    public static final String VERIF_APPROVED = "Approved";
    public static final String VERIF_REJECTED = "Rejected";

    /** Rental status values. */
    public static final String RENTAL_PENDING   = "Pending";
    public static final String RENTAL_ACCEPTED  = "Accepted";
    public static final String RENTAL_REJECTED  = "Rejected";
    public static final String RENTAL_ACTIVE    = "Active";
    public static final String RENTAL_RETURNED  = "Returned";
    public static final String RENTAL_CANCELLED = "Cancelled";
    public static final String RENTAL_OVERDUE   = "Overdue";

    /** Extras used across Intents. */
    public static final String EXTRA_IS_GUEST  = "extra_is_guest";
    public static final String EXTRA_PRODUCT   = "extra_product";
    public static final String EXTRA_USER_UID  = "extra_user_uid";
}
