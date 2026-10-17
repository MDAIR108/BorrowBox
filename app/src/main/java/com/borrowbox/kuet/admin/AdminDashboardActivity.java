package com.borrowbox.kuet.admin;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.borrowbox.kuet.MainActivity;
import com.borrowbox.kuet.databinding.ActivityAdminDashboardBinding;
import com.borrowbox.kuet.utils.AppConfig;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

/**
 * Admin Dashboard — entry point for admin actions.
 *
 * SECURITY: Checks users/{uid}.role == "admin" at runtime.
 * If not admin, immediately finishes with an error.
 * This is a defence-in-depth check; Firestore rules are the actual enforcement.
 *
 * Admin role is ONLY set by manually editing users/{uid}.role = "admin"
 * in the Firebase console. There is no in-app path to become admin.
 */
public class AdminDashboardActivity extends AppCompatActivity {

    private ActivityAdminDashboardBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAdminDashboardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        verifyAdminRole();
    }

    private void verifyAdminRole() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Not signed in.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        FirebaseFirestore.getInstance()
                .collection(AppConfig.COL_USERS)
                .document(user.getUid())
                .get()
                .addOnSuccessListener(snap -> {
                    String role = snap.getString("role");
                    if (!"admin".equals(role)) {
                        Toast.makeText(this, "Access denied. Admin only.", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }
                    setupDashboard();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Could not verify role.", Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    private void setupDashboard() {
        binding.tvAdminWelcome.setText("Admin Dashboard");

        binding.btnAdminVerifications.setOnClickListener(v ->
                startActivity(new Intent(this, AdminVerificationListActivity.class)));

        binding.btnAdminHome.setOnClickListener(v ->
                startActivity(new Intent(this, MainActivity.class)));
    }
}
