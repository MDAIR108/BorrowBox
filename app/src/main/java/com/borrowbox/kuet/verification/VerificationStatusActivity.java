package com.borrowbox.kuet.verification;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.borrowbox.kuet.MainActivity;
import com.borrowbox.kuet.databinding.ActivityVerificationStatusBinding;
import com.borrowbox.kuet.utils.AppConfig;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

/**
 * Shown after submitting verification — or when user wants to check their current status.
 * Reads studentVerifications/{uid}.status and displays it with appropriate badge color.
 */
public class VerificationStatusActivity extends AppCompatActivity {

    private ActivityVerificationStatusBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityVerificationStatusBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            loadStatus(user.getUid());
        }

        binding.btnVerifStatusDone.setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
    }

    private void loadStatus(String uid) {
        FirebaseFirestore.getInstance()
                .collection(AppConfig.COL_STUDENT_VERIF)
                .document(uid)
                .get()
                .addOnSuccessListener(snap -> {
                    if (!snap.exists()) return;
                    String status = snap.getString("status");
                    if (status == null) return;

                    binding.tvVerifStatusBadge.setText(status);

                    switch (status) {
                        case AppConfig.VERIF_APPROVED:
                            binding.tvVerifStatusTitle.setText("ID Verified ✓");
                            binding.tvVerifStatusBadge.setBackgroundResource(
                                    com.borrowbox.kuet.R.drawable.bg_status_available);
                            binding.tvVerifStatusBody.setText(
                                    "Your Student ID has been verified by an admin.\n"
                                    + "A ✓ Verified badge is now visible on your profile.");
                            break;
                        case AppConfig.VERIF_REJECTED:
                            binding.tvVerifStatusTitle.setText("Verification Rejected");
                            binding.tvVerifStatusBadge.setBackgroundResource(
                                    com.borrowbox.kuet.R.drawable.bg_status_rented);
                            binding.tvVerifStatusBody.setText(
                                    "Your submission was rejected. Please re-submit with a clearer photo of your KUET Student ID card.");
                            binding.btnVerifStatusDone.setText("Re-submit Verification");
                            binding.btnVerifStatusDone.setOnClickListener(v -> {
                                startActivity(new Intent(this, VerificationActivity.class));
                                finish();
                            });
                            break;
                        default: // Pending
                            binding.tvVerifStatusTitle.setText("Verification Pending");
                            binding.tvVerifStatusBadge.setBackgroundResource(
                                    com.borrowbox.kuet.R.drawable.bg_status_available);
                            binding.tvVerifStatusBadge.setBackgroundTintList(null);
                            break;
                    }
                });
    }
}
