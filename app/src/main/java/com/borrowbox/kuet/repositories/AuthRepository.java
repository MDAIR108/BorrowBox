package com.borrowbox.kuet.repositories;

import com.borrowbox.kuet.utils.AppConfig;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

/**
 * All Firebase Authentication and initial user-document logic lives here.
 *
 * Rules enforced here (also in Firestore rules):
 *  - Email must end in @stud.kuet.ac.bd
 *  - Roll number must be unique (checked via rollLookup/{roll} document)
 *  - Admin role is NEVER set here; set only in Firebase console manually.
 */
public class AuthRepository {

    public interface AuthCallback {
        void onSuccess(FirebaseUser user);
        void onError(String message);
    }

    public interface VoidCallback {
        void onSuccess();
        void onError(String message);
    }

    private final FirebaseAuth auth;
    private final FirebaseFirestore db;

    public AuthRepository() {
        FirebaseAuth a = null;
        FirebaseFirestore d = null;
        try {
            a = FirebaseAuth.getInstance();
            d = FirebaseFirestore.getInstance();
        } catch (Exception e) {
            e.printStackTrace();
        }
        this.auth = a;
        this.db = d;
    }

    /** @return the currently signed-in user, or null if not signed in or not initialized. */
    public FirebaseUser getCurrentUser() {
        if (auth == null) return null;
        return auth.getCurrentUser();
    }

    public boolean isLoggedIn() {
        return getCurrentUser() != null;
    }

    /**
     * Register a new student.
     *
     * Steps:
     * 1. Validate email domain (@stud.kuet.ac.bd).
     * 2. Check rollLookup/{rollNumber} for uniqueness.
     * 3. Create Firebase Auth account.
     * 4. Write rollLookup/{rollNumber} = {uid, email} (prevents parallel duplicate registration).
     * 5. Write users/{uid} base document.
     * 6. Send email verification.
     */
    public void register(String email, String password, String fullName,
                         String rollNumber, String department,
                         AuthCallback callback) {

        if (auth == null || db == null) {
            callback.onError("Firebase service is not available. Please check configuration.");
            return;
        }

        // 1. Domain check
        if (!email.toLowerCase().trim().endsWith("@" + AppConfig.KUET_EMAIL_DOMAIN)) {
            callback.onError("Only @" + AppConfig.KUET_EMAIL_DOMAIN + " emails are allowed.");
            return;
        }

        // 2. Roll number uniqueness check
        String normalizedRoll = rollNumber.trim();
        db.collection(AppConfig.COL_ROLL_LOOKUP)
                .document(normalizedRoll)
                .get()
                .addOnSuccessListener(snap -> {
                    if (snap.exists()) {
                        callback.onError("This roll number is already registered. Contact support if you think this is a mistake.");
                        return;
                    }

                    // 3. Create Firebase Auth account
                    auth.createUserWithEmailAndPassword(email.trim(), password)
                            .addOnSuccessListener(result -> {
                                FirebaseUser user = result.getUser();
                                if (user == null) {
                                    callback.onError("Registration failed. Please try again.");
                                    return;
                                }

                                String uid = user.getUid();

                                // 4. Write rollLookup entry (atomic — prevents duplicates even on race)
                                Map<String, Object> rollEntry = new HashMap<>();
                                rollEntry.put("uid", uid);
                                rollEntry.put("email", email.trim().toLowerCase());

                                db.collection(AppConfig.COL_ROLL_LOOKUP)
                                        .document(normalizedRoll)
                                        .set(rollEntry)
                                        .addOnSuccessListener(v -> {

                                            // 5. Write base users document
                                            Map<String, Object> userDoc = new HashMap<>();
                                            userDoc.put("uid", uid);
                                            userDoc.put("fullName", fullName.trim());
                                            userDoc.put("email", email.trim().toLowerCase());
                                            userDoc.put("rollNumber", normalizedRoll);
                                            userDoc.put("department", department.trim());
                                            userDoc.put("verificationStatus", AppConfig.VERIF_PENDING);
                                            userDoc.put("trustScore", 5.0);
                                            userDoc.put("role", "user"); // NEVER "admin" here
                                            userDoc.put("createdAt", System.currentTimeMillis());
                                            userDoc.put("updatedAt", System.currentTimeMillis());

                                            db.collection(AppConfig.COL_USERS)
                                                    .document(uid)
                                                    .set(userDoc, SetOptions.merge())
                                                    .addOnSuccessListener(v2 -> {
                                                        // 6. Send email verification
                                                        user.sendEmailVerification();
                                                        callback.onSuccess(user);
                                                    })
                                                    .addOnFailureListener(e ->
                                                            callback.onError("Failed to save profile: " + e.getMessage()));
                                        })
                                        .addOnFailureListener(e ->
                                                callback.onError("Failed to reserve roll number: " + e.getMessage()));
                            })
                            .addOnFailureListener(e -> {
                                String msg = e.getMessage() != null ? e.getMessage() : "Registration failed.";
                                // Make Firebase errors readable
                                if (msg.contains("email address is already in use")) {
                                    msg = "An account with this email already exists. Please log in.";
                                } else if (msg.contains("weak-password")) {
                                    msg = "Password is too weak. Use at least 6 characters.";
                                }
                                callback.onError(msg);
                            });
                })
                .addOnFailureListener(e ->
                        callback.onError("Could not verify roll number. Check your connection."));
    }

    /**
     * Sign in an existing user.
     * After sign-in, the caller should check user.isEmailVerified().
     */
    public void login(String email, String password, AuthCallback callback) {
        if (auth == null) {
            callback.onError("Firebase service is not available. Please check configuration.");
            return;
        }

        if (!email.toLowerCase().trim().endsWith("@" + AppConfig.KUET_EMAIL_DOMAIN)) {
            callback.onError("Please use your KUET student email (@" + AppConfig.KUET_EMAIL_DOMAIN + ").");
            return;
        }

        auth.signInWithEmailAndPassword(email.trim(), password)
                .addOnSuccessListener(result -> {
                    FirebaseUser user = result.getUser();
                    if (user == null) {
                        callback.onError("Login failed. Please try again.");
                        return;
                    }
                    callback.onSuccess(user);
                })
                .addOnFailureListener(e -> {
                    String msg = e.getMessage() != null ? e.getMessage() : "Login failed.";
                    if (msg.contains("password is invalid") || msg.contains("no user record")) {
                        msg = "Incorrect email or password.";
                    } else if (msg.contains("too many requests")) {
                        msg = "Too many failed attempts. Try again later.";
                    }
                    callback.onError(msg);
                });
    }

    /** Send a password-reset email. */
    public void sendPasswordResetEmail(String email, VoidCallback callback) {
        if (auth == null) {
            callback.onError("Firebase service is not available.");
            return;
        }
        auth.sendPasswordResetEmail(email.trim())
                .addOnSuccessListener(v -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(
                        e.getMessage() != null ? e.getMessage() : "Could not send reset email."));
    }

    /** Resend the verification email to the current user. */
    public void resendVerificationEmail(VoidCallback callback) {
        if (auth == null) {
            callback.onError("Firebase service is not available.");
            return;
        }
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            callback.onError("Not signed in.");
            return;
        }
        user.sendEmailVerification()
                .addOnSuccessListener(v -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(
                        e.getMessage() != null ? e.getMessage() : "Could not send email."));
    }

    /** Sign out. */
    public void signOut() {
        if (auth != null) {
            auth.signOut();
        }
    }
}
