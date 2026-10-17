package com.borrowbox.kuet.admin;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.borrowbox.kuet.databinding.ActivityAdminVerificationDetailBinding;
import com.borrowbox.kuet.utils.AppConfig;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

/**
 * Admin screen: view a specific student's verification request and approve or reject.
 *
 * SECURITY:
 *  - Role re-checked at onCreate. If not admin → finish().
 *  - Firestore rules also enforce admin-only writes to studentVerifications and
 *    admin-only writes to verificationStatus on users/{uid}.
 *  - Admin can NEVER approve their own account (Firestore rule + logic: studentUid != adminUid).
 *
 * To load the ID image we fetch the download URL from studentVerifications/{uid}.idImageUrl.
 * Glide is NOT yet in dependencies (Task 6 note: would add Glide once Firebase is active).
 * For now we set ImageView from the URL string — Glide would replace this one line.
 */
public class AdminVerificationDetailActivity extends AppCompatActivity {

    private ActivityAdminVerificationDetailBinding binding;
    private String studentUid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAdminVerificationDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        studentUid = getIntent().getStringExtra("studentUid");
        if (studentUid == null || studentUid.isEmpty()) {
            Toast.makeText(this, "Invalid student UID.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Re-check admin role
        FirebaseUser admin = FirebaseAuth.getInstance().getCurrentUser();
        if (admin == null) { finish(); return; }

        FirebaseFirestore.getInstance()
                .collection(AppConfig.COL_USERS)
                .document(admin.getUid())
                .get()
                .addOnSuccessListener(snap -> {
                    if (!"admin".equals(snap.getString("role"))) {
                        Toast.makeText(this, "Admin only.", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }
                    // Safety: admin cannot approve themselves
                    if (admin.getUid().equals(studentUid)) {
                        Toast.makeText(this, "Admin cannot approve their own account.", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }
                    setupDetail();
                })
                .addOnFailureListener(e -> finish());

        binding.btnAdminDetailBack.setOnClickListener(v -> finish());
    }

    private void setupDetail() {
        // Load student user profile
        FirebaseFirestore.getInstance()
                .collection(AppConfig.COL_USERS)
                .document(studentUid)
                .get()
                .addOnSuccessListener(snap -> {
                    binding.tvAdminStudentName.setText("Name: " + snap.getString("fullName"));
                    binding.tvAdminStudentEmail.setText("Email: " + snap.getString("email"));
                    binding.tvAdminStudentRoll.setText("Roll: " + snap.getString("rollNumber"));
                    binding.tvAdminStudentDept.setText("Department: " + snap.getString("department"));
                });

        // Load verification doc (for ID image URL)
        FirebaseFirestore.getInstance()
                .collection(AppConfig.COL_STUDENT_VERIF)
                .document(studentUid)
                .get()
                .addOnSuccessListener(snap -> {
                    String imageUrl = snap.getString("idImageUrl");
                    if (imageUrl != null && !imageUrl.isEmpty()) {
                        // TODO: replace with Glide.with(this).load(imageUrl).into(binding.ivAdminIdImage)
                        // once Glide dependency is added after google-services.json placement.
                        // For now, attempt to parse as local Uri (works for re-submission from device)
                        try {
                            binding.ivAdminIdImage.setImageURI(Uri.parse(imageUrl));
                        } catch (Exception ignored) { /* URL from Storage won't load via setImageURI */ }
                    }
                });

        binding.btnAdminApprove.setOnClickListener(v -> updateVerificationStatus(AppConfig.VERIF_APPROVED));
        binding.btnAdminReject.setOnClickListener(v -> updateVerificationStatus(AppConfig.VERIF_REJECTED));
    }

    /**
     * Writes status to:
     *   1. studentVerifications/{studentUid}.status
     *   2. users/{studentUid}.verificationStatus
     * Also logs the admin action to adminLogs/{docId}.
     * Firestore rules enforce that only admins can do this and that no self-approval happens.
     */
    private void updateVerificationStatus(String newStatus) {
        setDecisionLoading(true);
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        FirebaseUser admin = FirebaseAuth.getInstance().getCurrentUser();

        // 1. Update studentVerifications doc
        db.collection(AppConfig.COL_STUDENT_VERIF)
                .document(studentUid)
                .update("status", newStatus,
                        "reviewedAt", System.currentTimeMillis(),
                        "reviewedBy", admin != null ? admin.getUid() : "unknown")
                .addOnSuccessListener(v1 -> {
                    // 2. Update users/{studentUid}.verificationStatus
                    db.collection(AppConfig.COL_USERS)
                            .document(studentUid)
                            .update("verificationStatus", newStatus)
                            .addOnSuccessListener(v2 -> {
                                // 3. Log the admin action
                                Map<String, Object> log = new HashMap<>();
                                log.put("adminUid", admin != null ? admin.getUid() : "unknown");
                                log.put("action", "verificationStatus");
                                log.put("targetUid", studentUid);
                                log.put("newValue", newStatus);
                                log.put("timestamp", System.currentTimeMillis());
                                db.collection(AppConfig.COL_ADMIN_LOGS).add(log);

                                setDecisionLoading(false);
                                Toast.makeText(this,
                                        "Student " + newStatus + " successfully.",
                                        Toast.LENGTH_SHORT).show();
                                finish();
                            })
                            .addOnFailureListener(e -> {
                                setDecisionLoading(false);
                                Toast.makeText(this, "Failed to update user: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            });
                })
                .addOnFailureListener(e -> {
                    setDecisionLoading(false);
                    Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void setDecisionLoading(boolean loading) {
        binding.progressAdminDecision.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnAdminApprove.setEnabled(!loading);
        binding.btnAdminReject.setEnabled(!loading);
    }
}
